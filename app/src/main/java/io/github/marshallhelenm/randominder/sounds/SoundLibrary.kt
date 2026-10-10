package io.github.marshallhelenm.randominder.sounds

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.annotation.RawRes
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import io.github.marshallhelenm.randominder.R
import io.github.marshallhelenm.randominder.data.local.RandominderDatabase
import io.github.marshallhelenm.randominder.model.Sound
import io.github.marshallhelenm.randominder.model.SoundSource
import io.github.marshallhelenm.randominder.notifications.ReminderNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException

/** Sounds shipped in res/raw. Referenced through R so resource shrinking keeps them. */
data class BundledSound(@param:RawRes val rawRes: Int, val displayName: String)

// Sources and uploaders are recorded in docs/SOUND_CREDITS.md.
val BUNDLED_SOUNDS = listOf(
    BundledSound(R.raw.applause, "Applause"),
    BundledSound(R.raw.boing, "Boing"),
    BundledSound(R.raw.cartoon_1, "Cartoon 1"),
    BundledSound(R.raw.cartoon_2, "Cartoon 2"),
    BundledSound(R.raw.cartoon_4, "Cartoon 3"),
    BundledSound(R.raw.cartoon_jump, "Cartoon Jump"),
    BundledSound(R.raw.cat_meow_1, "Cat Meow 1"),
    BundledSound(R.raw.cat_meow_2, "Cat Meow 2"),
    BundledSound(R.raw.duck_quack, "Duck Quack"),
    BundledSound(R.raw.firework, "Firework"),
    BundledSound(R.raw.losing_horn, "Losing Horn"),
    BundledSound(R.raw.pop_wow, "Pop Wow"),
    BundledSound(R.raw.retro_lose, "Retro Lose"),
    BundledSound(R.raw.rimshot, "Rimshot"),
    BundledSound(R.raw.vocal_funny, "Silly Voice")
)

/** Keeps the sound pool, the files behind it, and the per-sound notification channels in step. */
class SoundLibrary(private val context: Context, db: RandominderDatabase) {
    private val sounds = db.soundDao()
    private val notifier = ReminderNotifier(context)

    // Imports go into shared storage so the system can play them; that needs MediaStore's
    // permission-free writes, which arrived in Android 10.
    val canImport: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun observeAll(): Flow<List<Sound>> = sounds.observeAll()

    /**
     * Adds bundled sounds missing from the pool and drops ones no longer shipped. Safe to call often:
     * existing rows, including whether they're switched on, are left alone.
     */
    suspend fun ensureBundled() {
        val shipped = BUNDLED_SOUNDS.associateBy { bundledUri(it.rawRes) }
        sounds.getAll()
            .filter { it.source == SoundSource.BUNDLED && it.uri !in shipped }
            .forEach { sounds.deleteById(it.id) }
        shipped.forEach { (uri, bundled) ->
            sounds.insert(Sound(displayName = bundled.displayName, uri = uri, source = SoundSource.BUNDLED))
        }
        syncChannels()
    }

    /**
     * Copies [source] into the shared Notifications/Randominder folder and adds it to the pool.
     * Returns null if it isn't audio or can't be copied. The system only plays a channel sound the app
     * itself can read, and an app can always read media entries it created.
     */
    suspend fun import(source: Uri): Sound? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null
        val fileName = displayNameOf(source)
        val target = copyIntoMediaStore(source, fileName ?: "sound") ?: return@withContext null
        val sound = Sound(
            displayName = fileName?.substringBeforeLast('.')?.ifBlank { null } ?: "Imported sound",
            uri = target.toString(),
            source = SoundSource.IMPORTED
        )
        val id = sounds.insert(sound)
        syncChannels()
        sound.copy(id = id)
    }

    suspend fun setEnabled(sound: Sound, enabled: Boolean) = sounds.setEnabled(sound.id, enabled)

    /** Removes an imported sound and its file. Bundled sounds always stay in the pool. */
    suspend fun remove(sound: Sound) = withContext(Dispatchers.IO) {
        if (sound.source == SoundSource.BUNDLED) return@withContext
        sounds.deleteById(sound.id)
        try {
            context.contentResolver.delete(sound.uri.toUri(), null, null)
        } catch (_: SecurityException) {
            // The file no longer belongs to us (e.g. restored from a backup); leave it where it is.
        }
        syncChannels()
    }

    private suspend fun syncChannels() = notifier.syncChannels(sounds.getAll())

    // Stored by resource name rather than id: ids can change between builds.
    private fun bundledUri(@RawRes rawRes: Int) =
        "android.resource://${context.packageName}/raw/${context.resources.getResourceEntryName(rawRes)}"

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun copyIntoMediaStore(source: Uri, fileName: String): Uri? {
        val resolver = context.contentResolver
        // Some sources (file:// URIs, some document providers) report no type, so fall back to the extension.
        val mimeType = (resolver.getType(source) ?: mimeTypeFromName(fileName))?.takeIf { it.startsWith("audio/") }
            ?: return null
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_NOTIFICATIONS}/Randominder")
            put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
            // Hidden from other apps until the copy finishes.
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val target = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openInputStream(source)?.use { input ->
                resolver.openOutputStream(target)?.use { output -> input.copyTo(output) }
            } ?: throw IOException("Can't copy $source")
            resolver.update(target, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            target
        } catch (_: Exception) {
            resolver.delete(target, null, null)
            null
        }
    }

    private fun mimeTypeFromName(fileName: String): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase())

    private fun displayNameOf(source: Uri): String? {
        if (source.scheme == "file") return source.lastPathSegment
        return context.contentResolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }

    companion object {
        fun from(context: Context) =
            SoundLibrary(context.applicationContext, RandominderDatabase.getInstance(context))
    }
}
