package io.github.marshallhelenm.randominder.scheduling

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.marshallhelenm.randominder.data.local.RandominderDatabase
import io.github.marshallhelenm.randominder.notifications.ReminderNotifier
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
            val due = PingScheduler.from(app).takeDuePings()
            if (due.isEmpty()) return@runAsync
            val notifier = ReminderNotifier(app)
            notifier.syncChannels(RandominderDatabase.getInstance(app).soundDao().getAll())
            due.forEach(notifier::show)
        }
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduler = PingScheduler.from(context.applicationContext)
        runAsync {
            when (intent.action) {
                // Alarms don't survive a reboot or an app update; the saved plan does.
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> scheduler.resumeAfterBoot()
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> scheduler.replanAll()
                // Switch the armed alarm between exact and inexact.
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> scheduler.rearm()
            }
        }
    }
}
