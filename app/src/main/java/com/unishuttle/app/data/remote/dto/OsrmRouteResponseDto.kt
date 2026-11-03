package com.unishuttle.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/** OSRM route service response (geometries=geojson, steps omitted). */
data class OsrmRouteResponseDto(
    val code: String? = null,
    val message: String? = null,
    val routes: List<OsrmRouteDto> = emptyList(),
)

data class OsrmRouteDto(
    val geometry: OsrmGeometryDto? = null,
    val legs: List<OsrmLegDto> = emptyList(),
)

data class OsrmGeometryDto(
    /** [longitude, latitude] pairs in GeoJSON order. */
    val coordinates: List<List<Double>> = emptyList(),
)

data class OsrmLegDto(
    /** Driving duration for this leg, in seconds. */
    val duration: Double? = null,
    val distance: Double? = null,
    @SerializedName("duration_typical") val durationTypical: Double? = null,
)
