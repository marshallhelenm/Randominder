package io.github.marshallhelenm.randominder.scheduling

import android.content.Context
import io.github.marshallhelenm.randominder.data.local.RandominderDatabase
import io.github.marshallhelenm.randominder.model.Reminder
import io.github.marshallhelenm.randominder.model.ScheduledPing
import io.github.marshallhelenm.randominder.model.Sound
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random

data class PingNotification(val reminderId: Long, val title: String, val text: String?, val sound: Sound?)

class PingScheduler(
    db: RandominderDatabase,
    private val alarms: AlarmScheduler,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val random: Random = Random.Default,
    // Read on every plan so a pause takes effect without the scheduler holding state.
    private val pausedUntil: () -> Long? = { null }
) {
    private val reminders = db.reminderDao()
    private val messages = db.reminderMessageDao()
    private val sounds = db.soundDao()
    private val pings = db.scheduledPingDao()

    /** Call after a reminder is created, edited, enabled, disabled or deleted. */
    suspend fun replan(reminderId: Long) {
        // A deleted reminder's pings are already gone with it, so it simply gets an empty plan.
        val plan = reminders.getById(reminderId)?.let { planFrom(it, today()) } ?: emptyList()
        pings.replaceForReminder(reminderId, plan)
        rearm()
    }

    /** Stored instants were computed in the old zone or clock, so every reminder is re-planned. */
    suspend fun replanAll() {
        reminders.getAll().forEach { pings.replaceForReminder(it.id, planFrom(it, today())) }
        rearm()
    }

    /**
     * Re-arms after anything that may have cancelled the alarm: a reboot, an app update, a force-stop or a
     * restore. Safe to call on every launch. Pings overdue by more than [LATE_PING_GRACE] are dropped rather
     * than delivered late in a burst; newer ones fire straight away, since inexact alarms can legitimately
     * run a few minutes late. A reminder left with no pings resumes on its next active day.
     */
    suspend fun resume() {
        pings.deleteDue(clock.millis() - LATE_PING_GRACE.toMillis())
        val stillPlanned = pings.getReminderIdsWithPings().toSet()
        reminders.getEnabled()
            .filter { it.id !in stillPlanned }
            .forEach { pings.insertAll(planFrom(it, today().plusDays(1))) }
        rearm()
    }

    /** Consumes every ping that's due, plans the next day for reminders that just finished theirs, and re-arms. */
    suspend fun takeDuePings(): List<PingNotification> {
        val due = pings.getDue(clock.millis())
        pings.deleteByIds(due.map { it.id })
        val pool = sounds.getEnabled()
        // Several due pings for one reminder (e.g. a late alarm) collapse into a single notification.
        val notifications = due.groupBy { it.reminderId }.mapNotNull { (reminderId, fired) ->
            val reminder = reminders.getById(reminderId) ?: return@mapNotNull null
            if (pings.getForReminder(reminderId).isEmpty()) {
                // Plan from the day after the one that just ended; re-rolling today could add extra pings.
                val endedOn = Instant.ofEpochMilli(fired.maxOf { it.fireAt }).atZone(clock.zone).toLocalDate()
                pings.insertAll(planFrom(reminder, endedOn.plusDays(1)))
            }
            val pick = RandomPicks.nextMessage(messages.getForReminder(reminderId), reminder.lastMessage, random)
            pick?.let {
                messages.recordShown(reminderId, it.message.id, it.newRound)
                reminders.updateLastMessage(reminderId, it.message.text)
            }
            // No sound (empty pool) means the default notification sound.
            PingNotification(reminderId, reminder.name, pick?.message?.text, pool.randomOrNull(random))
        }
        rearm()
        return notifications
    }

    /** An extra ping [SNOOZE] from now, allowed past the reminder's window since the user asked for it. */
    suspend fun snooze(reminderId: Long) {
        reminders.getById(reminderId)?.takeIf { it.enabled } ?: return
        val at = clock.millis() + SNOOZE.toMillis()
        if (at < (pausedUntil() ?: Long.MIN_VALUE)) return
        pings.insertAll(listOf(ScheduledPing(reminderId = reminderId, fireAt = at)))
        rearm()
    }

    suspend fun rearm() {
        val next = pings.getNext()
        if (next == null) alarms.cancel() else alarms.schedule(next.fireAt)
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    // The first active day on or after firstDate that still has pings ahead of now (and of any pause).
    // A week covers every weekday; pausing until resumed leaves nothing to plan.
    private fun planFrom(reminder: Reminder, firstDate: LocalDate): List<ScheduledPing> {
        val notBefore = maxOf(clock.millis(), pausedUntil() ?: Long.MIN_VALUE)
        if (notBefore == PauseStore.UNTIL_RESUMED) return emptyList()
        var date = firstDate
        repeat(8) {
            val activeDay = PingPlanner.nextActiveDate(reminder, date) ?: return emptyList()
            val times = PingPlanner.pingsOn(reminder, activeDay, clock.zone, random).filter { it > notBefore }
            if (times.isNotEmpty()) return times.map { ScheduledPing(reminderId = reminder.id, fireAt = it) }
            date = activeDay.plusDays(1)
        }
        return emptyList()
    }

    companion object {
        val LATE_PING_GRACE: Duration = Duration.ofMinutes(15)
        val SNOOZE: Duration = Duration.ofMinutes(10)

        fun from(context: Context): PingScheduler {
            val pause = SharedPreferencesPauseStore(context)
            return PingScheduler(
                RandominderDatabase.getInstance(context),
                AndroidAlarmScheduler(context.applicationContext),
                pausedUntil = { pause.pausedUntil }
            )
        }
    }
}
