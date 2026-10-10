package io.github.marshallhelenm.randominder.scheduling

import io.github.marshallhelenm.randominder.model.Reminder
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

object PingPlanner {
    /**
     * Picks [count] times in [start, end), one in each equal slice of the window. Purely uniform picks
     * can bunch several pings into a few minutes; one per slice keeps them random but spread out.
     * Returns nothing for an empty window or a non-positive count, since this runs from alarm
     * callbacks where throwing would crash the app in the background.
     */
    fun timesInWindow(start: LocalTime, end: LocalTime, count: Int, random: Random): List<LocalTime> {
        if (count < 1 || !end.isAfter(start)) return emptyList()
        val windowSeconds = Duration.between(start, end).seconds
        // Second precision keeps every slice at least one second wide, so times are strictly increasing.
        val pings = count.toLong().coerceAtMost(windowSeconds).toInt()
        val sliceSeconds = windowSeconds.toDouble() / pings
        return (0 until pings).map { i ->
            val sliceStart = (i * sliceSeconds).toLong()
            val sliceEnd = ((i + 1) * sliceSeconds).toLong()
            start.plusSeconds(random.nextLong(sliceStart, sliceEnd))
        }
    }

    /** Epoch-millis firing times for [reminder] on [date]; empty if it doesn't run that weekday. */
    fun pingsOn(reminder: Reminder, date: LocalDate, zone: ZoneId, random: Random): List<Long> {
        if (!reminder.enabled || date.dayOfWeek !in reminder.daysOfWeek) return emptyList()
        return timesInWindow(reminder.windowStart, reminder.windowEnd, reminder.pingCount, random)
            .map { date.atTime(it).atZone(zone).toInstant().toEpochMilli() }
    }

    /** The first date on or after [from] that [reminder] runs, or null if it never does. */
    fun nextActiveDate(reminder: Reminder, from: LocalDate): LocalDate? {
        if (!reminder.enabled || reminder.daysOfWeek.isEmpty()) return null
        return generateSequence(from) { it.plusDays(1) }
            .take(7)
            .first { it.dayOfWeek in reminder.daysOfWeek }
    }
}
