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
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import io.github.marshallhelenm.randominder.sounds.SoundLibrary
import io.github.marshallhelenm.randominder.ui.RandominderApp
import io.github.marshallhelenm.randominder.ui.theme.RandominderTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Re-read on every resume: the grant happens on a system settings screen outside the app.
    private var canScheduleExact by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Seeds the bundled sounds on first launch and keeps their channels in step afterwards.
        lifecycleScope.launch { SoundLibrary.from(applicationContext).ensureBundled() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            RandominderTheme {
                RandominderApp(canScheduleExact = canScheduleExact, onAllowExact = ::openExactAlarmSettings)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        canScheduleExact = AlarmManagerCompat.canScheduleExactAlarms(getSystemService(AlarmManager::class.java))
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName".toUri()))
        }
    }
}
