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
            NotificationChannelCompat.Builder(channelIdFor(it), NotificationManagerCompat.IMPORTANCE_DEFAULT)
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
        val channelId = ping.sound?.let(::channelIdFor) ?: DEFAULT_CHANNEL_ID
        if (manager.getNotificationChannelCompat(channelId) == null) manager.createNotificationChannel(defaultChannel())
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(ping.title)
            .setContentText(ping.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(ping.text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
        // Before Android 8 there are no channels and the sound is set per notification.
        ping.sound?.let { builder.setSound(it.uri.toUri()) }
        // One notification per reminder: a new ping replaces that reminder's previous one.
        manager.notify(ping.reminderId.toInt(), builder.build())
    }

    private fun defaultChannel() =
        NotificationChannelCompat.Builder(DEFAULT_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.default_channel_name))
            .build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val DEFAULT_CHANNEL_ID = "reminders_default"

        // Android revives a deleted channel's old settings if its id is reused, so the id must change
        // whenever the file does, even if a sound row id is reused (e.g. after the database is recreated).
        fun channelIdFor(sound: Sound) = "sound_${sound.id}_${Integer.toHexString(sound.uri.hashCode())}"
    }
}
