/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

@file:Suppress("PackageNaming")

package com.atrainingtracker.banalservice.devices.bluetooth_le.polar

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.polar.androidcommunications.api.ble.exceptions.BleControlPointCommandError
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdControlPointResponse
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * User-visible status warning emitted when a Polar PMD stream remains blocked by
 * `ERROR_ALREADY_IN_STATE` (e.g., background `fi.polar.polarflow` or stale sensor PMD state)
 * after bounded recovery retries are exhausted (`REQ-CON-053.3`).
 */
data class PolarPmdStreamWarning(
    val deviceId: Long,
    val deviceName: String,
    val polarId: String,
    val measurementType: PmdMeasurementType,
    val retryCount: Int
)

/**
 * Coordinates atomic single-flight stream slots (`HR`, `ACC`, `GYRO`, `PPI`), job-identity-safe
 * cleanup, bounded PMD `ERROR_ALREADY_IN_STATE` recovery (`stopStreaming` + backoff), and
 * session-deduplicated blocked-stream warning publication for [PolarOnlineStreamingBridge]
 * (`ATT-9161`, `REQ-CON-053`).
 */
@Suppress("TooGenericExceptionCaught")
class PolarPmdStreamCoordinator(
    private val scope: CoroutineScope,
    private val deviceId: Long,
    private val deviceNameProvider: () -> String,
    private val polarIdProvider: () -> String,
    private val macAddress: String,
    private val isConnectedProvider: () -> Boolean,
    private val isShutDownProvider: () -> Boolean,
    private val delayFn: suspend (Long) -> Unit = { delay(it) }
) {

    enum class StreamSlot { HR, ACC, GYRO, PPI }

    companion object {
        private const val TAG = "PolarOnlineImuBridge"
        private const val MAX_CAUSE_DEPTH = 6
        const val MAX_ALREADY_IN_STATE_RETRIES = 3
        /** Bounded drop-oldest flow buffer capacity for Polar BLE streams (`REQ-STB-023.3`). */
        const val PMD_STREAM_BUFFER_CAPACITY = 64
        val RETRY_BACKOFF_MS: LongArray = longArrayOf(500L, 1_000L, 2_000L)

        private val _streamWarning = MutableStateFlow<PolarPmdStreamWarning?>(null)
        val streamWarning: StateFlow<PolarPmdStreamWarning?> = _streamWarning.asStateFlow()

        /**
         * Clears the active PMD blocked-stream warning. When [polarId] is non-null, clears only if
         * owned by [polarId] so multi-device sessions cannot clobber a sibling sensor's warning.
         */
        fun consumeStreamWarning(polarId: String? = null) {
            if (polarId == null) {
                _streamWarning.value = null
            } else {
                _streamWarning.update { current ->
                    if (current?.polarId?.equals(polarId, ignoreCase = true) == true) null else current
                }
            }
        }

        @VisibleForTesting
        fun resetWarningsForTesting() {
            _streamWarning.value = null
        }

        /**
         * Returns `true` when [throwable] (or its cause chain) represents a Polar PMD
         * `ERROR_ALREADY_IN_STATE` control-point failure (`REQ-CON-053.2`).
         */
        fun isPmdAlreadyInStateError(throwable: Throwable): Boolean {
            var cursor: Throwable? = throwable
            var depth = 0
            while (cursor != null && depth < MAX_CAUSE_DEPTH) {
                if (cursor is BleControlPointCommandError &&
                    cursor.error == PmdControlPointResponse.PmdControlPointResponseCode.ERROR_ALREADY_IN_STATE
                ) {
                    return true
                }
                if (cursor.message?.contains("ERROR_ALREADY_IN_STATE", ignoreCase = true) == true) {
                    return true
                }
                cursor = cursor.cause
                depth++
            }
            return false
        }
    }

    private class SlotState {
        val active = AtomicBoolean(false)

        @Volatile
        var job: Job? = null
    }

    private val slotLock = Any()
    private val slots: Map<StreamSlot, SlotState> = StreamSlot.entries.associateWith { SlotState() }
    private val warningEmittedForSession = AtomicBoolean(false)

    /**
     * Atomically claims [slot] and starts a lazy coroutine running [block] (`REQ-CON-053.1`).
     *
     * Re-entrant calls while [slot] is active or in-flight return `false` immediately without
     * cancelling the active job. If [preAcquire] returns `false`, the atomic slot is rolled back.
     * [onFinalCleanup] executes strictly when this coroutine still owns [slot] (`job === myJob`).
     */
    fun launchStream(
        slot: StreamSlot,
        preAcquire: (() -> Boolean)? = null,
        onFinalCleanup: (() -> Unit)? = null,
        block: suspend () -> Unit
    ): Boolean {
        val state = slots.getValue(slot)
        val launchedJob = synchronized(slotLock) {
            if (isShutDownProvider() || !state.active.compareAndSet(false, true)) {
                return false
            }
            val acquired = try {
                preAcquire?.invoke() ?: true
            } catch (t: Throwable) {
                state.active.set(false)
                throw t
            }
            if (!acquired) {
                state.active.set(false)
                return false
            }
            scope.launch(start = CoroutineStart.LAZY) {
                val myJob = coroutineContext[Job]
                try {
                    block()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to start/collect ${slot.name} stream on ${polarIdProvider()}: ${t.message}")
                } finally {
                    synchronized(slotLock) {
                        if (state.job === myJob) {
                            try {
                                onFinalCleanup?.invoke()
                            } finally {
                                state.job = null
                                state.active.set(false)
                            }
                        }
                    }
                }
            }.also { state.job = it }
        }
        launchedJob.start()
        return true
    }

    /**
     * Cancels a single active stream [slot] atomically and clears its ownership under [slotLock]
     * so a silent-HR watchdog can cleanly re-subscribe [StreamSlot.HR] without disturbing sibling
     * PMD streams (`ATT-9155`, `REQ-CON-054.3`).
     */
    fun cancelSlot(slot: StreamSlot) {
        val jobToCancel = synchronized(slotLock) {
            val state = slots.getValue(slot)
            val current = state.job
            state.job = null
            state.active.set(false)
            current
        }
        jobToCancel?.cancel()
    }

    /**
     * Cancels all active stream jobs atomically, clears slot ownership and executes
     * [onStreamsCancelled] under [slotLock] so no concurrent launch can re-acquire channels before
     * cleanup completes, then cancels the snapshotted jobs outside [slotLock].
     */
    fun cancelAllStreams(onStreamsCancelled: () -> Unit = {}) {
        val jobsToCancel = synchronized(slotLock) {
            val snapshot = slots.values.mapNotNull { state ->
                val current = state.job
                state.job = null
                state.active.set(false)
                current
            }
            onStreamsCancelled()
            snapshot
        }
        jobsToCancel.forEach { it.cancel() }
    }

    fun isActive(slot: StreamSlot): Boolean = slots.getValue(slot).active.get()

    fun isJobActive(slot: StreamSlot): Boolean = slots.getValue(slot).job?.isActive == true

    /**
     * Resets per-connect-cycle warning deduplication and clears any active global warning owned by
     * one of [ownedIds] (`REQ-CON-053.3`).
     */
    fun resetSessionWarningState(vararg ownedIds: String?) {
        warningEmittedForSession.set(false)
        for (id in ownedIds) {
            if (!id.isNullOrBlank()) {
                consumeStreamWarning(id)
            }
        }
    }

    /**
     * Collects [flow] with explicit bounded [BufferOverflow.DROP_OLDEST] backpressure so bursty
     * Polar BLE notifications never stall GATT dispatch or grow unboundedly (`REQ-STB-023.3`).
     */
    suspend fun <T> collectBounded(
        flow: Flow<T>,
        capacity: Int = PMD_STREAM_BUFFER_CAPACITY,
        onSample: (T) -> Unit
    ) {
        val terminalFailure = AtomicReference<Throwable?>(null)
        flow
            .catch { err -> terminalFailure.set(err) }
            .buffer(capacity = capacity, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            .collect { sample -> onSample(sample) }
        terminalFailure.get()?.let { throw it }
    }

    /**
     * Collects a Polar PMD stream ([PmdMeasurementType.ACC], `GYRO`, or `PPI`) with bounded
     * `ERROR_ALREADY_IN_STATE` recovery: issues [stopStreaming] + short exponential backoff up to
     * [MAX_ALREADY_IN_STATE_RETRIES] times, resets retry state on first packet, and emits a single
     * session-deduplicated [PolarPmdStreamWarning] if retries are exhausted (`REQ-CON-053.2`, `.3`).
     */
    suspend fun <T> collectPmdWithAlreadyInStateRecovery(
        pmdType: PmdMeasurementType,
        sessionId: String,
        stopStreaming: () -> Unit,
        streamFactory: () -> Flow<T>,
        onSample: (T) -> Unit
    ) {
        var retryAttempt = 0
        while (true) {
            val streamError = try {
                collectBounded(streamFactory()) { sample ->
                    retryAttempt = 0
                    onSample(sample)
                }
                null
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                t
            }
            if (streamError == null) {
                return
            }
            if (!isPmdAlreadyInStateError(streamError)) {
                Log.w(TAG, "${pmdType.name} stream error on $sessionId: ${streamError.message}")
                return
            }
            if (isShutDownProvider() || !isConnectedProvider()) {
                return
            }
            if (retryAttempt < MAX_ALREADY_IN_STATE_RETRIES) {
                val backoffMs = RETRY_BACKOFF_MS[retryAttempt]
                retryAttempt++
                Log.w(
                    TAG,
                    "PMD ERROR_ALREADY_IN_STATE on ${pmdType.name} ($sessionId, mac=$macAddress): " +
                        "recovery attempt $retryAttempt/$MAX_ALREADY_IN_STATE_RETRIES " +
                        "(stopStreaming + ${backoffMs}ms backoff)"
                )
                runCatching { stopStreaming() }.onFailure { err ->
                    if (err is CancellationException) throw err
                    Log.w(TAG, "stopStreaming(${pmdType.name}) failed on $sessionId during recovery: ${err.message}")
                }
                delayFn(backoffMs)
                if (isShutDownProvider() || !isConnectedProvider()) {
                    return
                }
            } else {
                val resolvedPolarId = polarIdProvider()
                Log.w(
                    TAG,
                    "PMD ERROR_ALREADY_IN_STATE recovery exhausted ($MAX_ALREADY_IN_STATE_RETRIES retries) " +
                        "on ${pmdType.name} (polarId=$resolvedPolarId, sessionId=$sessionId, mac=$macAddress). " +
                        "PMD channel likely held by another app (e.g. fi.polar.polarflow) or stale sensor state."
                )
                if (warningEmittedForSession.compareAndSet(false, true)) {
                    _streamWarning.value = PolarPmdStreamWarning(
                        deviceId = deviceId,
                        deviceName = deviceNameProvider(),
                        polarId = resolvedPolarId,
                        measurementType = pmdType,
                        retryCount = MAX_ALREADY_IN_STATE_RETRIES
                    )
                }
                return
            }
        }
    }
}
