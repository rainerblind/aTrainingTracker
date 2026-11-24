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
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.banalservice.odometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Unit tests verifying 50-Hz [PhoneCarriageClassifier] classification accuracy across all
 * 9 [PhoneCarriageMode] states, transition hysteresis, glance detection, `Zum Bäcker`
 * dismount timeline, and missing-sensor fallback (REQ-FIL-028.1, TST-FIL-028.1, ATT-8890).
 */
// Linked test specs: TST-FIL-028, TST-FIL-034, TST-FIL-035, TST-FIL-039, TST-FIL-043, TST-FIL-044
class PhoneCarriageClassifierTest {

    private fun quaternionFromEulerXDeg(tiltDeg: Double): FloatArray {
        val halfRad = Math.toRadians(tiltDeg) * 0.5
        return floatArrayOf(sin(halfRad).toFloat(), 0f, 0f, cos(halfRad).toFloat())
    }

    private fun quaternionFromTiltAndRollDeg(pitchDeg: Double, rollDeg: Double): FloatArray {
        val hx = Math.toRadians(pitchDeg) * 0.5
        val hy = Math.toRadians(rollDeg) * 0.5
        val cx = cos(hx); val sx = sin(hx)
        val cy = cos(hy); val sy = sin(hy)
        return floatArrayOf(
            (sx * cy).toFloat(),
            (cx * sy).toFloat(),
            (-sx * sy).toFloat(),
            (cx * cy).toFloat()
        )
    }

    private fun feedWindow50Hz(
        classifier: PhoneCarriageClassifier,
        startMs: Long,
        durationMs: Long,
        basePitchDeg: Double,
        pitchAmpDeg: Double,
        oscFreqHz: Double,
        yawAmpRadPerSec: Double,
        baroAmpM: Double,
        speedMps: Double,
        wheelSpeedMps: Double? = null,
        stepFreqHz: Double? = null,
        includeRotVec: Boolean = true,
        includeBaro: Boolean = true
    ): PhoneCarriageMode {
        val dtMs = 20L
        var lastStepEmitMs = startMs - 1000L
        val stepIntervalMs = stepFreqHz?.let { (1000.0 / it).toLong().coerceAtLeast(100L) }
        var t = startMs
        while (t <= startMs + durationMs) {
            val sec = (t - startMs) / 1000.0
            val phase = 2.0 * PI * oscFreqHz * sec
            if (includeRotVec) {
                val pitch = basePitchDeg + pitchAmpDeg * sin(phase)
                classifier.ingestRotationVector(quaternionFromEulerXDeg(pitch), t)
            }
            val yawRate = yawAmpRadPerSec * sin(phase)
            classifier.ingestGyro(yawRate, kotlin.math.abs(yawRate), t)

            if (t % 200L == 0L && includeBaro) {
                // Barometric pocket pumping oscillates on 1-Hz cadence when baroAmpM > 0
                val baroAlt = 300.0 + baroAmpM * sin(PI * (t / 1000.0))
                classifier.ingestBarometerAltitude(baroAlt, t)
            }
            if (t % 1000L == 0L) {
                classifier.ingestGnssSpeed(speedMps, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                if (wheelSpeedMps != null) {
                    classifier.ingestWheelSpeed(wheelSpeedMps, t)
                }
                classifier.classify(t)
            }
            if (stepIntervalMs != null && t - lastStepEmitMs >= stepIntervalMs) {
                classifier.ingestStepEvent(t)
                lastStepEmitMs = t
            }
            t += dtMs
        }
        return classifier.classify(startMs + durationMs)
    }

    @Test
    fun testInitialState_startsUnknownAndMapsAllCodesCleanly() {
        val classifier = PhoneCarriageClassifier()
        assertEquals(PhoneCarriageMode.UNKNOWN, classifier.currentMode)
        assertEquals(PhoneCarriageMode.UNKNOWN, PhoneCarriageMode.fromCode(-1))
        assertEquals(PhoneCarriageMode.UNKNOWN, PhoneCarriageMode.fromCode(999))
        assertEquals(PhoneCarriageMode.UNKNOWN, PhoneCarriageMode.fromCode(null))
        for ((expectedIdx, mode) in PhoneCarriageMode.entries.withIndex()) {
            assertEquals(mode, PhoneCarriageMode.fromCode(mode.code))
            assertTrue(expectedIdx >= 0)
        }
    }

    @Test
    fun testAllCyclingAndRunningModes_classifiedAccurately() {
        // 1. Bike Handlebar
        PhoneCarriageClassifier().also { c ->
            val mode = feedWindow50Hz(
                c, 0L, 4_000L, basePitchDeg = 5.0, pitchAmpDeg = 1.0, oscFreqHz = 1.0,
                yawAmpRadPerSec = 0.03, baroAmpM = 0.04, speedMps = 7.5, wheelSpeedMps = 7.5
            )
            assertEquals(PhoneCarriageMode.BIKE_HANDLEBAR, mode)
        }

        // 2. Bike Jersey Back Pocket
        PhoneCarriageClassifier().also { c ->
            val mode = feedWindow50Hz(
                c, 0L, 4_000L, basePitchDeg = 48.0, pitchAmpDeg = 4.5, oscFreqHz = 1.1,
                yawAmpRadPerSec = 0.18, baroAmpM = 0.24, speedMps = 7.5, wheelSpeedMps = 7.5
            )
            assertEquals(PhoneCarriageMode.BIKE_JERSEY_BACK, mode)
        }

        // 3. Bike Front Jeans Pocket
        PhoneCarriageClassifier().also { c ->
            val mode = feedWindow50Hz(
                c, 0L, 4_000L, basePitchDeg = 60.0, pitchAmpDeg = 20.0, oscFreqHz = 1.35,
                yawAmpRadPerSec = 0.78, baroAmpM = 0.55, speedMps = 7.0, wheelSpeedMps = 7.0
            )
            assertEquals(PhoneCarriageMode.BIKE_FRONT_POCKET, mode)
        }

        // 4. Run Waist / Hip Belt
        PhoneCarriageClassifier().also { c ->
            val mode = feedWindow50Hz(
                c, 0L, 4_000L, basePitchDeg = 80.0, pitchAmpDeg = 5.0, oscFreqHz = 2.9,
                yawAmpRadPerSec = 0.30, baroAmpM = 0.25, speedMps = 3.2, stepFreqHz = 2.9
            )
            assertEquals(PhoneCarriageMode.RUN_WAIST_HIP, mode)
        }

        // 5. Run Upper Arm / Bicep Strap (pendulum at 0.5 * f_step = 1.45 Hz)
        PhoneCarriageClassifier().also { c ->
            val mode = feedWindow50Hz(
                c, 0L, 4_500L, basePitchDeg = 65.0, pitchAmpDeg = 24.0, oscFreqHz = 1.45,
                yawAmpRadPerSec = 0.38, baroAmpM = 0.20, speedMps = 3.2, stepFreqHz = 2.9
            )
            assertEquals(PhoneCarriageMode.RUN_BICEP_ARM, mode)
        }

        // 6. Run Handheld (large wrist yaw + glance baseline shift + chassis pressure)
        PhoneCarriageClassifier().also { c ->
            feedWindow50Hz(
                c, 0L, 3_000L, basePitchDeg = 70.0, pitchAmpDeg = 22.0, oscFreqHz = 1.45,
                yawAmpRadPerSec = 0.85, baroAmpM = 0.45, speedMps = 3.1, stepFreqHz = 2.9
            )
            // Brief flat glance hold at 25 deg for 1 s to trigger glance detection
            for (t in 3_020L..4_200L step 20L) {
                c.ingestRotationVector(quaternionFromEulerXDeg(20.0), t)
                c.ingestGyro(0.75, 0.75, t)
                c.ingestStepEvent(t)
            }
            val mode = c.classify(4_200L)
            assertTrue("Expected glance event detected during handheld transition", c.isGlanceActive(4_200L))
            assertEquals(PhoneCarriageMode.RUN_HANDHELD, mode)
        }
    }

    @Test
    fun testZumBaeckerBakeryTimeline_hysteresisAndResetClean() {
        val classifier = PhoneCarriageClassifier(
            windowMs = 5_000L,
            mountHysteresisMs = 4_000L,
            dismountHysteresisMs = 2_000L
        )
        classifier.setSportContext(SportContextHint.CYCLING)

        // Phase 1: Riding with phone in front jeans pocket (t = 0..8s)
        val ridingMode = feedWindow50Hz(
            classifier, 0L, 8_000L, basePitchDeg = 60.0, pitchAmpDeg = 20.0, oscFreqHz = 1.35,
            yawAmpRadPerSec = 0.78, baroAmpM = 0.55, speedMps = 6.8, wheelSpeedMps = 6.8
        )
        assertEquals(PhoneCarriageMode.BIKE_FRONT_POCKET, ridingMode)

        // Phase 2: Brief 1.0s smooth glide / coasting must NOT prematurely flip out of BIKE_FRONT_POCKET (hysteresis)
        val afterBriefBump = feedWindow50Hz(
            classifier, 8_020L, 1_000L, basePitchDeg = 5.0, pitchAmpDeg = 0.5, oscFreqHz = 1.0,
            yawAmpRadPerSec = 0.02, baroAmpM = 0.02, speedMps = 6.5, wheelSpeedMps = 6.5
        )
        assertEquals(PhoneCarriageMode.BIKE_FRONT_POCKET, afterBriefBump)

        // Phase 3: Dismount and walk into bakery (t = 12s..19s, wheel gone, v = 1.2 m/s, walking step 1.7 Hz)
        val walkingInMode = feedWindow50Hz(
            classifier, 12_000L, 7_000L, basePitchDeg = 65.0, pitchAmpDeg = 8.0, oscFreqHz = 1.7,
            yawAmpRadPerSec = 0.25, baroAmpM = 0.20, speedMps = 1.2, wheelSpeedMps = null, stepFreqHz = 1.7
        )
        assertEquals(PhoneCarriageMode.WALKING_TRANSITION, walkingInMode)

        // Phase 4: Standing still at bakery counter (t = 22s..30s, v = 0.0)
        val stationaryMode = feedWindow50Hz(
            classifier, 22_000L, 8_000L, basePitchDeg = 70.0, pitchAmpDeg = 0.2, oscFreqHz = 0.2,
            yawAmpRadPerSec = 0.01, baroAmpM = 0.02, speedMps = 0.0
        )
        assertEquals(PhoneCarriageMode.STATIONARY, stationaryMode)

        // Phase 5: Walk back out to bike (t = 33s..40s)
        val walkingOutMode = feedWindow50Hz(
            classifier, 33_000L, 7_000L, basePitchDeg = 65.0, pitchAmpDeg = 8.0, oscFreqHz = 1.7,
            yawAmpRadPerSec = 0.25, baroAmpM = 0.20, speedMps = 1.3, stepFreqHz = 1.7
        )
        assertEquals(PhoneCarriageMode.WALKING_TRANSITION, walkingOutMode)

        // Phase 6: Ride home in front pocket (t = 44s..52s)
        val rideHomeMode = feedWindow50Hz(
            classifier, 44_000L, 8_000L, basePitchDeg = 60.0, pitchAmpDeg = 20.0, oscFreqHz = 1.35,
            yawAmpRadPerSec = 0.78, baroAmpM = 0.55, speedMps = 6.9, wheelSpeedMps = 6.9
        )
        assertEquals(PhoneCarriageMode.BIKE_FRONT_POCKET, rideHomeMode)

        classifier.reset()
        assertEquals(PhoneCarriageMode.UNKNOWN, classifier.currentMode)
        assertFalse(classifier.isGlanceActive(52_000L))
    }

    @Test
    fun testGracefulDegradation_missingStepRotVecOrBarometer() {
        // Without step detector or barometer (denied ACTIVITY_RECOGNITION & barometer-free emulator):
        PhoneCarriageClassifier().also { c ->
            val pocketNoStepNoBaro = feedWindow50Hz(
                c, 0L, 4_500L, basePitchDeg = 60.0, pitchAmpDeg = 20.0, oscFreqHz = 1.35,
                yawAmpRadPerSec = 0.78, baroAmpM = 0.0, speedMps = 6.5, wheelSpeedMps = 6.5,
                stepFreqHz = null, includeBaro = false
            )
            assertEquals(PhoneCarriageMode.BIKE_FRONT_POCKET, pocketNoStepNoBaro)
        }

        // Without step detector, walking dismount still classifies deterministically from speed + IMU:
        PhoneCarriageClassifier().also { c ->
            val walkNoStep = feedWindow50Hz(
                c, 0L, 5_000L, basePitchDeg = 65.0, pitchAmpDeg = 7.0, oscFreqHz = 1.65,
                yawAmpRadPerSec = 0.25, baroAmpM = 0.20, speedMps = 1.25, wheelSpeedMps = null,
                stepFreqHz = null
            )
            assertEquals(PhoneCarriageMode.WALKING_TRANSITION, walkNoStep)
        }

        // Without rotation vector, front pocket still separates from handlebar via yaw + baro:
        PhoneCarriageClassifier().also { c ->
            val noRotVecHandlebar = feedWindow50Hz(
                c, 0L, 4_500L, basePitchDeg = 0.0, pitchAmpDeg = 0.0, oscFreqHz = 1.0,
                yawAmpRadPerSec = 0.02, baroAmpM = 0.04, speedMps = 7.5, wheelSpeedMps = 7.5,
                includeRotVec = false
            )
            assertEquals(PhoneCarriageMode.BIKE_HANDLEBAR, noRotVecHandlebar)
        }
        PhoneCarriageClassifier().also { c ->
            val noRotVecPocket = feedWindow50Hz(
                c, 0L, 4_500L, basePitchDeg = 0.0, pitchAmpDeg = 0.0, oscFreqHz = 1.3,
                yawAmpRadPerSec = 0.75, baroAmpM = 0.55, speedMps = 7.5, wheelSpeedMps = 7.5,
                includeRotVec = false
            )
            assertEquals(PhoneCarriageMode.BIKE_FRONT_POCKET, noRotVecPocket)
        }

        // Tilted aero stem mount still classifies as rigid BIKE_HANDLEBAR:
        PhoneCarriageClassifier().also { c ->
            for (t in 0L..4_000L step 20L) {
                c.ingestRotationVector(quaternionFromTiltAndRollDeg(32.0 + 0.4 * sin(t * 0.01), 1.0), t)
                c.ingestGyro(0.02, 0.02, t)
                if (t % 200L == 0L) c.ingestBarometerAltitude(200.0 + 0.03 * sin(t * 0.003), t)
                if (t % 1000L == 0L) {
                    c.ingestGnssSpeed(8.0, 45.0, 3.0, t)
                    c.ingestWheelSpeed(8.0, t)
                }
            }
            assertEquals(PhoneCarriageMode.BIKE_HANDLEBAR, c.classify(4_000L))
        }
    }

    @Test
    fun testStepDetectorLessPocketAndHandheldWalkingDismount_singleLimbPendulumNotFrontPocket() {
        // Without TYPE_STEP_DETECTOR (API 29+ ACTIVITY_RECOGNITION denied), pocket walking with
        // single-leg pendulum at 0.85 Hz (< 1.1 Hz) and swingSdDeg >= 12.0 at v = 1.4 and 2.3 m/s
        // under CYCLING context must classify as WALKING_TRANSITION rather than BIKE_FRONT_POCKET.
        for (walkSpeedMps in listOf(1.2, 2.3)) {
            val classifier = PhoneCarriageClassifier(
                windowMs = 5_000L,
                mountHysteresisMs = 4_000L,
                dismountHysteresisMs = 2_000L
            )
            classifier.setSportContext(SportContextHint.CYCLING)
            val rideMode = feedWindow50Hz(
                classifier, 0L, 5_000L, basePitchDeg = 60.0, pitchAmpDeg = 20.0, oscFreqHz = 1.35,
                yawAmpRadPerSec = 0.78, baroAmpM = 0.55, speedMps = 7.0, wheelSpeedMps = 7.0,
                stepFreqHz = null
            )
            assertEquals(PhoneCarriageMode.BIKE_FRONT_POCKET, rideMode)

            val dismountMode = feedWindow50Hz(
                classifier, 6_000L, 6_000L, basePitchDeg = 68.0, pitchAmpDeg = 22.0, oscFreqHz = 0.85,
                yawAmpRadPerSec = 0.72, baroAmpM = 0.45, speedMps = walkSpeedMps, wheelSpeedMps = null,
                stepFreqHz = null
            )
            assertEquals(
                "Step-detector-less single-leg/arm pendulum walk ($walkSpeedMps m/s, 0.85 Hz, high swingSdDeg) must classify as WALKING_TRANSITION",
                PhoneCarriageMode.WALKING_TRANSITION,
                dismountMode
            )
        }
    }

    @Test
    fun testGnssBearingGapAndInvalidFix_resetsStaleEstimatedYawRate() {
        val classifier = PhoneCarriageClassifier()

        // 1. Establish a sharp turning yaw rate (~1.57 rad/s) across two valid consecutive 1-s fixes
        classifier.ingestGnssSpeed(speedMps = 6.0, bearingDeg = 0.0, accuracyMeters = 4.0, timestampMs = 1_000L)
        classifier.ingestGnssSpeed(speedMps = 6.0, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = 2_000L)
        assertTrue(
            "Consecutive 1-s 90-deg turn must estimate non-zero GNSS yaw rate",
            kotlin.math.abs(classifier.currentEstimatedGnssYawRateRadPerSecForTest()) > 1.0
        )

        // 2. After a > 3_000 ms bearing gap (e.g. 4.5 s outage/gap), stale turn rate MUST reset to 0.0
        classifier.ingestGnssSpeed(speedMps = 6.0, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = 6_500L)
        assertEquals(
            "Expired > 3 s GNSS bearing gap must reset estimatedGnssYawRateRadPerSec to 0.0",
            0.0,
            classifier.currentEstimatedGnssYawRateRadPerSecForTest(),
            1e-9
        )

        // 3. Repeat sharp turn, then verify degraded accuracy (> 25 m) and slow speed (< 1.2 m/s) also reset it
        classifier.ingestGnssSpeed(speedMps = 6.0, bearingDeg = 180.0, accuracyMeters = 4.0, timestampMs = 7_500L)
        assertTrue(kotlin.math.abs(classifier.currentEstimatedGnssYawRateRadPerSecForTest()) > 1.0)
        classifier.ingestGnssSpeed(speedMps = 6.0, bearingDeg = 180.0, accuracyMeters = 40.0, timestampMs = 8_500L)
        assertEquals(0.0, classifier.currentEstimatedGnssYawRateRadPerSecForTest(), 1e-9)

        classifier.ingestGnssSpeed(speedMps = 6.0, bearingDeg = 0.0, accuracyMeters = 4.0, timestampMs = 9_500L)
        classifier.ingestGnssSpeed(speedMps = 6.0, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = 10_500L)
        assertTrue(kotlin.math.abs(classifier.currentEstimatedGnssYawRateRadPerSecForTest()) > 1.0)
        classifier.ingestGnssSpeed(speedMps = 1.0, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = 11_500L)
        assertEquals(0.0, classifier.currentEstimatedGnssYawRateRadPerSecForTest(), 1e-9)
    }

    // REQ-FIL-034.1 / TST-FIL-034.1: Indoor GNSS outage, stagnant cached OEM fixes & low-speed quiet IMU transition to STATIONARY
    @Test
    fun testIndoorGnssOutageAndStagnantCachedFixes_transitionToStationaryInsteadOfRunWaistHipFreeze_tstFil034_1() {
        // 1. Bakery walk-in (1.44 m/s) -> 120 s indoor GNSS outage with quiet IMU and zero steps
        val outageClassifier = PhoneCarriageClassifier(
            windowMs = 6_000L,
            mountHysteresisMs = 4_000L,
            dismountHysteresisMs = 2_000L
        )
        feedWindow50Hz(
            outageClassifier, 0L, 4_000L, basePitchDeg = 75.0, pitchAmpDeg = 6.0, oscFreqHz = 1.7,
            yawAmpRadPerSec = 0.22, baroAmpM = 0.18, speedMps = 1.44, stepFreqHz = 1.7
        )
        // Last indoor fix at t=4s (1.44 m/s), then zero GNSS callbacks & zero steps for 120 s with quiet IMU
        for (t in 4_020L..124_000L step 20L) {
            outageClassifier.ingestRotationVector(quaternionFromEulerXDeg(72.0 + 0.3 * sin(t * 0.002)), t)
            outageClassifier.ingestGyro(0.02, 0.02, t)
            if (t % 1000L == 0L) {
                val mode = outageClassifier.classify(t)
                if (t >= 12_000L) {
                    assertEquals(
                        "Indoor GNSS outage with quiet IMU must stay STATIONARY at t=${t / 1000L}s (never RUN_WAIST_HIP freeze)",
                        PhoneCarriageMode.STATIONARY,
                        mode
                    )
                }
            }
        }
        assertEquals(PhoneCarriageMode.STATIONARY, outageClassifier.currentMode)

        // 2. Stagnant cached OEM indoor fixes (optimistic acc=5 m, fake cached speed=2.0 m/s, < 2 m spread over >= 4 s) + quiet IMU
        val stagnantClassifier = PhoneCarriageClassifier()
        feedWindow50Hz(
            stagnantClassifier, 0L, 4_000L, basePitchDeg = 75.0, pitchAmpDeg = 6.0, oscFreqHz = 1.7,
            yawAmpRadPerSec = 0.22, baroAmpM = 0.18, speedMps = 1.4, stepFreqHz = 1.7
        )
        for (t in 4_020L..14_000L step 20L) {
            stagnantClassifier.ingestRotationVector(quaternionFromEulerXDeg(70.0 + 0.2 * sin(t * 0.001)), t)
            stagnantClassifier.ingestGyro(0.01, 0.01, t)
            if (t % 1000L == 0L) {
                stagnantClassifier.ingestGnssSpeed(
                    speedMps = 2.0,
                    bearingDeg = 90.0,
                    accuracyMeters = 5.0,
                    timestampMs = t,
                    latitude = 48.6567,
                    longitude = 9.0545
                )
                stagnantClassifier.classify(t)
            }
        }
        assertEquals(PhoneCarriageMode.STATIONARY, stagnantClassifier.currentMode)

        // 3. Pure IMU-only unit check with zero prior speed/steps must stay UNKNOWN (neutral 1.0 s yaw window invariant)
        val imuOnlyClassifier = PhoneCarriageClassifier()
        for (t in 0L..5_000L step 20L) {
            imuOnlyClassifier.ingestRotationVector(quaternionFromEulerXDeg(10.0), t)
            imuOnlyClassifier.ingestGyro(0.01, 0.01, t)
            if (t % 1000L == 0L) {
                assertEquals(PhoneCarriageMode.UNKNOWN, imuOnlyClassifier.classify(t))
            }
        }
    }

    // REQ-FIL-034.2 / TST-FIL-034.2: Cyclic thigh flexion (0.8-1.8 Hz +-25 deg) yields 0 false glances and BIKE_FRONT_POCKET while gentle wrist glances still fire
    @Test
    fun testCyclicFrontPocketPedaling_zeroFalseGlancesAndClassifiesBikeFrontPocket_tstFil034_2() {
        for (freqHz in listOf(0.85, 1.2, 1.5, 1.75)) {
            val classifier = PhoneCarriageClassifier()
            classifier.setSportContext(SportContextHint.UNKNOWN)
            classifier.setStepDetectorRegistered(true)
            var falseGlances = 0
            for (t in 0L..8_000L step 20L) {
                val sec = t / 1000.0
                val phase = 2.0 * PI * freqHz * sec
                val speed = 2.5 + (5.4 - 2.5) * (sec / 8.0)
                val pitch = 60.0 + 25.0 * sin(phase)
                classifier.ingestRotationVector(quaternionFromEulerXDeg(pitch), t)
                if (classifier.isGlanceActive(t)) falseGlances++
                val yaw = 0.72 * sin(phase)
                classifier.ingestGyro(yaw, kotlin.math.abs(yaw), t)
                // Smooth 1-1.75 Hz thigh motion without 2.4-3.6 Hz running footstrike impacts
                classifier.ingestAccelerometer(0.4 * sin(phase), -0.3 * cos(phase), 9.81 + 0.5 * sin(phase), t)
                if (t % 200L == 0L) {
                    classifier.ingestBarometerAltitude(438.0 + 0.50 * sin(PI * sec), t)
                }
                if (t % 1000L == 0L) {
                    classifier.ingestGnssSpeed(speed, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                    classifier.classify(t)
                }
            }
            assertEquals("Cyclic ${freqHz} Hz +-25 deg thigh flexion must trigger 0 false glances", 0, falseGlances)
            assertEquals(
                "Front-pocket acceleration 2.5->5.4 m/s at ${freqHz} Hz must classify BIKE_FRONT_POCKET (not RUN_HANDHELD)",
                PhoneCarriageMode.BIKE_FRONT_POCKET,
                classifier.currentMode
            )
        }

        // Gentle wrist glance (70 deg -> 35 deg sustained hold) must still trigger isGlanceActive
        val gentleGlanceClassifier = PhoneCarriageClassifier()
        for (t in 0L..4_200L step 20L) {
            val sec = t / 1000.0
            val pitch = if (t >= 3_000L) 35.0 else 70.0 + 18.0 * sin(2.0 * PI * 1.45 * sec)
            gentleGlanceClassifier.ingestRotationVector(quaternionFromEulerXDeg(pitch), t)
        }
        assertTrue(
            "Genuine gentle 70->35 deg wrist glance hold must trigger isGlanceActive",
            gentleGlanceClassifier.isGlanceActive(4_200L)
        )
    }

    // REQ-FIL-034.4 / TST-FIL-034.4: Cross-device & cross-athlete portability (permission-denied baro-free runner, 5.6 m/s interval runner, 2.0 m/s uphill MTB)
    @Test
    fun testCrossDeviceAndAthletePortability_deniedPermissionBaroFreeFastRunnerAndUphillMtb_tstFil034_4() {
        // 1. Permission-denied (stepDetectorRegistered=false), barometer-free runner at 3.2 m/s with 2.8 Hz accel footstrikes
        val deniedPermRunner = PhoneCarriageClassifier()
        deniedPermRunner.setSportContext(SportContextHint.UNKNOWN)
        deniedPermRunner.setStepDetectorRegistered(false)
        for (t in 0L..5_500L step 20L) {
            val sec = t / 1000.0
            val stepPhase = 2.0 * PI * 2.8 * sec
            val armPhase = 2.0 * PI * 1.4 * sec
            deniedPermRunner.ingestRotationVector(quaternionFromEulerXDeg(78.0 + 5.0 * sin(stepPhase)), t)
            deniedPermRunner.ingestGyro(0.26 * sin(armPhase), 0.26, t)
            // Signed 3-axis accel with strong 2.8 Hz running impact (> 1.2 m/s^2 RMS)
            deniedPermRunner.ingestAccelerometer(-0.8 * sin(stepPhase), 0.5 * cos(stepPhase), 9.81 + 4.2 * sin(stepPhase), t)
            if (t % 1000L == 0L) {
                deniedPermRunner.ingestGnssSpeed(3.2, bearingDeg = 45.0, accuracyMeters = 4.0, timestampMs = t)
                deniedPermRunner.classify(t)
            }
        }
        assertTrue(
            "Barometer-free permission-denied runner must classify RUN_* (was ${deniedPermRunner.currentMode})",
            deniedPermRunner.currentMode.isRunningMode()
        )

        // 2. Fast interval runner at 5.6 m/s (3:00 min/km) with 3.0 Hz footstrikes (both with & without step detector)
        for (withStepDetector in listOf(true, false)) {
            val fastRunner = PhoneCarriageClassifier()
            fastRunner.setSportContext(SportContextHint.UNKNOWN)
            fastRunner.setStepDetectorRegistered(withStepDetector)
            var lastStepMs = -1000L
            for (t in 0L..5_500L step 20L) {
                val sec = t / 1000.0
                val stepPhase = 2.0 * PI * 3.0 * sec
                val armPhase = 2.0 * PI * 1.5 * sec
                fastRunner.ingestRotationVector(quaternionFromEulerXDeg(68.0 + 20.0 * sin(armPhase)), t)
                fastRunner.ingestGyro(0.35 * sin(armPhase), 0.35, t)
                fastRunner.ingestAccelerometer(0.5 * sin(stepPhase), -0.6 * cos(stepPhase), 9.81 + 4.8 * sin(stepPhase), t)
                if (withStepDetector && t - lastStepMs >= 333L) {
                    fastRunner.ingestStepEvent(t)
                    lastStepMs = t
                }
                if (t % 1000L == 0L) {
                    fastRunner.ingestGnssSpeed(5.6, bearingDeg = 90.0, accuracyMeters = 3.0, timestampMs = t)
                    fastRunner.classify(t)
                }
            }
            assertTrue(
                "Fast 5.6 m/s interval runner (withStepDetector=$withStepDetector) must classify RUN_* (was ${fastRunner.currentMode})",
                fastRunner.currentMode.isRunningMode()
            )
        }

        // 3. Steep uphill MTB climber at 2.0 m/s, 60 RPM (1.0 Hz) smooth pedaling + low-energy 3.0 Hz harmonic (< 1.2 m/s^2 RMS)
        val uphillMtb = PhoneCarriageClassifier()
        uphillMtb.setSportContext(SportContextHint.UNKNOWN)
        uphillMtb.setStepDetectorRegistered(false) // Even without step detector, accel confirms zero footstrikes!
        for (t in 0L..6_000L step 20L) {
            val sec = t / 1000.0
            val pedalPhase = 2.0 * PI * 1.0 * sec
            uphillMtb.ingestRotationVector(quaternionFromEulerXDeg(60.0 + 18.0 * sin(pedalPhase)), t)
            uphillMtb.ingestGyro(0.65 * sin(pedalPhase), 0.65, t)
            // Low-energy 3.0 Hz harmonic (0.5 m/s^2 -> RMS ~0.35 < 1.2 m/s^2) must NOT spoof running footstrikes
            uphillMtb.ingestAccelerometer(
                0.3 * sin(pedalPhase),
                -0.2 * cos(pedalPhase),
                9.81 + 0.6 * sin(pedalPhase) + 0.5 * sin(3.0 * pedalPhase),
                t
            )
            if (t % 200L == 0L) {
                uphillMtb.ingestBarometerAltitude(800.0 + 0.45 * sin(PI * sec), t)
            }
            if (t % 1000L == 0L) {
                uphillMtb.ingestGnssSpeed(2.0, bearingDeg = 180.0, accuracyMeters = 5.0, timestampMs = t)
                uphillMtb.classify(t)
            }
        }
        assertTrue(
            "Steep uphill 2.0 m/s 60 RPM MTB climber must classify BIKE_* and never RUN_* (was ${uphillMtb.currentMode})",
            uphillMtb.currentMode.isCyclingMode()
        )
    }

    // REQ-FIL-035.1 / TST-FIL-035.1: Sport = RUN cycling guardrail, stale BIKE_* non-sticky eviction & triathlon/brick override
    @Test
    fun testRunningSportContextGuardrail_masksCyclingEvictsStaleBikeAndAllowsBrickOverride_tstFil035_1() {
        // 1. Sport = RUN with cycling-like front-pocket IMU, wheelSpeed <= 0.5 and speed < 5.5 -> 0% BIKE_*
        val runner = PhoneCarriageClassifier()
        runner.setSportContext(SportContextHint.RUNNING)
        runner.setStepDetectorRegistered(true) // zero steps reported
        for (t in 0L..8_000L step 20L) {
            val sec = t / 1000.0
            val phase = 2.0 * PI * 1.25 * sec
            runner.ingestRotationVector(quaternionFromEulerXDeg(58.0 + 28.0 * sin(phase)), t)
            runner.ingestGyro(0.75 * sin(phase), 0.75, t)
            if (t % 200L == 0L) {
                runner.ingestBarometerAltitude(400.0 + 0.55 * sin(PI * sec), t)
            }
            if (t % 1000L == 0L) {
                if (t >= 4_000L) runner.ingestWheelSpeed(0.15, t) // stationary/noise wheel speed <= 0.2 -> uses GNSS speed
                runner.ingestGnssSpeed(3.2, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                val mode = runner.classify(t)
                assertFalse(
                    "Cycling mode must be masked during Sport=RUN without wheel>0.5 or speed>=5.5 at t=$t (was $mode)",
                    mode.isCyclingMode()
                )
            }
        }
        assertTrue(
            "Running workout with cycling-like IMU must settle in RUN_* (was ${runner.currentMode})",
            runner.currentMode.isRunningMode()
        )
        // Also verify wheelSpeed = 0.4 m/s (<= 0.5) never emits any BIKE_* mode
        runner.ingestWheelSpeed(0.4, 9_000L)
        runner.ingestGnssSpeed(3.2, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = 9_000L)
        assertFalse("wheelSpeed=0.4 <= 0.5 must still mask BIKE_*", runner.classify(9_000L).isCyclingMode())

        // 2. Zero-IMU fallback under Sport = RUN must emit RUN_WAIST_HIP (never BIKE_HANDLEBAR)
        val zeroImuRunner = PhoneCarriageClassifier()
        zeroImuRunner.setSportContext(SportContextHint.RUNNING)
        for (sec in 0L..5L) {
            zeroImuRunner.ingestGnssSpeed(2.8, bearingDeg = 45.0, accuracyMeters = 4.0, timestampMs = sec * 1000L)
            zeroImuRunner.classify(sec * 1000L)
        }
        assertEquals(PhoneCarriageMode.RUN_WAIST_HIP, zeroImuRunner.currentMode)

        // 3. Stale committed BIKE_FRONT_POCKET must be non-sticky when Sport = RUN activates without cycling evidence
        val switched = PhoneCarriageClassifier()
        switched.setSportContext(SportContextHint.CYCLING)
        switched.setStepDetectorRegistered(true)
        feedWindow50Hz(
            classifier = switched,
            startMs = 0L,
            durationMs = 5_000L,
            basePitchDeg = 55.0,
            pitchAmpDeg = 28.0,
            oscFreqHz = 1.3,
            yawAmpRadPerSec = 0.75,
            baroAmpM = 0.50,
            speedMps = 7.0,
            wheelSpeedMps = 7.0
        )
        assertEquals(PhoneCarriageMode.BIKE_FRONT_POCKET, switched.currentMode)
        switched.setSportContext(SportContextHint.RUNNING)
        for (t in 5_020L..16_000L step 20L) {
            val sec = t / 1000.0
            val phase = 2.0 * PI * 1.3 * sec
            switched.ingestRotationVector(quaternionFromEulerXDeg(76.0 + 16.0 * sin(phase)), t)
            switched.ingestGyro(0.45 * sin(phase), 0.45, t)
            if (t % 200L == 0L) {
                switched.ingestBarometerAltitude(400.0 + 0.25 * sin(PI * sec), t)
            }
            if (t % 1000L == 0L) {
                switched.ingestGnssSpeed(2.4, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                switched.classify(t)
            }
        }
        assertEquals(
            "Stale BIKE_FRONT_POCKET must not stay sticky once Sport=RUN activates without wheel/>=5.5 m/s",
            PhoneCarriageMode.RUN_WAIST_HIP,
            switched.currentMode
        )

        // 4. Triathlon/brick override: wheelSpeed > 0.5 OR gnssSpeed >= 5.5 lifts mask and detects BIKE_*
        for (useWheelTrigger in listOf(true, false)) {
            val brick = PhoneCarriageClassifier()
            brick.setSportContext(SportContextHint.RUNNING)
            brick.setStepDetectorRegistered(true)
            for (t in 0L..7_000L step 20L) {
                val sec = t / 1000.0
                val phase = 2.0 * PI * 1.2 * sec
                brick.ingestRotationVector(quaternionFromEulerXDeg(14.0 + 1.0 * sin(phase)), t)
                brick.ingestGyro(0.02, 0.02, t)
                if (t % 200L == 0L) brick.ingestBarometerAltitude(400.0, t)
                if (t % 1000L == 0L) {
                    if (useWheelTrigger) {
                        brick.ingestWheelSpeed(6.0, t)
                        brick.ingestGnssSpeed(5.0, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                    } else {
                        brick.ingestGnssSpeed(6.5, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                    }
                    brick.classify(t)
                }
            }
            assertTrue(
                "Triathlon/brick override (useWheelTrigger=$useWheelTrigger) must lift cycling mask (was ${brick.currentMode})",
                brick.currentMode.isCyclingMode()
            )
        }
    }

    // REQ-FIL-035.2 / TST-FIL-035.2: Easy-pace walking-band bypass, front-hip running belt tiers, obstacle walk & 9-profile separation
    @Test
    fun testFrontHipRunningBeltAndEasyPace_bypassesWalkingBandsPreservesObstacleWalkAndCarrySeparation_tstFil035_2() {
        // 1. Front-hip running belt profiles (mild, lively, bouncy, low-yaw) at easy pace 1.9–2.3 m/s:
        //    both with 2.20 Hz in walkingStepBand and with dropped step detector + impactRms >= 1.8 m/s^2
        data class BeltCase(
            val label: String,
            val pitchAmpDeg: Double,
            val yawAmp: Double,
            val baroAmp: Double,
            val speedMps: Double,
            val stepHz: Double?,
            val impactAmp: Double
        )
        val beltCases = listOf(
            BeltCase("mild_easy_step_band", 15.5, 0.56, 0.25, 1.95, 2.20, 3.2),
            BeltCase("lively_dropped_steps", 20.0, 0.78, 0.30, 2.15, null, 3.4),
            BeltCase("bouncy_belt", 20.5, 0.85, 0.36, 2.30, 2.55, 3.6),
            BeltCase("low_yaw_belt", 16.5, 0.20, 0.22, 1.85, null, 3.0),
            // Permission-denied runner with moderate impact (1.5 m/s^2 RMS) + accel footstrike cadence >= 2.15 Hz
            BeltCase("accel_cadence_moderate_impact", 16.0, 0.50, 0.24, 1.70, null, 2.2)
        )
        for (c in beltCases) {
            val clf = PhoneCarriageClassifier()
            clf.setSportContext(SportContextHint.RUNNING)
            clf.setStepDetectorRegistered(c.stepHz != null)
            val stepIntervalMs = c.stepHz?.let { (1000.0 / it).toLong() } ?: Long.MAX_VALUE
            var lastStepMs = -stepIntervalMs
            for (t in 0L..7_000L step 20L) {
                val sec = t / 1000.0
                val swingPhase = 2.0 * PI * 1.3 * sec
                val strikePhase = 2.0 * PI * (c.stepHz ?: 2.65) * sec
                clf.ingestRotationVector(quaternionFromEulerXDeg(78.0 + c.pitchAmpDeg * sin(swingPhase)), t)
                clf.ingestGyro(c.yawAmp * sin(swingPhase), c.yawAmp, t)
                clf.ingestAccelerometer(0.4 * sin(strikePhase), 0.3 * cos(strikePhase), 9.81 + c.impactAmp * sin(strikePhase), t)
                if (t % 200L == 0L) {
                    clf.ingestBarometerAltitude(500.0 + c.baroAmp * sin(PI * sec), t)
                }
                if (c.stepHz != null && t - lastStepMs >= stepIntervalMs) {
                    clf.ingestStepEvent(t)
                    lastStepMs = t
                }
                if (t % 1000L == 0L) {
                    clf.ingestGnssSpeed(c.speedMps, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                    clf.classify(t)
                }
            }
            assertEquals(
                "Front-hip running belt case '${c.label}' must classify RUN_WAIST_HIP (was ${clf.currentMode})",
                PhoneCarriageMode.RUN_WAIST_HIP,
                clf.currentMode
            )
        }

        // 2. Slow obstacle climb (< 1.65 m/s, low impact) during Sport = RUN transitions to WALKING_TRANSITION
        val obstacleRun = PhoneCarriageClassifier()
        obstacleRun.setSportContext(SportContextHint.RUNNING)
        obstacleRun.setStepDetectorRegistered(true)
        var lastStepMs = -400L
        for (t in 0L..6_000L step 20L) {
            val sec = t / 1000.0
            obstacleRun.ingestRotationVector(quaternionFromEulerXDeg(78.0 + 16.0 * sin(2.0 * PI * 1.3 * sec)), t)
            obstacleRun.ingestGyro(0.45 * sin(2.0 * PI * 1.3 * sec), 0.45, t)
            obstacleRun.ingestAccelerometer(0.3, 0.2, 9.81 + 3.4 * sin(2.0 * PI * 2.6 * sec), t)
            if (t - lastStepMs >= 385L) { obstacleRun.ingestStepEvent(t); lastStepMs = t }
            if (t % 1000L == 0L) {
                obstacleRun.ingestGnssSpeed(2.2, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                obstacleRun.classify(t)
            }
        }
        assertEquals(PhoneCarriageMode.RUN_WAIST_HIP, obstacleRun.currentMode)

        // Fallen-tree obstacle stretch: 1.2 m/s, 1.5 Hz steps, low impact (~0.5 m/s^2 RMS) for 6 s -> WALKING_TRANSITION
        for (t in 6_020L..14_000L step 20L) {
            val sec = t / 1000.0
            obstacleRun.ingestRotationVector(quaternionFromEulerXDeg(76.0 + 9.0 * sin(2.0 * PI * 0.85 * sec)), t)
            obstacleRun.ingestGyro(0.20 * sin(2.0 * PI * 0.85 * sec), 0.20, t)
            obstacleRun.ingestAccelerometer(0.2, 0.1, 9.81 + 0.5 * sin(2.0 * PI * 1.5 * sec), t)
            if (t - lastStepMs >= 667L) { obstacleRun.ingestStepEvent(t); lastStepMs = t }
            if (t % 1000L == 0L) {
                obstacleRun.ingestGnssSpeed(1.2, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                obstacleRun.classify(t)
            }
        }
        assertEquals(
            "Genuine slow fallen-tree obstacle stretch must enter WALKING_TRANSITION",
            PhoneCarriageMode.WALKING_TRANSITION,
            obstacleRun.currentMode
        )

        // 3. Low-impact brisk walking at 1.85 m/s (impactRms < 1.2, 0 steps) must NOT be suppressed as running
        val briskWalk = PhoneCarriageClassifier()
        briskWalk.setSportContext(SportContextHint.RUNNING)
        briskWalk.setStepDetectorRegistered(false)
        for (t in 0L..6_000L step 20L) {
            val sec = t / 1000.0
            briskWalk.ingestRotationVector(quaternionFromEulerXDeg(76.0 + 10.0 * sin(2.0 * PI * 0.9 * sec)), t)
            briskWalk.ingestGyro(0.22 * sin(2.0 * PI * 0.9 * sec), 0.22, t)
            briskWalk.ingestAccelerometer(0.1, 0.1, 9.81 + 0.5 * sin(2.0 * PI * 1.8 * sec), t)
            if (t % 1000L == 0L) {
                briskWalk.ingestGnssSpeed(1.85, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                briskWalk.classify(t)
            }
        }
        assertEquals(
            "Low-impact brisk walk (1.85 m/s, impactRms < 1.2) must classify WALKING_TRANSITION",
            PhoneCarriageMode.WALKING_TRANSITION,
            briskWalk.currentMode
        )

        // 4. Handheld without glance (high swing >= 15 deg, high yaw >= 0.55, high baro >= 0.45) still classifies RUN_HANDHELD
        val handheldNoGlance = PhoneCarriageClassifier()
        handheldNoGlance.setSportContext(SportContextHint.RUNNING)
        feedWindow50Hz(
            classifier = handheldNoGlance,
            startMs = 0L,
            durationMs = 5_000L,
            basePitchDeg = 70.0,
            pitchAmpDeg = 26.0,
            oscFreqHz = 1.4,
            yawAmpRadPerSec = 1.05,
            baroAmpM = 0.65,
            speedMps = 3.2,
            stepFreqHz = 2.8
        )
        assertEquals(PhoneCarriageMode.RUN_HANDHELD, handheldNoGlance.currentMode)
    }

    // REQ-FIL-035.3 / TST-FIL-035.3: SCORE_MARGIN=0.85, 4 s RUN->WALK dwell, 6 s intra-run dwell, 0 ms wrist glance & 2 s WALK->RUN / STATIONARY
    @Test
    fun testChatterResistantMarginAndAsymmetricDwell_suppressesTransientDipsWhilePreservingFastGlanceAndWalkToRun_tstFil035_3() {
        val clf = PhoneCarriageClassifier()
        clf.setSportContext(SportContextHint.RUNNING)
        clf.setStepDetectorRegistered(true)

        // 1. Establish steady front-hip belt RUN_WAIST_HIP
        var lastStepMs = -400L
        for (t in 0L..6_000L step 20L) {
            val sec = t / 1000.0
            clf.ingestRotationVector(quaternionFromEulerXDeg(78.0 + 17.0 * sin(2.0 * PI * 1.3 * sec)), t)
            clf.ingestGyro(0.55 * sin(2.0 * PI * 1.3 * sec), 0.55, t)
            clf.ingestAccelerometer(0.4, 0.3, 9.81 + 3.4 * sin(2.0 * PI * 2.6 * sec), t)
            if (t % 200L == 0L) clf.ingestBarometerAltitude(500.0 + 0.24 * sin(PI * sec), t)
            if (t - lastStepMs >= 385L) { clf.ingestStepEvent(t); lastStepMs = t }
            if (t % 1000L == 0L) {
                clf.ingestGnssSpeed(2.3, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                clf.classify(t)
            }
        }
        assertEquals(PhoneCarriageMode.RUN_WAIST_HIP, clf.currentMode)

        // 2. Transient 2.5 s canopy GPS speed dip (1.2 m/s, < 4.0 s RUN->WALK dwell) must produce 0 switches
        var switches = 0
        var prev = clf.currentMode
        for (t in 6_020L..8_500L step 20L) {
            val sec = t / 1000.0
            clf.ingestRotationVector(quaternionFromEulerXDeg(78.0 + 17.0 * sin(2.0 * PI * 1.3 * sec)), t)
            clf.ingestGyro(0.55 * sin(2.0 * PI * 1.3 * sec), 0.55, t)
            clf.ingestAccelerometer(0.2, 0.1, 9.81 + 0.6 * sin(2.0 * PI * 1.5 * sec), t)
            if (t % 200L == 0L) clf.ingestBarometerAltitude(500.0 + 0.24 * sin(PI * sec), t)
            if (t - lastStepMs >= 650L) { clf.ingestStepEvent(t); lastStepMs = t }
            if (t % 500L == 0L) {
                clf.ingestGnssSpeed(1.2, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                val m = clf.classify(t)
                if (m != prev) { switches++; prev = m }
            }
        }
        assertEquals("2.5 s canopy GPS dip must not exit RUN_WAIST_HIP (4.0 s dwell)", 0, switches)
        assertEquals(PhoneCarriageMode.RUN_WAIST_HIP, clf.currentMode)

        // 3. Transient 4.5 s non-glance bicep-like IMU burst (< 6.0 s intra-run dwell) must produce 0 switches
        for (t in 8_520L..13_000L step 20L) {
            val sec = t / 1000.0
            clf.ingestRotationVector(quaternionFromEulerXDeg(75.0 + 25.0 * sin(2.0 * PI * 1.4 * sec)), t)
            clf.ingestGyro(0.35 * sin(2.0 * PI * 1.4 * sec), 0.35, t)
            clf.ingestAccelerometer(0.4, 0.3, 9.81 + 3.4 * sin(2.0 * PI * 2.8 * sec), t)
            if (t % 200L == 0L) clf.ingestBarometerAltitude(500.0 + 0.18 * sin(PI * sec), t)
            if (t - lastStepMs >= 357L) { clf.ingestStepEvent(t); lastStepMs = t }
            if (t % 500L == 0L) {
                clf.ingestGnssSpeed(2.6, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                val m = clf.classify(t)
                if (m != prev) { switches++; prev = m }
            }
        }
        assertEquals("4.5 s non-glance IMU burst must not chatter out of RUN_WAIST_HIP (6.0 s intra-run dwell)", 0, switches)
        assertEquals(PhoneCarriageMode.RUN_WAIST_HIP, clf.currentMode)

        // 4. Verified wrist glance switches RUN_WAIST_HIP -> RUN_HANDHELD immediately (0 ms)
        val glanceClf = PhoneCarriageClassifier()
        glanceClf.setSportContext(SportContextHint.RUNNING)
        glanceClf.setStepDetectorRegistered(true)
        var gStepMs = -400L
        for (t in 0L..6_000L step 20L) {
            val sec = t / 1000.0
            glanceClf.ingestRotationVector(quaternionFromEulerXDeg(76.0 + 16.0 * sin(2.0 * PI * 1.3 * sec)), t)
            glanceClf.ingestGyro(0.50 * sin(2.0 * PI * 1.3 * sec), 0.50, t)
            if (t % 200L == 0L) glanceClf.ingestBarometerAltitude(500.0 + 0.25 * sin(PI * sec), t)
            if (t - gStepMs >= 370L) { glanceClf.ingestStepEvent(t); gStepMs = t }
            if (t % 500L == 0L) {
                glanceClf.ingestGnssSpeed(2.6, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                glanceClf.classify(t)
            }
        }
        assertEquals(PhoneCarriageMode.RUN_WAIST_HIP, glanceClf.currentMode)
        // Pull phone out of front-hip belt and hold steady at 18 deg reading angle -> immediate 0 ms switch to RUN_HANDHELD
        for (t in 6_020L..7_500L step 20L) {
            val sec = t / 1000.0
            val pitch = if (t < 6_700L) 72.0 + 24.0 * sin(2.0 * PI * 1.4 * sec) else 18.0
            glanceClf.ingestRotationVector(quaternionFromEulerXDeg(pitch), t)
            glanceClf.ingestGyro(1.10 * sin(2.0 * PI * 1.4 * sec), 1.10, t)
            if (t % 200L == 0L) glanceClf.ingestBarometerAltitude(500.0 + 0.55 * sin(PI * sec), t)
            if (t - gStepMs >= 370L) { glanceClf.ingestStepEvent(t); gStepMs = t }
            if (t % 250L == 0L) {
                glanceClf.ingestGnssSpeed(2.8, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                glanceClf.classify(t)
            }
        }
        assertEquals(
            "Verified wrist glance must still immediately switch to RUN_HANDHELD (0 ms)",
            PhoneCarriageMode.RUN_HANDHELD,
            glanceClf.currentMode
        )

        // 5. Fast asymmetric 2.0 s dismount/resume: WALKING_TRANSITION -> RUN_WAIST_HIP and RUN_WAIST_HIP -> STATIONARY
        val fastResume = PhoneCarriageClassifier()
        fastResume.setSportContext(SportContextHint.RUNNING)
        fastResume.setStepDetectorRegistered(true)
        var stepTs = -700L
        for (t in 0L..5_000L step 20L) {
            val sec = t / 1000.0
            fastResume.ingestRotationVector(quaternionFromEulerXDeg(76.0 + 8.0 * sin(2.0 * PI * 0.85 * sec)), t)
            fastResume.ingestGyro(0.18 * sin(2.0 * PI * 0.85 * sec), 0.18, t)
            if (t - stepTs >= 650L) { fastResume.ingestStepEvent(t); stepTs = t }
            if (t % 500L == 0L) {
                fastResume.ingestGnssSpeed(1.2, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                fastResume.classify(t)
            }
        }
        assertEquals(PhoneCarriageMode.WALKING_TRANSITION, fastResume.currentMode)
        // Resume running -> must commit RUN_WAIST_HIP within 2.5 s (dismountHysteresisMs = 2.0 s, NOT 4.0/6.0 s)
        for (t in 5_020L..7_500L step 20L) {
            val sec = t / 1000.0
            fastResume.ingestRotationVector(quaternionFromEulerXDeg(78.0 + 14.0 * sin(2.0 * PI * 1.3 * sec)), t)
            fastResume.ingestGyro(0.42 * sin(2.0 * PI * 1.3 * sec), 0.42, t)
            if (t - stepTs >= 360L) { fastResume.ingestStepEvent(t); stepTs = t }
            if (t % 500L == 0L) {
                fastResume.ingestGnssSpeed(2.8, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                fastResume.classify(t)
            }
        }
        assertEquals(
            "WALKING_TRANSITION -> RUN_WAIST_HIP must remain fast (2.0 s dismountHysteresisMs)",
            PhoneCarriageMode.RUN_WAIST_HIP,
            fastResume.currentMode
        )
        // Stop at traffic light -> RUN_WAIST_HIP -> STATIONARY must remain fast (2.0 s dismountHysteresisMs)
        for (t in 7_520L..12_500L step 20L) {
            fastResume.ingestRotationVector(quaternionFromEulerXDeg(78.0), t)
            fastResume.ingestGyro(0.01, 0.01, t)
            if (t % 500L == 0L) {
                fastResume.ingestGnssSpeed(0.0, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                fastResume.classify(t)
            }
        }
        assertEquals(
            "RUN_WAIST_HIP -> STATIONARY must remain fast (2.0 s dismountHysteresisMs)",
            PhoneCarriageMode.STATIONARY,
            fastResume.currentMode
        )
    }

    // REQ-FIL-039.1 / TST-FIL-039.1: Active-footstrike cycling mask (UNKNOWN/post-crash & >=5.5 m/s GNSS spike) + high-impact step-batching guard
    @Test
    fun testActiveFootstrikeCyclingMaskAndHighImpactBatchingGuard_eliminatesBikeFrontPocketBleed_tstFil039_1() {
        // 1. Sport = UNKNOWN (e.g., post-crash RESUME_SERVICE_RECREATION) with cycling-like front-pocket IMU
        //    AND active 2.75 Hz running footstrikes (both hardware step detector and accel-only) -> 0% BIKE_*
        for (withStepDetector in listOf(true, false)) {
            val clf = PhoneCarriageClassifier()
            clf.setSportContext(SportContextHint.UNKNOWN)
            clf.setStepDetectorRegistered(withStepDetector)
            var lastStepMs = -400L
            for (t in 0L..8_000L step 20L) {
                val sec = t / 1000.0
                val swingPhase = 2.0 * PI * 1.35 * sec
                val strikePhase = 2.0 * PI * 2.75 * sec
                // Cycling-like front-pocket IMU: swingSd ~ 17 deg, yawRms ~ 0.78, baroStepRms ~ 0.46, dom = 1.35 Hz
                clf.ingestRotationVector(quaternionFromEulerXDeg(76.0 + 24.0 * sin(swingPhase)), t)
                clf.ingestGyro(1.10 * sin(swingPhase), 1.10, t)
                clf.ingestAccelerometer(0.5 * sin(strikePhase), -0.4 * cos(strikePhase), 9.81 + 4.0 * sin(strikePhase), t)
                if (t % 200L == 0L) clf.ingestBarometerAltitude(430.0 + 0.36 * sin(PI * sec), t)
                if (withStepDetector && t - lastStepMs >= 364L) {
                    clf.ingestStepEvent(t)
                    lastStepMs = t
                }
                if (t % 1000L == 0L) {
                    clf.ingestGnssSpeed(3.1, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                    val mode = clf.classify(t)
                    assertFalse(
                        "Active running footstrikes (withStepDetector=$withStepDetector) under UNKNOWN sportHint must never emit BIKE_* at t=$t (was $mode)",
                        mode.isCyclingMode()
                    )
                }
            }
            assertTrue(
                "Active running footstrikes under UNKNOWN sportHint must settle in RUN_* (was ${clf.currentMode})",
                clf.currentMode.isRunningMode()
            )
        }

        // 2. Sport = RUN with transient 5.8 m/s (>= 5.5 m/s) GNSS speed spike + active 2.8 Hz footstrikes -> 0% BIKE_*
        val spikeClf = PhoneCarriageClassifier()
        spikeClf.setSportContext(SportContextHint.RUNNING)
        spikeClf.setStepDetectorRegistered(true)
        var spikeStepMs = -400L
        for (t in 0L..9_000L step 20L) {
            val sec = t / 1000.0
            val swingPhase = 2.0 * PI * 1.4 * sec
            val strikePhase = 2.0 * PI * 2.8 * sec
            spikeClf.ingestRotationVector(quaternionFromEulerXDeg(76.0 + 24.0 * sin(swingPhase)), t)
            spikeClf.ingestGyro(1.10 * sin(swingPhase), 1.10, t)
            spikeClf.ingestAccelerometer(0.4 * sin(strikePhase), 0.3 * cos(strikePhase), 9.81 + 4.0 * sin(strikePhase), t)
            if (t % 200L == 0L) spikeClf.ingestBarometerAltitude(430.0 + 0.36 * sin(PI * sec), t)
            if (t - spikeStepMs >= 357L) {
                spikeClf.ingestStepEvent(t)
                spikeStepMs = t
            }
            if (t % 1000L == 0L) {
                val speed = if (t >= 3_000L) 5.8 else 3.2
                spikeClf.ingestGnssSpeed(speed, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                val mode = spikeClf.classify(t)
                assertFalse(
                    "GNSS >= 5.5 m/s spike with active 2.8 Hz footstrikes must never emit BIKE_* at t=$t (was $mode)",
                    mode.isCyclingMode()
                )
            }
        }
        assertTrue(spikeClf.currentMode.isRunningMode())

        // 3. SensorHub step-batching gap (> 2.2 s) under UNKNOWN sportHint with high-impact trail running (impactRms >= 1.8 m/s^2)
        //    must NOT falsely confirm no-footstrikes or flip steady RUN_WAIST_HIP into BIKE_FRONT_POCKET.
        val batchGapClf = PhoneCarriageClassifier()
        batchGapClf.setSportContext(SportContextHint.UNKNOWN)
        batchGapClf.setStepDetectorRegistered(true)
        var gapStepMs = -400L
        for (t in 0L..6_000L step 20L) {
            val sec = t / 1000.0
            batchGapClf.ingestRotationVector(quaternionFromEulerXDeg(78.0 + 18.0 * sin(2.0 * PI * 1.35 * sec)), t)
            batchGapClf.ingestGyro(0.85 * sin(2.0 * PI * 1.35 * sec), 0.85, t)
            batchGapClf.ingestAccelerometer(0.3, 0.2, 9.81 + 4.0 * sin(2.0 * PI * 2.7 * sec), t)
            if (t % 200L == 0L) batchGapClf.ingestBarometerAltitude(430.0 + 0.28 * sin(PI * sec), t)
            if (t - gapStepMs >= 370L) { batchGapClf.ingestStepEvent(t); gapStepMs = t }
            if (t % 1000L == 0L) {
                batchGapClf.ingestGnssSpeed(3.0, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                batchGapClf.classify(t)
            }
        }
        assertEquals(PhoneCarriageMode.RUN_WAIST_HIP, batchGapClf.currentMode)

        // 5 s step-detector batching silence + uneven trail impacts (high RMS >= 2.2 m/s^2, non-periodic chirp)
        for (t in 6_020L..11_000L step 20L) {
            val sec = t / 1000.0
            batchGapClf.ingestRotationVector(quaternionFromEulerXDeg(78.0 + 18.0 * sin(2.0 * PI * 1.35 * sec)), t)
            batchGapClf.ingestGyro(0.85 * sin(2.0 * PI * 1.35 * sec), 0.85, t)
            // Chirped/uneven high-impact trail steps keeping bandpass RMS >= 2.2 m/s^2 while suppressing ACF peak
            val trailAccelZ = 9.81 + 3.8 * sin(2.0 * PI * 2.4 * sec + 2.2 * sin(1.7 * sec)) + 2.4 * cos(2.0 * PI * 3.55 * sec)
            batchGapClf.ingestAccelerometer(0.4, -0.3, trailAccelZ, t)
            if (t % 200L == 0L) batchGapClf.ingestBarometerAltitude(430.0 + 0.28 * sin(PI * sec), t)
            if (t % 1000L == 0L) {
                batchGapClf.ingestGnssSpeed(3.0, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                val mode = batchGapClf.classify(t)
                assertFalse(
                    "High-impact trail step-batching gap must not bleed into BIKE_* at t=$t (was $mode)",
                    mode.isCyclingMode()
                )
            }
        }
        assertEquals(PhoneCarriageMode.RUN_WAIST_HIP, batchGapClf.currentMode)
    }

    // REQ-FIL-039.2 / TST-FIL-039.2: Viewing-inclination glance guard rejects vertical belt rolls/slips & stow while preserving genuine wrist glances
    @Test
    fun testViewingInclinationGlanceGuard_rejectsVerticalBeltRollsAndPreservesWristGlances_tstFil039_2() {
        // 1. Vertical-plane belt rolls (18 deg AND 35 deg lateral roll at 80 deg base pitch -> recInclination > 60 deg)
        for (rollShiftDeg in listOf(18.0, 35.0)) {
            val beltClf = PhoneCarriageClassifier()
            beltClf.setSportContext(SportContextHint.RUNNING)
            beltClf.setStepDetectorRegistered(true)
            var stepMs = -400L
            var falseGlances = 0
            for (t in 0L..8_000L step 20L) {
                val sec = t / 1000.0
                val roll = if (t >= 4_500L) rollShiftDeg else 0.0
                val pitch = 80.0 + 5.0 * sin(2.0 * PI * 1.35 * sec)
                beltClf.ingestRotationVector(quaternionFromTiltAndRollDeg(pitch, roll), t)
                if (beltClf.isGlanceActive(t)) falseGlances++
                beltClf.ingestGyro(0.75 * sin(2.0 * PI * 1.35 * sec), 0.75, t)
                if (t % 200L == 0L) beltClf.ingestBarometerAltitude(450.0 + 0.30 * sin(PI * sec), t)
                if (t - stepMs >= 370L) { beltClf.ingestStepEvent(t); stepMs = t }
                if (t % 500L == 0L) {
                    beltClf.ingestGnssSpeed(2.8, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                    beltClf.classify(t)
                }
            }
            assertEquals("Vertical-plane ${rollShiftDeg} deg belt roll must trigger 0 false glances", 0, falseGlances)
            assertEquals(
                "Vertical-plane ${rollShiftDeg} deg belt roll must stay RUN_WAIST_HIP",
                PhoneCarriageMode.RUN_WAIST_HIP,
                beltClf.currentMode
            )
        }

        // 2. Hill torso-lean (85 deg -> 68 deg, recInclination = 68 deg > 60 deg) & stow (20 deg -> 75 deg) -> 0 glances
        val hillAndStowClf = PhoneCarriageClassifier()
        var hillStowGlances = 0
        for (t in 0L..6_000L step 20L) {
            val pitch = if (t < 3_500L) 85.0 else 68.0
            hillAndStowClf.ingestRotationVector(quaternionFromEulerXDeg(pitch), t)
            if (hillAndStowClf.isGlanceActive(t)) hillStowGlances++
        }
        assertEquals("Hill torso-lean 85->68 deg (recIncl > 60 deg) must trigger 0 false glances", 0, hillStowGlances)

        val stowClf = PhoneCarriageClassifier()
        var stowGlances = 0
        for (t in 0L..6_000L step 20L) {
            val pitch = if (t < 3_500L) 20.0 else 75.0
            stowClf.ingestRotationVector(quaternionFromEulerXDeg(pitch), t)
            if (stowClf.isGlanceActive(t)) stowGlances++
        }
        assertEquals("Stowing phone 20->75 deg must trigger 0 false glances", 0, stowGlances)

        // 3. Genuine mid-angle wrist glance (55 deg -> 30 deg, recIncl = 30 <= 60, drop = 25 >= 10) -> fires & switches 0 ms
        val midGlanceClf = PhoneCarriageClassifier()
        midGlanceClf.setSportContext(SportContextHint.RUNNING)
        midGlanceClf.setStepDetectorRegistered(true)
        var midStepMs = -400L
        for (t in 0L..4_500L step 20L) {
            val sec = t / 1000.0
            val pitch = if (t < 3_500L) 55.0 + 4.0 * sin(2.0 * PI * 1.4 * sec) else 30.0
            midGlanceClf.ingestRotationVector(quaternionFromEulerXDeg(pitch), t)
            midGlanceClf.ingestGyro(0.95 * sin(2.0 * PI * 1.4 * sec), 0.95, t)
            if (t % 200L == 0L) midGlanceClf.ingestBarometerAltitude(450.0 + 0.55 * sin(PI * sec), t)
            if (t - midStepMs >= 365L) { midGlanceClf.ingestStepEvent(t); midStepMs = t }
            if (t % 500L == 0L) {
                midGlanceClf.ingestGnssSpeed(2.9, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                midGlanceClf.classify(t)
            }
        }
        assertTrue("Genuine 55->30 deg wrist glance must trigger isGlanceActive", midGlanceClf.isGlanceActive(4_500L))
        assertEquals(PhoneCarriageMode.RUN_HANDHELD, midGlanceClf.currentMode)
    }

    // REQ-FIL-039.3 / TST-FIL-039.3: 45-min Salomon front-hip belt replay (>=85% RUN_WAIST_HIP, 0% BIKE_*, low chatter),
    // narrow BICEP vs WAIST corner (steady-state & cold-warmup recovery), 10 s non-glance WAIST->HANDHELD dwell & sustained handheld
    @Test
    fun testSalomonFrontHipBeltReplayNarrowBicepCornerAndTenSecondDwell_achievesHighWaistShare_tstFil039_3() {
        // 1. 45-minute (2,700 s) synthetic Salomon front-hip belt run at 155-170 spm (2.58-2.83 Hz)
        val clf = PhoneCarriageClassifier()
        clf.setSportContext(SportContextHint.RUNNING)
        clf.setStepDetectorRegistered(true)

        var steadyRunSec = 0
        var steadyWaistSec = 0
        var bikeSec = 0
        var transitions = 0
        var prevMode = PhoneCarriageMode.UNKNOWN
        var sawObstacleWalk = false
        var sawGlanceHandheld = false
        var switchesDuring8sBurst = 0
        var switchesDuringNarrowCorner = 0
        var lastStepMs = -400L

        // Helper segments across 2,700 s:
        //   0..299 s:   calm/moderate belt (swingSd ~12 deg, yawRms ~0.55, baro ~0.28)
        //   300..599 s: lively pelvic-yaw belt (swingSd ~16.5 deg, yawRms ~0.78, baro ~0.40) + 18 deg roll at 420 s
        //   600..899 s: bouncy/hill belt (swingSd ~17.0 deg, yawRms ~0.82, baro ~0.55) + 35 deg roll at 720 s
        //   900..1199s: post-crash UNKNOWN sportHint simulation with strong pelvic yaw (swingSd ~15 deg, yawRms ~1.02, baro ~0.52)
        //   1200..1229s: steady-state narrow BICEP vs WAIST corner (swingSd ~16.5 deg, yawRms ~0.55, baro ~0.30, dom = 0.5*step)
        //   1230..1237s: 8 s non-glance extreme handheld-like bounce burst (< 10 s WAIST->HANDHELD dwell -> 0 switches)
        //   1238..1799s: lively front-hip belt with 158-168 spm cadence
        //   1800..1829s: genuine 30 s slow fallen-tree obstacle walk (1.2 m/s, 1.55 Hz, low impact -> WALKING_TRANSITION)
        //   1830..2399s: bouncy front-hip belt back to running
        //   2400..2414s: genuine 15 s wrist-glance screen check (78 -> 20 deg -> immediate 0 ms RUN_HANDHELD)
        //   2415..2699s: front-hip belt finish (recovers back to RUN_WAIST_HIP)
        for (t in 0L..2_700_000L step 20L) {
            val secInt = (t / 1000L).toInt()
            val sec = t / 1000.0
            val inObstacleWalk = secInt in 1800..1829
            val inGlanceWindow = secInt in 2400..2414
            val in8sBurst = secInt in 1230..1237
            val inNarrowCorner = secInt in 1200..1229
            val isSteadyRun = !inObstacleWalk && !inGlanceWindow && secInt >= 5 && secInt !in 2415..2440

            // Simulate post-crash UNKNOWN sportHint from t=900..1199 s (verifying 0% BIKE_* even without hint)
            if (t == 900_000L) clf.setSportContext(SportContextHint.UNKNOWN)
            if (t == 1_200_000L) clf.setSportContext(SportContextHint.RUNNING)

            val stepHz: Double
            val pitchAmpDeg: Double
            val basePitchDeg: Double
            val rollDeg: Double
            val oscHz: Double
            val yawAmp: Double
            val baroAmp: Double
            val impactAmp: Double
            val speedMps: Double

            when {
                inObstacleWalk -> {
                    stepHz = 1.55; basePitchDeg = 76.0; pitchAmpDeg = 8.0; rollDeg = 0.0
                    oscHz = 0.85; yawAmp = 0.22; baroAmp = 0.16; impactAmp = 0.5; speedMps = 1.2
                }
                inGlanceWindow -> {
                    stepHz = 2.70; basePitchDeg = if (t >= 2_401_500L) 20.0 else 78.0
                    pitchAmpDeg = if (t >= 2_401_500L) 1.5 else 24.0; rollDeg = 0.0
                    oscHz = 1.35; yawAmp = 1.15; baroAmp = 0.58; impactAmp = 3.5; speedMps = 2.9
                }
                in8sBurst -> {
                    stepHz = 2.72; basePitchDeg = 74.0; pitchAmpDeg = 27.0; rollDeg = 0.0
                    oscHz = 1.36; yawAmp = 1.15; baroAmp = 0.65; impactAmp = 3.8; speedMps = 3.0
                }
                inNarrowCorner -> {
                    // Narrow Gate 2 corner: swingSd ~ 16.5 deg (amp 23.3), yawRms ~ 0.55 (amp 0.78), baro ~ 0.30 (amp 0.23)
                    stepHz = 2.70; basePitchDeg = 78.0; pitchAmpDeg = 23.3; rollDeg = 0.0
                    oscHz = 1.35; yawAmp = 0.78; baroAmp = 0.23; impactAmp = 3.5; speedMps = 2.9
                }
                secInt in 300..599 -> {
                    stepHz = 2.70; basePitchDeg = 78.0; pitchAmpDeg = 23.3; rollDeg = if (secInt >= 420) 18.0 else 0.0
                    oscHz = 1.35; yawAmp = 1.10; baroAmp = 0.31; impactAmp = 3.6; speedMps = 2.9
                }
                secInt in 600..899 -> {
                    stepHz = 2.65; basePitchDeg = 78.0; pitchAmpDeg = 24.0; rollDeg = if (secInt >= 720) 35.0 else 0.0
                    oscHz = 1.32; yawAmp = 1.16; baroAmp = 0.43; impactAmp = 3.6; speedMps = 2.7
                }
                secInt in 900..1199 -> {
                    stepHz = 2.78; basePitchDeg = 78.0; pitchAmpDeg = 21.2; rollDeg = 10.0
                    oscHz = 1.39; yawAmp = 1.44; baroAmp = 0.41; impactAmp = 3.8; speedMps = 3.1
                }
                else -> {
                    stepHz = 2.68; basePitchDeg = 78.0; pitchAmpDeg = 17.0; rollDeg = 0.0
                    oscHz = 1.34; yawAmp = 0.80; baroAmp = 0.25; impactAmp = 3.4; speedMps = 2.85
                }
            }

            val oscPhase = 2.0 * PI * oscHz * sec
            val strikePhase = 2.0 * PI * stepHz * sec
            clf.ingestRotationVector(
                quaternionFromTiltAndRollDeg(basePitchDeg + pitchAmpDeg * sin(oscPhase), rollDeg),
                t
            )
            val yawRate = yawAmp * sin(oscPhase)
            clf.ingestGyro(yawRate, kotlin.math.abs(yawRate), t)
            clf.ingestAccelerometer(0.4 * sin(strikePhase), 0.3 * cos(strikePhase), 9.81 + impactAmp * sin(strikePhase), t)
            if (t % 200L == 0L) {
                clf.ingestBarometerAltitude(450.0 + baroAmp * sin(PI * sec), t)
            }
            val stepIntervalMs = (1000.0 / stepHz).toLong()
            if (t - lastStepMs >= stepIntervalMs) {
                clf.ingestStepEvent(t)
                lastStepMs = t
            }
            if (t % 1000L == 0L) {
                clf.ingestGnssSpeed(speedMps, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                val mode = clf.classify(t)
                if (mode != prevMode && prevMode != PhoneCarriageMode.UNKNOWN) {
                    transitions++
                    if (in8sBurst) switchesDuring8sBurst++
                    if (inNarrowCorner) switchesDuringNarrowCorner++
                }
                prevMode = mode
                if (mode.isCyclingMode()) bikeSec++
                if (inObstacleWalk && mode == PhoneCarriageMode.WALKING_TRANSITION) sawObstacleWalk = true
                if (inGlanceWindow && mode == PhoneCarriageMode.RUN_HANDHELD) sawGlanceHandheld = true
                if (isSteadyRun) {
                    steadyRunSec++
                    if (mode == PhoneCarriageMode.RUN_WAIST_HIP) steadyWaistSec++
                }
            }
        }

        val waistRatio = steadyWaistSec.toDouble() / steadyRunSec.toDouble()
        assertEquals("BIKE_* must record 0 s (0%) throughout the 45-min run", 0, bikeSec)
        assertTrue(
            "Steady front-hip belt running must classify >= 85% as RUN_WAIST_HIP (got ${"%.1f".format(waistRatio * 100)}% = $steadyWaistSec/$steadyRunSec s)",
            waistRatio >= 0.85
        )
        assertTrue(
            "45-min front-hip belt run must have drastically reduced transitions << 111 (got $transitions)",
            transitions <= 10
        )
        assertEquals("Steady-state narrow BICEP vs WAIST corner must produce 0 switches (SCORE_MARGIN=0.85)", 0, switchesDuringNarrowCorner)
        assertEquals("8 s non-glance bounce burst must produce 0 switches (10 s WAIST->HANDHELD dwell)", 0, switchesDuring8sBurst)
        assertTrue("Genuine slow obstacle walk must enter WALKING_TRANSITION", sawObstacleWalk)
        assertTrue("Genuine wrist glance must enter RUN_HANDHELD", sawGlanceHandheld)

        // 2. Cold-warmup starting directly inside the narrow BICEP vs WAIST corner recovers cleanly to RUN_WAIST_HIP
        val warmupCornerClf = PhoneCarriageClassifier()
        warmupCornerClf.setSportContext(SportContextHint.RUNNING)
        warmupCornerClf.setStepDetectorRegistered(true)
        var wStepMs = -400L
        for (t in 0L..16_000L step 20L) {
            val sec = t / 1000.0
            val oscPhase = 2.0 * PI * 1.35 * sec
            val strikePhase = 2.0 * PI * 2.70 * sec
            // First 4 s inside narrow corner (swingSd ~16.5, yawRms ~0.55, baro ~0.30), then typical lively belt (yawRms ~0.78, baro ~0.42)
            val yawAmp = if (t < 4_000L) 0.78 else 1.10
            val baroAmp = if (t < 4_000L) 0.23 else 0.33
            warmupCornerClf.ingestRotationVector(quaternionFromEulerXDeg(78.0 + 23.3 * sin(oscPhase)), t)
            warmupCornerClf.ingestGyro(yawAmp * sin(oscPhase), yawAmp, t)
            warmupCornerClf.ingestAccelerometer(0.4 * sin(strikePhase), 0.3 * cos(strikePhase), 9.81 + 3.5 * sin(strikePhase), t)
            if (t % 200L == 0L) warmupCornerClf.ingestBarometerAltitude(450.0 + baroAmp * sin(PI * sec), t)
            if (t - wStepMs >= 370L) { warmupCornerClf.ingestStepEvent(t); wStepMs = t }
            if (t % 1000L == 0L) {
                warmupCornerClf.ingestGnssSpeed(2.9, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                warmupCornerClf.classify(t)
            }
        }
        assertEquals(
            "Cold-warmup inside narrow BICEP vs WAIST corner must recover cleanly to RUN_WAIST_HIP",
            PhoneCarriageMode.RUN_WAIST_HIP,
            warmupCornerClf.currentMode
        )

        // 3. Sustained 12 s non-glance handheld transition (> 10 s dwell) DOES commit RUN_HANDHELD
        val sustainedHandClf = PhoneCarriageClassifier()
        sustainedHandClf.setSportContext(SportContextHint.RUNNING)
        sustainedHandClf.setStepDetectorRegistered(true)
        var sStepMs = -400L
        for (t in 0L..5_000L step 20L) {
            val sec = t / 1000.0
            sustainedHandClf.ingestRotationVector(quaternionFromEulerXDeg(78.0 + 16.0 * sin(2.0 * PI * 1.35 * sec)), t)
            sustainedHandClf.ingestGyro(0.75 * sin(2.0 * PI * 1.35 * sec), 0.75, t)
            if (t % 200L == 0L) sustainedHandClf.ingestBarometerAltitude(450.0 + 0.25 * sin(PI * sec), t)
            if (t - sStepMs >= 370L) { sustainedHandClf.ingestStepEvent(t); sStepMs = t }
            if (t % 1000L == 0L) {
                sustainedHandClf.ingestGnssSpeed(2.9, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                sustainedHandClf.classify(t)
            }
        }
        assertEquals(PhoneCarriageMode.RUN_WAIST_HIP, sustainedHandClf.currentMode)
        for (t in 5_020L..21_000L step 20L) {
            val sec = t / 1000.0
            sustainedHandClf.ingestRotationVector(quaternionFromEulerXDeg(70.0 + 27.0 * sin(2.0 * PI * 1.4 * sec)), t)
            sustainedHandClf.ingestGyro(1.15 * sin(2.0 * PI * 1.4 * sec), 1.15, t)
            if (t % 200L == 0L) sustainedHandClf.ingestBarometerAltitude(450.0 + 0.65 * sin(PI * sec), t)
            if (t - sStepMs >= 360L) { sustainedHandClf.ingestStepEvent(t); sStepMs = t }
            if (t % 1000L == 0L) {
                sustainedHandClf.ingestGnssSpeed(3.1, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                sustainedHandClf.classify(t)
            }
        }
        assertEquals(
            "Sustained > 10 s non-glance handheld running must transition RUN_WAIST_HIP -> RUN_HANDHELD",
            PhoneCarriageMode.RUN_HANDHELD,
            sustainedHandClf.currentMode
        )
    }

    // REQ-FIL-042.3 / TST-FIL-042.3: Out-of-order lagged classify() / GNSS timestamps must never seed
    // candidateSinceMs in the past relative to lastEventTimestampMs or bypass dismountHysteresisMs (2.0 s).
    @Test
    fun testOutOfOrderLaggedClassifyAndGnss_preservesFullDismountDwellWithoutBypass_TST_FIL_042_3() {
        val clf = PhoneCarriageClassifier()
        clf.setSportContext(SportContextHint.CYCLING)

        // 1. Establish steady BIKE_FRONT_POCKET over t = 0..5_000 ms (no steps, 6.0 m/s, 1.35 Hz thigh swing)
        for (t in 0L..5_000L step 20L) {
            val sec = t / 1000.0
            val phase = 2.0 * PI * 1.35 * sec
            clf.ingestRotationVector(quaternionFromEulerXDeg(60.0 + 20.0 * sin(phase)), t)
            clf.ingestGyro(0.75 * sin(phase), 0.75, t)
            if (t % 200L == 0L) clf.ingestBarometerAltitude(430.0 + 0.55 * sin(phase), t)
            if (t % 1000L == 0L) {
                clf.ingestGnssSpeed(6.0, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                clf.classify(t)
            }
        }
        assertEquals(PhoneCarriageMode.BIKE_FRONT_POCKET, clf.currentMode)

        // 2. Feed walking-speed cue at t = 5_400 ms, then invoke classify() with a -2,182 ms lagged timestamp (3_218 ms)
        //    followed 450 ms later by IMU classify(5_850 ms). Without the monotonic guard, 5_850 - 3_218 = 2_632 >= 2_000 ms
        //    would immediately bypass dismountHysteresisMs!
        clf.ingestGnssSpeed(1.2, bearingDeg = 90.0, accuracyMeters = 5.0, timestampMs = 5_400L)
        for (stepT in listOf(5_050L, 5_600L, 6_150L)) {
            if (stepT <= 5_400L) clf.ingestStepEvent(stepT)
        }
        assertEquals(PhoneCarriageMode.BIKE_FRONT_POCKET, clf.classify(5_400L - 2_182L))

        // Out-of-order older GNSS fix (3_218 ms) must not overwrite newer 5_400 ms GNSS speed
        clf.ingestGnssSpeed(0.0, bearingDeg = 0.0, accuracyMeters = 5.0, timestampMs = 3_218L)

        // Advance only 450 ms (to 5_850 ms) and 1_400 ms (to 6_800 ms) — BOTH < 2_000 ms dwell -> MUST stay BIKE_FRONT_POCKET
        for (t in 5_420L..6_800L step 20L) {
            val sec = t / 1000.0
            clf.ingestRotationVector(quaternionFromEulerXDeg(72.0 + 8.0 * sin(2.0 * PI * 0.9 * sec)), t)
            clf.ingestGyro(0.22 * sin(2.0 * PI * 0.9 * sec), 0.22, t)
            if (t % 550L == 0L) clf.ingestStepEvent(t)
            if (t == 5_850L || t == 6_800L) {
                assertEquals(
                    "Full 2.0 s dismount dwell must hold at t=$t despite earlier -2.18 s lagged classify()",
                    PhoneCarriageMode.BIKE_FRONT_POCKET,
                    clf.classify(t)
                )
            }
        }

        // Advance past full 2.0 s dwell (to 7_600 ms = +2.2 s from 5_400 ms) -> now commits WALKING_TRANSITION cleanly
        for (t in 6_820L..7_600L step 20L) {
            val sec = t / 1000.0
            clf.ingestRotationVector(quaternionFromEulerXDeg(72.0 + 8.0 * sin(2.0 * PI * 0.9 * sec)), t)
            clf.ingestGyro(0.22 * sin(2.0 * PI * 0.9 * sec), 0.22, t)
            if (t % 550L == 0L) clf.ingestStepEvent(t)
        }
        assertEquals(
            "Genuine sustained >= 2.0 s walking dismount must commit WALKING_TRANSITION",
            PhoneCarriageMode.WALKING_TRANSITION,
            clf.classify(7_600L)
        )
    }

    // REQ-FIL-043.1, REQ-FIL-043.2 / TST-FIL-043.1: Bank-stop departure continuous non-stationary dwell,
    // <= 1.0 s fast exit across shifting subclasses (WALK -> BIKE_JERSEY -> BIKE_FRONT_POCKET), post-exit
    // candidate sync, and brief fidget reset on return to STATIONARY.
    @Test
    fun testBankStopDeparture_continuousNonStationaryDwellAndFastExitWithinOneSecond_TST_FIL_043_1() {
        val clf = PhoneCarriageClassifier()
        clf.setSportContext(SportContextHint.CYCLING)
        val baseLat = 48.7800
        val baseLon = 9.1800
        val degLatPerMeter = 1.0 / 111_195.0

        // 1. Establish steady STATIONARY at Bank stop (t = 0..6_000 ms)
        for (t in 0L..6_000L step 20L) {
            clf.ingestRotationVector(quaternionFromEulerXDeg(80.0), t)
            clf.ingestGyro(0.01, 0.02, t)
            if (t % 1_000L == 0L) {
                clf.ingestGnssSpeed(0.0, bearingDeg = 90.0, accuracyMeters = 16.0, timestampMs = t, latitude = baseLat, longitude = baseLon)
                clf.classify(t)
            }
        }
        assertEquals(PhoneCarriageMode.STATIONARY, clf.currentMode)

        // 2. Accelerate out of stop mirroring 2026-11-21_081228 rows 404..409:
        //    t=6..7s: pushing off (1.6 m/s -> WALKING_TRANSITION)
        //    t=7..8s: accelerating (2.42 m/s, acc=15.8 m -> BIKE_JERSEY_BACK)
        //    t=8..9s: crossing 3.2 m/s (3.88 m/s, acc=13.7 m -> BIKE_FRONT_POCKET)
        //    t=9..11s: 4.60 -> 5.50 -> 6.10 m/s (acc=12.2 m, > 38 m displacement)
        val gnssProfile = mapOf(
            7_000L to Triple(1.60, 15.8, 3.0),
            8_000L to Triple(2.42, 15.8, 8.0),
            9_000L to Triple(3.88, 13.7, 15.0),
            10_000L to Triple(4.60, 12.2, 24.0),
            11_000L to Triple(5.50, 12.2, 34.0),
            12_000L to Triple(6.10, 12.2, 42.0)
        )
        var firstExitedAtMs: Long? = null
        var stationaryAbove5MpsSeconds = 0
        for (t in 6_020L..12_000L step 20L) {
            val sec = t / 1000.0
            when {
                t <= 7_000L -> {
                    // Pushing off: slow walking pendulum
                    val phase = 2.0 * PI * 0.9 * sec
                    clf.ingestRotationVector(quaternionFromEulerXDeg(74.0 + 8.0 * sin(phase)), t)
                    clf.ingestGyro(0.25 * sin(phase), 0.25, t)
                }
                t <= 8_000L -> {
                    // Jersey back moderate torso tilt
                    val phase = 2.0 * PI * 1.1 * sec
                    clf.ingestRotationVector(quaternionFromEulerXDeg(50.0 + 5.0 * sin(phase)), t)
                    clf.ingestGyro(0.28 * sin(phase), 0.28, t)
                }
                else -> {
                    // Front pocket thigh pedaling 1.3 Hz
                    val phase = 2.0 * PI * 1.3 * sec
                    clf.ingestRotationVector(quaternionFromEulerXDeg(60.0 + 20.0 * sin(phase)), t)
                    clf.ingestGyro(0.75 * sin(phase), 0.75, t)
                }
            }
            val fix = gnssProfile[t]
            if (fix != null) {
                val (speed, acc, distM) = fix
                clf.ingestGnssSpeed(
                    speedMps = speed,
                    bearingDeg = 0.0,
                    accuracyMeters = acc,
                    timestampMs = t,
                    latitude = baseLat + distM * degLatPerMeter,
                    longitude = baseLon
                )
                val mode = clf.classify(t)
                if (firstExitedAtMs == null && mode != PhoneCarriageMode.STATIONARY) {
                    firstExitedAtMs = t
                }
                if (speed > 5.0 && mode == PhoneCarriageMode.STATIONARY) {
                    stationaryAbove5MpsSeconds++
                }
            }
        }
        assertTrue(
            "Must exit STATIONARY within <= 1 s of crossing 3.2 m/s (at 9_000 ms), got exit at $firstExitedAtMs",
            firstExitedAtMs != null && firstExitedAtMs <= 10_000L
        )
        assertEquals("0 seconds above 5.0 m/s may remain STATIONARY", 0, stationaryAbove5MpsSeconds)
        assertTrue("Final committed mode must be active cycling, got ${clf.currentMode}", clf.currentMode.isCyclingMode())

        // 3. Verify post-exit state sync: immediate 1.0 s non-matching candidate after STATIONARY exit
        //    must NOT inherit old nonStationarySinceMs and bypass 2.0 s dismountHysteresisMs
        val syncClf = PhoneCarriageClassifier()
        syncClf.setSportContext(SportContextHint.CYCLING)
        for (t in 0L..5_000L step 20L) {
            syncClf.ingestRotationVector(quaternionFromEulerXDeg(80.0), t)
            syncClf.ingestGyro(0.01, 0.02, t)
            if (t % 1_000L == 0L) {
                syncClf.ingestGnssSpeed(0.0, bearingDeg = 0.0, accuracyMeters = 8.0, timestampMs = t, latitude = baseLat, longitude = baseLon)
                syncClf.classify(t)
            }
        }
        assertEquals(PhoneCarriageMode.STATIONARY, syncClf.currentMode)
        // Cold launch straight to >= 3.2 m/s with subclass flip (t=6s JERSEY -> t=7s FRONT_POCKET):
        // 0 ms dwell at t=6_000 stays STATIONARY; 1_000 ms dwell at t=7_000 exits despite subclass flip!
        for (t in 5_020L..7_000L step 20L) {
            val sec = t / 1000.0
            if (t <= 6_000L) {
                syncClf.ingestRotationVector(quaternionFromEulerXDeg(50.0 + 5.0 * sin(2.0 * PI * 1.1 * sec)), t)
                syncClf.ingestGyro(0.28 * sin(2.0 * PI * 1.1 * sec), 0.28, t)
            } else {
                syncClf.ingestRotationVector(quaternionFromEulerXDeg(60.0 + 20.0 * sin(2.0 * PI * 1.35 * sec)), t)
                syncClf.ingestGyro(0.75 * sin(2.0 * PI * 1.35 * sec), 0.75, t)
            }
            if (t == 6_000L) {
                syncClf.ingestGnssSpeed(3.88, bearingDeg = 0.0, accuracyMeters = 10.0, timestampMs = t, latitude = baseLat + 12.0 * degLatPerMeter, longitude = baseLon)
                assertEquals("Single-sample launch must hold 1.0 s fast-exit dwell", PhoneCarriageMode.STATIONARY, syncClf.classify(t))
            } else if (t == 7_000L) {
                syncClf.ingestGnssSpeed(5.50, bearingDeg = 0.0, accuracyMeters = 10.0, timestampMs = t, latitude = baseLat + 20.0 * degLatPerMeter, longitude = baseLon)
                assertTrue("Continuous 1.0 s fast exit across subclass flip must commit cycling", syncClf.classify(t).isCyclingMode())
            }
        }
        val exitedMode = syncClf.currentMode
        // Feed 1.0 s of walking cues (< 2.0 s dismountHysteresisMs) immediately post-exit -> must stay in exitedMode
        for (t in 7_020L..8_000L step 20L) {
            val sec = t / 1000.0
            syncClf.ingestRotationVector(quaternionFromEulerXDeg(74.0 + 8.0 * sin(2.0 * PI * 0.9 * sec)), t)
            syncClf.ingestGyro(0.25 * sin(2.0 * PI * 0.9 * sec), 0.25, t)
            if (t % 550L == 0L) syncClf.ingestStepEvent(t)
        }
        syncClf.ingestGnssSpeed(1.2, bearingDeg = 0.0, accuracyMeters = 6.0, timestampMs = 8_000L)
        assertEquals(
            "Post-exit candidateMode/candidateSinceMs sync must preserve full 2.0 s dismount dwell",
            exitedMode,
            syncClf.classify(8_000L)
        )

        // 4. Brief 1 s standstill fidget returning to STATIONARY clears nonStationarySinceMs (no accumulation)
        val fidgetClf = PhoneCarriageClassifier()
        for (t in 0L..5_000L step 20L) {
            fidgetClf.ingestRotationVector(quaternionFromEulerXDeg(80.0), t)
            fidgetClf.ingestGyro(0.01, 0.02, t)
            if (t % 1_000L == 0L) {
                fidgetClf.ingestGnssSpeed(0.0, accuracyMeters = 8.0, timestampMs = t)
                fidgetClf.classify(t)
            }
        }
        assertEquals(PhoneCarriageMode.STATIONARY, fidgetClf.currentMode)
        // Fidget #1 (1.0 s at 1.2 m/s) -> still STATIONARY
        for (t in 5_020L..6_000L step 20L) {
            fidgetClf.ingestRotationVector(quaternionFromEulerXDeg(74.0 + 8.0 * sin(2.0 * PI * 0.9 * t / 1000.0)), t)
            fidgetClf.ingestGyro(0.25, 0.25, t)
        }
        fidgetClf.ingestGnssSpeed(1.2, accuracyMeters = 8.0, timestampMs = 6_000L)
        assertEquals(PhoneCarriageMode.STATIONARY, fidgetClf.classify(6_000L))
        // Return to STATIONARY at t = 7_000 ms -> clears nonStationarySinceMs
        for (t in 6_020L..7_000L step 20L) {
            fidgetClf.ingestRotationVector(quaternionFromEulerXDeg(80.0), t)
            fidgetClf.ingestGyro(0.01, 0.02, t)
        }
        fidgetClf.ingestGnssSpeed(0.0, accuracyMeters = 8.0, timestampMs = 7_000L)
        assertEquals(PhoneCarriageMode.STATIONARY, fidgetClf.classify(7_000L))
        // Fidget #2 (1.0 s at 1.2 m/s at t = 8_000 ms) -> must still stay STATIONARY (timer was cleared!)
        for (t in 7_020L..8_000L step 20L) {
            fidgetClf.ingestRotationVector(quaternionFromEulerXDeg(74.0 + 8.0 * sin(2.0 * PI * 0.9 * t / 1000.0)), t)
            fidgetClf.ingestGyro(0.25, 0.25, t)
        }
        fidgetClf.ingestGnssSpeed(1.2, accuracyMeters = 8.0, timestampMs = 8_000L)
        assertEquals(
            "Returning to STATIONARY must clear nonStationarySinceMs so disjoint 1 s fidgets never accumulate",
            PhoneCarriageMode.STATIONARY,
            fidgetClf.classify(8_000L)
        )
    }

    // REQ-FIL-043.1, REQ-FIL-043.2 / TST-FIL-043.2: Cross-device portability — barometer-free/wheel-free/
    // permission-denied cycling & running (incl. omitted null accuracy & empty positionSamples), wheel-active
    // launch, rigid handlebar empty/stale vs >= 10 m fresh displacement, and slow-walk continuous 2.0 s exit.
    @Test
    fun testCrossDeviceStationaryFastExit_baroFreeWheelFreeWheelActiveAndRigidHandlebar_TST_FIL_043_2() {
        val baseLat = 48.7800
        val baseLon = 9.1800
        val degLatPerMeter = 1.0 / 111_195.0

        // (a1) Barometer-free, wheel-free, stepDetectorRegistered=false cycling with omitted accuracyMeters=null & empty positionSamples
        val bikeClf = PhoneCarriageClassifier()
        bikeClf.setSportContext(SportContextHint.CYCLING)
        bikeClf.setStepDetectorRegistered(false)
        for (t in 0L..4_000L step 20L) {
            bikeClf.ingestRotationVector(quaternionFromEulerXDeg(80.0), t)
            bikeClf.ingestGyro(0.01, 0.02, t)
            if (t % 1_000L == 0L) {
                bikeClf.ingestGnssSpeed(0.0, accuracyMeters = null, timestampMs = t)
                bikeClf.classify(t)
            }
        }
        assertEquals(PhoneCarriageMode.STATIONARY, bikeClf.currentMode)
        for (t in 4_020L..6_000L step 20L) {
            val phase = 2.0 * PI * 1.35 * (t / 1000.0)
            bikeClf.ingestRotationVector(quaternionFromEulerXDeg(60.0 + 20.0 * sin(phase)), t)
            bikeClf.ingestGyro(0.75 * sin(phase), 0.75, t)
            if (t == 5_000L || t == 6_000L) {
                bikeClf.ingestGnssSpeed(4.2, bearingDeg = 90.0, accuracyMeters = null, timestampMs = t)
                bikeClf.classify(t)
            }
        }
        assertTrue(
            "Baro-free/wheel-free cycling with omitted null accuracy & empty positionSamples must fast-exit <= 1 s",
            bikeClf.currentMode.isCyclingMode()
        )

        // (a2) Barometer-free, wheel-free, permission-denied running acceleration >= 3.2 m/s
        val runClf = PhoneCarriageClassifier()
        runClf.setSportContext(SportContextHint.RUNNING)
        runClf.setStepDetectorRegistered(false)
        for (t in 0L..4_000L step 20L) {
            runClf.ingestRotationVector(quaternionFromEulerXDeg(80.0), t)
            runClf.ingestGyro(0.01, 0.02, t)
            if (t % 1_000L == 0L) {
                runClf.ingestGnssSpeed(0.0, accuracyMeters = 8.0, timestampMs = t)
                runClf.classify(t)
            }
        }
        assertEquals(PhoneCarriageMode.STATIONARY, runClf.currentMode)
        for (t in 4_020L..6_000L step 20L) {
            val sec = t / 1000.0
            val osc = 2.0 * PI * 1.4 * sec
            val strike = 2.0 * PI * 2.8 * sec
            runClf.ingestRotationVector(quaternionFromEulerXDeg(76.0 + 18.0 * sin(osc)), t)
            runClf.ingestGyro(0.85 * sin(osc), 0.85, t)
            runClf.ingestAccelerometer(0.4 * sin(strike), 0.3 * cos(strike), 9.81 + 3.6 * sin(strike), t)
            if (t == 5_000L || t == 6_000L) {
                runClf.ingestGnssSpeed(3.5, bearingDeg = 90.0, accuracyMeters = 8.0, timestampMs = t)
                runClf.classify(t)
            }
        }
        assertTrue(
            "Baro-free/wheel-free/permission-denied running >= 3.2 m/s must fast-exit <= 1 s",
            runClf.currentMode.isRunningMode()
        )

        // (b) wheelActiveMoving cycling launch exits <= 1 s even with quiet handlebar IMU & coarse GNSS
        val wheelClf = PhoneCarriageClassifier()
        wheelClf.setSportContext(SportContextHint.CYCLING)
        for (t in 0L..4_000L step 20L) {
            wheelClf.ingestRotationVector(quaternionFromEulerXDeg(20.0), t)
            wheelClf.ingestGyro(0.01, 0.02, t)
            if (t % 1_000L == 0L) {
                wheelClf.ingestWheelSpeed(0.0, t)
                wheelClf.ingestGnssSpeed(0.0, accuracyMeters = 45.0, timestampMs = t)
                wheelClf.classify(t)
            }
        }
        assertEquals(PhoneCarriageMode.STATIONARY, wheelClf.currentMode)
        for (t in 4_020L..6_000L step 20L) {
            wheelClf.ingestRotationVector(quaternionFromEulerXDeg(20.0), t)
            wheelClf.ingestGyro(0.01, 0.02, t)
            if (t == 5_000L || t == 6_000L) {
                wheelClf.ingestWheelSpeed(4.5, t)
                wheelClf.classify(t)
            }
        }
        assertEquals(
            "wheelActiveMoving launch must exit STATIONARY within <= 1 s into BIKE_HANDLEBAR",
            PhoneCarriageMode.BIKE_HANDLEBAR,
            wheelClf.currentMode
        )

        // (c1) Rigid handlebar (quietImu=true, no wheel/baro): empty positionSamples blocks 1.0 s fast exit
        val emptyPosBarClf = PhoneCarriageClassifier()
        emptyPosBarClf.setSportContext(SportContextHint.CYCLING)
        for (t in 0L..4_000L step 20L) {
            emptyPosBarClf.ingestRotationVector(quaternionFromEulerXDeg(20.0), t)
            emptyPosBarClf.ingestGyro(0.01, 0.02, t)
            if (t % 1_000L == 0L) {
                emptyPosBarClf.ingestGnssSpeed(0.0, accuracyMeters = 6.0, timestampMs = t)
                emptyPosBarClf.classify(t)
            }
        }
        assertEquals(PhoneCarriageMode.STATIONARY, emptyPosBarClf.currentMode)
        for (t in 4_020L..6_000L step 20L) {
            emptyPosBarClf.ingestRotationVector(quaternionFromEulerXDeg(20.0), t)
            emptyPosBarClf.ingestGyro(0.01, 0.02, t)
            if (t == 5_000L || t == 6_000L) {
                emptyPosBarClf.ingestGnssSpeed(5.0, bearingDeg = 0.0, accuracyMeters = 6.0, timestampMs = t)
                emptyPosBarClf.classify(t)
            }
        }
        assertEquals(
            "Quiet-IMU rigid handlebar with empty positionSamples must NOT fast-exit at 1.0 s",
            PhoneCarriageMode.STATIONARY,
            emptyPosBarClf.currentMode
        )

        // (c2) Rigid handlebar (quietImu=true, no wheel/baro): fresh >= 10 m trusted displacement DOES fast-exit <= 1.0 s
        val dispBarClf = PhoneCarriageClassifier()
        dispBarClf.setSportContext(SportContextHint.CYCLING)
        for (t in 0L..4_000L step 20L) {
            dispBarClf.ingestRotationVector(quaternionFromEulerXDeg(20.0), t)
            dispBarClf.ingestGyro(0.01, 0.02, t)
            if (t % 1_000L == 0L) {
                dispBarClf.ingestGnssSpeed(0.0, accuracyMeters = 6.0, timestampMs = t, latitude = baseLat, longitude = baseLon)
                dispBarClf.classify(t)
            }
        }
        assertEquals(PhoneCarriageMode.STATIONARY, dispBarClf.currentMode)
        for (t in 4_020L..6_000L step 20L) {
            dispBarClf.ingestRotationVector(quaternionFromEulerXDeg(20.0), t)
            dispBarClf.ingestGyro(0.01, 0.02, t)
            if (t == 5_000L) {
                dispBarClf.ingestGnssSpeed(4.5, bearingDeg = 0.0, accuracyMeters = 6.0, timestampMs = t, latitude = baseLat + 6.0 * degLatPerMeter, longitude = baseLon)
                assertEquals(PhoneCarriageMode.STATIONARY, dispBarClf.classify(t))
            } else if (t == 6_000L) {
                dispBarClf.ingestGnssSpeed(5.0, bearingDeg = 0.0, accuracyMeters = 6.0, timestampMs = t, latitude = baseLat + 14.0 * degLatPerMeter, longitude = baseLon)
                assertEquals(
                    "Rigid handlebar with fresh >= 10 m trusted displacement must fast-exit <= 1.0 s",
                    PhoneCarriageMode.BIKE_HANDLEBAR,
                    dispBarClf.classify(t)
                )
            }
        }

        // (d) Slow non-stationary walk (1.2 m/s -> 2.6 m/s < 3.2 m/s) shifting subclasses exits at 2.0 s (not 1.0 s, not reset!)
        val walkClf = PhoneCarriageClassifier()
        walkClf.setSportContext(SportContextHint.CYCLING)
        for (t in 0L..4_000L step 20L) {
            walkClf.ingestRotationVector(quaternionFromEulerXDeg(80.0), t)
            walkClf.ingestGyro(0.01, 0.02, t)
            if (t % 1_000L == 0L) {
                walkClf.ingestGnssSpeed(0.0, accuracyMeters = 6.0, timestampMs = t)
                walkClf.classify(t)
            }
        }
        assertEquals(PhoneCarriageMode.STATIONARY, walkClf.currentMode)
        for (t in 4_020L..7_000L step 20L) {
            val sec = t / 1000.0
            if (t <= 6_000L) {
                walkClf.ingestRotationVector(quaternionFromEulerXDeg(74.0 + 8.0 * sin(2.0 * PI * 0.9 * sec)), t)
                walkClf.ingestGyro(0.25 * sin(2.0 * PI * 0.9 * sec), 0.25, t)
            } else {
                walkClf.ingestRotationVector(quaternionFromEulerXDeg(50.0 + 5.0 * sin(2.0 * PI * 1.1 * sec)), t)
                walkClf.ingestGyro(0.28 * sin(2.0 * PI * 1.1 * sec), 0.28, t)
            }
            if (t == 5_000L) {
                walkClf.ingestGnssSpeed(1.2, accuracyMeters = 6.0, timestampMs = t)
                assertEquals(PhoneCarriageMode.STATIONARY, walkClf.classify(t))
            } else if (t == 6_000L) {
                walkClf.ingestGnssSpeed(1.3, accuracyMeters = 6.0, timestampMs = t)
                assertEquals("Sub-3.2 m/s walk must still hold 2.0 s dwell at +1.0 s", PhoneCarriageMode.STATIONARY, walkClf.classify(t))
            } else if (t == 7_000L) {
                walkClf.ingestGnssSpeed(2.6, accuracyMeters = 6.0, timestampMs = t)
                assertTrue(
                    "Continuous 2.0 s dwell across shifting sub-3.2 m/s subclasses must exit STATIONARY at +2.0 s",
                    walkClf.classify(t) != PhoneCarriageMode.STATIONARY
                )
            }
        }
    }

    // REQ-FIL-043.1, REQ-FIL-043.3, REQ-FIL-042.3 / TST-FIL-043.3: Indoor coarse (> 20 m) & invalid
    // (NaN, Infinity, 0.0, < 0.0) GNSS spikes never poison isPositionStagnant or exit STATIONARY,
    // stagnant optimistic-accuracy spikes stay STATIONARY, UNKNOWN dropout gap resets nonStationarySinceMs,
    // and lagged timestamps & reset() preserve monotonic hygiene.
    @Test
    fun testStationaryIndoorMultipathSpikeAndLaggedTimestamp_preservesStationaryImmunity_TST_FIL_043_3() {
        val baseLat = 48.7800
        val baseLon = 9.1800
        val degLatPerMeter = 1.0 / 111_195.0

        // 1. Coarse (> 20 m) & invalid (NaN, +Inf, 0.0, -1.0) accuracy spikes with +50 m jumps must NOT
        //    poison positionSamples / isPositionStagnant or trigger false exit from STATIONARY
        val untrustedAccuracies = listOf(25.0, 45.0, Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0)
        for (badAcc in untrustedAccuracies) {
            val clf = PhoneCarriageClassifier()
            clf.setSportContext(SportContextHint.CYCLING)
            for (t in 0L..5_000L step 20L) {
                clf.ingestRotationVector(quaternionFromEulerXDeg(80.0), t)
                clf.ingestGyro(0.01, 0.02, t)
                if (t % 1_000L == 0L) {
                    clf.ingestGnssSpeed(0.0, accuracyMeters = 8.0, timestampMs = t, latitude = baseLat, longitude = baseLon)
                    clf.classify(t)
                }
            }
            assertEquals("Baseline must be STATIONARY for acc=$badAcc", PhoneCarriageMode.STATIONARY, clf.currentMode)

            // Inject 2.0 s multipath spike (6.5 m/s + 50 m coordinate jump) with untrusted accuracy
            for (t in 5_020L..7_000L step 20L) {
                clf.ingestRotationVector(quaternionFromEulerXDeg(80.0), t)
                clf.ingestGyro(0.01, 0.02, t)
                if (t == 6_000L || t == 7_000L) {
                    clf.ingestGnssSpeed(
                        speedMps = 6.5,
                        bearingDeg = 0.0,
                        accuracyMeters = badAcc,
                        timestampMs = t,
                        latitude = baseLat + 50.0 * degLatPerMeter,
                        longitude = baseLon
                    )
                    assertEquals(
                        "Untrusted accuracy=$badAcc multipath spike must stay STATIONARY at t=$t",
                        PhoneCarriageMode.STATIONARY,
                        clf.classify(t)
                    )
                }
            }

            // Follow with optimistic-accuracy stagnant coordinate (same baseLat/baseLon) at 6.0 m/s for 2 s:
            // if the +50 m jump had poisoned positionSamples, isPositionStagnant() would return false and break STATIONARY!
            for (t in 7_020L..9_000L step 20L) {
                clf.ingestRotationVector(quaternionFromEulerXDeg(80.0), t)
                clf.ingestGyro(0.01, 0.02, t)
                if (t == 8_000L || t == 9_000L) {
                    clf.ingestGnssSpeed(
                        speedMps = 6.0,
                        bearingDeg = 0.0,
                        accuracyMeters = 5.0,
                        timestampMs = t,
                        latitude = baseLat,
                        longitude = baseLon
                    )
                    assertEquals(
                        "Untrusted acc=$badAcc jump must NOT poison isPositionStagnant at t=$t",
                        PhoneCarriageMode.STATIONARY,
                        clf.classify(t)
                    )
                }
            }
        }

        // 2. UNKNOWN dropout gap clears nonStationarySinceMs so later motion starts a fresh 1.0 s dwell
        val gapClf = PhoneCarriageClassifier()
        gapClf.setSportContext(SportContextHint.CYCLING)
        for (t in 0L..4_000L step 20L) {
            gapClf.ingestRotationVector(quaternionFromEulerXDeg(80.0), t)
            gapClf.ingestGyro(0.01, 0.02, t)
            if (t % 1_000L == 0L) {
                gapClf.ingestGnssSpeed(0.0, accuracyMeters = 6.0, timestampMs = t)
                gapClf.classify(t)
            }
        }
        assertEquals(PhoneCarriageMode.STATIONARY, gapClf.currentMode)
        // Brief non-stationary blip at t=5_000 ms (0 ms dwell -> seeds nonStationarySinceMs = 5_000)
        for (t in 4_020L..5_000L step 20L) {
            val phase = 2.0 * PI * 1.3 * (t / 1000.0)
            gapClf.ingestRotationVector(quaternionFromEulerXDeg(60.0 + 20.0 * sin(phase)), t)
            gapClf.ingestGyro(0.75 * sin(phase), 0.75, t)
        }
        gapClf.ingestGnssSpeed(4.0, accuracyMeters = 6.0, timestampMs = 5_000L)
        assertEquals(PhoneCarriageMode.STATIONARY, gapClf.classify(5_000L))
        // Induce UNKNOWN gap at t = 12_000 ms (> STALENESS_MS = 6_000 ms with non-quiet IMU & no GNSS/steps)
        for (t in 11_020L..12_000L step 20L) {
            val phase = 2.0 * PI * 1.3 * (t / 1000.0)
            gapClf.ingestRotationVector(quaternionFromEulerXDeg(60.0 + 20.0 * sin(phase)), t)
            gapClf.ingestGyro(0.75 * sin(phase), 0.75, t)
        }
        assertEquals(PhoneCarriageMode.STATIONARY, gapClf.classify(12_000L))
        // Resume high speed at t = 12_500 ms (0 ms fresh dwell): MUST stay STATIONARY (UNKNOWN cleared old 5_000 ms timer!)
        for (t in 12_020L..12_500L step 20L) {
            val phase = 2.0 * PI * 1.3 * (t / 1000.0)
            gapClf.ingestRotationVector(quaternionFromEulerXDeg(60.0 + 20.0 * sin(phase)), t)
            gapClf.ingestGyro(0.75 * sin(phase), 0.75, t)
        }
        gapClf.ingestGnssSpeed(5.0, accuracyMeters = 6.0, timestampMs = 12_500L)
        assertEquals(
            "UNKNOWN gap must clear nonStationarySinceMs so resumed motion requires fresh 1.0 s dwell",
            PhoneCarriageMode.STATIONARY,
            gapClf.classify(12_500L)
        )
        // After full fresh 1.0 s dwell at t = 13_500 ms -> now exits cleanly
        for (t in 12_520L..13_500L step 20L) {
            val phase = 2.0 * PI * 1.3 * (t / 1000.0)
            gapClf.ingestRotationVector(quaternionFromEulerXDeg(60.0 + 20.0 * sin(phase)), t)
            gapClf.ingestGyro(0.75 * sin(phase), 0.75, t)
        }
        gapClf.ingestGnssSpeed(5.2, accuracyMeters = 6.0, timestampMs = 13_500L)
        assertTrue("Fresh 1.0 s dwell after UNKNOWN gap must exit STATIONARY", gapClf.classify(13_500L).isCyclingMode())

        // 3. Out-of-order lagged classify()/ingestGnssSpeed() monotonic dwell & reset() state cleanup
        val monoClf = PhoneCarriageClassifier()
        monoClf.setSportContext(SportContextHint.CYCLING)
        for (t in 0L..5_000L step 20L) {
            monoClf.ingestRotationVector(quaternionFromEulerXDeg(80.0), t)
            monoClf.ingestGyro(0.01, 0.02, t)
            if (t % 1_000L == 0L) {
                monoClf.ingestGnssSpeed(0.0, accuracyMeters = 8.0, timestampMs = t)
                monoClf.classify(t)
            }
        }
        assertEquals(PhoneCarriageMode.STATIONARY, monoClf.currentMode)
        for (t in 5_020L..6_000L step 20L) {
            val phase = 2.0 * PI * 1.3 * (t / 1000.0)
            monoClf.ingestRotationVector(quaternionFromEulerXDeg(60.0 + 20.0 * sin(phase)), t)
            monoClf.ingestGyro(0.75 * sin(phase), 0.75, t)
        }
        monoClf.ingestGnssSpeed(5.0, accuracyMeters = 6.0, timestampMs = 6_000L)
        // Lagged classify(3_800 ms) must clamp to effectiveNowMs = 6_000 ms (never seeding 3_800 ms in the past!)
        assertEquals(PhoneCarriageMode.STATIONARY, monoClf.classify(3_800L))
        // Lagged older poor-accuracy fix (3_800 ms) must not overwrite newer 6_000 ms accuracy/speed
        monoClf.ingestGnssSpeed(5.0, accuracyMeters = 50.0, timestampMs = 3_800L)
        for (t in 6_020L..6_500L step 20L) {
            val phase = 2.0 * PI * 1.3 * (t / 1000.0)
            monoClf.ingestRotationVector(quaternionFromEulerXDeg(60.0 + 20.0 * sin(phase)), t)
            monoClf.ingestGyro(0.75 * sin(phase), 0.75, t)
        }
        assertEquals(
            "At +500 ms (< 1_000 ms monotonic dwell), lagged classify must not bypass fast-exit dwell",
            PhoneCarriageMode.STATIONARY,
            monoClf.classify(6_500L)
        )
        for (t in 6_520L..7_000L step 20L) {
            val phase = 2.0 * PI * 1.3 * (t / 1000.0)
            monoClf.ingestRotationVector(quaternionFromEulerXDeg(60.0 + 20.0 * sin(phase)), t)
            monoClf.ingestGyro(0.75 * sin(phase), 0.75, t)
        }
        assertTrue(
            "At +1_000 ms monotonic dwell (uncorrupted by older 50 m accuracy fix), must exit STATIONARY",
            monoClf.classify(7_000L).isCyclingMode()
        )

        // reset() clears committedMode, nonStationarySinceMs, and lastGnssAccuracyMeters
        monoClf.reset()
        assertEquals(PhoneCarriageMode.UNKNOWN, monoClf.currentMode)
    }

    // REQ-FIL-044.1, REQ-FIL-044.2 / TST-FIL-044.1: Cycling start phone handling / bike-pushing (2026-11-21_081228 rows 1..25),
    // zero-stride Metzger->Bank errand roll (rows 310..340), <2s cold-start glance, and fresh/reset/Long.MIN_VALUE footstrike guard.
    @Test
    fun testCyclingStartPhoneHandlingAndZeroStrideErrandRoll_neverEmitRunHandheld_TST_FIL_044_1() {
        // 0. Fresh, post-reset, and Long.MIN_VALUE underflow guard on hasActiveRunFootstrike()
        val guardClf = PhoneCarriageClassifier()
        assertFalse("Fresh classifier must return false for hasActiveRunFootstrike()", guardClf.hasActiveRunFootstrike())
        assertFalse("Fresh classifier with Long.MIN_VALUE must return false without underflow", guardClf.hasActiveRunFootstrike(Long.MIN_VALUE))
        assertFalse("Fresh classifier with 0L must return false without underflow", guardClf.hasActiveRunFootstrike(0L))
        guardClf.ingestStepEvent(1_000L)
        guardClf.ingestStepEvent(1_380L)
        guardClf.ingestStepEvent(1_760L)
        guardClf.ingestStepEvent(2_140L)
        assertTrue("Active 2.63 Hz steps must report hasActiveRunFootstrike == true", guardClf.hasActiveRunFootstrike(2_200L))
        assertFalse("Explicit Long.MIN_VALUE must still return false even after samples", guardClf.hasActiveRunFootstrike(Long.MIN_VALUE))
        guardClf.reset()
        assertFalse("Post-reset classifier must return false for hasActiveRunFootstrike()", guardClf.hasActiveRunFootstrike())

        // 1. Window 1 (2026-11-21_081228 rows 1..25): start of ride with phone handling + glance +
        //    acc>15m GNSS spike (5.71->7.46->5.89 m/s) + 0.32..0.75 m/s bike-pushing with 0 strides.
        for (stepRegistered in listOf(true, false)) {
            for (includeBaro in listOf(true, false)) {
                val startClf = PhoneCarriageClassifier()
                startClf.setSportContext(SportContextHint.UNKNOWN)
                startClf.setStepDetectorRegistered(stepRegistered)
                for (t in 0L..25_000L step 20L) {
                    val sec = t / 1000.0
                    // Pull phone out & glance at t=3..4.2s, then irregular non-pendulum handling (~0.4 Hz)
                    val pitch = when {
                        t in 3_000L..4_200L -> 20.0
                        t < 3_000L -> 76.0 + 22.0 * sin(2.0 * PI * 0.45 * sec)
                        else -> 68.0 + 24.0 * sin(2.0 * PI * 0.42 * sec)
                    }
                    startClf.ingestRotationVector(quaternionFromEulerXDeg(pitch), t)
                    startClf.ingestGyro(0.90 * sin(2.0 * PI * 0.45 * sec), 0.90, t)
                    // Smooth bike-pushing / pocket-handling accel with zero 2.4-3.6 Hz running footstrikes
                    startClf.ingestAccelerometer(0.35 * sin(2.0 * PI * 0.8 * sec), -0.25 * cos(2.0 * PI * 0.8 * sec), 9.81 + 0.45 * sin(2.0 * PI * 0.8 * sec), t)
                    if (includeBaro && t % 200L == 0L) {
                        startClf.ingestBarometerAltitude(438.0 + 0.65 * sin(PI * sec), t)
                    }
                    if (t % 1_000L == 0L) {
                        val (spd, acc) = when (t) {
                            in 0L..2_000L -> 0.0 to 27.6
                            3_000L -> 5.71 to 27.6
                            4_000L -> 7.46 to 22.0
                            5_000L -> 5.89 to 17.3
                            in 6_000L..15_000L -> (0.32 + 0.03 * ((t - 6_000L) / 1_000L)) to 12.0
                            else -> 0.55 to 8.0
                        }
                        startClf.ingestGnssSpeed(spd, bearingDeg = 90.0, accuracyMeters = acc, timestampMs = t)
                        val mode = startClf.classify(t)
                        assertFalse(
                            "Window 1 start (stepReg=$stepRegistered, baro=$includeBaro) must never emit RUN_* at t=$t (was $mode)",
                            mode.isRunningMode()
                        )
                        assertFalse(startClf.hasActiveRunFootstrike(t))
                    }
                }
                assertEquals(
                    "Low-speed bike pushing (0.32..0.62 m/s, stepReg=$stepRegistered, baro=$includeBaro) must settle in WALKING_TRANSITION",
                    PhoneCarriageMode.WALKING_TRANSITION,
                    startClf.currentMode
                )
            }
        }

        // 2. Window 2 (2026-11-21_081228 rows 310..340, Metzger -> Bank): established BIKE resumes
        //    from stop and rolls 48 m at 2.0..4.0 m/s for 19 s with phone glance, 0 steps across rows 315..332
        //    and 1 isolated step at row 333 (0.70 m/s).
        for (stepRegistered in listOf(true, false)) {
            for (includeBaro in listOf(true, false)) {
                val rollClf = PhoneCarriageClassifier()
                rollClf.setSportContext(SportContextHint.UNKNOWN)
                rollClf.setStepDetectorRegistered(stepRegistered)
                // Establish BIKE_FRONT_POCKET first (t = 0..6s)
                for (t in 0L..6_000L step 20L) {
                    val sec = t / 1000.0
                    val phase = 2.0 * PI * 1.3 * sec
                    rollClf.ingestRotationVector(quaternionFromEulerXDeg(60.0 + 20.0 * sin(phase)), t)
                    rollClf.ingestGyro(0.75 * sin(phase), 0.75, t)
                    rollClf.ingestAccelerometer(0.3 * sin(phase), -0.2 * cos(phase), 9.81 + 0.4 * sin(phase), t)
                    if (includeBaro && t % 200L == 0L) rollClf.ingestBarometerAltitude(438.0 + 0.50 * sin(PI * sec), t)
                    if (t % 1_000L == 0L) {
                        rollClf.ingestGnssSpeed(6.5, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                        rollClf.classify(t)
                    }
                }
                assertEquals(PhoneCarriageMode.BIKE_FRONT_POCKET, rollClf.currentMode)

                // Stationary stop (t = 6..12s)
                for (t in 6_020L..12_000L step 20L) {
                    rollClf.ingestRotationVector(quaternionFromEulerXDeg(78.0), t)
                    rollClf.ingestGyro(0.01, 0.01, t)
                    rollClf.ingestAccelerometer(0.0, 0.0, 9.81, t)
                    if (t % 1_000L == 0L) {
                        rollClf.ingestGnssSpeed(0.0, bearingDeg = 90.0, accuracyMeters = 6.0, timestampMs = t)
                        rollClf.classify(t)
                    }
                }
                assertEquals(PhoneCarriageMode.STATIONARY, rollClf.currentMode)

                // 19 s roll-out (t = 12..31s, 2.71..3.98..0.70 m/s) with phone glance at t = 13..14s + 1 isolated step at t = 30s
                for (t in 12_020L..31_000L step 20L) {
                    val sec = t / 1000.0
                    val phase = 2.0 * PI * 1.35 * sec
                    val pitch = if (t in 13_000L..14_200L) 22.0 else 64.0 + 25.0 * sin(phase)
                    rollClf.ingestRotationVector(quaternionFromEulerXDeg(pitch), t)
                    rollClf.ingestGyro(0.95 * sin(phase), 0.95, t)
                    rollClf.ingestAccelerometer(0.35 * sin(phase), -0.25 * cos(phase), 9.81 + 0.45 * sin(phase), t)
                    if (includeBaro && t % 200L == 0L) rollClf.ingestBarometerAltitude(438.0 + 0.65 * sin(PI * sec), t)
                    if (stepRegistered && t == 30_000L) rollClf.ingestStepEvent(t)
                    if (t % 1_000L == 0L) {
                        val rollSec = (t - 12_000L) / 1_000L
                        val speed = when {
                            rollSec <= 4L -> 2.71 + 0.25 * rollSec
                            rollSec <= 14L -> 3.98 - 0.12 * (rollSec - 4L)
                            else -> 0.70
                        }
                        rollClf.ingestGnssSpeed(speed, bearingDeg = 90.0, accuracyMeters = 6.0, timestampMs = t)
                        val mode = rollClf.classify(t)
                        assertFalse(
                            "Window 2 zero-stride roll (stepReg=$stepRegistered, baro=$includeBaro) must never emit RUN_* at t=$t (was $mode)",
                            mode.isRunningMode()
                        )
                        assertFalse(rollClf.hasActiveRunFootstrike(t))
                    }
                }
            }
        }

        // 3. Cold-start < 2 s phone glance without accel warmup or step detector must never beat BIKE_FRONT_POCKET
        for (includeBaro in listOf(true, false)) {
            val coldGlanceClf = PhoneCarriageClassifier()
            coldGlanceClf.setSportContext(SportContextHint.UNKNOWN)
            coldGlanceClf.setStepDetectorRegistered(false)
            for (t in 0L..4_500L step 20L) {
                val sec = t / 1000.0
                val pitch = if (t >= 3_200L) 20.0 else 65.0 + 24.0 * sin(2.0 * PI * 1.3 * sec)
                coldGlanceClf.ingestRotationVector(quaternionFromEulerXDeg(pitch), t)
                coldGlanceClf.ingestGyro(0.95 * sin(2.0 * PI * 1.3 * sec), 0.95, t)
                if (includeBaro && t % 200L == 0L) coldGlanceClf.ingestBarometerAltitude(438.0 + 0.65 * sin(PI * sec), t)
                if (t % 1_000L == 0L) {
                    coldGlanceClf.ingestGnssSpeed(3.2, bearingDeg = 90.0, accuracyMeters = 5.0, timestampMs = t)
                    val mode = coldGlanceClf.classify(t)
                    assertFalse(
                        "Cold-start non-running glance (baro=$includeBaro) must not emit RUN_HANDHELD at t=$t (was $mode)",
                        mode == PhoneCarriageMode.RUN_HANDHELD
                    )
                }
            }
            assertTrue(
                "Cold-start non-running glance (baro=$includeBaro) must settle in BIKE_* (was ${coldGlanceClf.currentMode})",
                coldGlanceClf.currentMode.isCyclingMode()
            )
        }
    }

    // REQ-FIL-044.1, REQ-FIL-044.2 / TST-FIL-044.2: Genuine triathlon T2 slow jogger (2.0 m/s, 153 spm)
    // with handheld wrist glance across all 4 sensor profiles (hardware steps vs 50-Hz accel, with/without barometer).
    @Test
    fun testTriathlonT2SlowJoggerWithHandheldGlance_crossDeviceHardwareAndAccelFootstrikes_TST_FIL_044_2() {
        for (useHardwareSteps in listOf(true, false)) {
            for (includeBaro in listOf(true, false)) {
                for (hint in listOf(SportContextHint.UNKNOWN, SportContextHint.CYCLING)) {
                    val clf = PhoneCarriageClassifier()
                    clf.setSportContext(hint)
                    clf.setStepDetectorRegistered(useHardwareSteps)
                    // Phase 1: Cycling leg (0..5s)
                    for (t in 0L..5_000L step 20L) {
                        val sec = t / 1000.0
                        val phase = 2.0 * PI * 1.3 * sec
                        clf.ingestRotationVector(quaternionFromEulerXDeg(60.0 + 20.0 * sin(phase)), t)
                        clf.ingestGyro(0.75 * sin(phase), 0.75, t)
                        clf.ingestAccelerometer(0.3 * sin(phase), -0.2 * cos(phase), 9.81 + 0.4 * sin(phase), t)
                        if (includeBaro && t % 200L == 0L) clf.ingestBarometerAltitude(438.0 + 0.50 * sin(PI * sec), t)
                        if (t % 1_000L == 0L) {
                            clf.ingestGnssSpeed(7.5, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                            clf.classify(t)
                        }
                    }
                    assertEquals(PhoneCarriageMode.BIKE_FRONT_POCKET, clf.currentMode)

                    // Phase 2: T2 walking dismount (5..10s, 1.1 m/s)
                    for (t in 5_020L..10_000L step 20L) {
                        val sec = t / 1000.0
                        val phase = 2.0 * PI * 0.9 * sec
                        clf.ingestRotationVector(quaternionFromEulerXDeg(74.0 + 8.0 * sin(phase)), t)
                        clf.ingestGyro(0.22 * sin(phase), 0.22, t)
                        clf.ingestAccelerometer(0.2 * sin(phase), 0.1 * cos(phase), 9.81 + 0.5 * sin(2.0 * phase), t)
                        if (t % 1_000L == 0L) {
                            clf.ingestGnssSpeed(1.1, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                            clf.classify(t)
                        }
                    }
                    assertEquals(PhoneCarriageMode.WALKING_TRANSITION, clf.currentMode)

                    // Phase 3: T2 slow jogger (10..20s, 2.0 m/s, 2.55 Hz = 153 spm) with handheld wrist glance at t=17..18.2s
                    var lastStepMs = 9_500L
                    val stepPeriodMs = (1000.0 / 2.55).toLong() // ~392 ms
                    for (t in 10_020L..19_000L step 20L) {
                        val sec = t / 1000.0
                        val armPhase = 2.0 * PI * 1.28 * sec
                        val strikePhase = 2.0 * PI * 2.55 * sec
                        val pitch = if (t in 17_000L..18_200L) 20.0 else 72.0 + 25.0 * sin(armPhase)
                        clf.ingestRotationVector(quaternionFromEulerXDeg(pitch), t)
                        clf.ingestGyro(0.95 * sin(armPhase), 0.95, t)
                        if (!useHardwareSteps) {
                            clf.ingestAccelerometer(0.5 * sin(strikePhase), -0.4 * cos(strikePhase), 9.81 + 4.2 * sin(strikePhase), t)
                        }
                        if (useHardwareSteps && t - lastStepMs >= stepPeriodMs) {
                            clf.ingestStepEvent(t)
                            lastStepMs = t
                        }
                        if (includeBaro && t % 200L == 0L) clf.ingestBarometerAltitude(438.0 + 0.65 * sin(PI * sec), t)
                        if (t % 1_000L == 0L) {
                            clf.ingestGnssSpeed(2.0, bearingDeg = 90.0, accuracyMeters = 4.0, timestampMs = t)
                            clf.classify(t)
                        }
                    }
                    assertTrue(
                        "T2 slow 2.0 m/s jogger (hwStep=$useHardwareSteps, baro=$includeBaro, hint=$hint) must report hasActiveRunFootstrike == true",
                        clf.hasActiveRunFootstrike(19_000L)
                    )
                    assertEquals(
                        "T2 slow 2.0 m/s jogger glancing at handheld phone (hwStep=$useHardwareSteps, baro=$includeBaro, hint=$hint) must commit RUN_HANDHELD",
                        PhoneCarriageMode.RUN_HANDHELD,
                        clf.currentMode
                    )
                }
            }
        }
    }
}
