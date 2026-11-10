package com.unishuttle.app.driver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
class DriverNotificationStopReceiver : BroadcastReceiver() {
    @Inject lateinit var dutyCoordinator: DriverDutyCoordinator

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != ACTION_NOTIFICATION_STOP) return
        val isFixtureAction = intent.getBooleanExtra(EXTRA_IS_FIXTURE_ACTION, false)
            || intent.getBooleanExtra(EXTRA_IS_TEST_FIXTURE, false)
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                // Respect Android goAsync() budget with a bounded execution strategy
                withTimeoutOrNull(8_000L) {
                    dutyCoordinator.goOffDuty(
                        shutdownTimeoutMillis = 4_000L,
                        isFixtureAction = isFixtureAction,
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Notification stop execution encountered error", t)
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }

    companion object {
        const val ACTION_NOTIFICATION_STOP = "com.unishuttle.app.driver.ACTION_NOTIFICATION_STOP"
        const val EXTRA_IS_FIXTURE_ACTION = "com.unishuttle.app.driver.EXTRA_IS_FIXTURE_ACTION"
        const val EXTRA_IS_TEST_FIXTURE = "extra_is_test_fixture"
        private const val TAG = "DriverNotifStopReceiver"

        // Application-scoped supervisor job that survives Service destruction
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
