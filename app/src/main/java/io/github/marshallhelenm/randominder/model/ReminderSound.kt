package io.github.marshallhelenm.randominder.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

// A sound a reminder has chosen. Only used when the reminder isn't set to use all sounds.
@Entity(
    tableName = "reminder_sounds",
    primaryKeys = ["reminderId", "soundId"],
    foreignKeys = [
        ForeignKey(entity = Reminder::class, parentColumns = ["id"], childColumns = ["reminderId"], onDelete = ForeignKey.CASCADE),
        // Removing a sound from the pool drops it from every reminder's choices.
        ForeignKey(entity = Sound::class, parentColumns = ["id"], childColumns = ["soundId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["soundId"])]
)
data class ReminderSound(val reminderId: Long, val soundId: Long)
