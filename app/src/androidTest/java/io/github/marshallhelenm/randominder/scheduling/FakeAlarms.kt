package io.github.marshallhelenm.randominder.scheduling

// Records the one armed alarm instead of touching AlarmManager.
class FakeAlarms : AlarmScheduler {
    var armedAt: Long? = null

    override fun schedule(atMillis: Long) {
        armedAt = atMillis
    }

    override fun cancel() {
        armedAt = null
    }
}
