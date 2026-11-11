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
}
