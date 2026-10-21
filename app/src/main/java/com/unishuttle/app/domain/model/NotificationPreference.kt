package com.unishuttle.app.domain.model

data class NotificationPreference(
    val topicId: String,
    val label: String,
    val enabled: Boolean,
)
