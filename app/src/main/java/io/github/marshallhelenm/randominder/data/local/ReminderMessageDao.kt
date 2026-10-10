package io.github.marshallhelenm.randominder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.marshallhelenm.randominder.model.ReminderMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderMessageDao {
    @Query("SELECT * FROM reminder_messages WHERE reminderId = :reminderId ORDER BY id")
    fun observeForReminder(reminderId: Long): Flow<List<ReminderMessage>>

    @Query("SELECT * FROM reminder_messages WHERE reminderId = :reminderId ORDER BY id")
    suspend fun getForReminder(reminderId: Long): List<ReminderMessage>

    @Insert
    suspend fun insert(message: ReminderMessage): Long

    @Query("DELETE FROM reminder_messages WHERE id = :id")
    suspend fun deleteById(id: Long)
}
