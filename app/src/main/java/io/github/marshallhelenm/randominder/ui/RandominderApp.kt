package io.github.marshallhelenm.randominder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.marshallhelenm.randominder.R
import io.github.marshallhelenm.randominder.ui.components.AppBottomBar
import io.github.marshallhelenm.randominder.ui.components.AppDestination
import io.github.marshallhelenm.randominder.ui.components.AppTopBar
import io.github.marshallhelenm.randominder.ui.reminders.EditReminderScreen
import io.github.marshallhelenm.randominder.ui.reminders.EditReminderViewModel
import io.github.marshallhelenm.randominder.ui.reminders.RemindersScreen
import io.github.marshallhelenm.randominder.ui.sounds.SoundsSection

private enum class AppScreen { Reminders, Sounds, EditReminder }

@Composable
fun RandominderApp(canScheduleExact: Boolean, onAllowExact: () -> Unit, modifier: Modifier = Modifier) {
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Reminders) }
    // Null while on Edit means a new reminder.
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Bumped on every visit to Edit so each visit gets a fresh form instead of the last one's leftovers.
    var editSession by rememberSaveable { mutableIntStateOf(0) }

    fun openEditor(id: Long?) {
        editingId = id
        editSession++
        currentScreen = AppScreen.EditReminder
    }

    fun closeEditor() {
        editingId = null
        currentScreen = AppScreen.Reminders
    }

    // Owned here rather than by the edit screen so every way out (back, Cancel, the tab bar) can check it.
    val editViewModel = if (currentScreen == AppScreen.EditReminder) {
        viewModel<EditReminderViewModel>(key = "edit-$editSession")
    } else {
        null
    }
    // Where the user was heading when asked whether to discard their edits.
    var pendingLeave by rememberSaveable { mutableStateOf<AppScreen?>(null) }

    fun leave(to: AppScreen) {
        when {
            currentScreen != AppScreen.EditReminder -> currentScreen = to
            editViewModel?.hasUnsavedChanges() == true -> pendingLeave = to
            else -> {
                editingId = null
                currentScreen = to
            }
        }
    }

    // Reminders is the root, so back there falls through to the system and closes the app.
    BackHandler(enabled = currentScreen != AppScreen.Reminders) { leave(AppScreen.Reminders) }

    pendingLeave?.let { destination ->
        DiscardChangesDialog(
            onDiscard = {
                pendingLeave = null
                editingId = null
                currentScreen = destination
            },
            onKeepEditing = { pendingLeave = null }
        )
    }

    val title = when (currentScreen) {
        AppScreen.Reminders -> stringResource(R.string.app_name)
        AppScreen.Sounds -> stringResource(R.string.tab_sounds)
        AppScreen.EditReminder ->
            stringResource(if (editingId == null) R.string.new_reminder else R.string.edit_reminder)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AppTopBar(title = title, onBack = if (currentScreen == AppScreen.EditReminder) { { leave(AppScreen.Reminders) } } else null)
        },
        bottomBar = {
            AppBottomBar(
                // Edit is a sub-screen of Reminders.
                current = if (currentScreen == AppScreen.Sounds) AppDestination.Sounds else AppDestination.Reminders,
                onSelect = { selected ->
                    leave(
                        when (selected) {
                            AppDestination.Reminders -> AppScreen.Reminders
                            AppDestination.Sounds -> AppScreen.Sounds
                        }
                    )
                }
            )
        },
        floatingActionButton = {
            if (currentScreen == AppScreen.Reminders) {
                FloatingActionButton(onClick = { openEditor(null) }) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.new_reminder))
                }
            }
        }
    ) { innerPadding ->
        val screenModifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
        when (currentScreen) {
            AppScreen.Reminders -> RemindersScreen(
                canScheduleExact = canScheduleExact,
                onAllowExact = onAllowExact,
                onOpen = ::openEditor,
                modifier = screenModifier
            )

            AppScreen.Sounds -> SoundsSection(modifier = screenModifier.fillMaxWidth().padding(horizontal = 16.dp))

            AppScreen.EditReminder -> editViewModel?.let {
                EditReminderScreen(
                    reminderId = editingId,
                    viewModel = it,
                    onDone = ::closeEditor,
                    onCancel = { leave(AppScreen.Reminders) },
                    modifier = screenModifier
                )
            }
        }
    }
}

@Composable
private fun DiscardChangesDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(stringResource(R.string.discard_title)) },
        text = { Text(stringResource(R.string.discard_body)) },
        confirmButton = {
            TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onKeepEditing) { Text(stringResource(R.string.keep_editing)) } }
    )
}
