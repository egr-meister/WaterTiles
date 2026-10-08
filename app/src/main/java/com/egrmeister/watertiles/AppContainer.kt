package com.egrmeister.watertiles

import android.content.Context
import com.egrmeister.watertiles.core.SystemTimeProvider
import com.egrmeister.watertiles.core.TimeProvider
import com.egrmeister.watertiles.core.TodayClock
import com.egrmeister.watertiles.data.local.RoomTransactionRunner
import com.egrmeister.watertiles.data.local.SettingsStore
import com.egrmeister.watertiles.data.local.WaterDatabase
import com.egrmeister.watertiles.data.repository.WaterRepository
import com.egrmeister.watertiles.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency injection. */
class AppContainer(context: Context, val time: TimeProvider = SystemTimeProvider) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database: WaterDatabase by lazy { WaterDatabase.create(context) }
    val settings = SettingsStore(context)
    val repository: WaterRepository by lazy {
        WaterRepository(database.entryDao(), database.goalDao(), RoomTransactionRunner(database), time)
    }
    val reminderScheduler = ReminderScheduler(context.applicationContext, time)
    val todayClock = TodayClock(time, appScope)

    /** Clears every user record and preference, cancels reminders. */
    suspend fun clearAllData() {
        reminderScheduler.cancelAll()
        repository.clearAll()
        settings.clear()
    }
}
