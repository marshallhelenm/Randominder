package io.github.marshallhelenm.randominder.scheduling

import io.github.marshallhelenm.randominder.model.Reminder
import io.github.marshallhelenm.randominder.model.ReminderMessage
import io.github.marshallhelenm.randominder.model.Sound
import kotlin.random.Random

object RandomPicks {
    /** A random option, skipping [previous] when there's anything else to choose; null if [options] is empty. */
    fun <T> pickAvoidingRepeat(options: List<T>, previous: T?, random: Random): T? {
        if (options.isEmpty()) return null
        val candidates = options.filter { it != previous }.ifEmpty { options }
        return candidates.random(random)
    }

    /** Falls back to the reminder's name when it has no text options. */
    fun messageText(
        reminder: Reminder,
        messages: List<ReminderMessage>,
        previousText: String?,
        random: Random
    ): String = pickAvoidingRepeat(messages.map { it.text }.distinct(), previousText, random) ?: reminder.name

    /** Null means use the default notification sound. */
    fun sound(pool: List<Sound>, random: Random): Sound? = pool.randomOrNull(random)
}
