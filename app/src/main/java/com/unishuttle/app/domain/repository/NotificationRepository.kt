package com.unishuttle.app.domain.repository

import com.unishuttle.app.domain.model.NotificationItem
import com.unishuttle.app.domain.model.NotificationPreference
import com.unishuttle.app.domain.model.StopAlert
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun observeAlerts(): Flow<List<StopAlert>>

    suspend fun subscribeToStopAlert(
        stopId: String,
        stopName: String,
        thresholdMinutes: Int,
    )

    suspend fun unsubscribeFromStopAlert(stopId: String)

    fun observeNotificationHistory(): Flow<List<NotificationItem>>

    suspend fun addNotification(item: NotificationItem)

    fun observePreferences(): Flow<List<NotificationPreference>>

    suspend fun setPreferenceEnabled(topicId: String, enabled: Boolean)
}
