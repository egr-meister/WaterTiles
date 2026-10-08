package com.egrmeister.watertiles.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egrmeister.watertiles.AppContainer
import com.egrmeister.watertiles.domain.entries.EntryValidation
import com.egrmeister.watertiles.domain.entries.EntryValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class EntryDraft(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val amountText: String = "",
    val date: LocalDate = LocalDate.MIN,
    val time: LocalTime = LocalTime.NOON,
    val amountError: String? = null,
    val dateTimeError: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val missing: Boolean = false,
    val dirty: Boolean = false,
)

/** Draft lives in SavedStateHandle so it survives rotation and process recreation. */
class EntryEditorViewModel(
    private val c: AppContainer,
    private val handle: SavedStateHandle,
    private val entryId: Long?,
    initialDate: LocalDate,
) : ViewModel() {

    private val _state = MutableStateFlow(EntryDraft())
    val state: StateFlow<EntryDraft> = _state.asStateFlow()

    init {
        val restored = handle.get<String>(K_DATE)
        if (restored != null) {
            _state.value = EntryDraft(
                loaded = true,
                isNew = entryId == null,
                amountText = handle.get<String>(K_AMOUNT).orEmpty(),
                date = LocalDate.parse(restored),
                time = LocalTime.parse(handle.get<String>(K_TIME) ?: "12:00"),
                dirty = handle.get<Boolean>(K_DIRTY) ?: false,
            )
        } else if (entryId == null) {
            val today = c.time.today()
            val time = if (initialDate == today) c.time.localTimeNow().withSecond(0).withNano(0) else LocalTime.NOON
            setLoaded(EntryDraft(loaded = true, isNew = true, date = if (initialDate.isAfter(today)) today else initialDate, time = time))
        } else {
            viewModelScope.launch {
                val e = c.repository.getEntry(entryId)
                if (e == null) {
                    _state.value = EntryDraft(loaded = true, missing = true)
                } else {
                    setLoaded(
                        EntryDraft(
                            loaded = true,
                            isNew = false,
                            amountText = e.amountMl.toString(),
                            date = e.localDate,
                            time = e.localTime.withSecond(0).withNano(0),
                        ),
                    )
                }
            }
        }
    }

    private fun setLoaded(d: EntryDraft) {
        _state.value = d
        persist(d)
    }

    private fun persist(d: EntryDraft) {
        handle[K_AMOUNT] = d.amountText
        handle[K_DATE] = d.date.toString()
        handle[K_TIME] = d.time.toString()
        handle[K_DIRTY] = d.dirty
    }

    private fun edit(transform: (EntryDraft) -> EntryDraft) {
        _state.update { transform(it).copy(dirty = true) }
        persist(_state.value)
    }

    fun onAmount(text: String) = edit { it.copy(amountText = text.filter { ch -> ch.isDigit() }.take(5), amountError = null) }
    fun onDate(date: LocalDate) = edit { it.copy(date = date, dateTimeError = null) }
    fun onTime(time: LocalTime) = edit { it.copy(time = time, dateTimeError = null) }

    fun save() {
        val d = _state.value
        if (d.saving || !d.loaded || d.missing) return
        when (val v = EntryValidator.validate(d.amountText, d.date, d.time, c.time.zone(), c.time.now())) {
            is EntryValidation.Invalid -> _state.update { it.copy(amountError = v.amountError, dateTimeError = v.dateTimeError) }
            is EntryValidation.Valid -> {
                _state.update { it.copy(saving = true) }
                viewModelScope.launch {
                    if (entryId == null) {
                        c.repository.addEntry(v.amountMl, v.zoned)
                    } else {
                        c.repository.updateEntry(entryId, v.amountMl, v.zoned)
                    }
                    _state.update { it.copy(saving = false, saved = true, dirty = false) }
                }
            }
        }
    }

    fun today(): LocalDate = c.time.today()

    private companion object {
        const val K_AMOUNT = "draft_amount"
        const val K_DATE = "draft_date"
        const val K_TIME = "draft_time"
        const val K_DIRTY = "draft_dirty"
    }
}
