package io.github.marshallhelenm.randominder.sounds

import androidx.annotation.StringRes
import io.github.marshallhelenm.randominder.R
import io.github.marshallhelenm.randominder.model.Sound

// Display groups for the sound pool, in the order they're listed.
enum class SoundGroup(@param:StringRes val label: Int) {
    ANIMALS(R.string.sound_group_animals),
    CARTOON(R.string.sound_group_cartoon),
    GAME(R.string.sound_group_game),
    OUT_AND_ABOUT(R.string.sound_group_out_and_about),
    PHONE(R.string.sound_group_phone),
    YOURS(R.string.sound_group_yours)
}

data class SoundSection(val group: SoundGroup, val sounds: List<Sound>)
