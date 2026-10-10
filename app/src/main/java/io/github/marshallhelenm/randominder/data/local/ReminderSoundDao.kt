package io.github.marshallhelenm.randominder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import io.github.marshallhelenm.randominder.model.ReminderSound

@Dao
interface ReminderSoundDao {
    @Query("SELECT soundId FROM reminder_sounds WHERE reminderId = :reminderId")
    suspend fun getSoundIds(reminderId: Long): List<Long>

    @Insert
    suspend fun insertAll(links: List<ReminderSound>)

    @Query("DELETE FROM reminder_sounds WHERE reminderId = :reminderId")
    suspend fun deleteForReminder(reminderId: Long)

    @Transaction
    suspend fun replaceForReminder(reminderId: Long, soundIds: Collection<Long>) {
        deleteForReminder(reminderId)
        insertAll(soundIds.map { ReminderSound(reminderId, it) })
    }
}
