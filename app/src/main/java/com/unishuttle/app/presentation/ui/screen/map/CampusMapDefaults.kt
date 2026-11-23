package com.unishuttle.app.presentation.ui.screen.map

import com.unishuttle.app.domain.model.CampusLatLng
import com.unishuttle.app.domain.model.CampusRoute
import com.unishuttle.app.domain.model.ShuttleStop

object CampusMapDefaults {
    // VIT Vellore Campus
    val CampusCenter = CampusLatLng(latitude = 12.9692, longitude = 79.1559)
    val CampusBoundsSouthWest = CampusLatLng(latitude = 12.9592, longitude = 79.1459)
    val CampusBoundsNorthEast = CampusLatLng(latitude = 12.9792, longitude = 79.1659)
    const val DefaultZoom = 16.0

    const val ROUTE_LOOP_A_ID = "route_loop_a"
    const val ROUTE_LOOP_B_ID = "route_loop_b"

    val RouteLoopA = CampusRoute(
        id = ROUTE_LOOP_A_ID,
        name = "Loop A",
        description = "Clockwise Campus Loop",
        colorHex = "#D32F2F", // UniColors.Brick
        stopIds = listOf("stop_main_gate", "stop_tech_tower", "stop_sjt"),
        version = 1,
        isActive = true,
    )

    val RouteLoopB = CampusRoute(
        id = ROUTE_LOOP_B_ID,
        name = "Loop B",
        description = "Counter-Clockwise Campus Loop",
        colorHex = "#1976D2", // Distinct accessible blue
        stopIds = listOf("stop_sjt", "stop_tech_tower", "stop_main_gate"),
        version = 1,
        isActive = true,
    )

    val DefaultRoutes = listOf(RouteLoopA, RouteLoopB)

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
