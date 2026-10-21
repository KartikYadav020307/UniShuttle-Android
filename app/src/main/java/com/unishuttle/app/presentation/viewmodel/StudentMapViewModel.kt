package com.unishuttle.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unishuttle.app.domain.model.ShuttleLocation
import com.unishuttle.app.domain.repository.ShuttleLocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StudentMapUiState(
    val activeDrivers: List<ShuttleLocation> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class StudentMapViewModel @Inject constructor(
    private val shuttleLocationRepository: ShuttleLocationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudentMapUiState())
    val uiState: StateFlow<StudentMapUiState> = _uiState.asStateFlow()

    init {
        observeActiveDrivers()
    }

    private fun observeActiveDrivers() {
        viewModelScope.launch {
            shuttleLocationRepository.observeShuttleLocations()
                .catch { err ->
                    _uiState.update { it.copy(errorMessage = err.message) }
                }
                .collect { locations ->
                    // Only show shuttles updated in the last 5 minutes (on-duty guard)
                    val cutoff = System.currentTimeMillis() - ON_DUTY_STALENESS_MILLIS
                    _uiState.update {
                        it.copy(
                            activeDrivers = locations.filter { loc -> loc.updatedAtMillis >= cutoff },
                            errorMessage = null,
                        )
                    }
                }
        }
    }

    private companion object {
        const val ON_DUTY_STALENESS_MILLIS = 5 * 60 * 1_000L // 5 minutes
    }
}
