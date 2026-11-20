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
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.MyDevice
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.devices.RrBeatListener
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Stateful virtual device computing real-time Heart Rate Variability (HRV) metrics from the
 * `ECG > ANT+ > PPG` arbitrated RR stream ("Best Sensor"):
 * - [SensorType.HRV_RMSSD] (60s rolling window, excluding artifact-adjacent pairs to prevent downward bias)
 * - [SensorType.HRV_DFA_A1] (120s rolling window, evaluated every 5s across scales n in [4, 16])
 * - [SensorType.HRV_ARTIFACT_RATE] (120s window artifact percentage)
 * - [SensorType.HRV_SDNN] (120s rolling unbiased sample SD of non-artifact NN intervals, `REQ-HRV-050.2`)
 *
 * System invariants:
 * - Rule 51: Constructor seeds identical baseline state as [resetSession].
 * - Rule 52: Zero telemetry rows emitted to [MySensor] until valid RR packets arrive ([hasReceivedRrData]).
 * - Gate suppression: Suppresses DFA alpha 1 and SDNN emission (emits null) when 120s window artifact rate > threshold.
 *
 * Pursuant to REQ-TRK-060.3, REQ-TRK-060.4, REQ-TRK-060.5, REQ-HRV-048.3, and REQ-HRV-050.2.
 */
open class HRVMetricsDevice @JvmOverloads constructor(
    context: Context,
    mySensorManager: MySensorManager,
    timeProvider: TimeProvider? = null,
    artifactFilter: RrArtifactFilter = RrArtifactFilter(),
    startScheduler: Boolean = true,
    artifactGateThresholdPercent: Double? = null
) : MyDevice(context, mySensorManager, DeviceType.HRV_METRICS), RrBeatListener, HrvTelemetryProvider {

    fun interface TimeProvider {
        fun currentTimeMillis(): Long
    }

    companion object {
        const val WINDOW_RMSSD_MS = HrvRollingEngine.WINDOW_RMSSD_MS
        const val WINDOW_DFA_MS = HrvRollingEngine.WINDOW_DFA_MS
        const val WINDOW_FILTER_CONTEXT_MS = HrvRollingEngine.WINDOW_FILTER_CONTEXT_MS
        const val MIN_RMSSD_VALID_PAIRS = HrvRollingEngine.MIN_RMSSD_VALID_PAIRS
        const val MIN_SDNN_VALID_BEATS = HrvRollingEngine.MIN_SDNN_VALID_BEATS
        const val ARTIFACT_GATE_THRESHOLD_PERCENT = HrvRollingEngine.ARTIFACT_GATE_THRESHOLD_PERCENT
        const val EVALUATION_PERIOD_SECONDS = 5L
    }

    private val mTimeProvider: TimeProvider = timeProvider ?: TimeProvider { System.currentTimeMillis() }
    private val mEngine = HrvRollingEngine(context, artifactFilter, artifactGateThresholdPercent)
    private val mScheduler: ScheduledExecutorService? =
        if (startScheduler) Executors.newSingleThreadScheduledExecutor() else null

    private lateinit var mHrvRmssdSensor: MySensor<Double>
    private lateinit var mHrvDfaA1Sensor: MySensor<Double>
    private lateinit var mHrvArtifactRateSensor: MySensor<Double>
    private lateinit var mHrvSdnnSensor: MySensor<Double>

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

    init {
        registerSensors()
        // Rule 51: Constructor state seeding parity with resetSession()
        resetSession()

        if (startScheduler && mScheduler != null) {
            mScheduler.scheduleWithFixedDelay(this::calculateMetrics, 1, EVALUATION_PERIOD_SECONDS, TimeUnit.SECONDS)
        }
    }

    override fun getName(): String {
        return "HRV metrics"
    }

    override fun addSensors() {
        mHrvRmssdSensor = MySensor(this, SensorType.HRV_RMSSD)
        addSensor(mHrvRmssdSensor)

        mHrvDfaA1Sensor = MySensor(this, SensorType.HRV_DFA_A1)
        addSensor(mHrvDfaA1Sensor)

        mHrvArtifactRateSensor = MySensor(this, SensorType.HRV_ARTIFACT_RATE)
        addSensor(mHrvArtifactRateSensor)

        mHrvSdnnSensor = MySensor(this, SensorType.HRV_SDNN)
        addSensor(mHrvSdnnSensor)
    }

    fun resetSession() {
        mEngine.resetSession()
    }

    override fun onRrBeat(event: RrBeatEvent) {
        mEngine.onRrBeat(event)
    }

    override fun onAccumulatorsReset() {
        super.onAccumulatorsReset()
        resetSession()
    }

    fun calculateMetrics() {
        val snapshot = mEngine.evaluate(mTimeProvider.currentTimeMillis()) ?: return
        mHrvArtifactRateSensor.newValue(snapshot.artifactRatePercent)
        mHrvRmssdSensor.newValue(snapshot.rmssdMs)
        mHrvDfaA1Sensor.newValue(snapshot.dfaAlpha1)
        mHrvSdnnSensor.newValue(snapshot.sdnnMs)
    }

    @Synchronized
    override fun shutDown() {
        super.shutDown()
        if (mScheduler != null && !mScheduler.isShutdown) {
            mScheduler.shutdownNow()
        }
    }

    fun getHrvRmssdSensor(): MySensor<Double> = mHrvRmssdSensor
    fun getHrvDfaA1Sensor(): MySensor<Double> = mHrvDfaA1Sensor
    fun getHrvArtifactRateSensor(): MySensor<Double> = mHrvArtifactRateSensor
    fun getHrvSdnnSensor(): MySensor<Double> = mHrvSdnnSensor
}
