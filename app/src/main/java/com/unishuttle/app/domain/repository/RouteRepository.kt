package com.unishuttle.app.domain.repository

import com.unishuttle.app.domain.model.CampusLatLng
import com.unishuttle.app.domain.model.ShuttleLocation
import com.unishuttle.app.domain.model.ShuttleStop
import com.unishuttle.app.domain.model.StopEta

interface RouteRepository {
    suspend fun getRouteBetweenStops(stops: List<CampusLatLng>): Result<List<CampusLatLng>>

    suspend fun getEtasFromShuttleToStops(
        shuttle: ShuttleLocation,
        stops: List<ShuttleStop>,
    ): Result<List<StopEta>>
}
