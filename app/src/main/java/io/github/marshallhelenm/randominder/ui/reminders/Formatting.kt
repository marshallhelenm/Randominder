package io.github.marshallhelenm.randominder.ui.reminders

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import io.github.marshallhelenm.randominder.R
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// Read through LocalLocale so text updates when the user changes language without restarting the app.
@Composable
fun currentLocale(): Locale = LocalLocale.current.platformLocale

@Composable
fun formatTime(time: LocalTime): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(currentLocale()).format(time)

@Composable
fun formatWindow(start: LocalTime, end: LocalTime): String = "${formatTime(start)} – ${formatTime(end)}"

@Composable
fun daysText(days: Set<DayOfWeek>): String = when (val description = describeDays(days, currentLocale())) {
    DaysDescription.EveryDay -> stringResource(R.string.days_every_day)
    DaysDescription.Weekdays -> stringResource(R.string.days_weekdays)
    DaysDescription.Weekends -> stringResource(R.string.days_weekends)
    DaysDescription.Never -> stringResource(R.string.days_never)
    is DaysDescription.Listed -> description.names.joinToString(", ")
}
