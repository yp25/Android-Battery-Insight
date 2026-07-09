package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.service.BatteryMonitoringService
import com.example.service.BatterySyncWorker
import com.example.ui.BatteryDashboardScreen
import com.example.ui.BatteryViewModel
import com.example.ui.theme.MyApplicationTheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val viewModel: BatteryViewModel by viewModels()

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startBatteryService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Check and request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startBatteryService()
            }
        } else {
            startBatteryService()
        }

        // 2. Schedule unique periodic WorkManager task for database cleanup
        scheduleBackgroundCleanup()

        // 3. Render modern compose UI dashboard
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    BatteryDashboardScreen(viewModel = viewModel)
                }
            }
        }
    }

    private fun startBatteryService() {
        val intent = Intent(this, BatteryMonitoringService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            // Log or fallback
        }
    }

    private fun scheduleBackgroundCleanup() {
        try {
            val syncRequest = PeriodicWorkRequestBuilder<BatterySyncWorker>(24, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "battery_cleanup_work",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        } catch (e: Exception) {
            // WorkManager fallback
        }
    }
}
