package com.unishuttle.app.driver

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.ContextCompat
import com.unishuttle.app.BuildConfig
import com.unishuttle.app.domain.repository.AuthRepository
import com.unishuttle.app.domain.repository.SeatAvailabilityRepository
import com.unishuttle.app.domain.repository.ShuttleLocationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DutyState {
    object OffDuty : DutyState
    object Starting : DutyState
    object OnDuty : DutyState
    object Stopping : DutyState
    data class CleanupFailed(
        val rtdbFailed: Boolean,
        val firestoreFailed: Boolean,
        val message: String,
    ) : DutyState
    data class Error(val message: String) : DutyState
}

interface DriverDutyStorage {
    fun getPendingUid(): String?
    fun getPendingShuttleId(): String?
    fun getPendingRtdb(): Boolean
    fun getPendingFirestore(): Boolean
    fun recordPending(uid: String, shuttleId: String, rtdb: Boolean, firestore: Boolean)
    fun updateFlags(rtdb: Boolean, firestore: Boolean)
    fun clear()
}

class SharedPrefsDriverDutyStorage(private val application: Application) : DriverDutyStorage {
    private val prefs: SharedPreferences? by lazy {
        runCatching { application.getSharedPreferences(DriverDutyCoordinator.PREFS_NAME, Context.MODE_PRIVATE) }.getOrNull()
    }

    override fun getPendingUid(): String? = prefs?.getString(DriverDutyCoordinator.KEY_PENDING_UID, null)
    override fun getPendingShuttleId(): String? = prefs?.getString(DriverDutyCoordinator.KEY_PENDING_SHUTTLE_ID, null)
    override fun getPendingRtdb(): Boolean = prefs?.getBoolean(DriverDutyCoordinator.KEY_PENDING_RTDB, false) ?: false
    override fun getPendingFirestore(): Boolean = prefs?.getBoolean(DriverDutyCoordinator.KEY_PENDING_FIRESTORE, false) ?: false

    override fun recordPending(uid: String, shuttleId: String, rtdb: Boolean, firestore: Boolean) {
        prefs?.edit()
            ?.putString(DriverDutyCoordinator.KEY_PENDING_UID, uid)
            ?.putString(DriverDutyCoordinator.KEY_PENDING_SHUTTLE_ID, shuttleId)
            ?.putBoolean(DriverDutyCoordinator.KEY_PENDING_RTDB, rtdb)
            ?.putBoolean(DriverDutyCoordinator.KEY_PENDING_FIRESTORE, firestore)
            ?.putLong(DriverDutyCoordinator.KEY_PENDING_TIMESTAMP, System.currentTimeMillis())
            ?.commit()
    }

    override fun updateFlags(rtdb: Boolean, firestore: Boolean) {
        prefs?.edit()
            ?.putBoolean(DriverDutyCoordinator.KEY_PENDING_RTDB, rtdb)
            ?.putBoolean(DriverDutyCoordinator.KEY_PENDING_FIRESTORE, firestore)
            ?.commit()
    }

    override fun clear() {
        prefs?.edit()?.clear()?.commit()
    }
}

class InMemoryDriverDutyStorage : DriverDutyStorage {
    private var uid: String? = null
    private var shuttleId: String? = null
    private var rtdb: Boolean = false
    private var firestore: Boolean = false
    private var timestamp: Long = 0L

    override fun getPendingUid(): String? = uid
    override fun getPendingShuttleId(): String? = shuttleId
    override fun getPendingRtdb(): Boolean = rtdb
    override fun getPendingFirestore(): Boolean = firestore

    override fun recordPending(uid: String, shuttleId: String, rtdb: Boolean, firestore: Boolean) {
        this.uid = uid
        this.shuttleId = shuttleId
        this.rtdb = rtdb
        this.firestore = firestore
        this.timestamp = System.currentTimeMillis()
    }

    override fun updateFlags(rtdb: Boolean, firestore: Boolean) {
        this.rtdb = rtdb
        this.firestore = firestore
    }

    override fun clear() {
        uid = null
        shuttleId = null
        rtdb = false
        firestore = false
        timestamp = 0L
    }
}

/**
 * DriverDutyCoordinator owns the state machine for driver duty lifecycle.
 *
 * Persisted Off-Duty Recovery Model:
 * Pending off-duty cleanup state is written reliably to SharedPreferences before remote
 * operations commence on an IO dispatcher. If the application process dies during cleanup,
 * the pending cleanup record persists. On the next app launch, the coordinator restores
 * [DutyState.CleanupFailed], enabling user-triggered retry from the Driver Cockpit UI.
 * Headless background workers (WorkManager) are intentionally omitted in favor of explicit,
 * deterministic user-authorized recovery when the driver resumes their session.
 */
@Singleton
class DriverDutyCoordinator(
    private val application: Application,
    private val authRepository: AuthRepository,
    private val bridge: DriverServiceBridge,
    private val shuttleLocationRepository: ShuttleLocationRepository,
    private val seatAvailabilityRepository: SeatAvailabilityRepository,
    storageOverride: DriverDutyStorage? = null,
) {
    @Inject
    constructor(
        application: Application,
        authRepository: AuthRepository,
        bridge: DriverServiceBridge,
        shuttleLocationRepository: ShuttleLocationRepository,
        seatAvailabilityRepository: SeatAvailabilityRepository,
    ) : this(
        application = application,
        authRepository = authRepository,
        bridge = bridge,
        shuttleLocationRepository = shuttleLocationRepository,
        seatAvailabilityRepository = seatAvailabilityRepository,
        storageOverride = null,
    )

    private val storage: DriverDutyStorage = storageOverride ?: SharedPrefsDriverDutyStorage(application)

    private val dutyMutex = Mutex()
    private val stopMutex = Mutex()
    private var activeStopDeferred: CompletableDeferred<Boolean>? = null

    private val _dutyState = MutableStateFlow<DutyState>(DutyState.OffDuty)
    val dutyState: StateFlow<DutyState> = _dutyState.asStateFlow()

    init {
        // Restore pending cleanup state across process death on next app launch
        val storedUid = storage.getPendingUid()
        val storedShuttleId = storage.getPendingShuttleId()
        val pendingRtdb = storage.getPendingRtdb()
        val pendingFirestore = storage.getPendingFirestore()

        if (!storedUid.isNullOrBlank() && !storedShuttleId.isNullOrBlank() && (pendingRtdb || pendingFirestore)) {
            Log.w(
                TAG,
                "Restoring persisted off-duty cleanup state across process death: shuttle=$storedShuttleId, rtdb=$pendingRtdb, firestore=$pendingFirestore",
            )
            _dutyState.value = DutyState.CleanupFailed(
                rtdbFailed = pendingRtdb,
                firestoreFailed = pendingFirestore,
                message = "Pending off-duty cleanup detected from previous session for shuttle $storedShuttleId. Tap to retry.",
            )
        }
    }

    suspend fun goOnDuty(): Boolean = dutyMutex.withLock {
        if (_dutyState.value is DutyState.OnDuty) return true
        val driver = authRepository.getCurrentUser()
        val shuttleId = driver?.shuttleId?.trim().orEmpty()

        if (shuttleId.isBlank()) {
            _dutyState.value = DutyState.Error("No valid shuttle ID assigned to driver.")
            return false
        }

        _dutyState.value = DutyState.Starting
        val (requestId, startDeferred) = bridge.prepareStart()

        try {
            val intent = DriverLocationService.startIntent(application, shuttleId, requestId)
            ContextCompat.startForegroundService(application, intent)
        } catch (t: Throwable) {
            bridge.notifyStartComplete(requestId, ServiceStartResult.Failure("FGS start failed", t))
            _dutyState.value = DutyState.Error("Could not start foreground service: ${t.localizedMessage}")
            return false
        }

        val result = withTimeoutOrNull(5_000L) { startDeferred.await() }
            ?: ServiceStartResult.Failure("Service startup handshake timed out.")

        return when (result) {
            is ServiceStartResult.Success -> {
                _dutyState.value = DutyState.OnDuty
                true
            }
            is ServiceStartResult.Failure -> {
                // Invalidate start request in bridge so late acks are rejected
                bridge.cancelStart(requestId, result.reason)
                // Stop partially initialized service
                application.stopService(Intent(application, DriverLocationService::class.java))
                _dutyState.value = DutyState.Error(result.reason)
                false
            }
        }
    }

    suspend fun goOffDuty(
        shutdownTimeoutMillis: Long = 10_000L,
        retryRtdb: Boolean? = null,
        retryFirestore: Boolean? = null,
        isFixtureAction: Boolean = false,
        isExplicitFixtureStop: Boolean = isFixtureAction,
    ): Boolean {
        val actualFixtureAction = if (isFixtureAction) true else isExplicitFixtureStop
        val hasPersistedPending = hasPendingCleanup()
        val deferredToAwait: CompletableDeferred<Boolean>? = stopMutex.withLock {
            if (_dutyState.value is DutyState.OffDuty && !bridge.isServiceRunning.value && !hasPersistedPending) {
                return true
            }
            val existing = activeStopDeferred
            if (existing != null && !existing.isCompleted) {
                return@withLock existing
            }
            val newDeferred = CompletableDeferred<Boolean>()
            activeStopDeferred = newDeferred
            null
        }

        if (deferredToAwait != null) {
            return deferredToAwait.await()
        }

        var result = false
        try {
            result = dutyMutex.withLock {
                goOffDutyInternal(shutdownTimeoutMillis, retryRtdb, retryFirestore, actualFixtureAction)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Uncaught off-duty coordination failure", t)
            _dutyState.value = DutyState.CleanupFailed(
                rtdbFailed = true,
                firestoreFailed = true,
                message = "Off-duty operation failed: ${t.localizedMessage}",
            )
            result = false
        } finally {
            stopMutex.withLock {
                activeStopDeferred?.complete(result)
                activeStopDeferred = null
            }
        }
        return result
    }

    private suspend fun goOffDutyInternal(
        shutdownTimeoutMillis: Long,
        explicitRetryRtdb: Boolean?,
        explicitRetryFirestore: Boolean?,
        isFixtureAction: Boolean = false,
    ): Boolean {
        // Snapshot fixture mode before requesting shutdown
        val isBridgeRunning = bridge.isServiceRunning.value
        val isBridgeFixture = bridge.isFixtureMode.value && isBridgeRunning
        val isFixtureValid = BuildConfig.DEBUG && isFixtureAction && isBridgeFixture
        val isFixtureMismatch = (isFixtureAction && !isFixtureValid) || (!isFixtureAction && isBridgeFixture)

        val hasPersisted = hasPendingCleanup()
        if (_dutyState.value is DutyState.OffDuty && !isBridgeRunning && !hasPersisted) {
            return true
        }

        // Authorization check: confirm current authenticated user is still authorized for the persisted target (only for normal production sessions)
        if (!isFixtureValid && !isFixtureMismatch) {
            val driver = authRepository.getCurrentUser()
            val driverUid = driver?.uid?.trim().orEmpty()
            val shuttleId = driver?.shuttleId?.trim().orEmpty()

            val storedUid = storage.getPendingUid()
            val storedShuttleId = storage.getPendingShuttleId()
            if (hasPersisted && !storedUid.isNullOrBlank() && !storedShuttleId.isNullOrBlank()) {
                if (driverUid.isBlank() || driverUid != storedUid || shuttleId != storedShuttleId) {
                    Log.w(
                        TAG,
                        "Current driver ($driverUid, $shuttleId) does not match stored pending cleanup ($storedUid, $storedShuttleId); aborting recovery.",
                    )
                    _dutyState.value = DutyState.Error("Authenticated driver does not match pending cleanup session.")
                    return false
                }
            }
        }

        val prevState = _dutyState.value
        _dutyState.value = DutyState.Stopping

        // 1. Await local service shutdown handshake FIRST
        val localShutdown = bridge.requestShutdown(timeoutMillis = shutdownTimeoutMillis)
        if (localShutdown !is ServiceShutdownResult.Success) {
            val reason = when (localShutdown) {
                ServiceShutdownResult.TimedOut -> "Local service shutdown timed out."
                is ServiceShutdownResult.Failure -> localShutdown.reason
                ServiceShutdownResult.Success -> ""
            }
            // CRITICAL: Local tracking not confirmed stopped; ABORT remote deletion to prevent ghost location
            _dutyState.value = DutyState.CleanupFailed(
                rtdbFailed = false,
                firestoreFailed = false,
                message = "Service failed to stop ($reason). Remote cleanup aborted to prevent ghost location.",
            )
            return false
        }

        // Immediately following local shutdown success:
        // 2. Fixture mismatch: fail closed
        if (isFixtureMismatch) {
            Log.w(
                TAG,
                "[TEST_FIXTURE] Fixture mismatch detected (action=$isFixtureAction, bridgeFixture=$isBridgeFixture, valid=$isFixtureValid); local service stopped, but remote cleanup is blocked.",
            )
            _dutyState.value = DutyState.Error("Debug fixture state mismatch; remote cleanup blocked.")
            return false
        }

        // 3. Confirmed valid fixture session: bypass all remote calls
        if (isFixtureValid) {
            Log.i(TAG, "[TEST_FIXTURE] Confirmed debug fixture shutdown; bypassing all remote AuthRepository, RTDB, and Firestore cleanup.")
            // NOTE: Existing persisted production cleanup records are neither created, overwritten, nor cleared.
            _dutyState.value = DutyState.OffDuty
            return true
        }

        // 4. Normal production cleanup flow: executes ONLY when NOT in fixture mode and NOT in fixture mismatch
        val driver = authRepository.getCurrentUser()
        val driverUid = driver?.uid?.trim().orEmpty()
        val shuttleId = driver?.shuttleId?.trim().orEmpty()

        val storedUid = storage.getPendingUid()
        val storedShuttleId = storage.getPendingShuttleId()

        // Determine selective cleanup needs
        val shouldCleanRtdb = explicitRetryRtdb ?: when (prevState) {
            is DutyState.CleanupFailed -> prevState.rtdbFailed || (!prevState.rtdbFailed && !prevState.firestoreFailed)
            else -> true
        }
        val shouldCleanFirestore = explicitRetryFirestore ?: when (prevState) {
            is DutyState.CleanupFailed -> prevState.firestoreFailed || (!prevState.rtdbFailed && !prevState.firestoreFailed)
            else -> true
        }

        val targetShuttleId = if (shuttleId.isNotBlank()) shuttleId else storedShuttleId.orEmpty()
        val targetUid = if (driverUid.isNotBlank()) driverUid else storedUid.orEmpty()

        // Persist pending cleanup state BEFORE starting remote operations (reliable disk write on IO dispatcher)
        if (targetUid.isNotBlank() && targetShuttleId.isNotBlank() && (shouldCleanRtdb || shouldCleanFirestore)) {
            withContext(Dispatchers.IO) {
                storage.recordPending(targetUid, targetShuttleId, shouldCleanRtdb, shouldCleanFirestore)
            }
        }

        // 2. RTDB location deletion executes independently of Firestore seat streams
        var rtdbFailed = false
        if (shouldCleanRtdb && targetShuttleId.isNotBlank()) {
            val rtdbResult = runCatching {
                val deleted = withTimeoutOrNull(5_000L) {
                    shuttleLocationRepository.removeDriverLocation(targetShuttleId)
                    true
                }
                check(deleted == true) { "RTDB deletion timed out" }
            }
            if (rtdbResult.isSuccess) {
                withContext(Dispatchers.IO) {
                    storage.updateFlags(rtdb = false, firestore = shouldCleanFirestore)
                }
            } else {
                rtdbFailed = true
                Log.w(TAG, "RTDB removal failed during off-duty sequence", rtdbResult.exceptionOrNull())
            }
        }

        // 3. Firestore off-duty sentinel update executes independently
        var firestoreFailed = false
        if (shouldCleanFirestore && targetShuttleId.isNotBlank()) {
            val fsResult = runCatching {
                // Fetch authoritative seats with safe timeout and catch
                val activeSeats = withTimeoutOrNull(3_000L) {
                    seatAvailabilityRepository.observeSeatAvailability()
                        .catch { emit(emptyList()) }
                        .firstOrNull()
                        ?.firstOrNull { it.shuttleId == targetShuttleId }
                }

                if (activeSeats != null) {
                    val updated = withTimeoutOrNull(5_000L) {
                        seatAvailabilityRepository.updateSeatAvailability(
                            shuttleId = targetShuttleId,
                            availableSeats = activeSeats.availableSeats,
                            totalSeats = activeSeats.totalSeats,
                            updatedAtMillis = 0L,
                        )
                        true
                    }
                    check(updated == true) { "Firestore update timed out" }
                } else {
                    // NEVER fabricate 24 or invent vehicle capacity.
                    // Fail Firestore cleanup without corrupting vehicle capacity.
                    throw IllegalStateException("Authoritative seat data unavailable; skipped writing fabricated capacity.")
                }
            }
            if (fsResult.isSuccess) {
                withContext(Dispatchers.IO) {
                    storage.updateFlags(rtdb = rtdbFailed, firestore = false)
                }
            } else {
                firestoreFailed = true
                Log.w(TAG, "Firestore seat cleanup failed during off-duty sequence", fsResult.exceptionOrNull())
            }
        }

        if (rtdbFailed || firestoreFailed) {
            val msg = when {
                rtdbFailed && firestoreFailed -> "Remote RTDB and Firestore cleanup failed. Tap to retry."
                rtdbFailed -> "RTDB location removal failed. Tap to retry."
                else -> "Firestore seat availability cleanup failed. Tap to retry."
            }
            _dutyState.value = DutyState.CleanupFailed(
                rtdbFailed = rtdbFailed,
                firestoreFailed = firestoreFailed,
                message = msg,
            )
            return false
        }

        // Clear the record ONLY after all pending operations succeed
        withContext(Dispatchers.IO) {
            storage.clear()
        }
        _dutyState.value = DutyState.OffDuty
        return true
    }

    private fun hasPendingCleanup(): Boolean {
        val storedUid = storage.getPendingUid()
        val storedShuttleId = storage.getPendingShuttleId()
        val pendingRtdb = storage.getPendingRtdb()
        val pendingFirestore = storage.getPendingFirestore()
        return !storedUid.isNullOrBlank() && !storedShuttleId.isNullOrBlank() && (pendingRtdb || pendingFirestore)
    }

    companion object {
        private const val TAG = "DriverDutyCoordinator"
        const val PREFS_NAME = "driver_duty_cleanup_prefs"
        const val KEY_PENDING_UID = "pending_driver_uid"
        const val KEY_PENDING_SHUTTLE_ID = "pending_shuttle_id"
        const val KEY_PENDING_RTDB = "pending_rtdb"
        const val KEY_PENDING_FIRESTORE = "pending_firestore"
        const val KEY_PENDING_TIMESTAMP = "pending_timestamp"
    }
}
