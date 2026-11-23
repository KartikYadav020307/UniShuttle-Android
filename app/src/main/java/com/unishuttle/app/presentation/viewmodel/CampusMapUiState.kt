package com.unishuttle.app.presentation.viewmodel

import com.unishuttle.app.domain.model.CampusLatLng
import com.unishuttle.app.domain.model.CampusRoute
import com.unishuttle.app.domain.model.RouteGeometry
import com.unishuttle.app.domain.model.SeatAvailability
import com.unishuttle.app.domain.model.ShuttleLocation
import com.unishuttle.app.domain.model.ShuttleStop
import com.unishuttle.app.domain.model.StopAlert
import com.unishuttle.app.domain.model.StopEta

data class CampusMapUiState(
    val shuttles: List<ShuttleLocation> = emptyList(),
    val stops: List<ShuttleStop> = emptyList(),
    val routes: List<CampusRoute> = emptyList(),
    val selectedRouteId: String? = null, // null represents "All routes"
    val routeGeometries: List<RouteGeometry> = emptyList(),
    val seatAvailability: List<SeatAvailability> = emptyList(),
    val stopEtas: List<StopEta> = emptyList(),
    val activeAlerts: List<StopAlert> = emptyList(),
    val selectedStopId: String? = null,
    val isUsingFallbackStops: Boolean = false,
    val isUsingFallbackRoutes: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    val selectedRoute: CampusRoute? = routes.firstOrNull { it.id == selectedRouteId }

    /**
     * Authoritative stop ordering:
     * - When a route is selected: ordered strictly according to selectedRoute.stopIds.
     * - When "All routes" is selected: includes all campus stops (including unassigned legacy stops).
     */
    val filteredStops: List<ShuttleStop> = if (selectedRoute != null) {
        val stopMap = stops.associateBy { it.id }
        selectedRoute.stopIds.mapNotNull { stopMap[it] }
    } else {
        stops
    }

    /**
     * Shuttles filtered by selected route:
     * - When a route is selected: only vehicles assigned to that route.
     * - When "All routes" is selected: all vehicles (including unassigned legacy shuttles).
     */
    val filteredShuttles: List<ShuttleLocation> = if (selectedRouteId != null) {
        shuttles.filter { it.routeId == selectedRouteId }
    } else {
        shuttles
    }

    /**
     * Geometries filtered by selected route:
     * - When a route is selected: only geometry for that route.
     * - When "All routes" is selected: all active route geometries.
     */
    val filteredGeometries: List<RouteGeometry> = if (selectedRouteId != null) {
        routeGeometries.filter { geometry ->
            geometry.routeId == selectedRouteId && routes.any { it.id == geometry.routeId }
        }
    } else {
        val activeRouteIds = routes.mapTo(HashSet()) { it.id }
        routeGeometries.filter { it.routeId in activeRouteIds }
    }

    val filteredStopEtas: List<StopEta> = if (selectedRouteId != null) {
        val visibleShuttleIds = filteredShuttles.mapTo(HashSet()) { it.id }
        stopEtas.filter { it.shuttleId in visibleShuttleIds }
    } else {
        stopEtas
    }

    val selectedStop: ShuttleStop?
        get() = filteredStops.firstOrNull { it.id == selectedStopId }
            ?: stops.firstOrNull { it.id == selectedStopId }

    /**
     * Backward-compatible route points accessor for legacy consumers.
     */
    val routePoints: List<CampusLatLng>
        get() = filteredGeometries.firstOrNull()?.points ?: emptyList()
}
