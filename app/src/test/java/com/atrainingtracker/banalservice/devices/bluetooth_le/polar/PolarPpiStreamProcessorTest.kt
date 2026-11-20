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

import com.atrainingtracker.banalservice.devices.ProtocolType
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.devices.RrBeatNormalization
import com.atrainingtracker.banalservice.devices.hrv.RrArtifactFilter
import com.polar.sdk.api.model.PolarPpiData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM unit tests for [PolarPpiStreamProcessor] (`REQ-HRV-051.1` / `TST-HRV-051.1`).
 */
class PolarPpiStreamProcessorTest {

    private fun ppiSample(
        ppi: Int,
        blockerBit: Boolean = false,
        skinContactStatus: Boolean = true,
        skinContactSupported: Boolean = true,
        hr: Int = 72,
        errorEstimate: Int = 12
    ): PolarPpiData.PolarPpiSample {
        return PolarPpiData.PolarPpiSample(
            ppi = ppi,
            errorEstimate = errorEstimate,
            hr = hr,
            blockerBit = blockerBit,
            skinContactStatus = skinContactStatus,
            skinContactSupported = skinContactSupported,
            timeStamp = 0uL
        )
    }

    @Test
    fun testValidSamples_reconstructMonotonicTimestampsTaggedOpticalPpi_TST_HRV_051_1() {
        // REQ-HRV-051.1 / TST-HRV-051.1
        val processor = PolarPpiStreamProcessor()
        val arrival1 = 1_700_000_000_000L
        val events1 = processor.processSamples(
            listOf(ppiSample(800), ppiSample(820), ppiSample(790)),
            packetArrivalMs = arrival1
        )

        assertEquals(3, events1.size)
        assertEquals(arrival1 - 1610L, events1[0].beatTimestampMs)
        assertEquals(arrival1 - 790L, events1[1].beatTimestampMs)
        assertEquals(arrival1, events1[2].beatTimestampMs)
        for (ev in events1) {
            assertTrue("All PPI beats must be tagged isOpticalPpi=true", ev.isOpticalPpi)
            assertEquals(RrBeatEvent.ARTIFACT_NORMAL, ev.artifactFlag)
            assertEquals(0, ev.gapBeats)
            assertEquals(ProtocolType.BLE, ev.protocol)
        }
        assertEquals(790.0, processor.getLastNormalRrMsForTesting(), 0.001)

        val events2 = processor.processSamples(
            listOf(ppiSample(810), ppiSample(805)),
            packetArrivalMs = arrival1 + 1615L
        )
        assertEquals(2, events2.size)
        assertEquals(arrival1 + 810L, events2[0].beatTimestampMs)
        assertEquals(arrival1 + 1615L, events2[1].beatTimestampMs)
        assertTrue(events2.all { it.isOpticalPpi && it.artifactFlag == RrBeatEvent.ARTIFACT_NORMAL })
    }

    @Test
    fun testNonPositivePpiDropped_andHardwareArtifactsStampedGapPreservingTimeline_TST_HRV_051_1() {
        // REQ-HRV-051.1 / TST-HRV-051.1
        val processor = PolarPpiStreamProcessor()
        val arrival = 1_700_000_010_000L
        val events = processor.processSamples(
            samples = listOf(
                ppiSample(0),
                ppiSample(-50),
                ppiSample(800, blockerBit = false, skinContactStatus = true, skinContactSupported = true),
                ppiSample(815, blockerBit = true, skinContactStatus = true, skinContactSupported = true),
                ppiSample(825, blockerBit = false, skinContactStatus = false, skinContactSupported = true),
                ppiSample(810, blockerBit = false, skinContactStatus = false, skinContactSupported = false)
            ),
            packetArrivalMs = arrival
        )

        // Two non-positive samples (0, -50) must be dropped completely
        assertEquals(4, events.size)
        assertEquals(800.0, events[0].rrMs, 0.001)
        assertEquals(RrBeatEvent.ARTIFACT_NORMAL, events[0].artifactFlag)
        assertEquals(0, events[0].gapBeats)

        // blockerBit == true -> ARTIFACT_GAP with gapBeats == 0 (physiological interval)
        assertEquals(815.0, events[1].rrMs, 0.001)
        assertEquals(RrBeatEvent.ARTIFACT_GAP, events[1].artifactFlag)
        assertEquals(0, events[1].gapBeats)
        assertTrue(events[1].isOpticalPpi)

        // skinContactSupported && !skinContactStatus -> ARTIFACT_GAP with gapBeats == 0
        assertEquals(825.0, events[2].rrMs, 0.001)
        assertEquals(RrBeatEvent.ARTIFACT_GAP, events[2].artifactFlag)
        assertEquals(0, events[2].gapBeats)
        assertTrue(events[2].isOpticalPpi)

        // !skinContactSupported && !skinContactStatus -> NOT rejected
        assertEquals(810.0, events[3].rrMs, 0.001)
        assertEquals(RrBeatEvent.ARTIFACT_NORMAL, events[3].artifactFlag)
        assertEquals(0, events[3].gapBeats)
        assertTrue(events[3].isOpticalPpi)

        // Monotonic spacing preserved across the hardware-flagged samples
        assertTrue(events[0].beatTimestampMs < events[1].beatTimestampMs)
        assertTrue(events[1].beatTimestampMs < events[2].beatTimestampMs)
        assertTrue(events[2].beatTimestampMs < events[3].beatTimestampMs)

        // All-non-positive batch emits empty list
        assertTrue(processor.processSamples(listOf(ppiSample(0), ppiSample(-10)), arrival + 500L).isEmpty())
    }

    @Test
    fun testCleanReferenceRrNotMutatedByHardwareArtifact_andGapBeatsAccurateForSubsequentOutOfRange_TST_HRV_051_1() {
        // REQ-HRV-051.1 / TST-HRV-051.1
        val processor = PolarPpiStreamProcessor()
        val arrival = 1_700_000_020_000L

        // Clean beat establishes 800 ms clean reference; next beat has blockerBit=true with 400 ms (physiological);
        // third beat is out-of-range 3200 ms -> gapBeats must use clean 800 ms (3200/800 - 1 = 3), NOT 400 ms (7)!
        val events = processor.processSamples(
            samples = listOf(
                ppiSample(800, blockerBit = false),
                ppiSample(400, blockerBit = true),
                ppiSample(3200, blockerBit = false)
            ),
            packetArrivalMs = arrival
        )

        assertEquals(3, events.size)
        assertEquals(RrBeatEvent.ARTIFACT_NORMAL, events[0].artifactFlag)
        assertEquals(RrBeatEvent.ARTIFACT_GAP, events[1].artifactFlag)
        assertEquals(0, events[1].gapBeats)
        assertEquals(RrBeatEvent.ARTIFACT_GAP, events[2].artifactFlag)
        assertEquals(3, events[2].gapBeats)
        assertEquals(800.0, processor.getLastNormalRrMsForTesting(), 0.001)
    }

    @Test
    fun testHardwareArtifactsSurviveRrArtifactFilterAndNeverEnterCleanRmssdPairs_TST_HRV_051_1() {
        // REQ-HRV-051.1 / TST-HRV-051.1
        val processor = PolarPpiStreamProcessor()
        val filter = RrArtifactFilter()
        val allEvents = mutableListOf<RrBeatEvent>()
        val baseTs = 1_700_000_030_000L

        // Feed 40 optical beats; indices 10, 15, 20, 25 are hardware-flagged despite plausible 800 ms RR
        for (i in 0 until 40) {
            val isBlocked = i == 10 || i == 20
            val isBadContact = i == 15 || i == 25
            val batch = processor.processSamples(
                listOf(
                    ppiSample(
                        ppi = if (i % 2 == 0) 790 else 810,
                        blockerBit = isBlocked,
                        skinContactStatus = !isBadContact,
                        skinContactSupported = true
                    )
                ),
                packetArrivalMs = baseTs + i * 800L
            )
            allEvents.addAll(batch)
        }

        val liveResult = filter.filterLiveCausal(allEvents)
        val bidirResult = filter.filterBidirectionalBeats(allEvents)

        // All 4 hardware-flagged beats must survive as ARTIFACT_GAP in both causal and bidirectional filter modes
        assertEquals(4, liveResult.beats.count { it.artifactFlag == RrBeatEvent.ARTIFACT_GAP })
        assertEquals(4, bidirResult.beats.count { it.artifactFlag == RrBeatEvent.ARTIFACT_GAP })
        assertTrue("Hardware artifacts must elevate artifactRatePercent above 5%", bidirResult.artifactRatePercent > 5.0)
        assertFalse("Flagged beat 10 must be marked isArtifact=true", !bidirResult.beats[10].isArtifact)
        assertFalse("Flagged beat 15 must be marked isArtifact=true", !bidirResult.beats[15].isArtifact)

        // Reset clears internal timestamp/reference state cleanly (Rule 51)
        processor.reset()
        assertEquals(RrBeatNormalization.DEFAULT_REFERENCE_RR_MS, processor.getLastNormalRrMsForTesting(), 0.001)
        val freshArrival = baseTs + 500_000L
        val freshEvents = processor.processSamples(listOf(ppiSample(760)), packetArrivalMs = freshArrival)
        assertEquals(1, freshEvents.size)
        assertEquals(freshArrival, freshEvents[0].beatTimestampMs)
        assertEquals(0, freshEvents[0].gapBeats)
        assertEquals(RrBeatEvent.ARTIFACT_NORMAL, freshEvents[0].artifactFlag)
    }
}
