package io.github.marshallhelenm.randominder.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.LocalTime

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    // Pings land in [windowStart, windowEnd) on the same day; windows never cross midnight.
    val windowStart: LocalTime,
    val windowEnd: LocalTime,
    val pingCount: Int,
    val daysOfWeek: Set<DayOfWeek>
)
