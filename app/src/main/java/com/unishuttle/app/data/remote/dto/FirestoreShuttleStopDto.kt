package com.unishuttle.app.data.remote.dto

data class FirestoreShuttleStopDto(
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val order: Long? = null,
    // Retained for reading legacy documents only; route membership comes from CampusRoute.stopIds.
    val routeIds: List<String>? = null,
)
