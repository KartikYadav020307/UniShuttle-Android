package com.unishuttle.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class UniShuttleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        createNotificationChannel()
        requestFcmRegistrationToken()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Shuttle Alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notifications for shuttle arrivals and announcements"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun requestFcmRegistrationToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener {
            Log.d("UniShuttle", "FCM registration token available")
        }
    }

    private companion object {
        const val CHANNEL_ID = "shuttle_alerts"
    }
}
