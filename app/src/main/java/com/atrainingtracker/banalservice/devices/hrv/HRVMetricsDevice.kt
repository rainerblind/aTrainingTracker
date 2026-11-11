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
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/**
 * Stateful virtual device computing real-time Heart Rate Variability (HRV) metrics:
 * - [SensorType.HRV_RMSSD] (60s rolling window, excluding artifact-adjacent pairs to prevent downward bias)
 * - [SensorType.HRV_DFA_A1] (120s rolling window, evaluated every 5s across scales n in [4, 16])
 * - [SensorType.HRV_ARTIFACT_RATE] (120s window artifact percentage)
 *
 * System invariants:
 * - Rule 51: Constructor seeds identical baseline state as [resetSession].
 * - Rule 52: Zero telemetry rows emitted to [MySensor] until valid RR packets arrive ([hasReceivedRrData]).
 * - Gate suppression: Suppresses DFA alpha 1 emission (emits null) when 120s window artifact rate > 5.0%.
 *
 * Pursuant to REQ-TRK-060.3, REQ-TRK-060.4, and REQ-TRK-060.5.
 */
open class HRVMetricsDevice @JvmOverloads constructor(
    context: Context,
    mySensorManager: MySensorManager,
    timeProvider: TimeProvider? = null,
    private val artifactFilter: RrArtifactFilter = RrArtifactFilter(),
    startScheduler: Boolean = true
) : MyDevice(context, mySensorManager, DeviceType.HRV_METRICS), RrBeatListener {

    fun interface TimeProvider {
        fun currentTimeMillis(): Long
    }

    companion object {
        const val WINDOW_RMSSD_MS = 60_000L
        const val WINDOW_DFA_MS = 120_000L
        const val MIN_RMSSD_VALID_PAIRS = 10
        const val ARTIFACT_GATE_THRESHOLD_PERCENT = 5.0
        const val EVALUATION_PERIOD_SECONDS = 5L
    }

    private val mTimeProvider: TimeProvider = timeProvider ?: TimeProvider { System.currentTimeMillis() }
    private val mScheduler: ScheduledExecutorService? = if (startScheduler) Executors.newSingleThreadScheduledExecutor() else null

    private lateinit var mHrvRmssdSensor: MySensor<Double>
    private lateinit var mHrvDfaA1Sensor: MySensor<Double>
    private lateinit var mHrvArtifactRateSensor: MySensor<Double>

    // 120s rolling beat queue
    private val mBeatQueue = ArrayDeque<RrBeatEvent>()
    @Volatile
    var hasReceivedRrData: Boolean = false
        private set

    @Volatile
    var signalQualityResult: HrvSignalQualityResult = createDefaultSignalQualityResult()
        private set

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
    }

    @Synchronized
    fun resetSession() {
        mBeatQueue.clear()
        hasReceivedRrData = false
        signalQualityResult = createDefaultSignalQualityResult()
    }

    private fun createDefaultSignalQualityResult(): HrvSignalQualityResult {
        return HrvSignalQualityResult(
            status = HrvSignalQualityStatus.INSUFFICIENT_DATA,
            details = "Waiting for RR data",
            beatCount = 0
        )
    }

    @Synchronized
    override fun onRrBeat(event: RrBeatEvent) {
        hasReceivedRrData = true
        mBeatQueue.addLast(event)
        pruneOldBeats(event.beatTimestampMs)
    }

    @Synchronized
    private fun pruneOldBeats(currentTimestampMs: Long) {
        val cutoff = currentTimestampMs - WINDOW_DFA_MS
        while (!mBeatQueue.isEmpty() && mBeatQueue.first.beatTimestampMs < cutoff) {
            mBeatQueue.removeFirst()
        }
    }

    @Synchronized
    fun calculateMetrics() {
        // Rule 52: Zero telemetry rows emitted before physical RR data arrives
        if (!hasReceivedRrData || mBeatQueue.isEmpty()) {
            return
        }

        val now = mTimeProvider.currentTimeMillis()
        pruneOldBeats(now)

        if (mBeatQueue.isEmpty()) {
            return
        }

        val allBeats = mBeatQueue.toList()
        val filterResult = artifactFilter.filterBidirectionalBeats(allBeats)
        val filteredBeats = filterResult.beats

        // 1. Artifact rate over 120s window
        val artifactRate = filterResult.artifactRatePercent
        mHrvArtifactRateSensor.newValue(artifactRate)

        // 2. Bias-free rMSSD over 60s sliding window (REQ-TRK-060.3)
        val rmssdCutoff = now - WINDOW_RMSSD_MS
        val rmssdBeats = filteredBeats.filter { it.beatTimestampMs >= rmssdCutoff }
        val rmssdValue = computeBiasFreeRmssd(rmssdBeats)
        mHrvRmssdSensor.newValue(rmssdValue)

        // 3. DFA alpha 1 over 120s window with artifact gate suppression (REQ-TRK-060.4)
        val alpha1Value: Double?
        if (artifactRate > ARTIFACT_GATE_THRESHOLD_PERCENT) {
            // High artifact rate suppresses DFA alpha 1
            mHrvDfaA1Sensor.newValue(null)
            alpha1Value = null
        } else {
            val nnIntervals = filteredBeats.map { it.correctedRrMs }.toDoubleArray()
            val alpha1 = DfaCalculator.computeDfaAlpha1(nnIntervals)
            if (alpha1.isFinite() && !alpha1.isNaN()) {
                mHrvDfaA1Sensor.newValue(alpha1)
                alpha1Value = alpha1
            } else {
                mHrvDfaA1Sensor.newValue(null)
                alpha1Value = null
            }
        }

        // 4. Signal quality analysis (REQ-TRK-067)
        signalQualityResult = HrvSignalQualityAnalyzer.analyzeBeats(
            beats = filteredBeats,
            artifactRatePercent = artifactRate,
            rmssdMs = rmssdValue,
            dfaAlpha1 = alpha1Value
        )
    }

    private fun computeBiasFreeRmssd(beats: List<FilteredRrBeat>): Double? {
        if (beats.size < 2) return null

        var sumSquaredDiffs = 0.0
        var validPairCount = 0

        for (i in 0 until beats.size - 1) {
            val b1 = beats[i]
            val b2 = beats[i + 1]
            // Successive interval differences exclude artifact-adjacent pairs
            if (!b1.isArtifact && !b2.isArtifact) {
                val diff = b2.rawRrMs - b1.rawRrMs
                sumSquaredDiffs += diff * diff
                validPairCount++
            }
        }

        if (validPairCount < MIN_RMSSD_VALID_PAIRS) {
            return null
        }

        return sqrt(sumSquaredDiffs / validPairCount.toDouble())
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
}
