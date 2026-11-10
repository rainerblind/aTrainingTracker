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

package com.atrainingtracker.banalservice.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [BarometricAltitudeFilter] verifying non-linear outlier rejection,
 * pocket-compression pulse elimination, and mountain climb fidelity (REQ-TRK-057.1, TST-TRK-057.1, TST-TRK-057.2).
 */
class BarometricAltitudeFilterTest {

    private lateinit var filter: BarometricAltitudeFilter

    @Before
    fun setUp() {
        filter = BarometricAltitudeFilter(
            maxSlewRateMps = 1.2,
            medianWindowSize = 7,
            emaAlpha = 0.18
        )
    }

    /**
     * TST-TRK-057.1: Isolated pocket-compression pulses (e.g. +50m) are rejected by sliding median
     * and clamped by physical slew-rate limiter, preventing phantom elevation gain.
     */
    @Test
    fun testPocketCompressionPulse_isCompletelyRejected() {
        var timeMs = 1000L
        val baseline = 300.0

        // Step 1: Warm up filter at baseline 300m for 10 seconds
        repeat(10) {
            val out = filter.filter(baseline, timeMs)
            assertEquals("Baseline should be 300.0m", baseline, out, 0.001)
            timeMs += 1000L
        }

        // Step 2: Sudden 1-second pocket compression transient +50m (350.0m)
        val spike1Out = filter.filter(baseline + 50.0, timeMs)
        timeMs += 1000L
        assertEquals("Isolated 1s spike must be completely rejected by median window", baseline, spike1Out, 0.5)

        // Step 3: Second consecutive spike sample (2s compression pulse)
        val spike2Out = filter.filter(baseline + 50.0, timeMs)
        timeMs += 1000L
        assertEquals("2s spike must remain within 1m of baseline", baseline, spike2Out, 1.0)

        // Step 4: Pocket releases back to baseline 300m
        repeat(5) {
            val out = filter.filter(baseline, timeMs)
            timeMs += 1000L
            assertEquals("Output must remain stable at baseline", baseline, out, 0.5)
        }
    }

    /**
     * TST-TRK-057.2: High-gradient mountain climb (0.80 m/s = 2,880 m/h) is tracked with zero slope attenuation
     * and final elevation matches ground truth within 0.5m.
     */
    @Test
    fun testHighGradientMountainClimb_preservedWithZeroSlopeAttenuation() {
        var timeMs = 1000L
        var rawAlt = 400.0
        val climbRate = 0.80 // 0.80 m/s = 2,880 m/h

        // Initialize at 400m
        filter.filter(rawAlt, timeMs)

        // Continuous climb for 300 seconds (total climb: 240.0m -> target 640.0m)
        for (i in 1..300) {
            timeMs += 1000L
            rawAlt += climbRate
            filter.filter(rawAlt, timeMs)
        }

        // Level off at summit for 15 seconds to allow filter window to settle
        var summitOut = 0.0
        for (i in 1..15) {
            timeMs += 1000L
            summitOut = filter.filter(rawAlt, timeMs)
        }

        assertEquals("Summit elevation must match ground truth within 0.5m", 640.0, summitOut, 0.5)
    }

    /**
     * TST-TRK-057.1 (Edge Case): Sensor gaps > 10 seconds reinitialize the filter window
     * preventing prolonged slew-rate ramping.
     */
    @Test
    fun testSensorGap_reinitializesBaselineWithoutProlongedLag() {
        filter.filter(200.0, 1000L)
        assertEquals(200.0, filter.getFilteredAltitude(), 0.001)

        // Gap of 30 seconds, new altitude 250m
        val gapOutput = filter.filter(250.0, 31000L)
        assertEquals("Gap > 10s must immediately reinitialize baseline to 250m", 250.0, gapOutput, 0.001)
        assertEquals(250.0, filter.getFilteredAltitude(), 0.001)
    }

    /**
     * NaN / Infinity handling and reset() contract.
     */
    @Test
    fun testInvalidInputsAndReset() {
        filter.filter(500.0, 1000L)
        assertTrue(filter.isInitialized())

        // Invalid inputs return last valid filtered altitude
        val nanOut = filter.filter(Double.NaN, 2000L)
        assertEquals(500.0, nanOut, 0.001)

        val infOut = filter.filter(Double.POSITIVE_INFINITY, 3000L)
        assertEquals(500.0, infOut, 0.001)

        // Reset clears state
        filter.reset()
        assertFalse(filter.isInitialized())
    }
}
