package io.github.marshallhelenm.randominder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.marshallhelenm.randominder.model.Reminder
import io.github.marshallhelenm.randominder.model.ReminderMessage
import io.github.marshallhelenm.randominder.model.ReminderSound
import io.github.marshallhelenm.randominder.model.ScheduledPing
import io.github.marshallhelenm.randominder.model.Sound

@Database(
    entities = [Reminder::class, ReminderMessage::class, Sound::class, ScheduledPing::class, ReminderSound::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class RandominderDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun reminderMessageDao(): ReminderMessageDao
    abstract fun soundDao(): SoundDao
    abstract fun scheduledPingDao(): ScheduledPingDao
    abstract fun reminderSoundDao(): ReminderSoundDao

    companion object {
        @Volatile
        private var INSTANCE: RandominderDatabase? = null

        fun getInstance(context: Context): RandominderDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RandominderDatabase::class.java,
                    "randominder.db"
                )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
