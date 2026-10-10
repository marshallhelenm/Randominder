package io.github.marshallhelenm.randominder.scheduling

import io.github.marshallhelenm.randominder.model.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

class PingPlannerTest {
    private val nine = LocalTime.of(9, 0)
    private val five = LocalTime.of(17, 0)

    private fun reminder(
        days: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        enabled: Boolean = true,
        count: Int = 4
    ) = Reminder(
        id = 1, name = "Stretch", enabled = enabled,
        windowStart = nine, windowEnd = five, pingCount = count, daysOfWeek = days
    )

    @Test
    fun timesInWindow_putsOnePingInEachSlice() {
        repeat(200) { seed ->
            val times = PingPlanner.timesInWindow(nine, five, 4, Random(seed))

            assertEquals(4, times.size)
            // An 8-hour window in 4 slices: ping i must fall in [9 + 2i, 9 + 2(i+1)) hours.
            times.forEachIndexed { i, t ->
                val sliceStart = nine.plusHours(2L * i)
                val sliceEnd = nine.plusHours(2L * (i + 1))
                assertTrue("ping $i at $t (seed $seed)", !t.isBefore(sliceStart) && t.isBefore(sliceEnd))
            }
        }
    }

    @Test
    fun timesInWindow_variesWithTheRandomSource() {
        val plans = (0 until 20).map { PingPlanner.timesInWindow(nine, five, 3, Random(it)) }.toSet()
        assertTrue("expected different plans, got ${plans.size}", plans.size > 15)
    }

    @Test
    fun timesInWindow_moreRequestedThanSecondsAvailable_capsAtOnePerSecondStrictlyIncreasing() {
        val start = LocalTime.of(12, 0)
        val times = PingPlanner.timesInWindow(start, start.plusSeconds(5), 50, Random(1))

        assertEquals(5, times.size)
        assertEquals(times.sorted().distinct(), times)
    }

    @Test
    fun timesInWindow_invalidInput_returnsNothing() {
        assertEquals(emptyList<LocalTime>(), PingPlanner.timesInWindow(five, nine, 3, Random(1)))
        assertEquals(emptyList<LocalTime>(), PingPlanner.timesInWindow(nine, nine, 3, Random(1)))
        assertEquals(emptyList<LocalTime>(), PingPlanner.timesInWindow(nine, five, 0, Random(1)))
    }

    @Test
    fun pingsOn_activeDay_convertsTimesInTheGivenZone() {
        val monday = LocalDate.of(2026, 10, 12)
        val zone = ZoneId.of("America/New_York")

        val pings = PingPlanner.pingsOn(reminder(count = 2), monday, zone, Random(7))

        assertEquals(2, pings.size)
        val windowStart = monday.atTime(nine).atZone(zone).toInstant()
        val windowEnd = monday.atTime(five).atZone(zone).toInstant()
        pings.forEach {
            val at = Instant.ofEpochMilli(it)
            assertTrue(!at.isBefore(windowStart) && at.isBefore(windowEnd))
        }
    }

    @Test
    fun pingsOn_dayNotSelected_returnsNothing() {
        val tuesday = LocalDate.of(2026, 10, 13)
        assertEquals(emptyList<Long>(), PingPlanner.pingsOn(reminder(), tuesday, ZoneOffset.UTC, Random(1)))
    }

    @Test
    fun pingsOn_disabled_returnsNothing() {
        val monday = LocalDate.of(2026, 10, 12)
        assertEquals(
            emptyList<Long>(),
            PingPlanner.pingsOn(reminder(enabled = false), monday, ZoneOffset.UTC, Random(1))
        )
    }

    @Test
    fun pingsOn_springForwardDay_keepsWindowLength() {
        // US clocks jump 2:00 -> 3:00 on 2026-03-08; a 9-5 window is unaffected and stays 8 real hours.
        val sunday = LocalDate.of(2026, 3, 8)
        val zone = ZoneId.of("America/New_York")
        val pings = PingPlanner.pingsOn(reminder(days = setOf(DayOfWeek.SUNDAY), count = 1), sunday, zone, Random(3))
        val windowStart = sunday.atTime(nine).atZone(zone).toInstant()
        val offset = Duration.between(windowStart, Instant.ofEpochMilli(pings.single()))
        assertTrue(offset >= Duration.ZERO && offset < Duration.ofHours(8))
    }

    @Test
    fun nextActiveDate_returnsFromDateWhenItIsActive() {
        val monday = LocalDate.of(2026, 10, 12)
        assertEquals(monday, PingPlanner.nextActiveDate(reminder(), monday))
    }

    @Test
    fun nextActiveDate_skipsAheadToNextSelectedDay_acrossTheWeekBoundary() {
        val thursday = LocalDate.of(2026, 10, 15)
        assertEquals(LocalDate.of(2026, 10, 19), PingPlanner.nextActiveDate(reminder(), thursday))
    }

    @Test
    fun nextActiveDate_noDaysOrDisabled_isNull() {
        val monday = LocalDate.of(2026, 10, 12)
        assertNull(PingPlanner.nextActiveDate(reminder(days = emptySet()), monday))
        assertNull(PingPlanner.nextActiveDate(reminder(enabled = false), monday))
    }
}
