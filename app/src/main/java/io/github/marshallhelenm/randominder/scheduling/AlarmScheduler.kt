package io.github.marshallhelenm.randominder.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.AlarmManagerCompat

// Only one alarm is ever armed: the earliest pending ping. Each firing arms the next.
interface AlarmScheduler {
    fun schedule(atMillis: Long)
    fun cancel()
}

class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(atMillis: Long) {
        // Without the exact-alarm grant Android may hold the alarm back several minutes, but it still arrives.
        if (AlarmManagerCompat.canScheduleExactAlarms(alarmManager)) {
            AlarmManagerCompat.setExactAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, atMillis, pendingIntent())
        } else {
            AlarmManagerCompat.setAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, atMillis, pendingIntent())
        }
    }

    override fun cancel() = alarmManager.cancel(pendingIntent())

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, PingAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
