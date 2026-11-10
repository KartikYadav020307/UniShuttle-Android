package com.unishuttle.app.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unishuttle.app.domain.model.AppUser
import com.unishuttle.app.domain.repository.AuthRepository
import com.unishuttle.app.domain.repository.SeatAvailabilityRepository
import com.unishuttle.app.driver.DriverDutyCoordinator
import com.unishuttle.app.driver.DutyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DriverDashboardUiState(
    val driver: AppUser? = null,
    val dutyState: DutyState = DutyState.OffDuty,
    val availableSeats: Int = 0,
    val totalSeats: Int = 0,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    val isOnDuty: Boolean get() = dutyState is DutyState.OnDuty
    val isTransitioning: Boolean get() = dutyState is DutyState.Starting || dutyState is DutyState.Stopping
}

@HiltViewModel
class DriverDashboardViewModel @Inject constructor(
    application: Application,
    private val authRepository: AuthRepository,
    private val seatAvailabilityRepository: SeatAvailabilityRepository,
    private val dutyCoordinator: DriverDutyCoordinator,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DriverDashboardUiState())
    val uiState: StateFlow<DriverDashboardUiState> = _uiState.asStateFlow()
    val dutyState: StateFlow<DutyState> = dutyCoordinator.dutyState

    init {
        loadDriver()
        observeDutyState()
        observeSeats()
    }

    fun toggleDuty(hasLocationPermission: Boolean = true) {
        val current = _uiState.value
        if (current.isTransitioning) return

        if (current.isOnDuty) {
            viewModelScope.launch {
                dutyCoordinator.goOffDuty()
            }
        } else {
            if (!hasLocationPermission) {
                _uiState.update { it.copy(errorMessage = "Location permission is required to broadcast location.") }
                return
            }
            viewModelScope.launch {
                dutyCoordinator.goOnDuty()
            }
        }
    }

    fun retryCleanup() {
        viewModelScope.launch {
            dutyCoordinator.goOffDuty()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun incrementSeat() = adjustSeats(delta = 1)
    fun decrementSeat() = adjustSeats(delta = -1)

    private fun adjustSeats(delta: Int) {
        val state = _uiState.value
        val driver = state.driver ?: return
        if (state.totalSeats <= 0) return
        val newAvailable = (state.availableSeats + delta).coerceIn(0, state.totalSeats)
        if (newAvailable == state.availableSeats) return

        _uiState.update { it.copy(availableSeats = newAvailable, isSaving = true) }

        viewModelScope.launch {
            runCatching {
                seatAvailabilityRepository.updateSeatAvailability(
                    shuttleId = driver.shuttleId,
                    availableSeats = newAvailable,
                    totalSeats = state.totalSeats,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, errorMessage = null) }
            }.onFailure { err ->
                _uiState.update { it.copy(isSaving = false, errorMessage = err.message) }
            }
        }
    }

    private fun loadDriver() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            _uiState.update { it.copy(driver = user) }
        }
    }

    private fun observeDutyState() {
        viewModelScope.launch {
            dutyCoordinator.dutyState.collect { state ->
                _uiState.update { current ->
                    current.copy(
                        dutyState = state,
                        errorMessage = when (state) {
                            is DutyState.Error -> state.message
                            is DutyState.CleanupFailed -> state.message
                            else -> current.errorMessage
                        },
                    )
                }
            }
        }
    }

    private fun observeSeats() {
        viewModelScope.launch {
            seatAvailabilityRepository.observeSeatAvailability()
                .catch { /* non-critical */ }
                .collect { list ->
                    val shuttleId = _uiState.value.driver?.shuttleId ?: return@collect
                    val mine = list.firstOrNull { it.shuttleId == shuttleId } ?: return@collect
                    _uiState.update {
                        it.copy(
                            availableSeats = mine.availableSeats,
                            totalSeats = mine.totalSeats,
                        )
                    }
                }
        }
    }
}
