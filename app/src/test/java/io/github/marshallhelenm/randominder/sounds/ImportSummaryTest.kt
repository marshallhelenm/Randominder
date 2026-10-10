package io.github.marshallhelenm.randominder.sounds

import io.github.marshallhelenm.randominder.model.Sound
import io.github.marshallhelenm.randominder.model.SoundSource
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportSummaryTest {
    private val added = ImportResult.Added(Sound(displayName = "Bird", uri = "content://media/1", source = SoundSource.IMPORTED))

    @Test
    fun countsEachOutcome() {
        val summary = ImportSummary.of(listOf(added, ImportResult.TooLong, added, ImportResult.NotAudio, ImportResult.TooLong))

        assertEquals(ImportSummary(added = 2, tooLong = 2, notAudio = 1), summary)
        assertEquals(3, summary.skipped)
    }

    @Test
    fun emptyBatch_isAllZeros() {
        assertEquals(ImportSummary(added = 0, tooLong = 0, notAudio = 0), ImportSummary.of(emptyList()))
    }
}
