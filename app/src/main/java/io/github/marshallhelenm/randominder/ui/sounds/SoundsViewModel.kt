package io.github.marshallhelenm.randominder.ui.sounds

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.marshallhelenm.randominder.model.Sound
import io.github.marshallhelenm.randominder.sounds.ImportResult
import io.github.marshallhelenm.randominder.sounds.SoundLibrary
import io.github.marshallhelenm.randominder.sounds.SoundSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SoundsViewModel(application: Application) : AndroidViewModel(application) {
    private val library = SoundLibrary.from(application)
    private val previewer = SoundPreviewer(application)

    val canImport = library.canImport

    val sections: StateFlow<List<SoundSection>> = library.observeSections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // The last import's problem, if it had one; cleared when the next import starts.
    private val _importProblem = MutableStateFlow<ImportResult?>(null)
    val importProblem: StateFlow<ImportResult?> = _importProblem.asStateFlow()

    fun import(source: Uri) {
        _importProblem.value = null
        viewModelScope.launch { _importProblem.value = library.import(source).takeIf { it !is ImportResult.Added } }
    }

    fun addPhoneSound(uri: Uri) {
        _importProblem.value = null
        viewModelScope.launch { library.addPhoneSound(uri) }
    }

    fun setEnabled(sound: Sound, enabled: Boolean) {
        viewModelScope.launch { library.setEnabled(sound, enabled) }
    }

    fun setEnabled(section: SoundSection, enabled: Boolean) {
        viewModelScope.launch { library.setEnabled(section.sounds, enabled) }
    }

    fun setAllEnabled(enabled: Boolean) {
        viewModelScope.launch { library.setEnabled(sections.value.flatMap { it.sounds }, enabled) }
    }

    fun remove(sound: Sound) {
        viewModelScope.launch { library.remove(sound) }
    }

    fun preview(sound: Sound) = previewer.play(sound)

    override fun onCleared() = previewer.stop()
}
