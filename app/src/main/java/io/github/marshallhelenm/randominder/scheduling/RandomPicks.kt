package io.github.marshallhelenm.randominder.scheduling

import io.github.marshallhelenm.randominder.model.ReminderMessage
import kotlin.random.Random

data class MessagePick(val message: ReminderMessage, val newRound: Boolean)

object RandomPicks {
    /** A random option, skipping [previous] when there's anything else to choose; null if [options] is empty. */
    fun <T> pickAvoidingRepeat(options: List<T>, previous: T?, random: Random): T? {
        if (options.isEmpty()) return null
        val candidates = options.filter { it != previous }.ifEmpty { options }
        return candidates.random(random)
    }

    /**
     * Shuffle-bag pick: only messages not yet shown this round are eligible, and once all have been shown a
     * new round starts. A new round still avoids [previousText] so the boundary doesn't repeat. Null when the
     * reminder has no text options; its notification then shows just the reminder's name.
     */
    fun nextMessage(messages: List<ReminderMessage>, previousText: String?, random: Random): MessagePick? {
        if (messages.isEmpty()) return null
        val unseen = messages.filterNot { it.shownThisRound }
        val newRound = unseen.isEmpty()
        val pool = if (newRound) messages else unseen
        val pick = pool.filter { it.text != previousText }.ifEmpty { pool }.random(random)
        return MessagePick(pick, newRound)
    }
}
