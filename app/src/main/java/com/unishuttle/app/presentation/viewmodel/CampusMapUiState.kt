package com.unishuttle.app.presentation.viewmodel

import com.unishuttle.app.domain.model.CampusLatLng
import com.unishuttle.app.domain.model.SeatAvailability
import com.unishuttle.app.domain.model.ShuttleLocation
import com.unishuttle.app.domain.model.ShuttleStop
import com.unishuttle.app.domain.model.StopAlert
import com.unishuttle.app.domain.model.StopEta

data class CampusMapUiState(
    val shuttles: List<ShuttleLocation> = emptyList(),
    val stops: List<ShuttleStop> = emptyList(),
    val seatAvailability: List<SeatAvailability> = emptyList(),
    val stopEtas: List<StopEta> = emptyList(),
    val routePoints: List<CampusLatLng> = emptyList(),
    val activeAlerts: List<StopAlert> = emptyList(),
    val selectedStopId: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    val selectedStop: ShuttleStop? = stops.firstOrNull { it.id == selectedStopId }
}
