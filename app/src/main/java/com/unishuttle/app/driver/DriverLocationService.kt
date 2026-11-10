package com.unishuttle.app.driver

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.unishuttle.app.BuildConfig
import com.unishuttle.app.MainActivity
import com.unishuttle.app.R
import com.unishuttle.app.domain.repository.ShuttleLocationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class DriverLocationService : Service() {
    @Inject lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    @Inject lateinit var shuttleLocationRepository: ShuttleLocationRepository
    @Inject lateinit var bridge: DriverServiceBridge

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val shutdownMutex = Mutex()
    private var activeShuttleId: String = ""
    @Volatile private var isTracking: Boolean = false
    @Volatile private var isTestFixture: Boolean = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (!isTracking) return
            val location = result.lastLocation ?: return
            val heading = if (location.hasBearing()) location.bearing else 0f

            serviceScope.launch {
                if (!isTracking) return@launch
                writeMutex.withLock {
                    if (!isTracking) return@withLock
                    if (isTestFixture) {
                        Log.i(TAG, "[TEST_FIXTURE] Intercepted location callback for $activeShuttleId: lat=${location.latitude}, lng=${location.longitude}, heading=$heading (Suppressed RTDB write)")
                        return@withLock
                    }
                    runCatching {
                        shuttleLocationRepository.updateDriverLocation(
                            shuttleId = activeShuttleId,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            heading = heading,
                            updatedAtMillis = System.currentTimeMillis(),
                        )
                    }.onFailure { Log.w(TAG, "Location broadcast failed", it) }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestId = intent?.getLongExtra(EXTRA_START_REQUEST_ID, 0L) ?: 0L
        val shuttleId = intent?.getStringExtra(EXTRA_SHUTTLE_ID)?.trim().orEmpty()

        val requestedFixture = intent?.getBooleanExtra(EXTRA_IS_TEST_FIXTURE, false) == true
        isTestFixture = requestedFixture && BuildConfig.DEBUG
        if (requestedFixture && !BuildConfig.DEBUG) {
            Log.e(TAG, "Test fixture requested in non-debug build; rejecting fixture mode.")
        }

        if (shuttleId.isBlank()) {
            Log.e(TAG, "DriverLocationService started without a valid shuttle ID; failing closed.")
            // Satisfy Android FGS start contract before stopping
            runCatching {
                ensureNotificationChannel()
                val notification = buildNotification("UniShuttle")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            }
            serviceScope.launch {
                bridge.notifyStartComplete(requestId, ServiceStartResult.Failure("Missing or blank shuttle ID."))
            }
            stopSelf()
            return START_NOT_STICKY
        }

        activeShuttleId = shuttleId
        serviceScope.launch { startTracking(requestId) }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    private suspend fun startTracking(requestId: Long) {
        if (!hasLocationPermission()) {
            // Satisfy Android FGS start contract before stopping
            runCatching {
                ensureNotificationChannel()
                val notification = buildNotification(activeShuttleId.ifBlank { "UniShuttle" })
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            }
            bridge.notifyStartComplete(requestId, ServiceStartResult.Failure("Location permission missing."))
            stopSelf()
            return
        }

        try {
            bridge.registerService(isFixture = isTestFixture) { reqId -> serviceScope.launch { executeShutdownHandshake(reqId) } }
            ensureNotificationChannel()
            val notification = buildNotification(activeShuttleId)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                LOCATION_UPDATE_INTERVAL_MILLIS,
            )
                .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL_MILLIS)
                .build()

            // Genuinely await location update registration
            fusedLocationProviderClient.requestLocationUpdates(request, locationCallback, mainLooper).await()
            isTracking = true
            bridge.notifyStartComplete(requestId, ServiceStartResult.Success)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start location tracking", t)
            isTracking = false
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            bridge.notifyStartComplete(requestId, ServiceStartResult.Failure("Failed to initialize FGS", t))
            stopSelf()
        }
    }

    private suspend fun executeShutdownHandshake(requestId: Long = 0L) = shutdownMutex.withLock {
        try {
            isTracking = false
            // Explicitly await location callback removal
            runCatching { fusedLocationProviderClient.removeLocationUpdates(locationCallback).await() }
            // Drain in-flight write
            writeMutex.withLock { /* ensures in-flight network write finished */ }
            // Remove foreground notification
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            // Acknowledgement delivered BEFORE stopSelf()
            bridge.notifyShutdownComplete(ServiceShutdownResult.Success, requestId)
            stopSelf()
        } catch (t: Throwable) {
            Log.e(TAG, "Shutdown handshake encountered error", t)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            bridge.notifyShutdownComplete(ServiceShutdownResult.Failure("Callback removal failed", t), requestId)
            stopSelf()
        }
    }

    override fun onDestroy() {
        isTracking = false
        runCatching { fusedLocationProviderClient.removeLocationUpdates(locationCallback) }
        bridge.forceReset()
        serviceScope.cancel()
        super.onDestroy()
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

        // Broadcast to dedicated DriverNotificationStopReceiver
        val stopIntent = PendingIntent.getBroadcast(
            this,
            1,
            Intent(this, DriverNotificationStopReceiver::class.java).apply {
                action = DriverNotificationStopReceiver.ACTION_NOTIFICATION_STOP
                putExtra(
                    DriverNotificationStopReceiver.EXTRA_IS_FIXTURE_ACTION,
                    isTestFixture && BuildConfig.DEBUG,
                )
                if (isTestFixture) {
                    putExtra(DriverNotificationStopReceiver.EXTRA_IS_TEST_FIXTURE, true)
                }
            },
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
        const val EXTRA_START_REQUEST_ID = "extra_start_request_id"
        const val EXTRA_IS_TEST_FIXTURE = "extra_is_test_fixture"

        private const val CHANNEL_ID = "driver_tracking"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "DriverLocationService"
        private const val LOCATION_UPDATE_INTERVAL_MILLIS = 5_000L
        private const val LOCATION_FASTEST_INTERVAL_MILLIS = 2_000L

        fun startIntent(context: Context, shuttleId: String, requestId: Long = 0L): Intent =
            Intent(context, DriverLocationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SHUTTLE_ID, shuttleId)
                putExtra(EXTRA_START_REQUEST_ID, requestId)
            }
    }
}
