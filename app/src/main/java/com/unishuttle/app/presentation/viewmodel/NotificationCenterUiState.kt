package com.unishuttle.app.presentation.viewmodel

import com.unishuttle.app.domain.model.NotificationItem
import com.unishuttle.app.domain.model.NotificationPreference

data class NotificationCenterUiState(
    val notifications: List<NotificationItem> = emptyList(),
    val preferences: List<NotificationPreference> = emptyList(),
    val isLoading: Boolean = true,
)
