package io.github.marshallhelenm.randominder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.marshallhelenm.randominder.model.Sound
import kotlinx.coroutines.flow.Flow

@Dao
interface SoundDao {
    @Query("SELECT * FROM sounds ORDER BY displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<Sound>>

    @Query("SELECT * FROM sounds")
    suspend fun getAll(): List<Sound>

    @Query("SELECT * FROM sounds WHERE enabled = 1")
    suspend fun getEnabled(): List<Sound>

    @Query("UPDATE sounds SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE sounds SET enabled = :enabled WHERE id IN (:ids)")
    suspend fun setEnabled(ids: Collection<Long>, enabled: Boolean)

    // A reminder's chosen sounds, minus any switched off in the Sounds tab (the master switch).
    @Query(
        "SELECT sounds.* FROM sounds JOIN reminder_sounds ON reminder_sounds.soundId = sounds.id " +
            "WHERE reminder_sounds.reminderId = :reminderId AND sounds.enabled = 1"
    )
    suspend fun getEnabledForReminder(reminderId: Long): List<Sound>

    // Re-adding a file that's already in the pool is a no-op; returns -1 in that case.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sound: Sound): Long

    @Query("DELETE FROM sounds WHERE id = :id")
    suspend fun deleteById(id: Long)
}
