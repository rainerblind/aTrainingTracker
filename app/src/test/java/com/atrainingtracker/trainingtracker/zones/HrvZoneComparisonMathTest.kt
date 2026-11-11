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

import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvProtocolType
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.ThresholdConfidence
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit test suite verifying [HrvZoneComparisonMath] pursuant to REQ-PER-037 and TST-PER-045.
 */
class HrvZoneComparisonMathTest {

    @Test
    fun testComparePowerZones_validThresholds_computesCorrectDeltas() {
        val configuredFtp = 280 // z2Max = round(0.75 * 280) = 210
        val hrvt1Watts = 215.0
        val hrvt2Watts = 285.0

        val (t1Delta, t2Delta) = HrvZoneComparisonMath.comparePowerZones(
            hrvt1Watts = hrvt1Watts,
            hrvt2Watts = hrvt2Watts,
            configuredFtp = configuredFtp
        )

        assertNotNull(t1Delta)
        assertEquals(215, t1Delta!!.targetWatts)
        assertEquals(210, t1Delta.anchorWatts)
        assertEquals(5, t1Delta.deltaWatts)
        assertEquals((5.0 / 210.0) * 100.0, t1Delta.deltaPercent, 0.01)

        assertNotNull(t2Delta)
        assertEquals(285, t2Delta!!.targetWatts)
        assertEquals(280, t2Delta.anchorWatts)
        assertEquals(5, t2Delta.deltaWatts)
        assertEquals((5.0 / 280.0) * 100.0, t2Delta.deltaPercent, 0.01)
    }

    @Test
    fun testComparePowerZones_nullFtp_returnsNullDeltas() {
        val (t1Delta, t2Delta) = HrvZoneComparisonMath.comparePowerZones(
            hrvt1Watts = 215.0,
            hrvt2Watts = 285.0,
            configuredFtp = null
        )
        assertNull(t1Delta)
        assertNull(t2Delta)
    }

    @Test
    fun testComparePowerZones_unestimatedThresholds_returnsNullDeltasIndependently() {
        val configuredFtp = 250 // z2Max = 188
        val (t1Delta, t2Delta) = HrvZoneComparisonMath.comparePowerZones(
            hrvt1Watts = 190.0,
            hrvt2Watts = null,
            configuredFtp = configuredFtp
        )

        assertNotNull(t1Delta)
        assertEquals(2, t1Delta!!.deltaWatts)
        assertNull(t2Delta)
    }

    @Test
    fun testCompareHeartRateZones_validThresholds_computesCorrectInformationalDeltas() {
        val configuredHrMax = 190
        val customHrZones = HeartRateZoneThresholds(
            z1Max = 112,
            z2Max = 142,
            z3Max = 155,
            z4Max = 170
        )
        val hrvt1Bpm = 146.0
        val hrvt2Bpm = 172.0

        val (t1Delta, t2Delta) = HrvZoneComparisonMath.compareHeartRateZones(
            hrvt1Bpm = hrvt1Bpm,
            hrvt2Bpm = hrvt2Bpm,
            configuredHrMax = configuredHrMax,
            hrZoneThresholds = customHrZones
        )

        assertNotNull(t1Delta)
        assertEquals(146, t1Delta!!.targetBpm)
        assertEquals(142, t1Delta.anchorBpm)
        assertEquals(4, t1Delta.deltaBpm)
        assertEquals((146.0 / 190.0) * 100.0, t1Delta.percentHrMax, 0.01)

        assertNotNull(t2Delta)
        assertEquals(172, t2Delta!!.targetBpm)
        assertEquals(170, t2Delta.anchorBpm)
        assertEquals(2, t2Delta.deltaBpm)
        assertEquals((172.0 / 190.0) * 100.0, t2Delta.percentHrMax, 0.01)
    }

    @Test
    fun testCompareHeartRateZones_nullHrMax_returnsNullDeltas() {
        val (t1Delta, t2Delta) = HrvZoneComparisonMath.compareHeartRateZones(
            hrvt1Bpm = 146.0,
            hrvt2Bpm = 172.0,
            configuredHrMax = null
        )
        assertNull(t1Delta)
        assertNull(t2Delta)
    }

    @Test
    fun testEvaluateConsensusGate_guidedStepHighConfidence_unlocksImmediately() {
        val result = HrvZoneComparisonMath.evaluateConsensusGate(
            currentProtocol = HrvProtocolType.GUIDED_STEP,
            currentConfidence = ThresholdConfidence.HIGH,
            currentHrvt2Watts = 285
        )

        assertEquals(ConsensusGateStatus.UNLOCKED, result.status)
        assertEquals(R.string.hrv_consensus_reason_guided_high, result.reasonResId)
        assertEquals(285, result.suggestedFtp)
    }

    @Test
    fun testEvaluateConsensusGate_guidedRampHighConfidence_unlocksImmediately() {
        val result = HrvZoneComparisonMath.evaluateConsensusGate(
            currentProtocol = HrvProtocolType.GUIDED_RAMP,
            currentConfidence = ThresholdConfidence.HIGH,
            currentHrvt2Watts = 290
        )

        assertEquals(ConsensusGateStatus.UNLOCKED, result.status)
        assertEquals(R.string.hrv_consensus_reason_guided_high, result.reasonResId)
        assertEquals(290, result.suggestedFtp)
    }

    @Test
    fun testEvaluateConsensusGate_singleFreeRide_remainsLocked() {
        val result = HrvZoneComparisonMath.evaluateConsensusGate(
            currentProtocol = HrvProtocolType.FREE_WORKOUT,
            currentConfidence = ThresholdConfidence.HIGH,
            currentHrvt2Watts = 280,
            recentSessions = emptyList()
        )

        assertEquals(ConsensusGateStatus.LOCKED, result.status)
        assertEquals(R.string.hrv_consensus_reason_insufficient_sessions, result.reasonResId)
        assertNull(result.suggestedFtp)
    }

    @Test
    fun testEvaluateConsensusGate_threeSessionsWithin10Watts_unlocksWithMedian() {
        val now = System.currentTimeMillis()
        val recentSessions = listOf(
            HrvSessionThreshold(
                workoutId = 1L,
                timestampEpochMs = now - (2L * 24 * 60 * 60 * 1000L), // 2 days ago
                hrvt2Watts = 278,
                confidence = ThresholdConfidence.MEDIUM
            ),
            HrvSessionThreshold(
                workoutId = 2L,
                timestampEpochMs = now - (10L * 24 * 60 * 60 * 1000L), // 10 days ago
                hrvt2Watts = 280,
                confidence = ThresholdConfidence.HIGH
            )
        )

        val result = HrvZoneComparisonMath.evaluateConsensusGate(
            currentProtocol = HrvProtocolType.FREE_WORKOUT,
            currentConfidence = ThresholdConfidence.MEDIUM,
            currentHrvt2Watts = 284, // Spread: 284 - 278 = 6 W <= 10 W
            currentTimestampMs = now,
            recentSessions = recentSessions
        )

        assertEquals(ConsensusGateStatus.UNLOCKED, result.status)
        assertEquals(R.string.hrv_consensus_reason_multi_session, result.reasonResId)
        assertEquals(3, result.reasonParam)
        assertEquals(280, result.suggestedFtp) // Median of [278, 280, 284]
    }

    @Test
    fun testEvaluateConsensusGate_threeSessionsSpreadExceeds10Watts_remainsLocked() {
        val now = System.currentTimeMillis()
        val recentSessions = listOf(
            HrvSessionThreshold(
                workoutId = 1L,
                timestampEpochMs = now - (2L * 24 * 60 * 60 * 1000L),
                hrvt2Watts = 270,
                confidence = ThresholdConfidence.MEDIUM
            ),
            HrvSessionThreshold(
                workoutId = 2L,
                timestampEpochMs = now - (5L * 24 * 60 * 60 * 1000L),
                hrvt2Watts = 280,
                confidence = ThresholdConfidence.HIGH
            )
        )

        val result = HrvZoneComparisonMath.evaluateConsensusGate(
            currentProtocol = HrvProtocolType.FREE_WORKOUT,
            currentConfidence = ThresholdConfidence.HIGH,
            currentHrvt2Watts = 292, // Spread: 292 - 270 = 22 W > 10 W
            currentTimestampMs = now,
            recentSessions = recentSessions
        )

        assertEquals(ConsensusGateStatus.LOCKED, result.status)
        assertEquals(R.string.hrv_consensus_reason_spread_too_high, result.reasonResId)
        assertNull(result.suggestedFtp)
    }

    @Test
    fun testEvaluateConsensusGate_sessionsOlderThan30Days_ignored() {
        val now = System.currentTimeMillis()
        val recentSessions = listOf(
            HrvSessionThreshold(
                workoutId = 1L,
                timestampEpochMs = now - (35L * 24 * 60 * 60 * 1000L), // 35 days ago (expired)
                hrvt2Watts = 280,
                confidence = ThresholdConfidence.HIGH
            ),
            HrvSessionThreshold(
                workoutId = 2L,
                timestampEpochMs = now - (5L * 24 * 60 * 60 * 1000L),
                hrvt2Watts = 280,
                confidence = ThresholdConfidence.HIGH
            )
        )

        val result = HrvZoneComparisonMath.evaluateConsensusGate(
            currentProtocol = HrvProtocolType.FREE_WORKOUT,
            currentConfidence = ThresholdConfidence.HIGH,
            currentHrvt2Watts = 282,
            currentTimestampMs = now,
            recentSessions = recentSessions
        )

        // Only 2 sessions within 30 days -> Insufficient
        assertEquals(ConsensusGateStatus.LOCKED, result.status)
        assertEquals(R.string.hrv_consensus_reason_insufficient_sessions, result.reasonResId)
    }

    @Test
    fun testEvaluateConsensusGate_lowConfidenceSessions_ignored() {
        val now = System.currentTimeMillis()
        val recentSessions = listOf(
            HrvSessionThreshold(
                workoutId = 1L,
                timestampEpochMs = now - (2L * 24 * 60 * 60 * 1000L),
                hrvt2Watts = 280,
                confidence = ThresholdConfidence.LOW // Excluded
            ),
            HrvSessionThreshold(
                workoutId = 2L,
                timestampEpochMs = now - (5L * 24 * 60 * 60 * 1000L),
                hrvt2Watts = 280,
                confidence = ThresholdConfidence.HIGH
            )
        )

        val result = HrvZoneComparisonMath.evaluateConsensusGate(
            currentProtocol = HrvProtocolType.FREE_WORKOUT,
            currentConfidence = ThresholdConfidence.HIGH,
            currentHrvt2Watts = 282,
            currentTimestampMs = now,
            recentSessions = recentSessions
        )

        assertEquals(ConsensusGateStatus.LOCKED, result.status)
        assertEquals(R.string.hrv_consensus_reason_insufficient_sessions, result.reasonResId)
    }

    @Test
    fun testCalculateMedian_oddAndEvenCollections() {
        assertEquals(280, HrvZoneComparisonMath.calculateMedian(listOf(275, 280, 285)))
        assertEquals(280, HrvZoneComparisonMath.calculateMedian(listOf(270, 275, 285, 290)))
        assertEquals(275, HrvZoneComparisonMath.calculateMedian(listOf(275)))
    }
}
