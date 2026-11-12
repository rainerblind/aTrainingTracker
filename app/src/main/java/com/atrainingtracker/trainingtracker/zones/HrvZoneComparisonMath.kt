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

package com.atrainingtracker.trainingtracker.zones

import androidx.annotation.StringRes
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvProtocolType
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.ThresholdConfidence
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import kotlin.math.roundToInt

/**
 * Status of the multi-session / protocol consensus gate for updating Power Zones (REQ-PER-037.3).
 */
enum class ConsensusGateStatus {
    LOCKED,
    UNLOCKED
}

/**
 * Power comparison delta between an estimated physiological threshold and a Coggan power anchor (REQ-PER-037.1).
 */
data class PowerZoneDelta(
    val targetWatts: Int,
    val anchorWatts: Int,
    val deltaWatts: Int,
    val deltaPercent: Double
)

/**
 * Informational Heart Rate delta between an estimated threshold and configured HR boundaries (REQ-PER-037.2).
 */
data class HeartRateZoneDelta(
    val targetBpm: Int,
    val anchorBpm: Int,
    val deltaBpm: Int,
    val percentHrMax: Double
)

/**
 * Historical workout session threshold used for multi-session consensus evaluation (REQ-PER-037.3, REQ-PER-037.7).
 */
data class HrvSessionThreshold(
    val workoutId: Long = 0L,
    val timestampEpochMs: Long,
    val hrvt2Watts: Int? = null,
    val confidence: ThresholdConfidence = ThresholdConfidence.LOW,
    val hrvt1Bpm: Int? = null,
    val hrvt2Bpm: Int? = null,
    val hrConfidence: ThresholdConfidence = confidence
)

/**
 * Evaluated consensus gate outcome with localized reason and suggested FTP (REQ-PER-037.3).
 */
data class ConsensusGateResult(
    val status: ConsensusGateStatus,
    @StringRes val reasonResId: Int,
    val reasonParam: Int? = null,
    val suggestedFtp: Int? = null
)

/**
 * Evaluated heart rate consensus gate outcome with localized reason and suggested anchors (REQ-PER-037.7).
 */
data class HrConsensusGateResult(
    val status: ConsensusGateStatus,
    @StringRes val reasonResId: Int,
    val reasonParam: Int? = null,
    val suggestedHrvt1Bpm: Int? = null,
    val suggestedHrvt2Bpm: Int? = null
)

/**
 * Complete comparison result between estimated HRV thresholds and configured training zones (REQ-PER-037).
 */
data class HrvZoneComparisonResult(
    val powerT1Delta: PowerZoneDelta?,
    val powerT2Delta: PowerZoneDelta?,
    val configuredFtp: Int?,
    val hrT1Delta: HeartRateZoneDelta?,
    val hrT2Delta: HeartRateZoneDelta?,
    val configuredHrMax: Int?,
    val consensusGate: ConsensusGateResult,
    val hrConsensusGate: HrConsensusGateResult = HrConsensusGateResult(
        status = ConsensusGateStatus.LOCKED,
        reasonResId = R.string.hrv_hr_consensus_reason_insufficient_sessions
    )
)

/**
 * Pure mathematical engine for comparing estimated physiological HRV thresholds (HRVT1 and HRVT2)
 * against configured Coggan Power and Heart Rate training zones (REQ-PER-037).
 *
 * Implements:
 * - Coggan Zone 2 ceiling (z2Max = round(0.75 * ftp)) and FTP comparison.
 * - Informational Heart Rate delta and %HRmax computation with non-overwrite invariant.
 * - Multi-session consensus and high-confidence guided test gating (30-day window, <= 10W spread).
 * - Statistical median aggregation for suggested FTP anchor updates.
 */
object HrvZoneComparisonMath {

    const val MAX_CONSENSUS_SPREAD_WATTS = 10
    const val MAX_HR_CONSENSUS_SPREAD_BPM = 6
    const val MIN_CONSENSUS_SESSIONS = 3
    const val CONSENSUS_WINDOW_MS = 30L * 24 * 60 * 60 * 1000L // 30 days

    /**
     * Compares estimated HRVT1 and HRVT2 steady-state power values against configured Coggan power anchors.
     *
     * @param hrvt1Watts Estimated HRVT1 power in Watts, or null if unestimated / INSUFFICIENT_SPAN.
     * @param hrvt2Watts Estimated HRVT2 power in Watts, or null if unestimated / INSUFFICIENT_SPAN.
     * @param configuredFtp Configured Functional Threshold Power in Watts (must be in [50, 600] to compare).
     * @return Pair of optional [PowerZoneDelta] for (HRVT1 vs z2Max, HRVT2 vs FTP).
     */
    fun comparePowerZones(
        hrvt1Watts: Double?,
        hrvt2Watts: Double?,
        configuredFtp: Int?
    ): Pair<PowerZoneDelta?, PowerZoneDelta?> {
        if (configuredFtp == null || configuredFtp <= 0) {
            return Pair(null, null)
        }

        val z2Max = (0.75 * configuredFtp).roundToInt()

        val t1Delta = hrvt1Watts?.let { watts ->
            val targetWatts = watts.roundToInt()
            val deltaWatts = targetWatts - z2Max
            val deltaPercent = if (z2Max > 0) ((deltaWatts.toDouble() / z2Max) * 100.0) else 0.0
            PowerZoneDelta(
                targetWatts = targetWatts,
                anchorWatts = z2Max,
                deltaWatts = deltaWatts,
                deltaPercent = deltaPercent
            )
        }

        val t2Delta = hrvt2Watts?.let { watts ->
            val targetWatts = watts.roundToInt()
            val deltaWatts = targetWatts - configuredFtp
            val deltaPercent = if (configuredFtp > 0) ((deltaWatts.toDouble() / configuredFtp) * 100.0) else 0.0
            PowerZoneDelta(
                targetWatts = targetWatts,
                anchorWatts = configuredFtp,
                deltaWatts = deltaWatts,
                deltaPercent = deltaPercent
            )
        }

        return Pair(t1Delta, t2Delta)
    }

    /**
     * Compares estimated HRVT1 and HRVT2 heart rates against configured HR boundaries as an informational baseline.
     *
     * Under the Non-Overwrite Invariant (REQ-PER-037.2), HR comparisons never produce recommendations
     * to mutate configured HRmax or HR zones.
     *
     * @param hrvt1Bpm Estimated HRVT1 heart rate in bpm, or null if unestimated / INSUFFICIENT_SPAN.
     * @param hrvt2Bpm Estimated HRVT2 heart rate in bpm, or null if unestimated / INSUFFICIENT_SPAN.
     * @param configuredHrMax Configured maximum heart rate in bpm.
     * @param hrZoneThresholds Configured heart rate zone thresholds (optional; defaults to 69% and 89% HRmax).
     * @return Pair of optional [HeartRateZoneDelta] for (HRVT1 vs z2Max, HRVT2 vs z4Max).
     */
    fun compareHeartRateZones(
        hrvt1Bpm: Double?,
        hrvt2Bpm: Double?,
        configuredHrMax: Int?,
        hrZoneThresholds: HeartRateZoneThresholds? = null
    ): Pair<HeartRateZoneDelta?, HeartRateZoneDelta?> {
        if (configuredHrMax == null || configuredHrMax <= 0) {
            return Pair(null, null)
        }

        val z2Max = hrZoneThresholds?.z2Max ?: (0.69 * configuredHrMax).roundToInt()
        val z4Max = hrZoneThresholds?.z4Max ?: (0.89 * configuredHrMax).roundToInt()

        val t1Delta = hrvt1Bpm?.let { bpm ->
            val targetBpm = bpm.roundToInt()
            val deltaBpm = targetBpm - z2Max
            val percentHrMax = (targetBpm.toDouble() / configuredHrMax) * 100.0
            HeartRateZoneDelta(
                targetBpm = targetBpm,
                anchorBpm = z2Max,
                deltaBpm = deltaBpm,
                percentHrMax = percentHrMax
            )
        }

        val t2Delta = hrvt2Bpm?.let { bpm ->
            val targetBpm = bpm.roundToInt()
            val deltaBpm = targetBpm - z4Max
            val percentHrMax = (targetBpm.toDouble() / configuredHrMax) * 100.0
            HeartRateZoneDelta(
                targetBpm = targetBpm,
                anchorBpm = z4Max,
                deltaBpm = deltaBpm,
                percentHrMax = percentHrMax
            )
        }

        return Pair(t1Delta, t2Delta)
    }

    /**
     * Evaluates whether Power Zone updates (FTP) can be unlocked based on either a High-Confidence Guided Test
     * or a multi-session consensus across recent qualifying workouts (REQ-PER-037.3).
     *
     * @param currentProtocol Protocol provenance of current workout.
     * @param currentConfidence Confidence level of current workout.
     * @param currentHrvt2Watts Estimated HRVT2 power for current workout.
     * @param currentTimestampMs Timestamp of current workout in epoch milliseconds.
     * @param recentSessions Historical sessions within observation range.
     * @return [ConsensusGateResult] describing lock state, localized reason, and suggested FTP.
     */
    fun evaluateConsensusGate(
        currentProtocol: HrvProtocolType,
        currentConfidence: ThresholdConfidence,
        currentHrvt2Watts: Int?,
        currentTimestampMs: Long = System.currentTimeMillis(),
        recentSessions: List<HrvSessionThreshold> = emptyList()
    ): ConsensusGateResult {
        if (currentHrvt2Watts == null || currentHrvt2Watts <= 0) {
            return ConsensusGateResult(
                status = ConsensusGateStatus.LOCKED,
                reasonResId = R.string.hrv_consensus_reason_insufficient_sessions
            )
        }

        // Condition A: High-confidence Guided Step or Ramp Test (ATT-4969)
        val isGuidedTest = currentProtocol == HrvProtocolType.GUIDED_STEP || currentProtocol == HrvProtocolType.GUIDED_RAMP
        if (isGuidedTest && currentConfidence == ThresholdConfidence.HIGH) {
            return ConsensusGateResult(
                status = ConsensusGateStatus.UNLOCKED,
                reasonResId = R.string.hrv_consensus_reason_guided_high,
                suggestedFtp = currentHrvt2Watts
            )
        }

        // Condition B: Multi-Session Consensus (M >= 3 sessions within 30 days agreeing within +/-10 W)
        val currentSession = HrvSessionThreshold(
            timestampEpochMs = currentTimestampMs,
            hrvt2Watts = currentHrvt2Watts,
            confidence = currentConfidence
        )
        val allCandidateSessions = (recentSessions + currentSession)

        val qualifyingSessions = allCandidateSessions.filter { session ->
            val withinWindow = (currentTimestampMs - session.timestampEpochMs) in 0..CONSENSUS_WINDOW_MS
            val validWatts = session.hrvt2Watts != null && session.hrvt2Watts > 0
            val validConfidence = session.confidence == ThresholdConfidence.HIGH || session.confidence == ThresholdConfidence.MEDIUM
            withinWindow && validWatts && validConfidence
        }

        if (qualifyingSessions.size < MIN_CONSENSUS_SESSIONS) {
            return ConsensusGateResult(
                status = ConsensusGateStatus.LOCKED,
                reasonResId = R.string.hrv_consensus_reason_insufficient_sessions
            )
        }

        val wattsList = qualifyingSessions.map { it.hrvt2Watts!! }
        val minWatts = wattsList.minOrNull() ?: 0
        val maxWatts = wattsList.maxOrNull() ?: 0

        if (maxWatts - minWatts <= MAX_CONSENSUS_SPREAD_WATTS) {
            val suggestedFtp = calculateMedian(wattsList)
            return ConsensusGateResult(
                status = ConsensusGateStatus.UNLOCKED,
                reasonResId = R.string.hrv_consensus_reason_multi_session,
                reasonParam = qualifyingSessions.size,
                suggestedFtp = suggestedFtp
            )
        } else {
            return ConsensusGateResult(
                status = ConsensusGateStatus.LOCKED,
                reasonResId = R.string.hrv_consensus_reason_spread_too_high
            )
        }
    }

    /**
     * Calculates the statistical median of a non-empty integer list.
     */
    fun calculateMedian(values: List<Int>): Int {
        require(values.isNotEmpty()) { "Values list cannot be empty for median calculation" }
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            ((sorted[mid - 1] + sorted[mid]) / 2.0).roundToInt()
        }
    }

    /**
     * Evaluates whether Heart Rate training zone updates can be unlocked based on either a High-Confidence Guided Test
     * or a multi-session consensus across recent qualifying workouts (REQ-PER-037.7).
     *
     * @param currentProtocol Protocol provenance of current workout.
     * @param currentConfidence Confidence level of current workout.
     * @param currentHrvt1Bpm Estimated HRVT1 heart rate for current workout.
     * @param currentHrvt2Bpm Estimated HRVT2 heart rate for current workout.
     * @param currentTimestampMs Timestamp of current workout in epoch milliseconds.
     * @param recentSessions Historical sessions within observation range.
     * @return [HrConsensusGateResult] describing lock state, localized reason, and suggested HRVT1 / HRVT2 anchors.
     */
    fun evaluateHrConsensusGate(
        currentProtocol: HrvProtocolType,
        currentConfidence: ThresholdConfidence,
        currentHrvt1Bpm: Int?,
        currentHrvt2Bpm: Int?,
        currentTimestampMs: Long = System.currentTimeMillis(),
        recentSessions: List<HrvSessionThreshold> = emptyList()
    ): HrConsensusGateResult {
        if (currentHrvt1Bpm == null || currentHrvt2Bpm == null || currentHrvt1Bpm <= 0 || currentHrvt2Bpm <= currentHrvt1Bpm) {
            return HrConsensusGateResult(
                status = ConsensusGateStatus.LOCKED,
                reasonResId = R.string.hrv_hr_consensus_reason_insufficient_sessions
            )
        }

        // Condition A: High-confidence Guided Step or Ramp Test (ATT-4969)
        val isGuidedTest = currentProtocol == HrvProtocolType.GUIDED_STEP || currentProtocol == HrvProtocolType.GUIDED_RAMP
        if (isGuidedTest && currentConfidence == ThresholdConfidence.HIGH && (currentHrvt2Bpm >= currentHrvt1Bpm + 5)) {
            return HrConsensusGateResult(
                status = ConsensusGateStatus.UNLOCKED,
                reasonResId = R.string.hrv_hr_consensus_reason_guided_high,
                suggestedHrvt1Bpm = currentHrvt1Bpm,
                suggestedHrvt2Bpm = currentHrvt2Bpm
            )
        }

        // Condition B: Multi-Session Consensus (M >= 3 sessions within 30 days agreeing within <= 6 bpm)
        val currentSession = HrvSessionThreshold(
            timestampEpochMs = currentTimestampMs,
            hrvt1Bpm = currentHrvt1Bpm,
            hrvt2Bpm = currentHrvt2Bpm,
            hrConfidence = currentConfidence,
            confidence = currentConfidence
        )
        val allCandidateSessions = (recentSessions + currentSession)

        val qualifyingSessions = allCandidateSessions.filter { session ->
            val withinWindow = (currentTimestampMs - session.timestampEpochMs) in 0..CONSENSUS_WINDOW_MS
            val validBpm = session.hrvt1Bpm != null && session.hrvt2Bpm != null && session.hrvt1Bpm > 0 && session.hrvt2Bpm > session.hrvt1Bpm
            val validConfidence = session.hrConfidence == ThresholdConfidence.HIGH || session.hrConfidence == ThresholdConfidence.MEDIUM
            withinWindow && validBpm && validConfidence
        }

        if (qualifyingSessions.size < MIN_CONSENSUS_SESSIONS) {
            return HrConsensusGateResult(
                status = ConsensusGateStatus.LOCKED,
                reasonResId = R.string.hrv_hr_consensus_reason_insufficient_sessions
            )
        }

        val t1List = qualifyingSessions.map { it.hrvt1Bpm!! }
        val t2List = qualifyingSessions.map { it.hrvt2Bpm!! }

        val spreadT1 = (t1List.maxOrNull() ?: 0) - (t1List.minOrNull() ?: 0)
        val spreadT2 = (t2List.maxOrNull() ?: 0) - (t2List.minOrNull() ?: 0)

        if (spreadT1 <= MAX_HR_CONSENSUS_SPREAD_BPM && spreadT2 <= MAX_HR_CONSENSUS_SPREAD_BPM) {
            val suggestedT1 = calculateMedian(t1List)
            val suggestedT2 = calculateMedian(t2List)
            return HrConsensusGateResult(
                status = ConsensusGateStatus.UNLOCKED,
                reasonResId = R.string.hrv_hr_consensus_reason_multi_session,
                reasonParam = qualifyingSessions.size,
                suggestedHrvt1Bpm = suggestedT1,
                suggestedHrvt2Bpm = suggestedT2
            )
        } else {
            return HrConsensusGateResult(
                status = ConsensusGateStatus.LOCKED,
                reasonResId = R.string.hrv_hr_consensus_reason_spread_too_high
            )
        }
    }

    /**
     * Evaluates complete zone comparison metrics against both Power and Heart Rate training zones.
     */
    fun computeComparison(
        hrvt1Watts: Double?,
        hrvt2Watts: Double?,
        configuredFtp: Int?,
        hrvt1Bpm: Double?,
        hrvt2Bpm: Double?,
        configuredHrMax: Int?,
        hrZoneThresholds: HeartRateZoneThresholds? = null,
        currentProtocol: HrvProtocolType = HrvProtocolType.FREE_WORKOUT,
        currentConfidence: ThresholdConfidence = ThresholdConfidence.LOW,
        currentTimestampMs: Long = System.currentTimeMillis(),
        recentSessions: List<HrvSessionThreshold> = emptyList(),
        currentHrConfidence: ThresholdConfidence = currentConfidence
    ): HrvZoneComparisonResult {
        val (powerT1, powerT2) = comparePowerZones(hrvt1Watts, hrvt2Watts, configuredFtp)
        val (hrT1, hrT2) = compareHeartRateZones(hrvt1Bpm, hrvt2Bpm, configuredHrMax, hrZoneThresholds)

        val consensusGate = evaluateConsensusGate(
            currentProtocol = currentProtocol,
            currentConfidence = currentConfidence,
            currentHrvt2Watts = hrvt2Watts?.roundToInt(),
            currentTimestampMs = currentTimestampMs,
            recentSessions = recentSessions
        )

        val hrConsensusGate = evaluateHrConsensusGate(
            currentProtocol = currentProtocol,
            currentConfidence = currentHrConfidence,
            currentHrvt1Bpm = hrvt1Bpm?.roundToInt(),
            currentHrvt2Bpm = hrvt2Bpm?.roundToInt(),
            currentTimestampMs = currentTimestampMs,
            recentSessions = recentSessions
        )

        return HrvZoneComparisonResult(
            powerT1Delta = powerT1,
            powerT2Delta = powerT2,
            configuredFtp = configuredFtp,
            hrT1Delta = hrT1,
            hrT2Delta = hrT2,
            configuredHrMax = configuredHrMax,
            consensusGate = consensusGate,
            hrConsensusGate = hrConsensusGate
        )
    }
}
