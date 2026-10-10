package io.github.marshallhelenm.randominder.ui.reminders

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditReminderViewModel(application: Application) : AndroidViewModel(application) {
    private val editor = ReminderEditor.from(application)
    private var reminderId: Long? = null
    private var started = false

    // Null until the reminder has loaded.
    private val _form = MutableStateFlow<ReminderForm?>(null)
    val form: StateFlow<ReminderForm?> = _form.asStateFlow()

    // Errors stay hidden until the first save attempt, so a new form doesn't open covered in red.
    private val _showErrors = MutableStateFlow(false)
    val showErrors: StateFlow<Boolean> = _showErrors.asStateFlow()

    /** Null [id] means a new reminder. Only the first call counts, so recomposition can't reload over edits. */
    fun start(id: Long?) {
        if (started) return
        started = true
        reminderId = id
        if (id == null) {
            _form.value = ReminderForm()
        } else {
            viewModelScope.launch { _form.value = editor.load(id) ?: ReminderForm() }
        }
    }

    fun update(change: (ReminderForm) -> ReminderForm) = _form.update { it?.let(change) }

    fun save(onSaved: () -> Unit) {
        val current = _form.value ?: return
        if (current.errors.isNotEmpty()) {
            _showErrors.value = true
            return
        }
        viewModelScope.launch {
            editor.save(reminderId, current)
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = reminderId ?: return onDeleted()
        viewModelScope.launch {
            editor.delete(id)
            onDeleted()
        }
    }
}
