package com.egrmeister.watertiles.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.egrmeister.watertiles.domain.reminders.ReminderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

enum class AppTheme(val label: String) { LAGOON("Lagoon"), LAVENDER("Lavender"), MIDNIGHT("Midnight") }

data class AppSettings(
    val setupDone: Boolean = false,
    val tileSizeMl: Int = 100,
    val theme: AppTheme = AppTheme.LAGOON,
    val quickPortions: List<Int> = DEFAULT_PORTIONS,
    val reminders: ReminderSettings = ReminderSettings(),
    val reducedAnimation: Boolean = false,
) {
    companion object {
        val DEFAULT_PORTIONS = listOf(100, 200, 250, 500)
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Display preferences and lightweight state. Goals and entries live in Room. */
class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    private object Keys {
        val setupDone = booleanPreferencesKey("setup_done")
        val tileSize = intPreferencesKey("tile_size_ml")
        val theme = stringPreferencesKey("theme")
        val portions = listOf(
            intPreferencesKey("portion_1"),
            intPreferencesKey("portion_2"),
            intPreferencesKey("portion_3"),
            intPreferencesKey("portion_4"),
        )
        val remindersEnabled = booleanPreferencesKey("reminders_enabled")
        val reminderStart = intPreferencesKey("reminder_start_min")
        val reminderEnd = intPreferencesKey("reminder_end_min")
        val reminderInterval = intPreferencesKey("reminder_interval_min")
        val reducedAnimation = booleanPreferencesKey("reduced_animation")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        val defaults = AppSettings()
        AppSettings(
            setupDone = p[Keys.setupDone] ?: false,
            tileSizeMl = (p[Keys.tileSize] ?: defaults.tileSizeMl).takeIf { it == 100 || it == 200 } ?: 100,
            theme = p[Keys.theme]?.let { name -> AppTheme.entries.firstOrNull { it.name == name } } ?: AppTheme.LAGOON,
            quickPortions = Keys.portions.mapIndexed { i, k -> p[k] ?: AppSettings.DEFAULT_PORTIONS[i] },
            reminders = ReminderSettings(
                enabled = p[Keys.remindersEnabled] ?: false,
                start = minutesToTime(p[Keys.reminderStart] ?: (9 * 60)),
                end = minutesToTime(p[Keys.reminderEnd] ?: (21 * 60)),
                intervalMinutes = (p[Keys.reminderInterval] ?: 120)
                    .takeIf { it in ReminderSettings.INTERVALS } ?: 120,
            ),
            reducedAnimation = p[Keys.reducedAnimation] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun completeSetup(tileSizeMl: Int, theme: AppTheme) = store.edit {
        it[Keys.tileSize] = tileSizeMl
        it[Keys.theme] = theme.name
        it[Keys.remindersEnabled] = false
        it[Keys.setupDone] = true
    }

    suspend fun setTileSize(ml: Int) = store.edit { it[Keys.tileSize] = ml }
    suspend fun setTheme(theme: AppTheme) = store.edit { it[Keys.theme] = theme.name }
    suspend fun setReducedAnimation(value: Boolean) = store.edit { it[Keys.reducedAnimation] = value }

    suspend fun setQuickPortions(values: List<Int>) = store.edit { p ->
        values.take(4).forEachIndexed { i, v -> p[Keys.portions[i]] = v }
    }

    suspend fun setReminders(r: ReminderSettings) = store.edit {
        it[Keys.remindersEnabled] = r.enabled
        it[Keys.reminderStart] = r.start.toSecondOfDay() / 60
        it[Keys.reminderEnd] = r.end.toSecondOfDay() / 60
        it[Keys.reminderInterval] = r.intervalMinutes
    }

    suspend fun clear() = store.edit { it.clear() }

    private fun minutesToTime(m: Int): LocalTime = LocalTime.of((m / 60).coerceIn(0, 23), (m % 60).coerceIn(0, 59))
}
