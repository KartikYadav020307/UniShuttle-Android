package com.unishuttle.app.data.repository

import com.unishuttle.app.config.MapProviderConfig
import com.unishuttle.app.data.remote.api.OsrmApiService
import com.unishuttle.app.domain.model.CampusLatLng
import com.unishuttle.app.domain.model.ShuttleLocation
import com.unishuttle.app.domain.model.ShuttleStop
import com.unishuttle.app.domain.model.StopEta
import com.unishuttle.app.domain.repository.RouteRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Road routing and ETA source backed by the public OSRM demo server.
 *
 * - Results are cached by rounded input coordinates; identical routes are
 *   never fetched twice.
 * - Requests are serialized at least [MapProviderConfig.OSRM_MIN_REQUEST_INTERVAL_MILLIS]
 *   apart, respecting the demo server's one-request-per-second policy.
 * - Durations are driving times without traffic, dwell, or campus-specific
 *   constraints; the UI must treat them as approximate (see docs).
 */
@Singleton
class OsrmRoutingRepository @Inject constructor(
    private val osrmApiService: OsrmApiService,
) : RouteRepository {

    private val requestMutex = Mutex()
    private var lastRequestAtMillis = 0L
    private val routeCache = ConcurrentHashMap<String, List<CampusLatLng>>()

    override suspend fun getRouteBetweenStops(stops: List<CampusLatLng>): Result<List<CampusLatLng>> = runCatching {
        require(stops.size >= 2) { "At least two stops are required to request a route." }
        val key = cacheKey(stops)
        routeCache[key]?.let { return@runCatching it }

        val route = requestRoute(stops.map { it.toLngLat() })
        val points = route?.geometry?.coordinates
            ?.mapNotNull { pair ->
                if (pair.size >= 2) CampusLatLng(pair[1], pair[0]) else null
            }
            .orEmpty()
        check(points.size >= 2) { "OSRM returned no usable route geometry." }

        routeCache[key] = points
        points
    }

    override suspend fun getEtasFromShuttleToStops(
        shuttle: ShuttleLocation,
        stops: List<ShuttleStop>,
    ): Result<List<StopEta>> = runCatching {
        if (stops.isEmpty()) return@runCatching emptyList()

        val waypoints = buildList {
            add("${shuttle.longitude},${shuttle.latitude}")
            stops.forEach { add("${it.longitude},${it.latitude}") }
        }

        val route = requestRoute(waypoints)
        val legs = route?.legs.orEmpty()
        var cumulativeSeconds = 0L

        stops.mapIndexedNotNull { index, stop ->
            val seconds = legs.getOrNull(index)?.duration ?: return@mapIndexedNotNull null
            cumulativeSeconds += seconds.toLong()
            StopEta(
                shuttleId = shuttle.id,
                stopId = stop.id,
                etaText = formatEta(cumulativeSeconds),
                durationSeconds = cumulativeSeconds,
            )
        }
    }

    /** Executes one rate-limited request; returns null when OSRM reports "NoRoute". */
    private suspend fun requestRoute(waypoints: List<String>): com.unishuttle.app.data.remote.dto.OsrmRouteDto? {
        // Serialize every OSRM call and keep the demo server's 1 req/s policy.
        requestMutex.withLock {
            val wait = MapProviderConfig.OSRM_MIN_REQUEST_INTERVAL_MILLIS -
                (System.currentTimeMillis() - lastRequestAtMillis)
            if (wait > 0) kotlinx.coroutines.delay(wait)
            lastRequestAtMillis = System.currentTimeMillis()
        }

        val response = osrmApiService.getRoute(coordinates = waypoints.joinToString(";"))
        if (response.code != "Ok") {
            if (response.code == "NoRoute") return null
            error("OSRM error: ${response.code} — ${response.message.orEmpty()}")
        }
        return response.routes.firstOrNull() ?: error("OSRM returned no routes.")
    }

    internal fun cacheKey(points: List<CampusLatLng>): String =
        points.joinToString(";") {
            String.format(java.util.Locale.US, "%.4f,%.4f", it.latitude, it.longitude)
        }

    private fun CampusLatLng.toLngLat(): String = "$longitude,$latitude"

    private fun formatEta(durationSeconds: Long): String {
        val minutes = ((durationSeconds + 59) / 60).coerceAtLeast(1)
        return "$minutes min"
    }
}
