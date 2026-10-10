package io.github.marshallhelenm.randominder.scheduling

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.marshallhelenm.randominder.data.local.RandominderDatabase
import io.github.marshallhelenm.randominder.model.Reminder
import io.github.marshallhelenm.randominder.model.ReminderMessage
import io.github.marshallhelenm.randominder.model.ScheduledPing
import io.github.marshallhelenm.randominder.model.Sound
import io.github.marshallhelenm.randominder.model.SoundSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class PingSchedulerTest {
    private val zone = ZoneId.of("America/New_York")
    // 2026-10-12 is a Monday.
    private val monday = LocalDate.of(2026, 10, 12)
    private lateinit var db: RandominderDatabase
    private val alarms = FakeAlarms()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RandominderDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() = db.close()

    private fun schedulerAt(at: LocalDateTime, zone: ZoneId = this.zone, seed: Int = 1) =
        PingScheduler(db, alarms, Clock.fixed(at.atZone(zone).toInstant(), zone), Random(seed))

    private fun millis(at: LocalDateTime, zone: ZoneId = this.zone) = at.atZone(zone).toInstant().toEpochMilli()

    private fun dateOf(ping: ScheduledPing, zone: ZoneId = this.zone) =
        Instant.ofEpochMilli(ping.fireAt).atZone(zone).toLocalDate()

    private suspend fun addReminder(
        days: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        count: Int = 4,
        enabled: Boolean = true
    ): Reminder {
        val reminder = Reminder(
            id = 1, name = "Stretch", enabled = enabled,
            windowStart = LocalTime.of(9, 0), windowEnd = LocalTime.of(17, 0),
            pingCount = count, daysOfWeek = days
        )
        db.reminderDao().upsert(reminder)
        return reminder
    }

    private suspend fun planned() = db.scheduledPingDao().getForReminder(1)

    @Test
    fun replan_beforeWindow_plansAllOfTodayAndArmsTheEarliest() = runBlocking {
        addReminder()
        schedulerAt(monday.atTime(8, 0)).replan(1)

        val plan = planned()
        assertEquals(4, plan.size)
        assertTrue(plan.all { dateOf(it) == monday })
        assertEquals(plan.minOf { it.fireAt }, alarms.armedAt)
    }

    @Test
    fun replan_midWindow_keepsOnlyPingsStillAhead() = runBlocking {
        addReminder()
        val now = monday.atTime(13, 0)
        schedulerAt(now).replan(1)

        val plan = planned()
        // Slices are 9-11, 11-13, 13-15, 15-17; only the last two can still be ahead of 13:00.
        assertTrue(plan.size in 1..2)
        assertTrue(plan.all { it.fireAt > millis(now) && dateOf(it) == monday })
    }

    @Test
    fun replan_afterWindow_movesToNextActiveDay() = runBlocking {
        addReminder()
        schedulerAt(monday.atTime(18, 0)).replan(1)

        val plan = planned()
        assertEquals(4, plan.size)
        assertTrue(plan.all { dateOf(it) == monday.plusDays(2) })
    }

    @Test
    fun replan_disabled_clearsPlanAndCancelsAlarm() = runBlocking {
        addReminder()
        schedulerAt(monday.atTime(8, 0)).replan(1)
        addReminder(enabled = false)
        schedulerAt(monday.atTime(8, 0)).replan(1)

        assertEquals(emptyList<ScheduledPing>(), planned())
        assertNull(alarms.armedAt)
    }

    @Test
    fun takeDuePings_returnsDuePingAndArmsTheNext() = runBlocking {
        addReminder()
        db.scheduledPingDao().insertAll(
            listOf(
                ScheduledPing(reminderId = 1, fireAt = millis(monday.atTime(10, 0))),
                ScheduledPing(reminderId = 1, fireAt = millis(monday.atTime(12, 0)))
            )
        )

        val fired = schedulerAt(monday.atTime(10, 0, 1)).takeDuePings()

        assertEquals(listOf(1L), fired.map { it.reminderId })
        assertEquals(listOf(millis(monday.atTime(12, 0))), planned().map { it.fireAt })
        assertEquals(millis(monday.atTime(12, 0)), alarms.armedAt)
    }

    @Test
    fun takeDuePings_lastPingOfDay_plansNextActiveDayNotToday() = runBlocking {
        addReminder()
        db.scheduledPingDao().insertAll(listOf(ScheduledPing(reminderId = 1, fireAt = millis(monday.atTime(10, 0)))))

        // Fired well before 17:00, so re-rolling today would still find slots ahead; it must not.
        schedulerAt(monday.atTime(10, 0, 1)).takeDuePings()

        val plan = planned()
        assertEquals(4, plan.size)
        assertTrue(plan.all { dateOf(it) == monday.plusDays(2) })
        assertEquals(plan.minOf { it.fireAt }, alarms.armedAt)
    }

    @Test
    fun takeDuePings_severalDueForOneReminder_collapseToOneNotification() = runBlocking {
        addReminder()
        db.scheduledPingDao().insertAll(
            listOf(
                ScheduledPing(reminderId = 1, fireAt = millis(monday.atTime(10, 0))),
                ScheduledPing(reminderId = 1, fireAt = millis(monday.atTime(11, 0)))
            )
        )

        val fired = schedulerAt(monday.atTime(11, 30)).takeDuePings()

        assertEquals(1, fired.size)
    }

    @Test
    fun takeDuePings_avoidsRepeatingAndRemembersTheLastMessage() = runBlocking {
        addReminder()
        db.reminderMessageDao().insert(ReminderMessage(reminderId = 1, text = "Reach up"))
        db.reminderMessageDao().insert(ReminderMessage(reminderId = 1, text = "Touch toes"))
        var previous: String? = null
        repeat(6) { i ->
            db.scheduledPingDao().insertAll(listOf(ScheduledPing(reminderId = 1, fireAt = millis(monday.atTime(9, i)))))
            val text = schedulerAt(monday.atTime(9, i, 1), seed = i).takeDuePings().single().text
            if (previous != null) assertNotEquals(previous, text)
            assertEquals(text, db.reminderDao().getById(1)!!.lastMessage)
            previous = text
        }
    }

    @Test
    fun takeDuePings_emptyPools_noBodyTextAndDefaultSound() = runBlocking {
        addReminder()
        db.scheduledPingDao().insertAll(listOf(ScheduledPing(reminderId = 1, fireAt = millis(monday.atTime(10, 0)))))

        val ping = schedulerAt(monday.atTime(10, 1)).takeDuePings().single()

        assertEquals("Stretch", ping.title)
        assertNull(ping.text)
        assertNull(db.reminderDao().getById(1)!!.lastMessage)
        assertNull(ping.sound)
    }

    @Test
    fun takeDuePings_drawsSoundFromThePool() = runBlocking {
        addReminder()
        db.soundDao().insert(Sound(displayName = "Chime", uri = "content://media/1", source = SoundSource.IMPORTED))
        db.scheduledPingDao().insertAll(listOf(ScheduledPing(reminderId = 1, fireAt = millis(monday.atTime(10, 0)))))

        val ping = schedulerAt(monday.atTime(10, 1)).takeDuePings().single()

        assertEquals("Chime", ping.sound?.displayName)
    }

    @Test
    fun takeDuePings_neverPicksASwitchedOffSound() = runBlocking {
        addReminder()
        db.soundDao().insert(Sound(displayName = "On", uri = "content://media/1", source = SoundSource.IMPORTED))
        db.soundDao().insert(Sound(displayName = "Off", uri = "content://media/2", source = SoundSource.IMPORTED, enabled = false))
        repeat(20) { i ->
            db.scheduledPingDao().insertAll(listOf(ScheduledPing(reminderId = 1, fireAt = millis(monday.atTime(9, i)))))
            val ping = schedulerAt(monday.atTime(9, i, 1), seed = i).takeDuePings().single()
            assertEquals("On", ping.sound?.displayName)
        }
    }

    @Test
    fun takeDuePings_everySoundSwitchedOff_usesDefaultSound() = runBlocking {
        addReminder()
        db.soundDao().insert(Sound(displayName = "Off", uri = "content://media/2", source = SoundSource.IMPORTED, enabled = false))
        db.scheduledPingDao().insertAll(listOf(ScheduledPing(reminderId = 1, fireAt = millis(monday.atTime(10, 0)))))

        assertNull(schedulerAt(monday.atTime(10, 1)).takeDuePings().single().sound)
    }

    @Test
    fun takeDuePings_nothingDue_returnsNothingAndKeepsPlan() = runBlocking {
        addReminder()
        db.scheduledPingDao().insertAll(listOf(ScheduledPing(reminderId = 1, fireAt = millis(monday.atTime(10, 0)))))

        val fired = schedulerAt(monday.atTime(9, 59)).takeDuePings()

        assertEquals(emptyList<PingNotification>(), fired)
        assertEquals(1, planned().size)
        assertEquals(millis(monday.atTime(10, 0)), alarms.armedAt)
    }

    @Test
    fun resume_dropsLongMissedPingsAndKeepsTheRest() = runBlocking {
        addReminder()
        db.scheduledPingDao().insertAll(
            listOf(
                ScheduledPing(reminderId = 1, fireAt = millis(monday.atTime(10, 0))),
                ScheduledPing(reminderId = 1, fireAt = millis(monday.atTime(15, 0)))
            )
        )

        schedulerAt(monday.atTime(12, 0)).resume()

        assertEquals(listOf(millis(monday.atTime(15, 0))), planned().map { it.fireAt })
        assertEquals(millis(monday.atTime(15, 0)), alarms.armedAt)
    }

    @Test
    fun resume_allPingsLongMissed_resumesOnNextActiveDay() = runBlocking {
        addReminder()
        db.scheduledPingDao().insertAll(listOf(ScheduledPing(reminderId = 1, fireAt = millis(monday.atTime(10, 0)))))

        schedulerAt(monday.atTime(12, 0)).resume()

        val plan = planned()
        assertEquals(4, plan.size)
        assertTrue(plan.all { dateOf(it) == monday.plusDays(2) })
    }

    @Test
    fun resume_slightlyLatePing_isKeptAndFiresRightAway() = runBlocking {
        addReminder()
        val late = millis(monday.atTime(10, 0))
        db.scheduledPingDao().insertAll(listOf(ScheduledPing(reminderId = 1, fireAt = late)))

        // Inside the grace period: an inexact alarm may simply not have been delivered yet.
        schedulerAt(monday.atTime(10, 10)).resume()

        assertEquals(listOf(late), planned().map { it.fireAt })
        assertEquals(late, alarms.armedAt)
    }

    @Test
    fun resume_isSafeToRepeat() = runBlocking {
        addReminder()
        schedulerAt(monday.atTime(8, 0)).replan(1)
        val before = planned()

        repeat(3) { schedulerAt(monday.atTime(8, 30)).resume() }

        assertEquals(before, planned())
        assertEquals(before.minOf { it.fireAt }, alarms.armedAt)
    }

    @Test
    fun resume_afterAlarmsWereCancelled_rearmsTheNextPing() = runBlocking {
        addReminder()
        schedulerAt(monday.atTime(8, 0)).replan(1)
        // What a force-stop does: the plan stays in the database but the alarm is gone.
        alarms.cancel()

        schedulerAt(monday.atTime(8, 5)).resume()

        assertEquals(planned().minOf { it.fireAt }, alarms.armedAt)
    }

    @Test
    fun replanAll_afterZoneChange_placesPingsInTheNewZonesWindow() = runBlocking {
        addReminder()
        schedulerAt(monday.atTime(8, 0)).replan(1)
        val tokyo = ZoneId.of("Asia/Tokyo")

        schedulerAt(monday.atTime(8, 0), zone = tokyo).replanAll()

        val windowStart = millis(monday.atTime(9, 0), tokyo)
        val windowEnd = millis(monday.atTime(17, 0), tokyo)
        val plan = planned()
        assertEquals(4, plan.size)
        assertTrue(plan.all { it.fireAt in windowStart until windowEnd })
    }
}
