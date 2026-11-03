package com.unishuttle.app.presentation.ui.screen.map

import com.unishuttle.app.domain.model.CampusLatLng
import com.unishuttle.app.domain.model.ShuttleStop

object CampusMapDefaults {
    // VIT Vellore Campus
    val CampusCenter = CampusLatLng(latitude = 12.9692, longitude = 79.1559)
    val CampusBoundsSouthWest = CampusLatLng(latitude = 12.9592, longitude = 79.1459)
    val CampusBoundsNorthEast = CampusLatLng(latitude = 12.9792, longitude = 79.1659)
    const val DefaultZoom = 16.0

    /**
     * Immutable static campus stops used as local fallback when Firestore is
     * unreachable or unprovisioned. Read-only and never written back to the cloud.
     */
    val DefaultStops = listOf(
        ShuttleStop(
            id = "stop_main_gate",
            name = "Main Gate",
            latitude = 12.9692,
            longitude = 79.1559,
            order = 1,
        ),
        ShuttleStop(
            id = "stop_tech_tower",
            name = "Technology Tower",
            latitude = 12.9712,
            longitude = 79.1594,
            order = 2,
        ),
        ShuttleStop(
            id = "stop_sjt",
            name = "Silver Jubilee Tower",
            latitude = 12.9735,
            longitude = 79.1635,
            order = 3,
        ),
    )
}
