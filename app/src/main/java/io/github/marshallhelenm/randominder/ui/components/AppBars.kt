package io.github.marshallhelenm.randominder.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.marshallhelenm.randominder.R
import io.github.marshallhelenm.randominder.ui.theme.TitleFontFamily

// The app's top-level screens, one per bottom bar item.
enum class AppDestination(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    Reminders(R.string.tab_reminders, R.drawable.ic_notification),
    Sounds(R.string.tab_sounds, R.drawable.ic_music_note)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)?, showLogo: Boolean = false) {
    CenterAlignedTopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showLogo) {
                    // Decorative: the title next to it already names the app.
                    Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.height(36.dp))
                    Spacer(Modifier.width(10.dp))
                }
                // A bit above the default 22sp so the font's dot pattern stays legible.
                Text(title, fontFamily = TitleFontFamily, fontSize = 26.sp)
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                }
            }
        }
    )
}

@Composable
fun AppBottomBar(current: AppDestination, onSelect: (AppDestination) -> Unit) {
    NavigationBar {
        AppDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == current,
                onClick = { onSelect(destination) },
                icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                label = { Text(stringResource(destination.label)) }
            )
        }
    }
}
