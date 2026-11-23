package com.unishuttle.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.unishuttle.app.config.MapProviderConfig
import com.unishuttle.app.data.local.dao.RouteGeometryDao
import com.unishuttle.app.data.local.entity.CachedRouteGeometryEntity
import com.unishuttle.app.data.remote.api.OsrmApiService
import com.unishuttle.app.domain.model.CampusLatLng
import com.unishuttle.app.domain.model.CampusRoute
import com.unishuttle.app.domain.model.RouteGeometry
import com.unishuttle.app.domain.model.ShuttleLocation
import com.unishuttle.app.domain.model.ShuttleStop
import com.unishuttle.app.domain.model.StopEta
import com.unishuttle.app.domain.repository.RouteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import timber.log.Timber

/**
 * Road routing and ETA source backed by Room persistent geometry cache
 * and the public OSRM server.
 *
 * - CampusRoute.stopIds is the authoritative source for route membership and stop ordering.
 * - Persistent Room cache with SHA-256 stop sequence fingerprints and versioning.
 * - Stale-response protection and request coalescing to prevent duplicate calls.
 * - Fallback straight lines generated offline and automatically upgraded online.
 * - Directional, wraparound circular ETAs respecting shuttle heading and route position.
 */
@Singleton
class OsrmRoutingRepository @Inject constructor(
    private val osrmApiService: OsrmApiService,
    private val routeGeometryDao: RouteGeometryDao,
) : RouteRepository {

    private val gson = Gson()
    private val pointsListType = object : TypeToken<List<CampusLatLng>>() {}.type

    private val requestMutex = Mutex()
    private var lastRequestAtMillis = 0L

    // In-memory request coalescing map for active geometry fetches
    private val inFlightGeometryRequests = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<RouteGeometry>>()
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val latestRouteInputs = ConcurrentHashMap<String, RouteRequestStamp>()
    private val routeCacheMutexes = ConcurrentHashMap<String, Mutex>()

    // Legacy in-memory route cache for simple point queries
    private val memoryPointsCache = ConcurrentHashMap<String, List<CampusLatLng>>()

    override suspend fun getRouteBetweenStops(stops: List<CampusLatLng>): Result<List<CampusLatLng>> = captureResult {
        require(stops.size >= 2) { "At least two stops are required to request a route." }
        val key = cacheKey(stops)
        memoryPointsCache[key]?.let { return@captureResult it }

        val route = requestRoute(stops.map { it.toLngLat() })
        val points = route?.geometry?.coordinates
            ?.mapNotNull { pair ->
                if (pair.size >= 2) CampusLatLng(pair[1], pair[0]) else null
            }
            .orEmpty()
        check(points.size >= 2) { "OSRM returned no usable route geometry." }

        memoryPointsCache[key] = points
        points
    }

    override suspend fun getEtasFromShuttleToStops(
        shuttle: ShuttleLocation,
        stops: List<ShuttleStop>,
    ): Result<List<StopEta>> = captureResult {
        if (stops.isEmpty()) return@captureResult emptyList()

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

    override suspend fun getRouteGeometry(
        route: CampusRoute,
        stops: List<ShuttleStop>,
    ): Result<RouteGeometry> = captureResult {
        val orderedStops = getOrderedStops(route, stops)
        require(orderedStops.size >= 2) { "Route '${route.name}' requires at least 2 stops." }

        val stopHash = computeStopHash(route, orderedStops)
        val geometryHash = computeGeometryHash(route, orderedStops)
        recordLatestRouteInput(route, stopHash)

        // 1. Check persistent Room DB cache
        val cached = routeGeometryDao.getRouteGeometry(route.id)
        if (cached != null &&
            cached.stopHash == stopHash &&
            cached.geometryHash == geometryHash &&
            cached.routeVersion == route.version
        ) {
            val cachedPoints = deserializePoints(cached.pointsJson)
            if (cachedPoints.size >= 2) {
                if (cached.isFallback) {
                    // Cached geometry is straight-line fallback.
                    // Emit immediately for instant UI interactivity and trigger background upgrade.
                    scheduleBackgroundUpgrade(route, orderedStops, stopHash, geometryHash)
                }
                return@captureResult RouteGeometry(
                    routeId = route.id,
                    points = cachedPoints,
                    version = cached.routeVersion,
                    stopHash = cached.stopHash,
                    isFallback = cached.isFallback,
                    updatedAtMillis = cached.updatedAtMillis,
                )
            }
        }

        // 2. Fetch with request coalescing to prevent duplicate concurrent network queries
        coalesceRouteGeometryRequest(route, orderedStops, stopHash, geometryHash)
    }

    override fun observeRouteGeometries(): Flow<List<RouteGeometry>> =
        routeGeometryDao.observeAllRouteGeometries().map { entities ->
            entities.mapNotNull { entity ->
                val points = deserializePoints(entity.pointsJson)
                if (points.size >= 2) {
                    RouteGeometry(
                        routeId = entity.routeId,
                        points = points,
                        version = entity.routeVersion,
                        stopHash = entity.stopHash,
                        isFallback = entity.isFallback,
                        updatedAtMillis = entity.updatedAtMillis,
                    )
                } else null
            }
        }

    override suspend fun getEtasForRoute(
        route: CampusRoute,
        shuttles: List<ShuttleLocation>,
        stops: List<ShuttleStop>,
    ): Result<List<StopEta>> = captureResult {
        val orderedStops = getOrderedStops(route, stops)
        if (orderedStops.size < 2) return@captureResult emptyList()

        // Filter shuttles assigned to this route
        val assignedShuttles = shuttles.filter { it.routeId == route.id }
        if (assignedShuttles.isEmpty()) return@captureResult emptyList()

        val allEtas = mutableListOf<StopEta>()

        for (shuttle in assignedShuttles) {
            val nextStopIndex = identifyNextStopIndex(shuttle, orderedStops)
            val stopCount = orderedStops.size

            // Build circular wraparound sequence of waypoints starting from nextStopIndex
            val sequence = (0 until stopCount).map { i ->
                orderedStops[(nextStopIndex + i) % stopCount]
            }

            val waypoints = buildList {
                add("${shuttle.longitude},${shuttle.latitude}")
                sequence.forEach { add("${it.longitude},${it.latitude}") }
            }

            val osrmRoute = try {
                requestRoute(waypoints)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Timber.tag(TAG).w("OSRM ETA fetch failed for shuttle ${shuttle.id} on route ${route.id}: ${e.message}")
                null
            }

            if (osrmRoute != null) {
                val legs = osrmRoute.legs.orEmpty()
                var cumulativeSeconds = 0L

                sequence.forEachIndexed { index, stop ->
                    val legDuration = legs.getOrNull(index)?.duration
                    if (legDuration != null) {
                        cumulativeSeconds += legDuration.toLong()
                        allEtas.add(
                            StopEta(
                                shuttleId = shuttle.id,
                                stopId = stop.id,
                                etaText = formatEta(cumulativeSeconds),
                                durationSeconds = cumulativeSeconds,
                            ),
                        )
                    }
                }
            }
        }

        allEtas
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private fun getOrderedStops(route: CampusRoute, allStops: List<ShuttleStop>): List<ShuttleStop> {
        val stopMap = allStops.associateBy { it.id }
        return route.stopIds.mapNotNull { stopMap[it] }
    }

    private suspend fun coalesceRouteGeometryRequest(
        route: CampusRoute,
        orderedStops: List<ShuttleStop>,
        stopHash: String,
        geometryHash: String,
    ): RouteGeometry {
        val coalescingKey = "${route.id}:${route.version}:$stopHash"

        val deferred = inFlightGeometryRequests.computeIfAbsent(coalescingKey) {
            backgroundScope.async(start = CoroutineStart.LAZY) {
                fetchAndCacheRouteGeometry(route, orderedStops, stopHash, geometryHash)
            }.also { created ->
                created.invokeOnCompletion {
                    inFlightGeometryRequests.remove(coalescingKey, created)
                }
            }
        }
        deferred.start()
        return deferred.await()
    }

    private suspend fun fetchAndCacheRouteGeometry(
        route: CampusRoute,
        orderedStops: List<ShuttleStop>,
        stopHash: String,
        geometryHash: String,
    ): RouteGeometry {
        val waypoints = buildList {
            orderedStops.forEach { add("${it.longitude},${it.latitude}") }
            // Complete circular loop back to origin
            add("${orderedStops.first().longitude},${orderedStops.first().latitude}")
        }

        val osrmResult = try {
            requestRoute(waypoints)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Timber.tag(TAG).w("Failed to fetch OSRM geometry for route ${route.id}: ${e.message}; checking cache/fallback")
            null
        }

        val realPoints = osrmResult?.geometry?.coordinates
            ?.mapNotNull { pair ->
                if (pair.size >= 2) CampusLatLng(pair[1], pair[0]) else null
            }
            ?.takeIf { it.size >= 2 }

        val isFallback = realPoints == null
        val finalPoints = realPoints ?: generateStraightLineFallback(orderedStops)

        return saveGeometryIfCurrent(
            route = route,
            stopHash = stopHash,
            geometryHash = geometryHash,
            finalPoints = finalPoints,
            isFallback = isFallback,
        )
    }

    private suspend fun saveGeometryIfCurrent(
        route: CampusRoute,
        stopHash: String,
        geometryHash: String,
        finalPoints: List<CampusLatLng>,
        isFallback: Boolean,
    ): RouteGeometry {
        val requestStamp = RouteRequestStamp(route.version, stopHash)
        val routeMutex = routeCacheMutexes.computeIfAbsent(route.id) { Mutex() }
        return routeMutex.withLock {
            val existing = routeGeometryDao.getRouteGeometry(route.id)
            val existingGeometry = existing?.let(::toRouteGeometry)
            val latestRequest = latestRouteInputs[route.id]

            if (existing != null && existing.routeVersion > route.version) {
                return@withLock existingGeometry ?: RouteGeometry(
                    routeId = route.id,
                    points = finalPoints,
                    version = existing.routeVersion,
                    stopHash = existing.stopHash,
                    isFallback = existing.isFallback,
                    updatedAtMillis = existing.updatedAtMillis,
                )
            }

            // A same-version request for changed stops may finish after a newer request.
            if (latestRequest != requestStamp) {
                val currentGeometry = existing
                    ?.takeIf {
                        it.routeVersion == latestRequest?.version &&
                            it.stopHash == latestRequest.stopHash
                    }
                    ?.let(::toRouteGeometry)
                return@withLock currentGeometry ?: RouteGeometry(
                    routeId = route.id,
                    points = finalPoints,
                    version = route.version,
                    stopHash = stopHash,
                    isFallback = isFallback,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            }

            if (isFallback && existingGeometry != null) {
                if (!existing.isFallback && existing.geometryHash == geometryHash) {
                    Timber.tag(TAG).i("Preserving cached road geometry for route '${route.name}' despite transient OSRM failure.")
                    return@withLock existingGeometry
                }
                if (existing.isFallback &&
                    existing.routeVersion == route.version &&
                    existing.stopHash == stopHash &&
                    existing.geometryHash == geometryHash
                ) {
                    return@withLock existingGeometry
                }
            }

            val entity = CachedRouteGeometryEntity(
                routeId = route.id,
                routeVersion = route.version,
                stopHash = stopHash,
                geometryHash = geometryHash,
                pointsJson = gson.toJson(finalPoints),
                pointCount = finalPoints.size,
                isFallback = isFallback,
                updatedAtMillis = System.currentTimeMillis(),
            )
            routeGeometryDao.insertRouteGeometry(entity)
            RouteGeometry(
                routeId = route.id,
                points = finalPoints,
                version = route.version,
                stopHash = stopHash,
                isFallback = isFallback,
                updatedAtMillis = entity.updatedAtMillis,
            )
        }
    }

    private fun scheduleBackgroundUpgrade(
        route: CampusRoute,
        orderedStops: List<ShuttleStop>,
        expectedStopHash: String,
        geometryHash: String,
    ) {
        backgroundScope.launch {
            try {
                coalesceRouteGeometryRequest(route, orderedStops, expectedStopHash, geometryHash)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.tag(TAG).d("Background geometry upgrade deferred: ${error.message}")
            }
        }
    }

    private fun generateStraightLineFallback(orderedStops: List<ShuttleStop>): List<CampusLatLng> {
        return buildList {
            orderedStops.forEach { add(CampusLatLng(it.latitude, it.longitude)) }
            // Connect circular loop
            add(CampusLatLng(orderedStops.first().latitude, orderedStops.first().longitude))
        }
    }

    internal fun identifyNextStopIndex(shuttle: ShuttleLocation, orderedStops: List<ShuttleStop>): Int {
        val n = orderedStops.size
        if (n <= 1) return 0

        var bestIndex = 0
        var bestScore = Double.MAX_VALUE

        for (i in 0 until n) {
            val stopCandidate = orderedStops[i]

            val distToCandidate = distanceMeters(
                shuttle.latitude,
                shuttle.longitude,
                stopCandidate.latitude,
                stopCandidate.longitude,
            )

            val score = if (shuttle.hasHeading) {
                val bearingToCandidate = computeBearing(
                    shuttle.latitude,
                    shuttle.longitude,
                    stopCandidate.latitude,
                    stopCandidate.longitude,
                )
                val headingDiff = angleDifference(shuttle.heading.toDouble(), bearingToCandidate)
                if (headingDiff > 90.0) {
                    // Vehicle is traveling away from candidate stop (it is behind vehicle)
                    distToCandidate + 100_000.0
                } else {
                    // Vehicle is traveling toward candidate stop
                    distToCandidate * (1.0 + (headingDiff / 90.0))
                }
            } else {
                distToCandidate
            }

            if (score < bestScore) {
                bestScore = score
                bestIndex = i
            }
        }

        return bestIndex
    }

    private fun deserializePoints(json: String): List<CampusLatLng> = try {
        gson.fromJson(json, pointsListType) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    internal fun computeStopHash(route: CampusRoute, orderedStops: List<ShuttleStop>): String =
        hashRouteFingerprint(route, orderedStops, includeVersion = true)

    internal fun computeGeometryHash(route: CampusRoute, orderedStops: List<ShuttleStop>): String =
        hashRouteFingerprint(route, orderedStops, includeVersion = false)

    private fun hashRouteFingerprint(
        route: CampusRoute,
        orderedStops: List<ShuttleStop>,
        includeVersion: Boolean,
    ): String {
        val raw = buildString {
            append(route.id.length).append(':').append(route.id).append('|')
            if (includeVersion) append(route.version).append('|')
            orderedStops.forEach { stop ->
                append(stop.id.length).append(':').append(stop.id).append('|')
                append(java.lang.Double.toHexString(stop.latitude))
                append(',')
                append(java.lang.Double.toHexString(stop.longitude))
                append(';')
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private suspend fun recordLatestRouteInput(route: CampusRoute, stopHash: String) {
        routeCacheMutexes.computeIfAbsent(route.id) { Mutex() }.withLock {
            val current = latestRouteInputs[route.id]
            if (current == null || route.version >= current.version) {
                latestRouteInputs[route.id] = RouteRequestStamp(route.version, stopHash)
            }
        }
    }

    private fun toRouteGeometry(entity: CachedRouteGeometryEntity): RouteGeometry? {
        val points = deserializePoints(entity.pointsJson)
        if (points.size < 2) return null
        return RouteGeometry(
            routeId = entity.routeId,
            points = points,
            version = entity.routeVersion,
            stopHash = entity.stopHash,
            isFallback = entity.isFallback,
            updatedAtMillis = entity.updatedAtMillis,
        )
    }

    private suspend fun <T> captureResult(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }

    private suspend fun requestRoute(waypoints: List<String>): com.unishuttle.app.data.remote.dto.OsrmRouteDto? {
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
            String.format(Locale.US, "%.4f,%.4f", it.latitude, it.longitude)
        }

    private fun CampusLatLng.toLngLat(): String = "$longitude,$latitude"

    private fun formatEta(durationSeconds: Long): String {
        val minutes = ((durationSeconds + 59) / 60).coerceAtLeast(1)
        return "$minutes min"
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun computeBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }

    private fun angleDifference(angle1: Double, angle2: Double): Double {
        val diff = abs(angle1 - angle2) % 360.0
        return if (diff > 180.0) 360.0 - diff else diff
    }

    private data class RouteRequestStamp(
        val version: Int,
        val stopHash: String,
    )

    companion object {
        private const val TAG = "OsrmRoutingRepo"
    }
}
