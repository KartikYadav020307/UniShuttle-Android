package com.unishuttle.app.driver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.unishuttle.app.BuildConfig

/**
 * Debug-only foreground app entry point for POCO physical testing of DriverLocationService.
 *
 * Ensures the foreground service is launched from a genuine foreground Activity context
 * in compliance with Android 14+ FGS startup requirements, without polluting Firebase RTDB.
 * This class is strictly compiled into debug builds and rejected in release configurations.
 */
class DebugDriverFixtureActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        Log.i(TAG, "Permissions callback result: $permissions")
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            val shuttleId = intent.getStringExtra(EXTRA_SHUTTLE_ID) ?: "fixture_shuttle_1"
            startFixtureService(shuttleId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            Log.e(TAG, "DebugDriverFixtureActivity invoked in non-debug build; finishing.")
            finish()
            return
        }

        checkAndRequestPermissions()

        val autoStart = intent.getBooleanExtra(EXTRA_AUTO_START, true)
        val shuttleId = intent.getStringExtra(EXTRA_SHUTTLE_ID) ?: "fixture_shuttle_1"

        if (autoStart) {
            startFixtureService(shuttleId)
        }

        setContent {
            var statusText by remember { mutableStateOf("Fixture Service Active: $shuttleId") }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121212))
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "UniShuttle Debug Driver Fixture",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Strictly isolated debug test harness. Telemetry writes to Firebase RTDB are suppressed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Status: $statusText",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF66BB6A),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        startFixtureService(shuttleId)
                        statusText = "Started fixture tracking for $shuttleId"
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start Fixture Service")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        stopViaNotificationAction()
                        statusText = "Stop intent broadcast dispatched"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Stop via Notification Action Broadcast")
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startFixtureService(shuttleId: String) {
        Log.i(TAG, "Starting DriverLocationService in test fixture mode for $shuttleId from foreground activity")
        val intent = DriverLocationService.startIntent(this, shuttleId, requestId = 9001L).apply {
            putExtra(DriverLocationService.EXTRA_IS_TEST_FIXTURE, true)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopViaNotificationAction() {
        Log.i(TAG, "Broadcasting ACTION_NOTIFICATION_STOP from DebugDriverFixtureActivity")
        val stopIntent = Intent(this, DriverNotificationStopReceiver::class.java).apply {
            action = DriverNotificationStopReceiver.ACTION_NOTIFICATION_STOP
            putExtra(DriverNotificationStopReceiver.EXTRA_IS_FIXTURE_ACTION, BuildConfig.DEBUG)
            putExtra(DriverNotificationStopReceiver.EXTRA_IS_TEST_FIXTURE, true)
        }
        sendBroadcast(stopIntent)
    }

    companion object {
        private const val TAG = "DebugDriverFixtureAct"
        const val EXTRA_AUTO_START = "extra_auto_start"
        const val EXTRA_SHUTTLE_ID = "extra_shuttle_id"
    }
}
