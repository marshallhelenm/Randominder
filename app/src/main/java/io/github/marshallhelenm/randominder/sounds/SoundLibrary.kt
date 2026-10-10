package io.github.marshallhelenm.randominder.sounds

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Duration

/** Sounds shipped in res/raw. Referenced through R so resource shrinking keeps them. */
data class BundledSound(@param:RawRes val rawRes: Int, val displayName: String, val group: SoundGroup)

// Sources and uploaders are recorded in docs/SOUND_CREDITS.md.
val BUNDLED_SOUNDS = listOf(
    BundledSound(R.raw.animal_squeak, "Animal Squeak", SoundGroup.ANIMALS),
    BundledSound(R.raw.antelope, "Antelope", SoundGroup.ANIMALS),
    BundledSound(R.raw.applause, "Applause", SoundGroup.OUT_AND_ABOUT),
    BundledSound(R.raw.boing, "Boing 1", SoundGroup.CARTOON),
    BundledSound(R.raw.boing_2, "Boing 2", SoundGroup.CARTOON),
    BundledSound(R.raw.bubble_pop, "Bubble Pop 1", SoundGroup.CARTOON),
    BundledSound(R.raw.bubble_pop_2, "Bubble Pop 2", SoundGroup.CARTOON),
    BundledSound(R.raw.bubble_pop_3, "Bubble Pop 3", SoundGroup.CARTOON),
    BundledSound(R.raw.car_honk, "Car Honk", SoundGroup.OUT_AND_ABOUT),
    BundledSound(R.raw.cartoon_1, "Cartoon 1", SoundGroup.CARTOON),
    BundledSound(R.raw.cartoon_2, "Cartoon 2", SoundGroup.CARTOON),
    BundledSound(R.raw.cartoon_4, "Cartoon 3", SoundGroup.CARTOON),
    BundledSound(R.raw.cartoon_hearts, "Cartoon Hearts", SoundGroup.CARTOON),
    BundledSound(R.raw.cartoon_jump, "Cartoon Jump", SoundGroup.CARTOON),
    BundledSound(R.raw.cartoon_spin, "Cartoon Spin", SoundGroup.CARTOON),
    BundledSound(R.raw.cat_meow_1, "Cat Meow 1", SoundGroup.ANIMALS),
    BundledSound(R.raw.cat_meow_2, "Cat Meow 2", SoundGroup.ANIMALS),
    BundledSound(R.raw.cat_meow_3, "Cat Meow 3", SoundGroup.ANIMALS),
    BundledSound(R.raw.chicken, "Chicken", SoundGroup.ANIMALS),
    BundledSound(R.raw.donkey_bray, "Donkey Bray", SoundGroup.ANIMALS),
    BundledSound(R.raw.duck_quack, "Duck Quack 1", SoundGroup.ANIMALS),
    BundledSound(R.raw.duck_quack_2, "Duck Quack 2", SoundGroup.ANIMALS),
    BundledSound(R.raw.elephant, "Elephant", SoundGroup.ANIMALS),
    BundledSound(R.raw.firework, "Firework", SoundGroup.OUT_AND_ABOUT),
    BundledSound(R.raw.game_upgrade, "Game Upgrade", SoundGroup.GAME),
    BundledSound(R.raw.goat, "Goat", SoundGroup.ANIMALS),
    BundledSound(R.raw.horse_neigh, "Horse Neigh", SoundGroup.ANIMALS),
    BundledSound(R.raw.losing_horn, "Losing Horn", SoundGroup.GAME),
    BundledSound(R.raw.meadowlark, "Meadowlark", SoundGroup.ANIMALS),
    BundledSound(R.raw.monkey, "Monkey", SoundGroup.ANIMALS),
    BundledSound(R.raw.mouse, "Mouse", SoundGroup.ANIMALS),
    BundledSound(R.raw.playful_failure, "Playful Failure", SoundGroup.GAME),
    BundledSound(R.raw.pop_wow, "Pop Wow", SoundGroup.CARTOON),
    BundledSound(R.raw.retro_lose, "Retro Lose", SoundGroup.GAME),
    BundledSound(R.raw.rimshot, "Rimshot", SoundGroup.GAME),
    BundledSound(R.raw.rising_pop, "Rising Pop", SoundGroup.CARTOON),
    BundledSound(R.raw.rooster, "Rooster", SoundGroup.ANIMALS),
    BundledSound(R.raw.sheep_bleat, "Sheep Bleat 1", SoundGroup.ANIMALS),
    BundledSound(R.raw.sheep_bleat_2, "Sheep Bleat 2", SoundGroup.ANIMALS),
    BundledSound(R.raw.snowy_owl, "Snowy Owl", SoundGroup.ANIMALS),
    BundledSound(R.raw.squeaky_ringer, "Squeaky Ringer", SoundGroup.CARTOON),
    BundledSound(R.raw.squeaky_toy, "Squeaky Toy 1", SoundGroup.CARTOON),
    BundledSound(R.raw.squeaky_toy_2, "Squeaky Toy 2", SoundGroup.CARTOON),
    BundledSound(R.raw.train_whistle, "Train Whistle", SoundGroup.OUT_AND_ABOUT),
    BundledSound(R.raw.vocal_funny, "Silly Voice", SoundGroup.CARTOON),
    BundledSound(R.raw.wolf_howl, "Wolf Howl", SoundGroup.ANIMALS),
    BundledSound(R.raw.yippee, "Yippee", SoundGroup.CARTOON)
)

sealed interface ImportResult {
    data class Added(val sound: Sound) : ImportResult
    data object NotAudio : ImportResult
    data object TooLong : ImportResult
}

/** Keeps the sound pool, the files behind it, and the per-sound notification channels in step. */
class SoundLibrary(private val context: Context, db: RandominderDatabase) {
    private val sounds = db.soundDao()
    private val notifier = ReminderNotifier(context)

    // Imports go into shared storage so the system can play them; that needs MediaStore's
    // permission-free writes, which arrived in Android 10.
    val canImport: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun observeAll(): Flow<List<Sound>> = sounds.observeAll()

    fun observeSections(): Flow<List<SoundSection>> = sounds.observeAll().map(::sections)

    /** The pool split into its display groups, in group order; empty groups are left out. */
    fun sections(pool: List<Sound>): List<SoundSection> {
        val bundledGroups = BUNDLED_SOUNDS.associate { bundledUri(it.rawRes) to it.group }
        val byGroup = pool.groupBy {
            when (it.source) {
                // A bundled row no longer shipped is dropped by ensureBundled; until then, show it as yours.
                SoundSource.BUNDLED -> bundledGroups[it.uri] ?: SoundGroup.YOURS
                SoundSource.PHONE -> SoundGroup.PHONE
                SoundSource.IMPORTED -> SoundGroup.YOURS
            }
        }
        return SoundGroup.entries.mapNotNull { group ->
            byGroup[group]?.let { SoundSection(group, it.sortedBy { sound -> sound.displayName.lowercase() }) }
        }
    }

    suspend fun setEnabled(sounds: Collection<Sound>, enabled: Boolean) =
        this.sounds.setEnabled(sounds.map { it.id }, enabled)

    /**
     * Adds one of the phone's own notification sounds (from the system sound picker) to the pool. The file
     * stays where it is; the system can always play its own sounds. Re-adding one already there does nothing.
     */
    suspend fun addPhoneSound(uri: Uri) = withContext(Dispatchers.IO) {
        val title = RingtoneManager.getRingtone(context, uri)?.getTitle(context)?.ifBlank { null }
        sounds.insert(Sound(displayName = title ?: "Phone sound", uri = uri.toString(), source = SoundSource.PHONE))
        syncChannels()
    }

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
     * The system only plays a channel sound the app itself can read, and an app can always read media
     * entries it created.
     */
    suspend fun import(source: Uri): ImportResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext ImportResult.NotAudio
        // A notification sound plays to the end, so a whole song would play on every ping.
        val length = lengthOf(source) ?: return@withContext ImportResult.NotAudio
        if (length > MAX_SOUND_LENGTH) return@withContext ImportResult.TooLong
        val fileName = displayNameOf(source)
        val target = copyIntoMediaStore(source, fileName ?: "sound") ?: return@withContext ImportResult.NotAudio
        val sound = Sound(
            displayName = fileName?.substringBeforeLast('.')?.ifBlank { null } ?: "Imported sound",
            uri = target.toString(),
            source = SoundSource.IMPORTED
        )
        val id = sounds.insert(sound)
        syncChannels()
        ImportResult.Added(sound.copy(id = id))
    }

    suspend fun setEnabled(sound: Sound, enabled: Boolean) = sounds.setEnabled(sound.id, enabled)

    /** Removes an imported sound and its file, or a phone sound (leaving the phone's file). Bundled sounds stay. */
    suspend fun remove(sound: Sound) = withContext(Dispatchers.IO) {
        if (sound.source == SoundSource.BUNDLED) return@withContext
        sounds.deleteById(sound.id)
        if (sound.source == SoundSource.IMPORTED) deleteImportedFile(sound)
        syncChannels()
    }

    private fun deleteImportedFile(sound: Sound) {
        try {
            context.contentResolver.delete(sound.uri.toUri(), null, null)
        } catch (_: SecurityException) {
            // The file no longer belongs to us (e.g. restored from a backup); leave it where it is.
        }
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

    // Null when the file can't be read as audio at all.
    private fun lengthOf(source: Uri): Duration? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, source)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let(Duration::ofMillis)
        } catch (_: RuntimeException) {
            null
        } finally {
            retriever.release()
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
        val MAX_SOUND_LENGTH: Duration = Duration.ofSeconds(30)

        fun from(context: Context) =
            SoundLibrary(context.applicationContext, RandominderDatabase.getInstance(context))
    }
}
