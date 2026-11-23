package com.unishuttle.app.domain.repository

import com.unishuttle.app.domain.model.CampusLatLng
import com.unishuttle.app.domain.model.CampusRoute
import com.unishuttle.app.domain.model.RouteGeometry
import com.unishuttle.app.domain.model.ShuttleLocation
import com.unishuttle.app.domain.model.ShuttleStop
import com.unishuttle.app.domain.model.StopEta
import kotlinx.coroutines.flow.Flow

interface RouteRepository {
    suspend fun getRouteBetweenStops(stops: List<CampusLatLng>): Result<List<CampusLatLng>>

    suspend fun getEtasFromShuttleToStops(
        shuttle: ShuttleLocation,
        stops: List<ShuttleStop>,
    ): Result<List<StopEta>>

    suspend fun getRouteGeometry(
        route: CampusRoute,
        stops: List<ShuttleStop>,
    ): Result<RouteGeometry>

    fun observeRouteGeometries(): Flow<List<RouteGeometry>>

    suspend fun getEtasForRoute(
        route: CampusRoute,
        shuttles: List<ShuttleLocation>,
        stops: List<ShuttleStop>,
    ): Result<List<StopEta>>
}
