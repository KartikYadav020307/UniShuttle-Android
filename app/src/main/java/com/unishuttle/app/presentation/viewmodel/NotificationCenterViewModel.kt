package com.unishuttle.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unishuttle.app.domain.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationCenterViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationCenterUiState())
    val uiState: StateFlow<NotificationCenterUiState> = _uiState.asStateFlow()

    init {
        observeNotifications()
        observePreferences()
    }

    fun togglePreference(topicId: String, enabled: Boolean) {
        viewModelScope.launch {
            notificationRepository.setPreferenceEnabled(topicId, enabled)
        }
    }

    private fun observeNotifications() {
        viewModelScope.launch {
            notificationRepository.observeNotificationHistory()
                .catch { /* silently handle — show empty list */ }
                .collect { notifications ->
                    _uiState.update {
                        it.copy(
                            notifications = notifications,
                            isLoading = false,
                        )
                    }
                }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            notificationRepository.observePreferences()
                .catch { /* silently handle */ }
                .collect { preferences ->
                    _uiState.update {
                        it.copy(
                            preferences = preferences,
                            isLoading = false,
                        )
                    }
                }
        }
    }
}
