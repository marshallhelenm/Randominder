package io.github.marshallhelenm.randominder.ui.reminders

import io.github.marshallhelenm.randominder.model.Reminder
import io.github.marshallhelenm.randominder.model.ReminderMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class ReminderFormTest {
    private val valid = ReminderForm(name = "Stretch")

    @Test
    fun defaults_withAName_haveNoErrors() {
        assertEquals(emptySet<FormError>(), valid.errors)
    }

    @Test
    fun blankName_isAnError() {
        assertEquals(setOf(FormError.NAME_MISSING), valid.copy(name = "   ").errors)
    }

    @Test
    fun endNotAfterStart_isAnError() {
        val same = valid.copy(windowStart = LocalTime.of(9, 0), windowEnd = LocalTime.of(9, 0))
        val reversed = valid.copy(windowStart = LocalTime.of(17, 0), windowEnd = LocalTime.of(9, 0))
        assertEquals(setOf(FormError.WINDOW_EMPTY), same.errors)
        assertEquals(setOf(FormError.WINDOW_EMPTY), reversed.errors)
    }

    @Test
    fun noDays_isAnError() {
        assertEquals(setOf(FormError.NO_DAYS), valid.copy(daysOfWeek = emptySet()).errors)
    }

    @Test
    fun errorsAccumulate() {
        val form = ReminderForm(name = "", windowEnd = LocalTime.of(8, 0), daysOfWeek = emptySet())
        assertEquals(setOf(FormError.NAME_MISSING, FormError.WINDOW_EMPTY, FormError.NO_DAYS), form.errors)
    }

    @Test
    fun savedMessages_trimsAndDropsBlanks() {
        val form = valid.copy(messages = listOf("  Reach up ", "", "   ", "Touch toes"))
        assertEquals(listOf("Reach up", "Touch toes"), form.savedMessages)
    }

    @Test
    fun toReminder_trimsName_clampsPingCount_keepsLastMessage() {
        val reminder = valid.copy(name = " Stretch ", pingCount = 99).toReminder(id = 5, lastMessage = "Reach up")

        assertEquals(5L, reminder.id)
        assertEquals("Stretch", reminder.name)
        assertEquals(ReminderForm.PING_RANGE.last, reminder.pingCount)
        assertEquals("Reach up", reminder.lastMessage)
    }

    @Test
    fun differsFrom_realEditsCount() {
        assertEquals(true, valid.copy(name = "Stretch more").differsFrom(valid))
        assertEquals(true, valid.copy(pingCount = 5).differsFrom(valid))
        assertEquals(true, valid.copy(daysOfWeek = setOf(DayOfWeek.MONDAY)).differsFrom(valid))
        assertEquals(true, valid.copy(messages = listOf("Reach up")).differsFrom(valid))
    }

    @Test
    fun differsFrom_ignoresChangesThatWouldNotBeSaved() {
        assertEquals(false, valid.copy(name = "Stretch  ").differsFrom(valid))
        assertEquals(false, valid.copy(messages = listOf("", "  ")).differsFrom(valid))
        assertEquals(false, valid.copy(messages = listOf("Reach up ")).differsFrom(valid.copy(messages = listOf("Reach up"))))
    }

    @Test
    fun pingsMayBunch_whenAverageSpacingIsUnderTenMinutes() {
        val hour = valid.copy(windowStart = LocalTime.of(9, 0), windowEnd = LocalTime.of(10, 0))
        assertEquals(false, hour.copy(pingCount = 6).pingsMayBunch) // exactly 10 minutes apart
        assertEquals(true, hour.copy(pingCount = 7).pingsMayBunch)
        assertEquals(false, valid.copy(pingCount = 20).pingsMayBunch) // 9-5 with 20 pings: 24 minutes apart
    }

    @Test
    fun pingsMayBunch_ignoresAnEmptyWindow() {
        // The window error covers this case; no second warning on top.
        assertEquals(false, valid.copy(windowStart = LocalTime.of(10, 0), windowEnd = LocalTime.of(9, 0)).pingsMayBunch)
    }

    @Test
    fun from_roundTripsThroughToReminder() {
        val reminder = Reminder(
            id = 3, name = "Water", enabled = false,
            windowStart = LocalTime.of(8, 30), windowEnd = LocalTime.of(20, 15),
            pingCount = 6, daysOfWeek = setOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY), lastMessage = "Sip"
        )
        val messages = listOf(ReminderMessage(id = 1, reminderId = 3, text = "Sip"), ReminderMessage(id = 2, reminderId = 3, text = "Glass"))

        val form = ReminderForm.from(reminder.copy(useAllSounds = false), messages, listOf(4L, 9L))

        assertEquals(listOf("Sip", "Glass"), form.messages)
        assertEquals(setOf(4L, 9L), form.chosenSoundIds)
        assertEquals(reminder.copy(useAllSounds = false), form.toReminder(id = 3, lastMessage = "Sip"))
    }

    @Test
    fun choosingSoundsButPickingNone_isAnError() {
        assertEquals(setOf(FormError.NO_SOUNDS), valid.copy(useAllSounds = false).errors)
        assertEquals(emptySet<FormError>(), valid.copy(useAllSounds = false, chosenSoundIds = setOf(1L)).errors)
        assertEquals(emptySet<FormError>(), valid.copy(useAllSounds = true).errors)
    }

    @Test
    fun differsFrom_ignoresChoicesKeptWhileAllSoundsIsOn() {
        // Flipping to "choose", ticking a sound, then back to "all" saves nothing different.
        assertEquals(false, valid.copy(chosenSoundIds = setOf(3L)).differsFrom(valid))
        assertEquals(true, valid.copy(useAllSounds = false, chosenSoundIds = setOf(3L)).differsFrom(valid))
    }
}
