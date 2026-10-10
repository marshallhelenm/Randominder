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
    fun from_roundTripsThroughToReminder() {
        val reminder = Reminder(
            id = 3, name = "Water", enabled = false,
            windowStart = LocalTime.of(8, 30), windowEnd = LocalTime.of(20, 15),
            pingCount = 6, daysOfWeek = setOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY), lastMessage = "Sip"
        )
        val messages = listOf(ReminderMessage(id = 1, reminderId = 3, text = "Sip"), ReminderMessage(id = 2, reminderId = 3, text = "Glass"))

        val form = ReminderForm.from(reminder, messages)

        assertEquals(listOf("Sip", "Glass"), form.messages)
        assertEquals(reminder, form.toReminder(id = 3, lastMessage = "Sip"))
    }
}
