package com.egrmeister.watertiles.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.egrmeister.watertiles.MainActivity
import com.egrmeister.watertiles.R
import com.egrmeister.watertiles.core.TimeProvider
import com.egrmeister.watertiles.domain.reminders.ReminderPolicy
import com.egrmeister.watertiles.domain.reminders.ReminderSettings

/**
 * Inexact one-shot AlarmManager scheduling for the next eligible reminder slot.
 * No exact-alarm permission, no foreground service, no wake lock.
 */
class ReminderScheduler(private val context: Context, private val time: TimeProvider) {

    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)

    fun reschedule(settings: ReminderSettings) {
        cancelAlarm()
        if (!settings.enabled) return
        val next = ReminderPolicy.nextSlot(settings, time.now(), time.zone()) ?: return
        val trigger = next.toInstant().toEpochMilli()
        // Inexact: the system may batch or delay delivery.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, alarmIntent(trigger))
    }

    fun cancelAll() {
        cancelAlarm()
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun cancelAlarm() {
        alarms.cancel(alarmIntent(0L))
    }

    private fun alarmIntent(slotMillis: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .putExtra(EXTRA_SLOT, slotMillis)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_REMIND = "com.egrmeister.watertiles.action.REMIND"
        const val EXTRA_SLOT = "slot_epoch_millis"
        const val CHANNEL_ID = "water_reminders"
        const val NOTIFICATION_ID = 1001
        private const val REQUEST_ALARM = 10
        private const val REQUEST_OPEN = 11
        const val EXTRA_OPEN_TODAY = "open_today"

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.reminder_channel_description) }
                nm.createNotificationChannel(channel)
            }
        }

        fun hasPostPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        /** App-level and channel-level availability, including the runtime permission. */
        fun canPost(context: Context): Boolean {
            if (!hasPostPermission(context)) return false
            val nmc = NotificationManagerCompat.from(context)
            if (!nmc.areNotificationsEnabled()) return false
            val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID)
            return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
        }

        fun post(context: Context) {
            ensureChannel(context)
            if (!canPost(context)) return
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_TODAY, true)
            val content = PendingIntent.getActivity(
                context,
                REQUEST_OPEN,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.reminder_title))
                .setContentText(context.getString(R.string.reminder_body))
                .setContentIntent(content)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
            try {
                // Single notification id: a new reminder replaces the previous one.
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
                // Permission revoked between the check and the post; the tracker keeps working.
            }
        }
    }
}
