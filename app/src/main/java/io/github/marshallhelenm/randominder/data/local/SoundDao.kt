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

    // Re-adding a file that's already in the pool is a no-op; returns -1 in that case.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sound: Sound): Long

    @Query("DELETE FROM sounds WHERE id = :id")
    suspend fun deleteById(id: Long)
}
