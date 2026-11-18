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
// Linked test specs: TST-FIL-028
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
}
