package com.unishuttle.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_preferences")
data class NotificationPreferenceEntity(
    @PrimaryKey
    val topicId: String,
    val label: String,
    val enabled: Boolean,
)
