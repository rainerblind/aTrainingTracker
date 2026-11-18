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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.banalservice.odometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Unit and simulation tests for [ExtendedRtsAltitudeSlopeSmoother]
 * (REQ-FIL-017.2, TST-FIL-017.2, ATT-7750).
 */
class ExtendedRtsAltitudeSlopeSmootherTest {

    @Test
    fun testDelayedAnchorConvergence_eliminatesInitialElevationBias() {
        // 120 seconds hill climb (+8% grade = 0.08) at 5 m/s -> true delta h = 5 * 0.08 = 0.4 m/s
        // Start true alt = 500.0 m
        // Barometer has +20m initial offset (starts at 520m, rises by 0.4 m/s)
        // First 60s: NO DEM reference available (null)
        // At t = 60s..120s: DEM reference lock available at true altitude (500 + t * 0.4)
        val samples = mutableListOf<RawAltitudeSample>()
        var tMs = 1000000L
        val speed = 5.0
        val trueSlope = 0.08
        val initialTrueAlt = 500.0
        val baroOffset = 20.0

        for (i in 0 until 120) {
            val tSec = i.toDouble()
            val trueAlt = initialTrueAlt + tSec * speed * trueSlope
            val baroAlt = trueAlt + baroOffset
            val demRef = if (i >= 60) trueAlt else null

            samples.add(
                RawAltitudeSample(
                    timestampMs = tMs,
                    rawAltitudeM = baroAlt,
                    referenceAltitudeM = demRef,
                    smoothedSpeedMps = speed
                )
            )
            tMs += 1000L
        }

        val smoothed = ExtendedRtsAltitudeSlopeSmoother.smooth(samples)
        assertEquals(120, smoothed.size)

        // The RTS backward pass propagates the DEM anchor backward to t=0, eliminating the +20m initial offset
        val initialSmoothedAlt = smoothed[0].altitudeM
        val initialError = abs(initialSmoothedAlt - initialTrueAlt)
        assertTrue(
            "Initial smoothed alt was $initialSmoothedAlt (true $initialTrueAlt, baro was 520.0), error $initialError m",
            initialError < 5.0
        )

        // Verify slope grade converges near 8% (+/- 2%)
        val midSlope = smoothed[60].slopePercent
        assertTrue("Smoothed slope at t=60s was $midSlope%, expected ~8%", midSlope in 6.0..10.0)
    }

    @Test
    fun testStandstillClamping_suppressesFalseClimbAccumulation() {
        // 5-minute coffee stop (300 samples at 1 Hz, v = 0.0 m/s)
        // Barometric pressure drops, simulating a false +8.0 m elevation gain (1 hPa weather change)
        val samples = mutableListOf<RawAltitudeSample>()
        var tMs = 2000000L
        val baseAlt = 400.0

        for (i in 0 until 300) {
            val weatherDrift = (i.toDouble() / 300.0) * 8.0 // +8m drift over 5 mins
            samples.add(
                RawAltitudeSample(
                    timestampMs = tMs,
                    rawAltitudeM = baseAlt + weatherDrift,
                    referenceAltitudeM = null,
                    smoothedSpeedMps = 0.0,
                    isStandstill = true
                )
            )
            tMs += 1000L
        }

        val smoothed = ExtendedRtsAltitudeSlopeSmoother.smooth(samples)
        assertEquals(300, smoothed.size)

        // Standstill clamping: Q_g = 0, dh = 0 -> smoothed slope and ascent accumulation must remain 0.0
        val finalAscent = smoothed.last().ascentM
        assertEquals("False climb accumulated during standstill coffee stop: $finalAscent m", 0.0, finalAscent, 0.01)

        val finalDescent = smoothed.last().descentM
        assertEquals("False descent accumulated during standstill: $finalDescent m", 0.0, finalDescent, 0.01)

        for (pt in smoothed) {
            assertEquals("Slope non-zero during standstill: ${pt.slopePercent}%", 0.0, pt.slopePercent, 0.01)
        }
    }

    // TST-FIL-027.5: 2.0 m anchor hysteresis rejects moving micro-ripples while preserving genuine climbs
    @Test
    fun testHysteresisDeadband_rejectsMicroRipplesWhilePreservingRealClimb_tstFil027_5() {
        // 1. Flat moving ride (5.0 m/s) with +-0.6 m elevation ripples (< 2.0 m hysteresis) -> 0.0 m ascent/descent
        val flatSamples = (0 until 120).map { sec ->
            val ripple = if (sec % 4 < 2) 0.6 else -0.6
            RawAltitudeSample(
                timestampMs = 1_000_000L + sec * 1000L,
                rawAltitudeM = 438.0 + ripple,
                referenceAltitudeM = 438.0 + ripple,
                smoothedSpeedMps = 5.0
            )
        }
        val flatSmoothed = ExtendedRtsAltitudeSlopeSmoother.smooth(flatSamples)
        assertEquals("Flat micro-ripples must accumulate 0.0 m ascent", 0.0, flatSmoothed.last().ascentM, 1e-6)
        assertEquals("Flat micro-ripples must accumulate 0.0 m descent", 0.0, flatSmoothed.last().descentM, 1e-6)

        // 2. Genuine +20.0 m climb over 80s (0..80) at 5.0 m/s -> accumulates >= 17.0 m
        val climbSamples = (0..80).map { sec ->
            val alt = 438.0 + sec * 0.25
            RawAltitudeSample(
                timestampMs = 2_000_000L + sec * 1000L,
                rawAltitudeM = alt,
                referenceAltitudeM = alt,
                smoothedSpeedMps = 5.0
            )
        }
        val climbSmoothed = ExtendedRtsAltitudeSlopeSmoother.smooth(climbSamples)
        assertTrue(
            "Genuine +20 m climb must accumulate >= 17.0 m ascent (was ${climbSmoothed.last().ascentM})",
            climbSmoothed.last().ascentM >= 17.0
        )
    }

    // TST-FIL-028.4: Carriage-aware baro noise scaling dampens pocket pulses vs handlebar fidelity
    @Test
    fun testCarriageModeBaroVarianceScaling_dampensPocketJitterVsHandlebar_tstFil028_4() {
        val baseSamples = (0 until 30).map { sec ->
            val pulse = if (sec in 10..12) 4.0 else 0.0
            RawAltitudeSample(
                timestampMs = 3_000_000L + sec * 1000L,
                rawAltitudeM = 400.0 + pulse,
                referenceAltitudeM = null,
                smoothedSpeedMps = 6.0
            )
        }
        val handlebarSmoothed = ExtendedRtsAltitudeSlopeSmoother.smooth(
            baseSamples.map { it.copy(carriageMode = PhoneCarriageMode.BIKE_HANDLEBAR) }
        )
        val pocketSmoothed = ExtendedRtsAltitudeSlopeSmoother.smooth(
            baseSamples.map { it.copy(carriageMode = PhoneCarriageMode.BIKE_FRONT_POCKET) }
        )

        val handlebarPeakDeviation = handlebarSmoothed.maxOf { abs(it.altitudeM - 400.0) }
        val pocketPeakDeviation = pocketSmoothed.maxOf { abs(it.altitudeM - 400.0) }
        assertTrue(
            "Pocket mode (rBaro=2.0) must attenuate transient pressure pulse stronger than handlebar (pocket=$pocketPeakDeviation, handlebar=$handlebarPeakDeviation)",
            pocketPeakDeviation < handlebarPeakDeviation
        )
    }
}
