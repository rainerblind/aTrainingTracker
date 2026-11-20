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

package com.atrainingtracker.banalservice.devices.hrv

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.atrainingtracker.banalservice.devices.MyDevice
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.SensorType
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Per-device live HRV controller attached to RR-capable HRM devices (`BTLEHeartRateDevice`,
 * `ANTHeartRateDevice`, `PolarOnlineStreamingBridge`) pursuant to `REQ-HRV-050.3`.
 *
 * Concurrency & lifecycle invariants:
 * - **Rule 52 lazy initialization**: Zero HRV [MySensor]s registered and zero scheduler threads
 *   allocated until the first RR beat arrives via [onRrBeat].
 * - **Single-batch registration**: Registers all 4 HRV sensors (`HRV_RMSSD`, `HRV_DFA_A1`,
 *   `HRV_ARTIFACT_RATE`, `HRV_SDNN`) in one [HostCallbacks.addAndRegisterSensors] call.
 * - **Lock-order & generation-token safety (`REQ-SEN-021.3`)**: Strictly enforces
 *   `publishLock -> lock` acquisition order (never acquires `publishLock` inside `lock`) and
 *   never holds internal [lock] while calling into [hostCallbacks] or [MySensor.newValue].
 *   Cold-start/reconnect registration and disconnect/shutdown detachment serialize on
 *   [publishLock] while monotonic [generation] prevents stale timer ticks or delayed accumulator
 *   resets from overwriting valid values or leaving orphaned sensors.
 * - **Disconnect stale-value guard**: Pushes `newValue(null)` while sensors are still activated
 *   *before* `deactivateSensor()` / `unregisterSensor()` / `removeSensor()`.
 */
class PerDeviceHrvController @JvmOverloads constructor(
    context: Context?,
    private val hostDevice: MyDevice,
    private val hostCallbacks: HostCallbacks,
    timeProvider: HRVMetricsDevice.TimeProvider? = null,
    artifactFilter: RrArtifactFilter = RrArtifactFilter(),
    private val startScheduler: Boolean = true,
    artifactGateThresholdPercent: Double? = null
) : HrvTelemetryProvider {

    /**
     * Java-accessible bridge to [MyDevice]'s protected sensor registration helpers without
     * widening [MyDevice] visibility or modifying `DeviceManager.java`.
     */
    interface HostCallbacks {
        fun addAndRegisterSensors(sensors: List<@JvmSuppressWildcards MySensor<*>>)
        fun unregisterSensor(sensor: MySensor<*>)
        fun removeSensor(sensorType: SensorType)
    }

    private data class SensorBundle(
        val rmssd: MySensor<Double>,
        val dfaA1: MySensor<Double>,
        val artifactRate: MySensor<Double>,
        val sdnn: MySensor<Double>
    ) {
        fun asList(): List<MySensor<*>> = listOf(rmssd, dfaA1, artifactRate, sdnn)
    }

    private val mTimeProvider: HRVMetricsDevice.TimeProvider =
        timeProvider ?: HRVMetricsDevice.TimeProvider { System.currentTimeMillis() }
    private val mEngine = HrvRollingEngine(context, artifactFilter, artifactGateThresholdPercent)

    // Strict global lock hierarchy: publishLock -> lock (never reverse; hostCallbacks and
    // MySensor.newValue are invoked under publishLock without holding lock).
    private val lock = Any()
    private val publishLock = Any()

    private var sensors: SensorBundle? = null
    private var generation: Long = 0L
    private var scheduler: ScheduledExecutorService? = null
    private var scheduledTask: ScheduledFuture<*>? = null
    private var isShutDown: Boolean = false

    @VisibleForTesting
    internal var onBeforeResetNullPublishHook: (() -> Unit)? = null

    var artifactGateThresholdPercent: Double?
        get() = mEngine.artifactGateThresholdPercent
        set(value) {
            mEngine.artifactGateThresholdPercent = value
        }

    val hasReceivedRrData: Boolean
        get() = mEngine.hasReceivedRrData

    override val signalQualityResult: HrvSignalQualityResult
        get() = mEngine.signalQualityResult

    override val latestArtifactRatePercent: Double?
        get() = mEngine.latestArtifactRatePercent

    /**
     * Ingests an RR beat from the host HRM device, lazily creating and batch-registering all 4
     * per-device HRV sensors and arming the 5s scheduler on first beat (`Rule 52`).
     */
    fun onRrBeat(event: RrBeatEvent) {
        // Steady-state fast path: already registered, only needs brief lock to enqueue beat.
        val needsRegistration = synchronized(lock) {
            if (isShutDown) {
                return
            }
            if (sensors != null) {
                mEngine.onRrBeat(event)
                ensureSchedulerLocked()
                false
            } else {
                true
            }
        }
        if (!needsRegistration) {
            return
        }

        // Cold-start / reconnect path serialized under publishLock -> lock so disconnect/shutdown
        // cannot orphan newly created sensors or clobber newly registered reconnect sensors.
        synchronized(publishLock) {
            var createdBundle: SensorBundle? = null
            var createdGen = 0L
            synchronized(lock) {
                if (isShutDown) {
                    return
                }
                mEngine.onRrBeat(event)
                if (sensors == null) {
                    val created = SensorBundle(
                        rmssd = MySensor(hostDevice, SensorType.HRV_RMSSD),
                        dfaA1 = MySensor(hostDevice, SensorType.HRV_DFA_A1),
                        artifactRate = MySensor(hostDevice, SensorType.HRV_ARTIFACT_RATE),
                        sdnn = MySensor(hostDevice, SensorType.HRV_SDNN)
                    )
                    sensors = created
                    createdBundle = created
                    createdGen = generation
                }
                ensureSchedulerLocked()
            }
            val bundleToRegister = createdBundle
            if (bundleToRegister != null) {
                val stillValid = synchronized(lock) {
                    val valid = !isShutDown && generation == createdGen && sensors === bundleToRegister
                    if (!valid && sensors === bundleToRegister) {
                        // Defense-in-depth rollback: never leave an unregistered bundle wedged in sensors
                        sensors = null
                    }
                    valid
                }
                if (stillValid) {
                    hostCallbacks.addAndRegisterSensors(bundleToRegister.asList())
                }
            }
        }
    }

    /**
     * Deterministically evaluates rolling HRV metrics for the current generation (used by tests
     * when `startScheduler = false`).
     */
    fun calculateMetrics() {
        calculateMetricsForGeneration(expectedGeneration = null)
    }

    private fun calculateMetricsForGeneration(expectedGeneration: Long?) {
        val currentGen: Long
        val bundle: SensorBundle
        val snapshot: HrvMetricsSnapshot
        synchronized(lock) {
            if (isShutDown) return
            if (expectedGeneration != null && expectedGeneration != generation) return
            bundle = sensors ?: return
            currentGen = generation
            snapshot = mEngine.evaluate(mTimeProvider.currentTimeMillis()) ?: return
        }
        synchronized(publishLock) {
            val stillValid = synchronized(lock) {
                !isShutDown && generation == currentGen && sensors === bundle
            }
            if (!stillValid) return
            bundle.artifactRate.newValue(snapshot.artifactRatePercent)
            bundle.rmssd.newValue(snapshot.rmssdMs)
            bundle.dfaA1.newValue(snapshot.dfaAlpha1)
            bundle.sdnn.newValue(snapshot.sdnnMs)
        }
    }

    /**
     * Handles `BANALService.RESET_ACCUMULATORS_INTENT` while the HRM remains connected (`Rule 51`):
     * serialized under [publishLock] -> [lock] so it can never race with cold-start registration or
     * metrics publication; bumps generation, cancels the 5s timer until the next RR beat arrives,
     * resets rolling state, and pushes `null` to all 4 sensors while still activated without
     * unregistering them unless fresh post-reset RR beats have already arrived.
     */
    fun onAccumulatorsReset() {
        synchronized(publishLock) {
            val resetGen: Long
            val bundle: SensorBundle?
            synchronized(lock) {
                generation++
                resetGen = generation
                mEngine.resetSession()
                scheduledTask?.cancel(false)
                scheduledTask = null
                bundle = sensors
            }
            onBeforeResetNullPublishHook?.invoke()
            if (bundle != null) {
                val stillValid = synchronized(lock) {
                    !isShutDown && generation == resetGen && !mEngine.hasReceivedRrData && sensors === bundle
                }
                if (stillValid) {
                    pushNullValues(bundle)
                }
            }
        }
    }

    /**
     * Handles HRM disconnect (`unregisterSensors()`): serialized under [publishLock] so teardown
     * completes `pushNullValues` -> `deactivateSensor` -> `unregisterSensor` -> `removeSensor`
     * atomically with respect to cold-start/reconnect registration.
     */
    fun onDisconnect() {
        synchronized(publishLock) {
            val bundle: SensorBundle?
            val execToShutdown: ScheduledExecutorService?
            synchronized(lock) {
                generation++
                mEngine.resetSession()
                scheduledTask?.cancel(false)
                scheduledTask = null
                execToShutdown = scheduler
                scheduler = null
                bundle = sensors
                sensors = null
            }
            execToShutdown?.shutdownNow()
            detachSensorsLocked(bundle)
        }
    }

    /**
     * Permanently tears down the controller during host device [MyDevice.shutDown].
     */
    fun shutDown() {
        synchronized(publishLock) {
            val bundle: SensorBundle?
            val execToShutdown: ScheduledExecutorService?
            synchronized(lock) {
                isShutDown = true
                generation++
                mEngine.resetSession()
                scheduledTask?.cancel(false)
                scheduledTask = null
                execToShutdown = scheduler
                scheduler = null
                bundle = sensors
                sensors = null
            }
            execToShutdown?.shutdownNow()
            detachSensorsLocked(bundle)
        }
    }

    fun hasRegisteredSensors(): Boolean = synchronized(lock) { sensors != null }

    fun isSchedulerActive(): Boolean = synchronized(lock) {
        val task = scheduledTask
        task != null && !task.isCancelled && !task.isDone
    }

    private fun ensureSchedulerLocked() {
        if (startScheduler && scheduledTask == null) {
            val exec = scheduler ?: createDaemonScheduler().also { scheduler = it }
            val capturedGen = generation
            scheduledTask = exec.scheduleWithFixedDelay(
                { calculateMetricsForGeneration(capturedGen) },
                1L,
                HRVMetricsDevice.EVALUATION_PERIOD_SECONDS,
                TimeUnit.SECONDS
            )
        }
    }

    private fun detachSensorsLocked(bundle: SensorBundle?) {
        if (bundle == null) return
        val sensorList = bundle.asList()
        // Push null while sensors are still activated so FilterManager clears pinned tiles immediately
        pushNullValues(bundle)
        for (sensor in sensorList) {
            sensor.deactivateSensor()
        }
        for (sensor in sensorList) {
            hostCallbacks.unregisterSensor(sensor)
            hostCallbacks.removeSensor(sensor.sensorType)
        }
    }

    private fun pushNullValues(bundle: SensorBundle) {
        bundle.artifactRate.newValue(null)
        bundle.rmssd.newValue(null)
        bundle.dfaA1.newValue(null)
        bundle.sdnn.newValue(null)
    }

    private fun createDaemonScheduler(): ScheduledExecutorService {
        val deviceId = hostDevice.deviceId
        return Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "PerDeviceHrv-$deviceId").apply { isDaemon = true }
        }
    }
}
