package io.github.marshallhelenm.randominder.ui.sounds

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.marshallhelenm.randominder.R
import io.github.marshallhelenm.randominder.model.Sound
import io.github.marshallhelenm.randominder.model.SoundSource
import io.github.marshallhelenm.randominder.ui.theme.RandominderTheme

@Composable
fun SoundsSection(modifier: Modifier = Modifier, viewModel: SoundsViewModel = viewModel()) {
    val sounds by viewModel.sounds.collectAsStateWithLifecycle()
    val importFailed by viewModel.importFailed.collectAsStateWithLifecycle()
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::import)
    }
    SoundsList(
        sounds = sounds,
        canImport = viewModel.canImport,
        importFailed = importFailed,
        onImport = { pickAudio.launch("audio/*") },
        onPreview = viewModel::preview,
        onToggle = viewModel::setEnabled,
        onRemove = viewModel::remove,
        modifier = modifier
    )
}

@Composable
private fun SoundsList(
    sounds: List<Sound>,
    canImport: Boolean,
    importFailed: Boolean,
    onImport: () -> Unit,
    onPreview: (Sound) -> Unit,
    onToggle: (Sound, Boolean) -> Unit,
    onRemove: (Sound) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.sounds_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (canImport) {
                OutlinedButton(onClick = onImport) { Text(stringResource(R.string.sounds_import)) }
            }
        }
        if (importFailed) {
            Text(text = stringResource(R.string.sounds_import_failed), color = MaterialTheme.colorScheme.error)
        }
        LazyColumn {
            items(sounds, key = { it.id }) { sound ->
                SoundRow(
                    sound = sound,
                    onPreview = { onPreview(sound) },
                    onToggle = { onToggle(sound, it) },
                    onRemove = { onRemove(sound) }
                )
            }
        }
    }
}

@Composable
private fun SoundRow(sound: Sound, onPreview: () -> Unit, onToggle: (Boolean) -> Unit, onRemove: () -> Unit) {
    val toggleLabel = stringResource(R.string.sound_toggle, sound.displayName)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = sound.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(
                    if (sound.source == SoundSource.BUNDLED) R.string.sound_source_bundled else R.string.sound_source_imported
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onPreview) { Text(stringResource(R.string.sound_play)) }
        if (sound.source == SoundSource.IMPORTED) {
            TextButton(onClick = onRemove) { Text(stringResource(R.string.sound_remove)) }
        }
        Switch(
            checked = sound.enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.semantics { contentDescription = toggleLabel }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SoundsListPreview() {
    RandominderTheme {
        SoundsList(
            sounds = listOf(
                Sound(id = 1, displayName = "Chime", uri = "", source = SoundSource.BUNDLED),
                Sound(id = 2, displayName = "Birdsong", uri = "", source = SoundSource.IMPORTED, enabled = false)
            ),
            canImport = true,
            importFailed = true,
            onImport = {},
            onPreview = {},
            onToggle = { _, _ -> },
            onRemove = {}
        )
    }
}
