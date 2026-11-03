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

package com.atrainingtracker.banalservice.tpms

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Indirect Tire Pressure Monitoring System (iTPMS) Estimator (REQ-TRK-050, TST-TRK-050, ATT-4880).
 *
 * Fuses 3D GNSS ground displacement with ANT+/BLE wheel speed revolutions to detect:
 * 1. Multi-day tire deflation relative to freshly-pumped baseline [baselineCircumferenceMeters].
 * 2. In-ride slow punctures via a sliding-window one-sided CUSUM change-point detector.
 *
 * Operates exclusively on straight-line, high-confidence GNSS segments with 3D elevation slope correction,
 * guaranteeing zero false positives during sharp switchbacks, mountain descents, or urban multipath jitter.
 */
class IndirectTpmsEstimator(
    var baselineCircumferenceMeters: Double = 0.0
) {
    companion object {
        const val MAX_BEARING_RATE_DEG_PER_SEC = 3.0
        const val MAX_GNSS_ACCURACY_METERS = 6.0
        const val MIN_SPEED_MPS = 4.0 // 14.4 km/h

        const val GATED_DISTANCE_START_LOCK_METERS = 2000.0 // 2.0 km of gated riding to lock C_start
        const val WINDOW_DISTANCE_SHORT_METERS = 2000.0 // 2.0 km sliding window for C_short

        const val LOW_PRESSURE_THRESHOLD_RATIO = 0.006 // 0.6% drop below pumped baseline
        const val SLOW_PUNCTURE_THRESHOLD_RATIO = 0.010 // 1.0% drop below ride-start baseline

        const val CUSUM_SLACK = 0.003 // 0.3% slack allowance
        const val CUSUM_THRESHOLD = 0.007 // 0.7% cumulative drift trigger
        const val MIN_WINDOW_SAMPLES_DISTANCE_METERS = 500.0 // Minimum distance to evaluate sliding window

        private const val EARTH_RADIUS_METERS = 6371000.0
    }

    sealed interface TpmsAlertResult {
        val dropPercentage: Double

        data class LowPressure(
            override val dropPercentage: Double,
            val currentCircumferenceMm: Int,
            val baselineCircumferenceMm: Int
        ) : TpmsAlertResult

        data class SlowPuncture(
            override val dropPercentage: Double,
            val currentCircumferenceMm: Int,
            val startCircumferenceMm: Int
        ) : TpmsAlertResult
    }

    private data class GatedStep(
        val distance3dMeters: Double,
        val wheelRevolutions: Double
    )

    // Previous location state for differential math
    private var prevLat: Double? = null
    private var prevLon: Double? = null
    private var prevBearingDeg: Double? = null
    private var prevElevationMeters: Double? = null
    private var prevTimestampMs: Long? = null
    private var prevWheelDistanceMeters: Double? = null

    // Ride-start accumulation state
    private var startGatedDistanceMeters: Double = 0.0
    private var startGatedRevolutions: Double = 0.0
    var isStartLocked: Boolean = false
        private set
    var startCircumferenceMeters: Double = 0.0
        private set

    // Live rolling window state
    private val slidingWindow = ArrayDeque<GatedStep>()
    private var slidingDistanceMeters: Double = 0.0
    private var slidingRevolutions: Double = 0.0

    // CUSUM detector state
    private var cusumAccumulator: Double = 0.0
    private var lowPressureAlertFired: Boolean = false
    private var slowPunctureAlertFired: Boolean = false

    /**
     * Resets session accumulators and detector states when a workout begins or is cleared.
     */
    fun resetSession() {
        prevLat = null
        prevLon = null
        prevBearingDeg = null
        prevElevationMeters = null
        prevTimestampMs = null
        prevWheelDistanceMeters = null

        startGatedDistanceMeters = 0.0
        startGatedRevolutions = 0.0
        isStartLocked = false
        startCircumferenceMeters = 0.0

        slidingWindow.clear()
        slidingDistanceMeters = 0.0
        slidingRevolutions = 0.0

        cusumAccumulator = 0.0
        lowPressureAlertFired = false
        slowPunctureAlertFired = false
    }

    /**
     * Ingests a 1-Hz telemetry update with GNSS coordinates and wheel sensor distance.
     *
     * @return [TpmsAlertResult] if an alert condition is triggered, null otherwise.
     */
    fun processTelemetry(
        lat: Double,
        lon: Double,
        bearingDeg: Double,
        accuracyMeters: Double,
        speedMps: Double,
        elevationMeters: Double?,
        wheelDistanceMeters: Double,
        calibrationFactorMeters: Double,
        timestampMs: Long = System.currentTimeMillis()
    ): TpmsAlertResult? {
        val pLat = prevLat
        val pLon = prevLon
        val pBearing = prevBearingDeg
        val pTime = prevTimestampMs
        val pWheelDist = prevWheelDistanceMeters
        val pElev = prevElevationMeters

        // Update previous state before returning
        prevLat = lat
        prevLon = lon
        prevBearingDeg = bearingDeg
        prevElevationMeters = elevationMeters
        prevTimestampMs = timestampMs
        prevWheelDistanceMeters = wheelDistanceMeters

        if (pLat == null || pLon == null || pBearing == null || pTime == null || pWheelDist == null) {
            return null
        }

        val dtSec = (timestampMs - pTime) / 1000.0
        if (dtSec <= 0.0 || dtSec > 5.0) {
            // Irregular interval or time jump, discard differential step
            return null
        }

        // 1. GNSS Quality & Speed Gating
        if (accuracyMeters > MAX_GNSS_ACCURACY_METERS || speedMps < MIN_SPEED_MPS) {
            return null
        }

        // 2. Straight-Line Gating: reject cornering and lean-angle distortions
        val bearingDiff = abs(((bearingDeg - pBearing + 540.0) % 360.0) - 180.0)
        val bearingRateDegPerSec = bearingDiff / dtSec
        if (bearingRateDegPerSec >= MAX_BEARING_RATE_DEG_PER_SEC) {
            return null
        }

        // 3. Wheel revolutions delta
        val wheelDistDelta = wheelDistanceMeters - pWheelDist
        if (wheelDistDelta <= 0.0 || calibrationFactorMeters <= 0.0) {
            return null
        }
        val wheelRevDelta = wheelDistDelta / calibrationFactorMeters

        // 4. 3D ground distance calculation with slope correction
        val dist2d = computeDistanceMeters(pLat, pLon, lat, lon)
        val dist3d = if (elevationMeters != null && pElev != null) {
            val dh = elevationMeters - pElev
            sqrt(dist2d * dist2d + dh * dh)
        } else {
            dist2d
        }

        if (dist3d <= 0.0) {
            return null
        }

        return onGatedStep(dist3d, wheelRevDelta)
    }

    /**
     * Ingests a pre-gated or simulated differential step.
     */
    fun onGatedStep(dist3dMeters: Double, wheelRevDelta: Double): TpmsAlertResult? {
        if (dist3dMeters <= 0.0 || wheelRevDelta <= 0.0) {
            return null
        }

        // Phase 1: Lock ride-start baseline (C_start)
        if (!isStartLocked) {
            startGatedDistanceMeters += dist3dMeters
            startGatedRevolutions += wheelRevDelta

            if (startGatedDistanceMeters >= GATED_DISTANCE_START_LOCK_METERS && startGatedRevolutions > 0.0) {
                startCircumferenceMeters = startGatedDistanceMeters / startGatedRevolutions
                isStartLocked = true

                // Check multi-day low pressure drift against freshly-pumped baseline
                if (baselineCircumferenceMeters > 0.0 && !lowPressureAlertFired) {
                    val driftRatio = (startCircumferenceMeters / baselineCircumferenceMeters) - 1.0
                    if (driftRatio <= -LOW_PRESSURE_THRESHOLD_RATIO) {
                        lowPressureAlertFired = true
                        return TpmsAlertResult.LowPressure(
                            dropPercentage = abs(driftRatio) * 100.0,
                            currentCircumferenceMm = (startCircumferenceMeters * 1000.0).toInt(),
                            baselineCircumferenceMm = (baselineCircumferenceMeters * 1000.0).toInt()
                        )
                    }
                }
            }
            return null
        }

        // Phase 2: Live rolling window & CUSUM slow puncture detection
        val step = GatedStep(dist3dMeters, wheelRevDelta)
        slidingWindow.addLast(step)
        slidingDistanceMeters += dist3dMeters
        slidingRevolutions += wheelRevDelta

        while (slidingDistanceMeters > WINDOW_DISTANCE_SHORT_METERS && slidingWindow.size > 1) {
            val removed = slidingWindow.removeFirst()
            slidingDistanceMeters -= removed.distance3dMeters
            slidingRevolutions -= removed.wheelRevolutions
        }

        if (slidingDistanceMeters >= MIN_WINDOW_SAMPLES_DISTANCE_METERS && slidingRevolutions > 0.0) {
            val cShort = slidingDistanceMeters / slidingRevolutions
            val relativeDrop = (startCircumferenceMeters - cShort) / startCircumferenceMeters

            // One-sided CUSUM change-point detector
            val cusumIncrement = relativeDrop - CUSUM_SLACK
            cusumAccumulator = (cusumAccumulator + cusumIncrement).coerceAtLeast(0.0)

            if (!slowPunctureAlertFired) {
                if (relativeDrop >= SLOW_PUNCTURE_THRESHOLD_RATIO || cusumAccumulator >= CUSUM_THRESHOLD) {
                    slowPunctureAlertFired = true
                    return TpmsAlertResult.SlowPuncture(
                        dropPercentage = relativeDrop * 100.0,
                        currentCircumferenceMm = (cShort * 1000.0).toInt(),
                        startCircumferenceMm = (startCircumferenceMeters * 1000.0).toInt()
                    )
                }
            }
        }

        return null
    }

    private fun computeDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }
}
