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

import java.util.ArrayDeque
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/**
 * Decision produced by [AttFusedColdStartHelper] for a single GNSS epoch (REQ-FIL-036.1, REQ-FIL-036.2).
 *
 * @property shouldReleasePin True when corroborated sustained motion and directed raw-GNSS
 *   progression justify releasing coarse cold-start stationary pinning (subject to base-class
 *   indoor, external wheel, and IMU rest-variance vetoes).
 * @property seedHeadingRad One-shot initial ENU heading (radians) derived from directed raw-GNSS
 *   progression or corroborated moving bearing while EKF heading is still unanchored, or `null`.
 * @property seedHeadingVarRad2 Heading variance (rad^2) accompanying [seedHeadingRad].
 * @property decouplePositionVelocity True while EKF heading remains unanchored so GNSS position
 *   updates do not crush or spike forward speed via wrong-heading cross-covariance.
 */
data class ColdStartDecision(
    val shouldReleasePin: Boolean,
    val seedHeadingRad: Double?,
    val seedHeadingVarRad2: Double,
    val decouplePositionVelocity: Boolean
)

/**
 * Pure-JVM cold-start release, anti-oscillation guard, raw-chord heading seeding, and calibrated
 * Doppler speed-variance helper for [com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice_ATTFused]
 * and [ExtendedRtsOdometrySmoother] (REQ-FIL-036.1, REQ-FIL-036.2, ATT-9158).
 */
@Suppress("MagicNumber")
class AttFusedColdStartHelper {

    companion object {
        const val MIN_CORROBORATED_SPEED_MPS = 0.8
        const val MIN_MOTION_MODE_SPEED_MPS = 0.5
        const val MIN_BEARING_SEED_SPEED_MPS = 1.0
        const val MIN_CONSECUTIVE_MOTION_FIXES = 2
        const val MIN_RELEASE_SAMPLES = 3
        const val MIN_RELEASE_NET_DISPLACEMENT_METERS = 3.0
        const val MIN_HEADING_SEED_NET_DISPLACEMENT_METERS = 4.0
        const val MIN_BEARING_SEED_NET_DISPLACEMENT_METERS = 2.0
        const val MIN_DIRECTED_STRAIGHTNESS_RATIO = 0.55
        const val WINDOW_MS = 15_000L
        const val MAX_STEP_GAP_MS = 5_000L

        const val MIN_DOPPLER_SPEED_VARIANCE = 0.25
        const val MAX_TRUSTED_DOPPLER_SPEED_VARIANCE = 2.25
        private const val TRUSTED_DOPPLER_ACC_SCALE = 0.08
        private const val UNTRUSTED_DOPPLER_ACC_SCALE = 0.25

        /**
         * Returns calibrated Doppler speed variance ((m/s)^2) for GNSS speed updates (REQ-FIL-036.2).
         *
         * Trusted fixes (`0 < acc <= 20 m`) receive a realistic bounded variance in `[0.25, 2.25]`
         * so cold-start `10–20 m` horizontal uncertainty does not starve Doppler speed convergence,
         * while degraded fixes (`acc > 20 m`) retain coarse variance `max(0.5, (acc * 0.25)^2)`.
         */
        @JvmStatic
        fun calibratedDopplerSpeedVariance(accuracyMeters: Double): Double {
            if (!accuracyMeters.isFinite() || accuracyMeters <= 0.0) {
                return 1.0
            }
            if (accuracyMeters <= UnicycleOdometryEkf.MAX_GNSS_ACCURACY_METERS) {
                val sigma = accuracyMeters * TRUSTED_DOPPLER_ACC_SCALE
                return (sigma * sigma).coerceIn(MIN_DOPPLER_SPEED_VARIANCE, MAX_TRUSTED_DOPPLER_SPEED_VARIANCE)
            }
            val coarseSigma = accuracyMeters * UNTRUSTED_DOPPLER_ACC_SCALE
            return max(0.5, coarseSigma * coarseSigma)
        }
    }

    private data class MovingFix(val eastM: Double, val northM: Double, val timestampMs: Long)

    private val rawMovingFixes = ArrayDeque<MovingFix>()
    private var consecutiveMotionFixes: Int = 0
    private var lastMotionFixTimestampMs: Long = Long.MIN_VALUE

    @Volatile
    var hasAnchoredHeading: Boolean = false
        private set

    @Synchronized
    fun markHeadingAnchored() {
        hasAnchoredHeading = true
    }

    @Synchronized
    fun reset() {
        rawMovingFixes.clear()
        consecutiveMotionFixes = 0
        lastMotionFixTimestampMs = Long.MIN_VALUE
        hasAnchoredHeading = false
    }

    /**
     * Evaluates cold-start pin release, raw-chord initial heading seeding, and unanchored
     * position-velocity decoupling for the current raw GNSS fix in local ENU coordinates.
     */
    @Synchronized
    fun evaluateStep(
        eastM: Double,
        northM: Double,
        accuracyMeters: Double,
        timestampMs: Long,
        hasTrustedGpsSpeed: Boolean,
        gpsSpeedMps: Double,
        bearingDeg: Double?,
        isWheelMoving: Boolean,
        isStationary: Boolean,
        isStationaryByClassifier: Boolean,
        activeMotionModeOrSteps: Boolean
    ): ColdStartDecision {
        if (isStationary || isStationaryByClassifier) {
            clearMotionWindow()
            return ColdStartDecision(
                shouldReleasePin = false,
                seedHeadingRad = null,
                seedHeadingVarRad2 = 0.0,
                decouplePositionVelocity = false
            )
        }

        if (!eastM.isFinite() || !northM.isFinite() || !accuracyMeters.isFinite() || accuracyMeters <= 0.0) {
            return ColdStartDecision(
                shouldReleasePin = false,
                seedHeadingRad = null,
                seedHeadingVarRad2 = 0.0,
                decouplePositionVelocity = !hasAnchoredHeading
            )
        }

        if (lastMotionFixTimestampMs > Long.MIN_VALUE &&
            (timestampMs < lastMotionFixTimestampMs || (timestampMs - lastMotionFixTimestampMs) > MAX_STEP_GAP_MS)
        ) {
            clearMotionWindow()
        }

        val speedTrustedFinite = hasTrustedGpsSpeed && gpsSpeedMps.isFinite()
        val motionCorroborated = isWheelMoving ||
            (speedTrustedFinite && gpsSpeedMps >= MIN_CORROBORATED_SPEED_MPS) ||
            (activeMotionModeOrSteps && speedTrustedFinite && gpsSpeedMps >= MIN_MOTION_MODE_SPEED_MPS)

        if (motionCorroborated) {
            consecutiveMotionFixes++
        } else {
            consecutiveMotionFixes = 0
        }

        if (accuracyMeters <= UnicycleOdometryEkf.MAX_GNSS_ACCURACY_METERS) {
            val last = rawMovingFixes.peekLast()
            if (last == null || timestampMs > last.timestampMs) {
                rawMovingFixes.addLast(MovingFix(eastM, northM, timestampMs))
            }
            val cutoffMs = timestampMs - WINDOW_MS
            while (rawMovingFixes.size > 1 && rawMovingFixes.peekFirst()!!.timestampMs < cutoffMs) {
                rawMovingFixes.removeFirst()
            }
        }
        lastMotionFixTimestampMs = timestampMs

        val (netDist, straightness, chordHeadingRad) = computeWindowProgression()

        val directedProgression = rawMovingFixes.size >= MIN_RELEASE_SAMPLES &&
            netDist >= MIN_RELEASE_NET_DISPLACEMENT_METERS &&
            straightness >= MIN_DIRECTED_STRAIGHTNESS_RATIO

        val shouldReleasePin = consecutiveMotionFixes >= MIN_CONSECUTIVE_MOTION_FIXES &&
            (isWheelMoving || directedProgression)

        var seedHeadingRad: Double? = null
        var seedHeadingVarRad2 = 0.0
        if (!hasAnchoredHeading && rawMovingFixes.size >= MIN_RELEASE_SAMPLES) {
            if (netDist >= MIN_HEADING_SEED_NET_DISPLACEMENT_METERS &&
                straightness >= MIN_DIRECTED_STRAIGHTNESS_RATIO &&
                chordHeadingRad != null
            ) {
                seedHeadingRad = chordHeadingRad
                val angularSigma = atan2(accuracyMeters, netDist)
                seedHeadingVarRad2 = (angularSigma * angularSigma).coerceIn(0.04, 0.35)
            } else if (bearingDeg != null &&
                bearingDeg.isFinite() &&
                speedTrustedFinite &&
                gpsSpeedMps >= MIN_BEARING_SEED_SPEED_MPS &&
                consecutiveMotionFixes >= MIN_CONSECUTIVE_MOTION_FIXES &&
                netDist >= MIN_BEARING_SEED_NET_DISPLACEMENT_METERS &&
                straightness >= MIN_DIRECTED_STRAIGHTNESS_RATIO
            ) {
                seedHeadingRad = UnicycleOdometryEkf.normalizeAngle(Math.toRadians(90.0 - bearingDeg))
                seedHeadingVarRad2 = 0.16
            }
        }

        val decouplePositionVelocity = !hasAnchoredHeading && seedHeadingRad == null
        return ColdStartDecision(
            shouldReleasePin = shouldReleasePin,
            seedHeadingRad = seedHeadingRad,
            seedHeadingVarRad2 = seedHeadingVarRad2,
            decouplePositionVelocity = decouplePositionVelocity
        )
    }

    private fun clearMotionWindow() {
        rawMovingFixes.clear()
        consecutiveMotionFixes = 0
        lastMotionFixTimestampMs = Long.MIN_VALUE
    }

    private data class ProgressionStats(
        val netDistM: Double,
        val straightness: Double,
        val chordHeadingRad: Double?
    )

    private fun computeWindowProgression(): ProgressionStats {
        if (rawMovingFixes.size < 2) {
            return ProgressionStats(0.0, 0.0, null)
        }
        val first = rawMovingFixes.peekFirst()!!
        val last = rawMovingFixes.peekLast()!!
        val dE = last.eastM - first.eastM
        val dN = last.northM - first.northM
        val netDist = hypot(dE, dN)

        var pathDist = 0.0
        var prev: MovingFix? = null
        for (fix in rawMovingFixes) {
            if (prev != null) {
                pathDist += hypot(fix.eastM - prev.eastM, fix.northM - prev.northM)
            }
            prev = fix
        }
        val straightness = if (pathDist > 1e-6) netDist / pathDist else 0.0
        val heading = if (netDist > 1e-6) atan2(dN, dE) else null
        return ProgressionStats(netDist, straightness, heading)
    }
}
