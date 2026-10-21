package com.unishuttle.app.presentation.viewmodel

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unishuttle.app.domain.model.AppUser
import com.unishuttle.app.domain.model.SeatAvailability
import com.unishuttle.app.domain.repository.AuthRepository
import com.unishuttle.app.domain.repository.SeatAvailabilityRepository
import com.unishuttle.app.driver.DriverLocationService
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
    val isOnDuty: Boolean = false,
    val availableSeats: Int = 0,
    val totalSeats: Int = 24,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class DriverDashboardViewModel @Inject constructor(
    application: Application,
    private val authRepository: AuthRepository,
    private val seatAvailabilityRepository: SeatAvailabilityRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DriverDashboardUiState())
    val uiState: StateFlow<DriverDashboardUiState> = _uiState.asStateFlow()

    init {
        loadDriver()
        observeSeats()
    }

    fun toggleDuty() {
        val current = _uiState.value
        val driver = current.driver ?: return
        val newOnDuty = !current.isOnDuty

        _uiState.update { it.copy(isOnDuty = newOnDuty) }

        val context = getApplication<Application>()
        if (newOnDuty) {
            // Start the foreground location service — it will broadcast to Firestore
            val intent = DriverLocationService.startIntent(context, driver.shuttleId)
            ContextCompat.startForegroundService(context, intent)
        } else {
            // Stop broadcasting; remove from Firestore so students don't see a stale marker
            val stopIntent = Intent(context, DriverLocationService::class.java).apply {
                action = DriverLocationService.ACTION_STOP
            }
            context.stopService(stopIntent)

            viewModelScope.launch {
                // Write a sentinel "off-duty" value (updatedAtMillis = 0) so Firestore
                // listeners know this shuttle is no longer active.
                // The StudentMapViewModel already filters by staleness, so this is belt-and-suspenders.
                runCatching {
                    seatAvailabilityRepository.updateSeatAvailability(
                        shuttleId = driver.shuttleId,
                        availableSeats = _uiState.value.availableSeats,
                        totalSeats = _uiState.value.totalSeats,
                        updatedAtMillis = _uiState.value.let { s ->
                            seatAvailabilityRepository.updateSeatAvailability(
                                s.driver?.shuttleId ?: return@let 0L,
                                s.availableSeats, s.totalSeats, System.currentTimeMillis(),
                            )
                            0L
                        },
                    )
                }
            }
        }
    }

    fun incrementSeat() = adjustSeats(delta = 1)
    fun decrementSeat() = adjustSeats(delta = -1)

    private fun adjustSeats(delta: Int) {
        val state = _uiState.value
        val driver = state.driver ?: return
        val newAvailable = (state.availableSeats + delta).coerceIn(0, state.totalSeats)
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

    private fun observeSeats() {
        viewModelScope.launch {
            seatAvailabilityRepository.observeSeatAvailability()
                .catch { /* non-critical */ }
                .collect { list ->
                    val shuttleId = _uiState.value.driver?.shuttleId ?: return@collect
                    val mine = list.firstOrNull { it.shuttleId == shuttleId } ?: return@collect
                    _uiState.update {
                        it.copy(availableSeats = mine.availableSeats, totalSeats = mine.totalSeats)
                    }
                }
        }
    }
}
