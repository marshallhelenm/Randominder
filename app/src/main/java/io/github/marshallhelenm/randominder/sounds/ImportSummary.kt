package io.github.marshallhelenm.randominder.sounds

/** What a batch import did, so one message can cover several files. */
data class ImportSummary(val added: Int, val tooLong: Int, val notAudio: Int) {
    val skipped: Int get() = tooLong + notAudio

    companion object {
        fun of(results: List<ImportResult>) = ImportSummary(
            added = results.count { it is ImportResult.Added },
            tooLong = results.count { it == ImportResult.TooLong },
            notAudio = results.count { it == ImportResult.NotAudio }
        )
    }
}

/** One of the phone's own notification sounds, as offered in the "Built-in phone sounds" list. */
data class PhoneSound(val title: String, val uri: String)
