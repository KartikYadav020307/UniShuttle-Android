package com.unishuttle.app.driver

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.unishuttle.app.MainActivity
import com.unishuttle.app.R
import com.unishuttle.app.domain.repository.ShuttleLocationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DriverLocationService : Service() {
    @Inject lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    @Inject lateinit var shuttleLocationRepository: ShuttleLocationRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeShuttleId: String = DEFAULT_SHUTTLE_ID

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val heading = if (location.hasBearing()) location.bearing else 0f

            serviceScope.launch {
                shuttleLocationRepository.updateDriverLocation(
                    shuttleId = activeShuttleId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    heading = heading,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                activeShuttleId = intent?.getStringExtra(EXTRA_SHUTTLE_ID)?.ifBlank { null }
                    ?: DEFAULT_SHUTTLE_ID
                startForeground(NOTIFICATION_ID, buildNotification(activeShuttleId))
                startLocationUpdates()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
        super.onDestroy()
    }

    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            stopSelf()
            return
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_UPDATE_INTERVAL_MILLIS,
        )
            .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL_MILLIS)
            .build()

        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        fusedLocationProviderClient.requestLocationUpdates(
            request,
            locationCallback,
            mainLooper,
        )
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    private fun buildNotification(shuttleId: String): Notification {
        ensureNotificationChannel()

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, DriverLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("UniShuttle driver tracking")
            .setContentText("Broadcasting location for $shuttleId")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Driver tracking",
            NotificationManager.IMPORTANCE_LOW,
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.unishuttle.app.driver.START_LOCATION_UPDATES"
        const val ACTION_STOP = "com.unishuttle.app.driver.STOP_LOCATION_UPDATES"
        const val EXTRA_SHUTTLE_ID = "extra_shuttle_id"

        private const val DEFAULT_SHUTTLE_ID = "demo-shuttle"
        private const val CHANNEL_ID = "driver_tracking"
        private const val NOTIFICATION_ID = 1001
        private const val LOCATION_UPDATE_INTERVAL_MILLIS = 5_000L
        private const val LOCATION_FASTEST_INTERVAL_MILLIS = 2_000L

        fun startIntent(context: Context, shuttleId: String): Intent =
            Intent(context, DriverLocationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SHUTTLE_ID, shuttleId)
            }
    }
}
