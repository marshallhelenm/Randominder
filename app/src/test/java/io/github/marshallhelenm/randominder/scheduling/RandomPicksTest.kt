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
    fun nextMessage_noOptions_isNull() {
        assertNull(RandomPicks.nextMessage(emptyList(), null, Random(1)))
    }

    @Test
    fun nextMessage_onlyPicksFromMessagesNotYetShownThisRound() {
        val pool = messages("Sip!", "Hydrate", "Glass of water?").mapIndexed { i, m -> m.copy(shownThisRound = i != 1) }
        repeat(50) { seed ->
            val pick = RandomPicks.nextMessage(pool, null, Random(seed))!!
            assertEquals("Hydrate", pick.message.text)
            assertEquals(false, pick.newRound)
        }
    }

    @Test
    fun nextMessage_allShown_startsANewRoundWithoutRepeatingTheLastOne() {
        val pool = messages("Sip!", "Hydrate", "Glass of water?").map { it.copy(shownThisRound = true) }
        repeat(50) { seed ->
            val pick = RandomPicks.nextMessage(pool, "Hydrate", Random(seed))!!
            assertEquals(true, pick.newRound)
            assertNotEquals("Hydrate", pick.message.text)
        }
    }

    @Test
    fun nextMessage_singleMessage_repeatsEachRound() {
        val pool = messages("Sip!").map { it.copy(shownThisRound = true) }
        val pick = RandomPicks.nextMessage(pool, "Sip!", Random(1))!!
        assertEquals("Sip!", pick.message.text)
        assertEquals(true, pick.newRound)
    }

    @Test
    fun nextMessage_reachesEveryUnseenMessage() {
        val pool = messages("Sip!", "Hydrate", "Glass of water?")
        val seen = (0 until 200).map { RandomPicks.nextMessage(pool, null, Random(it))!!.message.text }.toSet()
        assertEquals(setOf("Sip!", "Hydrate", "Glass of water?"), seen)
    }
}
