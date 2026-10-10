package io.github.marshallhelenm.randominder.ui.reminders

import android.content.Context
import androidx.room.withTransaction
import io.github.marshallhelenm.randominder.data.local.RandominderDatabase
import io.github.marshallhelenm.randominder.model.Reminder
import io.github.marshallhelenm.randominder.scheduling.PauseStore
import io.github.marshallhelenm.randominder.scheduling.PingScheduler
import io.github.marshallhelenm.randominder.scheduling.SharedPreferencesPauseStore
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate

/** Every change to a reminder goes through here so its planned pings are always redone afterwards. */
class ReminderEditor(
    private val db: RandominderDatabase,
    private val scheduler: PingScheduler,
    private val pause: PauseStore,
    private val clock: Clock = Clock.systemDefaultZone()
) {
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

    fun observePause(): Flow<Long?> = pause.observe()

    /** Drops the rest of today's pings everywhere; tomorrow plans as usual, with nothing to undo. */
    suspend fun pauseUntilTomorrow() =
        setPause(LocalDate.now(clock).plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli())

    suspend fun pauseUntilResumed() = setPause(PauseStore.UNTIL_RESUMED)

    suspend fun resumeAll() = setPause(null)

    private suspend fun setPause(until: Long?) {
        pause.pausedUntil = until
        scheduler.replanAll()
    }

    suspend fun delete(id: Long) {
        reminders.deleteById(id)
        scheduler.replan(id)
    }

    companion object {
        fun from(context: Context) = ReminderEditor(
            RandominderDatabase.getInstance(context),
            PingScheduler.from(context),
            SharedPreferencesPauseStore(context)
        )
    }
}
