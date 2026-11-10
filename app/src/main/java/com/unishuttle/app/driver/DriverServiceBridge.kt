package com.unishuttle.app.driver

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ServiceStartResult {
    object Success : ServiceStartResult
    data class Failure(val reason: String, val throwable: Throwable? = null) : ServiceStartResult
}

sealed interface ServiceShutdownResult {
    object Success : ServiceShutdownResult
    data class Failure(val reason: String, val throwable: Throwable? = null) : ServiceShutdownResult
    object TimedOut : ServiceShutdownResult
}

@Singleton
class DriverServiceBridge @Inject constructor() {
    private val mutex = Mutex()
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _isFixtureMode = MutableStateFlow(false)
    val isFixtureMode: StateFlow<Boolean> = _isFixtureMode.asStateFlow()
    val isTestFixture: Boolean get() = _isFixtureMode.value

    private var activeStartRequestId: Long = 0L
    private var activeShutdownRequestId: Long = 0L
    private var startDeferred: CompletableDeferred<ServiceStartResult>? = null
    private var shutdownDeferred: CompletableDeferred<ServiceShutdownResult>? = null
    private var stopCallback: ((Long) -> Unit)? = null

    suspend fun registerService(isFixture: Boolean = false, stopTrigger: (Long) -> Unit) = mutex.withLock {
        _isServiceRunning.value = true
        _isFixtureMode.value = isFixture
        stopCallback = stopTrigger
    }

    suspend fun unregisterService() = mutex.withLock {
        forceReset()
    }

    suspend fun reset() = mutex.withLock {
        forceReset()
    }

    fun forceReset() {
        _isServiceRunning.value = false
        _isFixtureMode.value = false
        stopCallback = null
        startDeferred?.cancel()
        startDeferred = null
        shutdownDeferred?.cancel()
        shutdownDeferred = null
    }

    // ── Start Handshake ──

    suspend fun prepareStart(): Pair<Long, CompletableDeferred<ServiceStartResult>> = mutex.withLock {
        activeStartRequestId++
        val deferred = CompletableDeferred<ServiceStartResult>()
        startDeferred = deferred
        Pair(activeStartRequestId, deferred)
    }

    suspend fun cancelStart(requestId: Long, reason: String = "Startup cancelled") = mutex.withLock {
        if (requestId == activeStartRequestId) {
            startDeferred?.complete(ServiceStartResult.Failure(reason))
            startDeferred = null
            _isServiceRunning.value = false
            _isFixtureMode.value = false
            stopCallback = null
        }
    }

    suspend fun notifyStartComplete(requestId: Long, result: ServiceStartResult) = mutex.withLock {
        if (requestId != activeStartRequestId) {
            // Late acknowledgement from a timed-out attempt; ignore
            return@withLock
        }
        val deferred = startDeferred
        if (deferred == null || deferred.isCompleted) {
            // Start was already cancelled or completed; ignore late arrival
            return@withLock
        }
        deferred.complete(result)
        startDeferred = null
        if (result is ServiceStartResult.Failure) {
            _isServiceRunning.value = false
            _isFixtureMode.value = false
            stopCallback = null
        }
    }

    // ── Shutdown Handshake ──

    suspend fun requestShutdown(timeoutMillis: Long = 10_000L): ServiceShutdownResult {
        var callbackToInvoke: ((Long) -> Unit)? = null
        var reqId = 0L
        val deferred = mutex.withLock {
            if (!_isServiceRunning.value) return ServiceShutdownResult.Success
            val existing = shutdownDeferred
            if (existing != null && !existing.isCompleted) {
                return@withLock existing
            }
            val callback = stopCallback ?: return ServiceShutdownResult.Failure(
                "Service registered as running but stopCallback is missing."
            )
            activeShutdownRequestId++
            reqId = activeShutdownRequestId
            val newDeferred = CompletableDeferred<ServiceShutdownResult>()
            shutdownDeferred = newDeferred
            callbackToInvoke = callback
            newDeferred
        }

        callbackToInvoke?.invoke(reqId)

        val result = withTimeoutOrNull(timeoutMillis) { deferred.await() }
            ?: ServiceShutdownResult.TimedOut

        mutex.withLock {
            if (shutdownDeferred == deferred) {
                shutdownDeferred = null
            }
            // CRITICAL: Unregister ONLY on confirmed success. Do not unregister on Failure or Timeout!
            if (result is ServiceShutdownResult.Success) {
                _isServiceRunning.value = false
                _isFixtureMode.value = false
                stopCallback = null
            }
        }
        return result
    }

    suspend fun notifyShutdownComplete(result: ServiceShutdownResult, requestId: Long) = mutex.withLock {
        if (requestId != activeShutdownRequestId) {
            // Late or mismatched shutdown response; ignore
            return@withLock
        }
        shutdownDeferred?.complete(result)
        shutdownDeferred = null
        if (result is ServiceShutdownResult.Success) {
            _isServiceRunning.value = false
            _isFixtureMode.value = false
            stopCallback = null
        }
    }
}
