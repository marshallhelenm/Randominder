package io.github.marshallhelenm.randominder.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.marshallhelenm.randominder.model.Reminder
import io.github.marshallhelenm.randominder.model.ReminderMessage
import io.github.marshallhelenm.randominder.model.ScheduledPing
import io.github.marshallhelenm.randominder.model.Sound
import io.github.marshallhelenm.randominder.model.SoundSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class RandominderDatabaseTest {
    private lateinit var db: RandominderDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RandominderDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() = db.close()

    private fun reminder(id: Long, days: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)) = Reminder(
        id = id, name = "R$id",
        windowStart = LocalTime.of(9, 30), windowEnd = LocalTime.of(17, 15),
        pingCount = 3, daysOfWeek = days
    )

    @Test
    fun reminder_roundTripsTimesAndWeekdays() = runBlocking {
        db.reminderDao().upsert(reminder(1))
        db.reminderDao().upsert(reminder(2, days = emptySet()))

        assertEquals(reminder(1), db.reminderDao().getById(1))
        assertEquals(emptySet<DayOfWeek>(), db.reminderDao().getById(2)!!.daysOfWeek)
    }

    @Test
    fun deletingReminder_cascadesToItsMessagesAndPingsOnly() = runBlocking {
        db.reminderDao().upsert(reminder(1))
        db.reminderDao().upsert(reminder(2))
        db.reminderMessageDao().insert(ReminderMessage(reminderId = 1, text = "one"))
        db.reminderMessageDao().insert(ReminderMessage(reminderId = 2, text = "two"))
        db.scheduledPingDao().insertAll(listOf(ScheduledPing(reminderId = 1, fireAt = 100)))
        db.scheduledPingDao().insertAll(listOf(ScheduledPing(reminderId = 2, fireAt = 200)))

        db.reminderDao().deleteById(1)

        assertEquals(emptyList<ReminderMessage>(), db.reminderMessageDao().getForReminder(1))
        assertEquals(listOf("two"), db.reminderMessageDao().getForReminder(2).map { it.text })
        assertEquals(emptyList<ScheduledPing>(), db.scheduledPingDao().getForReminder(1))
        assertEquals(listOf(200L), db.scheduledPingDao().getForReminder(2).map { it.fireAt })
    }

    @Test
    fun getNext_returnsEarliestPingAcrossReminders() = runBlocking {
        db.reminderDao().upsert(reminder(1))
        db.reminderDao().upsert(reminder(2))
        db.scheduledPingDao().insertAll(
            listOf(
                ScheduledPing(reminderId = 1, fireAt = 500),
                ScheduledPing(reminderId = 2, fireAt = 300),
                ScheduledPing(reminderId = 1, fireAt = 900)
            )
        )

        assertEquals(300L, db.scheduledPingDao().getNext()!!.fireAt)
    }

    @Test
    fun getNext_emptyTable_isNull() = runBlocking {
        assertNull(db.scheduledPingDao().getNext())
    }

    @Test
    fun replaceForReminder_swapsOnlyThatRemindersPlan() = runBlocking {
        db.reminderDao().upsert(reminder(1))
        db.reminderDao().upsert(reminder(2))
        db.scheduledPingDao().insertAll(
            listOf(ScheduledPing(reminderId = 1, fireAt = 100), ScheduledPing(reminderId = 2, fireAt = 150))
        )

        db.scheduledPingDao().replaceForReminder(
            1,
            listOf(ScheduledPing(reminderId = 1, fireAt = 400), ScheduledPing(reminderId = 1, fireAt = 600))
        )

        assertEquals(listOf(400L, 600L), db.scheduledPingDao().getForReminder(1).map { it.fireAt })
        assertEquals(listOf(150L), db.scheduledPingDao().getForReminder(2).map { it.fireAt })
    }

    @Test
    fun insertingSameSoundUriTwice_keepsOneRow() = runBlocking {
        val chime = Sound(displayName = "Chime", uri = "content://media/external/audio/media/7", source = SoundSource.IMPORTED)
        val firstId = db.soundDao().insert(chime)
        val secondId = db.soundDao().insert(chime.copy(displayName = "Chime again"))

        assertEquals(-1L, secondId)
        assertEquals(listOf(firstId), db.soundDao().getAll().map { it.id })
    }
}
