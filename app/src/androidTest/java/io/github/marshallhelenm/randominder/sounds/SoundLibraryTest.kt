package io.github.marshallhelenm.randominder.sounds

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
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

        val sound = library.import(sourceFile("Birdsong.mp3", bytes))!!.also { imported += it }

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

        assertNull(result)
        assertEquals(emptyList<Sound>(), db.soundDao().getAll())
    }

    @Test
    fun remove_deletesRowFileAndChannel() = runBlocking {
        val sound = library.import(sourceFile("Gong.mp3", bundledBytes("duck_quack")))!!
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
