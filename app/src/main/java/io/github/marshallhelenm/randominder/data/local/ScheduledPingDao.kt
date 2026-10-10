package io.github.marshallhelenm.randominder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import io.github.marshallhelenm.randominder.model.ScheduledPing

@Dao
interface ScheduledPingDao {
    // Only the earliest ping is armed with AlarmManager; the rest wait here.
    @Query("SELECT * FROM scheduled_pings ORDER BY fireAt, id LIMIT 1")
    suspend fun getNext(): ScheduledPing?

    @Query("SELECT * FROM scheduled_pings WHERE reminderId = :reminderId ORDER BY fireAt")
    suspend fun getForReminder(reminderId: Long): List<ScheduledPing>

    @Insert
    suspend fun insertAll(pings: List<ScheduledPing>)

    @Query("DELETE FROM scheduled_pings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM scheduled_pings WHERE reminderId = :reminderId")
    suspend fun deleteForReminder(reminderId: Long)

    // Editing a reminder discards its old plan, so the swap must not leave it half-planned.
    @Transaction
    suspend fun replaceForReminder(reminderId: Long, pings: List<ScheduledPing>) {
        deleteForReminder(reminderId)
        insertAll(pings)
    }
}
