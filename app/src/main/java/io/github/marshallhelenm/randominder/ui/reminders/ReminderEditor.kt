package io.github.marshallhelenm.randominder.ui.reminders

import android.content.Context
import androidx.room.withTransaction
import io.github.marshallhelenm.randominder.data.local.RandominderDatabase
import io.github.marshallhelenm.randominder.model.Reminder
import io.github.marshallhelenm.randominder.scheduling.PingScheduler
import kotlinx.coroutines.flow.Flow

/** Every change to a reminder goes through here so its planned pings are always redone afterwards. */
class ReminderEditor(private val db: RandominderDatabase, private val scheduler: PingScheduler) {
    private val reminders = db.reminderDao()
    private val messages = db.reminderMessageDao()

    fun observeAll(): Flow<List<Reminder>> = reminders.observeAll()

    suspend fun load(id: Long): ReminderForm? =
        reminders.getById(id)?.let { ReminderForm.from(it, messages.getForReminder(id)) }

    /** Saves [form] as reminder [id], or as a new reminder when [id] is null. Returns the reminder's id. */
    suspend fun save(id: Long?, form: ReminderForm): Long {
        val savedId = db.withTransaction {
            val existing = id?.let { reminders.getById(it) }
            val reminder = form.toReminder(id = existing?.id ?: 0, lastMessage = existing?.lastMessage)
            // Upsert returns the new row id on insert but not on update.
            val inserted = reminders.upsert(reminder)
            val newId = existing?.id ?: inserted
            messages.replaceForReminder(newId, form.savedMessages)
            newId
        }
        scheduler.replan(savedId)
        return savedId
    }

    suspend fun setEnabled(reminder: Reminder, enabled: Boolean) {
        reminders.upsert(reminder.copy(enabled = enabled))
        scheduler.replan(reminder.id)
    }

    suspend fun delete(id: Long) {
        reminders.deleteById(id)
        scheduler.replan(id)
    }

    companion object {
        fun from(context: Context) =
            ReminderEditor(RandominderDatabase.getInstance(context), PingScheduler.from(context))
    }
}
