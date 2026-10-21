package com.unishuttle.app.data.repository

import com.unishuttle.app.BuildConfig
import com.unishuttle.app.data.mapper.PolylineDecoder
import com.unishuttle.app.data.remote.api.DirectionsApiService
import com.unishuttle.app.domain.model.CampusLatLng
import com.unishuttle.app.domain.model.ShuttleLocation
import com.unishuttle.app.domain.model.ShuttleStop
import com.unishuttle.app.domain.model.StopEta
import com.unishuttle.app.domain.repository.RouteRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDirectionsRouteRepository @Inject constructor(
    private val directionsApiService: DirectionsApiService,
) : RouteRepository {
    override suspend fun getRouteBetweenStops(stops: List<CampusLatLng>): Result<List<CampusLatLng>> = runCatching {
        require(stops.size >= 2) { "At least two stops are required to request a route." }

        val response = directionsApiService.getDirections(
            origin = stops.first().toDirectionsParam(),
            destination = stops.last().toDirectionsParam(),
            waypoints = stops.drop(1).dropLast(1).takeIf { it.isNotEmpty() }
                ?.joinToString(separator = "|") { it.toDirectionsParam() },
            apiKey = BuildConfig.DIRECTIONS_API_KEY,
        )

        if (response.status != "OK") {
            error(response.errorMessage ?: "Directions API returned ${response.status}.")
        }

        val encodedPolyline = response.routes.firstOrNull()?.overviewPolyline?.points
        if (encodedPolyline.isNullOrBlank()) {
            error("Directions API did not return an overview polyline.")
        }

        PolylineDecoder.decode(encodedPolyline)
    }

    override suspend fun getEtasFromShuttleToStops(
        shuttle: ShuttleLocation,
        stops: List<ShuttleStop>,
    ): Result<List<StopEta>> = runCatching {
        if (stops.isEmpty()) return@runCatching emptyList()

        val response = directionsApiService.getDirections(
            origin = "${shuttle.latitude},${shuttle.longitude}",
            destination = stops.last().toDirectionsParam(),
            waypoints = stops.dropLast(1).takeIf { it.isNotEmpty() }
                ?.joinToString(separator = "|") { it.toDirectionsParam() },
            departureTime = "now",
            apiKey = BuildConfig.DIRECTIONS_API_KEY,
        )

        if (response.status != "OK") {
            error(response.errorMessage ?: "Directions API returned ${response.status}.")
        }

        val legs = response.routes.firstOrNull()?.legs.orEmpty()
        var cumulativeSeconds = 0L

        stops.mapIndexedNotNull { index, stop ->
            val leg = legs.getOrNull(index)
            val seconds = leg?.durationInTraffic?.value ?: leg?.duration?.value ?: return@mapIndexedNotNull null
            cumulativeSeconds += seconds

            StopEta(
                shuttleId = shuttle.id,
                stopId = stop.id,
                etaText = formatEta(cumulativeSeconds),
                durationSeconds = cumulativeSeconds,
            )
        }
    }

    private fun CampusLatLng.toDirectionsParam(): String = "$latitude,$longitude"

    private fun ShuttleStop.toDirectionsParam(): String = "$latitude,$longitude"

    private fun formatEta(durationSeconds: Long): String {
        val minutes = ((durationSeconds + 59) / 60).coerceAtLeast(1)
        return "$minutes min"
    }
}
