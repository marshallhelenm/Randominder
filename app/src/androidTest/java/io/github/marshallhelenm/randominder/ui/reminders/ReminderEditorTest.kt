package io.github.marshallhelenm.randominder.ui.reminders

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.marshallhelenm.randominder.data.local.RandominderDatabase
import io.github.marshallhelenm.randominder.model.ReminderMessage
import io.github.marshallhelenm.randominder.model.ScheduledPing
import io.github.marshallhelenm.randominder.model.Sound
import io.github.marshallhelenm.randominder.model.SoundSource
import io.github.marshallhelenm.randominder.scheduling.FakeAlarms
import io.github.marshallhelenm.randominder.scheduling.FakePauseStore
import io.github.marshallhelenm.randominder.scheduling.PingScheduler
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class ReminderEditorTest {
    private val zone = ZoneId.of("America/New_York")
    private val now = LocalDate.of(2026, 10, 12).atTime(8, 0).atZone(zone).toInstant()
    private val alarms = FakeAlarms()
    private val pause = FakePauseStore()
    private lateinit var db: RandominderDatabase
    private lateinit var editor: ReminderEditor

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RandominderDatabase::class.java
        ).build()
        val clock = Clock.fixed(now, zone)
        editor = ReminderEditor(db, PingScheduler(db, alarms, clock, Random(1)) { pause.pausedUntil }, pause, clock)
    }

    @After
    fun tearDown() = db.close()

    private val form = ReminderForm(name = "Stretch", pingCount = 3, messages = listOf("Reach up", " ", "Touch toes"))

    private suspend fun pings(id: Long): List<ScheduledPing> = db.scheduledPingDao().getForReminder(id)

    @Test
    fun saveNew_storesReminderAndMessages_andPlansPings() = runBlocking {
        val id = editor.save(null, form)

        assertEquals("Stretch", db.reminderDao().getById(id)!!.name)
        assertEquals(listOf("Reach up", "Touch toes"), db.reminderMessageDao().getForReminder(id).map { it.text })
        assertEquals(3, pings(id).size)
        assertEquals(pings(id).minOf { it.fireAt }, alarms.armedAt)
    }

    @Test
    fun saveExisting_updatesInPlace_replacesMessages_keepsLastMessage() = runBlocking {
        val id = editor.save(null, form)
        db.reminderDao().updateLastMessage(id, "Reach up")

        val sameId = editor.save(id, form.copy(name = "Stretch more", pingCount = 5, messages = listOf("Twist")))

        assertEquals(id, sameId)
        val reminder = db.reminderDao().getById(id)!!
        assertEquals("Stretch more", reminder.name)
        assertEquals("Reach up", reminder.lastMessage)
        assertEquals(listOf("Twist"), db.reminderMessageDao().getForReminder(id).map { it.text })
        assertEquals(5, pings(id).size)
        assertEquals(1, db.reminderDao().getAll().size)
    }

    @Test
    fun loadReturnsWhatWasSaved() = runBlocking {
        val id = editor.save(null, form.copy(windowStart = LocalTime.of(10, 15)))

        val loaded = editor.load(id)!!

        assertEquals(form.copy(windowStart = LocalTime.of(10, 15), messages = form.savedMessages), loaded)
    }

    @Test
    fun chosenSounds_roundTrip_andSwitchingBackToAllClearsThem() = runBlocking {
        val bird = db.soundDao().insert(Sound(displayName = "Bird", uri = "content://media/1", source = SoundSource.IMPORTED))
        val id = editor.save(null, form.copy(useAllSounds = false, chosenSoundIds = setOf(bird)))

        val loaded = editor.load(id)!!
        assertEquals(false, loaded.useAllSounds)
        assertEquals(setOf(bird), loaded.chosenSoundIds)

        editor.save(id, loaded.copy(useAllSounds = true))
        assertEquals(emptyList<Long>(), db.reminderSoundDao().getSoundIds(id))
        assertEquals(true, db.reminderDao().getById(id)!!.useAllSounds)
    }

    @Test
    fun loadMissing_isNull() = runBlocking {
        assertNull(editor.load(42))
    }

    @Test
    fun switchingOff_clearsPingsAndCancelsAlarm_switchingOnReplans() = runBlocking {
        val id = editor.save(null, form)

        editor.setEnabled(db.reminderDao().getById(id)!!, false)
        assertEquals(emptyList<ScheduledPing>(), pings(id))
        assertNull(alarms.armedAt)

        editor.setEnabled(db.reminderDao().getById(id)!!, true)
        assertEquals(3, pings(id).size)
        assertTrue(alarms.armedAt != null)
    }

    @Test
    fun pauseUntilTomorrow_thenResume_restoresTodaysPings() = runBlocking {
        val id = editor.save(null, form)
        val today = LocalDate.of(2026, 10, 12)

        editor.pauseUntilTomorrow()
        assertTrue(pings(id).isNotEmpty())
        assertTrue(pings(id).all { Instant.ofEpochMilli(it.fireAt).atZone(zone).toLocalDate() == today.plusDays(1) })

        editor.resumeAll()
        assertNull(pause.pausedUntil)
        assertTrue(pings(id).all { Instant.ofEpochMilli(it.fireAt).atZone(zone).toLocalDate() == today })
    }

    @Test
    fun pauseUntilResumed_clearsEverything_untilResumed() = runBlocking {
        val id = editor.save(null, form)

        editor.pauseUntilResumed()
        assertEquals(emptyList<ScheduledPing>(), pings(id))
        assertNull(alarms.armedAt)

        editor.resumeAll()
        assertEquals(3, pings(id).size)
    }

    @Test
    fun delete_removesEverything_andCancelsAlarm() = runBlocking {
        val id = editor.save(null, form)

        editor.delete(id)

        assertNull(db.reminderDao().getById(id))
        assertEquals(emptyList<ReminderMessage>(), db.reminderMessageDao().getForReminder(id))
        assertEquals(emptyList<ScheduledPing>(), pings(id))
        assertNull(alarms.armedAt)
    }
}
