package io.github.marshallhelenm.randominder.scheduling

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePauseStore : PauseStore {
    private val state = MutableStateFlow<Long?>(null)

    override var pausedUntil: Long?
        get() = state.value
        set(value) {
            state.value = value
        }

    override fun observe(): Flow<Long?> = state
}
