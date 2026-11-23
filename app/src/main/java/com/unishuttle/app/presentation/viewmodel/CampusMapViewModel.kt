package com.unishuttle.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unishuttle.app.domain.model.CampusRoute
import com.unishuttle.app.domain.model.RouteGeometry
import com.unishuttle.app.domain.model.ShuttleStop
import com.unishuttle.app.domain.repository.CampusRouteRepository
import com.unishuttle.app.domain.repository.NotificationRepository
import com.unishuttle.app.domain.repository.RouteRepository
import com.unishuttle.app.domain.repository.SeatAvailabilityRepository
import com.unishuttle.app.domain.repository.ShuttleLocationRepository
import com.unishuttle.app.domain.repository.ShuttleStopRepository
import com.unishuttle.app.presentation.ui.screen.map.CampusMapDefaults
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
    private val campusRouteRepository: CampusRouteRepository,
    private val routeRepository: RouteRepository,
    private val seatAvailabilityRepository: SeatAvailabilityRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CampusMapUiState())
    val uiState: StateFlow<CampusMapUiState> = _uiState.asStateFlow()

    private var etaRefreshJob: Job? = null
    private var lastEtaRefreshMillis = 0L

    init {
        observeRoutes()
        observeRouteGeometries()
        observeShuttles()
        observeStops()
        observeSeatAvailability()
        observeAlerts()
    }

    fun selectRoute(routeId: String?) {
        val currentStopId = _uiState.value.selectedStopId
        val routes = _uiState.value.routes

        // Stop sheet retention/dismissal rule:
        // If the selected stop does not belong to the newly chosen route, dismiss it.
        val shouldKeepStop = if (currentStopId != null && routeId != null) {
            val targetRoute = routes.firstOrNull { it.id == routeId }
            targetRoute?.stopIds?.contains(currentStopId) == true
        } else {
            true // "All routes" preserves selection
        }

        _uiState.update { state ->
            state.copy(
                selectedRouteId = routeId,
                selectedStopId = if (shouldKeepStop) state.selectedStopId else null,
            )
        }

        refreshEtas(force = true)
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

    private fun observeRouteGeometries() {
        viewModelScope.launch {
            routeRepository.observeRouteGeometries()
                .catch { /* Room geometry observation fallback */ }
                .collect { geometries ->
                    _uiState.update { it.copy(routeGeometries = geometries) }
                }
        }
    }

    private fun observeRoutes() {
        viewModelScope.launch {
            campusRouteRepository.observeRoutes()
                .catch { error ->
                    _uiState.update { state ->
                        val selectedRouteId = state.selectedRouteId
                            ?.takeIf { id -> CampusMapDefaults.DefaultRoutes.any { it.id == id } }
                        state.copy(
                            routes = CampusMapDefaults.DefaultRoutes,
                            selectedRouteId = selectedRouteId,
                            isUsingFallbackRoutes = true,
                        )
                    }
                    loadRouteGeometries(CampusMapDefaults.DefaultRoutes, _uiState.value.stops)
                }
                .collect { routes ->
                    val isFallback = routes == CampusMapDefaults.DefaultRoutes
                    _uiState.update { state ->
                        val selectedRouteId = state.selectedRouteId
                            ?.takeIf { id -> routes.any { it.id == id } }
                        state.copy(
                            routes = routes,
                            selectedRouteId = selectedRouteId,
                            isUsingFallbackRoutes = isFallback,
                        )
                    }
                    loadRouteGeometries(routes, _uiState.value.stops)
                    refreshEtas(force = true)
                }
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
                    val fallback = CampusMapDefaults.DefaultStops
                    _uiState.update {
                        it.copy(
                            stops = fallback,
                            isUsingFallbackStops = true,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                    loadRouteGeometries(_uiState.value.routes, fallback)
                    refreshEtas(force = true)
                }
                .collect { stops ->
                    val isFallback = stops.isNotEmpty() && stops == CampusMapDefaults.DefaultStops
                    _uiState.update {
                        it.copy(
                            stops = stops,
                            isUsingFallbackStops = isFallback,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                    loadRouteGeometries(_uiState.value.routes, stops)
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

    private fun loadRouteGeometries(routes: List<CampusRoute>, stops: List<ShuttleStop>) {
        if (routes.isEmpty() || stops.isEmpty()) return

        viewModelScope.launch {
            val geometries = mutableListOf<RouteGeometry>()
            for (route in routes) {
                routeRepository.getRouteGeometry(route, stops)
                    .onSuccess { geometry ->
                        geometries.add(geometry)
                    }
                    .onFailure { error ->
                        // Non-critical; individual route geometry failure logs warning
                    }
            }
            if (geometries.isNotEmpty()) {
                _uiState.update { it.copy(routeGeometries = geometries) }
            }
        }
    }

    private fun refreshEtas(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val state = _uiState.value
        val freshShuttles = state.shuttles.filter {
            it.updatedAtMillis >= now - SHUTTLE_FRESHNESS_MILLIS
        }
        if (state.stops.isEmpty() || freshShuttles.isEmpty()) {
            etaRefreshJob?.cancel()
            _uiState.update { it.copy(stopEtas = emptyList()) }
            return
        }
        if (!force && now - lastEtaRefreshMillis < ETA_REFRESH_INTERVAL_MILLIS) return

        etaRefreshJob?.cancel()
        etaRefreshJob = viewModelScope.launch {
            lastEtaRefreshMillis = now
            val allEtas = mutableListOf<com.unishuttle.app.domain.model.StopEta>()

            // 1. Calculate directional wraparound ETAs for configured routes
            for (route in state.routes) {
                routeRepository.getEtasForRoute(route, freshShuttles, state.stops)
                    .onSuccess { etas -> allEtas.addAll(etas) }
            }

            // 2. Also calculate legacy fallback ETAs for unassigned shuttles
            val unassignedShuttles = freshShuttles.filter { it.routeId.isNullOrBlank() }
            for (shuttle in unassignedShuttles) {
                routeRepository.getEtasFromShuttleToStops(shuttle, state.stops)
                    .onSuccess { etas -> allEtas.addAll(etas) }
            }

            _uiState.update { it.copy(stopEtas = allEtas) }
        }
    }

    private companion object {
        const val ETA_REFRESH_INTERVAL_MILLIS = 15_000L
        const val SHUTTLE_FRESHNESS_MILLIS = 5 * 60 * 1_000L
    }
}
