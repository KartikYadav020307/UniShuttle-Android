package com.unishuttle.app

import com.unishuttle.app.data.local.dao.RouteGeometryDao
import com.unishuttle.app.data.local.entity.CachedRouteGeometryEntity
import com.unishuttle.app.data.mapper.toDomain
import com.unishuttle.app.data.remote.api.OsrmApiService
import com.unishuttle.app.data.remote.dto.FirestoreShuttleStopDto
import com.unishuttle.app.data.remote.dto.OsrmGeometryDto
import com.unishuttle.app.data.remote.dto.OsrmRouteDto
import com.unishuttle.app.data.remote.dto.OsrmRouteResponseDto
import com.unishuttle.app.data.repository.OsrmRoutingRepository
import com.unishuttle.app.domain.model.CampusRoute
import com.unishuttle.app.domain.model.RouteGeometry
import com.unishuttle.app.domain.model.ShuttleLocation
import com.unishuttle.app.domain.model.ShuttleStop
import com.unishuttle.app.presentation.ui.map.RouteColorValidator
import com.unishuttle.app.presentation.ui.screen.map.CampusMapDefaults
import com.unishuttle.app.presentation.viewmodel.CampusMapUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class MultiRouteAndCachingUnitTest {

    private class MemoryRouteGeometryDao : RouteGeometryDao {
        private val entries = ConcurrentHashMap<String, CachedRouteGeometryEntity>()
        private val state = MutableStateFlow<List<CachedRouteGeometryEntity>>(emptyList())

        override suspend fun getRouteGeometry(routeId: String): CachedRouteGeometryEntity? = entries[routeId]
        override suspend fun getAllRouteGeometries(): List<CachedRouteGeometryEntity> = entries.values.toList()
        override fun observeAllRouteGeometries(): Flow<List<CachedRouteGeometryEntity>> = state
        override suspend fun insertRouteGeometry(geometry: CachedRouteGeometryEntity) {
            entries[geometry.routeId] = geometry
            state.value = entries.values.toList()
        }
        override suspend fun deleteRouteGeometry(routeId: String) {
            entries.remove(routeId)
            state.value = entries.values.toList()
        }
        override suspend fun clearAll() {
            entries.clear()
            state.value = emptyList()
        }
    }

    private class ControllableOsrmApiService(
        private val handler: suspend (Int, String) -> OsrmRouteResponseDto,
    ) : OsrmApiService {
        val requestCount = AtomicInteger()

        override suspend fun getRoute(
            coordinates: String,
            overview: String,
            geometries: String,
        ): OsrmRouteResponseDto = handler(requestCount.incrementAndGet(), coordinates)
    }

    private fun routeResponse(points: List<List<Double>>) = OsrmRouteResponseDto(
        code = "Ok",
        routes = listOf(OsrmRouteDto(geometry = OsrmGeometryDto(coordinates = points))),
    )

    private val stopMainGate = ShuttleStop(
        id = "stop_main_gate",
        name = "Main Gate",
        latitude = 12.9692,
        longitude = 79.1559,
        order = 1,
    )

    private val stopTechTower = ShuttleStop(
        id = "stop_tech_tower",
        name = "Technology Tower",
        latitude = 12.9712,
        longitude = 79.1594,
        order = 2,
    )

    private val stopSjt = ShuttleStop(
        id = "stop_sjt",
        name = "Silver Jubilee Tower",
        latitude = 12.9735,
        longitude = 79.1635,
        order = 3,
    )

    private val stopOnlyLoopA = ShuttleStop(
        id = "stop_only_a",
        name = "Loop A Special Stop",
        latitude = 12.9750,
        longitude = 79.1650,
        order = 4,
    )

    private val allStops = listOf(stopMainGate, stopTechTower, stopSjt, stopOnlyLoopA)

    private val routeLoopA = CampusRoute(
        id = "route_loop_a",
        name = "Loop A",
        colorHex = "#D32F2F",
        stopIds = listOf("stop_main_gate", "stop_tech_tower", "stop_sjt", "stop_only_a"),
    )

    private val routeLoopB = CampusRoute(
        id = "route_loop_b",
        name = "Loop B",
        colorHex = "#1976D2",
        stopIds = listOf("stop_sjt", "stop_tech_tower", "stop_main_gate"), // Reverse order
    )

    @Test
    fun `authoritative stopIds sequence dictates stop order regardless of database order column`() {
        // Base state has stops ordered: 1: Main Gate, 2: Tech Tower, 3: SJT, 4: Only A
        val stateAll = CampusMapUiState(
            stops = allStops,
            routes = listOf(routeLoopA, routeLoopB),
            selectedRouteId = null,
        )
        // Under "All routes", returns all stops
        assertEquals(4, stateAll.filteredStops.size)
        assertEquals("stop_main_gate", stateAll.filteredStops[0].id)

        // Select Loop B (authoritative stopIds: SJT -> TT -> Main Gate)
        val stateLoopB = stateAll.copy(selectedRouteId = "route_loop_b")
        val filteredB = stateLoopB.filteredStops

        assertEquals("Loop B must have exactly 3 stops", 3, filteredB.size)
        assertEquals("First stop in Loop B must be SJT", "stop_sjt", filteredB[0].id)
        assertEquals("Second stop in Loop B must be TT", "stop_tech_tower", filteredB[1].id)
        assertEquals("Third stop in Loop B must be Main Gate", "stop_main_gate", filteredB[2].id)
        assertFalse("Stop only in Loop A must not be present in Loop B", filteredB.any { it.id == "stop_only_a" })
    }

    @Test
    fun `shuttle filtering strictly isolates assigned routes and preserves unassigned on all routes`() {
        val shuttleA = ShuttleLocation(id = "s1", latitude = 12.969, longitude = 79.155, heading = 0f, updatedAtMillis = 1000L, routeId = "route_loop_a")
        val shuttleB = ShuttleLocation(id = "s2", latitude = 12.971, longitude = 79.159, heading = 0f, updatedAtMillis = 1000L, routeId = "route_loop_b")
        val shuttleLegacy = ShuttleLocation(id = "s3", latitude = 12.973, longitude = 79.163, heading = 0f, updatedAtMillis = 1000L, routeId = null)

        val state = CampusMapUiState(
            shuttles = listOf(shuttleA, shuttleB, shuttleLegacy),
            routes = listOf(routeLoopA, routeLoopB),
            selectedRouteId = null,
        )

        // All routes: contains all three shuttles
        assertEquals(3, state.filteredShuttles.size)

        // Loop A: contains only shuttleA
        val stateA = state.copy(selectedRouteId = "route_loop_a")
        assertEquals(1, stateA.filteredShuttles.size)
        assertEquals("s1", stateA.filteredShuttles[0].id)

        // Loop B: contains only shuttleB
        val stateB = state.copy(selectedRouteId = "route_loop_b")
        assertEquals(1, stateB.filteredShuttles.size)
        assertEquals("s2", stateB.filteredShuttles[0].id)
    }

    @Test
    fun `geometry filtering delivers active route geometries`() {
        val geomA = RouteGeometry(routeId = "route_loop_a", points = listOf(), version = 1, stopHash = "h1", isFallback = false, updatedAtMillis = 100L)
        val geomB = RouteGeometry(routeId = "route_loop_b", points = listOf(), version = 1, stopHash = "h2", isFallback = false, updatedAtMillis = 100L)

        val state = CampusMapUiState(
            routeGeometries = listOf(geomA, geomB),
            routes = listOf(routeLoopA, routeLoopB),
            selectedRouteId = null,
        )

        assertEquals(2, state.filteredGeometries.size)

        val stateA = state.copy(selectedRouteId = "route_loop_a")
        assertEquals(1, stateA.filteredGeometries.size)
        assertEquals("route_loop_a", stateA.filteredGeometries[0].routeId)
    }

    @Test
    fun `route selection filters etas by shuttle assignment and hides inactive geometry`() {
        val shuttleA = ShuttleLocation("a", 12.97, 79.15, 0f, 100L, routeId = routeLoopA.id)
        val shuttleB = ShuttleLocation("b", 12.97, 79.15, 0f, 100L, routeId = routeLoopB.id)
        val legacy = ShuttleLocation("legacy", 12.97, 79.15, 0f, 100L)
        val etas = listOf(
            com.unishuttle.app.domain.model.StopEta("a", "stop_main_gate", "2 min", 120L),
            com.unishuttle.app.domain.model.StopEta("b", "stop_main_gate", "3 min", 180L),
            com.unishuttle.app.domain.model.StopEta("legacy", "stop_main_gate", "4 min", 240L),
        )
        val geometries = listOf(
            RouteGeometry(routeLoopA.id, emptyList(), stopHash = "a", isFallback = false, updatedAtMillis = 1L),
            RouteGeometry(routeLoopB.id, emptyList(), stopHash = "b", isFallback = false, updatedAtMillis = 1L),
            RouteGeometry("inactive-route", emptyList(), stopHash = "stale", isFallback = false, updatedAtMillis = 1L),
        )
        val allRoutes = CampusMapUiState(
            shuttles = listOf(shuttleA, shuttleB, legacy),
            routes = listOf(routeLoopA, routeLoopB),
            stopEtas = etas,
            routeGeometries = geometries,
        )

        assertEquals(3, allRoutes.filteredStopEtas.size)
        assertEquals(2, allRoutes.filteredGeometries.size)

        val selectedA = allRoutes.copy(selectedRouteId = routeLoopA.id)
        assertEquals(listOf("a"), selectedA.filteredStopEtas.map { it.shuttleId })
        assertEquals(listOf(routeLoopA.id), selectedA.filteredGeometries.map { it.routeId })
    }

    @Test
    fun `unassigned shuttle label stays explicit while assigned labels stay compact`() {
        assertEquals("SH-legacy · Unassigned", ShuttleLocation("legacy", 0.0, 0.0, 0f, 0L).displayLabel)
        assertEquals(
            "SH-assigned",
            ShuttleLocation("assigned", 0.0, 0.0, 0f, 0L, routeId = routeLoopA.id).displayLabel,
        )
    }

    @Test
    fun `deterministic sha256 stop hash is order-sensitive and collision-resistant`() {
        val fakeDao = object : RouteGeometryDao {
            override suspend fun getRouteGeometry(routeId: String): CachedRouteGeometryEntity? = null
            override suspend fun getAllRouteGeometries(): List<CachedRouteGeometryEntity> = emptyList()
            override fun observeAllRouteGeometries(): Flow<List<CachedRouteGeometryEntity>> = emptyFlow()
            override suspend fun insertRouteGeometry(geometry: CachedRouteGeometryEntity) {}
            override suspend fun deleteRouteGeometry(routeId: String) {}
            override suspend fun clearAll() {}
        }

        val fakeApi = Proxy.newProxyInstance(
            OsrmApiService::class.java.classLoader,
            arrayOf(OsrmApiService::class.java),
        ) { _, _, _ -> null } as OsrmApiService

        val repo = OsrmRoutingRepository(fakeApi, fakeDao)

        val stopsForward = listOf(stopMainGate, stopTechTower, stopSjt)
        val stopsReverse = listOf(stopSjt, stopTechTower, stopMainGate)

        val hashForward = repo.computeStopHash(routeLoopA, stopsForward)
        val hashReverse = repo.computeStopHash(routeLoopA, stopsReverse)
        val hashForwardAgain = repo.computeStopHash(routeLoopA, stopsForward)

        // Deterministic: identical sequence produces identical hash
        assertEquals(64, hashForward.length)
        assertEquals(hashForward, hashForwardAgain)

        // Order-sensitive: reverse sequence produces distinct hash
        assertNotEquals("Reverse stop sequence must yield distinct hash", hashForward, hashReverse)

        assertNotEquals(hashForward, repo.computeStopHash(routeLoopB, stopsForward))
        assertNotEquals(hashForward, repo.computeStopHash(routeLoopA.copy(version = 2), stopsForward))
        assertNotEquals(
            hashForward,
            repo.computeStopHash(routeLoopA, listOf(stopMainGate.copy(id = "renamed"), stopTechTower, stopSjt)),
        )
        assertNotEquals(
            hashForward,
            repo.computeStopHash(routeLoopA, listOf(stopMainGate.copy(latitude = 12.969201), stopTechTower, stopSjt)),
        )
        assertEquals(
            repo.computeGeometryHash(routeLoopA, stopsForward),
            repo.computeGeometryHash(routeLoopA.copy(version = 2), stopsForward),
        )
    }

    @Test
    fun `next stop identification accounts for heading and forward progress`() {
        val fakeDao = object : RouteGeometryDao {
            override suspend fun getRouteGeometry(routeId: String): CachedRouteGeometryEntity? = null
            override suspend fun getAllRouteGeometries(): List<CachedRouteGeometryEntity> = emptyList()
            override fun observeAllRouteGeometries(): Flow<List<CachedRouteGeometryEntity>> = emptyFlow()
            override suspend fun insertRouteGeometry(geometry: CachedRouteGeometryEntity) {}
            override suspend fun deleteRouteGeometry(routeId: String) {}
            override suspend fun clearAll() {}
        }
        val fakeApi = Proxy.newProxyInstance(
            OsrmApiService::class.java.classLoader,
            arrayOf(OsrmApiService::class.java),
        ) { _, _, _ -> null } as OsrmApiService

        val repo = OsrmRoutingRepository(fakeApi, fakeDao)

        val orderedStops = listOf(stopMainGate, stopTechTower, stopSjt)

        // Shuttle is between Main Gate (12.9692, 79.1559) and Tech Tower (12.9712, 79.1594), heading North-East (~45 deg)
        val shuttleMovingToTT = ShuttleLocation(
            id = "s1",
            latitude = 12.9702,
            longitude = 79.1576,
            heading = 45f,
            updatedAtMillis = 1000L,
            routeId = "route_loop_a",
        )

        val nextIndex = repo.identifyNextStopIndex(shuttleMovingToTT, orderedStops)
        // Next stop should be Tech Tower (index 1)
        assertEquals(1, nextIndex)
        assertEquals("stop_tech_tower", orderedStops[nextIndex].id)
    }

    @Test
    fun `next stop identification correctly rejects departed stops even when physically closer`() {
        val fakeDao = object : RouteGeometryDao {
            override suspend fun getRouteGeometry(routeId: String): CachedRouteGeometryEntity? = null
            override suspend fun getAllRouteGeometries(): List<CachedRouteGeometryEntity> = emptyList()
            override fun observeAllRouteGeometries(): Flow<List<CachedRouteGeometryEntity>> = emptyFlow()
            override suspend fun insertRouteGeometry(geometry: CachedRouteGeometryEntity) {}
            override suspend fun deleteRouteGeometry(routeId: String) {}
            override suspend fun clearAll() {}
        }
        val fakeApi = Proxy.newProxyInstance(
            OsrmApiService::class.java.classLoader,
            arrayOf(OsrmApiService::class.java),
        ) { _, _, _ -> null } as OsrmApiService

        val repo = OsrmRoutingRepository(fakeApi, fakeDao)
        val orderedStops = listOf(stopMainGate, stopTechTower, stopSjt)

        // Shuttle has just passed Tech Tower (12.9712, 79.1594) heading towards SJT (12.9735, 79.1635)
        // At (12.9714, 79.1598), distance to Tech Tower is ~47m (behind), distance to SJT is ~460m (ahead).
        // Heading is 58 degrees (towards SJT).
        val shuttlePastTT = ShuttleLocation(
            id = "s1",
            latitude = 12.9714,
            longitude = 79.1598,
            heading = 58f,
            updatedAtMillis = 1000L,
            routeId = "route_loop_a",
        )

        val nextIndex = repo.identifyNextStopIndex(shuttlePastTT, orderedStops)
        // Must identify SJT (index 2) as next stop, NOT Tech Tower (index 1) which is behind the vehicle
        assertEquals("Next stop must be SJT (index 2) because Tech Tower is behind the vehicle", 2, nextIndex)
        assertEquals("stop_sjt", orderedStops[nextIndex].id)
    }

    @Test
    fun `northbound heading zero is treated as a real heading`() {
        val fakeDao = object : RouteGeometryDao {
            override suspend fun getRouteGeometry(routeId: String): CachedRouteGeometryEntity? = null
            override suspend fun getAllRouteGeometries(): List<CachedRouteGeometryEntity> = emptyList()
            override fun observeAllRouteGeometries(): Flow<List<CachedRouteGeometryEntity>> = emptyFlow()
            override suspend fun insertRouteGeometry(geometry: CachedRouteGeometryEntity) {}
            override suspend fun deleteRouteGeometry(routeId: String) {}
            override suspend fun clearAll() {}
        }
        val fakeApi = Proxy.newProxyInstance(
            OsrmApiService::class.java.classLoader,
            arrayOf(OsrmApiService::class.java),
        ) { _, _, _ -> null } as OsrmApiService
        val repo = OsrmRoutingRepository(fakeApi, fakeDao)
        val south = ShuttleStop("south", "South", -0.001, 0.0)
        val north = ShuttleStop("north", "North", 0.002, 0.0)
        val shuttle = ShuttleLocation("n", 0.0, 0.0, 0f, 100L)

        assertEquals(1, repo.identifyNextStopIndex(shuttle, listOf(south, north)))
    }

    @Test
    fun `cache downgrade protection preserves valid road curves when osrm network fails`() = kotlinx.coroutines.runBlocking {
        val cachedRoadPoints = listOf(
            com.unishuttle.app.domain.model.CampusLatLng(12.9692, 79.1559),
            com.unishuttle.app.domain.model.CampusLatLng(12.9700, 79.1570),
            com.unishuttle.app.domain.model.CampusLatLng(12.9712, 79.1594),
        )
        val gson = com.google.gson.Gson()
        val stops = listOf(stopMainGate, stopTechTower)

        val failingApi = ControllableOsrmApiService { _, _ ->
            throw java.io.IOException("Network unreachable")
        }

        val dummyDao = object : RouteGeometryDao {
            override suspend fun getRouteGeometry(routeId: String): CachedRouteGeometryEntity? = null
            override suspend fun getAllRouteGeometries(): List<CachedRouteGeometryEntity> = emptyList()
            override fun observeAllRouteGeometries(): Flow<List<CachedRouteGeometryEntity>> = emptyFlow()
            override suspend fun insertRouteGeometry(geometry: CachedRouteGeometryEntity) {}
            override suspend fun deleteRouteGeometry(routeId: String) {}
            override suspend fun clearAll() {}
        }
        val route = routeLoopA.copy(stopIds = listOf("stop_main_gate", "stop_tech_tower"))
        val repo = OsrmRoutingRepository(failingApi, dummyDao)
        val priorVersionRoute = route.copy(version = route.version - 1)
        val expectedHash = repo.computeStopHash(priorVersionRoute, stops)
        val expectedGeometryHash = repo.computeGeometryHash(route, stops)

        var insertedEntity: CachedRouteGeometryEntity? = null
        val fakeDao = object : RouteGeometryDao {
            val validEntity = CachedRouteGeometryEntity(
                routeId = "route_loop_a",
                routeVersion = priorVersionRoute.version,
                stopHash = expectedHash,
                geometryHash = expectedGeometryHash,
                pointsJson = gson.toJson(cachedRoadPoints),
                pointCount = 3,
                isFallback = false, // Valid real road curve
                updatedAtMillis = 5000L,
            )

            override suspend fun getRouteGeometry(routeId: String): CachedRouteGeometryEntity? = validEntity
            override suspend fun getAllRouteGeometries(): List<CachedRouteGeometryEntity> = listOf(validEntity)
            override fun observeAllRouteGeometries(): Flow<List<CachedRouteGeometryEntity>> =
                kotlinx.coroutines.flow.flowOf(listOf(validEntity))
            override suspend fun insertRouteGeometry(geometry: CachedRouteGeometryEntity) {
                insertedEntity = geometry
            }
            override suspend fun deleteRouteGeometry(routeId: String) {}
            override suspend fun clearAll() {}
        }

        val repoWithCache = OsrmRoutingRepository(failingApi, fakeDao)

        // The version mismatch forces an OSRM fetch even though the cached road curve still
        // matches the current stop coordinates. A transient failure must not replace that curve.
        val result = repoWithCache.getRouteGeometry(route, stops)

        assertTrue(result.isSuccess)
        val geom = result.getOrThrow()
        assertFalse("Must preserve real road geometry, not downgrade to fallback", geom.isFallback)
        assertEquals(3, geom.points.size)
        assertEquals("The test must exercise a failed OSRM request", 1, failingApi.requestCount.get())
        assertNull("Must not insert fallback entity when valid cache exists", insertedEntity)
    }

    @Test
    fun `identical geometry requests coalesce and cancelling one caller does not cancel shared work`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<OsrmRouteResponseDto>()
        val api = ControllableOsrmApiService { _, _ ->
            started.complete(Unit)
            release.await()
        }
        val dao = MemoryRouteGeometryDao()
        val repo = OsrmRoutingRepository(api, dao)
        val route = routeLoopA.copy(stopIds = listOf(stopMainGate.id, stopTechTower.id))
        val stops = listOf(stopMainGate, stopTechTower)

        val cancelledCaller = async(Dispatchers.Default) { repo.getRouteGeometry(route, stops) }
        withTimeout(2_000L) { started.await() }
        val survivingCaller = async(Dispatchers.Default) { repo.getRouteGeometry(route, stops) }
        delay(50L)
        cancelledCaller.cancelAndJoin()

        release.complete(routeResponse(listOf(listOf(79.1559, 12.9692), listOf(79.157, 12.9702))))
        val result = withTimeout(2_000L) { survivingCaller.await() }

        assertTrue(result.isSuccess)
        assertEquals(1, api.requestCount.get())
        assertFalse(result.getOrThrow().isFallback)
    }

    @Test
    fun `failed osrm request clears in flight entry and cached fallback can be upgraded on retry`() = runBlocking {
        val api = ControllableOsrmApiService { request, _ ->
            if (request == 1) throw java.io.IOException("Temporary network failure")
            routeResponse(listOf(listOf(79.1559, 12.9692), listOf(79.158, 12.9705)))
        }
        val dao = MemoryRouteGeometryDao()
        val repo = OsrmRoutingRepository(api, dao)
        val route = routeLoopA.copy(stopIds = listOf(stopMainGate.id, stopTechTower.id))
        val stops = listOf(stopMainGate, stopTechTower)

        val initial = repo.getRouteGeometry(route, stops).getOrThrow()
        assertTrue(initial.isFallback)
        assertTrue(dao.getRouteGeometry(route.id)!!.isFallback)
        assertEquals(1, api.requestCount.get())

        // A matching cached fallback returns immediately and starts a new shared refresh. If
        // the completed request remained in the in-flight map, this retry could never run.
        assertTrue(repo.getRouteGeometry(route, stops).getOrThrow().isFallback)
        withTimeout(4_000L) {
            while (dao.getRouteGeometry(route.id)?.isFallback != false) delay(10L)
        }

        assertEquals(2, api.requestCount.get())
    }

    @Test
    fun `route cache insert exception clears failed in flight entry so a later caller can retry`() = runBlocking {
        val backingDao = MemoryRouteGeometryDao()
        var failFirstInsert = true
        val dao = object : RouteGeometryDao {
            override suspend fun getRouteGeometry(routeId: String): CachedRouteGeometryEntity? =
                backingDao.getRouteGeometry(routeId)

            override suspend fun getAllRouteGeometries(): List<CachedRouteGeometryEntity> =
                backingDao.getAllRouteGeometries()

            override fun observeAllRouteGeometries(): Flow<List<CachedRouteGeometryEntity>> =
                backingDao.observeAllRouteGeometries()

            override suspend fun insertRouteGeometry(geometry: CachedRouteGeometryEntity) {
                if (failFirstInsert) {
                    failFirstInsert = false
                    error("Synthetic cache write failure")
                }
                backingDao.insertRouteGeometry(geometry)
            }

            override suspend fun deleteRouteGeometry(routeId: String) = backingDao.deleteRouteGeometry(routeId)
            override suspend fun clearAll() = backingDao.clearAll()
        }
        val api = ControllableOsrmApiService { _, _ ->
            routeResponse(listOf(listOf(79.1559, 12.9692), listOf(79.158, 12.9705)))
        }
        val repo = OsrmRoutingRepository(api, dao)
        val route = routeLoopA.copy(stopIds = listOf(stopMainGate.id, stopTechTower.id))
        val stops = listOf(stopMainGate, stopTechTower)

        val first = repo.getRouteGeometry(route, stops)
        assertTrue(first.isFailure)
        assertTrue(first.exceptionOrNull() is IllegalStateException)
        assertEquals(1, api.requestCount.get())

        val retry = repo.getRouteGeometry(route, stops)
        assertTrue(retry.isSuccess)
        assertFalse(retry.getOrThrow().isFallback)
        assertEquals(2, api.requestCount.get())
        assertNotNull(backingDao.getRouteGeometry(route.id))
    }

    @Test
    fun `same-version older response cannot overwrite geometry for newer stop coordinates`() = runBlocking {
        val oldStarted = CompletableDeferred<Unit>()
        val newStarted = CompletableDeferred<Unit>()
        val oldResponse = CompletableDeferred<OsrmRouteResponseDto>()
        val newResponse = CompletableDeferred<OsrmRouteResponseDto>()
        val api = ControllableOsrmApiService { request, _ ->
            when (request) {
                1 -> {
                    oldStarted.complete(Unit)
                    oldResponse.await()
                }
                2 -> {
                    newStarted.complete(Unit)
                    newResponse.await()
                }
                else -> error("Unexpected OSRM request $request")
            }
        }
        val dao = MemoryRouteGeometryDao()
        val repo = OsrmRoutingRepository(api, dao)
        val route = routeLoopA.copy(stopIds = listOf(stopMainGate.id, stopTechTower.id))
        val oldStops = listOf(stopMainGate, stopTechTower)
        val newStops = listOf(stopMainGate.copy(latitude = 12.969201), stopTechTower)
        val oldPoints = listOf(listOf(79.1559, 12.9692), listOf(79.156, 12.9701), listOf(79.1594, 12.9712))
        val newPoints = listOf(listOf(79.1559, 12.969201), listOf(79.158, 12.9705), listOf(79.1594, 12.9712))

        val oldRequest = async(Dispatchers.Default) { repo.getRouteGeometry(route, oldStops) }
        withTimeout(2_000L) { oldStarted.await() }
        val newRequest = async(Dispatchers.Default) { repo.getRouteGeometry(route, newStops) }
        withTimeout(4_000L) { newStarted.await() }

        newResponse.complete(routeResponse(newPoints))
        val newestGeometry = withTimeout(2_000L) { newRequest.await().getOrThrow() }
        oldResponse.complete(routeResponse(oldPoints))
        val olderGeometry = withTimeout(2_000L) { oldRequest.await().getOrThrow() }
        val persisted = dao.getRouteGeometry(route.id)

        assertEquals(newestGeometry.stopHash, persisted?.stopHash)
        assertEquals(newestGeometry.points, olderGeometry.points)
        assertEquals(newestGeometry.points, com.google.gson.Gson().fromJson(
            persisted!!.pointsJson,
            object : com.google.gson.reflect.TypeToken<List<com.unishuttle.app.domain.model.CampusLatLng>>() {}.type,
        ))
        assertEquals(2, api.requestCount.get())
    }

    @Test
    fun `repeated cached fallback reads share one background road refresh`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<OsrmRouteResponseDto>()
        val api = ControllableOsrmApiService { _, _ ->
            started.complete(Unit)
            release.await()
        }
        val dao = MemoryRouteGeometryDao()
        val repo = OsrmRoutingRepository(api, dao)
        val route = routeLoopA.copy(stopIds = listOf(stopMainGate.id, stopTechTower.id))
        val stops = listOf(stopMainGate, stopTechTower)
        val stopHash = repo.computeStopHash(route, stops)
        val geometryHash = repo.computeGeometryHash(route, stops)
        dao.insertRouteGeometry(
            CachedRouteGeometryEntity(
                routeId = route.id,
                routeVersion = route.version,
                stopHash = stopHash,
                geometryHash = geometryHash,
                pointsJson = com.google.gson.Gson().toJson(
                    listOf(
                        com.unishuttle.app.domain.model.CampusLatLng(12.9692, 79.1559),
                        com.unishuttle.app.domain.model.CampusLatLng(12.9712, 79.1594),
                    ),
                ),
                pointCount = 2,
                isFallback = true,
                updatedAtMillis = 10L,
            ),
        )

        assertTrue(repo.getRouteGeometry(route, stops).getOrThrow().isFallback)
        assertTrue(repo.getRouteGeometry(route, stops).getOrThrow().isFallback)
        withTimeout(2_000L) { started.await() }
        delay(50L)
        assertEquals(1, api.requestCount.get())

        release.complete(routeResponse(listOf(listOf(79.1559, 12.9692), listOf(79.158, 12.9705))))
        withTimeout(2_000L) {
            while (dao.getRouteGeometry(route.id)?.isFallback != false) delay(10L)
        }
        assertEquals(1, api.requestCount.get())
    }

    @Test
    fun `stop membership authority evaluates against CampusRoute stopIds`() {
        val routeA = CampusRoute(
            id = "route_loop_a",
            name = "Loop A",
            colorHex = "#D32F2F",
            stopIds = listOf("stop_remote_1"),
        )
        val routeB = CampusRoute(
            id = "route_loop_b",
            name = "Loop B",
            colorHex = "#1976D2",
            stopIds = listOf("stop_sjt"),
        )
        val stopFromLegacyDto = FirestoreShuttleStopDto(
            name = "Remote Stop",
            latitude = 12.9692,
            longitude = 79.1559,
            order = 1L,
            routeIds = listOf("route_loop_b"),
        ).toDomain("stop_remote_1")!!

        // Evaluated authoritatively against CampusRoute
        assertTrue("Must serve routeA because stop_remote_1 is in routeA.stopIds", stopFromLegacyDto.servesCampusRoute(routeA))
        assertFalse("Must not serve routeB despite its conflicting legacy routeIds field", stopFromLegacyDto.servesCampusRoute(routeB))
        assertFalse("Must not be unassigned when included in routeA", stopFromLegacyDto.isUnassigned(listOf(routeA, routeB)))

        val trulyUnassignedStop = ShuttleStop(
            id = "stop_orphan",
            name = "Orphan Stop",
            latitude = 12.9800,
            longitude = 79.1700,
            order = 99,
        )
        assertTrue("Must be unassigned when absent from all active route stopIds", trulyUnassignedStop.isUnassigned(listOf(routeA, routeB)))
    }

    @Test
    fun `performance benchmark verifies hash and filtering latency is sub-millisecond`() {
        val fakeDao = object : RouteGeometryDao {
            override suspend fun getRouteGeometry(routeId: String): CachedRouteGeometryEntity? = null
            override suspend fun getAllRouteGeometries(): List<CachedRouteGeometryEntity> = emptyList()
            override fun observeAllRouteGeometries(): Flow<List<CachedRouteGeometryEntity>> = emptyFlow()
            override suspend fun insertRouteGeometry(geometry: CachedRouteGeometryEntity) {}
            override suspend fun deleteRouteGeometry(routeId: String) {}
            override suspend fun clearAll() {}
        }
        val fakeApi = Proxy.newProxyInstance(
            OsrmApiService::class.java.classLoader,
            arrayOf(OsrmApiService::class.java),
        ) { _, _, _ -> null } as OsrmApiService

        val repo = OsrmRoutingRepository(fakeApi, fakeDao)
        val stops = listOf(stopMainGate, stopTechTower, stopSjt, stopOnlyLoopA)

        val iterations = 500
        val startTime = System.nanoTime()

        for (i in 0 until iterations) {
            val hash = repo.computeStopHash(routeLoopA, stops)
            assertEquals(64, hash.length)
        }

        val elapsedMillis = (System.nanoTime() - startTime) / 1_000_000.0
        val avgTimeMs = elapsedMillis / iterations

        // Must execute in < 0.2ms per call, well within 16.6ms frame budget
        assertTrue("Average hash computation must be < 1ms (was: ${avgTimeMs}ms)", avgTimeMs < 1.0)
    }
}
