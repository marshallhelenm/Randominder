package io.github.marshallhelenm.randominder.ui.sounds

import android.app.Application
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.marshallhelenm.randominder.model.Sound
import io.github.marshallhelenm.randominder.sounds.SoundLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SoundsViewModel(application: Application) : AndroidViewModel(application) {
    private val library = SoundLibrary.from(application)
    private var playing: Ringtone? = null

    val canImport = library.canImport

    val sounds: StateFlow<List<Sound>> = library.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importFailed = MutableStateFlow(false)
    val importFailed: StateFlow<Boolean> = _importFailed.asStateFlow()

    init {
        viewModelScope.launch { library.ensureBundled() }
    }

    fun import(source: Uri) {
        _importFailed.value = false
        viewModelScope.launch { _importFailed.value = library.import(source) == null }
    }

    fun setEnabled(sound: Sound, enabled: Boolean) {
        viewModelScope.launch { library.setEnabled(sound, enabled) }
    }

    fun remove(sound: Sound) {
        viewModelScope.launch { library.remove(sound) }
    }

    fun preview(sound: Sound) {
        playing?.stop()
        playing = RingtoneManager.getRingtone(getApplication(), sound.uri.toUri())?.also { it.play() }
    }

    override fun onCleared() {
        playing?.stop()
    }
}
