package io.github.marshallhelenm.randominder.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SoundSource {
    // Shipped in res/raw; the system can read android.resource:// URIs directly.
    BUNDLED,
    // Picked from the device and copied into the shared media library so the system can play it.
    IMPORTED,
    // One of the phone's own notification sounds, used in place; removing it never touches the file.
    PHONE
}

// App-wide pool: every reminder draws from all of these. Each sound gets its own notification channel.
@Entity(
    tableName = "sounds",
    indices = [Index(value = ["uri"], unique = true)]
)
data class Sound(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val displayName: String,
    val uri: String,
    val source: SoundSource,
    // Off means pings skip it; it keeps its channel so per-sound system settings survive toggling.
    val enabled: Boolean = true
)
