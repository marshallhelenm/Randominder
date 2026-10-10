package io.github.marshallhelenm.randominder.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.marshallhelenm.randominder.R
import io.github.marshallhelenm.randominder.model.Sound
import io.github.marshallhelenm.randominder.sounds.SoundGroup
import io.github.marshallhelenm.randominder.sounds.SoundSection
import io.github.marshallhelenm.randominder.ui.sounds.soundSections

/** "All sounds" or a hand-picked set, chosen from the same grouped list as the Sounds tab. */
@Composable
fun ReminderSoundsChoice(
    useAllSounds: Boolean,
    chosenSoundIds: Set<Long>,
    sections: List<SoundSection>,
    showError: Boolean,
    onUseAllSounds: (Boolean) -> Unit,
    onChosenChange: (Set<Long>) -> Unit,
    onPreview: (Sound) -> Unit
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.selectableGroup()) {
        ChoiceRow(stringResource(R.string.reminder_sounds_all), selected = useAllSounds) { onUseAllSounds(true) }
        ChoiceRow(stringResource(R.string.reminder_sounds_choose), selected = !useAllSounds) {
            onUseAllSounds(false)
            if (chosenSoundIds.isEmpty()) picking = true
        }
    }
    if (!useAllSounds) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                pluralStringResource(R.plurals.reminder_sounds_chosen, chosenSoundIds.size, chosenSoundIds.size),
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = { picking = true }) { Text(stringResource(R.string.reminder_sounds_pick)) }
        }
        if (showError) {
            Text(
                stringResource(R.string.error_no_sounds),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    if (picking) {
        SoundPicker(
            sections = sections,
            chosen = chosenSoundIds,
            onChosenChange = onChosenChange,
            onPreview = onPreview,
            onDone = { picking = false }
        )
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SoundPicker(
    sections: List<SoundSection>,
    chosen: Set<Long>,
    onChosenChange: (Set<Long>) -> Unit,
    onPreview: (Sound) -> Unit,
    onDone: () -> Unit
) {
    var collapsed by rememberSaveable { mutableStateOf(setOf<SoundGroup>()) }
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.reminder_sounds_pick_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.reminder_sounds_pick_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    soundSections(
                        sections = sections,
                        collapsed = collapsed,
                        onToggleCollapsed = { group -> collapsed = if (group in collapsed) collapsed - group else collapsed + group },
                        headerControl = { section ->
                            val ids = section.sounds.map { it.id }.toSet()
                            val label = stringResource(R.string.reminder_sounds_pick_group, stringResource(section.group.label))
                            Checkbox(
                                checked = chosen.containsAll(ids),
                                onCheckedChange = { onChosenChange(if (it) chosen + ids else chosen - ids) },
                                modifier = Modifier.semantics { contentDescription = label }
                            )
                        },
                        row = { sound ->
                            PickerRow(
                                sound = sound,
                                checked = sound.id in chosen,
                                onCheckedChange = { onChosenChange(if (it) chosen + sound.id else chosen - sound.id) },
                                onPreview = { onPreview(sound) }
                            )
                        }
                    )
                }
                Button(onClick = onDone, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.done)) }
            }
        }
    }
}

@Composable
private fun PickerRow(sound: Sound, checked: Boolean, onCheckedChange: (Boolean) -> Unit, onPreview: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Checkbox),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(sound.displayName, style = MaterialTheme.typography.bodyLarge)
                // The Sounds tab is the master switch: a sound off there never plays, even if chosen here.
                if (!sound.enabled) {
                    Text(
                        stringResource(R.string.reminder_sounds_off_in_tab),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        TextButton(onClick = onPreview) { Text(stringResource(R.string.sound_play)) }
    }
}
