package io.github.marshallhelenm.randominder.scheduling

import io.github.marshallhelenm.randominder.model.Reminder
import io.github.marshallhelenm.randominder.model.ReminderMessage
import io.github.marshallhelenm.randominder.model.Sound
import io.github.marshallhelenm.randominder.model.SoundSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.random.Random

class RandomPicksTest {
    private val reminder = Reminder(
        id = 1, name = "Drink water",
        windowStart = LocalTime.of(9, 0), windowEnd = LocalTime.of(17, 0),
        pingCount = 3, daysOfWeek = setOf(DayOfWeek.MONDAY)
    )

    private fun messages(vararg texts: String) =
        texts.mapIndexed { i, t -> ReminderMessage(id = i + 1L, reminderId = 1, text = t) }

    @Test
    fun pickAvoidingRepeat_neverRepeatsWhenThereIsAnAlternative() {
        repeat(200) { seed ->
            assertNotEquals("b", RandomPicks.pickAvoidingRepeat(listOf("a", "b", "c"), "b", Random(seed)))
        }
    }

    @Test
    fun pickAvoidingRepeat_reachesEveryOtherOption() {
        val seen = (0 until 200).map { RandomPicks.pickAvoidingRepeat(listOf("a", "b", "c"), "b", Random(it)) }.toSet()
        assertEquals(setOf("a", "c"), seen)
    }

    @Test
    fun pickAvoidingRepeat_singleOption_repeatsIt() {
        assertEquals("a", RandomPicks.pickAvoidingRepeat(listOf("a"), "a", Random(1)))
    }

    @Test
    fun pickAvoidingRepeat_empty_isNull() {
        assertNull(RandomPicks.pickAvoidingRepeat(emptyList<String>(), null, Random(1)))
    }

    @Test
    fun messageText_noOptions_fallsBackToReminderName() {
        assertEquals("Drink water", RandomPicks.messageText(reminder, emptyList(), null, Random(1)))
    }

    @Test
    fun messageText_duplicateTexts_stillAvoidRepeat() {
        val pool = messages("Sip!", "Sip!", "Hydrate")
        repeat(100) { seed ->
            assertEquals("Hydrate", RandomPicks.messageText(reminder, pool, "Sip!", Random(seed)))
        }
    }

    @Test
    fun messageText_picksFromThePool() {
        val pool = messages("Sip!", "Hydrate", "Glass of water?")
        val seen = (0 until 200).map { RandomPicks.messageText(reminder, pool, null, Random(it)) }.toSet()
        assertEquals(setOf("Sip!", "Hydrate", "Glass of water?"), seen)
    }

    @Test
    fun sound_emptyPool_isNullForDefault() {
        assertNull(RandomPicks.sound(emptyList(), Random(1)))
    }

    @Test
    fun sound_picksFromPool() {
        val pool = listOf(
            Sound(id = 1, displayName = "Chime", uri = "android.resource://x/1", source = SoundSource.BUNDLED),
            Sound(id = 2, displayName = "Bird", uri = "content://media/2", source = SoundSource.IMPORTED)
        )
        val seen = (0 until 100).mapNotNull { RandomPicks.sound(pool, Random(it))?.id }.toSet()
        assertTrue(seen == setOf(1L, 2L))
    }
}
