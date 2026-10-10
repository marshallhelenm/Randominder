package io.github.marshallhelenm.randominder.ui.sounds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.marshallhelenm.randominder.R
import io.github.marshallhelenm.randominder.model.Sound
import io.github.marshallhelenm.randominder.sounds.SoundGroup
import io.github.marshallhelenm.randominder.sounds.SoundSection

/**
 * The sound pool as collapsible groups, shared by the Sounds tab and a reminder's sound picker. Each caller
 * supplies the group header's control (a switch or a checkbox) and how a sound row looks.
 */
fun LazyListScope.soundSections(
    sections: List<SoundSection>,
    collapsed: Set<SoundGroup>,
    onToggleCollapsed: (SoundGroup) -> Unit,
    headerControl: @Composable (SoundSection) -> Unit,
    row: @Composable (Sound) -> Unit
) {
    sections.forEach { section ->
        item(key = "group-${section.group}") {
            GroupHeader(
                section = section,
                isCollapsed = section.group in collapsed,
                onToggleCollapsed = { onToggleCollapsed(section.group) },
                control = { headerControl(section) }
            )
        }
        if (section.group !in collapsed) {
            items(section.sounds, key = { it.id }) { row(it) }
        }
    }
}

@Composable
private fun GroupHeader(
    section: SoundSection,
    isCollapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    control: @Composable () -> Unit
) {
    val state = stringResource(if (isCollapsed) R.string.group_collapsed else R.string.group_expanded)
    HorizontalDivider()
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onToggleCollapsed)
                .semantics { stateDescription = state }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                modifier = Modifier.rotate(if (isCollapsed) -90f else 0f)
            )
            Text(
                text = stringResource(R.string.sound_group_header, stringResource(section.group.label), section.sounds.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        control()
    }
}
