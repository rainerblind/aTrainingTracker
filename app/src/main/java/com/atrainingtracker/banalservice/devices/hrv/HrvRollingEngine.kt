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
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.devices.RrBeatNormalization
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import java.util.ArrayDeque
import kotlin.math.sqrt

/**
 * Snapshot produced by [HrvRollingEngine.evaluate] for a single evaluation tick (`REQ-HRV-050.2`).
 */
data class HrvMetricsSnapshot(
    val rmssdMs: Double?,
    val dfaAlpha1: Double?,
    val artifactRatePercent: Double?,
    val sdnnMs: Double?,
    val signalQualityResult: HrvSignalQualityResult,
    val isDrained: Boolean
)

/**
 * Reusable thread-safe causal rolling HRV engine shared by the global arbitrated [HRVMetricsDevice]
 * and per-device [PerDeviceHrvController] instances (`REQ-HRV-050.2`, `REQ-HRV-048.3`, `REQ-TRK-060`).
 *
 * Computes:
 * - Artifact rate (%) over active 120s window (filtered with 180s trailing Lipponen context).
 * - Bias-free rMSSD (ms) over 60s window excluding artifact-adjacent pairs (`>= 10` valid pairs).
 * - DFA alpha 1 over 120s window (`n in [4, 16]`, 50% overlap, gated on configured `[3.0, 5.0]%` threshold).
 * - Unbiased rolling SDNN (ms, `N - 1`) over active 120s non-artifact NN intervals (`>= 10` valid beats,
 *   gated on configured `[3.0, 5.0]%` artifact threshold).
 */
class HrvRollingEngine @JvmOverloads constructor(
    private val context: Context?,
    private val artifactFilter: RrArtifactFilter = RrArtifactFilter(),
    artifactGateThresholdPercent: Double? = null
) {

    companion object {
        const val WINDOW_RMSSD_MS = 60_000L
        const val WINDOW_DFA_MS = 120_000L
        const val WINDOW_FILTER_CONTEXT_MS = WINDOW_DFA_MS + 60_000L
        const val MIN_RMSSD_VALID_PAIRS = 10
        const val MIN_SDNN_VALID_BEATS = 10
        const val ARTIFACT_GATE_THRESHOLD_PERCENT = 5.0
        const val MIN_ARTIFACT_GATE_THRESHOLD_PERCENT = 3.0
        const val MAX_ARTIFACT_GATE_THRESHOLD_PERCENT = 5.0

        @JvmStatic
        fun createDefaultSignalQualityResult(): HrvSignalQualityResult {
            return HrvSignalQualityResult(
                status = HrvSignalQualityStatus.INSUFFICIENT_DATA,
                details = "Waiting for RR data",
                beatCount = 0
            )
        }
    }

    @Volatile
    var artifactGateThresholdPercent: Double? = artifactGateThresholdPercent

    private val beatQueue = ArrayDeque<RrBeatEvent>()

    @Volatile
    var hasReceivedRrData: Boolean = false
        private set

    @Volatile
    var signalQualityResult: HrvSignalQualityResult = createDefaultSignalQualityResult()
        private set

    @Volatile
    var latestArtifactRatePercent: Double? = null
        private set

    init {
        resetSession()
    }

    /**
     * Resets beat buffers and quality telemetry to initial baseline state (`Rule 51`).
     */
    @Synchronized
    fun resetSession() {
        beatQueue.clear()
        hasReceivedRrData = false
        signalQualityResult = createDefaultSignalQualityResult()
        latestArtifactRatePercent = null
    }

    /**
     * Appends an incoming RR beat event and prunes stale entries older than `180 s`.
     */
    @Synchronized
    fun onRrBeat(event: RrBeatEvent) {
        hasReceivedRrData = true
        beatQueue.addLast(event)
        pruneOldBeats(event.beatTimestampMs)
    }

    /**
     * Evaluates rolling HRV metrics at [nowMs]. Returns `null` when no RR beat has arrived yet
     * (`Rule 52`), a drained snapshot (`isDrained = true`, all metrics `null`) when no beats remain
     * inside `[nowMs - 120s, nowMs]`, or freshly computed metrics over the active 120s/60s windows.
     */
    @Synchronized
    fun evaluate(nowMs: Long): HrvMetricsSnapshot? {
        if (!hasReceivedRrData) {
            return null
        }

        pruneOldBeats(nowMs)

        val dfaCutoff = nowMs - WINDOW_DFA_MS
        if (beatQueue.isEmpty() || beatQueue.none { it.beatTimestampMs >= dfaCutoff }) {
            val defaultQuality = createDefaultSignalQualityResult()
            signalQualityResult = defaultQuality
            latestArtifactRatePercent = null
            return HrvMetricsSnapshot(
                rmssdMs = null,
                dfaAlpha1 = null,
                artifactRatePercent = null,
                sdnnMs = null,
                signalQualityResult = defaultQuality,
                isDrained = true
            )
        }

        val allBeats = RrBeatNormalization.sortAndNormalizeChronologically(beatQueue.toList())
        val filterResult = artifactFilter.filterLiveCausal(allBeats)
        val filteredBeats = filterResult.beats
        val dfaWindowBeats = filteredBeats.filter { it.beatTimestampMs >= dfaCutoff }

        // 1. Artifact rate over active 120s window (REQ-HRV-048.3; empty lookahead-trimmed window -> 0.0%)
        val artifactRate = if (dfaWindowBeats.isNotEmpty()) {
            (dfaWindowBeats.count { it.isArtifact }.toDouble() / dfaWindowBeats.size) * 100.0
        } else {
            0.0
        }
        latestArtifactRatePercent = artifactRate

        // 2. Bias-free rMSSD over 60s sliding window sliced from active 120s beats (REQ-TRK-060.3)
        val rmssdCutoff = nowMs - WINDOW_RMSSD_MS
        val rmssdBeats = dfaWindowBeats.filter { it.beatTimestampMs >= rmssdCutoff }
        val rmssdValue = computeBiasFreeRmssd(rmssdBeats)

        // 3. DFA alpha 1 over active 120s window with optical PPI & artifact gate suppression (REQ-TRK-060.4, REQ-HRV-051.3)
        val activeWindowHasOpticalPpi = allBeats.any { it.beatTimestampMs >= dfaCutoff && it.isOpticalPpi }
        val effectiveGateThreshold = getEffectiveArtifactGateThreshold()
        val alpha1Value: Double? = if (activeWindowHasOpticalPpi || dfaWindowBeats.isEmpty() || artifactRate > effectiveGateThreshold) {
            null
        } else {
            val nnIntervals = dfaWindowBeats.map { it.correctedRrMs }.toDoubleArray()
            val alpha1 = DfaCalculator.computeDfaAlpha1(nnIntervals)
            if (alpha1.isFinite() && alpha1 > 0.0) alpha1 else null
        }

        // 4. Unbiased rolling SDNN (N - 1) over active 120s non-artifact NN intervals (REQ-HRV-050.2)
        val sdnnValue = computeRollingSdnn(
            beats = dfaWindowBeats,
            artifactRatePercent = artifactRate,
            effectiveGateThreshold = effectiveGateThreshold
        )

        // 5. Signal quality analysis strictly over active 120s window (REQ-TRK-067, REQ-HRV-048.3, REQ-HRV-051.3)
        val quality = HrvSignalQualityAnalyzer.analyzeBeats(
            beats = dfaWindowBeats,
            artifactRatePercent = artifactRate,
            rmssdMs = rmssdValue,
            dfaAlpha1 = alpha1Value,
            isOpticalPpi = activeWindowHasOpticalPpi
        )
        signalQualityResult = quality

        return HrvMetricsSnapshot(
            rmssdMs = rmssdValue,
            dfaAlpha1 = alpha1Value,
            artifactRatePercent = artifactRate,
            sdnnMs = sdnnValue,
            signalQualityResult = quality,
            isDrained = false
        )
    }

    @Synchronized
    private fun pruneOldBeats(currentTimestampMs: Long) {
        val cutoff = currentTimestampMs - WINDOW_FILTER_CONTEXT_MS
        val iterator = beatQueue.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().beatTimestampMs < cutoff) {
                iterator.remove()
            }
        }
    }

    private fun getEffectiveArtifactGateThreshold(): Double {
        artifactGateThresholdPercent?.let { return it }
        val ctx = context ?: return ARTIFACT_GATE_THRESHOLD_PERCENT
        return try {
            if (TuningPreferencesDataStore.hasInitializedInstance()) {
                TuningPreferencesDataStore.getInstance(ctx)
                    .getTuningConfig()
                    .hrvArtifactGateThresholdPercent
                    .toDouble()
                    .coerceIn(MIN_ARTIFACT_GATE_THRESHOLD_PERCENT, MAX_ARTIFACT_GATE_THRESHOLD_PERCENT)
            } else {
                ARTIFACT_GATE_THRESHOLD_PERCENT
            }
        } catch (_: Exception) {
            ARTIFACT_GATE_THRESHOLD_PERCENT
        }
    }

    private fun computeBiasFreeRmssd(beats: List<FilteredRrBeat>): Double? {
        if (beats.size < 2) return null

        var sumSquaredDiffs = 0.0
        var validPairCount = 0

        for (i in 0 until beats.size - 1) {
            val b1 = beats[i]
            val b2 = beats[i + 1]
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

    private fun computeRollingSdnn(
        beats: List<FilteredRrBeat>,
        artifactRatePercent: Double,
        effectiveGateThreshold: Double
    ): Double? {
        if (beats.isEmpty() || artifactRatePercent > effectiveGateThreshold) {
            return null
        }
        val validBeats = beats.filter { !it.isArtifact }
        if (validBeats.size < MIN_SDNN_VALID_BEATS) {
            return null
        }
        val mean = validBeats.sumOf { it.correctedRrMs } / validBeats.size.toDouble()
        var sumSquaredDiffs = 0.0
        for (beat in validBeats) {
            val diff = beat.correctedRrMs - mean
            sumSquaredDiffs += diff * diff
        }
        val sdnn = sqrt(sumSquaredDiffs / (validBeats.size - 1).toDouble())
        return if (sdnn.isFinite() && !sdnn.isNaN()) sdnn else null
    }
}
