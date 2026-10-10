package io.github.marshallhelenm.randominder.ui.sounds

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.marshallhelenm.randominder.model.Sound
import io.github.marshallhelenm.randominder.sounds.ImportSummary
import io.github.marshallhelenm.randominder.sounds.PhoneSound
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

    // What the last batch of files did; cleared when the next one starts.
    private val _importSummary = MutableStateFlow<ImportSummary?>(null)
    val importSummary: StateFlow<ImportSummary?> = _importSummary.asStateFlow()

    // Null until the "Built-in phone sounds" list has been opened and loaded.
    private val _phoneSounds = MutableStateFlow<List<PhoneSound>?>(null)
    val phoneSounds: StateFlow<List<PhoneSound>?> = _phoneSounds.asStateFlow()

    fun import(sources: List<Uri>) {
        if (sources.isEmpty()) return
        _importSummary.value = null
        viewModelScope.launch { _importSummary.value = ImportSummary.of(sources.map { library.import(it) }) }
    }

    fun loadPhoneSounds() {
        if (_phoneSounds.value != null) return
        viewModelScope.launch { _phoneSounds.value = library.phoneNotificationSounds() }
    }

    fun addPhoneSounds(chosen: List<PhoneSound>) {
        viewModelScope.launch { library.addPhoneSounds(chosen) }
    }

    fun preview(phoneSound: PhoneSound) = previewer.play(phoneSound.uri)

    fun stopPreview() = previewer.stop()

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
