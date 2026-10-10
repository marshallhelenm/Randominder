package io.github.marshallhelenm.randominder.ui.reminders

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.marshallhelenm.randominder.model.Reminder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RemindersViewModel(application: Application) : AndroidViewModel(application) {
    private val editor = ReminderEditor.from(application)

    val reminders: StateFlow<List<Reminder>> = editor.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEnabled(reminder: Reminder, enabled: Boolean) {
        viewModelScope.launch { editor.setEnabled(reminder, enabled) }
    }
}
