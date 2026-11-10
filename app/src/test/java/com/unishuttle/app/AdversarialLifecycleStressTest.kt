package com.unishuttle.app

import android.app.Application
import com.unishuttle.app.domain.model.AppUser
import com.unishuttle.app.domain.model.SeatAvailability
import com.unishuttle.app.domain.model.ShuttleLocation
import com.unishuttle.app.domain.model.UserRole
import com.unishuttle.app.domain.repository.AuthRepository
import com.unishuttle.app.domain.repository.AuthState
import com.unishuttle.app.domain.repository.SeatAvailabilityRepository
import com.unishuttle.app.domain.repository.ShuttleLocationRepository
import com.unishuttle.app.driver.DriverDutyCoordinator
import com.unishuttle.app.driver.DriverServiceBridge
import com.unishuttle.app.driver.DutyState
import com.unishuttle.app.driver.ServiceShutdownResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

private data class StressSeatUpdateCall(
    val shuttleId: String,
    val availableSeats: Int,
    val totalSeats: Int,
    val updatedAtMillis: Long,
)

class AdversarialLifecycleStressTest {

    // ── Challenge 1: Service Killed During Tracking (onDestroy unblocks subsequent off-duty) ──

    @Test
    fun `service killed during tracking - bridge forceReset unblocks subsequent offDuty and cleans remote state`() = runBlocking {
        val shuttleId = "shuttle_stress_1"
        val fakeAuth = StressAuthRepo(AppUser("d1", "d@u.edu", "Driver", UserRole.DRIVER, shuttleId))
        val bridge = DriverServiceBridge()
        val fakeLocation = StressLocationRepo()
        val fakeSeats = StressSeatRepo(listOf(SeatAvailability(shuttleId, 10, 20, 1000L)))

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = bridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
        )

        // 1. Simulate active tracking with coordinator OnDuty
        bridge.registerService { /* stop callback */ }
        assertTrue("Bridge reports service running", bridge.isServiceRunning.value)
        val dutyField = DriverDutyCoordinator::class.java.getDeclaredField("_dutyState").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val stateFlow = dutyField.get(coordinator) as MutableStateFlow<DutyState>
        stateFlow.value = DutyState.OnDuty

        // 2. Simulate Android OS killing the service (onDestroy calls bridge.forceReset())
        bridge.forceReset()
        assertFalse("Bridge reports service stopped after forceReset", bridge.isServiceRunning.value)

        // 3. Driver now triggers goOffDuty()
        val offDutyResult = coordinator.goOffDuty(shutdownTimeoutMillis = 1000L)
        assertTrue("Off-duty must succeed even when service was already killed", offDutyResult)
        assertTrue("Coordinator reaches OffDuty state", coordinator.dutyState.value is DutyState.OffDuty)

        // 4. Verify remote cleanup completed
        assertEquals("RTDB location removed", 1, fakeLocation.removeCalls.get())
        assertEquals("Firestore sentinel updated to 0L", 1, fakeSeats.updateCalls.size)
        assertEquals(0L, fakeSeats.updateCalls.first().updatedAtMillis)
    }

    // ── Challenge 2: Repeated Rapid Concurrent goOffDuty Calls ──

    @Test
    fun `rapid repeated concurrent goOffDuty calls deduplicate to single execution without race`() = runBlocking {
        val shuttleId = "shuttle_stress_2"
        val fakeAuth = StressAuthRepo(AppUser("d2", "d2@u.edu", "Driver2", UserRole.DRIVER, shuttleId))
        val bridge = DriverServiceBridge()
        val fakeLocation = StressLocationRepo(delayMillis = 50L)
        val fakeSeats = StressSeatRepo(
            initialSeats = listOf(SeatAvailability(shuttleId, 8, 16, 2000L)),
            delayMillis = 50L,
        )

        bridge.registerService { reqId ->
            launch(Dispatchers.Default) {
                delay(30L)
                bridge.notifyShutdownComplete(ServiceShutdownResult.Success, reqId)
            }
        }

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = bridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
        )

        // Launch 25 concurrent calls to goOffDuty
        val concurrentCount = 25
        val deferreds = (1..concurrentCount).map {
            async(Dispatchers.Default) {
                coordinator.goOffDuty(shutdownTimeoutMillis = 2000L)
            }
        }

        val results = deferreds.awaitAll()

        // All 25 calls must receive true
        results.forEach { result ->
            assertTrue("Every concurrent call must resolve to true", result)
        }

        // Remote operations must be executed EXACTLY ONCE
        assertEquals("RTDB removed exactly once", 1, fakeLocation.removeCalls.get())
        assertEquals("Firestore updated exactly once", 1, fakeSeats.updateCalls.size)
        assertTrue("Final state is OffDuty", coordinator.dutyState.value is DutyState.OffDuty)
    }

    // ── Challenge 3: In-Flight forceReset During Shutdown Handshake ──

    @Test
    fun `forceReset during in-flight shutdown handshake does not deadlock coordinator`() = runBlocking {
        val shuttleId = "shuttle_stress_3"
        val fakeAuth = StressAuthRepo(AppUser("d3", "d3@u.edu", "Driver3", UserRole.DRIVER, shuttleId))
        val bridge = DriverServiceBridge()
        val fakeLocation = StressLocationRepo()
        val fakeSeats = StressSeatRepo(listOf(SeatAvailability(shuttleId, 5, 10, 1000L)))

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = bridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
        )

        bridge.registerService {
            // Simulate slow or unresponsive service
        }

        // Launch goOffDuty
        val stopDeferred = async(Dispatchers.Default) {
            coordinator.goOffDuty(shutdownTimeoutMillis = 500L)
        }

        // While shutdown is waiting, simulate sudden onDestroy() / forceReset
        delay(50L)
        bridge.forceReset()

        // Off-duty should complete (either false or handled) without hanging
        val completedInTime = withTimeoutOrNull(2000L) {
            stopDeferred.await()
        }
        assertNotNull("goOffDuty must not deadlock when bridge.forceReset is invoked during handshake", completedInTime)

        // Subsequent off-duty attempt must work cleanly now that service is stopped
        val retryResult = coordinator.goOffDuty(shutdownTimeoutMillis = 500L)
        assertTrue("Subsequent goOffDuty succeeds cleanly after forceReset", retryResult)
        assertEquals("RTDB node removed on retry", 1, fakeLocation.removeCalls.get())
    }

    // ── Challenge 4: Coroutine Cancellation of goOffDuty and Recovery ──

    @Test
    fun `cancellation of goOffDuty does not permanently wedge subsequent calls`() = runBlocking {
        val shuttleId = "shuttle_stress_4"
        val fakeAuth = StressAuthRepo(AppUser("d4", "d4@u.edu", "Driver4", UserRole.DRIVER, shuttleId))
        val bridge = DriverServiceBridge()
        val fakeLocation = StressLocationRepo(delayMillis = 500L)
        val fakeSeats = StressSeatRepo(listOf(SeatAvailability(shuttleId, 12, 24, 1000L)), delayMillis = 500L)

        bridge.registerService { reqId ->
            launch(Dispatchers.Default) {
                delay(100L)
                bridge.notifyShutdownComplete(ServiceShutdownResult.Success, reqId)
            }
        }

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = bridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
        )

        // Launch and cancel a goOffDuty coroutine mid-flight (simulating timeout or scope cancellation)
        val cancellingJob = launch(Dispatchers.Default) {
            coordinator.goOffDuty(shutdownTimeoutMillis = 1000L)
        }
        delay(50L) // Wait until it enters goOffDuty
        cancellingJob.cancel()
        cancellingJob.join()

        // CRITICAL CHECK: Now call goOffDuty again from another caller (e.g. user retries from Dashboard)
        val recoveryResult = withTimeoutOrNull(3000L) {
            coordinator.goOffDuty(shutdownTimeoutMillis = 1000L)
        }

        assertNotNull("Subsequent goOffDuty must NOT hang forever after prior cancellation", recoveryResult)
        assertTrue("Subsequent goOffDuty recovers and completes successfully", recoveryResult == true)
        assertTrue("Final state is OffDuty", coordinator.dutyState.value is DutyState.OffDuty)
    }

    // ── Challenge 5: goAsync Bounded Timeout Simulation Under Cascade Failure ──

    @Test
    fun `cascade remote failure is strictly bounded and completes within timeout budget`() = runBlocking {
        val shuttleId = "shuttle_stress_5"
        val fakeAuth = StressAuthRepo(AppUser("d5", "d5@u.edu", "Driver5", UserRole.DRIVER, shuttleId))
        val bridge = DriverServiceBridge()
        val fakeLocation = StressLocationRepo(shouldTimeout = true)
        val fakeSeats = StressSeatRepo(shouldTimeout = true)

        bridge.registerService { reqId ->
            launch(Dispatchers.Default) {
                // Local shutdown takes 200ms
                delay(200L)
                bridge.notifyShutdownComplete(ServiceShutdownResult.Success, reqId)
            }
        }

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = bridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
        )

        val startTime = System.currentTimeMillis()
        // In DriverNotificationStopReceiver, withTimeoutOrNull(8_000L) bounds goOffDuty
        val receiverResult = withTimeoutOrNull(8_000L) {
            coordinator.goOffDuty(shutdownTimeoutMillis = 4_000L)
        }
        val elapsed = System.currentTimeMillis() - startTime

        // The receiver must complete within 8_500 ms limit
        assertTrue("Execution duration ($elapsed ms) must be within 8_500 ms", elapsed <= 8_500L)
        assertFalse("Off-duty returns false on cascade remote timeout", receiverResult == true)
        assertTrue("State is CleanupFailed", coordinator.dutyState.value is DutyState.CleanupFailed)
    }
}

// ── Test Double Implementations ──

private class StressAuthRepo(private val user: AppUser) : AuthRepository {
    override val authState: Flow<AuthState> = flowOf(AuthState.SignedIn(user))
    override suspend fun getCurrentUser(): AppUser = user
    override suspend fun signInWithGoogle(idToken: String, role: UserRole): Result<AppUser> = Result.success(user)
    override suspend fun reloadProfile(): Result<AppUser> = Result.success(user)
    override suspend fun logout() {}
    override suspend fun refreshIdToken(): String? = "token"
}

private class StressLocationRepo(
    private val delayMillis: Long = 0L,
    private val shouldTimeout: Boolean = false,
) : ShuttleLocationRepository {
    val removeCalls = AtomicInteger(0)

    override fun observeShuttleLocations(): Flow<List<ShuttleLocation>> = flowOf(emptyList())
    override suspend fun updateDriverLocation(shuttleId: String, latitude: Double, longitude: Double, heading: Float, updatedAtMillis: Long) {}
    override suspend fun removeDriverLocation(shuttleId: String) {
        if (shouldTimeout) {
            delay(10_000L) // Will exceed 5_000L timeout
        } else if (delayMillis > 0L) {
            delay(delayMillis)
        }
        removeCalls.incrementAndGet()
    }
}

private class StressSeatRepo(
    initialSeats: List<SeatAvailability> = emptyList(),
    private val delayMillis: Long = 0L,
    private val shouldTimeout: Boolean = false,
) : SeatAvailabilityRepository {
    val seatsFlow = MutableStateFlow(initialSeats)
    val updateCalls = mutableListOf<StressSeatUpdateCall>()

    override fun observeSeatAvailability(): Flow<List<SeatAvailability>> = flow {
        if (shouldTimeout) {
            delay(10_000L) // Will exceed 3_000L timeout
        } else if (delayMillis > 0L) {
            delay(delayMillis)
        }
        seatsFlow.collect { emit(it) }
    }

    override suspend fun updateSeatAvailability(shuttleId: String, availableSeats: Int, totalSeats: Int, updatedAtMillis: Long) {
        if (shouldTimeout) {
            delay(10_000L)
        } else if (delayMillis > 0L) {
            delay(delayMillis)
        }
        updateCalls.add(StressSeatUpdateCall(shuttleId, availableSeats, totalSeats, updatedAtMillis))
    }
}
