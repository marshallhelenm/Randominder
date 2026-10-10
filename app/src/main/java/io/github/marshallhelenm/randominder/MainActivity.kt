package io.github.marshallhelenm.randominder

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import io.github.marshallhelenm.randominder.scheduling.PingScheduler
import io.github.marshallhelenm.randominder.sounds.SoundLibrary
import io.github.marshallhelenm.randominder.ui.RandominderApp
import io.github.marshallhelenm.randominder.ui.theme.RandominderTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Re-read on every resume: both are changed on system screens outside the app.
    private var canScheduleExact by mutableStateOf(true)
    private var notificationsEnabled by mutableStateOf(true)

    // Set when a tapped notification asks for its reminder; cleared once the UI has opened it.
    private var reminderToOpen by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only a fresh launch: after a rotation the request has already been handled.
        if (savedInstanceState == null) reminderToOpen = intent.reminderIdExtra()
        lifecycleScope.launch {
            // Seeds the bundled sounds on first launch and keeps their channels in step afterwards.
            SoundLibrary.from(applicationContext).ensureBundled()
            // A force-stop or a restore cancels alarms without any broadcast, so opening the app re-arms them.
            PingScheduler.from(applicationContext).resume()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            RandominderTheme {
                RandominderApp(
                    reminderToOpen = reminderToOpen,
                    onReminderOpened = { reminderToOpen = null },
                    notificationsEnabled = notificationsEnabled,
                    onEnableNotifications = ::openNotificationSettings,
                    canScheduleExact = canScheduleExact,
                    onAllowExact = ::openExactAlarmSettings
                )
            }
        }
    }

    // singleTop: a notification tapped while the app is open arrives here instead of a second activity.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.reminderIdExtra()?.let { reminderToOpen = it }
    }

    private fun Intent.reminderIdExtra(): Long? = getLongExtra(EXTRA_REMINDER_ID, -1).takeIf { it >= 0 }

    override fun onResume() {
        super.onResume()
        canScheduleExact = AlarmManagerCompat.canScheduleExactAlarms(getSystemService(AlarmManager::class.java))
        // False when the permission is denied or the app's notifications are switched off in settings.
        notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    // The settings page works even after the permission prompt has been permanently denied.
    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
        }
        startActivity(intent)
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName".toUri()))
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "io.github.marshallhelenm.randominder.REMINDER_ID"
    }
}
