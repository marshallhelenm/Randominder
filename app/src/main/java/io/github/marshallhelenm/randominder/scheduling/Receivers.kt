package io.github.marshallhelenm.randominder.scheduling

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import io.github.marshallhelenm.randominder.notifications.ReminderNotifier
import io.github.marshallhelenm.randominder.sounds.SoundLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Receivers must finish within ~10s; goAsync keeps the process alive while Room work runs off the main thread.
private fun BroadcastReceiver.runAsync(work: suspend () -> Unit) {
    val result = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            work()
        } finally {
            result.finish()
        }
    }
}

class PingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        runAsync {
            val notifier = ReminderNotifier(app)
            PingScheduler.from(app).takeDuePings().forEach(notifier::show)
        }
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val scheduler = PingScheduler.from(app)
        runAsync {
            when (intent.action) {
                // Alarms don't survive a reboot or an app update; the saved plan does.
                Intent.ACTION_BOOT_COMPLETED -> scheduler.resume()
                Intent.ACTION_MY_PACKAGE_REPLACED -> {
                    // An update can add or drop bundled sounds, and pings may fire before the app is opened.
                    SoundLibrary.from(app).ensureBundled()
                    scheduler.resume()
                }
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> scheduler.replanAll()
                // Switch the armed alarm between exact and inexact.
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> scheduler.rearm()
            }
        }
    }
}

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderNotifier.EXTRA_REMINDER_ID, -1).takeIf { it >= 0 } ?: return
        val app = context.applicationContext
        NotificationManagerCompat.from(app).cancel(ReminderNotifier.notificationIdFor(reminderId))
        if (intent.action == ReminderNotifier.ACTION_SNOOZE) {
            runAsync { PingScheduler.from(app).snooze(reminderId) }
        }
    }
}
