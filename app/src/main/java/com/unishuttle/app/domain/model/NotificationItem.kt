package com.unishuttle.app.domain.model

data class NotificationItem(
    val id: String,
    val title: String,
    val body: String,
    val receivedAtMillis: Long,
    val type: NotificationType,
)

enum class NotificationType {
    SHUTTLE_ARRIVAL,
    ANNOUNCEMENT,
    OTHER,
}
