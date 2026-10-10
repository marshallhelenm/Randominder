package io.github.marshallhelenm.randominder.ui.reminders

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

sealed interface DaysDescription {
    data object EveryDay : DaysDescription
    data object Weekdays : DaysDescription
    data object Weekends : DaysDescription
    data object Never : DaysDescription
    data class Listed(val names: List<String>) : DaysDescription
}

private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** The week in the order the user's locale shows it (Sunday first in the US, Monday in most of Europe). */
fun orderedWeek(locale: Locale): List<DayOfWeek> {
    val first = WeekFields.of(locale).firstDayOfWeek
    return (0L until 7L).map { first.plus(it) }
}

fun describeDays(days: Set<DayOfWeek>, locale: Locale): DaysDescription = when {
    days.isEmpty() -> DaysDescription.Never
    days.size == 7 -> DaysDescription.EveryDay
    days == DayOfWeek.entries.toSet() - WEEKEND -> DaysDescription.Weekdays
    days == WEEKEND -> DaysDescription.Weekends
    else -> DaysDescription.Listed(
        orderedWeek(locale).filter { it in days }.map { it.getDisplayName(TextStyle.SHORT, locale) }
    )
}
