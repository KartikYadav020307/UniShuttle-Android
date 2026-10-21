package com.unishuttle.app.data.remote.api

import com.unishuttle.app.data.remote.dto.DirectionsResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface DirectionsApiService {
    @GET("maps/api/directions/json")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("waypoints") waypoints: String?,
        @Query("mode") mode: String = "driving",
        @Query("departure_time") departureTime: String? = null,
        @Query("key") apiKey: String,
    ): DirectionsResponseDto
}
