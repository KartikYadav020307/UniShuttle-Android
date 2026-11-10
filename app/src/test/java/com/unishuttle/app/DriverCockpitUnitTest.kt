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
import com.unishuttle.app.driver.DriverLocationService
import com.unishuttle.app.driver.DriverNotificationStopReceiver
import com.unishuttle.app.driver.DriverServiceBridge
import com.unishuttle.app.driver.DutyState
import com.unishuttle.app.driver.InMemoryDriverDutyStorage
import com.unishuttle.app.driver.ServiceShutdownResult
import com.unishuttle.app.driver.ServiceStartResult
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverCockpitUnitTest {

    // ── 1. Domain & Dynamic Capacity Bounds ──

    @Test
    fun `SeatAvailability isOffDuty is true strictly when updatedAtMillis is zero or negative`() {
        val offDutyZero = SeatAvailability(shuttleId = "101", availableSeats = 10, totalSeats = 24, updatedAtMillis = 0L)
        val offDutyNegative = SeatAvailability(shuttleId = "101", availableSeats = 10, totalSeats = 24, updatedAtMillis = -1L)
        val onDutyActive = SeatAvailability(shuttleId = "101", availableSeats = 10, totalSeats = 24, updatedAtMillis = 1700000000000L)

        assertTrue("Zero timestamp represents off-duty sentinel", offDutyZero.isOffDuty)
        assertTrue("Negative timestamp represents off-duty sentinel", offDutyNegative.isOffDuty)
        assertFalse("Positive timestamp represents active on-duty shuttle", onDutyActive.isOffDuty)
    }

    @Test
    fun `SeatAvailability ratio and fullness bounds calculate accurately`() {
        val emptyBus = SeatAvailability(shuttleId = "101", availableSeats = 24, totalSeats = 24, updatedAtMillis = 1000L)
        val fullBus = SeatAvailability(shuttleId = "101", availableSeats = 0, totalSeats = 24, updatedAtMillis = 1000L)
        val halfBus = SeatAvailability(shuttleId = "101", availableSeats = 12, totalSeats = 24, updatedAtMillis = 1000L)

        assertEquals(0f, emptyBus.occupiedRatio, 0.001f)
        assertEquals(1f, emptyBus.availableRatio, 0.001f)
        assertFalse(emptyBus.isFull)

        assertEquals(1f, fullBus.occupiedRatio, 0.001f)
        assertEquals(0f, fullBus.availableRatio, 0.001f)
        assertTrue(fullBus.isFull)

        assertEquals(0.5f, halfBus.occupiedRatio, 0.001f)
        assertEquals(0.5f, halfBus.availableRatio, 0.001f)
        assertFalse(halfBus.isFull)
    }

    @Test
    fun `Seat adjustment delta clamps strictly between 0 and vehicle capacity`() {
        val totalSeats = 32
        var availableSeats = 0

        // Decrement below 0 is clamped
        availableSeats = (availableSeats - 1).coerceIn(0, totalSeats)
        assertEquals(0, availableSeats)

        // Increment to max
        availableSeats = 32
        availableSeats = (availableSeats + 1).coerceIn(0, totalSeats)
        assertEquals(32, availableSeats)

        // Mid-range increment
        availableSeats = 15
        availableSeats = (availableSeats + 1).coerceIn(0, totalSeats)
        assertEquals(16, availableSeats)

        // Mid-range decrement
        availableSeats = (availableSeats - 1).coerceIn(0, totalSeats)
        assertEquals(15, availableSeats)
    }

    // ── 2. Service Bridge Symmetrical Handshakes ──

    @Test
    fun `DriverServiceBridge prepareStart increments request id monotonically`() = runBlocking {
        val bridge = DriverServiceBridge()
        val (req1, _) = bridge.prepareStart()
        val (req2, _) = bridge.prepareStart()
        val (req3, _) = bridge.prepareStart()

        assertEquals(1L, req1)
        assertEquals(2L, req2)
        assertEquals(3L, req3)
    }

    @Test
    fun `DriverServiceBridge start handshake completes on matching request id`() = runBlocking {
        val bridge = DriverServiceBridge()
        val (reqId, deferred) = bridge.prepareStart()

        assertFalse(deferred.isCompleted)
        bridge.notifyStartComplete(reqId, ServiceStartResult.Success)
        assertTrue(deferred.isCompleted)
        assertTrue(deferred.await() is ServiceStartResult.Success)
    }

    @Test
    fun `DriverServiceBridge ignores stale start complete from timed out request`() = runBlocking {
        val bridge = DriverServiceBridge()
        val (req1, deferred1) = bridge.prepareStart()
        val (req2, deferred2) = bridge.prepareStart()

        // Belated response for req1
        bridge.notifyStartComplete(req1, ServiceStartResult.Success)
        assertFalse("Stale response must not complete active startDeferred", deferred2.isCompleted)

        // Matching response for req2
        bridge.notifyStartComplete(req2, ServiceStartResult.Success)
        assertTrue("Matching response completes active startDeferred", deferred2.isCompleted)
    }

    @Test
    fun `DriverServiceBridge shutdown handshake triggers callback and awaits completion`() = runBlocking {
        val bridge = DriverServiceBridge()
        var callbackInvoked = false

        bridge.registerService { reqId ->
            callbackInvoked = true
            launch {
                // Simulate service acknowledging shutdown with explicit requestId
                bridge.notifyShutdownComplete(ServiceShutdownResult.Success, reqId)
            }
        }
        assertTrue(bridge.isServiceRunning.value)

        val result = bridge.requestShutdown(timeoutMillis = 1000L)
        assertTrue(callbackInvoked)
        assertTrue(result is ServiceShutdownResult.Success)
        assertFalse("Bridge unregisters running service upon confirmed Success", bridge.isServiceRunning.value)
    }

    @Test
    fun `DriverServiceBridge shutdown failure preserves running state for safe retry`() = runBlocking {
        val bridge = DriverServiceBridge()
        bridge.registerService { reqId ->
            launch {
                bridge.notifyShutdownComplete(ServiceShutdownResult.Failure("FusedLocationProvider failure"), reqId)
            }
        }
        assertTrue(bridge.isServiceRunning.value)

        val result = bridge.requestShutdown(timeoutMillis = 1000L)
        assertTrue(result is ServiceShutdownResult.Failure)
        // CRITICAL: Must not unregister on failure so that subsequent retry does not falsely think service is stopped!
        assertTrue("Failed shutdown retains registered state for retry", bridge.isServiceRunning.value)
    }

    @Test
    fun `DriverServiceBridge returns Success immediately if service already stopped`() = runBlocking {
        val bridge = DriverServiceBridge()
        assertFalse(bridge.isServiceRunning.value)

        val result = bridge.requestShutdown(timeoutMillis = 100L)
        assertTrue("Stopping an already-stopped service is an immediate idempotent Success", result is ServiceShutdownResult.Success)
    }

    // ── 3. Duty Coordinator Orchestration & Failsafe Invariants ──

    @Test
    fun `DriverDutyCoordinator fails closed when driver has blank shuttle id`() = runBlocking {
        val fakeAuth = FakeAuthRepo(AppUser(uid = "d1", email = "driver@campus.edu", displayName = "Driver Dave", role = UserRole.DRIVER, shuttleId = ""))
        val fakeBridge = DriverServiceBridge()
        val fakeLocation = FakeLocationRepo()
        val fakeSeats = FakeSeatRepo()

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = fakeBridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
        )

        val success = coordinator.goOnDuty()
        assertFalse(success)
        assertTrue(coordinator.dutyState.value is DutyState.Error)
        assertEquals("No valid shuttle ID assigned to driver.", (coordinator.dutyState.value as DutyState.Error).message)
    }

    @Test
    fun `DriverDutyCoordinator goOffDuty ABORTS remote cleanup when local shutdown fails`() = runBlocking {
        val fakeAuth = FakeAuthRepo(AppUser(uid = "d1", email = "driver@campus.edu", displayName = "Driver Dave", role = UserRole.DRIVER, shuttleId = "101"))
        val fakeBridge = DriverServiceBridge()
        // Register service with a mock stop callback that NEVER completes
        fakeBridge.registerService { /* never acknowledges */ }

        val fakeLocation = FakeLocationRepo()
        val fakeSeats = FakeSeatRepo()

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = fakeBridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
        )

        // Attempt goOffDuty with short timeout bridge will time out
        val success = coordinator.goOffDuty(shutdownTimeoutMillis = 100L)
        assertFalse(success)
        assertTrue(coordinator.dutyState.value is DutyState.CleanupFailed)

        // CRITICAL INVARIANT: Neither RTDB nor Firestore remote cleanup must execute if local GPS wasn't confirmed stopped!
        assertEquals("RTDB deletion must NOT execute when local shutdown fails", 0, fakeLocation.removeCalls.size)
        assertEquals("Firestore cleanup must NOT execute when local shutdown fails", 0, fakeSeats.updateCalls.size)
    }

    @Test
    fun `DriverDutyCoordinator goOffDuty executes remote cleanup and preserves authoritative capacity`() = runBlocking {
        val shuttleId = "101"
        val fakeAuth = FakeAuthRepo(AppUser(uid = "d1", email = "driver@campus.edu", displayName = "Driver Dave", role = UserRole.DRIVER, shuttleId = shuttleId))
        val fakeBridge = DriverServiceBridge()
        fakeBridge.registerService { reqId ->
            launch { fakeBridge.notifyShutdownComplete(ServiceShutdownResult.Success, reqId) }
        }

        val fakeLocation = FakeLocationRepo()
        val fakeSeats = FakeSeatRepo(
            initialSeats = listOf(
                SeatAvailability(shuttleId = shuttleId, availableSeats = 17, totalSeats = 28, updatedAtMillis = 5000L)
            )
        )

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = fakeBridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
        )

        val success = coordinator.goOffDuty()
        assertTrue(success)
        assertTrue(coordinator.dutyState.value is DutyState.OffDuty)

        // 1. RTDB node removed
        assertEquals(1, fakeLocation.removeCalls.size)
        assertEquals(shuttleId, fakeLocation.removeCalls.first())

        // 2. Firestore updated with 0L sentinel and authoritative capacity preserved
        assertEquals(1, fakeSeats.updateCalls.size)
        val update = fakeSeats.updateCalls.first()
        assertEquals(shuttleId, update.shuttleId)
        assertEquals("Authoritative availableSeats must be preserved, not fabricated to 0", 17, update.availableSeats)
        assertEquals("Authoritative totalSeats must be preserved, not fabricated to 0", 28, update.totalSeats)
        assertEquals("Off-duty sentinel must be 0L", 0L, update.updatedAtMillis)
    }

    // ── 4. Service Contracts & Intent Constants ──

    @Test
    fun `Service and BroadcastReceiver intent contracts are well-defined`() {
        assertEquals("extra_shuttle_id", DriverLocationService.EXTRA_SHUTTLE_ID)
        assertEquals("extra_start_request_id", DriverLocationService.EXTRA_START_REQUEST_ID)
        assertEquals("com.unishuttle.app.driver.ACTION_NOTIFICATION_STOP", DriverNotificationStopReceiver.ACTION_NOTIFICATION_STOP)
    }

    // ── 5. Hardened Edge Cases (R1, R2, R3) ──

    @Test
    fun `DriverDutyCoordinator goOffDuty deletes RTDB node even when Firestore throws or is unavailable`() = runBlocking {
        val shuttleId = "101"
        val fakeAuth = FakeAuthRepo(AppUser(uid = "d1", email = "driver@campus.edu", displayName = "Driver Dave", role = UserRole.DRIVER, shuttleId = shuttleId))
        val fakeBridge = DriverServiceBridge()
        fakeBridge.registerService { reqId ->
            launch { fakeBridge.notifyShutdownComplete(ServiceShutdownResult.Success, reqId) }
        }

        val fakeLocation = FakeLocationRepo()
        val fakeSeats = FakeSeatRepo()
        fakeSeats.shouldThrowOnObserve = true // Simulate Firestore offline / unprovisioned

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = fakeBridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
        )

        val success = coordinator.goOffDuty()
        assertFalse("Off-duty returns false on partial cleanup failure", success)
        assertTrue(coordinator.dutyState.value is DutyState.CleanupFailed)
        val cleanupState = coordinator.dutyState.value as DutyState.CleanupFailed
        assertFalse("RTDB succeeded, so rtdbFailed must be false", cleanupState.rtdbFailed)
        assertTrue("Firestore failed, so firestoreFailed must be true", cleanupState.firestoreFailed)

        // CRITICAL INVARIANT: RTDB node was deleted despite Firestore throwing exception
        assertEquals(1, fakeLocation.removeCalls.size)
        assertEquals(shuttleId, fakeLocation.removeCalls.first())
        assertEquals("Firestore update must not have been executed", 0, fakeSeats.updateCalls.size)
    }

    @Test
    fun `DriverDutyCoordinator goOffDuty never writes 24 or fabricated capacity when seat data is missing`() = runBlocking {
        val shuttleId = "101"
        val fakeAuth = FakeAuthRepo(AppUser(uid = "d1", email = "driver@campus.edu", displayName = "Driver Dave", role = UserRole.DRIVER, shuttleId = shuttleId))
        val fakeBridge = DriverServiceBridge()
        fakeBridge.registerService { reqId ->
            launch { fakeBridge.notifyShutdownComplete(ServiceShutdownResult.Success, reqId) }
        }

        val fakeLocation = FakeLocationRepo()
        val fakeSeats = FakeSeatRepo(initialSeats = emptyList()) // No seat document for this shuttle

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = fakeBridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
        )

        val success = coordinator.goOffDuty()
        assertFalse(success)
        val cleanupState = coordinator.dutyState.value as DutyState.CleanupFailed
        assertFalse("RTDB succeeded", cleanupState.rtdbFailed)
        assertTrue("Firestore failed safely without corrupting data", cleanupState.firestoreFailed)

        // Invariant: Never invent 24 or call updateSeatAvailability with fabricated numbers
        assertEquals("No fake seat updates should be sent to Firestore", 0, fakeSeats.updateCalls.size)
        assertEquals(1, fakeLocation.removeCalls.size)
    }

    @Test
    fun `DriverDutyCoordinator goOffDuty selective retry completes only previously failed step`() = runBlocking {
        val shuttleId = "101"
        val fakeAuth = FakeAuthRepo(AppUser(uid = "d1", email = "driver@campus.edu", displayName = "Driver Dave", role = UserRole.DRIVER, shuttleId = shuttleId))
        val fakeBridge = DriverServiceBridge()
        fakeBridge.registerService { reqId ->
            launch { fakeBridge.notifyShutdownComplete(ServiceShutdownResult.Success, reqId) }
        }

        val fakeLocation = FakeLocationRepo()
        val fakeSeats = FakeSeatRepo(
            initialSeats = listOf(
                SeatAvailability(shuttleId = shuttleId, availableSeats = 15, totalSeats = 30, updatedAtMillis = 5000L)
            )
        )
        fakeSeats.shouldFailUpdate = true // Initial attempt fails at Firestore

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = fakeBridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
        )

        // Attempt 1: RTDB succeeds, Firestore fails
        val success1 = coordinator.goOffDuty()
        assertFalse(success1)
        val cleanupState = coordinator.dutyState.value as DutyState.CleanupFailed
        assertFalse(cleanupState.rtdbFailed)
        assertTrue(cleanupState.firestoreFailed)
        assertEquals(1, fakeLocation.removeCalls.size)
        assertEquals(0, fakeSeats.updateCalls.size)

        // Fix Firestore for Retry
        fakeSeats.shouldFailUpdate = false

        // Attempt 2 (Retry): Selective retry should NOT call RTDB removal again
        val success2 = coordinator.goOffDuty()
        assertTrue(success2)
        assertTrue(coordinator.dutyState.value is DutyState.OffDuty)
        assertEquals("RTDB removal must NOT be repeated during selective retry", 1, fakeLocation.removeCalls.size)
        assertEquals("Firestore must be updated during selective retry", 1, fakeSeats.updateCalls.size)
        assertEquals(30, fakeSeats.updateCalls.first().totalSeats)
    }

    @Test
    fun `DriverDutyCoordinator goOffDuty concurrent calls join single in-flight cleanup`() = runBlocking {
        val shuttleId = "101"
        val fakeAuth = FakeAuthRepo(AppUser(uid = "d1", email = "driver@campus.edu", displayName = "Driver Dave", role = UserRole.DRIVER, shuttleId = shuttleId))
        val fakeBridge = DriverServiceBridge()
        fakeBridge.registerService { reqId ->
            launch {
                delay(50L)
                fakeBridge.notifyShutdownComplete(ServiceShutdownResult.Success, reqId)
            }
        }

        val fakeLocation = FakeLocationRepo()
        val fakeSeats = FakeSeatRepo(
            initialSeats = listOf(
                SeatAvailability(shuttleId = shuttleId, availableSeats = 10, totalSeats = 20, updatedAtMillis = 5000L)
            )
        )

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = fakeBridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
        )

        // Launch concurrent stop requests
        val job1 = async { coordinator.goOffDuty() }
        val job2 = async { coordinator.goOffDuty() }

        val res1 = job1.await()
        val res2 = job2.await()

        assertTrue(res1)
        assertTrue(res2)
        assertTrue(coordinator.dutyState.value is DutyState.OffDuty)
        assertEquals("RTDB removal called exactly once despite duplicate requests", 1, fakeLocation.removeCalls.size)
        assertEquals("Firestore update called exactly once despite duplicate requests", 1, fakeSeats.updateCalls.size)
    }

    @Test
    fun `DriverServiceBridge cancelStart invalidates active request and rejects late acknowledgements`() = runBlocking {
        val bridge = DriverServiceBridge()
        val (reqId, deferred) = bridge.prepareStart()
        bridge.registerService { }
        assertTrue(bridge.isServiceRunning.value)

        bridge.cancelStart(reqId, "Startup timed out")
        assertTrue(deferred.isCompleted)
        assertTrue(deferred.await() is ServiceStartResult.Failure)
        assertFalse("Running flag must be reset on cancel", bridge.isServiceRunning.value)

        // Late acknowledgement arrives from slow service
        bridge.notifyStartComplete(reqId, ServiceStartResult.Success)
        assertFalse("Late acknowledgement must NOT flip isServiceRunning back to true", bridge.isServiceRunning.value)
    }

    @Test
    fun `DriverServiceBridge start failure cleans up stopCallback and running state`() = runBlocking {
        val bridge = DriverServiceBridge()
        val (reqId, _) = bridge.prepareStart()
        bridge.registerService { }
        assertTrue(bridge.isServiceRunning.value)

        bridge.notifyStartComplete(reqId, ServiceStartResult.Failure("Init error"))
        assertFalse(bridge.isServiceRunning.value)

        // Requesting shutdown on stopped service returns Success immediately without invoking stale callback
        val result = bridge.requestShutdown()
        assertTrue(result is ServiceShutdownResult.Success)
    }

    @Test
    fun `DriverServiceBridge monotonic shutdown request IDs reject stale responses`() = runBlocking {
        val bridge = DriverServiceBridge()
        bridge.registerService { }
        assertTrue(bridge.isServiceRunning.value)

        // Attempt 1: times out
        val timeoutResult = bridge.requestShutdown(timeoutMillis = 10L)
        assertTrue(timeoutResult is ServiceShutdownResult.TimedOut)

        // Attempt 2: retry
        val retryDeferred = async { bridge.requestShutdown(timeoutMillis = 1000L) }
        delay(50L)

        // Arbitrary mismatched response (reqId = 999L)
        bridge.notifyShutdownComplete(ServiceShutdownResult.Failure("mismatched failure"), requestId = 999L)
        assertFalse("Mismatched response must not complete retryDeferred", retryDeferred.isCompleted)

        // Stale response for attempt 1 (reqId = 1L)
        bridge.notifyShutdownComplete(ServiceShutdownResult.Failure("stale failure"), requestId = 1L)
        assertFalse("Stale response must not complete retryDeferred", retryDeferred.isCompleted)

        // Matching response for attempt 2 (reqId = 2L)
        bridge.notifyShutdownComplete(ServiceShutdownResult.Success, requestId = 2L)
        val result = retryDeferred.await()
        assertTrue("Matching response completes retryDeferred with Success", result is ServiceShutdownResult.Success)
        assertFalse(bridge.isServiceRunning.value)
    }

    @Test
    fun `DriverServiceBridge forceReset clears running state and callbacks`() = runBlocking {
        val bridge = DriverServiceBridge()
        bridge.registerService { }
        assertTrue(bridge.isServiceRunning.value)

        bridge.forceReset()
        assertFalse(bridge.isServiceRunning.value)

        val result = bridge.requestShutdown()
        assertTrue(result is ServiceShutdownResult.Success)
    }

    // ── 6. Adversarial Stress-Harness Tests ──

    @Test
    fun `DriverDutyCoordinator goOffDuty handles RTDB failure while Firestore succeeds and selective retry only retries RTDB`() = runBlocking {
        val shuttleId = "101"
        val fakeAuth = FakeAuthRepo(AppUser(uid = "d1", email = "driver@campus.edu", displayName = "Driver Dave", role = UserRole.DRIVER, shuttleId = shuttleId))
        val fakeBridge = DriverServiceBridge()
        fakeBridge.registerService { reqId ->
            launch { fakeBridge.notifyShutdownComplete(ServiceShutdownResult.Success, reqId) }
        }

        val fakeLocation = FakeLocationRepo()
        fakeLocation.shouldFailRemove = true // RTDB fails on first attempt

        val fakeSeats = FakeSeatRepo(
            initialSeats = listOf(
                SeatAvailability(shuttleId = shuttleId, availableSeats = 14, totalSeats = 28, updatedAtMillis = 5000L)
            )
        )

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = fakeBridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
        )

        // Attempt 1: RTDB fails, Firestore succeeds
        val success1 = coordinator.goOffDuty()
        assertFalse(success1)
        assertTrue(coordinator.dutyState.value is DutyState.CleanupFailed)
        val cleanupState = coordinator.dutyState.value as DutyState.CleanupFailed
        assertTrue("RTDB failed", cleanupState.rtdbFailed)
        assertFalse("Firestore succeeded", cleanupState.firestoreFailed)
        assertEquals(0, fakeLocation.removeCalls.size)
        assertEquals(1, fakeSeats.updateCalls.size)

        // Fix RTDB for retry
        fakeLocation.shouldFailRemove = false

        // Attempt 2 (Selective Retry): Should ONLY execute RTDB deletion, NOT repeat Firestore update
        val success2 = coordinator.goOffDuty()
        assertTrue(success2)
        assertTrue(coordinator.dutyState.value is DutyState.OffDuty)
        assertEquals("RTDB removal must be called once during selective retry", 1, fakeLocation.removeCalls.size)
        assertEquals("Firestore update must NOT be repeated during selective retry", 1, fakeSeats.updateCalls.size)
    }

    @Test
    fun `DriverDutyCoordinator goOffDuty handles 10 rapid concurrent stop requests without race conditions`() = runBlocking {
        val shuttleId = "101"
        val fakeAuth = FakeAuthRepo(AppUser(uid = "d1", email = "driver@campus.edu", displayName = "Driver Dave", role = UserRole.DRIVER, shuttleId = shuttleId))
        val fakeBridge = DriverServiceBridge()
        fakeBridge.registerService { reqId ->
            launch {
                delay(20L)
                fakeBridge.notifyShutdownComplete(ServiceShutdownResult.Success, reqId)
            }
        }

        val fakeLocation = FakeLocationRepo()
        val fakeSeats = FakeSeatRepo(
            initialSeats = listOf(
                SeatAvailability(shuttleId = shuttleId, availableSeats = 12, totalSeats = 24, updatedAtMillis = 5000L)
            )
        )

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = fakeBridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
        )

        val deferreds = (1..10).map {
            async { coordinator.goOffDuty() }
        }

        val results = deferreds.map { it.await() }
        assertTrue("All 10 concurrent requests must return true", results.all { it })
        assertTrue(coordinator.dutyState.value is DutyState.OffDuty)
        assertEquals("RTDB removal called exactly once across 10 concurrent requests", 1, fakeLocation.removeCalls.size)
        assertEquals("Firestore update called exactly once across 10 concurrent requests", 1, fakeSeats.updateCalls.size)
    }

    @Test
    fun `DriverDutyCoordinator goOffDuty completes RTDB deletion even when Firestore flow hangs`() = runBlocking {
        val shuttleId = "101"
        val fakeAuth = FakeAuthRepo(AppUser(uid = "d1", email = "driver@campus.edu", displayName = "Driver Dave", role = UserRole.DRIVER, shuttleId = shuttleId))
        val fakeBridge = DriverServiceBridge()
        fakeBridge.registerService { reqId ->
            launch { fakeBridge.notifyShutdownComplete(ServiceShutdownResult.Success, reqId) }
        }

        val fakeLocation = FakeLocationRepo()
        val fakeSeats = FakeSeatRepo()
        fakeSeats.shouldHangOnObserve = true // Simulate hanging Firestore stream

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = fakeBridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
        )

        val success = coordinator.goOffDuty()
        assertFalse("Off-duty returns false when Firestore hangs", success)
        assertTrue(coordinator.dutyState.value is DutyState.CleanupFailed)
        val cleanupState = coordinator.dutyState.value as DutyState.CleanupFailed
        assertFalse("RTDB succeeded before Firestore hung", cleanupState.rtdbFailed)
        assertTrue("Firestore timed out / hung", cleanupState.firestoreFailed)

        // EMPIRICAL PROOF: RTDB deletion completed despite Firestore hanging
        assertEquals("RTDB deletion must have completed", 1, fakeLocation.removeCalls.size)
        assertEquals(shuttleId, fakeLocation.removeCalls.first())
        assertEquals("Firestore update was never reached", 0, fakeSeats.updateCalls.size)
    }

    // ── Dedicated Milestone 2 Test Scenarios ──

    @Test
    fun `DriverDutyCoordinator goOffDuty handles RTDB deletion succeeding while Firestore cleanup fails`() = runBlocking {
        val shuttleId = "101"
        val fakeAuth = FakeAuthRepo(AppUser(uid = "d1", email = "driver@campus.edu", displayName = "Driver Dave", role = UserRole.DRIVER, shuttleId = shuttleId))
        val fakeBridge = DriverServiceBridge()
        fakeBridge.registerService { reqId ->
            launch { fakeBridge.notifyShutdownComplete(ServiceShutdownResult.Success, reqId) }
        }

        val fakeLocation = FakeLocationRepo()
        val fakeSeats = FakeSeatRepo(
            initialSeats = listOf(
                SeatAvailability(shuttleId = shuttleId, availableSeats = 10, totalSeats = 20, updatedAtMillis = 5000L)
            )
        )
        fakeSeats.shouldFailUpdate = true // Firestore fails on update

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = fakeBridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
        )

        val success = coordinator.goOffDuty()
        assertFalse("Off-duty returns false when Firestore cleanup fails", success)
        assertTrue(coordinator.dutyState.value is DutyState.CleanupFailed)
        val cleanupState = coordinator.dutyState.value as DutyState.CleanupFailed
        assertFalse("RTDB deletion must succeed (rtdbFailed = false)", cleanupState.rtdbFailed)
        assertTrue("Firestore cleanup must fail (firestoreFailed = true)", cleanupState.firestoreFailed)

        // Verifications
        assertEquals("RTDB location must be removed", 1, fakeLocation.removeCalls.size)
        assertEquals(shuttleId, fakeLocation.removeCalls.first())
        assertEquals("Firestore update was attempted but failed", 0, fakeSeats.updateCalls.size)
    }

    @Test
    fun `DriverDutyCoordinator and notification stop timeout followed by successful retry`() = runBlocking {
        val shuttleId = "101"
        val fakeAuth = FakeAuthRepo(AppUser(uid = "d1", email = "driver@campus.edu", displayName = "Driver Dave", role = UserRole.DRIVER, shuttleId = shuttleId))
        val fakeBridge = DriverServiceBridge()

        var shutdownAttempts = 0
        fakeBridge.registerService { reqId ->
            shutdownAttempts++
            if (shutdownAttempts == 1) {
                // First attempt: simulate service hanging / timing out (no response)
            } else {
                // Retry attempt: acknowledges immediately
                launch { fakeBridge.notifyShutdownComplete(ServiceShutdownResult.Success, reqId) }
            }
        }

        val fakeLocation = FakeLocationRepo()
        val fakeSeats = FakeSeatRepo(
            initialSeats = listOf(
                SeatAvailability(shuttleId = shuttleId, availableSeats = 8, totalSeats = 16, updatedAtMillis = 5000L)
            )
        )

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = fakeBridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
        )

        // Attempt 1: Stop request times out within bounded goAsync budget
        val success1 = coordinator.goOffDuty(shutdownTimeoutMillis = 50L)
        assertFalse("Attempt 1 must fail due to shutdown timeout", success1)
        assertTrue(coordinator.dutyState.value is DutyState.CleanupFailed)
        assertEquals("Remote cleanup must NOT run when shutdown times out", 0, fakeLocation.removeCalls.size)
        assertEquals("Remote cleanup must NOT run when shutdown times out", 0, fakeSeats.updateCalls.size)

        // Attempt 2: Durable retry from notification or dashboard
        val success2 = coordinator.goOffDuty(shutdownTimeoutMillis = 1000L)
        assertTrue("Attempt 2 retry must succeed", success2)
        assertTrue("State transitions to OffDuty", coordinator.dutyState.value is DutyState.OffDuty)
        assertEquals("RTDB location removed on retry", 1, fakeLocation.removeCalls.size)
        assertEquals("Firestore sentinel updated on retry", 1, fakeSeats.updateCalls.size)
    }

    @Test
    fun `DriverDutyCoordinator goOnDuty partial startup cleanup resets bridge and prevents orphaned service`() = runBlocking {
        val shuttleId = "101"
        val fakeAuth = FakeAuthRepo(AppUser(uid = "d1", email = "driver@campus.edu", displayName = "Driver Dave", role = UserRole.DRIVER, shuttleId = shuttleId))
        val fakeBridge = DriverServiceBridge()

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = fakeBridge,
            shuttleLocationRepository = FakeLocationRepo(),
            seatAvailabilityRepository = FakeSeatRepo(),
        )

        // Simulate service failing startup asynchronously (e.g. permission denied or FGS promotion failure)
        val goOnDutyJob = async {
            coordinator.goOnDuty()
        }

        // Wait until bridge has prepared start
        delay(50L)
        // Complete start with failure
        fakeBridge.notifyStartComplete(1L, ServiceStartResult.Failure("Location permission revoked"))

        val success = goOnDutyJob.await()
        assertFalse("goOnDuty must return false on partial startup failure", success)
        assertTrue(coordinator.dutyState.value is DutyState.Error)
        assertEquals("Location permission revoked", (coordinator.dutyState.value as DutyState.Error).message)

        // Invariant: bridge must not report running service
        assertFalse("Bridge must not report service running after failed startup", fakeBridge.isServiceRunning.value)

        // Late acknowledgement arrives
        fakeBridge.notifyStartComplete(1L, ServiceStartResult.Success)
        assertFalse("Late ack must not resurrect service running flag", fakeBridge.isServiceRunning.value)
    }

    @Test
    fun `DriverDutyCoordinator goOffDuty shutdown failure followed by successful retry`() = runBlocking {
        val shuttleId = "101"
        val fakeAuth = FakeAuthRepo(AppUser(uid = "d1", email = "driver@campus.edu", displayName = "Driver Dave", role = UserRole.DRIVER, shuttleId = shuttleId))
        val fakeBridge = DriverServiceBridge()

        var shutdownCount = 0
        fakeBridge.registerService { reqId ->
            shutdownCount++
            launch {
                if (shutdownCount == 1) {
                    // First attempt: reports explicit failure
                    fakeBridge.notifyShutdownComplete(ServiceShutdownResult.Failure("FusedLocationClient error"), reqId)
                } else {
                    // Retry attempt: succeeds
                    fakeBridge.notifyShutdownComplete(ServiceShutdownResult.Success, reqId)
                }
            }
        }

        val fakeLocation = FakeLocationRepo()
        val fakeSeats = FakeSeatRepo(
            initialSeats = listOf(
                SeatAvailability(shuttleId = shuttleId, availableSeats = 5, totalSeats = 15, updatedAtMillis = 5000L)
            )
        )

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = fakeBridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
        )

        // Attempt 1: Shutdown fails
        val success1 = coordinator.goOffDuty()
        assertFalse("First attempt fails when local shutdown fails", success1)
        assertTrue(coordinator.dutyState.value is DutyState.CleanupFailed)
        assertEquals("No remote cleanup when shutdown fails", 0, fakeLocation.removeCalls.size)
        assertEquals("No remote cleanup when shutdown fails", 0, fakeSeats.updateCalls.size)
        assertTrue("Bridge preserves registered state so retry knows service was not stopped", fakeBridge.isServiceRunning.value)

        // Attempt 2: Retry succeeds
        val success2 = coordinator.goOffDuty()
        assertTrue("Retry succeeds", success2)
        assertTrue(coordinator.dutyState.value is DutyState.OffDuty)
        assertEquals("RTDB location removed on retry", 1, fakeLocation.removeCalls.size)
        assertEquals("Firestore sentinel updated on retry", 1, fakeSeats.updateCalls.size)
        assertFalse("Bridge isServiceRunning is false after successful stop", fakeBridge.isServiceRunning.value)
    }

    // ── Persistent Off-Duty Storage & Recovery across Process Death ──

    @Test
    fun `DriverDutyCoordinator persists pending cleanup state and restores across simulated process death`() = runBlocking {
        val shuttleId = "shuttle_storage_1"
        val driverUid = "driver_storage_uid"
        val fakeAuth = FakeAuthRepo(AppUser(uid = driverUid, email = "driver@campus.edu", displayName = "Driver Dave", role = UserRole.DRIVER, shuttleId = shuttleId))
        val fakeBridge = DriverServiceBridge()
        fakeBridge.registerService { reqId ->
            launch { fakeBridge.notifyShutdownComplete(ServiceShutdownResult.Success, reqId) }
        }

        val fakeLocation = FakeLocationRepo()
        val fakeSeats = FakeSeatRepo()
        fakeSeats.shouldFailUpdate = true // Firestore fails, RTDB succeeds

        val persistentStorage = InMemoryDriverDutyStorage()

        val coordinatorSession1 = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = fakeBridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
            storageOverride = persistentStorage,
        )

        val successSession1 = coordinatorSession1.goOffDuty()
        assertFalse("Session 1 off-duty must fail because Firestore failed", successSession1)
        assertTrue(coordinatorSession1.dutyState.value is DutyState.CleanupFailed)

        // Verify storage persisted the failed state before process death
        assertEquals("Persisted UID must match", driverUid, persistentStorage.getPendingUid())
        assertEquals("Persisted shuttleId must match", shuttleId, persistentStorage.getPendingShuttleId())
        assertFalse("RTDB succeeded, so rtdb flag must be cleared", persistentStorage.getPendingRtdb())
        assertTrue("Firestore failed, so firestore flag must remain true", persistentStorage.getPendingFirestore())

        // SIMULATE PROCESS DEATH: create brand new Coordinator instance with the same persistent storage
        val fakeBridge2 = DriverServiceBridge()
        val coordinatorSession2 = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = fakeBridge2,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
            storageOverride = persistentStorage,
        )

        // EMPIRICAL PROOF: New coordinator immediately restores CleanupFailed on launch!
        val restoredState = coordinatorSession2.dutyState.value
        assertTrue("Restored state must be CleanupFailed", restoredState is DutyState.CleanupFailed)
        val cleanupRestored = restoredState as DutyState.CleanupFailed
        assertFalse("Restored RTDB must be false (succeeded in session 1)", cleanupRestored.rtdbFailed)
        assertTrue("Restored Firestore must be true (failed in session 1)", cleanupRestored.firestoreFailed)

        // RETRY IN SESSION 2: fix Firestore and trigger retry
        fakeSeats.shouldFailUpdate = false
        fakeSeats.seatsFlow.value = listOf(
            SeatAvailability(shuttleId = shuttleId, availableSeats = 15, totalSeats = 30, updatedAtMillis = 1000L)
        )

        val retrySuccess = coordinatorSession2.goOffDuty()
        assertTrue("Retry must succeed when Firestore is available", retrySuccess)
        assertTrue("Final state must be OffDuty", coordinatorSession2.dutyState.value is DutyState.OffDuty)

        // Storage must be completely cleared after full success
        assertNull("Storage UID must be cleared", persistentStorage.getPendingUid())
        assertNull("Storage shuttleId must be cleared", persistentStorage.getPendingShuttleId())
        assertFalse("Storage rtdb flag must be false", persistentStorage.getPendingRtdb())
        assertFalse("Storage firestore flag must be false", persistentStorage.getPendingFirestore())
    }

    @Test
    fun `DriverDutyCoordinator rejects recovery retry if current authenticated user does not match stored pending session`() = runBlocking {
        val storedShuttleId = "shuttle_auth_check"
        val originalDriverUid = "driver_original"
        val differentDriverUid = "driver_impostor"

        val persistentStorage = InMemoryDriverDutyStorage().apply {
            recordPending(uid = originalDriverUid, shuttleId = storedShuttleId, rtdb = true, firestore = true)
        }

        // Authenticated user is a different driver!
        val fakeAuthDifferent = FakeAuthRepo(AppUser(uid = differentDriverUid, email = "other@campus.edu", displayName = "Different Driver", role = UserRole.DRIVER, shuttleId = storedShuttleId))
        val fakeBridge = DriverServiceBridge()

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuthDifferent,
            bridge = fakeBridge,
            shuttleLocationRepository = FakeLocationRepo(),
            seatAvailabilityRepository = FakeSeatRepo(),
            storageOverride = persistentStorage,
        )

        val success = coordinator.goOffDuty()
        assertFalse("Recovery must be rejected for mismatched user", success)
        assertTrue("State must transition to DutyState.Error", coordinator.dutyState.value is DutyState.Error)
        val errorState = coordinator.dutyState.value as DutyState.Error
        assertTrue("Error message must explain authentication mismatch", errorState.message.contains("Authenticated driver does not match pending cleanup session"))
    }

    // ── 8. Requirement R2/R3: Driver Fixture Stop & Fail-Closed Safety ──

    @Test
    fun `DriverDutyCoordinator in fixture mode makes zero AuthRepository, RTDB, and Firestore calls during shutdown`() = runBlocking {
        val fakeAuth = FakeAuthRepo(AppUser(uid = "driver_1", email = "driver@campus.edu", displayName = "Driver One", role = UserRole.DRIVER, shuttleId = "shuttle_1"))
        val fakeBridge = DriverServiceBridge()
        val fakeLocation = FakeLocationRepo()
        val fakeSeats = FakeSeatRepo()

        val unrelatedPendingStorage = InMemoryDriverDutyStorage().apply {
            recordPending(uid = "unrelated_driver", shuttleId = "unrelated_shuttle", rtdb = true, firestore = true)
        }

        // Register bridge in test fixture mode
        fakeBridge.registerService(isFixture = true) { reqId ->
            runBlocking { fakeBridge.notifyShutdownComplete(ServiceShutdownResult.Success, reqId) }
        }
        assertTrue("Bridge must be running", fakeBridge.isServiceRunning.value)
        assertTrue("Bridge must be in fixture mode", fakeBridge.isFixtureMode.value)

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = fakeBridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
            storageOverride = unrelatedPendingStorage,
        )

        val success = coordinator.goOffDuty(isFixtureAction = true)
        assertTrue("Confirmed fixture stop must succeed", success)
        assertTrue("State must transition to DutyState.OffDuty", coordinator.dutyState.value is DutyState.OffDuty)
        assertFalse("Bridge service must no longer be running", fakeBridge.isServiceRunning.value)
        assertFalse("Bridge fixture mode must be reset", fakeBridge.isFixtureMode.value)

        // Strict Invariants: ZERO remote network calls or Auth calls during fixture shutdown
        assertEquals("AuthRepository.getCurrentUser() must NEVER be called during fixture stop", 0, fakeAuth.getCurrentUserCalls)
        assertEquals("RTDB removeDriverLocation() must NEVER be called in fixture mode", 0, fakeLocation.removeCalls.size)
        assertEquals("Firestore updateSeatAvailability() must NEVER be called in fixture mode", 0, fakeSeats.updateCalls.size)

        // Existing unrelated pending recovery state must be completely untouched
        assertEquals("Pending UID must remain untouched", "unrelated_driver", unrelatedPendingStorage.getPendingUid())
        assertEquals("Pending shuttleId must remain untouched", "unrelated_shuttle", unrelatedPendingStorage.getPendingShuttleId())
        assertTrue("Pending RTDB flag must remain untouched", unrelatedPendingStorage.getPendingRtdb())
        assertTrue("Pending Firestore flag must remain untouched", unrelatedPendingStorage.getPendingFirestore())
    }

    @Test
    fun `DriverDutyCoordinator inconsistent fixture state fails safely without triggering production remote cleanup`() = runBlocking {
        val fakeAuth = FakeAuthRepo(AppUser(uid = "driver_1", email = "driver@campus.edu", displayName = "Driver One", role = UserRole.DRIVER, shuttleId = "shuttle_1"))
        val fakeBridge = DriverServiceBridge()
        val fakeLocation = FakeLocationRepo()
        val fakeSeats = FakeSeatRepo()

        val pendingStorage = InMemoryDriverDutyStorage().apply {
            recordPending(uid = "saved_driver", shuttleId = "saved_shuttle", rtdb = true, firestore = true)
        }

        // Mismatch Case A: Bridge registered as production (isFixture = false), but stop action asserts fixture
        fakeBridge.registerService(isFixture = false) { reqId ->
            runBlocking { fakeBridge.notifyShutdownComplete(ServiceShutdownResult.Success, reqId) }
        }
        assertFalse("Bridge is NOT fixture", fakeBridge.isFixtureMode.value)
        assertTrue("Bridge is running", fakeBridge.isServiceRunning.value)

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = fakeBridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
            storageOverride = pendingStorage,
        )

        val successA = coordinator.goOffDuty(isFixtureAction = true)
        assertFalse("Fixture mismatch (action=true, bridge=false) must fail closed", successA)
        assertTrue("State must transition to DutyState.Error on mismatch", coordinator.dutyState.value is DutyState.Error)
        val errorA = coordinator.dutyState.value as DutyState.Error
        assertTrue(errorA.message.contains("Debug fixture state mismatch"))

        // Local service was stopped, but zero remote operations occurred
        assertEquals("AuthRepository.getCurrentUser() must not be called", 0, fakeAuth.getCurrentUserCalls)
        assertEquals("RTDB must not be touched on mismatch", 0, fakeLocation.removeCalls.size)
        assertEquals("Firestore must not be touched on mismatch", 0, fakeSeats.updateCalls.size)
        assertEquals("saved_driver", pendingStorage.getPendingUid())
        assertTrue(pendingStorage.getPendingRtdb())

        // Mismatch Case B: Bridge registered as fixture (isFixture = true), but stop action does NOT declare fixture
        fakeBridge.registerService(isFixture = true) { reqId ->
            runBlocking { fakeBridge.notifyShutdownComplete(ServiceShutdownResult.Success, reqId) }
        }
        assertTrue("Bridge is fixture", fakeBridge.isFixtureMode.value)
        assertTrue("Bridge is running", fakeBridge.isServiceRunning.value)

        val coordinatorB = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = fakeBridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
            storageOverride = pendingStorage,
        )

        val successB = coordinatorB.goOffDuty(isFixtureAction = false)
        assertFalse("Fixture mismatch (action=false, bridge=true) must fail closed", successB)
        assertTrue("State must transition to DutyState.Error on mismatch", coordinatorB.dutyState.value is DutyState.Error)
        assertEquals("RTDB must not be touched on mismatch", 0, fakeLocation.removeCalls.size)
        assertEquals("Firestore must not be touched on mismatch", 0, fakeSeats.updateCalls.size)
    }

    @Test
    fun `DriverDutyCoordinator production cleanup behavior is completely unchanged in normal production off-duty`() = runBlocking {
        val fakeAuth = FakeAuthRepo(AppUser(uid = "prod_driver", email = "driver@campus.edu", displayName = "Prod Driver", role = UserRole.DRIVER, shuttleId = "shuttle_prod"))
        val fakeBridge = DriverServiceBridge()
        val fakeLocation = FakeLocationRepo()
        val fakeSeats = FakeSeatRepo(listOf(SeatAvailability("shuttle_prod", 14, 28, 1000L)))
        val storage = InMemoryDriverDutyStorage()

        // Normal production registration
        fakeBridge.registerService(isFixture = false) { reqId ->
            runBlocking { fakeBridge.notifyShutdownComplete(ServiceShutdownResult.Success, reqId) }
        }
        assertTrue("Bridge is running", fakeBridge.isServiceRunning.value)
        assertFalse("Bridge is NOT fixture", fakeBridge.isFixtureMode.value)

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = fakeBridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
            storageOverride = storage,
        )

        val success = coordinator.goOffDuty(isFixtureAction = false)
        assertTrue("Normal production off-duty must succeed", success)
        assertTrue("State must transition to DutyState.OffDuty", coordinator.dutyState.value is DutyState.OffDuty)

        // Verify full production cleanup executed
        assertNotEquals("AuthRepository.getCurrentUser() must be called in production off-duty", 0, fakeAuth.getCurrentUserCalls)
        assertEquals("RTDB location must be removed in production off-duty", 1, fakeLocation.removeCalls.size)
        assertEquals("RTDB target shuttle must match", "shuttle_prod", fakeLocation.removeCalls.first())
        assertEquals("Firestore seat availability must be updated in production off-duty", 1, fakeSeats.updateCalls.size)
        val updateCall = fakeSeats.updateCalls.first()
        assertEquals("shuttle_prod", updateCall.shuttleId)
        assertEquals(14, updateCall.availableSeats)
        assertEquals(28, updateCall.totalSeats)
        assertEquals(0L, updateCall.updatedAtMillis) // sentinel
        assertNull("Storage must be cleared after successful cleanup", storage.getPendingUid())
    }

    @Test
    fun `DriverDutyCoordinator failed local shutdown does not report OffDuty`() = runBlocking {
        val fakeAuth = FakeAuthRepo(AppUser(uid = "driver_1", email = "driver@campus.edu", displayName = "Driver One", role = UserRole.DRIVER, shuttleId = "shuttle_1"))
        val fakeBridge = DriverServiceBridge()
        val fakeLocation = FakeLocationRepo()
        val fakeSeats = FakeSeatRepo()

        // Bridge registered as fixture, but shutdown fails
        fakeBridge.registerService(isFixture = true) { reqId ->
            runBlocking { fakeBridge.notifyShutdownComplete(ServiceShutdownResult.Failure("Teardown failed"), reqId) }
        }

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = fakeBridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
        )

        val success = coordinator.goOffDuty(isFixtureAction = true)
        assertFalse("Failed local shutdown must return false", success)
        assertFalse("State must NOT be OffDuty", coordinator.dutyState.value is DutyState.OffDuty)
        assertTrue("State must transition to CleanupFailed", coordinator.dutyState.value is DutyState.CleanupFailed)
        assertEquals("RTDB must not be called on failed local shutdown", 0, fakeLocation.removeCalls.size)
        assertEquals("Firestore must not be called on failed local shutdown", 0, fakeSeats.updateCalls.size)
    }

    @Test
    fun `DriverServiceBridge fixture state does not leak into subsequent normal duty sessions`() = runBlocking {
        val fakeAuth = FakeAuthRepo(AppUser(uid = "driver_normal", email = "driver@campus.edu", displayName = "Normal Driver", role = UserRole.DRIVER, shuttleId = "shuttle_normal"))
        val fakeBridge = DriverServiceBridge()
        val fakeLocation = FakeLocationRepo()
        val fakeSeats = FakeSeatRepo(listOf(SeatAvailability("shuttle_normal", 10, 20, 1000L)))

        // Session 1: Fixture service
        fakeBridge.registerService(isFixture = true) { reqId ->
            runBlocking { fakeBridge.notifyShutdownComplete(ServiceShutdownResult.Success, reqId) }
        }
        assertTrue("Session 1 must be fixture", fakeBridge.isFixtureMode.value)
        fakeBridge.forceReset()
        assertFalse("forceReset must clear fixture state", fakeBridge.isFixtureMode.value)

        // Session 2: Normal production service
        fakeBridge.registerService(isFixture = false) { reqId ->
            runBlocking { fakeBridge.notifyShutdownComplete(ServiceShutdownResult.Success, reqId) }
        }
        assertFalse("Session 2 must be production", fakeBridge.isFixtureMode.value)

        val coordinator = DriverDutyCoordinator(
            application = Application(),
            authRepository = fakeAuth,
            bridge = fakeBridge,
            shuttleLocationRepository = fakeLocation,
            seatAvailabilityRepository = fakeSeats,
        )

        val success = coordinator.goOffDuty(isFixtureAction = false)
        assertTrue("Production stop must succeed", success)
        assertTrue("State must be OffDuty", coordinator.dutyState.value is DutyState.OffDuty)

        // In normal production mode, remote cleanup MUST execute
        assertEquals("Production RTDB removal must execute", 1, fakeLocation.removeCalls.size)
        assertEquals("Production RTDB target must match", "shuttle_normal", fakeLocation.removeCalls.first())
        assertEquals("Production Firestore update must execute", 1, fakeSeats.updateCalls.size)
    }
}

// ── Lightweight Fakes for Isolated Deterministic Testing ──

private class FakeAuthRepo(private var user: AppUser?) : AuthRepository {
    var getCurrentUserCalls = 0
    override val authState: Flow<AuthState> = flowOf(user?.let { AuthState.SignedIn(it) } ?: AuthState.SignedOut)
    override suspend fun getCurrentUser(): AppUser? {
        getCurrentUserCalls++
        return user
    }
    override suspend fun signInWithGoogle(idToken: String, role: UserRole): Result<AppUser> = Result.success(user!!)
    override suspend fun reloadProfile(): Result<AppUser> = Result.success(user!!)
    override suspend fun logout() { user = null }
    override suspend fun refreshIdToken(): String? = "fake_token"
}

private class FakeLocationRepo : ShuttleLocationRepository {
    val removeCalls = mutableListOf<String>()
    var shouldFailRemove = false

    override fun observeShuttleLocations(): Flow<List<ShuttleLocation>> = flowOf(emptyList())
    override suspend fun updateDriverLocation(shuttleId: String, latitude: Double, longitude: Double, heading: Float, updatedAtMillis: Long) {}
    override suspend fun removeDriverLocation(shuttleId: String) {
        if (shouldFailRemove) throw RuntimeException("RTDB removal failed")
        removeCalls.add(shuttleId)
    }
}

private data class SeatUpdateCall(val shuttleId: String, val availableSeats: Int, val totalSeats: Int, val updatedAtMillis: Long)

private class FakeSeatRepo(initialSeats: List<SeatAvailability> = emptyList()) : SeatAvailabilityRepository {
    val seatsFlow = MutableStateFlow(initialSeats)
    val updateCalls = mutableListOf<SeatUpdateCall>()
    var shouldThrowOnObserve = false
    var shouldFailUpdate = false
    var shouldHangOnObserve = false

    override fun observeSeatAvailability(): Flow<List<SeatAvailability>> = flow {
        if (shouldThrowOnObserve) throw RuntimeException("Firestore stream offline")
        if (shouldHangOnObserve) {
            delay(10_000L) // Hangs longer than coordinator's 3_000L timeout
        }
        seatsFlow.collect { emit(it) }
    }

    override suspend fun updateSeatAvailability(shuttleId: String, availableSeats: Int, totalSeats: Int, updatedAtMillis: Long) {
        if (shouldFailUpdate) throw RuntimeException("Firestore update permission denied")
        updateCalls.add(SeatUpdateCall(shuttleId, availableSeats, totalSeats, updatedAtMillis))
    }
}
