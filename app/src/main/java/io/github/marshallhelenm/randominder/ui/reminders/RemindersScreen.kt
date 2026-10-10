package io.github.marshallhelenm.randominder.ui.reminders

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
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
import io.github.marshallhelenm.randominder.model.Reminder
import io.github.marshallhelenm.randominder.scheduling.PauseStore
import io.github.marshallhelenm.randominder.ui.theme.RandominderTheme
import java.time.DayOfWeek
import java.time.LocalTime

@Composable
fun RemindersScreen(
    notificationsEnabled: Boolean,
    onEnableNotifications: () -> Unit,
    canScheduleExact: Boolean,
    onAllowExact: () -> Unit,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RemindersViewModel = viewModel()
) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val pausedUntil by viewModel.pausedUntil.collectAsStateWithLifecycle()
    // A pause that has run out (e.g. "until tomorrow" after midnight) no longer counts.
    val pausedMessage = pausedUntil?.takeIf { it > System.currentTimeMillis() }?.let {
        if (it == PauseStore.UNTIL_RESUMED) R.string.paused_until_resumed else R.string.paused_until_tomorrow
    }
    RemindersList(
        reminders = reminders,
        pausedMessage = pausedMessage,
        onResumeAll = viewModel::resumeAll,
        notificationsEnabled = notificationsEnabled,
        onEnableNotifications = onEnableNotifications,
        canScheduleExact = canScheduleExact,
        onAllowExact = onAllowExact,
        onOpen = onOpen,
        onToggle = viewModel::setEnabled,
        modifier = modifier
    )
}

@Composable
private fun RemindersList(
    reminders: List<Reminder>,
    @StringRes pausedMessage: Int?,
    onResumeAll: () -> Unit,
    notificationsEnabled: Boolean,
    onEnableNotifications: () -> Unit,
    canScheduleExact: Boolean,
    onAllowExact: () -> Unit,
    onOpen: (Long) -> Unit,
    onToggle: (Reminder, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Notifications first: with them off, nothing reaches the user at all.
        if (!notificationsEnabled) {
            Notice(R.string.notifications_off_explanation, R.string.notifications_off_button, onEnableNotifications)
        }
        if (!canScheduleExact) Notice(R.string.exact_alarm_explanation, R.string.exact_alarm_button, onAllowExact)
        pausedMessage?.let { Notice(it, R.string.resume, onResumeAll) }
        if (reminders.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.width(160.dp))
                Text(
                    text = stringResource(R.string.reminders_empty),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Bottom padding keeps the last row clear of the add button.
            // Dimmed while paused: the reminders keep their settings but none of them will ping.
            LazyColumn(
                contentPadding = PaddingValues(bottom = 88.dp),
                modifier = Modifier.alpha(if (pausedMessage != null) 0.5f else 1f)
            ) {
                items(reminders, key = { it.id }) { reminder ->
                    ReminderRow(reminder, onOpen = { onOpen(reminder.id) }, onToggle = { onToggle(reminder, it) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun Notice(@StringRes explanation: Int, @StringRes action: Int, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(explanation))
            Button(onClick = onAction) { Text(stringResource(action)) }
        }
    }
}

@Composable
private fun ReminderRow(reminder: Reminder, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    val toggleLabel = stringResource(R.string.reminder_toggle, reminder.name)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = reminder.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${formatWindow(reminder.windowStart, reminder.windowEnd)} · " +
                    pluralStringResource(R.plurals.ping_count, reminder.pingCount, reminder.pingCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = daysText(reminder.daysOfWeek),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = reminder.enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.semantics { contentDescription = toggleLabel }
        )
    }
}

@Composable
fun PauseMenu(onPauseUntilTomorrow: () -> Unit, onPauseUntilResumed: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_pause), contentDescription = stringResource(R.string.pause_menu))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.pause_until_tomorrow)) },
                onClick = {
                    open = false
                    onPauseUntilTomorrow()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.pause_until_resumed)) },
                onClick = {
                    open = false
                    onPauseUntilResumed()
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RemindersListPreview() {
    RandominderTheme {
        RemindersList(
            pausedMessage = R.string.paused_until_tomorrow,
            onResumeAll = {},
            reminders = listOf(
                Reminder(
                    id = 1, name = "Stretch", windowStart = LocalTime.of(9, 0), windowEnd = LocalTime.of(17, 0),
                    pingCount = 4, daysOfWeek = DayOfWeek.entries.toSet() - setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                ),
                Reminder(
                    id = 2, name = "Drink water", enabled = false, windowStart = LocalTime.of(8, 30),
                    windowEnd = LocalTime.of(21, 0), pingCount = 1, daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
                )
            ),
            notificationsEnabled = false,
            onEnableNotifications = {},
            canScheduleExact = false,
            onAllowExact = {},
            onOpen = {},
            onToggle = { _, _ -> }
        )
    }
}
