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

package com.atrainingtracker.banalservice.devices.bluetooth_le

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-JVM verification suite for [CscRevolutionTracker] (REQ-SET-124, TST-SET-126.1).
 */
class CscRevolutionTrackerTest {

    @Test
    fun testWheel_uint16EventTimeRollover_csc1024AndCps2048_computesWithoutDrop() {
        val cscTracker = CscRevolutionTracker(wheelTimeResolution = 1024)
        assertEquals(
            CscRevolutionTracker.WheelStatus.INITIAL_BASELINE,
            cscTracker.processWheel(cumulativeWheelRevolutions = 100L, wheelEventTime = 65000L, calibrationFactor = 2.0).status
        )

        // Rollover 65000 -> 500 yields modular timeDiff = (500 - 65000) & 0xFFFF = 1036 ticks
        val cscRollover = cscTracker.processWheel(cumulativeWheelRevolutions = 102L, wheelEventTime = 500L, calibrationFactor = 2.0)
        val expectedCscSpeed = 2.0 * 2L * 1024 / 1036.0
        assertEquals(CscRevolutionTracker.WheelStatus.COMPUTED, cscRollover.status)
        assertEquals(expectedCscSpeed, cscRollover.speedMps, 1e-6)
        assertEquals(1.0 / expectedCscSpeed, cscRollover.paceSpm!!, 1e-6)
        assertEquals(4.0, cscRollover.distanceMeters, 1e-6)
        assertEquals(2L, cscRollover.revDiff)

        // CPS 2048 Hz rollover 0xFF00 -> 0x0100 yields timeDiff = 512 ticks (= 0.25 s)
        val cpsTracker = CscRevolutionTracker(wheelTimeResolution = 2048)
        assertEquals(
            CscRevolutionTracker.WheelStatus.INITIAL_BASELINE,
            cpsTracker.processWheel(cumulativeWheelRevolutions = 10L, wheelEventTime = 0xFF00L, calibrationFactor = 2.1).status
        )
        val cpsRollover = cpsTracker.processWheel(cumulativeWheelRevolutions = 11L, wheelEventTime = 0x0100L, calibrationFactor = 2.1)
        assertEquals(CscRevolutionTracker.WheelStatus.COMPUTED, cpsRollover.status)
        assertEquals(8.4, cpsRollover.speedMps, 1e-6)
        assertEquals(1.0 / 8.4, cpsRollover.paceSpm!!, 1e-6)
        assertEquals(2.1, cpsRollover.distanceMeters, 1e-6)
        assertEquals(1L, cpsRollover.revDiff)
    }

    @Test
    fun testCrank_uint16EventTimeRollover_computesWithoutDrop() {
        val tracker = CscRevolutionTracker(wheelTimeResolution = 1024)
        assertEquals(
            CscRevolutionTracker.CrankStatus.INITIAL_BASELINE,
            tracker.processCrank(cumulativeCrankRevolutions = 10L, crankEventTime = 65000L).status
        )

        val rollover1 = tracker.processCrank(cumulativeCrankRevolutions = 12L, crankEventTime = 500L)
        assertEquals(CscRevolutionTracker.CrankStatus.COMPUTED, rollover1.status)
        assertEquals((60.0 * 2L * 1024.0) / 1036.0, rollover1.cadenceRpm, 1e-6)
        assertEquals(1036L, rollover1.timeDiff)
        assertEquals(2L, rollover1.revDiff)

        // Second rollover across 0xFF00 -> 0x0100 (512 ticks = 0.5 s at 1024 Hz), +1 rev => 120.0 rpm
        tracker.processCrank(cumulativeCrankRevolutions = 20L, crankEventTime = 0xFF00L)
        val rollover2 = tracker.processCrank(cumulativeCrankRevolutions = 21L, crankEventTime = 0x0100L)
        assertEquals(CscRevolutionTracker.CrankStatus.COMPUTED, rollover2.status)
        assertEquals(120.0, rollover2.cadenceRpm, 1e-6)
        assertEquals(512L, rollover2.timeDiff)
    }

    @Test
    fun testWheel_uint32RevolutionWrap_andCumulativeDistance_preservesUnsignedDelta() {
        val tracker = CscRevolutionTracker(wheelTimeResolution = 1024)
        tracker.processWheel(cumulativeWheelRevolutions = 0xFFFFFFFEL, wheelEventTime = 1000L, calibrationFactor = 2.0)

        // 0xFFFFFFFE -> 2 wraps across UINT32 boundary (+4 revs) in 1024 ticks
        val wrapped = tracker.processWheel(cumulativeWheelRevolutions = 2L, wheelEventTime = 2024L, calibrationFactor = 2.0)
        assertEquals(CscRevolutionTracker.WheelStatus.COMPUTED, wrapped.status)
        assertEquals(4L, wrapped.revDiff)
        assertEquals(8.0, wrapped.speedMps, 1e-6)
        assertEquals(8.0, wrapped.distanceMeters, 1e-6)

        val next = tracker.processWheel(cumulativeWheelRevolutions = 5L, wheelEventTime = 3048L, calibrationFactor = 2.0)
        assertEquals(3L, next.revDiff)
        assertEquals(14.0, next.distanceMeters, 1e-6)
    }

    @Test
    fun testCrank_uint16RevolutionWrap_calculates120Rpm() {
        val tracker = CscRevolutionTracker(wheelTimeResolution = 1024)
        tracker.processCrank(cumulativeCrankRevolutions = 65535L, crankEventTime = 1000L)

        val wrapped = tracker.processCrank(cumulativeCrankRevolutions = 1L, crankEventTime = 2024L)
        assertEquals(CscRevolutionTracker.CrankStatus.COMPUTED, wrapped.status)
        assertEquals(2L, wrapped.revDiff)
        assertEquals(120.0, wrapped.cadenceRpm, 1e-6)
    }

    @Test
    fun testWheel_identicalTimestamps_zeroesSpeedAndNullsPaceOnFourthPacket_thenRecovers() {
        val tracker = CscRevolutionTracker(wheelTimeResolution = 1024)
        tracker.processWheel(cumulativeWheelRevolutions = 50L, wheelEventTime = 65000L, calibrationFactor = 2.0)

        repeat(3) {
            val noUpdate = tracker.processWheel(cumulativeWheelRevolutions = 50L, wheelEventTime = 65000L, calibrationFactor = 2.0)
            assertEquals(CscRevolutionTracker.WheelStatus.NO_UPDATE, noUpdate.status)
        }

        val zeroed = tracker.processWheel(cumulativeWheelRevolutions = 50L, wheelEventTime = 65000L, calibrationFactor = 2.0)
        assertEquals(CscRevolutionTracker.WheelStatus.IDENTICAL_TICK_ZEROED, zeroed.status)
        assertEquals(0.0, zeroed.speedMps, 0.0)
        assertNull(zeroed.paceSpm)

        // Rollover progress resets identical counter and resumes computing
        val recovered = tracker.processWheel(cumulativeWheelRevolutions = 52L, wheelEventTime = 500L, calibrationFactor = 2.0)
        assertEquals(CscRevolutionTracker.WheelStatus.COMPUTED, recovered.status)
        assertEquals(CscRevolutionTracker.WheelStatus.NO_UPDATE,
            tracker.processWheel(cumulativeWheelRevolutions = 52L, wheelEventTime = 500L, calibrationFactor = 2.0).status
        )
    }

    @Test
    fun testCrank_identicalTimestamps_zeroesCadenceOnFourthPacket_thenRecovers() {
        val tracker = CscRevolutionTracker(wheelTimeResolution = 1024)
        tracker.processCrank(cumulativeCrankRevolutions = 10L, crankEventTime = 65000L)

        repeat(3) {
            val noUpdate = tracker.processCrank(cumulativeCrankRevolutions = 10L, crankEventTime = 65000L)
            assertEquals(CscRevolutionTracker.CrankStatus.NO_UPDATE, noUpdate.status)
        }

        val zeroed = tracker.processCrank(cumulativeCrankRevolutions = 10L, crankEventTime = 65000L)
        assertEquals(CscRevolutionTracker.CrankStatus.IDENTICAL_TICK_ZEROED, zeroed.status)
        assertEquals(0.0, zeroed.cadenceRpm, 0.0)

        val recovered = tracker.processCrank(cumulativeCrankRevolutions = 12L, crankEventTime = 500L)
        assertEquals(CscRevolutionTracker.CrankStatus.COMPUTED, recovered.status)
    }

    @Test
    fun testWheel_advancingTimeWithZeroRevolutions_omitsPaceUpdate() {
        val tracker = CscRevolutionTracker(wheelTimeResolution = 1024)
        tracker.processWheel(cumulativeWheelRevolutions = 100L, wheelEventTime = 1000L, calibrationFactor = 2.0)

        val update = tracker.processWheel(cumulativeWheelRevolutions = 100L, wheelEventTime = 2024L, calibrationFactor = 2.0)
        assertEquals(CscRevolutionTracker.WheelStatus.COMPUTED, update.status)
        assertEquals(0.0, update.speedMps, 0.0)
        assertNull("Pace MUST remain null when revDiff == 0 to avoid Infinity", update.paceSpm)
        assertEquals(0L, update.revDiff)
    }

    @Test
    fun testCrank_implausibleCadenceOver250Rpm_isIgnoredAndUpdatesBaseline() {
        val tracker = CscRevolutionTracker(wheelTimeResolution = 1024)
        tracker.processCrank(cumulativeCrankRevolutions = 100L, crankEventTime = 1000L)

        // +50 revs in 1024 ticks = 3000 rpm > 250.0 rpm
        val rejected = tracker.processCrank(cumulativeCrankRevolutions = 150L, crankEventTime = 2024L)
        assertEquals(CscRevolutionTracker.CrankStatus.IGNORED_OUT_OF_RANGE, rejected.status)
        assertEquals(3000.0, rejected.cadenceRpm, 1e-6)

        // Baseline must have updated unconditionally to (150, 2024), so +2 revs in 1024 ticks = 120 rpm
        val subsequent = tracker.processCrank(cumulativeCrankRevolutions = 152L, crankEventTime = 3048L)
        assertEquals(CscRevolutionTracker.CrankStatus.COMPUTED, subsequent.status)
        assertEquals(120.0, subsequent.cadenceRpm, 1e-6)
    }

    @Test
    fun testReset_clearsAllBaselinesAndIdenticalCounters() {
        val tracker = CscRevolutionTracker(wheelTimeResolution = 2048)
        tracker.processWheel(cumulativeWheelRevolutions = 10L, wheelEventTime = 1000L, calibrationFactor = 2.0)
        tracker.processCrank(cumulativeCrankRevolutions = 20L, crankEventTime = 1000L)
        repeat(3) {
            tracker.processWheel(cumulativeWheelRevolutions = 10L, wheelEventTime = 1000L, calibrationFactor = 2.0)
            tracker.processCrank(cumulativeCrankRevolutions = 20L, crankEventTime = 1000L)
        }

        tracker.reset()

        assertEquals(
            CscRevolutionTracker.WheelStatus.INITIAL_BASELINE,
            tracker.processWheel(cumulativeWheelRevolutions = 15L, wheelEventTime = 1000L, calibrationFactor = 2.0).status
        )
        assertEquals(
            CscRevolutionTracker.CrankStatus.INITIAL_BASELINE,
            tracker.processCrank(cumulativeCrankRevolutions = 25L, crankEventTime = 1000L).status
        )
        // Fresh baseline needs a full 4 identical packets before zeroing
        assertEquals(
            CscRevolutionTracker.WheelStatus.NO_UPDATE,
            tracker.processWheel(cumulativeWheelRevolutions = 15L, wheelEventTime = 1000L, calibrationFactor = 2.0).status
        )
        assertEquals(
            CscRevolutionTracker.CrankStatus.NO_UPDATE,
            tracker.processCrank(cumulativeCrankRevolutions = 25L, crankEventTime = 1000L).status
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testConstructorValidation_rejectsNonPositiveWheelResolution() {
        CscRevolutionTracker(wheelTimeResolution = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testConstructorValidation_rejectsNonPositiveMaxIdenticalTicks() {
        CscRevolutionTracker(wheelTimeResolution = 1024, maxIdenticalTicks = 0)
    }
}
