package io.github.marshallhelenm.randominder.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.marshallhelenm.randominder.model.Sound
import io.github.marshallhelenm.randominder.model.SoundSource
import io.github.marshallhelenm.randominder.scheduling.PingNotification
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// Runs against the device's real notification service, so it needs API 26+ for channels.
@RunWith(AndroidJUnit4::class)
class ReminderNotifierTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val system = context.getSystemService(NotificationManager::class.java)
    private val notifier = ReminderNotifier(context)
    private val chime = Sound(id = 41, displayName = "Chime", uri = "android.resource://${context.packageName}/1", source = SoundSource.BUNDLED)
    // The system swaps a channel sound the app can't read for the default, so test URIs must be app-readable.
    private val bird = Sound(id = 42, displayName = "Bird", uri = "android.resource://${context.packageName}/2", source = SoundSource.BUNDLED)

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Posting is asynchronous; wait for the system's view of this notification id to settle.
    private fun postedWithId(id: Int, expectedText: String): List<StatusBarNotification> {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (true) {
            val posted = system.activeNotifications.filter { it.id == id }
            val text = posted.lastOrNull()?.notification?.extras?.getString("android.text")
            if (text == expectedText || SystemClock.uptimeMillis() > deadline) return posted
            SystemClock.sleep(50)
        }
    }

    private fun postedWithTitle(id: Int, expectedTitle: String): List<StatusBarNotification> {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (true) {
            val posted = system.activeNotifications.filter { it.id == id }
            val title = posted.lastOrNull()?.notification?.extras?.getString("android.title")
            if (title == expectedTitle || SystemClock.uptimeMillis() > deadline) return posted
            SystemClock.sleep(50)
        }
    }

    @After
    fun tearDown() {
        system.cancelAll()
        notifier.syncChannels(emptyList())
    }

    @Test
    fun syncChannels_createsOnePerSoundWithItsUri_plusDefault() {
        notifier.syncChannels(listOf(chime, bird))

        assertEquals(chime.uri, system.getNotificationChannel(ReminderNotifier.channelIdFor(chime)).sound.toString())
        assertEquals(bird.uri, system.getNotificationChannel(ReminderNotifier.channelIdFor(bird)).sound.toString())
        assertNotNull(system.getNotificationChannel(ReminderNotifier.DEFAULT_CHANNEL_ID))
    }

    @Test
    fun syncChannels_removesChannelsForSoundsLeavingThePool() {
        notifier.syncChannels(listOf(chime, bird))
        notifier.syncChannels(listOf(chime))

        assertNotNull(system.getNotificationChannel(ReminderNotifier.channelIdFor(chime)))
        assertNull(system.getNotificationChannel(ReminderNotifier.channelIdFor(bird)))
    }

    @Test
    fun show_postsOnTheSoundsChannel() {
        notifier.syncChannels(listOf(chime))

        notifier.show(PingNotification(reminderId = 7, title = "Stretch", text = "Reach up", sound = chime))

        val posted = postedWithId(7, "Reach up").single()
        assertEquals(ReminderNotifier.channelIdFor(chime), posted.notification.channelId)
        assertEquals("Reach up", posted.notification.extras.getString("android.text"))
    }

    @Test
    fun show_withoutText_postsTitleOnly() {
        notifier.syncChannels(emptyList())

        notifier.show(PingNotification(reminderId = 9, title = "Stretch", text = null, sound = null))

        val posted = postedWithTitle(9, "Stretch").single().notification
        assertNull(posted.extras.getCharSequence("android.text"))
    }

    @Test
    fun show_withoutSound_usesDefaultChannel_andReplacesThatRemindersPreviousPing() {
        notifier.syncChannels(emptyList())

        notifier.show(PingNotification(reminderId = 8, title = "Water", text = "Sip", sound = null))
        notifier.show(PingNotification(reminderId = 8, title = "Water", text = "Hydrate", sound = null))

        val posted = postedWithId(8, "Hydrate")
        assertEquals(1, posted.size)
        assertEquals(ReminderNotifier.DEFAULT_CHANNEL_ID, posted.single().notification.channelId)
        assertEquals("Hydrate", posted.single().notification.extras.getString("android.text"))
    }
}
