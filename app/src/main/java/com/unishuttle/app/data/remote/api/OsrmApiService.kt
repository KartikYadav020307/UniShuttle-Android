package com.unishuttle.app.data.remote.api

import com.unishuttle.app.data.remote.dto.OsrmRouteResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * OSRM demo route service. Coordinate strings are `{longitude},{latitude}`
 * pairs joined by `;`, per the OSRM API.
 */
interface OsrmApiService {
    @GET("route/v1/driving/{coordinates}")
    suspend fun getRoute(
        @Path("coordinates", encoded = true) coordinates: String,
        @Query("overview") overview: String = "full",
        @Query("geometries") geometries: String = "geojson",
    ): OsrmRouteResponseDto
}
