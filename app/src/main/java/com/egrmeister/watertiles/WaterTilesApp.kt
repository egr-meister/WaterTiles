package com.egrmeister.watertiles

import android.app.Application
import com.egrmeister.watertiles.reminders.ReminderScheduler
import kotlinx.coroutines.launch

class WaterTilesApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderScheduler.ensureChannel(this)
        // Recover the reminder schedule after process start (covers updates and force-stop recovery).
        container.appScope.launch {
            container.reminderScheduler.reschedule(container.settings.current().reminders)
        }
    }
}
