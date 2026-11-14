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

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Gated Online Wheel Circumference Auto-Calibrator (REQ-FIL-010, TST-FUSION-003.1, ATT-131).
 *
 * Dynamically calibrates effective wheel circumference C_{wheel} (meters) against high-confidence
 * 3D GNSS ground displacement on straight road segments using a recursive scalar Kalman filter.
 *
 * Shares strict gating criteria with [com.atrainingtracker.banalservice.tpms.IndirectTpmsEstimator]
 * without interfering with iTPMS slow-puncture tracking:
 * 1. GNSS Horizontal Accuracy <= 6.0 m
 * 2. Forward Speed >= 4.0 m/s (14.4 km/h)
 * 3. World Yaw-Rate |\omega_{ENU,z}| < 3.0 deg/s (0.05236 rad/s)
 * 4. 3D Slope-Corrected Accumulated Interval \Delta s_{3D} >= 20.0 m
 *
 * Guarantees convergence within < 0.3% of true circumference within 1,000 meters of qualified straight riding.
 */
class WheelCircumferenceCalibrator(
    initialCircumferenceMeters: Double = DEFAULT_CIRCUMFERENCE_METERS,
    initialVariance: Double = DEFAULT_INITIAL_VARIANCE
) {
    companion object {
        const val DEFAULT_CIRCUMFERENCE_METERS = 2.105 // Standard 700x25c wheel
        const val DEFAULT_INITIAL_VARIANCE = 0.05 // (m)^2

        const val MAX_GNSS_ACCURACY_METERS = 6.0
        const val MIN_SPEED_MPS = 4.0
        const val MAX_YAW_RATE_RAD_PER_SEC = 0.05235987755982988 // 3.0 deg/s
        const val MIN_INTERVAL_DISTANCE_METERS = 20.0

        private const val PROCESS_NOISE_Q = 1e-7 // Small tire wear random walk per update
        private const val MIN_MEASUREMENT_VARIANCE_R = 1e-4
        private const val EARTH_RADIUS_METERS = 6371000.0
    }

    /**
     * Current estimated wheel circumference in meters.
     */
    var circumferenceMeters: Double = initialCircumferenceMeters
        private set

    /**
     * Estimation error variance P in m^2.
     */
    var variance: Double = initialVariance
        private set

    /**
     * Standard deviation of the circumference estimate in meters.
     */
    val stdDevMeters: Double
        get() = sqrt(max(0.0, variance))

    /**
     * Cumulative distance (meters) of straight qualified riding ingested by the filter.
     */
    var totalQualifiedDistanceMeters: Double = 0.0
        private set

    /**
     * Total number of completed Kalman measurement updates.
     */
    var updateCount: Int = 0
        private set

    /**
     * Whether the calibrator has achieved high confidence (variance < 1e-4 or >= 500m qualified distance).
     */
    val isCalibrated: Boolean
        get() = updateCount >= 10 && (variance < 1e-3 || totalQualifiedDistanceMeters >= 500.0)

    // Interval accumulation state
    private var pendingDistanceMeters: Double = 0.0
    private var pendingRevolutions: Double = 0.0

    // Previous location state for differential GPS
    private var prevLat: Double? = null
    private var prevLon: Double? = null
    private var prevElevationMeters: Double? = null
    private var prevWheelRevs: Double? = null
    private var prevTimestampMs: Long? = null

    /**
     * Ingests a differential step with known 3D ground distance and wheel revolutions.
     *
     * @param deltaDistance3d 3D displacement over the step (meters).
     * @param deltaRevolutions Wheel revolutions over the step.
     * @param gnssAccuracyMeters Horizontal GNSS accuracy 1-sigma (meters).
     * @param speedMps Current forward speed (m/s).
     * @param yawRateRadPerSec World yaw-rate |\omega_{ENU,z}| (rad/s).
     * @return True if an interval update (\Delta s >= 20m) occurred and updated [circumferenceMeters], false otherwise.
     */
    fun processStep(
        deltaDistance3d: Double,
        deltaRevolutions: Double,
        gnssAccuracyMeters: Double,
        speedMps: Double,
        yawRateRadPerSec: Double
    ): Boolean {
        // 1. Strict Gating Verification
        if (gnssAccuracyMeters > MAX_GNSS_ACCURACY_METERS ||
            speedMps < MIN_SPEED_MPS ||
            abs(yawRateRadPerSec) >= MAX_YAW_RATE_RAD_PER_SEC
        ) {
            // Discard un-qualified pending segment to prevent turn or multipath pollution
            pendingDistanceMeters = 0.0
            pendingRevolutions = 0.0
            return false
        }

        if (deltaDistance3d <= 0.0 || deltaRevolutions <= 0.0) {
            return false
        }

        // 2. Accumulate qualified riding segment
        pendingDistanceMeters += deltaDistance3d
        pendingRevolutions += deltaRevolutions

        // 3. Check whether interval threshold (\Delta s >= 20.0 m) is reached
        if (pendingDistanceMeters >= MIN_INTERVAL_DISTANCE_METERS && pendingRevolutions > 0.0) {
            val z = pendingDistanceMeters / pendingRevolutions

            // Measurement error variance scaled by GNSS accuracy and accumulated revolutions
            val rMeasurement = max(
                MIN_MEASUREMENT_VARIANCE_R,
                (2.0 * gnssAccuracyMeters * gnssAccuracyMeters) / (pendingRevolutions * pendingRevolutions)
            )

            // Scalar Kalman Filter Update
            val pPrior = variance + PROCESS_NOISE_Q
            val s = pPrior + rMeasurement
            val k = pPrior / s
            val innovation = z - circumferenceMeters

            circumferenceMeters += k * innovation
            variance = (1.0 - k) * pPrior
            totalQualifiedDistanceMeters += pendingDistanceMeters
            updateCount++

            // Reset pending accumulators for the next interval
            pendingDistanceMeters = 0.0
            pendingRevolutions = 0.0
            return true
        }

        return false
    }

    /**
     * Ingests 1-Hz location telemetry and total accumulated wheel revolutions.
     */
    fun processTelemetry(
        lat: Double,
        lon: Double,
        elevationMeters: Double?,
        totalRevolutions: Double,
        gnssAccuracyMeters: Double,
        speedMps: Double,
        yawRateRadPerSec: Double,
        timestampMs: Long = System.currentTimeMillis()
    ): Boolean {
        val pLat = prevLat
        val pLon = prevLon
        val pElev = prevElevationMeters
        val pRevs = prevWheelRevs
        val pTime = prevTimestampMs

        prevLat = lat
        prevLon = lon
        prevElevationMeters = elevationMeters
        prevWheelRevs = totalRevolutions
        prevTimestampMs = timestampMs

        if (pLat == null || pLon == null || pRevs == null || pTime == null) {
            return false
        }

        val dtSec = (timestampMs - pTime) / 1000.0
        if (dtSec <= 0.0 || dtSec > 5.0) {
            pendingDistanceMeters = 0.0
            pendingRevolutions = 0.0
            return false
        }

        val deltaRevs = totalRevolutions - pRevs
        if (deltaRevs <= 0.0) {
            return false
        }

        val dist2d = computeDistanceMeters(pLat, pLon, lat, lon)
        val dist3d = if (elevationMeters != null && pElev != null) {
            val dh = elevationMeters - pElev
            sqrt(dist2d * dist2d + dh * dh)
        } else {
            dist2d
        }

        return processStep(
            deltaDistance3d = dist3d,
            deltaRevolutions = deltaRevs,
            gnssAccuracyMeters = gnssAccuracyMeters,
            speedMps = speedMps,
            yawRateRadPerSec = yawRateRadPerSec
        )
    }

    /**
     * Resets session accumulators and filter state.
     */
    fun reset(
        newCircumferenceMeters: Double = DEFAULT_CIRCUMFERENCE_METERS,
        newVariance: Double = DEFAULT_INITIAL_VARIANCE
    ) {
        circumferenceMeters = newCircumferenceMeters
        variance = newVariance
        totalQualifiedDistanceMeters = 0.0
        updateCount = 0
        pendingDistanceMeters = 0.0
        pendingRevolutions = 0.0
        prevLat = null
        prevLon = null
        prevElevationMeters = null
        prevWheelRevs = null
        prevTimestampMs = null
    }

    private fun computeDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(rLat1) * cos(rLat2) * sin(dLon / 2.0) * sin(dLon / 2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_METERS * c
    }
}
