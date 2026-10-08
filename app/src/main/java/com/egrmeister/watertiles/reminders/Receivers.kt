package com.egrmeister.watertiles.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.egrmeister.watertiles.WaterTilesApp
import com.egrmeister.watertiles.domain.reminders.ReminderDecision
import com.egrmeister.watertiles.domain.reminders.ReminderPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Fires for a scheduled slot, re-checks eligibility, posts at most one notification, reschedules. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMIND) return
        val app = context.applicationContext as WaterTilesApp
        runAsync {
            val c = app.container
            val settings = c.settings.current()
            val slot = Instant.ofEpochMilli(intent.getLongExtra(ReminderScheduler.EXTRA_SLOT, 0L))
            val now = c.time.now()
            val zone = c.time.zone()
            val today = c.time.today()
            val decision = ReminderPolicy.decide(
                s = settings.reminders,
                scheduledFor = slot,
                now = now,
                zone = zone,
                todayGoalMl = c.repository.goalFor(today),
                todayTotalMl = c.repository.totalFor(today),
                lastEntryAt = c.repository.latestEntryAt(),
            )
            if (decision == ReminderDecision.Post && settings.setupDone) {
                ReminderScheduler.post(context)
            }
            // Always schedule only the next future slot; missed slots are never replayed.
            c.reminderScheduler.reschedule(settings.reminders)
        }
    }
}

/** Reboot, app update, time and time-zone changes: recompute the next slot. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> Unit
            else -> return
        }
        val app = context.applicationContext as WaterTilesApp
        runAsync {
            val c = app.container
            c.todayClock.refresh()
            c.reminderScheduler.reschedule(c.settings.current().reminders)
        }
    }
}
