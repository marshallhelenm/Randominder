package io.github.marshallhelenm.randominder.ui.sounds

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import androidx.core.net.toUri
import io.github.marshallhelenm.randominder.model.Sound

/** Plays one sound at a time; starting another stops the last. */
class SoundPreviewer(private val context: Context) {
    private var playing: Ringtone? = null

    fun play(sound: Sound) = play(sound.uri)

    fun play(uri: String) {
        stop()
        playing = RingtoneManager.getRingtone(context, uri.toUri())?.also { it.play() }
    }

    fun stop() {
        playing?.stop()
        playing = null
    }
}
