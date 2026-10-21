package com.unishuttle.app.presentation.ui.screen.map

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds

object CampusMapDefaults {
    // VIT Vellore Campus
    val CampusCenter = LatLng(12.9692, 79.1559)
    val CampusBounds: LatLngBounds = LatLngBounds(
        LatLng(12.9592, 79.1459), // Southwest corner
        LatLng(12.9792, 79.1659), // Northeast corner
    )
    const val DefaultZoom = 15f
}
