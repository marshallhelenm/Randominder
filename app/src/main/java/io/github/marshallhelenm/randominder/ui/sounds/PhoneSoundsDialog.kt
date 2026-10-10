package io.github.marshallhelenm.randominder.ui.sounds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.marshallhelenm.randominder.R
import io.github.marshallhelenm.randominder.sounds.PhoneSound

/**
 * The phone's own notification sounds with checkboxes, so several can be added at once. Android's picker only
 * allows one. Sounds already in the pool show as ticked and can't be added twice.
 */
@Composable
fun PhoneSoundsDialog(
    phoneSounds: List<PhoneSound>?,
    alreadyAdded: Set<String>,
    onPreview: (PhoneSound) -> Unit,
    onAdd: (List<PhoneSound>) -> Unit,
    onDismiss: () -> Unit
) {
    // Chosen by URI so the choice survives rotation.
    var chosen by rememberSaveable { mutableStateOf(setOf<String>()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.phone_sounds_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.phone_sounds_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        phoneSounds == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        phoneSounds.isEmpty() -> Text(stringResource(R.string.phone_sounds_none), modifier = Modifier.align(Alignment.Center))
                        else -> LazyColumn {
                            items(phoneSounds, key = { it.uri }) { sound ->
                                val added = sound.uri in alreadyAdded
                                PhoneSoundRow(
                                    sound = sound,
                                    checked = added || sound.uri in chosen,
                                    enabled = !added,
                                    onCheckedChange = { chosen = if (it) chosen + sound.uri else chosen - sound.uri },
                                    onPreview = { onPreview(sound) }
                                )
                            }
                        }
                    }
                }
                Row(modifier = Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Button(
                        onClick = { onAdd(phoneSounds.orEmpty().filter { it.uri in chosen }) },
                        enabled = chosen.isNotEmpty()
                    ) {
                        Text(pluralStringResource(R.plurals.phone_sounds_add, chosen.size, chosen.size))
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneSoundRow(
    sound: PhoneSound,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onPreview: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .toggleable(value = checked, enabled = enabled, onValueChange = onCheckedChange, role = Role.Checkbox),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(sound.title, style = MaterialTheme.typography.bodyLarge)
                if (!enabled) {
                    Text(
                        stringResource(R.string.phone_sounds_already_added),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        TextButton(onClick = onPreview) { Text(stringResource(R.string.sound_play)) }
    }
}
