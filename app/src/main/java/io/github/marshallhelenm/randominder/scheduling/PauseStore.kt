package io.github.marshallhelenm.randominder.scheduling

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** When all reminders are paused until, in epoch millis; null when they aren't. */
interface PauseStore {
    var pausedUntil: Long?

    fun observe(): Flow<Long?>

    companion object {
        // "Until I turn it back on": no ping can be planned at or after this.
        const val UNTIL_RESUMED = Long.MAX_VALUE
    }
}

class SharedPreferencesPauseStore(context: Context) : PauseStore {
    private val prefs = context.applicationContext.getSharedPreferences("randominder", Context.MODE_PRIVATE)

    override var pausedUntil: Long?
        get() = if (prefs.contains(KEY)) prefs.getLong(KEY, 0) else null
        set(value) = prefs.edit { if (value == null) remove(KEY) else putLong(KEY, value) }

    override fun observe(): Flow<Long?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == KEY) trySend(pausedUntil) }
        trySend(pausedUntil)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private companion object {
        const val KEY = "paused_until"
    }
}
