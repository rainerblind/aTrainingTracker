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

package com.atrainingtracker.trainingtracker.database.hrv

import com.atrainingtracker.banalservice.devices.RrBeatEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * TST-PER-042.2: Unit tests verifying cumulative beat-to-beat timestamp reconstruction,
 * wall-clock drift resynchronization, and gap estimation (REQ-PER-034.2).
 */
class RrBeatTimestampReconstructionTest {

    private lateinit var reconstructor: RrBeatTimestampReconstructor

    @Before
    fun setUp() {
        reconstructor = RrBeatTimestampReconstructor()
    }

    @Test
    fun firstBeat_anchorsToPacketArrivalMs() {
        val arrival = 1_700_000_000_000L
        val beat = reconstructor.reconstruct(arrival, 850.0)

        assertEquals(arrival, beat.beatTimestampMs)
        assertEquals(850.0, beat.rrMs, 0.001)
        assertEquals(0, beat.gapBeats)
        assertEquals(RrBeatEvent.ARTIFACT_NORMAL, beat.artifactFlag)
    }

    @Test
    fun subsequentBeats_accumulateCumulatively_withinDriftThreshold() {
        val start = 1_000_000L
        reconstructor.reconstruct(start, 800.0) // 1_000_000

        val beat2 = reconstructor.reconstruct(start + 810L, 800.0) // 1_000_800
        assertEquals(1_000_800L, beat2.beatTimestampMs)
        assertEquals(0, beat2.gapBeats)

        val beat3 = reconstructor.reconstruct(start + 1600L, 850.0) // 1_001_650
        assertEquals(1_001_650L, beat3.beatTimestampMs)
        assertEquals(0, beat3.gapBeats)
    }

    @Test
    fun largeDriftExceedingThreshold_resynchronizesToWallClock_andEstimatesGapBeats() {
        val start = 1_000_000L
        reconstructor.reconstruct(start, 800.0) // t = 1_000_000

        // Continuous beat
        val beat2 = reconstructor.reconstruct(start + 800L, 800.0) // t = 1_000_800
        assertEquals(1_000_800L, beat2.beatTimestampMs)

        // Drop-out of 4 seconds (drift = 4000 - 800 = 3200ms > 2000ms threshold)
        val dropoutArrival = start + 800L + 4000L // 1_005_600L
        val beat3 = reconstructor.reconstruct(dropoutArrival, 800.0)

        assertEquals(dropoutArrival, beat3.beatTimestampMs)
        assertEquals(RrBeatEvent.ARTIFACT_GAP, beat3.artifactFlag)
        // 4000 / 800 = 5 intervals, so 4 missing gap beats
        assertEquals(4, beat3.gapBeats)
    }

    @Test
    fun timestampsRemainStrictlyMonotonic() {
        val start = 1_000_000L
        var prevTs = 0L

        for (i in 0 until 50) {
            val arrival = start + (i * 800L)
            val beat = reconstructor.reconstruct(arrival, 800.0)
            assertTrue("Timestamp must be strictly increasing", beat.beatTimestampMs > prevTs)
            prevTs = beat.beatTimestampMs
        }
    }
}
