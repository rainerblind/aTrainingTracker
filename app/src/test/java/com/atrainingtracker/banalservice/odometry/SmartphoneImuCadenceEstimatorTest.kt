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

package com.atrainingtracker.banalservice.odometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SmartphoneImuCadenceEstimatorTest {

    // TST-FIL-029.2: BIKE_FRONT_POCKET 75 RPM, 1.5 s coasting gate -> 0.0 RPM within 2.0 s, handlebar transition -> null
    @Test
    fun bikeFrontPocket_tracks75RpmCoastsToZeroWithin2sAndSuppressesOnHandlebar_tstFil029_2() {
        val emitted = mutableListOf<Pair<Long, Double?>>()
        var latestStrides = 0
        var simTs = 0L
        val estimator = SmartphoneImuCadenceEstimator(
            onCadence = { emitted.add(simTs to it) },
            onStride = { latestStrides = it }
        )

        val freqHz = 1.25 // 75 RPM
        for (tick in 0..300) { // 0..6000 ms at 50 Hz
            simTs = tick * 20L
            val phase = 2.0 * PI * freqHz * (simTs / 1000.0)
            estimator.onAccSample(
                axMps2 = 2.5 * sin(phase),
                ayMps2 = 9.81 + 1.2 * cos(2.0 * phase),
                azMps2 = 1.8 * cos(phase),
                timestampMs = simTs,
                mode = PhoneCarriageMode.BIKE_FRONT_POCKET,
                sportContext = SportContextHint.CYCLING
            )
            estimator.onGyroSample(
                gxRadPerSec = 2.4 * sin(phase),
                gyRadPerSec = 0.6 * cos(phase),
                gzRadPerSec = 0.4 * sin(phase),
                timestampMs = simTs,
                mode = PhoneCarriageMode.BIKE_FRONT_POCKET,
                sportContext = SportContextHint.CYCLING
            )
        }

        val pedalingRpm = estimator.latestCadence
        assertNotNull("Front pocket pedaling should emit non-null cadence", pedalingRpm)
        assertEquals(75.0, pedalingRpm!!, 2.0)
        assertEquals(0, latestStrides)

        // Coasting begins at 6020 ms: angular velocity < 0.15 rad/s while rough road buzz continues in ACC.
        val coastStartMs = simTs + 20L
        var zeroReachedAtMs: Long? = null
        for (tick in 0..125) { // +2500 ms
            simTs = coastStartMs + tick * 20L
            val tSec = simTs / 1000.0
            estimator.onAccSample(
                axMps2 = 0.45 * sin(2.0 * PI * 11.0 * tSec),
                ayMps2 = 9.81 + 0.50 * cos(2.0 * PI * 14.0 * tSec),
                azMps2 = 0.35 * sin(2.0 * PI * 17.0 * tSec),
                timestampMs = simTs,
                mode = PhoneCarriageMode.BIKE_FRONT_POCKET,
                sportContext = SportContextHint.CYCLING
            )
            estimator.onGyroSample(
                gxRadPerSec = 0.06 * sin(2.0 * PI * 9.0 * tSec),
                gyRadPerSec = 0.05 * cos(2.0 * PI * 13.0 * tSec),
                gzRadPerSec = 0.04 * sin(2.0 * PI * 11.0 * tSec),
                timestampMs = simTs,
                mode = PhoneCarriageMode.BIKE_FRONT_POCKET,
                sportContext = SportContextHint.CYCLING
            )
            if (zeroReachedAtMs == null && estimator.latestCadence == 0.0) {
                zeroReachedAtMs = simTs
            }
        }

        assertNotNull("Coasting energy gate must drop cadence to 0.0 RPM", zeroReachedAtMs)
        assertTrue(
            "Coasting zero must happen within 2.0 s (took ${zeroReachedAtMs!! - coastStartMs} ms)",
            zeroReachedAtMs - coastStartMs <= 2000L
        )
        assertEquals(0.0, estimator.latestCadence!!, 1e-6)

        // Classifier settles from quiet coasting into BIKE_HANDLEBAR -> cleanly suppresses to null.
        simTs += 20L
        estimator.onModeOrSportChanged(
            mode = PhoneCarriageMode.BIKE_HANDLEBAR,
            sportContext = SportContextHint.CYCLING,
            timestampMs = simTs
        )
        assertNull("Settling to BIKE_HANDLEBAR must suppress cadence to null", estimator.latestCadence)
        assertNull("Final emitted cadence must be null after handlebar transition", emitted.last().second)
    }

    // TST-FIL-029.2: BIKE_JERSEY_BACK f/2f harmonic disambiguation + strict prominence gate >= 0.45
    @Test
    fun bikeJerseyBack_disambiguatesFVs2fAndRejectsLowProminenceRoadChatter_tstFil029_2() {
        val estimator = SmartphoneImuCadenceEstimator()

        // 1. 55 RPM (0.9167 Hz) asymmetric pelvic roll + stronger 2f (1.8333 Hz) vertical/pitch bounce.
        val fHz = 55.0 / 60.0
        for (tick in 0..350) { // 7.0 s at 50 Hz
            val tMs = tick * 20L
            val tSec = tMs / 1000.0
            estimator.onGyroSample(
                gxRadPerSec = 0.52 * sin(2.0 * PI * fHz * tSec),
                gyRadPerSec = 0.85 * sin(2.0 * PI * (2.0 * fHz) * tSec + 0.3),
                gzRadPerSec = 0.38 * cos(2.0 * PI * fHz * tSec),
                timestampMs = tMs,
                mode = PhoneCarriageMode.BIKE_JERSEY_BACK,
                sportContext = SportContextHint.CYCLING
            )
        }
        assertNotNull("Jersey back should detect 55 RPM fundamental", estimator.latestCadence)
        assertEquals(55.0, estimator.latestCadence!!, 2.5)

        // 2. Pure single-frequency 90 RPM (1.5 Hz) must NOT halve to 45 RPM.
        estimator.reset()
        val pure90Hz = 1.5
        for (tick in 0..300) {
            val tMs = tick * 20L
            val tSec = tMs / 1000.0
            estimator.onGyroSample(
                gxRadPerSec = 0.70 * sin(2.0 * PI * pure90Hz * tSec),
                gyRadPerSec = 0.25 * cos(2.0 * PI * pure90Hz * tSec),
                gzRadPerSec = 0.40 * sin(2.0 * PI * pure90Hz * tSec + 0.5),
                timestampMs = tMs,
                mode = PhoneCarriageMode.BIKE_JERSEY_BACK,
                sportContext = SportContextHint.CYCLING
            )
        }
        assertNotNull("Pure 90 RPM jersey signal must remain emitted", estimator.latestCadence)
        assertEquals(90.0, estimator.latestCadence!!, 2.0)

        // 3. Multi-frequency incoherent road chatter (R(tau)/R(0) < 0.45) must stay null.
        estimator.reset()
        for (tick in 0..300) {
            val tMs = tick * 20L
            val tSec = tMs / 1000.0
            // Quasi-random deterministic broadband mix inside 0.65-2.2 Hz with incommensurate ratios
            val x = 0.35 * sin(2.0 * PI * 0.73 * tSec) + 0.40 * cos(2.0 * PI * 1.19 * tSec + 0.7) +
                0.38 * sin(2.0 * PI * 1.71 * tSec + 1.9) + 0.42 * cos(2.0 * PI * 2.09 * tSec + 2.4)
            val y = 0.36 * cos(2.0 * PI * 0.89 * tSec + 0.4) - 0.39 * sin(2.0 * PI * 1.47 * tSec + 1.1) +
                0.37 * cos(2.0 * PI * 1.93 * tSec + 2.2)
            val z = 0.34 * sin(2.0 * PI * 1.07 * tSec + 1.5) + 0.35 * sin(2.0 * PI * 1.63 * tSec + 0.2)
            estimator.onGyroSample(
                gxRadPerSec = x,
                gyRadPerSec = y,
                gzRadPerSec = z,
                timestampMs = tMs,
                mode = PhoneCarriageMode.BIKE_JERSEY_BACK,
                sportContext = SportContextHint.CYCLING
            )
        }
        assertNull("Low-prominence incoherent chatter must be rejected (< 0.45)", estimator.latestCadence)
    }

    // TST-FIL-029.2: Strict suppression on BIKE_HANDLEBAR, UNKNOWN, and BIKE WALKING_TRANSITION
    @Test
    fun suppressedBikeAndUnknownModes_neverEmitCadenceOrStrides_tstFil029_2() {
        val emissions = mutableListOf<Double?>()
        var strides = 0
        val estimator = SmartphoneImuCadenceEstimator(
            onCadence = { emissions.add(it) },
            onStride = { strides = it }
        )

        val suppressedCases = listOf(
            PhoneCarriageMode.BIKE_HANDLEBAR to SportContextHint.CYCLING,
            PhoneCarriageMode.UNKNOWN to SportContextHint.CYCLING,
            PhoneCarriageMode.WALKING_TRANSITION to SportContextHint.CYCLING,
            PhoneCarriageMode.BIKE_HANDLEBAR to SportContextHint.UNKNOWN
        )

        var baseTs = 0L
        for ((mode, sport) in suppressedCases) {
            for (tick in 0..250) {
                val tMs = baseTs + tick * 20L
                val tSec = tMs / 1000.0
                // Inject both 1.33 Hz cycling-like vibration and 12 Hz handlebar road chatter.
                estimator.onAccSample(
                    axMps2 = 3.0 * sin(2.0 * PI * 1.333 * tSec) + 5.0 * sin(2.0 * PI * 12.0 * tSec),
                    ayMps2 = 9.81 + 4.0 * cos(2.0 * PI * 18.0 * tSec),
                    azMps2 = 2.0 * sin(2.0 * PI * 25.0 * tSec),
                    timestampMs = tMs,
                    mode = mode,
                    sportContext = sport
                )
                estimator.onGyroSample(
                    gxRadPerSec = 2.0 * sin(2.0 * PI * 1.333 * tSec),
                    gyRadPerSec = 1.5 * cos(2.0 * PI * 12.0 * tSec),
                    gzRadPerSec = 0.8 * sin(2.0 * PI * 1.333 * tSec),
                    timestampMs = tMs,
                    mode = mode,
                    sportContext = sport
                )
                if (tick % 18 == 0) {
                    estimator.onStepDetected(tMs, mode, sport)
                }
                assertNull("Suppressed mode $mode / $sport must remain null", estimator.latestCadence)
            }
            baseTs += 6000L
        }

        assertTrue("No non-null cadence may ever be emitted", emissions.all { it == null })
        assertEquals(0, strides)
        assertEquals(0, estimator.totalStrides)
    }

    // TST-FIL-029.3: RUN_WAIST_HIP + RUN WALKING_TRANSITION at 170 steps/min (85 strides/min)
    @Test
    fun runWaistHip_emits85StridesPerMinAndMonotonicStridesFromOne_tstFil029_3() {
        val stridesObserved = mutableListOf<Int>()
        val estimator = SmartphoneImuCadenceEstimator(
            onStride = { stridesObserved.add(it) }
        )

        val stepsPerMin = 170.0
        val stepHz = stepsPerMin / 60.0 // 2.8333 Hz -> 85.0 strides/min
        val stepPeriodMs = (60_000.0 / stepsPerMin).toLong() // 352 ms -> ~170 steps/min
        var nextStepTsMs = 353L

        for (tick in 0..400) { // 8.0 s
            val tMs = tick * 20L
            val tSec = tMs / 1000.0
            estimator.onAccSample(
                axMps2 = 0.8 * sin(2.0 * PI * (stepHz / 2.0) * tSec),
                ayMps2 = 9.81 + 4.2 * sin(2.0 * PI * stepHz * tSec),
                azMps2 = 1.1 * cos(2.0 * PI * stepHz * tSec),
                timestampMs = tMs,
                mode = PhoneCarriageMode.RUN_WAIST_HIP,
                sportContext = SportContextHint.RUNNING,
                isGyroEnabled = true
            )
            if (tMs >= nextStepTsMs) {
                estimator.onStepDetected(
                    timestampMs = tMs,
                    mode = PhoneCarriageMode.RUN_WAIST_HIP,
                    sportContext = SportContextHint.RUNNING
                )
                nextStepTsMs += stepPeriodMs
            }
        }

        assertNotNull(estimator.latestCadence)
        assertEquals(85.0, estimator.latestCadence!!, 2.0)
        assertTrue("Expected monotonic strides starting at 1", stridesObserved.isNotEmpty())
        assertEquals(1, stridesObserved.first())
        for (i in 1 until stridesObserved.size) {
            assertEquals(stridesObserved[i - 1] + 1, stridesObserved[i])
        }
        assertTrue("Expected ~10-12 strides over 8s, got ${stridesObserved.last()}", stridesObserved.last() in 9..13)

        // Verify WALKING_TRANSITION under RUNNING tracks walking cadence (60 strides/min = 120 steps/min).
        estimator.reset()
        val walkStepHz = 2.0 // 120 steps/min = 60 strides/min
        for (tick in 0..300) { // 6.0 s without hardware step detector (pure ACC waveform)
            val tMs = tick * 20L
            val tSec = tMs / 1000.0
            estimator.onAccSample(
                axMps2 = 0.5 * sin(2.0 * PI * 1.0 * tSec),
                ayMps2 = 9.81 + 3.0 * sin(2.0 * PI * walkStepHz * tSec),
                azMps2 = 0.7 * cos(2.0 * PI * walkStepHz * tSec),
                timestampMs = tMs,
                mode = PhoneCarriageMode.WALKING_TRANSITION,
                sportContext = SportContextHint.RUNNING
            )
        }
        assertNotNull(estimator.latestCadence)
        assertEquals(60.0, estimator.latestCadence!!, 2.0)
        assertTrue("Waveform step fallback must emit strides >= 1", estimator.totalStrides >= 1)
    }

    // TST-FIL-029.3: RUN_BICEP_ARM & RUN_HANDHELD with wrist-glance IMU blanking
    @Test
    fun runBicepAndHandheld_track85StridesPerMinAndBlankGlanceSpikes_tstFil029_3() {
        val bicepEstimator = SmartphoneImuCadenceEstimator()
        val strideHz = 85.0 / 60.0 // 1.4167 Hz arm swing = 85 strides/min
        for (tick in 0..350) { // 7.0 s
            val tMs = tick * 20L
            val tSec = tMs / 1000.0
            bicepEstimator.onAccSample(
                axMps2 = 2.0 * sin(2.0 * PI * strideHz * tSec),
                ayMps2 = 9.81 + 2.5 * cos(2.0 * PI * (2.0 * strideHz) * tSec),
                azMps2 = 1.5 * cos(2.0 * PI * strideHz * tSec),
                timestampMs = tMs,
                mode = PhoneCarriageMode.RUN_BICEP_ARM,
                sportContext = SportContextHint.RUNNING
            )
            bicepEstimator.onGyroSample(
                gxRadPerSec = 2.2 * sin(2.0 * PI * strideHz * tSec),
                gyRadPerSec = 0.8 * cos(2.0 * PI * strideHz * tSec),
                gzRadPerSec = 1.1 * sin(2.0 * PI * strideHz * tSec + 0.4),
                timestampMs = tMs,
                mode = PhoneCarriageMode.RUN_BICEP_ARM,
                sportContext = SportContextHint.RUNNING
            )
        }
        assertNotNull(bicepEstimator.latestCadence)
        assertEquals(85.0, bicepEstimator.latestCadence!!, 2.0)
        assertTrue("Bicep arm waveform detector must emit strides", bicepEstimator.totalStrides in 7..11)

        // RUN_HANDHELD with wrist-glance spike injection from 6.0 s .. 7.8 s
        val cadenceHistory = mutableListOf<Double>()
        val handheldEstimator = SmartphoneImuCadenceEstimator(
            onCadence = { if (it != null) cadenceHistory.add(it) }
        )
        var stridesBeforeGlance = 0
        var stridesRightAfterGlance = 0
        for (tick in 0..550) { // 11.0 s
            val tMs = tick * 20L
            val tSec = tMs / 1000.0
            val inGlance = tMs in 6000L..7800L

            if (inGlance) {
                // Inject violent wrist-raise flick transient that would cause phantom cadence/strides if unblanked.
                handheldEstimator.onAccSample(
                    axMps2 = 18.0 * sin(2.0 * PI * 3.8 * tSec),
                    ayMps2 = -12.0,
                    azMps2 = 15.0 * cos(2.0 * PI * 3.5 * tSec),
                    timestampMs = tMs,
                    mode = PhoneCarriageMode.RUN_HANDHELD,
                    sportContext = SportContextHint.RUNNING,
                    isGyroEnabled = true,
                    isGlanceActive = true
                )
                handheldEstimator.onGyroSample(
                    gxRadPerSec = 9.0 * sin(2.0 * PI * 3.7 * tSec),
                    gyRadPerSec = 7.5 * cos(2.0 * PI * 3.4 * tSec),
                    gzRadPerSec = 6.0 * sin(2.0 * PI * 3.9 * tSec),
                    timestampMs = tMs,
                    mode = PhoneCarriageMode.RUN_HANDHELD,
                    sportContext = SportContextHint.RUNNING,
                    isGlanceActive = true
                )
                if (tMs == 7800L) stridesRightAfterGlance = handheldEstimator.totalStrides
            } else {
                handheldEstimator.onAccSample(
                    axMps2 = 2.2 * sin(2.0 * PI * strideHz * tSec),
                    ayMps2 = 9.81 + 2.4 * cos(2.0 * PI * (2.0 * strideHz) * tSec),
                    azMps2 = 1.6 * cos(2.0 * PI * strideHz * tSec),
                    timestampMs = tMs,
                    mode = PhoneCarriageMode.RUN_HANDHELD,
                    sportContext = SportContextHint.RUNNING,
                    isGyroEnabled = true,
                    isGlanceActive = false
                )
                handheldEstimator.onGyroSample(
                    gxRadPerSec = 2.3 * sin(2.0 * PI * strideHz * tSec),
                    gyRadPerSec = 0.9 * cos(2.0 * PI * strideHz * tSec),
                    gzRadPerSec = 1.2 * sin(2.0 * PI * strideHz * tSec),
                    timestampMs = tMs,
                    mode = PhoneCarriageMode.RUN_HANDHELD,
                    sportContext = SportContextHint.RUNNING,
                    isGlanceActive = false
                )
                if (tMs == 5980L) stridesBeforeGlance = handheldEstimator.totalStrides
            }
        }

        assertEquals(
            "Wrist-flick glance must trigger zero phantom strides",
            stridesBeforeGlance,
            stridesRightAfterGlance
        )
        val postWarmupEmissions = cadenceHistory.drop(1) // allow first 1s startup window
        assertTrue(
            "Every post-warmup emission including across glance must hold 85 +- 2 spm, got $postWarmupEmissions",
            postWarmupEmissions.all { it in 83.0..87.0 }
        )
        assertTrue(
            "Glance window must maintain 1-Hz held cadence emissions (expected >= 8, got ${postWarmupEmissions.size})",
            postWarmupEmissions.size >= 8
        )
    }

    // TST-FIL-029.2: Gyro-absent front pocket recovers from coasting zero, and low-amplitude jersey back drops out on prolonged road noise
    @Test
    fun bikeFrontPocketGyroAbsentResumeAndJerseyBackLowAmpAndNoiseDropout_tstFil029_2() {
        // 1. Gyro-absent BIKE_FRONT_POCKET: pedals -> coasts to 0.0 -> resumes pedaling to ~75 RPM
        val pocket = SmartphoneImuCadenceEstimator()
        var simTs = 0L
        val freqHz = 1.25 // 75 RPM
        for (tick in 0..300) {
            simTs = tick * 20L
            val phase = 2.0 * PI * freqHz * (simTs / 1000.0)
            pocket.onAccSample(
                axMps2 = 2.6 * sin(phase),
                ayMps2 = 9.81 + 1.2 * cos(2.0 * phase),
                azMps2 = 1.9 * cos(phase),
                timestampMs = simTs,
                mode = PhoneCarriageMode.BIKE_FRONT_POCKET,
                sportContext = SportContextHint.CYCLING,
                isGyroEnabled = false
            )
        }
        assertNotNull(pocket.latestCadence)
        assertEquals(75.0, pocket.latestCadence!!, 2.5)

        val coastStartMs = simTs + 20L
        for (tick in 0..100) { // 2.0 s quiet ACC
            simTs = coastStartMs + tick * 20L
            pocket.onAccSample(
                axMps2 = 0.02,
                ayMps2 = 9.81,
                azMps2 = 0.02,
                timestampMs = simTs,
                mode = PhoneCarriageMode.BIKE_FRONT_POCKET,
                sportContext = SportContextHint.CYCLING,
                isGyroEnabled = false
            )
        }
        assertEquals(0.0, pocket.latestCadence!!, 1e-6)

        val resumeStartMs = simTs + 20L
        for (tick in 0..250) { // 5.0 s resume pedaling
            simTs = resumeStartMs + tick * 20L
            val phase = 2.0 * PI * freqHz * (simTs / 1000.0)
            pocket.onAccSample(
                axMps2 = 2.6 * sin(phase),
                ayMps2 = 9.81 + 1.2 * cos(2.0 * phase),
                azMps2 = 1.9 * cos(phase),
                timestampMs = simTs,
                mode = PhoneCarriageMode.BIKE_FRONT_POCKET,
                sportContext = SportContextHint.CYCLING,
                isGyroEnabled = false
            )
        }
        assertEquals(75.0, pocket.latestCadence!!, 2.5)

        // 2. BIKE_JERSEY_BACK: subtle torso sway (RMS ~0.12 rad/s, < 0.25 but >= 0.04) stays active,
        //    then prolonged incoherent chatter (> 3 s) cleanly drops out to null.
        val jersey = SmartphoneImuCadenceEstimator()
        val f80Hz = 80.0 / 60.0
        for (tick in 0..300) { // 6.0 s low-amplitude torso sway
            simTs = tick * 20L
            val tSec = simTs / 1000.0
            jersey.onGyroSample(
                gxRadPerSec = 0.14 * sin(2.0 * PI * f80Hz * tSec),
                gyRadPerSec = 0.08 * cos(2.0 * PI * f80Hz * tSec),
                gzRadPerSec = 0.06 * sin(2.0 * PI * f80Hz * tSec + 0.4),
                timestampMs = simTs,
                mode = PhoneCarriageMode.BIKE_JERSEY_BACK,
                sportContext = SportContextHint.CYCLING
            )
        }
        assertNotNull("Low-amplitude jersey pedaling (>= 0.04 rad/s) must stay active", jersey.latestCadence)
        assertEquals(80.0, jersey.latestCadence!!, 2.5)

        val noiseStartMs = simTs + 20L
        for (tick in 0..250) { // 5.0 s incoherent chatter -> prominence < 0.45 drops out to null
            simTs = noiseStartMs + tick * 20L
            val tSec = simTs / 1000.0
            jersey.onGyroSample(
                gxRadPerSec = 0.35 * sin(2.0 * PI * 0.73 * tSec) + 0.40 * cos(2.0 * PI * 1.19 * tSec + 0.7) +
                    0.38 * sin(2.0 * PI * 1.71 * tSec + 1.9) + 0.42 * cos(2.0 * PI * 2.09 * tSec + 2.4),
                gyRadPerSec = 0.36 * cos(2.0 * PI * 0.89 * tSec + 0.4) - 0.39 * sin(2.0 * PI * 1.47 * tSec + 1.1),
                gzRadPerSec = 0.34 * sin(2.0 * PI * 1.07 * tSec + 1.5) + 0.35 * sin(2.0 * PI * 1.63 * tSec + 0.2),
                timestampMs = simTs,
                mode = PhoneCarriageMode.BIKE_JERSEY_BACK,
                sportContext = SportContextHint.CYCLING
            )
        }
        assertNull("Prolonged low-prominence chatter in jersey back must suppress to null", jersey.latestCadence)
    }

    // TST-FIL-029.3: Gyro-absent indoor run fallback (ACC + TYPE_STEP_DETECTOR across all RUN_* modes)
    @Test
    fun gyroAbsentIndoorFallback_accPlusStepDetectorDelivers85StridesPerMinAndStrides_tstFil029_3() {
        val runModes = listOf(
            PhoneCarriageMode.RUN_WAIST_HIP,
            PhoneCarriageMode.RUN_BICEP_ARM,
            PhoneCarriageMode.RUN_HANDHELD
        )
        for (mode in runModes) {
            val stridesObserved = mutableListOf<Int>()
            val estimator = SmartphoneImuCadenceEstimator(
                onStride = { stridesObserved.add(it) }
            )
            val stepsPerMin = 170.0
            val stepHz = stepsPerMin / 60.0
            var nextStepMs = 353L

            for (tick in 0..350) { // 7.0 s, ZERO gyro samples (isGyroEnabled = false)
                val tMs = tick * 20L
                val tSec = tMs / 1000.0
                estimator.onAccSample(
                    axMps2 = 0.9 * sin(2.0 * PI * (stepHz / 2.0) * tSec),
                    ayMps2 = 9.81 + 3.4 * sin(2.0 * PI * stepHz * tSec),
                    azMps2 = 1.0 * cos(2.0 * PI * stepHz * tSec),
                    timestampMs = tMs,
                    mode = mode,
                    sportContext = SportContextHint.RUNNING,
                    isGyroEnabled = false
                )
                if (tMs >= nextStepMs) {
                    estimator.onStepDetected(
                        timestampMs = tMs,
                        mode = mode,
                        sportContext = SportContextHint.RUNNING
                    )
                    nextStepMs += 353L
                }
            }

            assertNotNull("Mode $mode must produce gyro-absent cadence", estimator.latestCadence)
            assertEquals("Mode $mode gyro-absent cadence", 85.0, estimator.latestCadence!!, 2.0)
            assertTrue("Mode $mode must emit monotonic strides starting at 1", stridesObserved.isNotEmpty())
            assertEquals(1, stridesObserved.first())
            for (i in 1 until stridesObserved.size) {
                assertEquals(stridesObserved[i - 1] + 1, stridesObserved[i])
            }
        }
    }

    // TST-FIL-029.1/.2: Reject NaN/+-Infinity without IIR poisoning, and reject short/invalid ACF windows
    @Test
    fun nonFiniteSamplesDoNotPoisonIirOrRmsAndShortWindowReturnsNull_tstFil029_2() {
        // 1. Direct DSP boundary & non-finite guards
        assertNull(findFundamentalLagResult(minLag = 0, maxLag = 50, minOverlap = 20, availableSamples = 100) { 0.9 })
        assertNull(findFundamentalLagResult(minLag = 10, maxLag = 50, minOverlap = 0, availableSamples = 100) { 0.9 })
        assertNull(findFundamentalLagResult(minLag = 22, maxLag = 77, minOverlap = 35, availableSamples = 56) { 0.9 })
        assertNull(
            findFundamentalLagResult(minLag = 12, maxLag = 42, minOverlap = 45, availableSamples = 120) {
                Double.NaN
            }
        )
        val biquad = BiquadBandPass(fsHz = 50.0, lowCutHz = 0.65, highCutHz = 2.2)
        assertEquals(0.0, biquad.step(Double.NaN), 1e-9)
        assertEquals(0.0, biquad.step(Double.POSITIVE_INFINITY), 1e-9)
        assertTrue(biquad.step(1.5).isFinite())

        // 2. Short window (< minOverlap + minLag = 57 samples -> ~1.12 s) stays null even with valid 75 RPM sine
        val estimator = SmartphoneImuCadenceEstimator()
        val freqHz = 1.25 // 75 RPM
        for (tick in 0..50) { // 1.0 s = 51 samples (< 57)
            val tMs = tick * 20L
            val phase = 2.0 * PI * freqHz * (tMs / 1000.0)
            estimator.onAccSample(
                axMps2 = 2.5 * sin(phase),
                ayMps2 = 9.81 + 1.2 * cos(2.0 * phase),
                azMps2 = 1.8 * cos(phase),
                timestampMs = tMs,
                mode = PhoneCarriageMode.BIKE_FRONT_POCKET,
                sportContext = SportContextHint.CYCLING
            )
            estimator.onGyroSample(
                gxRadPerSec = 2.4 * sin(phase),
                gyRadPerSec = 0.6 * cos(phase),
                gzRadPerSec = 0.4 * sin(phase),
                timestampMs = tMs,
                mode = PhoneCarriageMode.BIKE_FRONT_POCKET,
                sportContext = SportContextHint.CYCLING
            )
        }
        assertNull("Short window (< minOverlap + minLag) must return null", estimator.latestCadence)

        // 3. Inject NaN / +-Infinity spikes at startup and mid-stream; valid 50-Hz pedaling recovers cleanly
        val nonFiniteTriples = listOf(
            Triple(Double.NaN, 9.81, 0.0),
            Triple(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1.0),
            Triple(0.0, Double.NaN, Double.NEGATIVE_INFINITY)
        )
        var tMs = 1020L
        for ((x, y, z) in nonFiniteTriples) {
            estimator.onAccSample(x, y, z, tMs, PhoneCarriageMode.BIKE_FRONT_POCKET, SportContextHint.CYCLING)
            estimator.onGyroSample(x, y, z, tMs, PhoneCarriageMode.BIKE_FRONT_POCKET, SportContextHint.CYCLING)
            tMs += 20L
        }

        for (tick in 55..300) { // through 6.0 s
            val curMs = tick * 20L
            val phase = 2.0 * PI * freqHz * (curMs / 1000.0)
            if (tick == 180) {
                estimator.onAccSample(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, curMs, PhoneCarriageMode.BIKE_FRONT_POCKET, SportContextHint.CYCLING)
                estimator.onGyroSample(Double.NEGATIVE_INFINITY, Double.NaN, Double.POSITIVE_INFINITY, curMs, PhoneCarriageMode.BIKE_FRONT_POCKET, SportContextHint.CYCLING)
            }
            estimator.onAccSample(
                axMps2 = 2.5 * sin(phase),
                ayMps2 = 9.81 + 1.2 * cos(2.0 * phase),
                azMps2 = 1.8 * cos(phase),
                timestampMs = curMs,
                mode = PhoneCarriageMode.BIKE_FRONT_POCKET,
                sportContext = SportContextHint.CYCLING
            )
            estimator.onGyroSample(
                gxRadPerSec = 2.4 * sin(phase),
                gyRadPerSec = 0.6 * cos(phase),
                gzRadPerSec = 0.4 * sin(phase),
                timestampMs = curMs,
                mode = PhoneCarriageMode.BIKE_FRONT_POCKET,
                sportContext = SportContextHint.CYCLING
            )
        }
        assertNotNull("Estimator must recover valid cadence after NaN/Infinity samples", estimator.latestCadence)
        assertEquals(75.0, estimator.latestCadence!!, 2.5)
    }

    // TST-FIL-029.3: Running standstill zeros cadence to 0.0 within STANDSTILL_TIMEOUT_MS with zero phantom strides
    @Test
    fun runningStandstill_zerosCadenceWithinTimeoutWithoutPhantomStrides_tstFil029_3() {
        val estimator = SmartphoneImuCadenceEstimator()
        val stepHz = 170.0 / 60.0 // 85 strides/min
        var nextStepMs = 353L
        var simTs = 0L
        for (tick in 0..300) { // 6.0 s running
            simTs = tick * 20L
            val tSec = simTs / 1000.0
            estimator.onAccSample(
                axMps2 = 0.8 * sin(2.0 * PI * (stepHz / 2.0) * tSec),
                ayMps2 = 9.81 + 4.0 * sin(2.0 * PI * stepHz * tSec),
                azMps2 = 1.0 * cos(2.0 * PI * stepHz * tSec),
                timestampMs = simTs,
                mode = PhoneCarriageMode.RUN_WAIST_HIP,
                sportContext = SportContextHint.RUNNING
            )
            if (simTs >= nextStepMs) {
                estimator.onStepDetected(simTs, PhoneCarriageMode.RUN_WAIST_HIP, SportContextHint.RUNNING)
                nextStepMs += 353L
            }
        }
        assertNotNull(estimator.latestCadence)
        assertEquals(85.0, estimator.latestCadence!!, 2.0)
        val stridesBeforeStop = estimator.totalStrides
        assertTrue(stridesBeforeStop >= 6)

        // Standstill begins -> zero within 2.5 s and zero additional strides
        val stopStartMs = simTs + 20L
        var zeroReachedAtMs: Long? = null
        for (tick in 0..150) { // +3.0 s quiet
            simTs = stopStartMs + tick * 20L
            estimator.onAccSample(
                axMps2 = 0.02,
                ayMps2 = 9.81,
                azMps2 = 0.01,
                timestampMs = simTs,
                mode = PhoneCarriageMode.RUN_WAIST_HIP,
                sportContext = SportContextHint.RUNNING
            )
            if (zeroReachedAtMs == null && estimator.latestCadence == 0.0) {
                zeroReachedAtMs = simTs
            }
        }
        assertNotNull("Running standstill must drop cadence to 0.0", zeroReachedAtMs)
        assertTrue(
            "Running standstill zeroing took ${zeroReachedAtMs!! - stopStartMs} ms",
            zeroReachedAtMs - stopStartMs <= 2500L
        )
        assertEquals(0.0, estimator.latestCadence!!, 1e-6)
        assertEquals("No phantom strides may occur during standstill", stridesBeforeStop, estimator.totalStrides)
    }

    // TST-FIL-029.3: Out-of-order TYPE_STEP_DETECTOR timestamps must be rejected without stride double-counting
    @Test
    fun outOfOrderStepTimestamps_rejectedWithoutDoubleCountingStrides_tstFil029_3() {
        val strides = mutableListOf<Int>()
        val estimator = SmartphoneImuCadenceEstimator(onStride = { strides.add(it) })

        // Two valid steps (353 ms apart) -> 1 stride
        estimator.onStepDetected(353L, PhoneCarriageMode.RUN_WAIST_HIP, SportContextHint.RUNNING)
        estimator.onStepDetected(706L, PhoneCarriageMode.RUN_WAIST_HIP, SportContextHint.RUNNING)
        assertEquals(1, estimator.totalStrides)

        // Out-of-order delayed events (< lastHardwareStepTsMs = 706) and sub-refractory event -> all rejected
        estimator.onStepDetected(500L, PhoneCarriageMode.RUN_WAIST_HIP, SportContextHint.RUNNING)
        estimator.onStepDetected(300L, PhoneCarriageMode.RUN_WAIST_HIP, SportContextHint.RUNNING)
        estimator.onStepDetected(800L, PhoneCarriageMode.RUN_WAIST_HIP, SportContextHint.RUNNING) // < 220 ms refractory
        assertEquals("Out-of-order/sub-refractory steps must not double-count", 1, estimator.totalStrides)

        // Subsequent two genuine steps -> increments cleanly to 2
        estimator.onStepDetected(1059L, PhoneCarriageMode.RUN_WAIST_HIP, SportContextHint.RUNNING)
        estimator.onStepDetected(1412L, PhoneCarriageMode.RUN_WAIST_HIP, SportContextHint.RUNNING)
        assertEquals(listOf(1, 2), strides)
        assertEquals(2, estimator.totalStrides)
    }

    // TST-FIL-029.2: RUN_BICEP_ARM & RUN_HANDHELD with gyro disabled and no TYPE_STEP_DETECTOR still emit CADENCE and STRIDES
    @Test
    fun runningArmModes_gyroAndStepDetectorAbsent_fallBackToImpactAccCadenceAndStrides_tstFil029_2() {
        for (mode in listOf(PhoneCarriageMode.RUN_BICEP_ARM, PhoneCarriageMode.RUN_HANDHELD)) {
            val strides = mutableListOf<Int>()
            val estimator = SmartphoneImuCadenceEstimator(onStride = { strides.add(it) })
            val stepHz = 170.0 / 60.0 // 85 strides/min
            for (tick in 0..300) { // 6.0 s at 50 Hz, ACC only (isGyroEnabled = false, no step detector)
                val ts = tick * 20L
                val tSec = ts / 1000.0
                estimator.onAccSample(
                    axMps2 = 0.7 * sin(2.0 * PI * (stepHz / 2.0) * tSec),
                    ayMps2 = 9.81 + 3.6 * sin(2.0 * PI * stepHz * tSec),
                    azMps2 = 0.9 * cos(2.0 * PI * stepHz * tSec),
                    timestampMs = ts,
                    mode = mode,
                    sportContext = SportContextHint.RUNNING,
                    isGyroEnabled = false,
                    isGlanceActive = false
                )
            }
            assertNotNull("Expected fallback CADENCE for $mode without gyro/step detector", estimator.latestCadence)
            assertEquals("Expected ~85 spm for $mode fallback", 85.0, estimator.latestCadence!!, 2.5)
            assertTrue("Expected >= 5 fallback strides for $mode (got ${estimator.totalStrides})", estimator.totalStrides >= 5)
            assertEquals((1..estimator.totalStrides).toList(), strides)
        }
    }

    // TST-FIL-029.2 (Cold Final Bounce 2): Gyro-absent RUN_BICEP_ARM with strong arm-swing ACC (>= 0.40 m/s^2)
    // must keep step-level refractory (~220 ms, NOT stride-level ~438 ms) over 20s and across RUN_BICEP_ARM -> RUN_WAIST_HIP.
    @Test
    fun runningArmMode_gyroAbsent_20SecondsWarmArmAcc_countsFullStridesAcrossArmToStepTransition_tstFil029_2() {
        val strides = mutableListOf<Int>()
        val estimator = SmartphoneImuCadenceEstimator(onStride = { strides.add(it) })
        val stepHz = 170.0 / 60.0 // 85 strides/min -> ~28.3 strides over 20 s
        var lastTsMs = 0L

        // Phase 1: 20.0 s in RUN_BICEP_ARM with gyro disabled, no step detector, strong arm + vertical ACC
        for (tick in 0..1000) {
            val ts = tick * 20L
            lastTsMs = ts
            val tSec = ts / 1000.0
            estimator.onAccSample(
                axMps2 = 1.6 * sin(2.0 * PI * (stepHz / 2.0) * tSec),
                ayMps2 = 9.81 + 3.8 * sin(2.0 * PI * stepHz * tSec),
                azMps2 = 1.1 * cos(2.0 * PI * (stepHz / 2.0) * tSec),
                timestampMs = ts,
                mode = PhoneCarriageMode.RUN_BICEP_ARM,
                sportContext = SportContextHint.RUNNING,
                isGyroEnabled = false,
                isGlanceActive = false
            )
        }

        assertNotNull("Expected CADENCE in 20s gyro-absent RUN_BICEP_ARM", estimator.latestCadence)
        assertEquals(85.0, estimator.latestCadence!!, 2.5)
        assertTrue(
            "20s at 85 strides/min must yield ~28 strides without 50% refractory drop (got ${estimator.totalStrides})",
            estimator.totalStrides in 26..30
        )

        // Phase 2: Transition cleanly to RUN_WAIST_HIP for 6.0 s (~8.5 additional strides)
        val stridesBeforeTransition = estimator.totalStrides
        for (tick in 1..300) {
            val ts = lastTsMs + tick * 20L
            val tSec = ts / 1000.0
            estimator.onAccSample(
                axMps2 = 1.0 * sin(2.0 * PI * (stepHz / 2.0) * tSec),
                ayMps2 = 9.81 + 3.8 * sin(2.0 * PI * stepHz * tSec),
                azMps2 = 0.8 * cos(2.0 * PI * stepHz * tSec),
                timestampMs = ts,
                mode = PhoneCarriageMode.RUN_WAIST_HIP,
                sportContext = SportContextHint.RUNNING,
                isGyroEnabled = false,
                isGlanceActive = false
            )
        }
        val addedInStepMode = estimator.totalStrides - stridesBeforeTransition
        assertTrue(
            "6s RUN_WAIST_HIP post-transition must add ~8-9 strides cleanly (got $addedInStepMode)",
            addedInStepMode in 7..10
        )
        assertEquals((1..estimator.totalStrides).toList(), strides)
    }

    // TST-FIL-046.2: Sustained unclassified WALKING_TRANSITION (>= 6 s dwell), < 6 s blip rejection,
    // UNKNOWN -> RUNNING continuity, STATIONARY sport-contradiction suppression, and CYCLING/OTHER 0-stride guard.
    @Test
    fun walkingTransition_unknownDwellConfirmationAndCyclingOtherStationaryGuards_tstFil046_2() {
        val strides = mutableListOf<Int>()
        val estimator = SmartphoneImuCadenceEstimator(onStride = { strides.add(it) })
        val walkStepHz = 1.75 // 105 steps/min = 52.5 strides/min

        // 1. Pre-ride 18 s driveway bike-walkout under UNKNOWN must stay SUPPRESSED (0 cadence, 0 strides)
        for (tick in 0..900) { // 0..18000 ms (< 25 s dwell)
            val ts = tick * 20L
            val tSec = ts / 1000.0
            estimator.onAccSample(
                axMps2 = 0.4 * sin(2.0 * PI * (walkStepHz / 2.0) * tSec),
                ayMps2 = 9.81 + 2.4 * sin(2.0 * PI * walkStepHz * tSec),
                azMps2 = 0.5 * cos(2.0 * PI * walkStepHz * tSec),
                timestampMs = ts,
                mode = PhoneCarriageMode.WALKING_TRANSITION,
                sportContext = SportContextHint.UNKNOWN
            )
        }
        assertNull("18 s UNKNOWN driveway walkout must stay suppressed", estimator.latestCadence)
        assertEquals("18 s UNKNOWN driveway walkout must emit 0 strides", 0, estimator.totalStrides)
        assertTrue(!estimator.isRunningOrWalkingActive)

        // Interleave brief BIKE_FRONT_POCKET -> resets dwell timer cleanly
        estimator.onModeOrSportChanged(PhoneCarriageMode.BIKE_FRONT_POCKET, SportContextHint.UNKNOWN, 18_200L)
        assertTrue(!estimator.isRunningOrWalkingActive)

        // 2. Sustained 34 s phone-only walk under UNKNOWN (pure 50-Hz ACC, zero TYPE_STEP_DETECTOR)
        val walkStartMs = 20_000L
        for (tick in 0..1700) { // 34.0 s (>= 25 s dwell + 9 s active walking)
            val ts = walkStartMs + tick * 20L
            val tSec = ts / 1000.0
            estimator.onAccSample(
                axMps2 = 0.4 * sin(2.0 * PI * (walkStepHz / 2.0) * tSec),
                ayMps2 = 9.81 + 2.4 * sin(2.0 * PI * walkStepHz * tSec),
                azMps2 = 0.5 * cos(2.0 * PI * walkStepHz * tSec),
                timestampMs = ts,
                mode = PhoneCarriageMode.WALKING_TRANSITION,
                sportContext = SportContextHint.UNKNOWN
            )
            if (ts - walkStartMs < SmartphoneImuCadenceEstimator.UNKNOWN_WALK_CONFIRM_DWELL_MS) {
                assertEquals("Must stay suppressed prior to 25.0 s dwell at dt=${ts - walkStartMs} ms", 0, estimator.totalStrides)
            }
        }
        assertTrue("Sustained >= 25 s UNKNOWN walk must activate RUNNING_STEP", estimator.isRunningOrWalkingActive)
        assertNotNull("Sustained UNKNOWN walk must emit walking CADENCE", estimator.latestCadence)
        assertTrue("Expected walking cadence in 45..60 spm (got ${estimator.latestCadence})", estimator.latestCadence!! in 45.0..60.0)
        assertTrue("Expected positive walking strides (got ${estimator.totalStrides})", estimator.totalStrides >= 4)

        // 3. Seamless UNKNOWN -> RUNNING transition + STATIONARY pause continuity, then CYCLING contradiction suppresses
        val stridesBeforeRun = estimator.totalStrides
        estimator.onModeOrSportChanged(PhoneCarriageMode.WALKING_TRANSITION, SportContextHint.RUNNING, 54_020L)
        assertTrue(estimator.isRunningOrWalkingActive)
        estimator.onModeOrSportChanged(PhoneCarriageMode.STATIONARY, SportContextHint.RUNNING, 54_500L)
        assertTrue("STATIONARY under RUNNING must retain active walking session", estimator.isRunningOrWalkingActive)
        estimator.onModeOrSportChanged(PhoneCarriageMode.STATIONARY, SportContextHint.CYCLING, 55_000L)
        assertTrue("STATIONARY contradicted by CYCLING must suppress immediately", !estimator.isRunningOrWalkingActive)
        assertNull(estimator.latestCadence)

        // 4. Long 30 s WALKING_TRANSITION under CYCLING and OTHER must emit 0 additional strides
        val stridesBeforeSuppressed = estimator.totalStrides
        assertEquals(stridesBeforeRun, stridesBeforeSuppressed)
        for (ctx in listOf(SportContextHint.CYCLING, SportContextHint.OTHER)) {
            val base = if (ctx == SportContextHint.CYCLING) 60_000L else 100_000L
            for (tick in 0..1500) { // 30 s (> 25 s dwell)
                val ts = base + tick * 20L
                val tSec = ts / 1000.0
                estimator.onAccSample(
                    axMps2 = 0.4 * sin(2.0 * PI * (walkStepHz / 2.0) * tSec),
                    ayMps2 = 9.81 + 2.4 * sin(2.0 * PI * walkStepHz * tSec),
                    azMps2 = 0.5 * cos(2.0 * PI * walkStepHz * tSec),
                    timestampMs = ts,
                    mode = PhoneCarriageMode.WALKING_TRANSITION,
                    sportContext = ctx
                )
                if (tick % 28 == 0) {
                    estimator.onStepDetected(ts, PhoneCarriageMode.WALKING_TRANSITION, ctx)
                }
            }
            assertNull("WALKING_TRANSITION under $ctx must stay null", estimator.latestCadence)
            assertTrue("WALKING_TRANSITION under $ctx must keep isRunningOrWalkingActive false", !estimator.isRunningOrWalkingActive)
        }
        assertEquals("CYCLING and OTHER walking must emit 0 strides", stridesBeforeSuppressed, estimator.totalStrides)

        // 5. Backwards timestamp resets dwell cleanly; reset() clears all state
        estimator.reset()
        assertTrue(!estimator.isRunningOrWalkingActive)
        assertEquals(0, estimator.totalStrides)
        estimator.onAccSample(0.2, 10.5, 0.3, 10_000L, PhoneCarriageMode.WALKING_TRANSITION, SportContextHint.UNKNOWN)
        estimator.onAccSample(0.2, 10.5, 0.3, 4_000L, PhoneCarriageMode.WALKING_TRANSITION, SportContextHint.UNKNOWN)
        assertTrue("Backwards timestamp must reset dwell without promoting", !estimator.isRunningOrWalkingActive)
    }
}
