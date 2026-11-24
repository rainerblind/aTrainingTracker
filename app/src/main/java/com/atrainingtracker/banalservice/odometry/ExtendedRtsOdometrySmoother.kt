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

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Smoothed odometry state point output by the Extended Rauch-Tung-Striebel (ERTS/RTS) backward pass
 * (REQ-FIL-017.1, TST-FIL-017.1, ATT-7750).
 *
 * @property timestampMs Timestamp in milliseconds.
 * @property eastMeters Smoothed East displacement in meters from reference origin.
 * @property northMeters Smoothed North displacement in meters from reference origin.
 * @property speedMps Smoothed horizontal speed in meters per second.
 * @property headingRad Smoothed ENU heading angle in radians (0 = East, PI/2 = North).
 * @property gyroBiasRadPerSec Smoothed vertical gyroscope bias in radians per second.
 * @property latitude Smoothed WGS-84 latitude in degrees.
 * @property longitude Smoothed WGS-84 longitude in degrees.
 * @property bearingDeg Smoothed course-over-ground bearing in degrees (0 = North, clockwise).
 * @property deltaDistanceMeters Smoothed horizontal distance increment since previous step.
 */
data class SmoothedOdometryPoint(
    val timestampMs: Long,
    val eastMeters: Double,
    val northMeters: Double,
    val speedMps: Double,
    val headingRad: Double,
    val gyroBiasRadPerSec: Double,
    val latitude: Double,
    val longitude: Double,
    val bearingDeg: Double,
    val deltaDistanceMeters: Double
)

/**
 * Raw trajectory sample point ingested by [ExtendedRtsOdometrySmoother] for forward replay.
 */
data class RawOdometrySample(
    val timestampMs: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val speedMps: Double? = null,
    val bearingDeg: Double? = null,
    val accuracyMeters: Double? = null,
    val worldYawRateRadPerSec: Double? = null,
    val wheelSpeedMps: Double? = null,
    val carriageMode: PhoneCarriageMode = PhoneCarriageMode.UNKNOWN,
    val isPauseGap: Boolean = false
) {
    /**
     * Checks whether this sample contains a physically valid, non-Null-Island GNSS position fix (REQ-FIL-019.1).
     */
    fun hasValidGnssFix(): Boolean = ExtendedRtsOdometrySmoother.hasValidGnssFix(latitude, longitude)
}

/**
 * Post-workout Extended Rauch-Tung-Striebel (ERTS/RTS) backward smoother for 5-state unicycle odometry
 * (REQ-FIL-017.1, ATT-7750).
 *
 * Reuses the exact 5-state unicycle model $[E, N, v, \psi, b_{\omega_z}]^T$ from ATT-131.
 * Pure Kotlin implementation: zero external linear algebra dependencies, < 50ms for 14,400 samples.
 */
@Suppress("TooManyFunctions", "MagicNumber")
object ExtendedRtsOdometrySmoother {

    const val STATE_DIM = 5
    const val EARTH_RADIUS_METERS = 6371000.0
    const val MAX_ACTIVE_SAMPLE_GAP_MS = 5_000L
    private const val MIN_CHORD_DISPLACEMENT_METERS = 5.0
    private const val MIN_RUNNING_HEADING_SPEED_MPS = 0.8
    private const val MIN_BEARING_HEADING_SPEED_MPS = 1.2
    private const val ONE_HZ_REPLAY_MIN_DT_SEC = 0.5
    private const val MAX_ONE_HZ_YAW_RATE_RADPS = 0.35
    private const val OFFLINE_ONE_HZ_GYRO_NOISE_SCALE = 25.0
    private const val YAW_RATE_WINDOW_HALF_WIDTH = 2
    private const val YAW_RATE_WINDOW_MAX_GAP_MS = 3_000L
    private const val STATIONARY_SPEED_THRESHOLD_MPS = 0.05
    private const val GLITCH_NEIGHBOR_HALF_WIDTH = 3
    private const val GLITCH_NEIGHBOR_MAX_GAP_MS = 5_000L
    private const val GLITCH_IMMEDIATE_MAX_GAP_MS = 2_500L
    private const val GLITCH_MIN_MOVING_SPEED_MPS = 0.25
    private const val DISCONTINUITY_MAX_DT_GNSS_SEC = 5.0
    private const val TRANSITION_ENVELOPE_HALF_WIDTH = 5
    private const val TRANSITION_ENVELOPE_MAX_GAP_MS = 6_000L
    private const val SPEED_ENVELOPE_HALF_WIDTH = 4
    private const val SPEED_ENVELOPE_MAX_GAP_MS = 5_000L

    private fun isStationarySample(sample: RawOdometrySample): Boolean {
        val speed = sample.speedMps?.takeIf { it.isFinite() }
        val wheelSpeed = sample.wheelSpeedMps?.takeIf { it.isFinite() }
        if (wheelSpeed != null && wheelSpeed > STATIONARY_SPEED_THRESHOLD_MPS) {
            return false
        }
        if (sample.carriageMode == PhoneCarriageMode.STATIONARY && (speed == null || speed <= 0.25)) {
            return true
        }
        return speed != null && speed <= STATIONARY_SPEED_THRESHOLD_MPS
    }

    private fun sanitizeNonNegativeFinite(value: Double): Double {
        return if (value.isFinite()) max(0.0, value) else 0.0
    }

    private fun copyAndDecoupleVelocityPosition(
        src: Array<DoubleArray>,
        dst: Array<DoubleArray>,
        symmetric: Boolean
    ) {
        for (i in 0 until STATE_DIM) {
            System.arraycopy(src[i], 0, dst[i], 0, STATE_DIM)
        }
        dst[0][2] = 0.0
        dst[1][2] = 0.0
        if (symmetric) {
            dst[2][0] = 0.0
            dst[2][1] = 0.0
        }
    }

    /**
     * Overflow-safe check for whether two consecutive sample timestamps span a pause gap (`dt > 5 s`, REQ-FIL-045.2).
     */
    internal fun isTimestampPauseGap(prevTimestampMs: Long, currTimestampMs: Long): Boolean {
        return currTimestampMs > prevTimestampMs &&
            prevTimestampMs <= Long.MAX_VALUE - MAX_ACTIVE_SAMPLE_GAP_MS &&
            currTimestampMs > prevTimestampMs + MAX_ACTIVE_SAMPLE_GAP_MS
    }

    /**
     * Checks whether the given coordinates represent a physically valid GNSS fix (REQ-FIL-019.2).
     *
     * Returns false if coordinates are null, NaN, out of geographic bounds ([-90, 90], [-180, 180]),
     * or at Null Island (0.0, 0.0) in the Gulf of Guinea.
     */
    fun hasValidGnssFix(lat: Double?, lon: Double?): Boolean {
        if (lat == null || lon == null) return false
        if (lat.isNaN() || lon.isNaN()) return false
        val inBounds = lat in -90.0..90.0 && lon in -180.0..180.0
        val notNullIsland = abs(lat) > 1e-6 || abs(lon) > 1e-6
        return inBounds && notNullIsland
    }

    /**
     * Runs backward RTS recursion on a recorded list of forward [OdometrySnapshot] points.
     *
     * Recursion:
     * $$C_k = P_{k|k} F_k^T (P_{k+1|k})^{-1}$$
     * $$x_{k|N} = x_{k|k} + C_k (x_{k+1|N} - x_{k+1|k})$$
     * $$P_{k|N} = P_{k|k} + C_k (P_{k+1|N} - P_{k+1|k}) C_k^T$$
     * with heading difference $\psi_{k+1|N} - \psi_{k+1|k}$ wrapped to $[-\pi, \pi)$.
     */
    @JvmOverloads
    fun smoothSnapshots(
        snapshots: List<OdometrySnapshot>,
        refLat: Double,
        refLon: Double,
        stationaryMask: BooleanArray? = null,
        discontinuityMask: BooleanArray? = null,
        maxSpeedEnvelope: DoubleArray? = null,
        pauseGapMask: BooleanArray? = null
    ): List<SmoothedOdometryPoint> {
        val n = snapshots.size
        if (n == 0) return emptyList()
        if (n == 1) {
            val s0 = snapshots[0]
            val x0 = s0.statePosterior
            val (lat, lon) = enuToLatLng(x0[0], x0[1], refLat, refLon)
            val env0 = maxSpeedEnvelope?.getOrNull(0)
            var speed0 = if (stationaryMask?.getOrNull(0) == true) 0.0 else sanitizeNonNegativeFinite(x0[2])
            if (stationaryMask?.getOrNull(0) != true && env0 != null && env0.isFinite()) {
                speed0 = min(speed0, env0)
            }
            return listOf(
                SmoothedOdometryPoint(
                    timestampMs = s0.timestampMs,
                    eastMeters = x0[0],
                    northMeters = x0[1],
                    speedMps = speed0,
                    headingRad = x0[3],
                    gyroBiasRadPerSec = x0[4],
                    latitude = lat,
                    longitude = lon,
                    bearingDeg = headingToBearingDeg(x0[3]),
                    deltaDistanceMeters = 0.0
                )
            )
        }

        fun isPauseBoundary(idx: Int): Boolean {
            if (idx <= 0) return false
            if (pauseGapMask != null && idx < pauseGapMask.size && pauseGapMask[idx]) return true
            return isTimestampPauseGap(snapshots[idx - 1].timestampMs, snapshots[idx].timestampMs)
        }

        // Smoothed state arrays
        val smoothedStates = Array(n) { DoubleArray(STATE_DIM) }
        val smoothedCovs = Array(n) { Array(STATE_DIM) { DoubleArray(STATE_DIM) } }

        // Step N-1 initialization: x^s_{N-1} = x_{N-1|N-1}, P^s_{N-1} = P_{N-1|N-1}
        val lastIdx = n - 1
        for (i in 0 until STATE_DIM) {
            smoothedStates[lastIdx][i] = snapshots[lastIdx].statePosterior[i]
            for (j in 0 until STATE_DIM) {
                smoothedCovs[lastIdx][i][j] = snapshots[lastIdx].covariancePosterior[i][j]
            }
        }
        if (stationaryMask?.getOrNull(lastIdx) == true) {
            smoothedStates[lastIdx][2] = 0.0
        } else {
            var lastSpeed = sanitizeNonNegativeFinite(smoothedStates[lastIdx][2])
            val envLast = maxSpeedEnvelope?.getOrNull(lastIdx)
            if (envLast != null && envLast.isFinite()) {
                lastSpeed = min(lastSpeed, envLast)
            }
            smoothedStates[lastIdx][2] = lastSpeed
        }

        // Pre-allocate scratch buffers to guarantee zero heap allocations in backward pass
        val aug = Array(STATE_DIM) { DoubleArray(2 * STATE_DIM) }
        val invPNextPrior = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        val fNextWork = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        val pNextPriorWork = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        val fNextTransposed = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        val pFt = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        val ck = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        val deltaX = DoubleArray(STATE_DIM)
        val cDeltaX = DoubleArray(STATE_DIM)
        val deltaCov = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        val cDeltaCov = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        val ckTransposed = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        val cDeltaCovCt = Array(STATE_DIM) { DoubleArray(STATE_DIM) }

        // Backward recursion from N-2 down to 0
        for (k in n - 2 downTo 0) {
            val curr = snapshots[k]
            val next = snapshots[k + 1]
            val currStationary = stationaryMask != null && k < stationaryMask.size && stationaryMask[k]

            // REQ-FIL-045.2: Decouple backward RTS completely across pause boundaries (C_k = 0)
            if (isPauseBoundary(k + 1)) {
                for (i in 0 until STATE_DIM) {
                    smoothedStates[k][i] = curr.statePosterior[i]
                    for (j in 0 until STATE_DIM) {
                        smoothedCovs[k][i][j] = curr.covariancePosterior[i][j]
                    }
                }
                smoothedStates[k][3] = UnicycleOdometryEkf.normalizeAngle(smoothedStates[k][3])
                var decoupledSpeed = if (currStationary) 0.0 else sanitizeNonNegativeFinite(smoothedStates[k][2])
                val envLimit = maxSpeedEnvelope?.getOrNull(k)
                if (!currStationary && envLimit != null && envLimit.isFinite()) {
                    decoupledSpeed = min(decoupledSpeed, envLimit)
                }
                smoothedStates[k][2] = decoupledSpeed
                continue
            }

            val nextStationary = stationaryMask != null && (k + 1) < stationaryMask.size && stationaryMask[k + 1]
            val currDiscontinuity = discontinuityMask != null && k < discontinuityMask.size && discontinuityMask[k]
            val nextDiscontinuity = discontinuityMask != null && (k + 1) < discontinuityMask.size && discontinuityMask[k + 1]
            val isDecoupledBoundary = currStationary || nextStationary || currDiscontinuity || nextDiscontinuity

            val pCurrPost = curr.covariancePosterior
            val fNext = if (isDecoupledBoundary) {
                copyAndDecoupleVelocityPosition(next.transitionJacobian, fNextWork, symmetric = false)
                fNextWork
            } else {
                next.transitionJacobian
            }
            val pNextPrior = if (isDecoupledBoundary) {
                copyAndDecoupleVelocityPosition(next.covariancePrior, pNextPriorWork, symmetric = true)
                pNextPriorWork
            } else {
                next.covariancePrior
            }

            // Invert P_{k+1|k} into scratch buffer
            invert5x5(pNextPrior, invPNextPrior, aug)

            // C_k = P_{k|k} * F_k^T * (P_{k+1|k})^{-1}
            transpose5x5(fNext, fNextTransposed)
            matMul5x5(pCurrPost, fNextTransposed, pFt)
            matMul5x5(pFt, invPNextPrior, ck)

            // deltaX = x^s_{k+1} - x_{k+1|k}
            for (i in 0 until STATE_DIM) {
                deltaX[i] = smoothedStates[k + 1][i] - next.statePrior[i]
            }
            // Normalize heading angle difference
            deltaX[3] = UnicycleOdometryEkf.normalizeAngle(deltaX[3])

            // x^s_k = x_{k|k} + C_k * deltaX
            matVecMul5x5(ck, deltaX, cDeltaX)
            for (i in 0 until STATE_DIM) {
                smoothedStates[k][i] = curr.statePosterior[i] + cDeltaX[i]
            }
            if (currStationary && nextStationary) {
                // REQ-FIL-032.3: Pin [E, N] identically across consecutive stationary epochs (0.00 m Haversine drift)
                smoothedStates[k][0] = smoothedStates[k + 1][0]
                smoothedStates[k][1] = smoothedStates[k + 1][1]
            }
            smoothedStates[k][3] = UnicycleOdometryEkf.normalizeAngle(smoothedStates[k][3])
            var smoothedSpeed = if (currStationary) 0.0 else sanitizeNonNegativeFinite(smoothedStates[k][2])
            val envLimit = maxSpeedEnvelope?.getOrNull(k)
            if (!currStationary && envLimit != null && envLimit.isFinite()) {
                smoothedSpeed = min(smoothedSpeed, envLimit)
            }
            smoothedStates[k][2] = smoothedSpeed // Pin stationary speed & bound discontinuity catch-up overshoot

            // P^s_k = P_{k|k} + C_k * (P^s_{k+1} - P_{k+1|k}) * C_k^T
            matSub5x5(smoothedCovs[k + 1], pNextPrior, deltaCov)
            matMul5x5(ck, deltaCov, cDeltaCov)
            transpose5x5(ck, ckTransposed)
            matMul5x5(cDeltaCov, ckTransposed, cDeltaCovCt)

            for (i in 0 until STATE_DIM) {
                for (j in 0 until STATE_DIM) {
                    smoothedCovs[k][i][j] = pCurrPost[i][j] + cDeltaCovCt[i][j]
                }
            }
            symmetrize5x5(smoothedCovs[k])
        }

        // Build smoothed points with geographic coordinates and distance increments
        val result = ArrayList<SmoothedOdometryPoint>(n)
        for (k in 0 until n) {
            val xk = smoothedStates[k]
            val (lat, lon) = enuToLatLng(xk[0], xk[1], refLat, refLon)
            val bearing = headingToBearingDeg(xk[3])

            // REQ-FIL-045.2: Never integrate distance across a pause gap
            val deltaDist = if (k == 0 || isPauseBoundary(k)) {
                0.0
            } else {
                val dt = (snapshots[k].timestampMs - snapshots[k - 1].timestampMs) / 1000.0
                if (dt > 0.0) sanitizeNonNegativeFinite(xk[2] * dt) else 0.0
            }

            result.add(
                SmoothedOdometryPoint(
                    timestampMs = snapshots[k].timestampMs,
                    eastMeters = xk[0],
                    northMeters = xk[1],
                    speedMps = xk[2],
                    headingRad = xk[3],
                    gyroBiasRadPerSec = xk[4],
                    latitude = lat,
                    longitude = lon,
                    bearingDeg = bearing,
                    deltaDistanceMeters = deltaDist
                )
            )
        }
        return result
    }

    /**
     * Executes complete forward EKF replay followed by backward RTS smoothing on raw samples.
     *
     * When [initialHeadingRad] is omitted (`Double.NaN`), resolves initial heading from the first
     * valid GNSS movement chord (falling back to moving GNSS bearing, then North `PI / 2.0`)
     * instead of hardcoding North (REQ-FIL-025.1, ATT-8705).
     */
    @JvmOverloads
    fun smoothSamples(
        samples: List<RawOdometrySample>,
        initialHeadingRad: Double = Double.NaN
    ): List<SmoothedOdometryPoint> {
        // REQ-FIL-019.2: Select tangent-plane reference origin strictly from the first valid GNSS sample
        val firstValidIdx = samples.indexOfFirst { it.hasValidGnssFix() }
        if (firstValidIdx < 0) {
            return emptyList()
        }

        val firstValidFix = samples[firstValidIdx]
        val refLat = firstValidFix.latitude!!
        val refLon = firstValidFix.longitude!!
        val resolvedGlobalHeading = resolveInitialHeadingRad(
            samples = samples,
            firstValidIdx = firstValidIdx,
            refLat = refLat,
            refLon = refLon,
            explicitHeadingRad = initialHeadingRad
        )

        // REQ-FIL-045.2: Partition contiguous active segments at every pause boundary (isPauseGap or dt > 5 s)
        val result = ArrayList<SmoothedOdometryPoint>(samples.size)
        var segStart = 0
        var fallbackEast = 0.0
        var fallbackNorth = 0.0
        var fallbackHeadingRad = resolvedGlobalHeading

        while (segStart < samples.size) {
            var segEnd = segStart + 1
            while (segEnd < samples.size) {
                val curr = samples[segEnd]
                val prev = samples[segEnd - 1]
                if (curr.isPauseGap || isTimestampPauseGap(prev.timestampMs, curr.timestampMs)) {
                    break
                }
                segEnd++
            }

            val segment = samples.subList(segStart, segEnd)
            val segSmoothed = smoothContiguousSegment(
                samples = segment,
                refLat = refLat,
                refLon = refLon,
                fallbackEast = fallbackEast,
                fallbackNorth = fallbackNorth,
                fallbackHeadingRad = fallbackHeadingRad,
                explicitInitialHeadingRad = if (segStart == 0) initialHeadingRad else Double.NaN
            )
            result.addAll(segSmoothed)
            val lastPt = segSmoothed.lastOrNull()
            if (lastPt != null) {
                if (lastPt.eastMeters.isFinite() && lastPt.northMeters.isFinite()) {
                    fallbackEast = lastPt.eastMeters
                    fallbackNorth = lastPt.northMeters
                }
                if (lastPt.headingRad.isFinite()) {
                    fallbackHeadingRad = lastPt.headingRad
                }
            }
            segStart = segEnd
        }

        return result
    }

    private fun smoothContiguousSegment(
        samples: List<RawOdometrySample>,
        refLat: Double,
        refLon: Double,
        fallbackEast: Double,
        fallbackNorth: Double,
        fallbackHeadingRad: Double,
        explicitInitialHeadingRad: Double
    ): List<SmoothedOdometryPoint> {
        val segFirstValidIdx = samples.indexOfFirst { it.hasValidGnssFix() }
        val initEast: Double
        val initNorth: Double
        val resolvedInitialHeading: Double
        if (segFirstValidIdx >= 0) {
            val segFirstFix = samples[segFirstValidIdx]
            val (e, n) = latLngToEnu(segFirstFix.latitude!!, segFirstFix.longitude!!, refLat, refLon)
            initEast = e
            initNorth = n
            resolvedInitialHeading = resolveInitialHeadingRad(
                samples = samples,
                firstValidIdx = segFirstValidIdx,
                refLat = refLat,
                refLon = refLon,
                explicitHeadingRad = explicitInitialHeadingRad
            )
        } else {
            initEast = fallbackEast
            initNorth = fallbackNorth
            resolvedInitialHeading = if (explicitInitialHeadingRad.isFinite()) {
                UnicycleOdometryEkf.normalizeAngle(explicitInitialHeadingRad)
            } else {
                fallbackHeadingRad
            }
        }
        val filteredYawRates = computeFilteredYawRates(samples)

        val stationaryMask = BooleanArray(samples.size) { isStationarySample(samples[it]) }
        val gatedSpeeds = preGateIsolatedSpeedGlitches(samples, stationaryMask)
        val discontinuityMask = BooleanArray(samples.size)

        // Initialize forward EKF (disable internal snapshotList copies since smoothSamples records its own)
        val initialGatedSpeed = gatedSpeeds.firstOrNull()?.takeIf { it.isFinite() } ?: 0.0
        val ekf = UnicycleOdometryEkf(
            initialEast = initEast,
            initialNorth = initNorth,
            initialSpeed = if (stationaryMask[0]) 0.0 else sanitizeNonNegativeFinite(initialGatedSpeed),
            initialHeadingRad = resolvedInitialHeading,
            recordSnapshots = false
        )

        val snapshots = ArrayList<OdometrySnapshot>(samples.size)

        var lastTimeMs = samples.first().timestampMs
        var hasChordAnchor = false
        var chordAnchorEast = 0.0
        var chordAnchorNorth = 0.0
        var chordAnchorTimeMs = 0L
        var hasLastValidGnss = false
        var lastValidGnssEast = 0.0
        var lastValidGnssNorth = 0.0
        var lastValidGnssTsMs = 0L

        for (i in samples.indices) {
            val sample = samples[i]
            val dtSec = if (i == 0) 1.0 else max(0.001, (sample.timestampMs - lastTimeMs) / 1000.0)
            lastTimeMs = sample.timestampMs
            val isStationary = stationaryMask[i]

            // 1. Predict step with 1-Hz snapshot yaw-rate anti-aliasing (REQ-FIL-025.2)
            val validWheelSpeed = sample.wheelSpeedMps?.takeIf { it.isFinite() }
            val validSpeed = gatedSpeeds[i].takeIf { it.isFinite() }
            val useAntiAliasedYaw = dtSec >= ONE_HZ_REPLAY_MIN_DT_SEC && validWheelSpeed == null
            val rawYawRate = sample.worldYawRateRadPerSec?.takeIf { it.isFinite() } ?: 0.0
            val yawRate = if (useAntiAliasedYaw) filteredYawRates[i] else rawYawRate
            val acc = sample.accuracyMeters?.takeIf { it.isFinite() } ?: 10.0
            val hasTrustedGnss = sample.hasValidGnssFix() && acc <= UnicycleOdometryEkf.MAX_GNSS_ACCURACY_METERS
            val modeProfile = sample.carriageMode.profile
            val gyroNoiseScale = if (useAntiAliasedYaw && hasTrustedGnss) modeProfile.ertsGyroNoiseScale else 1.0

            ekf.predict(dtSec, yawRate, sample.timestampMs, gyroNoiseScale, isStationary = isStationary)

            // Capture prior state and transition Jacobian right after predict
            val priorState = DoubleArray(STATE_DIM) { j ->
                when (j) {
                    0 -> ekf.eastMeters
                    1 -> ekf.northMeters
                    2 -> ekf.speedMps
                    3 -> ekf.headingRad
                    else -> ekf.gyroBiasRadPerSec
                }
            }
            val priorCov = ekf.getCovarianceCopy()
            val transJac = ekf.getTransitionJacobianCopy()

            val enuCoords = if (sample.hasValidGnssFix()) {
                latLngToEnu(sample.latitude!!, sample.longitude!!, refLat, refLon)
            } else {
                null
            }

            // Detect dt_gnss-normalized teleport/unpin step discontinuities using pre-gated speed (REQ-FIL-036.3)
            var isDiscontinuity = false
            if (hasTrustedGnss && enuCoords != null) {
                if (hasLastValidGnss && !isStationary) {
                    val dtGnssSec = (sample.timestampMs - lastValidGnssTsMs) / 1000.0
                    if (dtGnssSec > 0.0 && dtGnssSec <= DISCONTINUITY_MAX_DT_GNSS_SEC) {
                        val stepDist = hypot(enuCoords.first - lastValidGnssEast, enuCoords.second - lastValidGnssNorth)
                        val localSpeedRef = validWheelSpeed ?: validSpeed ?: ekf.speedMps
                        val rapidTeleport = (stepDist / dtGnssSec) > max(6.0, localSpeedRef * 2.2 + 2.5)
                        val postStandstillJump = i > 0 && stationaryMask[i - 1] &&
                            stepDist > max(5.0, localSpeedRef * dtGnssSec * 2.0 + 2.0)
                        if (rapidTeleport || postStandstillJump) {
                            isDiscontinuity = true
                        }
                    }
                }
                lastValidGnssEast = enuCoords.first
                lastValidGnssNorth = enuCoords.second
                lastValidGnssTsMs = sample.timestampMs
                hasLastValidGnss = true
            }
            discontinuityMask[i] = isDiscontinuity

            if (isStationary || isDiscontinuity) {
                transJac[0][2] = 0.0
                transJac[1][2] = 0.0
                priorCov[0][2] = 0.0
                priorCov[2][0] = 0.0
                priorCov[1][2] = 0.0
                priorCov[2][1] = 0.0
            }

            // 2. Anchor heading from GNSS displacement chord before position update so position
            //    cross-covariance never drives speed x[2] negative (REQ-FIL-025.1, REQ-FIL-025.2)
            if (!isStationary && enuCoords != null && acc <= UnicycleOdometryEkf.MAX_GNSS_ACCURACY_METERS) {
                val (eastM, northM) = enuCoords
                if (!hasChordAnchor || isDiscontinuity) {
                    hasChordAnchor = true
                    chordAnchorEast = eastM
                    chordAnchorNorth = northM
                    chordAnchorTimeMs = sample.timestampMs
                } else {
                    val dE = eastM - chordAnchorEast
                    val dN = northM - chordAnchorNorth
                    val chordDist = hypot(dE, dN)
                    val dtChord = (sample.timestampMs - chordAnchorTimeMs) / 1000.0
                    if (chordDist >= MIN_CHORD_DISPLACEMENT_METERS && dtChord > 0.0) {
                        val chordSpeed = chordDist / dtChord
                        if (chordSpeed >= MIN_RUNNING_HEADING_SPEED_MPS) {
                            val chordHeadingRad = atan2(dN, dE)
                            val angularSigma = atan2(acc, chordDist)
                            val chordVar = (angularSigma * angularSigma).coerceIn(0.01, 0.25)
                            ekf.updateOfflineHeading(chordHeadingRad, chordVar, sample.timestampMs)
                        }
                        chordAnchorEast = eastM
                        chordAnchorNorth = northM
                        chordAnchorTimeMs = sample.timestampMs
                    }
                }
            }

            // 3. Apply ZUPT / speed BEFORE GNSS position update to block cross-covariance spikes (REQ-FIL-036.3)
            if (isStationary) {
                ekf.applyZeroVelocityUpdate(sample.timestampMs)
            } else {
                validWheelSpeed?.let { ws ->
                    ekf.updateSpeed(ws, 0.5, sample.timestampMs)
                } ?: validSpeed?.let { s ->
                    val sVar = AttFusedColdStartHelper.calibratedDopplerSpeedVariance(acc)
                    ekf.updateSpeed(s, sVar, sample.timestampMs)
                }
            }
            if (enuCoords != null) {
                ekf.updateGnssPosition(
                    eastMeters = enuCoords.first,
                    northMeters = enuCoords.second,
                    accuracyMeters = acc,
                    timestampMs = sample.timestampMs,
                    isStationary = isStationary,
                    decoupleVelocity = isDiscontinuity
                )
            }
            if (!isStationary && !isDiscontinuity) {
                // 4. Doppler / running-pace GNSS bearing heading update
                if (validSpeed != null && sample.bearingDeg != null && sample.bearingDeg.isFinite()) {
                    val enuHeadingRad = UnicycleOdometryEkf.normalizeAngle(Math.toRadians(90.0 - sample.bearingDeg))
                    if (validSpeed > UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS) {
                        ekf.updateDopplerHeading(enuHeadingRad, Math.toRadians(5.0) * Math.toRadians(5.0), sample.timestampMs)
                    } else if (validSpeed >= MIN_BEARING_HEADING_SPEED_MPS && hasTrustedGnss) {
                        val speedRatio = UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS / validSpeed
                        val bearingVar = (0.04 * speedRatio * speedRatio).coerceIn(0.04, 0.40)
                        ekf.updateOfflineHeading(enuHeadingRad, bearingVar, sample.timestampMs)
                    }
                }
            }

            // Capture posterior state and covariance after measurement updates
            val postState = DoubleArray(STATE_DIM) { j ->
                when (j) {
                    0 -> ekf.eastMeters
                    1 -> ekf.northMeters
                    2 -> ekf.speedMps
                    3 -> ekf.headingRad
                    else -> ekf.gyroBiasRadPerSec
                }
            }
            val postCov = ekf.getCovarianceCopy()

            snapshots.add(
                OdometrySnapshot(
                    timestampMs = sample.timestampMs,
                    dtSec = dtSec,
                    statePrior = priorState,
                    covariancePrior = priorCov,
                    transitionJacobian = transJac,
                    statePosterior = postState,
                    covariancePosterior = postCov
                )
            )
        }

        val maxSpeedEnvelope = computeTransitionSpeedEnvelope(samples, stationaryMask, discontinuityMask, gatedSpeeds)
        return smoothSnapshots(snapshots, refLat, refLon, stationaryMask, discontinuityMask, maxSpeedEnvelope)
    }

    /**
     * Pre-gates isolated 1-tick geometric speed glitches against nearby non-stationary neighbors
     * (`>= 2` moving neighbors within `±3` epochs / `5 s`) without clipping genuine launches
     * (`0, 0, 2.0, 2.2`) or multi-second sprint accelerations (`2.0 -> 3.5 -> 5.0 -> 6.0 m/s`,
     * REQ-FIL-036.3).
     */
    private fun preGateIsolatedSpeedGlitches(
        samples: List<RawOdometrySample>,
        stationaryMask: BooleanArray
    ): DoubleArray {
        val n = samples.size
        val gated = DoubleArray(n) { i ->
            samples[i].speedMps?.takeIf { it.isFinite() } ?: Double.NaN
        }
        val scratch = DoubleArray(2 * GLITCH_NEIGHBOR_HALF_WIDTH)
        for (i in 0 until n) {
            if (stationaryMask[i]) continue
            if (samples[i].wheelSpeedMps?.isFinite() == true) continue
            val s = gated[i]
            if (!s.isFinite() || s <= STATIONARY_SPEED_THRESHOLD_MPS) continue

            val centerTs = samples[i].timestampMs
            var count = 0
            val lo = max(0, i - GLITCH_NEIGHBOR_HALF_WIDTH)
            val hi = min(n - 1, i + GLITCH_NEIGHBOR_HALF_WIDTH)
            for (j in lo..hi) {
                if (j == i || stationaryMask[j]) continue
                if (abs(samples[j].timestampMs - centerTs) > GLITCH_NEIGHBOR_MAX_GAP_MS) continue
                val nj = samples[j].speedMps?.takeIf { it.isFinite() && it > GLITCH_MIN_MOVING_SPEED_MPS } ?: continue
                scratch[count++] = nj
            }
            if (count < 2) continue

            // Insertion sort tiny scratch[0 until count]
            for (a in 1 until count) {
                val key = scratch[a]
                var b = a - 1
                while (b >= 0 && scratch[b] > key) {
                    scratch[b + 1] = scratch[b]
                    b--
                }
                scratch[b + 1] = key
            }
            val localMedian = if (count % 2 == 1) {
                scratch[count / 2]
            } else {
                0.5 * (scratch[count / 2 - 1] + scratch[count / 2])
            }
            val spikeThreshold = max(2.0 * localMedian, localMedian + 2.5)
            if (s <= spikeThreshold) continue

            val prevMoving = if (i > 0 && !stationaryMask[i - 1] &&
                abs(centerTs - samples[i - 1].timestampMs) <= GLITCH_IMMEDIATE_MAX_GAP_MS
            ) {
                samples[i - 1].speedMps?.takeIf { it.isFinite() }
            } else {
                null
            }
            val nextMoving = if (i + 1 < n && !stationaryMask[i + 1] &&
                abs(samples[i + 1].timestampMs - centerTs) <= GLITCH_IMMEDIATE_MAX_GAP_MS
            ) {
                samples[i + 1].speedMps?.takeIf { it.isFinite() }
            } else {
                null
            }
            val corroboratedByImmediateNeighbor = (prevMoving != null && prevMoving >= 0.75 * s) ||
                (nextMoving != null && nextMoving >= 0.75 * s)
            if (!corroboratedByImmediateNeighbor) {
                gated[i] = localMedian
            }
        }
        return gated
    }

    private fun computeTransitionSpeedEnvelope(
        samples: List<RawOdometrySample>,
        stationaryMask: BooleanArray,
        discontinuityMask: BooleanArray,
        gatedSpeeds: DoubleArray
    ): DoubleArray {
        val n = samples.size
        val envelope = DoubleArray(n) { Double.POSITIVE_INFINITY }
        for (i in 0 until n) {
            if (stationaryMask[i]) continue
            val centerTs = samples[i].timestampMs
            var nearTransition = false
            val tLo = max(0, i - TRANSITION_ENVELOPE_HALF_WIDTH)
            val tHi = min(n - 1, i + TRANSITION_ENVELOPE_HALF_WIDTH)
            for (j in tLo..tHi) {
                if (abs(samples[j].timestampMs - centerTs) > TRANSITION_ENVELOPE_MAX_GAP_MS) continue
                if (discontinuityMask[j] || (j > 0 && stationaryMask[j] != stationaryMask[j - 1])) {
                    nearTransition = true
                    break
                }
            }
            if (!nearTransition) continue

            var localMax = Double.NEGATIVE_INFINITY
            val sLo = max(0, i - SPEED_ENVELOPE_HALF_WIDTH)
            val sHi = min(n - 1, i + SPEED_ENVELOPE_HALF_WIDTH)
            for (m in sLo..sHi) {
                if (stationaryMask[m]) continue
                if (abs(samples[m].timestampMs - centerTs) > SPEED_ENVELOPE_MAX_GAP_MS) continue
                val spd = samples[m].wheelSpeedMps?.takeIf { it.isFinite() && it >= 0.0 }
                    ?: gatedSpeeds[m].takeIf { it.isFinite() && it >= 0.0 }
                    ?: continue
                if (spd > localMax) localMax = spd
            }
            if (localMax.isFinite() && localMax >= 0.0) {
                envelope[i] = localMax * 1.25 + 0.5
            }
        }
        return envelope
    }

    private fun resolveInitialHeadingRad(
        samples: List<RawOdometrySample>,
        firstValidIdx: Int,
        refLat: Double,
        refLon: Double,
        explicitHeadingRad: Double
    ): Double {
        if (explicitHeadingRad.isFinite()) {
            return UnicycleOdometryEkf.normalizeAngle(explicitHeadingRad)
        }

        val firstFix = samples[firstValidIdx]
        val (firstEast, firstNorth) = latLngToEnu(firstFix.latitude!!, firstFix.longitude!!, refLat, refLon)
        val firstFixTimeMs = firstFix.timestampMs
        for (j in (firstValidIdx + 1) until samples.size) {
            val s = samples[j]
            if (!s.hasValidGnssFix()) continue
            val acc = s.accuracyMeters ?: 10.0
            if (acc > UnicycleOdometryEkf.MAX_GNSS_ACCURACY_METERS) continue

            val (eastM, northM) = latLngToEnu(s.latitude!!, s.longitude!!, refLat, refLon)
            val dE = eastM - firstEast
            val dN = northM - firstNorth
            val dist = hypot(dE, dN)
            val dtSec = (s.timestampMs - firstFixTimeMs) / 1000.0
            if (dist >= MIN_CHORD_DISPLACEMENT_METERS && (dtSec <= 0.0 || dist / dtSec >= 0.4)) {
                return atan2(dN, dE)
            }
        }

        val movingBearing = samples.firstOrNull {
            it.bearingDeg != null && it.bearingDeg.isFinite() && (it.speedMps == null || it.speedMps >= 0.5)
        }?.bearingDeg
        if (movingBearing != null) {
            return UnicycleOdometryEkf.normalizeAngle(Math.toRadians(90.0 - movingBearing))
        }

        return PI / 2.0
    }

    private fun computeFilteredYawRates(samples: List<RawOdometrySample>): DoubleArray {
        val n = samples.size
        val filtered = DoubleArray(n)
        for (i in 0 until n) {
            val sample = samples[i]
            val halfWidth = sample.carriageMode.profile.ertsYawHalfWidthSamples.coerceIn(0, 3)
            val centerTs = sample.timestampMs
            var sum = 0.0
            var count = 0
            val lo = max(0, i - halfWidth)
            val hi = min(n - 1, i + halfWidth)
            for (j in lo..hi) {
                val yr = samples[j].worldYawRateRadPerSec ?: continue
                if (!yr.isFinite()) continue
                if (abs(samples[j].timestampMs - centerTs) <= YAW_RATE_WINDOW_MAX_GAP_MS) {
                    sum += yr
                    count++
                }
            }
            val meanRate = if (count > 0) sum / count else 0.0
            val yawCap = if (sample.carriageMode == PhoneCarriageMode.BIKE_HANDLEBAR) 1.5 else MAX_ONE_HZ_YAW_RATE_RADPS
            filtered[i] = meanRate.coerceIn(-yawCap, yawCap)
        }
        return filtered
    }

    fun enuToLatLng(eastM: Double, northM: Double, refLat: Double, refLon: Double): Pair<Double, Double> {
        val lat = refLat + Math.toDegrees(northM / EARTH_RADIUS_METERS)
        val cosRef = cos(Math.toRadians(refLat))
        val lon = if (abs(cosRef) > 1e-6) {
            refLon + Math.toDegrees(eastM / (EARTH_RADIUS_METERS * cosRef))
        } else {
            refLon
        }
        return Pair(lat, lon)
    }

    fun latLngToEnu(lat: Double, lon: Double, refLat: Double, refLon: Double): Pair<Double, Double> {
        val dLatRad = Math.toRadians(lat - refLat)
        val dLonRad = Math.toRadians(lon - refLon)
        val northMeters = dLatRad * EARTH_RADIUS_METERS
        val eastMeters = dLonRad * EARTH_RADIUS_METERS * cos(Math.toRadians(refLat))
        return Pair(eastMeters, northMeters)
    }

    fun headingToBearingDeg(headingRad: Double): Double {
        val deg = Math.toDegrees(PI / 2.0 - headingRad)
        var norm = deg % 360.0
        if (norm < 0.0) norm += 360.0
        return norm
    }

    /**
     * Pure Kotlin Gauss-Jordan 5x5 matrix inversion with partial pivoting.
     */
    fun invert5x5(m: Array<DoubleArray>): Array<DoubleArray> {
        val out = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        val aug = Array(STATE_DIM) { DoubleArray(2 * STATE_DIM) }
        invert5x5(m, out, aug)
        return out
    }

    fun invert5x5(m: Array<DoubleArray>, out: Array<DoubleArray>, aug: Array<DoubleArray>) {
        val n = STATE_DIM
        for (i in 0 until n) {
            val mi = m[i]
            val augi = aug[i]
            for (j in 0 until n) {
                augi[j] = mi[j]
                augi[j + n] = if (i == j) 1.0 else 0.0
            }
        }

        for (col in 0 until n) {
            var maxRow = col
            var maxVal = abs(aug[col][col])
            for (row in col + 1 until n) {
                val v = abs(aug[row][col])
                if (v > maxVal) {
                    maxVal = v
                    maxRow = row
                }
            }

            if (maxVal < 1e-12) {
                // Tikhonov regularization for near-singular covariance blocks
                aug[col][col] += 1e-6
            }

            if (maxRow != col) {
                val temp = aug[col]
                aug[col] = aug[maxRow]
                aug[maxRow] = temp
            }

            val pivot = aug[col][col]
            val invPivot = 1.0 / pivot
            val augCol = aug[col]
            for (j in 0 until 2 * n) {
                augCol[j] *= invPivot
            }

            for (r in 0 until n) {
                if (r != col) {
                    val factor = aug[r][col]
                    if (abs(factor) > 1e-15) {
                        val augR = aug[r]
                        for (j in 0 until 2 * n) {
                            augR[j] -= factor * augCol[j]
                        }
                    }
                }
            }
        }

        for (i in 0 until n) {
            val augi = aug[i]
            val outi = out[i]
            for (j in 0 until n) {
                outi[j] = augi[j + n]
            }
        }
    }

    fun matMul5x5(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
        val res = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        matMul5x5(a, b, res)
        return res
    }

    fun matMul5x5(a: Array<DoubleArray>, b: Array<DoubleArray>, out: Array<DoubleArray>) {
        val n = STATE_DIM
        for (i in 0 until n) {
            val outi = out[i]
            for (j in 0 until n) {
                outi[j] = 0.0
            }
            for (k in 0 until n) {
                val aik = a[i][k]
                if (abs(aik) > 1e-15) {
                    val bk = b[k]
                    for (j in 0 until n) {
                        outi[j] += aik * bk[j]
                    }
                }
            }
        }
    }

    fun matVecMul5x5(a: Array<DoubleArray>, v: DoubleArray): DoubleArray {
        val res = DoubleArray(STATE_DIM)
        matVecMul5x5(a, v, res)
        return res
    }

    fun matVecMul5x5(a: Array<DoubleArray>, v: DoubleArray, out: DoubleArray) {
        val n = STATE_DIM
        for (i in 0 until n) {
            var sum = 0.0
            val ai = a[i]
            for (j in 0 until n) {
                sum += ai[j] * v[j]
            }
            out[i] = sum
        }
    }

    fun matSub5x5(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
        val res = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        matSub5x5(a, b, res)
        return res
    }

    fun matSub5x5(a: Array<DoubleArray>, b: Array<DoubleArray>, out: Array<DoubleArray>) {
        val n = STATE_DIM
        for (i in 0 until n) {
            val ai = a[i]
            val bi = b[i]
            val outi = out[i]
            for (j in 0 until n) {
                outi[j] = ai[j] - bi[j]
            }
        }
    }

    fun transpose5x5(a: Array<DoubleArray>): Array<DoubleArray> {
        val res = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        transpose5x5(a, res)
        return res
    }

    fun transpose5x5(a: Array<DoubleArray>, out: Array<DoubleArray>) {
        val n = STATE_DIM
        for (i in 0 until n) {
            for (j in 0 until n) {
                out[i][j] = a[j][i]
            }
        }
    }

    fun symmetrize5x5(a: Array<DoubleArray>) {
        val n = STATE_DIM
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val avg = 0.5 * (a[i][j] + a[j][i])
                a[i][j] = avg
                a[j][i] = avg
            }
        }
    }
}
