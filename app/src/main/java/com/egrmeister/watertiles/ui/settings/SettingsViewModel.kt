package com.egrmeister.watertiles.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egrmeister.watertiles.AppContainer
import com.egrmeister.watertiles.data.local.AppSettings
import com.egrmeister.watertiles.data.local.AppTheme
import com.egrmeister.watertiles.domain.goals.GoalApply
import com.egrmeister.watertiles.domain.goals.GoalChange
import com.egrmeister.watertiles.domain.goals.GoalResolver
import com.egrmeister.watertiles.domain.reminders.ReminderSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val todayGoal: Int? = null,
    val pendingGoal: GoalChange? = null,
)

class SettingsViewModel(private val c: AppContainer) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        c.settings.settings,
        c.repository.observeGoalChanges(),
        c.todayClock.today,
    ) { settings, goals, today ->
        SettingsUiState(
            loading = false,
            settings = settings,
            todayGoal = GoalResolver.goalFor(today, goals),
            pendingGoal = GoalResolver.pendingAfter(today, goals),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setGoal(goalMl: Int, apply: GoalApply) = viewModelScope.launch {
        c.repository.setGoal(goalMl, GoalResolver.effectiveDateFor(apply, c.time.today()))
    }

    fun setTileSize(ml: Int) = viewModelScope.launch { c.settings.setTileSize(ml) }
    fun setTheme(theme: AppTheme) = viewModelScope.launch { c.settings.setTheme(theme) }
    fun setReducedAnimation(v: Boolean) = viewModelScope.launch { c.settings.setReducedAnimation(v) }
    fun setQuickPortions(values: List<Int>) = viewModelScope.launch { c.settings.setQuickPortions(values) }

    fun setReminders(r: ReminderSettings) = viewModelScope.launch {
        c.settings.setReminders(r)
        c.reminderScheduler.reschedule(r)
    }

    fun clearAll(onDone: () -> Unit) = viewModelScope.launch {
        c.clearAllData()
        onDone()
    }
}
