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

import androidx.annotation.VisibleForTesting
import com.atrainingtracker.banalservice.BSportType
import java.util.ArrayDeque
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Sport-level hint passed opportunistically to [PhoneCarriageClassifier] without hard dependency
 * on Android framework services.
 */
enum class SportContextHint {
    UNKNOWN,
    CYCLING,
    RUNNING,
    OTHER;

    companion object {
        @JvmStatic
        fun fromBSportType(bSportType: BSportType?): SportContextHint = when (bSportType) {
            BSportType.BIKE -> CYCLING
            BSportType.RUN -> RUNNING
            BSportType.CONFLICT -> OTHER
            BSportType.UNKNOWN, null -> UNKNOWN
        }
    }
}

/**
 * Pure-JVM sliding-window IMU, orientation, barometer, step-detector, and speed classifier
 * producing hysteresis-stabilized [PhoneCarriageMode] classifications at 1 Hz (REQ-FIL-028.1, ATT-8890).
 *
 * Degrades deterministically when any subset of hardware sensors (`TYPE_STEP_DETECTOR`,
 * `TYPE_GAME_ROTATION_VECTOR`, `TYPE_GYROSCOPE`, `TYPE_PRESSURE`) or permissions are absent.
 */
@Suppress("MagicNumber", "TooManyFunctions", "ComplexMethod", "ReturnCount")
class PhoneCarriageClassifier(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val mountHysteresisMs: Long = DEFAULT_MOUNT_HYSTERESIS_MS,
    private val dismountHysteresisMs: Long = DEFAULT_DISMOUNT_HYSTERESIS_MS
) {
    companion object {
        const val DEFAULT_WINDOW_MS = 6_000L
        const val DEFAULT_MOUNT_HYSTERESIS_MS = 4_000L
        const val DEFAULT_DISMOUNT_HYSTERESIS_MS = 2_000L
        private const val MIN_WARMUP_MS = 1_800L
        private const val STALENESS_MS = 6_000L
        private const val WHEEL_STALENESS_MS = 3_000L
        private const val STEP_ACTIVE_TIMEOUT_MS = 2_200L
        private const val GLANCE_SHIFT_THRESHOLD_DEG = 15.0
        private const val GLANCE_DEBOUNCE_MS = 700L
        private const val GLANCE_MEMORY_MS = 15_000L
        private const val GLANCE_ACTIVE_HOLD_MS = 1_200L
        private const val STATIONARY_SPEED_MPS = 0.15
        private const val WALK_MIN_SPEED_MPS = 0.35
        private const val WALK_MAX_SPEED_MPS = 2.50
        private const val SCORE_MARGIN = 0.25
    }

    private data class GravitySample(val timestampMs: Long, val gx: Double, val gy: Double, val gz: Double)
    private data class GyroSample(val timestampMs: Long, val yawResidualRadPerSec: Double, val rawMagRadPerSec: Double)
    private data class BaroSample(val timestampMs: Long, val altitudeM: Double)

    private val gravitySamples = ArrayDeque<GravitySample>()
    private val gyroSamples = ArrayDeque<GyroSample>()
    private val baroSamples = ArrayDeque<BaroSample>()
    private val stepTimestampsMs = ArrayDeque<Long>()
    private val glanceTimestampsMs = ArrayDeque<Long>()

    private var lastGnssSpeedMps: Double? = null
    private var lastGnssTimestampMs: Long = Long.MIN_VALUE
    private var lastGnssBearingDeg: Double? = null
    private var lastGnssBearingTimestampMs: Long = Long.MIN_VALUE
    private var estimatedGnssYawRateRadPerSec: Double = 0.0

    private var lastWheelSpeedMps: Double? = null
    private var lastWheelTimestampMs: Long = Long.MIN_VALUE

    private var sportHint: SportContextHint = SportContextHint.UNKNOWN
    private var firstSampleTimestampMs: Long = Long.MIN_VALUE
    private var lastEventTimestampMs: Long = Long.MIN_VALUE

    private var lastGlanceEventMs: Long = Long.MIN_VALUE
    private var committedMode: PhoneCarriageMode = PhoneCarriageMode.UNKNOWN
    private var candidateMode: PhoneCarriageMode = PhoneCarriageMode.UNKNOWN
    private var candidateSinceMs: Long = Long.MIN_VALUE

    val currentMode: PhoneCarriageMode
        @Synchronized get() = committedMode

    @Synchronized
    fun setSportContext(hint: SportContextHint) {
        sportHint = hint
    }

    @Synchronized
    fun reset() {
        gravitySamples.clear()
        gyroSamples.clear()
        baroSamples.clear()
        stepTimestampsMs.clear()
        glanceTimestampsMs.clear()
        lastGnssSpeedMps = null
        lastGnssTimestampMs = Long.MIN_VALUE
        lastGnssBearingDeg = null
        lastGnssBearingTimestampMs = Long.MIN_VALUE
        estimatedGnssYawRateRadPerSec = 0.0
        lastWheelSpeedMps = null
        lastWheelTimestampMs = Long.MIN_VALUE
        firstSampleTimestampMs = Long.MIN_VALUE
        lastEventTimestampMs = Long.MIN_VALUE
        lastGlanceEventMs = Long.MIN_VALUE
        committedMode = PhoneCarriageMode.UNKNOWN
        candidateMode = PhoneCarriageMode.UNKNOWN
        candidateSinceMs = Long.MIN_VALUE
    }

    @Synchronized
    fun ingestRotationVector(rotationVector: FloatArray, timestampMs: Long) {
        if (rotationVector.size < 3) return
        val qx = rotationVector[0].toDouble()
        val qy = rotationVector[1].toDouble()
        val qz = rotationVector[2].toDouble()
        if (!qx.isFinite() || !qy.isFinite() || !qz.isFinite()) return

        val qw = if (rotationVector.size >= 4 && rotationVector[3].isFinite()) {
            rotationVector[3].toDouble()
        } else {
            sqrt(max(0.0, 1.0 - (qx * qx + qy * qy + qz * qz)))
        }

        // Row 2 of world->body or body->ENU matrix gives world +Z (Up) expressed in body frame
        val gx = 2.0 * (qx * qz - qw * qy)
        val gy = 2.0 * (qy * qz + qw * qx)
        val gz = 1.0 - 2.0 * (qx * qx + qy * qy)
        val norm = sqrt(gx * gx + gy * gy + gz * gz)
        if (!norm.isFinite() || norm < 1e-6) return

        val sample = GravitySample(timestampMs, gx / norm, gy / norm, gz / norm)
        noteTimestamp(timestampMs)
        gravitySamples.addLast(sample)
        pruneWindows(timestampMs)
        detectGlanceShift(timestampMs)
    }

    @Synchronized
    fun ingestGyro(
        worldYawRateRadPerSec: Double,
        rawGyroMagRadPerSec: Double = abs(worldYawRateRadPerSec),
        timestampMs: Long
    ) {
        if (!worldYawRateRadPerSec.isFinite() || !rawGyroMagRadPerSec.isFinite()) return
        noteTimestamp(timestampMs)
        val refCourseRate = if (timestampMs - lastGnssBearingTimestampMs <= STALENESS_MS) {
            estimatedGnssYawRateRadPerSec
        } else {
            0.0
        }
        val yawResidual = abs(worldYawRateRadPerSec - refCourseRate)
        gyroSamples.addLast(GyroSample(timestampMs, yawResidual, abs(rawGyroMagRadPerSec)))
        pruneWindows(timestampMs)
    }

    @Synchronized
    fun ingestBarometerAltitude(baroAltitudeM: Double, timestampMs: Long) {
        if (!baroAltitudeM.isFinite()) return
        noteTimestamp(timestampMs)
        baroSamples.addLast(BaroSample(timestampMs, baroAltitudeM))
        pruneWindows(timestampMs)
    }

    @Synchronized
    fun ingestStepEvent(timestampMs: Long) {
        noteTimestamp(timestampMs)
        stepTimestampsMs.addLast(timestampMs)
        pruneWindows(timestampMs)
    }

    @Synchronized
    fun ingestGnssSpeed(
        speedMps: Double,
        bearingDeg: Double? = null,
        accuracyMeters: Double? = null,
        timestampMs: Long
    ) {
        noteTimestamp(timestampMs)
        if (speedMps.isFinite() && speedMps >= 0.0) {
            lastGnssSpeedMps = speedMps
            lastGnssTimestampMs = timestampMs
        }
        val validBearing = bearingDeg != null && bearingDeg.isFinite() &&
            (accuracyMeters == null || (accuracyMeters.isFinite() && accuracyMeters <= 25.0))
        if (validBearing && speedMps.isFinite() && speedMps >= 1.2) {
            val safeBearing = bearingDeg
            val prevB = lastGnssBearingDeg
            val prevT = lastGnssBearingTimestampMs
            if (prevB != null && prevT > Long.MIN_VALUE && timestampMs > prevT && timestampMs - prevT <= 3_000L) {
                // Geographic bearing increases clockwise; ENU yaw is counter-clockwise
                val dBearingRad = UnicycleOdometryEkf.normalizeAngle(Math.toRadians(prevB - safeBearing))
                val dtSec = (timestampMs - prevT) / 1000.0
                estimatedGnssYawRateRadPerSec = (dBearingRad / dtSec).coerceIn(-3.0, 3.0)
            } else {
                estimatedGnssYawRateRadPerSec = 0.0
            }
            lastGnssBearingDeg = safeBearing
            lastGnssBearingTimestampMs = timestampMs
        } else {
            estimatedGnssYawRateRadPerSec = 0.0
            lastGnssBearingDeg = null
            lastGnssBearingTimestampMs = Long.MIN_VALUE
        }
        pruneWindows(timestampMs)
    }

    @VisibleForTesting
    @Synchronized
    internal fun currentEstimatedGnssYawRateRadPerSecForTest(): Double = estimatedGnssYawRateRadPerSec

    @Synchronized
    fun ingestWheelSpeed(wheelSpeedMps: Double, timestampMs: Long) {
        if (!wheelSpeedMps.isFinite() || wheelSpeedMps < 0.0) return
        noteTimestamp(timestampMs)
        lastWheelSpeedMps = wheelSpeedMps
        lastWheelTimestampMs = timestampMs
        pruneWindows(timestampMs)
    }

    @Synchronized
    fun isGlanceActive(timestampMs: Long = lastEventTimestampMs): Boolean {
        return lastGlanceEventMs > Long.MIN_VALUE &&
            timestampMs >= lastGlanceEventMs &&
            (timestampMs - lastGlanceEventMs) <= GLANCE_ACTIVE_HOLD_MS
    }

    /**
     * Estimates active running step frequency in Hz when hardware step events are flowing,
     * or returns `null` when unavailable/stale.
     */
    @Synchronized
    fun currentStepFrequencyHz(nowMs: Long = lastEventTimestampMs): Double? {
        if (stepTimestampsMs.size < 3 || nowMs == Long.MIN_VALUE) return null
        val newest = stepTimestampsMs.peekLast() ?: return null
        if (nowMs - newest > STEP_ACTIVE_TIMEOUT_MS) return null
        val oldest = stepTimestampsMs.peekFirst() ?: return null
        val spanSec = (newest - oldest) / 1000.0
        if (spanSec < 1.0) return null
        return ((stepTimestampsMs.size - 1) / spanSec).coerceIn(0.4, 5.0)
    }

    /**
     * Evaluates the sliding feature window at [nowMs] and returns the hysteresis-stabilized [PhoneCarriageMode].
     */
    @Synchronized
    fun classify(nowMs: Long = lastEventTimestampMs): PhoneCarriageMode {
        if (nowMs == Long.MIN_VALUE || firstSampleTimestampMs == Long.MIN_VALUE) {
            return committedMode
        }
        pruneWindows(nowMs)

        val rawCandidate = evaluateRawCandidate(nowMs)
        if (rawCandidate == PhoneCarriageMode.UNKNOWN) {
            return committedMode
        }

        if (committedMode == PhoneCarriageMode.UNKNOWN) {
            val elapsedMs = nowMs - firstSampleTimestampMs
            if (elapsedMs >= MIN_WARMUP_MS || hasStrongDirectEvidence(nowMs, rawCandidate)) {
                committedMode = rawCandidate
                candidateMode = rawCandidate
                candidateSinceMs = nowMs
            }
            return committedMode
        }

        if (rawCandidate == committedMode) {
            candidateMode = committedMode
            candidateSinceMs = Long.MIN_VALUE
            return committedMode
        }

        if (candidateMode != rawCandidate || candidateSinceMs == Long.MIN_VALUE) {
            candidateMode = rawCandidate
            candidateSinceMs = nowMs
            return committedMode
        }

        val requiredDwellMs = if (isDismountOrRemountTransition(committedMode, rawCandidate)) {
            dismountHysteresisMs
        } else {
            mountHysteresisMs
        }
        if (nowMs - candidateSinceMs >= requiredDwellMs) {
            committedMode = rawCandidate
            candidateSinceMs = Long.MIN_VALUE
        }
        return committedMode
    }

    private fun isDismountOrRemountTransition(from: PhoneCarriageMode, to: PhoneCarriageMode): Boolean {
        return from.isWalkingOrStationary() || to.isWalkingOrStationary()
    }

    private fun hasStrongDirectEvidence(nowMs: Long, candidate: PhoneCarriageMode): Boolean {
        val speed = effectiveSpeedMps(nowMs) ?: return false
        return candidate == PhoneCarriageMode.STATIONARY &&
            speed <= STATIONARY_SPEED_MPS &&
            gravitySamples.size >= 5
    }

    private fun evaluateRawCandidate(nowMs: Long): PhoneCarriageMode {
        val wheelFresh = lastWheelTimestampMs > Long.MIN_VALUE && (nowMs - lastWheelTimestampMs) <= WHEEL_STALENESS_MS
        val wheelSpeed = if (wheelFresh) lastWheelSpeedMps else null
        val gnssFresh = lastGnssTimestampMs > Long.MIN_VALUE && (nowMs - lastGnssTimestampMs) <= STALENESS_MS
        val gnssSpeed = if (gnssFresh) lastGnssSpeedMps else null
        val effectiveSpeed = effectiveSpeedMps(nowMs)

        val stepFreqHz = currentStepFrequencyHz(nowMs)
        // Without any speed/step reference, retain UNKNOWN so IMU-only gyro unit checks stay at neutral 1.0 s window
        if (effectiveSpeed == null && stepFreqHz == null) {
            return PhoneCarriageMode.UNKNOWN
        }

        val hasOrientation = gravitySamples.size >= 3
        val orientationStats = if (hasOrientation) computeOrientationStats() else null
        val hasGyro = gyroSamples.size >= 3
        val yawResidualRms = if (hasGyro) computeGyroRms { it.yawResidualRadPerSec } else null
        val rawGyroRms = if (hasGyro) computeGyroRms { it.rawMagRadPerSec } else null
        val baroStepRms = computeOneHzBaroStepRms()
        val glanceCount = glanceTimestampsMs.size
        val dominantFreqHz = estimateDominantOscillationHz(orientationStats, rawGyroRms)

        if (effectiveSpeed == null && !hasOrientation && !hasGyro && baroStepRms == null && stepFreqHz == null) {
            return PhoneCarriageMode.UNKNOWN
        }

        // 1. Stationary detection (bakery counter, traffic light stop)
        val lowSpeed = effectiveSpeed != null && effectiveSpeed <= STATIONARY_SPEED_MPS
        val quietImu = (!hasOrientation || (orientationStats!!.swingSdDeg < 2.5)) &&
            (!hasGyro || (rawGyroRms!! < 0.14)) &&
            stepFreqHz == null
        if (lowSpeed && (quietImu || (effectiveSpeed <= 0.10 && (wheelSpeed == null || wheelSpeed <= 0.10)))) {
            return PhoneCarriageMode.STATIONARY
        }

        // 2. Walking / Transition dismount detection (independent of cycling sportHint!)
        val newerWalkEvidenceThanWheel = lastWheelTimestampMs == Long.MIN_VALUE ||
            (lastGnssTimestampMs > lastWheelTimestampMs + 1_500L && gnssSpeed != null && gnssSpeed <= WALK_MAX_SPEED_MPS)
        val wheelActiveMoving = wheelSpeed != null && wheelSpeed > 0.5 && !newerWalkEvidenceThanWheel
        val walkingSpeedBand = effectiveSpeed != null && effectiveSpeed in WALK_MIN_SPEED_MPS..WALK_MAX_SPEED_MPS
        val walkingStepBand = stepFreqHz != null && stepFreqHz in 1.15..2.30
        if (!wheelActiveMoving) {
            if (walkingStepBand && (effectiveSpeed == null || effectiveSpeed <= WALK_MAX_SPEED_MPS)) {
                return PhoneCarriageMode.WALKING_TRANSITION
            }
            if (walkingSpeedBand) {
                val gyroOrSwingPresent = yawResidualRms == null || yawResidualRms >= 0.08 ||
                    (orientationStats != null && orientationStats.swingSdDeg >= 4.0)
                val isPendulumWalking = dominantFreqHz == null || dominantFreqHz in 0.60..1.12
                val isStepHarmonicWalking = dominantFreqHz != null && dominantFreqHz in 1.12..2.35 &&
                    (effectiveSpeed <= 2.15 || sportHint != SportContextHint.RUNNING)
                if (stepFreqHz == null && gyroOrSwingPresent && (isPendulumWalking || isStepHarmonicWalking)) {
                    return PhoneCarriageMode.WALKING_TRANSITION
                }
            }
        }

        // 3. Score cycling vs running carriage regimes
        var cyclingPrior = 0.0
        var runningPrior = 0.0
        when {
            wheelActiveMoving -> cyclingPrior += 6.0
            stepFreqHz != null && stepFreqHz >= 2.25 -> runningPrior += 5.0
            else -> {
                if (sportHint == SportContextHint.CYCLING) cyclingPrior += 2.5
                if (sportHint == SportContextHint.RUNNING) runningPrior += 2.5
                if (effectiveSpeed != null) {
                    if (effectiveSpeed >= 5.5) cyclingPrior += 3.0
                    else if (effectiveSpeed in 2.2..4.6 && wheelSpeed == null) runningPrior += 0.6
                }
            }
        }

        // Handlebar vs other fallback if zero IMU is available
        if (!hasOrientation && !hasGyro && baroStepRms == null) {
            return if (runningPrior > cyclingPrior) {
                PhoneCarriageMode.RUN_WAIST_HIP
            } else {
                PhoneCarriageMode.BIKE_HANDLEBAR
            }
        }

        val sBikeHandlebar = cyclingPrior + scoreBikeHandlebar(
            orientationStats, yawResidualRms, baroStepRms, effectiveSpeed
        )
        val sBikeJersey = cyclingPrior + scoreBikeJerseyBack(
            orientationStats, yawResidualRms, baroStepRms
        )
        val sBikeFrontPocket = cyclingPrior + scoreBikeFrontPocket(
            orientationStats, yawResidualRms, baroStepRms, dominantFreqHz
        )
        val sRunWaist = runningPrior + scoreRunWaistHip(
            orientationStats, yawResidualRms, baroStepRms, stepFreqHz, glanceCount
        )
        val sRunBicep = runningPrior + scoreRunBicepArm(
            orientationStats, yawResidualRms, baroStepRms, dominantFreqHz, stepFreqHz, glanceCount
        )
        val sRunHandheld = runningPrior + scoreRunHandheld(
            orientationStats, yawResidualRms, baroStepRms, glanceCount
        )

        var bestMode = PhoneCarriageMode.BIKE_HANDLEBAR
        var bestScore = sBikeHandlebar
        if (sBikeJersey > bestScore) { bestScore = sBikeJersey; bestMode = PhoneCarriageMode.BIKE_JERSEY_BACK }
        if (sBikeFrontPocket > bestScore) { bestScore = sBikeFrontPocket; bestMode = PhoneCarriageMode.BIKE_FRONT_POCKET }
        if (sRunWaist > bestScore) { bestScore = sRunWaist; bestMode = PhoneCarriageMode.RUN_WAIST_HIP }
        if (sRunBicep > bestScore) { bestScore = sRunBicep; bestMode = PhoneCarriageMode.RUN_BICEP_ARM }
        if (sRunHandheld > bestScore) { bestScore = sRunHandheld; bestMode = PhoneCarriageMode.RUN_HANDHELD }

        val committedScore = when (committedMode) {
            PhoneCarriageMode.BIKE_HANDLEBAR -> sBikeHandlebar
            PhoneCarriageMode.BIKE_JERSEY_BACK -> sBikeJersey
            PhoneCarriageMode.BIKE_FRONT_POCKET -> sBikeFrontPocket
            PhoneCarriageMode.RUN_WAIST_HIP -> sRunWaist
            PhoneCarriageMode.RUN_BICEP_ARM -> sRunBicep
            PhoneCarriageMode.RUN_HANDHELD -> sRunHandheld
            else -> Double.NEGATIVE_INFINITY
        }
        if (committedScore.isFinite() && bestMode != committedMode && bestScore < committedScore + SCORE_MARGIN) {
            return committedMode
        }
        return bestMode
    }

    private fun scoreBikeHandlebar(
        o: OrientationStats?,
        yawResidualRms: Double?,
        baroStepRms: Double?,
        speedMps: Double?
    ): Double {
        var score = 0.0
        if (o != null) {
            score += if (o.swingSdDeg < 2.5) 3.0 else if (o.swingSdDeg < 4.5) 0.5 else -3.0
            if (o.meanInclinationDeg < 28.0) score += 1.0
        }
        if (yawResidualRms != null) {
            score += if (yawResidualRms < 0.06) 2.5 else if (yawResidualRms < 0.12) 0.5 else -2.5
        }
        if (baroStepRms != null) {
            score += if (baroStepRms < 0.16) 2.0 else if (baroStepRms < 0.22) 0.3 else -2.0
        }
        if (speedMps != null && speedMps > 2.2) score += 0.3
        return score
    }

    private fun scoreBikeJerseyBack(
        o: OrientationStats?,
        yawResidualRms: Double?,
        baroStepRms: Double?
    ): Double {
        var score = 0.0
        if (o != null) {
            score += if (o.meanInclinationDeg in 25.0..75.0) 1.8 else 0.0
            score += when {
                o.swingSdDeg in 1.2..8.5 -> 2.0
                o.swingSdDeg < 1.2 -> 0.4
                else -> -2.0
            }
        }
        if (yawResidualRms != null) {
            score += when {
                yawResidualRms in 0.06..0.28 -> 2.2
                yawResidualRms < 0.06 -> 0.4
                else -> -1.5
            }
        }
        if (baroStepRms != null) {
            score += when {
                baroStepRms in 0.15..0.34 -> 2.0
                baroStepRms < 0.15 -> 0.2
                else -> -1.0
            }
        }
        return score
    }

    private fun scoreBikeFrontPocket(
        o: OrientationStats?,
        yawResidualRms: Double?,
        baroStepRms: Double?,
        dominantFreqHz: Double?
    ): Double {
        var score = 0.0
        if (o != null) {
            score += if (o.swingSdDeg >= 9.0) 2.6 else if (o.swingSdDeg >= 5.5) 1.0 else -2.5
        }
        if (yawResidualRms != null) {
            score += if (yawResidualRms >= 0.35) 2.6 else if (yawResidualRms >= 0.25) 1.0 else -2.0
        }
        if (baroStepRms != null) {
            score += if (baroStepRms >= 0.35) 2.6 else if (baroStepRms >= 0.26) 0.8 else -1.0
        }
        if (dominantFreqHz != null && dominantFreqHz in 0.80..1.75) {
            score += 0.8
        }
        return score
    }

    private fun scoreRunWaistHip(
        o: OrientationStats?,
        yawResidualRms: Double?,
        baroStepRms: Double?,
        stepFreqHz: Double?,
        glanceCount: Int
    ): Double {
        var score = 0.0
        if (o != null) {
            score += if (o.swingSdDeg < 10.0) 2.6 else -2.0
        }
        if (yawResidualRms != null) {
            score += if (yawResidualRms in 0.06..0.38) 2.2 else if (yawResidualRms < 0.06) 0.8 else -1.5
        }
        if (baroStepRms != null) {
            score += if (baroStepRms < 0.40) 1.2 else -0.5
        }
        if (stepFreqHz != null && stepFreqHz >= 2.25) {
            score += 0.8
        }
        if (glanceCount == 0) score += 0.6 else score -= 1.0
        return score
    }

    private fun scoreRunBicepArm(
        o: OrientationStats?,
        yawResidualRms: Double?,
        baroStepRms: Double?,
        dominantFreqHz: Double?,
        stepFreqHz: Double?,
        glanceCount: Int
    ): Double {
        var score = 0.0
        if (o != null) {
            score += when {
                o.swingSdDeg >= 9.0 -> 2.6
                o.swingSdDeg >= 6.5 -> 1.0
                else -> -2.2
            }
        }
        if (yawResidualRms != null) {
            score += when {
                yawResidualRms in 0.16..0.48 -> 2.0
                yawResidualRms > 0.48 -> 0.3
                else -> -1.0
            }
        }
        if (baroStepRms != null) {
            score += if (baroStepRms < 0.36) 1.6 else -0.8
        }
        if (dominantFreqHz != null && stepFreqHz != null && abs(dominantFreqHz - 0.5 * stepFreqHz) <= 0.35) {
            score += 1.4
        } else if (dominantFreqHz != null && dominantFreqHz in 1.1..1.85) {
            score += 0.8
        }
        if (glanceCount == 0) score += 0.8 else score -= 1.5
        return score
    }

    private fun scoreRunHandheld(
        o: OrientationStats?,
        yawResidualRms: Double?,
        baroStepRms: Double?,
        glanceCount: Int
    ): Double {
        var score = 0.0
        if (o != null) {
            score += if (o.swingSdDeg >= 9.0) 1.8 else -1.5
        }
        if (yawResidualRms != null) {
            score += if (yawResidualRms >= 0.48) 2.8 else if (yawResidualRms >= 0.36) 1.0 else -1.5
        }
        if (baroStepRms != null) {
            score += if (baroStepRms >= 0.35) 1.8 else 0.0
        }
        if (glanceCount >= 1) {
            score += 2.2
        }
        return score
    }

    private fun effectiveSpeedMps(nowMs: Long): Double? {
        val wheelFresh = lastWheelTimestampMs > Long.MIN_VALUE && (nowMs - lastWheelTimestampMs) <= WHEEL_STALENESS_MS
        val gnssFresh = lastGnssTimestampMs > Long.MIN_VALUE && (nowMs - lastGnssTimestampMs) <= STALENESS_MS
        val g = if (gnssFresh) lastGnssSpeedMps else null
        val newerWalkEvidence = lastWheelTimestampMs > Long.MIN_VALUE &&
            lastGnssTimestampMs > lastWheelTimestampMs + 1_500L &&
            g != null && g <= WALK_MAX_SPEED_MPS
        val w = if (wheelFresh && !newerWalkEvidence) lastWheelSpeedMps else null
        return when {
            w != null && w > 0.2 -> w
            g != null -> g
            else -> w
        }
    }

    private data class OrientationStats(
        val meanInclinationDeg: Double,
        val swingSdDeg: Double,
        val oscillationAnglesDeg: DoubleArray,
        val timestampsMs: LongArray
    )

    private fun computeOrientationStats(): OrientationStats {
        val n = gravitySamples.size
        var sumGx = 0.0
        var sumGy = 0.0
        var sumGz = 0.0
        for (s in gravitySamples) {
            sumGx += s.gx
            sumGy += s.gy
            sumGz += s.gz
        }
        val norm = sqrt(sumGx * sumGx + sumGy * sumGy + sumGz * sumGz).coerceAtLeast(1e-6)
        val mx = sumGx / norm
        val my = sumGy / norm
        val mz = sumGz / norm
        val meanInclinationDeg = Math.toDegrees(acos(abs(mz).coerceIn(0.0, 1.0)))

        var varX = 0.0
        var varY = 0.0
        var varZ = 0.0
        for (s in gravitySamples) {
            varX += abs(s.gx - mx)
            varY += abs(s.gy - my)
            varZ += abs(s.gz - mz)
        }
        val useX = varX >= varY && varX >= varZ
        val useY = !useX && varY >= varZ

        val angles = DoubleArray(n)
        val ts = LongArray(n)
        var sumSq = 0.0
        var idx = 0
        for (s in gravitySamples) {
            val dot = (s.gx * mx + s.gy * my + s.gz * mz).coerceIn(-1.0, 1.0)
            val angDeg = Math.toDegrees(acos(dot))
            val deltaComponent = when {
                useX -> s.gx - mx
                useY -> s.gy - my
                else -> s.gz - mz
            }
            angles[idx] = if (deltaComponent >= 0.0) angDeg else -angDeg
            ts[idx] = s.timestampMs
            sumSq += angDeg * angDeg
            idx++
        }
        val sdDeg = sqrt(sumSq / n)
        return OrientationStats(meanInclinationDeg, sdDeg, angles, ts)
    }

    private inline fun computeGyroRms(selector: (GyroSample) -> Double): Double {
        var sumSq = 0.0
        for (s in gyroSamples) {
            val v = selector(s)
            sumSq += v * v
        }
        return sqrt(sumSq / gyroSamples.size)
    }

    private fun computeOneHzBaroStepRms(): Double? {
        if (baroSamples.size < 2) return null
        val bucketValues = mutableListOf<Pair<Long, Double>>()
        var bucketSec = Long.MIN_VALUE
        var sumAlt = 0.0
        var count = 0
        for (s in baroSamples) {
            val sec = s.timestampMs / 1_000L
            if (bucketSec == Long.MIN_VALUE) {
                bucketSec = sec
            }
            if (sec != bucketSec && count > 0) {
                bucketValues.add(bucketSec to (sumAlt / count))
                bucketSec = sec
                sumAlt = 0.0
                count = 0
            }
            sumAlt += s.altitudeM
            count++
        }
        if (count > 0) {
            bucketValues.add(bucketSec to (sumAlt / count))
        }
        if (bucketValues.size < 2) return null

        var sumSq = 0.0
        var steps = 0
        for (i in 1 until bucketValues.size) {
            val dtSec = (bucketValues[i].first - bucketValues[i - 1].first).toDouble()
            if (dtSec in 0.5..3.0) {
                val vz = (bucketValues[i].second - bucketValues[i - 1].second) / dtSec
                sumSq += vz * vz
                steps++
            }
        }
        return if (steps > 0) sqrt(sumSq / steps) else null
    }

    private fun estimateDominantOscillationHz(o: OrientationStats?, rawGyroRms: Double?): Double? {
        if (o != null && o.oscillationAnglesDeg.size >= 8 && o.swingSdDeg >= 2.0) {
            val n = o.oscillationAnglesDeg.size
            var sum = 0.0
            for (i in 0 until n) sum += o.oscillationAnglesDeg[i]
            val mean = sum / n
            var firstCrossTs = Long.MIN_VALUE
            var lastCrossTs = Long.MIN_VALUE
            var crossCount = 0
            for (i in 1 until n) {
                val prev = o.oscillationAnglesDeg[i - 1] - mean
                val curr = o.oscillationAnglesDeg[i] - mean
                if (prev < 0.0 && curr >= 0.0) {
                    val ts = o.timestampsMs[i]
                    if (firstCrossTs == Long.MIN_VALUE) firstCrossTs = ts
                    lastCrossTs = ts
                    crossCount++
                }
            }
            if (crossCount >= 2) {
                val spanSec = (lastCrossTs - firstCrossTs) / 1000.0
                if (spanSec >= 1.0) {
                    return ((crossCount - 1) / spanSec).coerceIn(0.3, 5.0)
                }
            }
        }
        // Fallback when rotation vector absent: count mean crossings directly without heap list allocations
        if (gyroSamples.size >= 8 && (rawGyroRms ?: 0.0) >= 0.15) {
            var sum = 0.0
            for (s in gyroSamples) sum += s.yawResidualRadPerSec
            val mean = sum / gyroSamples.size
            var firstCrossTs = Long.MIN_VALUE
            var lastCrossTs = Long.MIN_VALUE
            var crossCount = 0
            var prevDiff = Double.NaN
            for (s in gyroSamples) {
                val currDiff = s.yawResidualRadPerSec - mean
                if (!prevDiff.isNaN() && prevDiff < 0.0 && currDiff >= 0.0) {
                    if (firstCrossTs == Long.MIN_VALUE) firstCrossTs = s.timestampMs
                    lastCrossTs = s.timestampMs
                    crossCount++
                }
                prevDiff = currDiff
            }
            if (crossCount >= 2) {
                val spanSec = (lastCrossTs - firstCrossTs) / 1000.0
                if (spanSec >= 1.0) {
                    return ((crossCount - 1) / spanSec).coerceIn(0.3, 5.0)
                }
            }
        }
        return null
    }

    private fun detectGlanceShift(nowMs: Long) {
        if (gravitySamples.size < 6) return
        if (lastGlanceEventMs > Long.MIN_VALUE && (nowMs - lastGlanceEventMs) < GLANCE_DEBOUNCE_MS) return

        var recX = 0.0
        var recY = 0.0
        var recZ = 0.0
        var recN = 0
        var prevX = 0.0
        var prevY = 0.0
        var prevZ = 0.0
        var prevN = 0

        for (s in gravitySamples) {
            val ageMs = nowMs - s.timestampMs
            if (ageMs in 0L..700L) {
                recX += s.gx
                recY += s.gy
                recZ += s.gz
                recN++
            } else if (ageMs in 1_000L..2_400L) {
                prevX += s.gx
                prevY += s.gy
                prevZ += s.gz
                prevN++
            }
        }
        if (recN < 2 || prevN < 2) return

        val rNorm = sqrt(recX * recX + recY * recY + recZ * recZ).coerceAtLeast(1e-6)
        val pNorm = sqrt(prevX * prevX + prevY * prevY + prevZ * prevZ).coerceAtLeast(1e-6)
        val dot = ((recX / rNorm) * (prevX / pNorm) +
            (recY / rNorm) * (prevY / pNorm) +
            (recZ / rNorm) * (prevZ / pNorm)).coerceIn(-1.0, 1.0)
        val deltaDeg = Math.toDegrees(acos(dot))
        if (deltaDeg >= GLANCE_SHIFT_THRESHOLD_DEG) {
            lastGlanceEventMs = nowMs
            glanceTimestampsMs.addLast(nowMs)
        }
    }

    private fun noteTimestamp(timestampMs: Long) {
        if (firstSampleTimestampMs == Long.MIN_VALUE || timestampMs < firstSampleTimestampMs) {
            firstSampleTimestampMs = timestampMs
        }
        if (timestampMs > lastEventTimestampMs) {
            lastEventTimestampMs = timestampMs
        }
    }

    private fun pruneWindows(nowMs: Long) {
        val cutoff = nowMs - windowMs
        while (gravitySamples.isNotEmpty() && gravitySamples.peekFirst()!!.timestampMs < cutoff) {
            gravitySamples.removeFirst()
        }
        while (gyroSamples.isNotEmpty() && gyroSamples.peekFirst()!!.timestampMs < cutoff) {
            gyroSamples.removeFirst()
        }
        while (baroSamples.isNotEmpty() && baroSamples.peekFirst()!!.timestampMs < cutoff) {
            baroSamples.removeFirst()
        }
        while (stepTimestampsMs.isNotEmpty() && stepTimestampsMs.peekFirst()!! < cutoff) {
            stepTimestampsMs.removeFirst()
        }
        val glanceCutoff = nowMs - GLANCE_MEMORY_MS
        while (glanceTimestampsMs.isNotEmpty() && glanceTimestampsMs.peekFirst()!! < glanceCutoff) {
            glanceTimestampsMs.removeFirst()
        }
    }
}
