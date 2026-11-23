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

import com.atrainingtracker.banalservice.devices.RrBeatEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.sqrt

/**
 * Pure-JVM unit tests for [HrvRmssdCalculator] verifying documented threshold differentiation
 * (`10` live / `3` post-workout / `1` resting), artifact-adjacent pair exclusion, `minValidPairs < 1`
 * NaN-safety clamping, and raw RR interval parity (`REQ-MIG-061.2`, `TST-MIG-061.2`).
 */
class HrvRmssdCalculatorTest {

    private fun beat(rrMs: Double, isArtifact: Boolean = false, tsMs: Long = 1000L): FilteredRrBeat {
        return FilteredRrBeat(
            beatTimestampMs = tsMs,
            rawRrMs = rrMs,
            correctedRrMs = rrMs,
            artifactFlag = if (isArtifact) RrBeatEvent.ARTIFACT_ECTOPIC else RrBeatEvent.ARTIFACT_NORMAL,
            isArtifact = isArtifact
        )
    }

    @Test
    fun computeBiasFreeRmssd_enforcesCallerThresholds_TST_MIG_061_2() {
        // 5 clean beats = 4 valid successive pairs (diffs: +20, -20, +20, -20 -> rMSSD = 20.0)
        val fiveBeats = listOf(
            beat(800.0, tsMs = 1000L),
            beat(820.0, tsMs = 1820L),
            beat(800.0, tsMs = 2620L),
            beat(820.0, tsMs = 3440L),
            beat(800.0, tsMs = 4240L)
        )

        // Live rolling threshold (10 pairs) -> null
        assertNull(HrvRmssdCalculator.computeBiasFreeRmssd(fiveBeats, HrvRmssdCalculator.LIVE_ROLLING_MIN_VALID_PAIRS))
        // Post-workout centered threshold (3 pairs) -> 20.0
        assertEquals(20.0, HrvRmssdCalculator.computeBiasFreeRmssd(fiveBeats, HrvRmssdCalculator.POST_WORKOUT_MIN_VALID_PAIRS)!!, 1e-6)
        // Morning resting check threshold (1 pair) -> 20.0
        assertEquals(20.0, HrvRmssdCalculator.computeBiasFreeRmssd(fiveBeats, HrvRmssdCalculator.RESTING_CHECK_MIN_VALID_PAIRS)!!, 1e-6)
    }

    @Test
    fun computeBiasFreeRmssd_excludesArtifactAdjacentPairsAndGuardsZeroMinPairs_TST_MIG_061_2() {
        val allArtifacts = listOf(beat(800.0, isArtifact = true), beat(900.0, isArtifact = true))
        // Guardrail 2: minValidPairs = 0 or negative must never produce 0.0 / 0.0 = NaN
        assertNull(HrvRmssdCalculator.computeBiasFreeRmssd(allArtifacts, minValidPairs = 0))
        assertNull(HrvRmssdCalculator.computeBiasFreeRmssd(allArtifacts, minValidPairs = -5))

        // Valid pairs: (800->830: +30) and (810->850: +40); pairs touching artifact 1500 are excluded
        val mixed = listOf(
            beat(800.0),
            beat(830.0),
            beat(1500.0, isArtifact = true),
            beat(810.0),
            beat(850.0)
        )
        val expected = sqrt((30.0 * 30.0 + 40.0 * 40.0) / 2.0)
        val actual = HrvRmssdCalculator.computeBiasFreeRmssd(mixed, minValidPairs = 2)
        assertNotNull(actual)
        assertEquals(expected, actual!!, 1e-6)
        assertNull(HrvRmssdCalculator.computeBiasFreeRmssd(mixed, minValidPairs = 3))
    }

    @Test
    fun computeRawRmssd_computesExpectedRootMeanSquareDifference_TST_MIG_061_2() {
        assertNull(HrvRmssdCalculator.computeRawRmssd(emptyList()))
        assertNull(HrvRmssdCalculator.computeRawRmssd(listOf(800.0), minValidPairs = 0))

        // diffs: +10, -30 -> sqrt((100 + 900) / 2) = sqrt(500)
        val actual = HrvRmssdCalculator.computeRawRmssd(listOf(800.0, 810.0, 780.0))
        assertEquals(sqrt(500.0), actual!!, 1e-6)
    }
}
