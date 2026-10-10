package io.github.marshallhelenm.randominder.scheduling

import io.github.marshallhelenm.randominder.model.ReminderMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class RandomPicksTest {
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
    fun messageText_noOptions_isNull() {
        assertNull(RandomPicks.messageText(emptyList(), null, Random(1)))
    }

    @Test
    fun messageText_duplicateTexts_stillAvoidRepeat() {
        val pool = messages("Sip!", "Sip!", "Hydrate")
        repeat(100) { seed ->
            assertEquals("Hydrate", RandomPicks.messageText(pool, "Sip!", Random(seed)))
        }
    }

    @Test
    fun messageText_picksFromThePool() {
        val pool = messages("Sip!", "Hydrate", "Glass of water?")
        val seen = (0 until 200).map { RandomPicks.messageText(pool, null, Random(it)) }.toSet()
        assertEquals(setOf("Sip!", "Hydrate", "Glass of water?"), seen)
    }
}
