package com.unishuttle.app.data.repository

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.unishuttle.app.data.local.dao.NotificationDao
import com.unishuttle.app.data.local.entity.NotificationEntity
import com.unishuttle.app.data.local.entity.NotificationPreferenceEntity
import com.unishuttle.app.data.local.entity.StopAlertEntity
import com.unishuttle.app.domain.model.NotificationItem
import com.unishuttle.app.domain.model.NotificationPreference
import com.unishuttle.app.domain.model.NotificationType
import com.unishuttle.app.domain.model.StopAlert
import com.unishuttle.app.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalNotificationRepository @Inject constructor(
    private val dao: NotificationDao,
    private val messaging: FirebaseMessaging,
) : NotificationRepository {

    // ── Stop Alerts ──

    override fun observeAlerts(): Flow<List<StopAlert>> =
        dao.observeAlerts().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun subscribeToStopAlert(
        stopId: String,
        stopName: String,
        thresholdMinutes: Int,
    ) {
        // Remove any existing alert for this stop first
        val existing = dao.getAlert(stopId)
        if (existing != null) {
            unsubscribeFcmTopic(existing.stopId, existing.thresholdMinutes)
        }

        // Subscribe to the FCM topic
        val topic = fcmTopic(stopId, thresholdMinutes)
        messaging.subscribeToTopic(topic).await()
        Log.d(TAG, "Subscribed to FCM topic: $topic")

        // Persist the alert locally
        dao.insertAlert(
            StopAlertEntity(
                stopId = stopId,
                stopName = stopName,
                thresholdMinutes = thresholdMinutes,
                subscribedAtMillis = System.currentTimeMillis(),
            ),
        )

        // Also create/update a preference entry so it appears in the Notification Center
        val prefTopicId = "stop_$stopId"
        if (dao.getPreference(prefTopicId) == null) {
            dao.insertPreference(
                NotificationPreferenceEntity(
                    topicId = prefTopicId,
                    label = stopName,
                    enabled = true,
                ),
            )
        }
    }

    override suspend fun unsubscribeFromStopAlert(stopId: String) {
        val existing = dao.getAlert(stopId) ?: return
        unsubscribeFcmTopic(existing.stopId, existing.thresholdMinutes)
        dao.deleteAlert(stopId)
    }

    // ── Notification History ──

    override fun observeNotificationHistory(): Flow<List<NotificationItem>> =
        dao.observeNotifications().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun addNotification(item: NotificationItem) {
        dao.insertNotification(
            NotificationEntity(
                id = item.id,
                title = item.title,
                body = item.body,
                receivedAtMillis = item.receivedAtMillis,
                type = item.type.name,
            ),
        )
    }

    // ── Notification Preferences ──

    override fun observePreferences(): Flow<List<NotificationPreference>> =
        dao.observePreferences().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun setPreferenceEnabled(topicId: String, enabled: Boolean) {
        dao.updatePreferenceEnabled(topicId, enabled)

        // Subscribe or unsubscribe from the underlying FCM topic
        if (enabled) {
            messaging.subscribeToTopic(topicId).await()
        } else {
            messaging.unsubscribeFromTopic(topicId).await()
        }
    }

    // ── Private helpers ──

    private suspend fun unsubscribeFcmTopic(stopId: String, thresholdMinutes: Int) {
        val topic = fcmTopic(stopId, thresholdMinutes)
        messaging.unsubscribeFromTopic(topic).await()
        Log.d(TAG, "Unsubscribed from FCM topic: $topic")
    }

    private companion object {
        const val TAG = "NotificationRepo"

        fun fcmTopic(stopId: String, thresholdMinutes: Int): String =
            "stop_${stopId}_${thresholdMinutes}min"

        // ── Entity → Domain mappers ──

        fun StopAlertEntity.toDomain() = StopAlert(
            stopId = stopId,
            stopName = stopName,
            thresholdMinutes = thresholdMinutes,
            subscribedAtMillis = subscribedAtMillis,
        )

        fun NotificationEntity.toDomain() = NotificationItem(
            id = id,
            title = title,
            body = body,
            receivedAtMillis = receivedAtMillis,
            type = runCatching { NotificationType.valueOf(type) }
                .getOrDefault(NotificationType.OTHER),
        )

        fun NotificationPreferenceEntity.toDomain() = NotificationPreference(
            topicId = topicId,
            label = label,
            enabled = enabled,
        )
    }
}
