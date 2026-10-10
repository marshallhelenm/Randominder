package io.github.marshallhelenm.randominder.ui.reminders

import io.github.marshallhelenm.randominder.model.Reminder
import io.github.marshallhelenm.randominder.model.ReminderMessage
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

enum class FormError { NAME_MISSING, WINDOW_EMPTY, NO_DAYS }

/** What the edit screen shows and changes; becomes a [Reminder] plus its messages on save. */
data class ReminderForm(
    val name: String = "",
    val enabled: Boolean = true,
    val windowStart: LocalTime = LocalTime.of(9, 0),
    val windowEnd: LocalTime = LocalTime.of(17, 0),
    val pingCount: Int = 3,
    val daysOfWeek: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val messages: List<String> = emptyList()
) {
    val errors: Set<FormError>
        get() = buildSet {
            if (name.isBlank()) add(FormError.NAME_MISSING)
            // Windows stay within one day, so the end has to come after the start.
            if (!windowEnd.isAfter(windowStart)) add(FormError.WINDOW_EMPTY)
            if (daysOfWeek.isEmpty()) add(FormError.NO_DAYS)
        }

    /**
     * When the phone is idle, Android only lets an app's alarms wake it about once every 9 minutes, so
     * pings averaging closer than [CLOSE_PING_SPACING] may arrive late or together.
     */
    val pingsMayBunch: Boolean
        get() = windowEnd.isAfter(windowStart) &&
            Duration.between(windowStart, windowEnd).dividedBy(pingCount.coerceIn(PING_RANGE).toLong()) < CLOSE_PING_SPACING

    /** Blank lines are dropped rather than saved as empty pings. */
    val savedMessages: List<String> get() = messages.map(String::trim).filter(String::isNotEmpty)

    /** Ignores edits that wouldn't change what gets saved, like trailing spaces or an empty message line. */
    fun differsFrom(other: ReminderForm): Boolean = normalized() != other.normalized()

    private fun normalized() = copy(name = name.trim(), messages = savedMessages)

    fun toReminder(id: Long, lastMessage: String?) = Reminder(
        id = id,
        name = name.trim(),
        enabled = enabled,
        windowStart = windowStart,
        windowEnd = windowEnd,
        pingCount = pingCount.coerceIn(PING_RANGE),
        daysOfWeek = daysOfWeek,
        lastMessage = lastMessage
    )

    companion object {
        val PING_RANGE = 1..20
        val CLOSE_PING_SPACING: Duration = Duration.ofMinutes(10)

        fun from(reminder: Reminder, messages: List<ReminderMessage>) = ReminderForm(
            name = reminder.name,
            enabled = reminder.enabled,
            windowStart = reminder.windowStart,
            windowEnd = reminder.windowEnd,
            pingCount = reminder.pingCount,
            daysOfWeek = reminder.daysOfWeek,
            messages = messages.map { it.text }
        )
    }
}
