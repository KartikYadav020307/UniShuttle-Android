package com.unishuttle.app.domain.model

data class RouteGeometry(
    val routeId: String,
    val points: List<CampusLatLng>,
    val version: Int = 1,
    val stopHash: String,
    val isFallback: Boolean,
    val updatedAtMillis: Long,
)
