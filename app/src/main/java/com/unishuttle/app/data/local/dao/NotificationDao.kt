package com.unishuttle.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unishuttle.app.data.local.entity.NotificationEntity
import com.unishuttle.app.data.local.entity.NotificationPreferenceEntity
import com.unishuttle.app.data.local.entity.StopAlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    // ── Stop Alerts ──

    @Query("SELECT * FROM stop_alerts ORDER BY subscribedAtMillis DESC")
    fun observeAlerts(): Flow<List<StopAlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: StopAlertEntity)

    @Query("DELETE FROM stop_alerts WHERE stopId = :stopId")
    suspend fun deleteAlert(stopId: String)

    @Query("SELECT * FROM stop_alerts WHERE stopId = :stopId LIMIT 1")
    suspend fun getAlert(stopId: String): StopAlertEntity?

    // ── Notification History ──

    @Query("SELECT * FROM notifications ORDER BY receivedAtMillis DESC")
    fun observeNotifications(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    // ── Notification Preferences ──

    @Query("SELECT * FROM notification_preferences ORDER BY label ASC")
    fun observePreferences(): Flow<List<NotificationPreferenceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreference(preference: NotificationPreferenceEntity)

    @Query("UPDATE notification_preferences SET enabled = :enabled WHERE topicId = :topicId")
    suspend fun updatePreferenceEnabled(topicId: String, enabled: Boolean)

    @Query("SELECT * FROM notification_preferences WHERE topicId = :topicId LIMIT 1")
    suspend fun getPreference(topicId: String): NotificationPreferenceEntity?
}
