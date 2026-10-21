package com.unishuttle.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class DirectionsResponseDto(
    val status: String? = null,
    @SerializedName("error_message")
    val errorMessage: String? = null,
    val routes: List<DirectionsRouteDto> = emptyList(),
)

data class DirectionsRouteDto(
    @SerializedName("overview_polyline")
    val overviewPolyline: DirectionsPolylineDto? = null,
    val legs: List<DirectionsLegDto> = emptyList(),
)

data class DirectionsPolylineDto(
    val points: String? = null,
)

data class DirectionsLegDto(
    val duration: DirectionsTextValueDto? = null,
    @SerializedName("duration_in_traffic")
    val durationInTraffic: DirectionsTextValueDto? = null,
)

data class DirectionsTextValueDto(
    val text: String? = null,
    val value: Long? = null,
)
