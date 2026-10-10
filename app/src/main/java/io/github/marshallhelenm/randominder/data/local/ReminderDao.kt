package io.github.marshallhelenm.randominder.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.marshallhelenm.randominder.model.Reminder
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun getEnabled(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): Reminder?

    @Query("UPDATE reminders SET lastMessage = :text WHERE id = :id")
    suspend fun updateLastMessage(id: Long, text: String)

    @Upsert
    suspend fun upsert(reminder: Reminder): Long

    // Messages and scheduled pings go with it via ON DELETE CASCADE.
    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}
