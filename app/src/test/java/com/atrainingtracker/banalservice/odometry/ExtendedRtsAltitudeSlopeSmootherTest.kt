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

    // TST-FIL-033.1: Zum Bäcker #58 closed-loop DEM grid step at slow start + 20s standstill
    @Test
    fun testZumBaeckerStartupDemGridStepAndStandstill_noSlopeBlowupOrInitialOvershoot_tstFil033_1() {
        val samples = mutableListOf<RawAltitudeSample>()
        var tMs = 4_000_000L

        for (sec in 0 until 180) {
            val speed: Double
            val isStandstill: Boolean
            val baroAlt: Double
            val demAlt: Double
            when {
                // Slow startup (~5 m travel over t=0..7 s) with integer DEM stepping 438 -> 435 at t=4 s
                sec < 4 -> {
                    speed = 0.8
                    isStandstill = false
                    baroAlt = 437.93 - sec * 0.05
                    demAlt = 438.0
                }
                sec < 8 -> {
                    speed = 0.7
                    isStandstill = false
                    baroAlt = 437.70 - (sec - 4) * 0.05
                    demAlt = 435.0
                }
                // 20 s standstill (t=8..28 s)
                sec < 29 -> {
                    speed = 0.0
                    isStandstill = true
                    baroAlt = 437.50
                    demAlt = if (sec < 18) 435.0 else 438.0
                }
                // Flat roll-away (t=29..49 s) at 438 m
                sec < 50 -> {
                    speed = 4.5
                    isStandstill = false
                    baroAlt = 437.50
                    demAlt = 438.0
                }
                // Genuine +20 m climb (t=50..114 s, 65 s at 5 m/s -> ~6.15% grade)
                sec < 115 -> {
                    val alt = 438.0 + ((sec - 49).toDouble() / 65.0) * 20.0
                    speed = 5.0
                    isStandstill = false
                    baroAlt = alt - 0.5
                    demAlt = kotlin.math.floor(alt)
                }
                // Genuine -20 m descent back to 438 m closed loop (t=115..179 s)
                else -> {
                    val alt = 458.0 - ((sec - 114).toDouble() / 65.0) * 20.0
                    speed = 5.0
                    isStandstill = false
                    baroAlt = alt - 0.5
                    demAlt = kotlin.math.floor(alt)
                }
            }
            samples.add(
                RawAltitudeSample(
                    timestampMs = tMs,
                    rawAltitudeM = baroAlt,
                    referenceAltitudeM = demAlt,
                    smoothedSpeedMps = speed,
                    isStandstill = isStandstill,
                    carriageMode = PhoneCarriageMode.BIKE_FRONT_POCKET
                )
            )
            tMs += 1000L
        }

        val smoothed = ExtendedRtsAltitudeSlopeSmoother.smooth(samples)
        assertEquals(180, smoothed.size)

        val startAlt = smoothed[0].altitudeM
        assertTrue(
            "Initial smoothed altitude must stay within +-2.0 m of 438.0 m (was $startAlt m)",
            abs(startAlt - 438.0) <= 2.0
        )
        assertTrue(
            "All smoothed slopes must remain within +-35% (max=${smoothed.maxOf { abs(it.slopePercent) }}%)",
            smoothed.all { it.slopePercent.isFinite() && abs(it.slopePercent) <= 35.0 + 1e-6 }
        )
        val maxFlatStartSlope = smoothed.take(29).maxOf { abs(it.slopePercent) }
        assertTrue(
            "Flat-start slope must stay < 10% (was $maxFlatStartSlope%)",
            maxFlatStartSlope < 10.0
        )
        val finalAscent = smoothed.last().ascentM
        val finalDescent = smoothed.last().descentM
        assertTrue(
            "Genuine +20 m climb must be preserved (ascent=$finalAscent m)",
            finalAscent >= 16.0
        )
        assertTrue(
            "Closed-loop ascent/descent must match within 3.0 m (ascent=$finalAscent, descent=$finalDescent)",
            abs(finalAscent - finalDescent) <= 3.0
        )
    }

    // TST-FIL-033.1: Steep mountain transient -10 m DEM grid-boundary step stays bounded without +20 m overshoot
    @Test
    fun testSteepMountainTransientDemStepAndStandstill_staysBoundedWithoutOvershoot_tstFil033_1() {
        val samples = (0 until 120).map { sec ->
            val speed = when {
                sec < 8 -> 0.8
                sec < 29 -> 0.0
                else -> 5.0
            }
            val demAlt = if (sec in 4..7) 428.0 else 438.0
            RawAltitudeSample(
                timestampMs = 5_000_000L + sec * 1000L,
                rawAltitudeM = 437.5,
                referenceAltitudeM = demAlt,
                smoothedSpeedMps = speed,
                isStandstill = speed < 0.3,
                carriageMode = PhoneCarriageMode.BIKE_FRONT_POCKET
            )
        }

        val smoothed = ExtendedRtsAltitudeSlopeSmoother.smooth(samples)
        assertEquals(120, smoothed.size)
        assertTrue(
            "Start altitude must stay bounded near 438 m without +20 m overshoot (was ${smoothed[0].altitudeM} m)",
            abs(smoothed[0].altitudeM - 438.0) <= 2.5
        )
        assertTrue(
            "All slopes must stay within +-35% (max=${smoothed.maxOf { abs(it.slopePercent) }}%)",
            smoothed.all { it.slopePercent.isFinite() && abs(it.slopePercent) <= 35.0 + 1e-6 }
        )
        assertTrue(
            "Flat-start slope must stay < 10% (max=${smoothed.take(29).maxOf { abs(it.slopePercent) }}%)",
            smoothed.take(29).all { abs(it.slopePercent) < 10.0 }
        )
    }

    // TST-FIL-033.1: Helper NaN/bounds guards and exact 3x3 Joseph SPD covariance update
    @Test
    fun testHelperGuards_lowSpeedGainClampAndJosephSpd_tstFil033_1() {
        assertEquals(0.0, ExtendedRtsAltitudeSlopeSmoother.lowSpeedSlopeGainFactor(Double.NaN), 1e-12)
        assertEquals(0.0, ExtendedRtsAltitudeSlopeSmoother.lowSpeedSlopeGainFactor(Double.POSITIVE_INFINITY), 1e-12)
        assertEquals(0.0, ExtendedRtsAltitudeSlopeSmoother.lowSpeedSlopeGainFactor(0.2), 1e-12)
        assertEquals(0.25, ExtendedRtsAltitudeSlopeSmoother.lowSpeedSlopeGainFactor(0.75), 1e-9)
        assertEquals(1.0, ExtendedRtsAltitudeSlopeSmoother.lowSpeedSlopeGainFactor(1.2), 1e-12)

        assertEquals(0.0, ExtendedRtsAltitudeSlopeSmoother.clampPhysicalSlope(Double.NaN), 1e-12)
        assertEquals(-0.35, ExtendedRtsAltitudeSlopeSmoother.clampPhysicalSlope(-0.91), 1e-12)
        assertEquals(0.35, ExtendedRtsAltitudeSlopeSmoother.clampPhysicalSlope(0.55), 1e-12)

        // Suboptimal gain k1=0.0 on correlated P must remain strictly symmetric and SPD
        val p = arrayOf(
            doubleArrayOf(4.0, 0.05, -0.5),
            doubleArrayOf(0.05, 0.0025, 0.01),
            doubleArrayOf(-0.5, 0.01, 40.0)
        )
        ExtendedRtsAltitudeSlopeSmoother.josephCovarianceUpdate3x3(
            p = p,
            kVec = doubleArrayOf(0.5, 0.0, -0.0625),
            hVec = doubleArrayOf(1.0, 0.0, 0.0),
            rVar = 4.0
        )
        for (i in 0 until 3) {
            assertTrue("Diagonal p[$i][$i] must stay positive", p[i][i] > 0.0)
            for (j in 0 until 3) {
                assertEquals("Joseph update must be symmetric at ($i,$j)", p[i][j], p[j][i], 1e-12)
            }
        }
        val det = p[0][0] * (p[1][1] * p[2][2] - p[1][2] * p[2][1]) -
            p[0][1] * (p[1][0] * p[2][2] - p[1][2] * p[2][0]) +
            p[0][2] * (p[1][0] * p[2][1] - p[1][1] * p[2][0])
        assertTrue("Joseph updated covariance must be strictly positive-definite (det=$det)", det > 0.0)
    }

    // TST-FIL-033.3: Barometer-free DEM/GPS-only stream, leading-null rows, and n==1 non-finite guard
    @Test
    fun testBarometerFreeDemOnlyLeadingNullAndSingleSample_finiteAndPhysicallyBounded_tstFil033_3() {
        // 1. Barometer-free phone (rawAltitudeM = Double.NaN, DEM/GPS climb +10% then descent -10%)
        val baroFreeSamples = (0 until 120).map { sec ->
            val speed = if (sec < 60) 4.0 else 6.0
            val alt = if (sec < 60) {
                500.0 + sec * 0.4 // +10% at 4 m/s
            } else {
                500.0 + 59 * 0.4 - (sec - 59) * 0.6 // -10% at 6 m/s
            }
            RawAltitudeSample(
                timestampMs = 6_000_000L + sec * 1000L,
                rawAltitudeM = Double.NaN,
                referenceAltitudeM = alt,
                smoothedSpeedMps = speed
            )
        }
        val baroFreeSmoothed = ExtendedRtsAltitudeSlopeSmoother.smooth(baroFreeSamples)
        assertEquals(120, baroFreeSmoothed.size)
        assertTrue(
            "Barometer-free outputs must all be finite and physically bounded",
            baroFreeSmoothed.all {
                it.altitudeM.isFinite() &&
                    it.slopePercent.isFinite() &&
                    abs(it.slopePercent) <= 35.0 + 1e-6 &&
                    it.ascentM.isFinite() &&
                    it.descentM.isFinite()
            }
        )
        assertTrue(
            "Barometer-free climb must learn positive slope (mid-climb=${baroFreeSmoothed[35].slopePercent}%)",
            baroFreeSmoothed[35].slopePercent in 5.0..15.0
        )
        assertTrue(
            "Barometer-free descent must learn negative slope (mid-descent=${baroFreeSmoothed[95].slopePercent}%)",
            baroFreeSmoothed[95].slopePercent in -15.0..-5.0
        )
        assertTrue("Barometer-free climb must accumulate ascent (was ${baroFreeSmoothed.last().ascentM})", baroFreeSmoothed.last().ascentM >= 15.0)
        assertTrue("Barometer-free descent must accumulate descent (was ${baroFreeSmoothed.last().descentM})", baroFreeSmoothed.last().descentM >= 25.0)

        // 2. Leading-null rows must NOT poison state with NaN or 0.0 m sea-level spike
        val leadingNullSamples = (0 until 20).map { sec ->
            val hasData = sec >= 4
            RawAltitudeSample(
                timestampMs = 7_000_000L + sec * 1000L,
                rawAltitudeM = if (hasData) 438.0 else Double.NaN,
                referenceAltitudeM = if (hasData) 438.0 else null,
                smoothedSpeedMps = if (hasData) 5.0 else 0.0,
                isStandstill = !hasData
            )
        }
        val leadingNullSmoothed = ExtendedRtsAltitudeSlopeSmoother.smooth(leadingNullSamples)
        assertTrue(
            "Leading-null workout must produce finite altitudes near 438 m (min=${leadingNullSmoothed.minOf { it.altitudeM }})",
            leadingNullSmoothed.all { it.altitudeM.isFinite() && abs(it.altitudeM - 438.0) <= 2.0 }
        )

        // 3. Single-sample n==1 with non-finite altitude must emit finite 0.0 (never NaN)
        val singleNaN = ExtendedRtsAltitudeSlopeSmoother.smooth(
            listOf(
                RawAltitudeSample(
                    timestampMs = 8_000_000L,
                    rawAltitudeM = Double.NaN,
                    referenceAltitudeM = null,
                    smoothedSpeedMps = Double.NaN
                )
            )
        )
        assertEquals(1, singleNaN.size)
        assertEquals(0.0, singleNaN[0].altitudeM, 1e-12)
        assertEquals(0.0, singleNaN[0].slopePercent, 1e-12)
    }

    // REQ-STB-025.4 / TST-STB-025.4: Direct ERTS smoother absorbs sustained +20.2 m barometer reset step into b_baro without slope spike
    @Test
    fun testSustainedBaroResetStep_absorbedIntoBiasWithoutSlopeSpike_TST_STB_025_4() {
        val baseTs = 9_000_000L
        val samples = (0 until 80).map { sec ->
            val ts = if (sec < 40) baseTs + sec * 1000L else baseTs + (sec + 3) * 1000L
            val baro = if (sec < 40) 430.6 else 450.8 // +20.2 m uncalibrated barometer step after 4 s restart gap
            RawAltitudeSample(
                timestampMs = ts,
                rawAltitudeM = baro,
                referenceAltitudeM = 430.6,
                smoothedSpeedMps = 3.4,
                isStandstill = false,
                carriageMode = PhoneCarriageMode.RUN_WAIST_HIP
            )
        }

        val detected = ExtendedRtsAltitudeSlopeSmoother.detectDirectBaroResetSteps(samples)
        assertTrue("Direct smoother must detect sustained +20.2 m reset step at idx=40", detected.contains(40))

        val smoothed = ExtendedRtsAltitudeSlopeSmoother.smooth(samples)
        val maxAbsSlope = smoothed.maxOf { abs(it.slopePercent) }
        val maxAltDev = smoothed.maxOf { abs(it.altitudeM - 430.6) }

        assertTrue(
            "ERTS slope across sustained +20.2 m barometer step must stay < 5% (got $maxAbsSlope%)",
            maxAbsSlope < 5.0
        )
        assertTrue(
            "ERTS altitude across sustained +20.2 m barometer step must remain flat near 430.6 m (maxDev=$maxAltDev m)",
            maxAltDev < 3.0
        )
    }

    // REQ-FIL-040.4 / TST-FIL-040.4: Low-speed end-of-ride braking deceleration prevents -11% slope plunge
    // on flat road while preserving real -8% downhill braking and guarding sub-100ms dt jitter / pause gaps.
    @Test
    fun testLowSpeedBrakingProtection_eliminatesFlatBrakingPlungeAndPreservesDownhill_TST_FIL_040_4() {
        val baseTs = 10_000_000L

        // 1. Flat road (DEM = 438.0 m): 20 s steady cruise at 5.5 m/s, then 15 s braking 5.5 -> 0.6 m/s
        //    with simultaneous -2.0 m barometer decompression drop, followed by 5 s standstill.
        val flatBrakingSamples = (0 until 40).map { sec ->
            val speed = when {
                sec < 20 -> 5.5
                sec < 35 -> 5.5 - (4.9 * (sec - 19) / 15.0)
                else -> 0.0
            }
            val baroDrop = when {
                sec < 20 -> 0.0
                sec < 35 -> -2.0 * ((sec - 19) / 15.0)
                else -> -2.0
            }
            RawAltitudeSample(
                timestampMs = baseTs + sec * 1000L,
                rawAltitudeM = 438.0 + baroDrop,
                referenceAltitudeM = 438.0,
                smoothedSpeedMps = speed,
                isStandstill = speed < 0.3,
                carriageMode = PhoneCarriageMode.BIKE_FRONT_POCKET
            )
        }
        val flatSmoothed = ExtendedRtsAltitudeSlopeSmoother.smooth(flatBrakingSamples)
        val brakingWindow = flatSmoothed.subList(20, 35)
        val maxAbsBrakingSlope = brakingWindow.maxOf { abs(it.slopePercent) }
        val maxAltDeviation = brakingWindow.maxOf { abs(it.altitudeM - 438.0) }
        assertTrue(
            "Flat braking slope must stay within |SLOPE| <= 4.0% (no -11% dip; got $maxAbsBrakingSlope%)",
            maxAbsBrakingSlope <= 4.0
        )
        assertTrue(
            "Flat braking altitude must remain anchored near 438.0 m (maxDev=$maxAltDeviation m)",
            maxAltDeviation <= 1.5
        )

        // 2. Real -8% downhill braking (5.5 -> 1.0 m/s): must preserve smooth negative slope (not clamped to 0%)
        var downhillAlt = 500.0
        val downhillSamples = (0 until 35).map { sec ->
            val speed = if (sec < 20) 5.5 else 5.5 - (4.5 * (sec - 19) / 15.0)
            if (sec > 0) downhillAlt += -0.08 * speed
            RawAltitudeSample(
                timestampMs = baseTs + 100_000L + sec * 1000L,
                rawAltitudeM = downhillAlt,
                referenceAltitudeM = downhillAlt,
                smoothedSpeedMps = speed,
                isStandstill = false,
                carriageMode = PhoneCarriageMode.BIKE_HANDLEBAR
            )
        }
        val downhillSmoothed = ExtendedRtsAltitudeSlopeSmoother.smooth(downhillSamples)
        val midDownhillBrakingSlope = downhillSmoothed[28].slopePercent
        assertTrue(
            "Real -8% downhill braking must preserve smooth negative slope in -11%..-3% (got $midDownhillBrakingSlope%)",
            midDownhillBrakingSlope in -11.0..-3.0
        )

        // 3. Sub-100ms duplicate timestamp jitter & >5 s pause gap remain finite and well-behaved
        val jitterAndPauseSamples = listOf(
            RawAltitudeSample(baseTs, 440.0, 440.0, 5.0, false),
            RawAltitudeSample(baseTs + 1L, 440.0, 440.0, 4.9, false), // dt = 0.001 s -> accel ignored
            RawAltitudeSample(baseTs + 1_000L, 440.4, 440.4, 5.0, false),
            RawAltitudeSample(baseTs + 12_000L, 440.4, 440.4, 2.0, false), // 11 s pause gap -> clean decay
            RawAltitudeSample(baseTs + 13_000L, 440.5, 440.5, 2.0, false)
        )
        val jitterSmoothed = ExtendedRtsAltitudeSlopeSmoother.smooth(jitterAndPauseSamples)
        assertTrue(
            "Sub-100ms jitter and >5 s pause gap must produce finite smooth outputs",
            jitterSmoothed.all { it.altitudeM.isFinite() && it.slopePercent.isFinite() }
        )
    }
}
