package io.github.marshallhelenm.randominder.ui.sounds

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.marshallhelenm.randominder.R
import io.github.marshallhelenm.randominder.model.Sound
import io.github.marshallhelenm.randominder.model.SoundSource
import io.github.marshallhelenm.randominder.sounds.ImportSummary
import io.github.marshallhelenm.randominder.sounds.SoundGroup
import io.github.marshallhelenm.randominder.sounds.SoundSection
import io.github.marshallhelenm.randominder.ui.theme.RandominderTheme

@Composable
fun SoundsScreen(modifier: Modifier = Modifier, viewModel: SoundsViewModel = viewModel()) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val importSummary by viewModel.importSummary.collectAsStateWithLifecycle()
    val phoneSounds by viewModel.phoneSounds.collectAsStateWithLifecycle()
    var choosingPhoneSounds by rememberSaveable { mutableStateOf(false) }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents(), viewModel::import)
    SoundsList(
        sections = sections,
        canImport = viewModel.canImport,
        importSummary = importSummary,
        onImport = { pickAudio.launch("audio/*") },
        onAddPhoneSound = {
            viewModel.loadPhoneSounds()
            choosingPhoneSounds = true
        },
        onAllEnabled = viewModel::setAllEnabled,
        onGroupEnabled = viewModel::setEnabled,
        onPreview = viewModel::preview,
        onToggle = viewModel::setEnabled,
        onRemove = viewModel::remove,
        modifier = modifier
    )
    if (choosingPhoneSounds) {
        val close = {
            viewModel.stopPreview()
            choosingPhoneSounds = false
        }
        PhoneSoundsDialog(
            phoneSounds = phoneSounds,
            alreadyAdded = sections.flatMap { it.sounds }.filter { it.source == SoundSource.PHONE }.map { it.uri }.toSet(),
            onPreview = viewModel::preview,
            onAdd = {
                viewModel.addPhoneSounds(it)
                close()
            },
            onDismiss = close
        )
    }
}

@Composable
private fun SoundsList(
    sections: List<SoundSection>,
    canImport: Boolean,
    importSummary: ImportSummary?,
    onImport: () -> Unit,
    onAddPhoneSound: () -> Unit,
    onAllEnabled: (Boolean) -> Unit,
    onGroupEnabled: (SoundSection, Boolean) -> Unit,
    onPreview: (Sound) -> Unit,
    onToggle: (Sound, Boolean) -> Unit,
    onRemove: (Sound) -> Unit,
    modifier: Modifier = Modifier
) {
    var collapsed by rememberSaveable { mutableStateOf(setOf<SoundGroup>()) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.sounds_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(stringResource(R.string.sounds_add_heading), style = MaterialTheme.typography.titleSmall)
        // Side by side with equal widths; a long label wraps inside its own button instead of stacking them.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canImport) {
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.sounds_import), textAlign = TextAlign.Center)
                }
            }
            OutlinedButton(onClick = onAddPhoneSound, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.sounds_add_from_phone), textAlign = TextAlign.Center)
            }
        }
        importSummary?.let { ImportSummaryText(it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onAllEnabled(true) }) { Text(stringResource(R.string.sounds_all_on)) }
            TextButton(onClick = { onAllEnabled(false) }) { Text(stringResource(R.string.sounds_all_off)) }
        }
        LazyColumn {
            soundSections(
                sections = sections,
                collapsed = collapsed,
                onToggleCollapsed = { group -> collapsed = if (group in collapsed) collapsed - group else collapsed + group },
                headerControl = { section ->
                    val label = stringResource(R.string.sound_group_toggle, stringResource(section.group.label))
                    // On only when every sound in the group is on; flipping it sets them all.
                    Switch(
                        checked = section.sounds.all { it.enabled },
                        onCheckedChange = { onGroupEnabled(section, it) },
                        modifier = Modifier.semantics { contentDescription = label }
                    )
                },
                row = { sound ->
                    SoundRow(
                        sound = sound,
                        onPreview = { onPreview(sound) },
                        onToggle = { onToggle(sound, it) },
                        onRemove = { onRemove(sound) }
                    )
                }
            )
        }
    }
}

@Composable
private fun ImportSummaryText(summary: ImportSummary) {
    val parts = buildList {
        if (summary.added > 0) add(pluralStringResource(R.plurals.import_added, summary.added, summary.added))
        if (summary.skipped > 0) {
            val reasons = buildList {
                if (summary.tooLong > 0) add(pluralStringResource(R.plurals.import_too_long, summary.tooLong, summary.tooLong))
                if (summary.notAudio > 0) add(pluralStringResource(R.plurals.import_not_audio, summary.notAudio, summary.notAudio))
            }
            add(stringResource(R.string.import_skipped, reasons.joinToString(", ")))
        }
    }
    Text(
        text = parts.joinToString(" "),
        // Only an import where nothing worked reads as an error.
        color = if (summary.added == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SoundRow(sound: Sound, onPreview: () -> Unit, onToggle: (Boolean) -> Unit, onRemove: () -> Unit) {
    val toggleLabel = stringResource(R.string.sound_toggle, sound.displayName)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = sound.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(
                    when (sound.source) {
                        SoundSource.BUNDLED -> R.string.sound_source_bundled
                        SoundSource.IMPORTED -> R.string.sound_source_imported
                        SoundSource.PHONE -> R.string.sound_source_phone
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onPreview) { Text(stringResource(R.string.sound_play)) }
        if (sound.source != SoundSource.BUNDLED) {
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
            sections = listOf(
                SoundSection(SoundGroup.ANIMALS, listOf(Sound(id = 1, displayName = "Chicken", uri = "", source = SoundSource.BUNDLED))),
                SoundSection(
                    SoundGroup.PHONE,
                    listOf(Sound(id = 2, displayName = "Chime", uri = "", source = SoundSource.PHONE, enabled = false))
                )
            ),
            canImport = true,
            importSummary = ImportSummary(added = 3, tooLong = 1, notAudio = 0),
            onImport = {},
            onAddPhoneSound = {},
            onAllEnabled = {},
            onGroupEnabled = { _, _ -> },
            onPreview = {},
            onToggle = { _, _ -> },
            onRemove = {}
        )
    }
}
