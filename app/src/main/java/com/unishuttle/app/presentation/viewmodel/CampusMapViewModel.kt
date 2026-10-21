package com.unishuttle.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unishuttle.app.domain.model.CampusLatLng
import com.unishuttle.app.domain.model.ShuttleStop
import com.unishuttle.app.domain.repository.NotificationRepository
import com.unishuttle.app.domain.repository.RouteRepository
import com.unishuttle.app.domain.repository.SeatAvailabilityRepository
import com.unishuttle.app.domain.repository.ShuttleLocationRepository
import com.unishuttle.app.domain.repository.ShuttleStopRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CampusMapViewModel @Inject constructor(
    private val shuttleLocationRepository: ShuttleLocationRepository,
    private val shuttleStopRepository: ShuttleStopRepository,
    private val routeRepository: RouteRepository,
    private val seatAvailabilityRepository: SeatAvailabilityRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CampusMapUiState())
    val uiState: StateFlow<CampusMapUiState> = _uiState.asStateFlow()
    private var etaRefreshJob: Job? = null
    private var lastEtaRefreshMillis = 0L

    init {
        observeShuttles()
        observeStops()
        observeSeatAvailability()
        observeAlerts()
    }

    fun selectStop(stopId: String) {
        _uiState.update { it.copy(selectedStopId = stopId) }
    }

    fun dismissStopDetails() {
        _uiState.update { it.copy(selectedStopId = null) }
    }

    fun subscribeToStopAlert(stopId: String, stopName: String, thresholdMinutes: Int) {
        viewModelScope.launch {
            notificationRepository.subscribeToStopAlert(stopId, stopName, thresholdMinutes)
        }
    }

    fun unsubscribeFromStopAlert(stopId: String) {
        viewModelScope.launch {
            notificationRepository.unsubscribeFromStopAlert(stopId)
        }
    }

    private fun observeShuttles() {
        viewModelScope.launch {
            shuttleLocationRepository.observeShuttleLocations()
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Unable to load shuttle locations.",
                        )
                    }
                }
                .collect { shuttles ->
                    _uiState.update {
                        it.copy(
                            shuttles = shuttles,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                    refreshEtas()
                }
        }
    }

    private fun observeStops() {
        viewModelScope.launch {
            shuttleStopRepository.observeStops()
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Unable to load shuttle stops.",
                        )
                    }
                }
                .collect { stops ->
                    _uiState.update {
                        it.copy(
                            stops = stops,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                    loadRoute(stops)
                    refreshEtas(force = true)
                }
        }
    }

    private fun observeSeatAvailability() {
        viewModelScope.launch {
            seatAvailabilityRepository.observeSeatAvailability()
                .catch { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Unable to load seat availability.")
                    }
                }
                .collect { seatAvailability ->
                    _uiState.update {
                        it.copy(
                            seatAvailability = seatAvailability,
                            errorMessage = null,
                        )
                    }
                }
        }
    }

    private fun observeAlerts() {
        viewModelScope.launch {
            notificationRepository.observeAlerts()
                .catch { /* silently ignore — alerts are non-critical */ }
                .collect { alerts ->
                    _uiState.update { it.copy(activeAlerts = alerts) }
                }
        }
    }

    private fun loadRoute(stops: List<ShuttleStop>) {
        if (stops.size < 2) {
            _uiState.update { it.copy(routePoints = emptyList()) }
            return
        }

        viewModelScope.launch {
            val waypoints = stops.map { stop ->
                CampusLatLng(
                    latitude = stop.latitude,
                    longitude = stop.longitude,
                )
            }

            routeRepository.getRouteBetweenStops(waypoints)
                .onSuccess { routePoints ->
                    _uiState.update { it.copy(routePoints = routePoints) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Unable to load shuttle route.")
                    }
                }
        }
    }

    private fun refreshEtas(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastEtaRefreshMillis < ETA_REFRESH_INTERVAL_MILLIS) return

        val state = _uiState.value
        if (state.shuttles.isEmpty() || state.stops.isEmpty()) return

        etaRefreshJob?.cancel()
        etaRefreshJob = viewModelScope.launch {
            lastEtaRefreshMillis = now
            val etas = state.shuttles.flatMap { shuttle ->
                routeRepository.getEtasFromShuttleToStops(shuttle, state.stops)
                    .getOrElse { emptyList() }
            }

            _uiState.update { it.copy(stopEtas = etas) }
        }
    }

    private companion object {
        const val ETA_REFRESH_INTERVAL_MILLIS = 15_000L
    }
}
