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

    // REQ-FIL-045.3 / TST-FIL-045.3: Multi-pause altitude segmentation, zero phantom g*v*dt_pause jump,
    // trailing 1-sample ascent/descent/bias/fallback continuity, and DEM-only bias carryover.
    @Test
    fun testPauseGapSegmentation_zeroPhantomJumpAndTrailingSingleSampleAndBiasCarryover_TST_FIL_045_3() {
        // 1. Overflow-safe isTimestampPauseGap checks
        org.junit.Assert.assertFalse(ExtendedRtsAltitudeSlopeSmoother.isTimestampPauseGap(1_000L, 6_000L))
        assertTrue(ExtendedRtsAltitudeSlopeSmoother.isTimestampPauseGap(1_000L, 6_001L))
        assertTrue(ExtendedRtsAltitudeSlopeSmoother.isTimestampPauseGap(Long.MIN_VALUE, 0L))
        org.junit.Assert.assertFalse(ExtendedRtsAltitudeSlopeSmoother.isTimestampPauseGap(Long.MAX_VALUE - 1_000L, Long.MAX_VALUE))

        // 2. Multi-pause climb/descent:
        //    Seg 0 (0..59): +20 m climb at 5.0 m/s (+6.7% grade, 500 -> 520 m)
        //    4 s auto-pause (isPauseGap = true, dt = 5 s) -> Seg 1 (60..79): flat 520 m
        //    17 s auto-pause -> Seg 2 (80..99): flat 520 m
        //    134 s auto-pause (resume 3.84 m/s while prior climb was steep!) -> Seg 3 (100..139): -10 m descent (520 -> 510 m)
        //    610 s (> 10 min) manual pause with +12 m baro weather drift during pause -> Seg 4 (140..159): flat 510 m (DEM=510, baro=522)
        //    20 s pause -> Seg 5 (160): trailing 1-sample post-pause segment (must preserve cumulative ascent/descent!)
        val samples = mutableListOf<RawAltitudeSample>()
        var tMs = 20_000_000L

        // Seg 0: 60 s climb 500 -> 520 m (baro offset +5 m)
        for (i in 0 until 60) {
            if (i > 0) tMs += 1_000L
            val trueAlt = 500.0 + (20.0 * i / 59.0)
            samples.add(RawAltitudeSample(tMs, trueAlt + 5.0, trueAlt, 5.0, false))
        }
        val pause4s = samples.size
        tMs += 5_000L
        for (i in 0 until 20) {
            if (i > 0) tMs += 1_000L
            samples.add(RawAltitudeSample(tMs, 525.0, 520.0, 5.0, false, isPauseGap = (i == 0)))
        }
        val pause17s = samples.size
        tMs += 17_000L
        for (i in 0 until 20) {
            if (i > 0) tMs += 1_000L
            samples.add(RawAltitudeSample(tMs, 525.0, 520.0, 4.0, false))
        }
        val pause134s = samples.size
        tMs += 134_000L // 134 s pause resuming at 3.84 m/s
        for (i in 0 until 40) {
            if (i > 0) tMs += 1_000L
            val trueAlt = 520.0 - (10.0 * i / 39.0)
            val spd = if (i == 0) 3.84 else 5.0
            samples.add(RawAltitudeSample(tMs, trueAlt + 5.0, trueAlt, spd, false))
        }
        val pause610s = samples.size
        tMs += 610_000L // > 10 min manual pause with +12 m weather drift in baro (510 -> 522 m raw baro, DEM stays 510 m)
        for (i in 0 until 20) {
            if (i > 0) tMs += 1_000L
            samples.add(RawAltitudeSample(tMs, 522.0, 510.0, 5.0, false))
        }
        val pauseTrailingSingle = samples.size
        tMs += 20_000L // Trailing 1-sample segment after pause
        samples.add(RawAltitudeSample(tMs, 522.0, 510.0, 4.0, false))

        val smoothed = ExtendedRtsAltitudeSlopeSmoother.smooth(samples)
        assertEquals(samples.size, smoothed.size)
        assertTrue(
            "All outputs must be finite and within +-35% slope",
            smoothed.all { it.altitudeM.isFinite() && it.slopePercent.isFinite() && abs(it.slopePercent) <= 35.0 + 1e-6 }
        )

        // Every pause-resume row must emit verticalSpeedMps == 0.0 and zero phantom altitude jump
        for (pIdx in listOf(pause4s, pause17s, pause134s, pause610s, pauseTrailingSingle)) {
            assertEquals("Pause boundary $pIdx must emit 0.0 m/s vertical speed", 0.0, smoothed[pIdx].verticalSpeedMps, 1e-12)
        }
        assertTrue(
            "134 s pause resume altitude must stay near 520 m without g*v*dt_pause jump (was ${smoothed[pause134s].altitudeM})",
            abs(smoothed[pause134s].altitudeM - 520.0) <= 2.5
        )
        assertTrue(
            "610 s manual pause with +12 m baro weather drift must stay near 510 m (was ${smoothed[pause610s].altitudeM})",
            abs(smoothed[pause610s].altitudeM - 510.0) <= 2.5
        )

        // Cumulative ascent/descent must remain monotonic and preserved on trailing 1-sample segment (never reset to 0!)
        for (i in 1 until smoothed.size) {
            assertTrue("Ascent must be non-decreasing at $i", smoothed[i].ascentM >= smoothed[i - 1].ascentM - 1e-9)
            assertTrue("Descent must be non-decreasing at $i", smoothed[i].descentM >= smoothed[i - 1].descentM - 1e-9)
        }
        assertTrue("Active climb (~20 m) preserved on trailing 1-sample tail (was ${smoothed.last().ascentM})", smoothed.last().ascentM >= 16.0)
        assertTrue("Active descent (~10 m) preserved on trailing 1-sample tail (was ${smoothed.last().descentM})", smoothed.last().descentM >= 7.0)
        assertEquals("Trailing 1-sample segment must carry exact prior ascent", smoothed[pauseTrailingSingle - 1].ascentM, smoothed.last().ascentM, 1e-9)
        assertEquals("Trailing 1-sample segment must carry exact prior descent", smoothed[pauseTrailingSingle - 1].descentM, smoothed.last().descentM, 1e-9)

        // 3. DEM-only middle segment forwarding learned +15 m baro bias to baro-only final segment + all-NaN fallback
        val biasHandoffSamples = mutableListOf<RawAltitudeSample>()
        var bt = 30_000_000L
        // Seg 0: baro = 515 m, DEM = 500 m -> learns b_baro ~= +15 m
        for (i in 0 until 40) {
            if (i > 0) bt += 1_000L
            biasHandoffSamples.add(RawAltitudeSample(bt, 515.0, 500.0, 5.0, false))
        }
        // Pause -> Seg 1: DEM-only (no baro) at 500 m
        bt += 30_000L
        for (i in 0 until 15) {
            if (i > 0) bt += 1_000L
            biasHandoffSamples.add(RawAltitudeSample(bt, Double.NaN, 500.0, 5.0, false))
        }
        // Pause -> Seg 2: Baro-only (no DEM) at raw 515 m -> must inherit +15 m bias and stay near 500 m!
        val baroOnlyStart = biasHandoffSamples.size
        bt += 30_000L
        for (i in 0 until 15) {
            if (i > 0) bt += 1_000L
            biasHandoffSamples.add(RawAltitudeSample(bt, 515.0, null, 5.0, false))
        }
        // Pause -> Seg 3: Single baro-only sample (n == 1) -> must also subtract inherited +15 m bias (~500 m)
        val singleBaroOnlyIdx = biasHandoffSamples.size
        bt += 30_000L
        biasHandoffSamples.add(RawAltitudeSample(bt, 515.0, null, 5.0, false))
        // Pause -> Seg 4: All-NaN multi-sample & single-sample segments -> must use fallbackInitialAlt (~500 m, never 0.0!)
        val allNaNMultiIdx = biasHandoffSamples.size
        bt += 30_000L
        biasHandoffSamples.add(RawAltitudeSample(bt, Double.NaN, null, 5.0, false))
        bt += 1_000L
        biasHandoffSamples.add(RawAltitudeSample(bt, Double.NaN, null, 5.0, false))
        val allNaNSingleIdx = biasHandoffSamples.size
        bt += 30_000L
        biasHandoffSamples.add(RawAltitudeSample(bt, Double.NaN, null, 5.0, false))

        val biasSmoothed = ExtendedRtsAltitudeSlopeSmoother.smooth(biasHandoffSamples)
        assertTrue(
            "Baro-only segment after DEM-only bridge must inherit +15 m bias near 500 m (was ${biasSmoothed[baroOnlyStart].altitudeM})",
            abs(biasSmoothed[baroOnlyStart].altitudeM - 500.0) <= 3.0
        )
        assertTrue(
            "Single-sample baro-only segment must inherit +15 m bias near 500 m (was ${biasSmoothed[singleBaroOnlyIdx].altitudeM})",
            abs(biasSmoothed[singleBaroOnlyIdx].altitudeM - 500.0) <= 3.0
        )
        assertTrue(
            "All-NaN multi-sample segment must stay at fallback ~500 m (not 0.0 m; was ${biasSmoothed[allNaNMultiIdx].altitudeM})",
            abs(biasSmoothed[allNaNMultiIdx].altitudeM - 500.0) <= 3.0
        )
        assertTrue(
            "All-NaN single-sample segment must stay at fallback ~500 m (not 0.0 m; was ${biasSmoothed[allNaNSingleIdx].altitudeM})",
            abs(biasSmoothed[allNaNSingleIdx].altitudeM - 500.0) <= 3.0
        )
    }

    // REQ-FIL-048.2 / TST-FIL-048.2: Walking-speed q_g regularization helper bounds, initial >100 m QNH bias seeding,
    // baro-armed 16 m^2 DEM variance + step gating (incl. Gate 3 Watchpoints 1 & 2), and flat-city walk stability.
    @Test
    fun testWalkingSpeedSlopeRegularization_qnhSeedingAndUrbanDemPlateau_tstFil048_2() {
        // 1. Helper bounds & preservation of lowSpeedSlopeGainFactor (TST-FIL-033.1)
        assertEquals(0.0, ExtendedRtsAltitudeSlopeSmoother.walkingSpeedSlopeProcessScale(Double.NaN), 1e-12)
        assertEquals(0.0, ExtendedRtsAltitudeSlopeSmoother.walkingSpeedSlopeProcessScale(Double.POSITIVE_INFINITY), 1e-12)
        assertEquals(0.0, ExtendedRtsAltitudeSlopeSmoother.walkingSpeedSlopeProcessScale(0.2), 1e-12)
        assertEquals(0.04, ExtendedRtsAltitudeSlopeSmoother.walkingSpeedSlopeProcessScale(0.3), 1e-9)
        val scaleAt12 = ExtendedRtsAltitudeSlopeSmoother.walkingSpeedSlopeProcessScale(1.2)
        assertTrue("Scale at 1.2 m/s must damp strongly (was $scaleAt12)", scaleAt12 in 0.14..0.20)
        assertEquals(1.0, ExtendedRtsAltitudeSlopeSmoother.walkingSpeedSlopeProcessScale(2.8), 1e-12)
        assertEquals(1.0, ExtendedRtsAltitudeSlopeSmoother.walkingSpeedSlopeProcessScale(12.0), 1e-12)
        assertEquals(0.25, ExtendedRtsAltitudeSlopeSmoother.lowSpeedSlopeGainFactor(0.75), 1e-9)
        assertEquals(1.0, ExtendedRtsAltitudeSlopeSmoother.lowSpeedSlopeGainFactor(1.2), 1e-12)

        // 2. Flat 1.2 m/s city walk (1000 s) with 108 m initial ISA-vs-MSL QNH offset (-10 m baro vs 98 m ref),
        //    periodic 0.20 m/s baro noise puffs, and a 7-min +12 m urban SRTM DEM plateau (t=420..840)
        val rng = java.util.Random(42L)
        val walkSamples = (0 until 1000).map { t ->
            val puff = if (t % 60 < 10) 0.8 * kotlin.math.sin(2.0 * kotlin.math.PI * (t % 20) / 10.0) else 0.0
            val baro = -10.0 + puff + rng.nextGaussian() * 0.20
            val dem = if (t in 420 until 840) 110.0 else 98.0
            RawAltitudeSample(
                timestampMs = 40_000_000L + t * 1_000L,
                rawAltitudeM = baro,
                referenceAltitudeM = dem,
                smoothedSpeedMps = 1.2,
                isStandstill = false,
                carriageMode = PhoneCarriageMode.WALKING_TRANSITION
            )
        }
        val smoothedWalk = ExtendedRtsAltitudeSlopeSmoother.smooth(walkSamples)
        assertEquals(1000, smoothedWalk.size)
        val maxAbsWalkSlope = smoothedWalk.maxOf { abs(it.slopePercent) }
        assertTrue(
            "Flat city walk with 108 m QNH offset, 0.20 m/s noise, and +12 m DEM plateau must keep |slope| < 5% (max=$maxAbsWalkSlope%)",
            maxAbsWalkSlope < 5.0
        )

        // 3. Gate 3 Watchpoints 1 & 2: noisy tick-0 DEM (+10 m), single-tick baro NaN dropout,
        //    and baro reset step with concurrent DEM must remain bounded without spurious spikes
        val edgeSamples = (0 until 80).map { t ->
            val baro = when {
                t == 20 -> Double.NaN
                t >= 45 -> 455.0 // +15 m baro reset step
                else -> 440.0
            }
            val dem = if (t == 0) 450.0 else 440.0
            RawAltitudeSample(
                timestampMs = 45_000_000L + t * 1_000L,
                rawAltitudeM = baro,
                referenceAltitudeM = dem,
                smoothedSpeedMps = 1.3,
                isStandstill = false,
                carriageMode = PhoneCarriageMode.WALKING_TRANSITION,
                baroResetStep = (t == 45)
            )
        }
        val smoothedEdge = ExtendedRtsAltitudeSlopeSmoother.smooth(edgeSamples)
        assertTrue(
            "Noisy tick-0 DEM, NaN baro dropout, and baro reset step must keep |slope| < 5% (max=${smoothedEdge.maxOf { abs(it.slopePercent) }}%)",
            smoothedEdge.all { it.slopePercent.isFinite() && abs(it.slopePercent) < 5.0 }
        )

        // 4. Delayed tick-1 reference arrival with +121.5 m QNH offset (-24.5 m baro vs +97.0 m ref,
        //    mirroring 2026-11-23_171814) plus transient pocket baro spike on flat DEM must NOT Huber-truncate
        //    the initial anchor or veto flat DEM.
        val delayedQnhSamples = (0 until 60).map { t ->
            val baro = if (t == 25) -16.5 else -24.5 // +8 m single-tick pocket spike on flat DEM
            val ref = if (t == 0) null else 97.0
            RawAltitudeSample(
                timestampMs = 46_000_000L + t * 1_000L,
                rawAltitudeM = baro,
                referenceAltitudeM = ref,
                smoothedSpeedMps = 1.25,
                isStandstill = false,
                carriageMode = PhoneCarriageMode.WALKING_TRANSITION
            )
        }
        val smoothedDelayedQnh = ExtendedRtsAltitudeSlopeSmoother.smooth(delayedQnhSamples)
        assertTrue(
            "Delayed +121.5 m QNH lock must anchor altitude near 97 m from tick 0 (was ${smoothedDelayedQnh[0].altitudeM})",
            abs(smoothedDelayedQnh[0].altitudeM - 97.0) <= 3.0
        )
        assertTrue(
            "Delayed +121.5 m QNH lock + pocket baro spike must keep |slope| < 3% (max=${smoothedDelayedQnh.maxOf { abs(it.slopePercent) }}%)",
            smoothedDelayedQnh.all { it.slopePercent.isFinite() && abs(it.slopePercent) < 3.0 }
        )
    }

    // REQ-FIL-048.3 / TST-FIL-048.3: Genuine sustained +12% hiking climb (1.2 m/s, 100 s) and
    // -10% cycling descent (12.0 m/s, 100 s) converge cleanly in ERTS for both baro-equipped and baro-free phones.
    @Test
    fun testGenuineHikingClimbAndCyclingDescent_baroAndBaroFree_tstFil048_3() {
        for (hasBaro in listOf(true, false)) {
            // 1. Sustained +12% hiking climb at 1.2 m/s (+14.4 m over 100 s / 120 m)
            val hikeSamples = (0 until 100).map { t ->
                val trueAlt = 500.0 + 0.144 * t
                RawAltitudeSample(
                    timestampMs = 50_000_000L + t * 1_000L,
                    rawAltitudeM = if (hasBaro) trueAlt + 30.0 else Double.NaN,
                    referenceAltitudeM = trueAlt,
                    smoothedSpeedMps = 1.2,
                    isStandstill = false,
                    carriageMode = PhoneCarriageMode.WALKING_TRANSITION
                )
            }
            val smoothedHike = ExtendedRtsAltitudeSlopeSmoother.smooth(hikeSamples)
            assertTrue(
                "hasBaro=$hasBaro: ERTS +12% hiking climb at 1.2 m/s must converge to +10..+14% (mid=${smoothedHike[50].slopePercent}%)",
                smoothedHike[50].slopePercent in 10.0..14.0
            )
            assertTrue(
                "hasBaro=$hasBaro: ERTS +12% hiking climb end slope must stay +10..+14% (end=${smoothedHike.last().slopePercent}%)",
                smoothedHike.last().slopePercent in 10.0..14.0
            )

            // 2. Sustained -10% cycling descent at 12.0 m/s (-120 m over 100 s)
            val descentSamples = (0 until 100).map { t ->
                val trueAlt = 1000.0 - 1.2 * t
                RawAltitudeSample(
                    timestampMs = 60_000_000L + t * 1_000L,
                    rawAltitudeM = if (hasBaro) trueAlt + 30.0 else Double.NaN,
                    referenceAltitudeM = trueAlt,
                    smoothedSpeedMps = 12.0,
                    isStandstill = false,
                    carriageMode = PhoneCarriageMode.BIKE_HANDLEBAR
                )
            }
            val smoothedDescent = ExtendedRtsAltitudeSlopeSmoother.smooth(descentSamples)
            assertTrue(
                "hasBaro=$hasBaro: ERTS -10% cycling descent at 12 m/s must converge to -12..-8% (mid=${smoothedDescent[50].slopePercent}%)",
                smoothedDescent[50].slopePercent in -12.0..-8.0
            )
        }
    }

    // REQ-FIL-048.2 / TST-FIL-048.2 (Stage 6 Bounce 1/5): Ref-before-Baro (1-tick & 5-tick delay, 108 m & 140 m QNH,
    // moving & standstill), both-missing-at-tick-0 (108 m QNH), and multi-tick baro NaN dropout epoch alignment.
    @Test
    fun testRefBeforeBaroMissingTick0QnhAndEpochPairedBaroDropout_tstFil048_2() {
        // 1. Ref-before-Baro across (delayTicks, qnhOffsetM, standstillAtArrival)
        val refBeforeBaroConfigs = listOf(
            Triple(1, 108.0, false),
            Triple(5, 108.0, false),
            Triple(1, 140.0, false),
            Triple(5, 140.0, false),
            Triple(5, 140.0, true)
        )
        for ((delayTicks, qnhOffsetM, standstillAtArrival) in refBeforeBaroConfigs) {
            val samples = (0 until 60).map { t ->
                val speed = if (standstillAtArrival && t < 10) 0.0 else 1.25
                RawAltitudeSample(
                    timestampMs = 70_000_000L + t * 1_000L,
                    rawAltitudeM = if (t < delayTicks) Double.NaN else 98.0 - qnhOffsetM,
                    referenceAltitudeM = 98.0,
                    smoothedSpeedMps = speed,
                    isStandstill = speed < 0.3,
                    carriageMode = PhoneCarriageMode.WALKING_TRANSITION
                )
            }
            val smoothed = ExtendedRtsAltitudeSlopeSmoother.smooth(samples)
            val maxAbsSlope = smoothed.maxOf { abs(it.slopePercent) }
            val maxAltErr = smoothed.maxOf { abs(it.altitudeM - 98.0) }
            assertTrue(
                "Ref-before-Baro(delay=$delayTicks, qnh=$qnhOffsetM, stat=$standstillAtArrival): |slope| must stay < 1.0% (max=$maxAbsSlope%)",
                maxAbsSlope < 1.0
            )
            assertTrue(
                "Ref-before-Baro(delay=$delayTicks, qnh=$qnhOffsetM, stat=$standstillAtArrival): alt error must stay <= 0.5 m (max=$maxAltErr m)",
                maxAltErr <= 0.5
            )
        }

        // 2. Both rawAltitudeM and referenceAltitudeM missing at tick 0 (and ticks 0..2), 108 m QNH offset
        for (missingPrefix in listOf(1, 3)) {
            val samples = (0 until 60).map { t ->
                RawAltitudeSample(
                    timestampMs = 75_000_000L + t * 1_000L,
                    rawAltitudeM = if (t < missingPrefix) Double.NaN else -10.0,
                    referenceAltitudeM = if (t < missingPrefix) null else 98.0,
                    smoothedSpeedMps = 1.25,
                    isStandstill = false,
                    carriageMode = PhoneCarriageMode.WALKING_TRANSITION
                )
            }
            val smoothed = ExtendedRtsAltitudeSlopeSmoother.smooth(samples)
            val maxAbsSlope = smoothed.maxOf { abs(it.slopePercent) }
            val maxAltErr = smoothed.maxOf { abs(it.altitudeM - 98.0) }
            assertTrue(
                "Both-missing(prefix=$missingPrefix, qnh=108 m): |slope| must stay < 1.0% (max=$maxAbsSlope%)",
                maxAbsSlope < 1.0
            )
            assertTrue(
                "Both-missing(prefix=$missingPrefix, qnh=108 m): alt error must stay <= 0.5 m (max=$maxAltErr m)",
                maxAltErr <= 0.5
            )
        }

        // 3. Multi-tick baro NaN dropout during a climb: epoch-paired lastRefBaroAltM must reset to NaN during
        //    dropout so post-dropout reference recovery never compares a stale pre-dropout baro against new DEM.
        val dropoutClimb = (0 until 80).map { t ->
            val trueAlt = 500.0 + 0.144 * t // +12% climb at 1.2 m/s
            val baro = if (t in 25..49) Double.NaN else trueAlt + 30.0
            RawAltitudeSample(
                timestampMs = 80_000_000L + t * 1_000L,
                rawAltitudeM = baro,
                referenceAltitudeM = trueAlt,
                smoothedSpeedMps = 1.2,
                isStandstill = false,
                carriageMode = PhoneCarriageMode.WALKING_TRANSITION
            )
        }
        val smoothedDropout = ExtendedRtsAltitudeSlopeSmoother.smooth(dropoutClimb)
        assertTrue(
            "Post-dropout recovery on genuine +12% climb must stay near true altitude (t=55 err=${abs(smoothedDropout[55].altitudeM - (500.0 + 0.144 * 55))})",
            abs(smoothedDropout[55].altitudeM - (500.0 + 0.144 * 55)) <= 2.0
        )
        assertTrue(
            "Post-dropout +12% climb slope must remain smooth and positive (t=60=${smoothedDropout[60].slopePercent}%)",
            smoothedDropout[60].slopePercent in 8.0..15.0
        )
    }
}
