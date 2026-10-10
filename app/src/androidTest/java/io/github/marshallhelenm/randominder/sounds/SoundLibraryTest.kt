package io.github.marshallhelenm.randominder.sounds

import android.app.NotificationManager
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.marshallhelenm.randominder.data.local.RandominderDatabase
import io.github.marshallhelenm.randominder.model.Sound
import io.github.marshallhelenm.randominder.model.SoundSource
import io.github.marshallhelenm.randominder.notifications.ReminderNotifier
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Duration

// Uses the device's real MediaStore and notification service; needs API 29+ for imports.
@RunWith(AndroidJUnit4::class)
class SoundLibraryTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val system = context.getSystemService(NotificationManager::class.java)
    private lateinit var db: RandominderDatabase
    private lateinit var library: SoundLibrary
    private val imported = mutableListOf<Sound>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, RandominderDatabase::class.java).build()
        library = SoundLibrary(context, db)
    }

    @After
    fun tearDown() = runBlocking {
        imported.forEach { library.remove(it) }
        ReminderNotifier(context).syncChannels(emptyList())
        db.close()
    }

    private fun bundledBytes(rawName: String): ByteArray =
        context.contentResolver.openInputStream("android.resource://${context.packageName}/raw/$rawName".toUri())!!
            .use { it.readBytes() }

    private fun sourceFile(name: String, bytes: ByteArray): Uri =
        File(context.cacheDir, name).apply { writeBytes(bytes) }.let(Uri::fromFile)

    // 8kHz 8-bit mono PCM: small enough to generate in a test, long enough to cross the limit.
    private fun silentWav(seconds: Int): ByteArray {
        val samples = 8_000 * seconds
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(8_000); putInt(8_000)
            putShort(1); putShort(8)
            put("data".toByteArray()); putInt(samples)
        }.array()
        return header + ByteArray(samples) { 128.toByte() }
    }

    private fun mediaStoreCopiesNamed(name: String): Int = context.contentResolver.query(
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        arrayOf(MediaStore.Audio.Media._ID),
        "${MediaStore.Audio.Media.DISPLAY_NAME} = ?",
        arrayOf(name),
        null
    )?.use { it.count } ?: 0

    private fun readable(uri: String): Boolean =
        try {
            context.contentResolver.openInputStream(uri.toUri())?.use { true } ?: false
        } catch (_: Exception) {
            false
        }

    @Test
    fun ensureBundled_addsEveryShippedSound_onceEach_withPlayableUris() = runBlocking {
        library.ensureBundled()
        library.ensureBundled()

        val pool = db.soundDao().getAll()
        assertEquals(BUNDLED_SOUNDS.map { it.displayName }.sorted(), pool.map { it.displayName }.sorted())
        assertTrue(pool.all { it.source == SoundSource.BUNDLED && readable(it.uri) })
    }

    @Test
    fun ensureBundled_bundledChannelsKeepTheirSound() = runBlocking {
        library.ensureBundled()

        db.soundDao().getAll().forEach {
            assertEquals(it.uri, system.getNotificationChannel(ReminderNotifier.channelIdFor(it)).sound.toString())
        }
    }

    @Test
    fun ensureBundled_dropsBundledSoundsNoLongerShipped_keepsImports() = runBlocking {
        db.soundDao().insert(Sound(displayName = "Retired", uri = "android.resource://x/raw/old", source = SoundSource.BUNDLED))
        db.soundDao().insert(Sound(displayName = "Mine", uri = "content://media/1", source = SoundSource.IMPORTED))

        library.ensureBundled()

        val names = db.soundDao().getAll().map { it.displayName }
        assertTrue("Retired" !in names)
        assertTrue("Mine" in names)
    }

    @Test
    fun import_copiesIntoMediaStore_andItsChannelKeepsTheSound() = runBlocking {
        val bytes = bundledBytes("boing")

        val sound = (library.import(sourceFile("Birdsong.mp3", bytes)) as ImportResult.Added).sound.also { imported += it }

        assertEquals("Birdsong", sound.displayName)
        assertEquals(SoundSource.IMPORTED, sound.source)
        assertTrue(sound.uri.startsWith("content://media/"))
        val copy = context.contentResolver.openInputStream(sound.uri.toUri())!!.use { it.readBytes() }
        assertArrayEquals(bytes, copy)
        // The system replaces sounds the app can't read with the default; this one must survive.
        assertEquals(sound.uri, system.getNotificationChannel(ReminderNotifier.channelIdFor(sound)).sound.toString())
        assertEquals(listOf(sound), db.soundDao().getAll())
    }

    @Test
    fun import_nonAudio_isRejectedWithoutLeavingAnything() = runBlocking {
        val result = library.import(sourceFile("notes.txt", "not audio".toByteArray()))

        assertEquals(ImportResult.NotAudio, result)
        assertEquals(emptyList<Sound>(), db.soundDao().getAll())
    }

    @Test
    fun import_longerThanTheLimit_isRejectedWithoutLeavingAnything() = runBlocking {
        val result = library.import(sourceFile("Song.wav", silentWav(seconds = 31)))

        assertEquals(ImportResult.TooLong, result)
        assertEquals(emptyList<Sound>(), db.soundDao().getAll())
        assertEquals(0, mediaStoreCopiesNamed("Song.wav"))
    }

    @Test
    fun import_justUnderTheLimit_isAdded() = runBlocking {
        val result = library.import(sourceFile("Chime.wav", silentWav(seconds = 29)))

        assertTrue(result is ImportResult.Added)
        imported += (result as ImportResult.Added).sound
    }

    @Test
    fun bundledSounds_areAllWithinTheImportLimit() {
        val retriever = MediaMetadataRetriever()
        try {
            BUNDLED_SOUNDS.forEach {
                retriever.setDataSource(context, "android.resource://${context.packageName}/${it.rawRes}".toUri())
                val millis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue("${it.displayName} is ${millis}ms", Duration.ofMillis(millis) <= SoundLibrary.MAX_SOUND_LENGTH)
            }
        } finally {
            retriever.release()
        }
    }

    @Test
    fun remove_deletesRowFileAndChannel() = runBlocking {
        val sound = (library.import(sourceFile("Gong.mp3", bundledBytes("duck_quack"))) as ImportResult.Added).sound
        assertNotNull(system.getNotificationChannel(ReminderNotifier.channelIdFor(sound)))

        library.remove(sound)

        assertEquals(emptyList<Sound>(), db.soundDao().getAll())
        assertTrue(!readable(sound.uri))
        assertNull(system.getNotificationChannel(ReminderNotifier.channelIdFor(sound)))
    }

    @Test
    fun ensureBundled_keepsASwitchedOffBundledSoundOff() = runBlocking {
        library.ensureBundled()
        val chime = db.soundDao().getAll().first()
        library.setEnabled(chime, false)

        library.ensureBundled()

        assertEquals(false, db.soundDao().getAll().single { it.id == chime.id }.enabled)
    }

    @Test
    fun setEnabled_switchingOff_keepsTheSoundsChannel() = runBlocking {
        library.ensureBundled()
        val chime = db.soundDao().getAll().first()

        library.setEnabled(chime, false)
        library.ensureBundled()

        assertNotNull(system.getNotificationChannel(ReminderNotifier.channelIdFor(chime)))
        assertEquals(chime.uri, system.getNotificationChannel(ReminderNotifier.channelIdFor(chime)).sound.toString())
    }

    @Test
    fun remove_bundledSound_isIgnored() = runBlocking {
        library.ensureBundled()
        val chime = db.soundDao().getAll().first()

        library.remove(chime)

        assertTrue(chime in db.soundDao().getAll())
    }
}
