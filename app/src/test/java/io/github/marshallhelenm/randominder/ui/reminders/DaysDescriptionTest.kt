package io.github.marshallhelenm.randominder.ui.reminders

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.DayOfWeek
import java.util.Locale

class DaysDescriptionTest {
    @Test
    fun orderedWeek_followsTheLocalesFirstDay() {
        assertEquals(SUNDAY, orderedWeek(Locale.US).first())
        assertEquals(MONDAY, orderedWeek(Locale.UK).first())
        assertEquals(DayOfWeek.entries.toSet(), orderedWeek(Locale.US).toSet())
    }

    @Test
    fun namedPatterns() {
        assertEquals(DaysDescription.EveryDay, describeDays(DayOfWeek.entries.toSet(), Locale.US))
        assertEquals(DaysDescription.Weekdays, describeDays(DayOfWeek.entries.toSet() - setOf(SATURDAY, SUNDAY), Locale.US))
        assertEquals(DaysDescription.Weekends, describeDays(setOf(SATURDAY, SUNDAY), Locale.US))
        assertEquals(DaysDescription.Never, describeDays(emptySet(), Locale.US))
    }

    @Test
    fun otherSets_listShortNamesInLocaleWeekOrder() {
        val days = setOf(FRIDAY, SUNDAY, WEDNESDAY)
        assertEquals(DaysDescription.Listed(listOf("Sun", "Wed", "Fri")), describeDays(days, Locale.US))
        assertEquals(DaysDescription.Listed(listOf("Wed", "Fri", "Sun")), describeDays(days, Locale.UK))
    }
}
