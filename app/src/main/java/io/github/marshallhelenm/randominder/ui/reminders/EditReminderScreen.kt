package io.github.marshallhelenm.randominder.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.marshallhelenm.randominder.R
import io.github.marshallhelenm.randominder.model.Sound
import io.github.marshallhelenm.randominder.sounds.SoundSection
import io.github.marshallhelenm.randominder.ui.theme.RandominderTheme
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle

@Composable
fun EditReminderScreen(
    reminderId: Long?,
    viewModel: EditReminderViewModel,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Keyed on the ViewModel too: reopening the same reminder (e.g. from its notification) brings a fresh
    // ViewModel with the same id, and it still has to load.
    LaunchedEffect(viewModel, reminderId) { viewModel.start(reminderId) }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val showErrors by viewModel.showErrors.collectAsStateWithLifecycle()
    val soundSections by viewModel.soundSections.collectAsStateWithLifecycle()
    val current = form ?: return
    ReminderFormContent(
        form = current,
        errors = if (showErrors) current.errors else emptySet(),
        isNew = reminderId == null,
        onChange = viewModel::update,
        onSave = { viewModel.save(onDone) },
        onCancel = onCancel,
        onDelete = { viewModel.delete(onDone) },
        soundSections = soundSections,
        onPreviewSound = viewModel::preview,
        modifier = modifier
    )
}

@Composable
private fun ReminderFormContent(
    form: ReminderForm,
    errors: Set<FormError>,
    isNew: Boolean,
    onChange: ((ReminderForm) -> ReminderForm) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    soundSections: List<SoundSection>,
    onPreviewSound: (Sound) -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        OutlinedTextField(
            value = form.name,
            onValueChange = { name -> onChange { it.copy(name = name) } },
            label = { Text(stringResource(R.string.reminder_name)) },
            isError = FormError.NAME_MISSING in errors,
            supportingText = if (FormError.NAME_MISSING in errors) {
                { Text(stringResource(R.string.error_name_missing)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reminder_enabled), modifier = Modifier.weight(1f))
            Switch(checked = form.enabled, onCheckedChange = { on -> onChange { it.copy(enabled = on) } })
        }

        Section(title = stringResource(R.string.reminder_window)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeButton(
                    label = stringResource(R.string.reminder_window_start),
                    time = form.windowStart,
                    onPick = { time -> onChange { it.copy(windowStart = time) } }
                )
                Text("–")
                TimeButton(
                    label = stringResource(R.string.reminder_window_end),
                    time = form.windowEnd,
                    onPick = { time -> onChange { it.copy(windowEnd = time) } }
                )
            }
            if (FormError.WINDOW_EMPTY in errors) ErrorText(stringResource(R.string.error_window_empty))
        }

        Section(title = stringResource(R.string.reminder_ping_count)) {
            PingCountStepper(count = form.pingCount, onChange = { count -> onChange { it.copy(pingCount = count) } })
            if (form.pingsMayBunch) {
                Text(
                    text = stringResource(R.string.pings_may_bunch),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Section(title = stringResource(R.string.reminder_days)) {
            DayChips(selected = form.daysOfWeek, onChange = { days -> onChange { it.copy(daysOfWeek = days) } })
            if (FormError.NO_DAYS in errors) ErrorText(stringResource(R.string.error_no_days))
        }

        Section(title = stringResource(R.string.reminder_sounds)) {
            ReminderSoundsChoice(
                useAllSounds = form.useAllSounds,
                chosenSoundIds = form.chosenSoundIds,
                sections = soundSections,
                showError = FormError.NO_SOUNDS in errors,
                onUseAllSounds = { all -> onChange { it.copy(useAllSounds = all) } },
                onChosenChange = { ids -> onChange { it.copy(chosenSoundIds = ids) } },
                onPreview = onPreviewSound
            )
        }

        Section(title = stringResource(R.string.reminder_messages)) {
            Text(
                text = stringResource(R.string.reminder_messages_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            form.messages.forEachIndexed { index, text ->
                MessageField(
                    text = text,
                    onTextChange = { new -> onChange { it.copy(messages = it.messages.replaceAt(index, new)) } },
                    onRemove = { onChange { it.copy(messages = it.messages.removeAt(index)) } }
                )
            }
            TextButton(onClick = { onChange { it.copy(messages = it.messages + "") } }) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.reminder_add_message))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onSave) { Text(stringResource(R.string.save)) }
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            Spacer(Modifier.weight(1f))
            if (!isNew) {
                TextButton(onClick = { confirmingDelete = true }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.delete_reminder_title, form.name)) },
            text = { Text(stringResource(R.string.delete_reminder_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text = text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeButton(label: String, time: LocalTime, onPick: (LocalTime) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val shown = formatTime(time)
    // Without this a screen reader only hears "9:00 AM", not which end of the window it is.
    val description = stringResource(R.string.time_button_description, label, shown)
    OutlinedButton(onClick = { picking = true }, modifier = Modifier.semantics { contentDescription = description }) {
        Text(shown)
    }
    if (!picking) return
    val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute)
    // Typing is quicker than the dial for exact times, so both are offered.
    var typing by rememberSaveable { mutableStateOf(false) }
    TimePickerDialog(
        onDismissRequest = { picking = false },
        title = { Text(label) },
        confirmButton = {
            TextButton(onClick = {
                picking = false
                onPick(LocalTime.of(state.hour, state.minute))
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        modeToggleButton = {
            TextButton(onClick = { typing = !typing }) {
                Text(stringResource(if (typing) R.string.time_use_dial else R.string.time_use_keyboard))
            }
        }
    ) {
        if (typing) TimeInput(state) else TimePicker(state)
    }
}

@Composable
private fun PingCountStepper(count: Int, onChange: (Int) -> Unit) {
    val range = ReminderForm.PING_RANGE
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        FilledTonalIconButton(onClick = { onChange(count - 1) }, enabled = count > range.first) {
            Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.ping_count_decrease))
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(40.dp)
        )
        FilledTonalIconButton(onClick = { onChange(count + 1) }, enabled = count < range.last) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.ping_count_increase))
        }
    }
}

@Composable
private fun DayChips(selected: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    val locale = currentLocale()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        orderedWeek(locale).forEach { day ->
            val on = day in selected
            FilterChip(
                selected = on,
                onClick = { onChange(if (on) selected - day else selected + day) },
                label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) }
            )
        }
    }
}

@Composable
private fun MessageField(text: String, onTextChange: (String) -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.reminder_remove_message))
        }
    }
}

private fun <T> List<T>.replaceAt(index: Int, value: T) = toMutableList().also { it[index] = value }

private fun <T> List<T>.removeAt(index: Int) = filterIndexed { i, _ -> i != index }

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun ReminderFormPreview() {
    RandominderTheme {
        ReminderFormContent(
            form = ReminderForm(name = "", messages = listOf("Reach for the sky", "")),
            errors = setOf(FormError.NAME_MISSING, FormError.NO_DAYS),
            isNew = false,
            onChange = {},
            onSave = {},
            onCancel = {},
            onDelete = {},
            soundSections = emptyList(),
            onPreviewSound = {}
        )
    }
}
