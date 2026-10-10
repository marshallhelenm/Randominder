package io.github.marshallhelenm.randominder.ui.reminders

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.marshallhelenm.randominder.model.Sound
import io.github.marshallhelenm.randominder.sounds.SoundLibrary
import io.github.marshallhelenm.randominder.sounds.SoundSection
import io.github.marshallhelenm.randominder.ui.sounds.SoundPreviewer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditReminderViewModel(application: Application) : AndroidViewModel(application) {
    private val editor = ReminderEditor.from(application)
    private val previewer = SoundPreviewer(application)

    val soundSections: StateFlow<List<SoundSection>> = SoundLibrary.from(application).observeSections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun preview(sound: Sound) = previewer.play(sound)

    override fun onCleared() = previewer.stop()
    private var reminderId: Long? = null
    private var started = false
    // The form as loaded, to tell whether leaving would lose edits.
    private var original: ReminderForm? = null

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
            show(ReminderForm())
        } else {
            viewModelScope.launch { show(editor.load(id) ?: ReminderForm()) }
        }
    }

    private fun show(loaded: ReminderForm) {
        original = loaded
        _form.value = loaded
    }

    fun hasUnsavedChanges(): Boolean {
        val current = _form.value ?: return false
        return original?.let(current::differsFrom) ?: false
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
