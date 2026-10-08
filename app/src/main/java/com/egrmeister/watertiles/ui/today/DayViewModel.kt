package com.egrmeister.watertiles.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egrmeister.watertiles.AppContainer
import com.egrmeister.watertiles.core.Format
import com.egrmeister.watertiles.data.local.AppSettings
import com.egrmeister.watertiles.domain.entries.QuickAddGuard
import com.egrmeister.watertiles.domain.entries.UndoSlot
import com.egrmeister.watertiles.domain.entries.WaterEntry
import com.egrmeister.watertiles.domain.goals.GoalResolver
import com.egrmeister.watertiles.domain.mosaic.Mosaic
import com.egrmeister.watertiles.domain.mosaic.MosaicCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DayUiState(
    val loading: Boolean = true,
    val date: LocalDate = LocalDate.MIN,
    val isToday: Boolean = false,
    val isFuture: Boolean = false,
    val goalMl: Int? = null,
    val mosaic: Mosaic? = null,
    val entries: List<WaterEntry> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

sealed interface UndoAction {
    data class Added(val id: Long, val amountMl: Int) : UndoAction
    data class Deleted(val entry: WaterEntry) : UndoAction
}

data class DayMessage(val text: String, val undo: UndoAction?)

/**
 * Backs both Today (follows the current date, refreshed at midnight) and a calendar day.
 * @param fixedDate null to follow today.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModel(private val c: AppContainer, fixedDate: LocalDate?) : ViewModel() {

    private val dateFlow: Flow<LocalDate> = fixedDate?.let { flowOf(it) } ?: c.todayClock.today
    private val guard = QuickAddGuard()
    private val undoSlot = UndoSlot<UndoAction>()
    private val messages = Channel<DayMessage>(Channel.BUFFERED)
    val events: Flow<DayMessage> = messages.receiveAsFlow()

    val state: StateFlow<DayUiState> = dateFlow.flatMapLatest { date ->
        combine(
            c.repository.observeEntries(date),
            c.repository.observeGoalChanges(),
            c.settings.settings,
            c.todayClock.today,
        ) { entries, goals, settings, today ->
            val goal = GoalResolver.goalFor(date, goals)
            val total = entries.sumOf { it.amountMl.toLong() } // derived from entries, Long
            DayUiState(
                loading = false,
                date = date,
                isToday = date == today,
                isFuture = date.isAfter(today),
                goalMl = goal,
                mosaic = MosaicCalculator.build(goal, settings.tileSizeMl, total),
                entries = entries,
                settings = settings,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUiState())

    fun refreshToday() = c.todayClock.refresh()

    fun quickAdd(amountMl: Int, activationId: Long) {
        if (!guard.tryBegin(activationId)) return
        viewModelScope.launch {
            try {
                val id = c.repository.addNow(amountMl)
                val action = UndoAction.Added(id, amountMl)
                undoSlot.offer(action)
                messages.send(DayMessage("Added ${Format.ml(amountMl)}", action))
            } finally {
                guard.end()
            }
        }
    }

    fun delete(entryId: Long) {
        viewModelScope.launch {
            val removed = c.repository.deleteEntry(entryId) ?: return@launch
            val action = UndoAction.Deleted(removed)
            undoSlot.offer(action)
            messages.send(DayMessage("Deleted ${Format.ml(removed.amountMl)} entry", action))
        }
    }

    /** Undo restores or removes the exact entry once. */
    fun undo(expected: UndoAction) {
        if (undoSlot.peek() != expected) return
        val action = undoSlot.take() ?: return
        viewModelScope.launch {
            when (action) {
                is UndoAction.Added -> c.repository.removeAddedEntry(action.id)
                is UndoAction.Deleted -> c.repository.restoreEntry(action.entry)
            }
        }
    }

    /** Called when the snackbar for [expected] goes away without Undo. */
    fun undoExpired(expected: UndoAction) {
        if (undoSlot.peek() == expected) undoSlot.clear()
    }
}
