package io.github.marshallhelenm.randominder.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// A planned firing time, persisted so a reboot or process death doesn't re-roll the day's schedule.
@Entity(
    tableName = "scheduled_pings",
    foreignKeys = [
        ForeignKey(
            entity = Reminder::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["reminderId"]), Index(value = ["fireAt"])]
)
data class ScheduledPing(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val reminderId: Long,
    // Epoch millis.
    val fireAt: Long
)
