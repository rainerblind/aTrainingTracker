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

import com.atrainingtracker.banalservice.devices.ProtocolType
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [RrArtifactFilter] validating Lipponen & Tarvainen (2019) Mode A (live causal)
 * and Mode B (post-hoc centered) artifact detection and correction pursuant to REQ-TRK-060.2 and TST-TRK-060.2.
 */
class RrArtifactFilterTest {

    private lateinit var filter: RrArtifactFilter

    @Before
    fun setUp() {
        filter = RrArtifactFilter()
    }

    @Test
    fun testCleanSeriesProducesZeroArtifacts() {
        // Steady series with small physiological variation (~800ms)
        val series = MutableList(50) { i -> 800.0 + (i % 5) * 5.0 }
        val result = filter.filterBidirectional(series)

        assertEquals(50, result.totalCount)
        assertEquals(0, result.artifactCount)
        assertEquals(0.0, result.artifactRatePercent, 0.001)
        for (i in series.indices) {
            val beat = result.beats[i]
            assertFalse(beat.isArtifact)
            assertEquals(RrBeatEvent.ARTIFACT_NORMAL, beat.artifactFlag)
            assertEquals(series[i], beat.correctedRrMs, 0.001)
        }
    }

    @Test
    fun testModeBCenteredEctopicBeatDetection() {
        // Normal baseline 800ms, with premature ventricular contraction (PVC): short beat (450ms) + compensatory pause (1150ms) at indices 20, 21
        val series = MutableList(60) { 800.0 }
        series[20] = 450.0
        series[21] = 1150.0

        val result = filter.filterBidirectional(series)

        assertEquals(60, result.totalCount)
        assertTrue(result.artifactCount >= 1)
        val ectopicBeat = result.beats[20]
        assertTrue(ectopicBeat.isArtifact)
        assertEquals(RrBeatEvent.ARTIFACT_ECTOPIC, ectopicBeat.artifactFlag)
        // Corrected interval should match local median (~800ms)
        assertEquals(800.0, ectopicBeat.correctedRrMs, 20.0)
    }

    @Test
    fun testModeBCenteredMissedBeatDetection() {
        // Normal baseline 800ms, missed beat at index 25 (1600ms ~ 2x normal)
        val series = MutableList(60) { 800.0 }
        series[25] = 1600.0

        val result = filter.filterBidirectional(series)

        assertTrue(result.artifactCount >= 1)
        // Missed beat splits into 2 beats, so total becomes 61
        assertEquals(61, result.beats.size)
        val missedBeat = result.beats[25]
        assertTrue(missedBeat.isArtifact)
        assertTrue(
            missedBeat.artifactFlag == RrBeatEvent.ARTIFACT_MISSED ||
            missedBeat.artifactFlag == RrBeatEvent.ARTIFACT_LONG
        )
        assertEquals(800.0, missedBeat.correctedRrMs, 20.0)
    }

    @Test
    fun testMissedBeatSplitIntoMultipleIntervals() {
        // Normal baseline 800ms, missed beat of 1600ms (k = 2)
        val series = MutableList(50) { 800.0 }
        series[20] = 1600.0

        val result = filter.filterBidirectional(series)
        assertEquals(51, result.beats.size)

        val split1 = result.beats[20]
        val split2 = result.beats[21]
        assertTrue(split1.isArtifact)
        assertTrue(split2.isArtifact)
        assertEquals(800.0, split1.correctedRrMs, 10.0)
        assertEquals(800.0, split2.correctedRrMs, 10.0)
        assertEquals(800.0, split1.rawRrMs, 10.0)
        assertEquals(800.0, split2.rawRrMs, 10.0)
    }

    @Test
    fun testAdjacentShortBeatsMerged() {
        // Normal baseline 800ms, two adjacent short beats of 400ms each at indices 20 and 21
        val series = MutableList(50) { 800.0 }
        series[20] = 400.0
        series[21] = 400.0

        val result = filter.filterBidirectional(series)
        // Two short beats merged into 1 beat -> 50 - 1 = 49 beats
        assertEquals(49, result.beats.size)
        val mergedBeat = result.beats[20]
        assertTrue(mergedBeat.isArtifact)
        assertEquals(RrBeatEvent.ARTIFACT_SHORT, mergedBeat.artifactFlag)
        assertEquals(800.0, mergedBeat.rawRrMs, 10.0)
        assertEquals(800.0, mergedBeat.correctedRrMs, 20.0)
    }

    @Test
    fun testModeBCenteredExtraShortBeatDetection() {
        // Normal baseline 800ms, noise spike / extra trigger at index 30 (250ms)
        val series = MutableList(60) { 800.0 }
        series[30] = 250.0

        val result = filter.filterBidirectional(series)

        assertTrue(result.artifactCount >= 1)
        val shortBeat = result.beats[30]
        assertTrue(shortBeat.isArtifact)
        assertEquals(RrBeatEvent.ARTIFACT_SHORT, shortBeat.artifactFlag)
        assertEquals(800.0, shortBeat.correctedRrMs, 20.0)
    }

    @Test
    fun testModeALiveCausalLookaheadFiltering() {
        // Generate stream of beat events with 5-beat lookahead
        val baseTimestamp = 1000000L
        val events = ArrayList<RrBeatEvent>()
        for (i in 0 until 30) {
            val rr = if (i == 15) 450.0 else if (i == 16) 1150.0 else 800.0
            events.add(
                RrBeatEvent(
                    beatTimestampMs = baseTimestamp + i * 800L,
                    rrMs = rr,
                    rawCounter = i,
                    protocol = ProtocolType.SIMULATED
                )
            )
        }

        val causalFiltered = filter.filterLiveCausal(events)
        // With 30 beats and 5 lookahead, 25 beats evaluated
        assertEquals(25, causalFiltered.beats.size)

        // Beat at index 15 should be classified as ectopic
        val ectopicBeat = causalFiltered.beats[15]
        assertTrue(ectopicBeat.isArtifact)
        assertEquals(RrBeatEvent.ARTIFACT_ECTOPIC, ectopicBeat.artifactFlag)
        assertEquals(800.0, ectopicBeat.correctedRrMs, 20.0)
    }

    @Test
    fun testRfGapFlagPreserved() {
        val eventGap = RrBeatEvent(
            beatTimestampMs = 1000L,
            rrMs = 1800.0,
            rawCounter = 5,
            protocol = ProtocolType.ANT_PLUS,
            gapBeats = 1,
            artifactFlag = RrBeatEvent.ARTIFACT_GAP
        )
        val result = filter.filterBidirectionalBeats(listOf(eventGap))
        // gapBeats = 1 expands into gapBeats + 1 = 2 beats
        assertEquals(2, result.beats.size)
        assertEquals(RrBeatEvent.ARTIFACT_GAP, result.beats[0].artifactFlag)
        assertEquals(RrBeatEvent.ARTIFACT_GAP, result.beats[1].artifactFlag)
        assertTrue(result.beats[0].isArtifact)
        assertTrue(result.beats[1].isArtifact)
    }

    @Test
    fun testUnflaggedMultiSecondAndSubMinimumRr_defensivelyFlaggedAndExpandedAsArtifactGap() {
        val baseTs = 1_000_000L
        val beats = MutableList(30) { i ->
            val rr = when (i) {
                12 -> 5196.29 // unflagged multi-second RR (> 2500 ms)
                20 -> 180.0   // unflagged sub-minimum RR (< 250 ms)
                else -> 800.0
            }
            RrBeatEvent(
                beatTimestampMs = baseTs + i * 800L,
                rrMs = rr,
                rawCounter = i,
                protocol = ProtocolType.BLE,
                gapBeats = 0,
                artifactFlag = RrBeatEvent.ARTIFACT_NORMAL
            )
        }

        val bidirectional = filter.filterBidirectionalBeats(beats)
        val gapBeatsBi = bidirectional.beats.filter { it.artifactFlag == RrBeatEvent.ARTIFACT_GAP }
        assertTrue("Multi-second and <250ms RRs must be flagged ARTIFACT_GAP", gapBeatsBi.size >= 7)

        val causal = filter.filterLiveCausal(beats)
        val gapBeatsCausal = causal.beats.filter { it.artifactFlag == RrBeatEvent.ARTIFACT_GAP }
        assertTrue("Live causal filter must also flag unflagged extreme RRs", gapBeatsCausal.size >= 7)
    }

    @Test
    fun testInputArtifactFlagsAndGapBeats_aligned1To1AcrossArtifactsAndHardwareGaps_TST_HRV_048() {
        // REQ-HRV-048 (REQ-HRV-048.1) / TST-HRV-048 (TST-HRV-048.1)
        val baseTs = 1_700_000_000_000L
        val beats = MutableList(120) { i ->
            val rr = when (i) {
                20 -> 275.39   // short/extra outlier amidst ~385 ms running beats
                35 -> 190.0    // merged short pair first half (out-of-range -> GAP; use 260 + 260 below for merged SHORT)
                45 -> 260.0    // merged short pair beat 1
                46 -> 260.0    // merged short pair beat 2
                60 -> 779.30   // missed beat (~2x 385 ms -> k = 2, gapBeats = 1)
                80 -> 5196.29  // unflagged multi-second gap (> 2500 ms)
                95 -> 780.0    // hardware RF ARTIFACT_GAP with gapBeats = 3
                else -> 385.0 + (i % 3 - 1) * 2.0
            }
            val initialFlag = when (i) {
                95 -> RrBeatEvent.ARTIFACT_GAP
                10 -> RrBeatEvent.ARTIFACT_ECTOPIC // stale flag on clean beat must clear to 0
                else -> RrBeatEvent.ARTIFACT_NORMAL
            }
            val initialGap = when (i) {
                95 -> 3
                10 -> 2 // stale gap on clean beat must clear to 0
                else -> 0
            }
            RrBeatEvent(
                beatTimestampMs = baseTs + i * 385L,
                rrMs = rr,
                rawCounter = i,
                protocol = ProtocolType.BLE,
                gapBeats = initialGap,
                artifactFlag = initialFlag
            )
        }
        // Keep index 35 clean so only intended test anomalies exist
        beats[35] = beats[35].copy(rrMs = 385.0)

        val result = filter.filterBidirectionalBeats(beats)

        assertEquals(beats.size, result.inputArtifactFlags.size)
        assertEquals(beats.size, result.inputGapBeats.size)

        // Stale non-GAP flag/gap on clean beat 10 clears to (0, 0)
        assertEquals(RrBeatEvent.ARTIFACT_NORMAL, result.inputArtifactFlags[10])
        assertEquals(0, result.inputGapBeats[10])

        // 275.39 ms short outlier flagged non-zero with gap = 0
        assertTrue(result.inputArtifactFlags[20] != RrBeatEvent.ARTIFACT_NORMAL)
        assertEquals(0, result.inputGapBeats[20])

        // Merged short pair (45, 46): BOTH input indices flagged SHORT with gap = 0
        assertEquals(RrBeatEvent.ARTIFACT_SHORT, result.inputArtifactFlags[45])
        assertEquals(RrBeatEvent.ARTIFACT_SHORT, result.inputArtifactFlags[46])
        assertEquals(0, result.inputGapBeats[45])
        assertEquals(0, result.inputGapBeats[46])

        // 779.30 ms missed beat amidst 385 ms -> MISSED/LONG with k - 1 = 1 missed beat
        assertTrue(
            result.inputArtifactFlags[60] == RrBeatEvent.ARTIFACT_MISSED ||
                result.inputArtifactFlags[60] == RrBeatEvent.ARTIFACT_LONG
        )
        assertEquals(1, result.inputGapBeats[60])

        // 5196.29 ms unflagged physiological gap -> ARTIFACT_GAP with gap >= 5
        assertEquals(RrBeatEvent.ARTIFACT_GAP, result.inputArtifactFlags[80])
        assertTrue("Expected gapBeats >= 5 for 5196 ms gap, was ${result.inputGapBeats[80]}", result.inputGapBeats[80] >= 5)

        // Hardware ARTIFACT_GAP preserves gapBeats = 3
        assertEquals(RrBeatEvent.ARTIFACT_GAP, result.inputArtifactFlags[95])
        assertEquals(3, result.inputGapBeats[95])
    }
}
