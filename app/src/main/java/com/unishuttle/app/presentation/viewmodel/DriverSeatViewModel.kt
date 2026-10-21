package com.unishuttle.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unishuttle.app.domain.repository.SeatAvailabilityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DriverSeatViewModel @Inject constructor(
    private val seatAvailabilityRepository: SeatAvailabilityRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DriverSeatUiState())
    val uiState: StateFlow<DriverSeatUiState> = _uiState.asStateFlow()

    init {
        observeCurrentShuttleSeats()
    }

    fun incrementAvailableSeats() {
        val state = _uiState.value
        saveSeatCount((state.availableSeats + 1).coerceAtMost(state.totalSeats))
    }

    fun decrementAvailableSeats() {
        val state = _uiState.value
        saveSeatCount((state.availableSeats - 1).coerceAtLeast(0))
    }

    private fun observeCurrentShuttleSeats() {
        viewModelScope.launch {
            seatAvailabilityRepository.observeSeatAvailability()
                .catch { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Unable to load seats.")
                    }
                }
                .collect { seatAvailability ->
                    val state = _uiState.value
                    val currentSeats = seatAvailability.firstOrNull { it.shuttleId == state.shuttleId }
                    if (currentSeats != null) {
                        _uiState.update {
                            it.copy(
                                availableSeats = currentSeats.availableSeats,
                                totalSeats = currentSeats.totalSeats,
                                errorMessage = null,
                            )
                        }
                    }
                }
        }
    }

    private fun saveSeatCount(availableSeats: Int) {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                availableSeats = availableSeats,
                isSaving = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                seatAvailabilityRepository.updateSeatAvailability(
                    shuttleId = state.shuttleId,
                    availableSeats = availableSeats,
                    totalSeats = state.totalSeats,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            }
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "Unable to update seats.",
                        )
                    }
                }
        }
    }
}
