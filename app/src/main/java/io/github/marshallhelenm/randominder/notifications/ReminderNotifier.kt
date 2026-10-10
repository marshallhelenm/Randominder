package io.github.marshallhelenm.randominder.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.github.marshallhelenm.randominder.MainActivity
import io.github.marshallhelenm.randominder.R
import io.github.marshallhelenm.randominder.model.Sound
import io.github.marshallhelenm.randominder.scheduling.NotificationActionReceiver
import io.github.marshallhelenm.randominder.scheduling.PingNotification

/**
 * Since Android 8 a notification's sound belongs to its channel, so every sound in the pool gets its own
 * channel and a ping picks a sound by picking that channel. The system then plays it, honouring silent
 * mode and Do Not Disturb.
 */
class ReminderNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /** Creates channels for the pool plus the default, and removes channels for sounds no longer in it. */
    fun syncChannels(sounds: List<Sound>) {
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channels = sounds.map {
            // A channel's sound can't change after creation, so the id is tied to the sound itself, not its name.
            NotificationChannelCompat.Builder(channelIdFor(it), PING_IMPORTANCE)
                .setName(context.getString(R.string.sound_channel_name, it.displayName))
                .setSound(it.uri.toUri(), audio)
                .build()
        } + defaultChannel()
        manager.createNotificationChannelsCompat(channels)
        manager.deleteUnlistedNotificationChannels(channels.map { it.id })
    }

    fun show(ping: PingNotification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        // Posting to a channel that doesn't exist drops the notification, so a sound without one
        // (e.g. bundled sounds not yet set up after an update) falls back to the default.
        val channelId = ping.sound?.let(::channelIdFor)
            ?.takeIf { manager.getNotificationChannelCompat(it) != null }
            ?: DEFAULT_CHANNEL_ID.also { manager.createNotificationChannel(defaultChannel()) }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(ping.title)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // Android 7 has no channels; this is what makes the ping pop up there.
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openReminderIntent(ping.reminderId))
            .setAutoCancel(true)
            .addAction(R.drawable.ic_close, context.getString(R.string.action_done), actionIntent(ACTION_DONE, ping.reminderId))
            .addAction(R.drawable.ic_notification, context.getString(R.string.action_snooze), actionIntent(ACTION_SNOOZE, ping.reminderId))
        // A reminder without messages shows only its name, rather than repeating it as the body.
        ping.text?.let { builder.setContentText(it).setStyle(NotificationCompat.BigTextStyle().bigText(it)) }
        // Before Android 8 there are no channels and the sound is set per notification.
        ping.sound?.let { builder.setSound(it.uri.toUri()) }
        // One notification per reminder: a new ping replaces that reminder's previous one.
        manager.notify(notificationIdFor(ping.reminderId), builder.build())
    }

    private fun defaultChannel() =
        NotificationChannelCompat.Builder(DEFAULT_CHANNEL_ID, PING_IMPORTANCE)
            .setName(context.getString(R.string.default_channel_name))
            .build()

    // The request code is the reminder id: with a shared one, FLAG_UPDATE_CURRENT would make every
    // reminder's notification carry the extras of whichever was posted last.
    private fun openReminderIntent(reminderId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        reminderId.toInt(),
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_REMINDER_ID, reminderId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun actionIntent(action: String, reminderId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        reminderId.toInt(),
        Intent(context, NotificationActionReceiver::class.java).setAction(action).putExtra(EXTRA_REMINDER_ID, reminderId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val DEFAULT_CHANNEL_ID = "reminders_default"
        const val ACTION_DONE = "io.github.marshallhelenm.randominder.action.DONE"
        const val ACTION_SNOOZE = "io.github.marshallhelenm.randominder.action.SNOOZE"
        const val EXTRA_REMINDER_ID = "reminderId"

        // Each reminder has at most one notification, so its id doubles as the notification id.
        fun notificationIdFor(reminderId: Long) = reminderId.toInt()

        // High importance shows each ping as a banner over the top of the screen. Android freezes a
        // channel's importance once it exists on a device, so changing this later needs new channel ids.
        const val PING_IMPORTANCE = NotificationManagerCompat.IMPORTANCE_HIGH

        // Android revives a deleted channel's old settings if its id is reused, so the id must change
        // whenever the file does, even if a sound row id is reused (e.g. after the database is recreated).
        fun channelIdFor(sound: Sound) = "sound_${sound.id}_${Integer.toHexString(sound.uri.hashCode())}"
    }
}
