package com.unishuttle.app.data.mapper

import com.unishuttle.app.data.remote.dto.FirebaseShuttleLocationDto
import com.unishuttle.app.data.remote.dto.FirestoreCampusRouteDto
import com.unishuttle.app.data.remote.dto.FirestoreSeatAvailabilityDto
import com.unishuttle.app.data.remote.dto.FirestoreShuttleStopDto
import com.unishuttle.app.domain.model.CampusRoute
import com.unishuttle.app.domain.model.SeatAvailability
import com.unishuttle.app.domain.model.ShuttleLocation
import com.unishuttle.app.domain.model.ShuttleStop

fun FirebaseShuttleLocationDto.toDomain(id: String): ShuttleLocation? {
    val latitude = latitude ?: return null
    val longitude = longitude ?: return null

    val validHeading = heading
        ?.toFloat()
        ?.takeIf { it.isFinite() && it in 0f..360f }

    return ShuttleLocation(
        id = id,
        latitude = latitude,
        longitude = longitude,
        heading = validHeading ?: 0f,
        updatedAtMillis = updatedAtMillis ?: 0L,
        routeId = routeId?.trim()?.ifEmpty { null },
        hasHeading = validHeading != null,
    )
}

fun FirestoreShuttleStopDto.toDomain(id: String): ShuttleStop? {
    val latitude = latitude ?: return null
    val longitude = longitude ?: return null

    return ShuttleStop(
        id = id,
        name = name.orEmpty().ifBlank { "Stop" },
        latitude = latitude,
        longitude = longitude,
        order = order?.toInt() ?: Int.MAX_VALUE,
    )
}

fun FirestoreCampusRouteDto.toDomain(id: String): CampusRoute {
    return CampusRoute(
        id = id,
        name = name.orEmpty().ifBlank { id },
        description = description.orEmpty(),
        colorHex = colorHex.orEmpty(),
        stopIds = stopIds?.mapNotNull { it.trim().ifEmpty { null } } ?: emptyList(),
        version = version?.toInt() ?: 1,
        isActive = isActive ?: true,
    )
}

fun FirestoreSeatAvailabilityDto.toDomain(shuttleId: String): SeatAvailability? {
    val totalSeats = totalSeats?.toInt() ?: return null
    val availableSeats = availableSeats?.toInt() ?: return null

    return SeatAvailability(
        shuttleId = shuttleId,
        availableSeats = availableSeats.coerceIn(0, totalSeats.coerceAtLeast(0)),
        totalSeats = totalSeats.coerceAtLeast(0),
        updatedAtMillis = updatedAtMillis ?: 0L,
    )
}
