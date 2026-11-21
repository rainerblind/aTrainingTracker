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
import kotlin.math.cos
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
        private const val GLANCE_MAX_VIEWING_INCLINATION_DEG = 60.0
        private const val GLANCE_MIN_INCLINATION_DROP_DEG = 10.0
        private const val GLANCE_DEBOUNCE_MS = 700L
        private const val GLANCE_MEMORY_MS = 15_000L
        private const val GLANCE_ACTIVE_HOLD_MS = 1_200L
        private const val STATIONARY_SPEED_MPS = 0.15
        private const val QUIET_IMU_STATIONARY_SPEED_MPS = 1.8
        private const val STAGNANT_POSITION_MIN_SPAN_MS = 4_000L
        private const val STAGNANT_POSITION_MAX_SPREAD_M = 2.0
        private const val EARTH_RADIUS_METERS = 6_371_000.0
        private const val GLANCE_PREV_MIN_AGE_MS = 1_000L
        private const val GLANCE_PREV_MAX_AGE_MS = 4_500L
        private const val GLANCE_PREV_MIN_SPAN_MS = 1_200L
        private const val GLANCE_REC_OSCILLATING_SD_DEG = 8.0
        private const val GLANCE_REC_OSCILLATING_DELTA_RATIO = 2.6
        private const val GLANCE_PREV_CYCLIC_SD_DEG = 7.0
        private const val GLANCE_PREV_ENVELOPE_RATIO = 1.25
        private const val ACCEL_IMPACT_HPF_HZ = 2.1
        private const val ACCEL_IMPACT_LPF_HZ = 4.2
        private const val ACCEL_IMPACT_MIN_RMS_MPS2 = 1.2
        private const val ACCEL_IMPACT_MIN_SPAN_MS = 2_000L
        private const val ACCEL_IMPACT_MIN_SAMPLES = 100
        private const val ACCEL_ACF_MIN_LAG = 13
        private const val ACCEL_ACF_MAX_LAG = 22
        private const val ACCEL_ACF_MIN_OVERLAP = 50
        private const val ACCEL_ACF_MIN_CORRELATION = 0.42
        private const val CONFIRMED_NO_FOOTSTRIKE_WALK_SUPPRESS_SPEED_MPS = 1.6
        private const val CONFIRMED_NO_FOOTSTRIKE_BIKE_PRIOR_SPEED_MPS = 1.8
        private const val RUN_CYCLING_MASK_WHEEL_MAX_MPS = 0.5
        private const val RUN_CYCLING_MASK_SPEED_MPS = 5.5
        private const val ACTIVE_FOOTSTRIKE_MIN_HZ = 2.25
        private const val EASY_RUN_MIN_SPEED_MPS = 1.65
        private const val EASY_RUN_FALLBACK_SPEED_MPS = 1.75
        private const val EASY_RUN_IMPACT_RMS_MPS2 = 1.8
        private const val EASY_RUN_MIN_STEP_HZ = 2.15
        private const val INTRA_RUN_HYSTERESIS_MIN_MS = 6_000L
        private const val WAIST_TO_HANDHELD_HYSTERESIS_MIN_MS = 10_000L
        private const val WALK_MIN_SPEED_MPS = 0.35
        private const val WALK_MAX_SPEED_MPS = 2.50
        private const val SCORE_MARGIN = 0.85
    }

    private data class GravitySample(val timestampMs: Long, val gx: Double, val gy: Double, val gz: Double)
    private data class GyroSample(val timestampMs: Long, val yawResidualRadPerSec: Double, val rawMagRadPerSec: Double)
    private data class BaroSample(val timestampMs: Long, val altitudeM: Double)
    private data class PositionSample(val timestampMs: Long, val lat: Double, val lon: Double)
    private data class AccelFootstrikeEval(
        val footstrikeFreqHz: Double?,
        val confirmedNoFootstrikes: Boolean,
        val impactRmsMps2: Double? = null
    )

    private val gravitySamples = ArrayDeque<GravitySample>()
    private val gyroSamples = ArrayDeque<GyroSample>()
    private val baroSamples = ArrayDeque<BaroSample>()
    private val positionSamples = ArrayDeque<PositionSample>()
    private val stepTimestampsMs = ArrayDeque<Long>()
    private val glanceTimestampsMs = ArrayDeque<Long>()

    private val accResampler = UniformGridResampler(ImuCadenceDspConstants.GRID_STEP_MS)
    private val impactBandPass = BiquadBandPass(ImuCadenceDspConstants.FS_HZ, ACCEL_IMPACT_HPF_HZ, ACCEL_IMPACT_LPF_HZ)
    private val impactRing = DoubleRingBuffer(ImuCadenceDspConstants.WINDOW_SAMPLES)
    private var firstAccelTimestampMs: Long = Long.MIN_VALUE
    private var lastAccelTimestampMs: Long = Long.MIN_VALUE
    private var stepDetectorRegistered: Boolean = false

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

    /**
     * Records whether hardware `TYPE_STEP_DETECTOR` is actively registered (distinguishing
     * `stepDetectorActiveAndZero` from permission-denied/absent `stepDetectorUnavailable`; REQ-FIL-034.4).
     */
    @Synchronized
    fun setStepDetectorRegistered(registered: Boolean) {
        stepDetectorRegistered = registered
    }

    @Synchronized
    fun reset() {
        gravitySamples.clear()
        gyroSamples.clear()
        baroSamples.clear()
        positionSamples.clear()
        stepTimestampsMs.clear()
        glanceTimestampsMs.clear()
        accResampler.reset()
        impactBandPass.reset()
        impactRing.clear()
        firstAccelTimestampMs = Long.MIN_VALUE
        lastAccelTimestampMs = Long.MIN_VALUE
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

    /**
     * Ingests a signed 3-axis accelerometer sample (m/s²) onto a uniform 50-Hz time-based grid
     * for permission-free `2.4–3.6 Hz` running footstrike periodicity detection (REQ-FIL-034.4).
     */
    @Synchronized
    fun ingestAccelerometer(axMps2: Double, ayMps2: Double, azMps2: Double, timestampMs: Long) {
        if (!axMps2.isFinite() || !ayMps2.isFinite() || !azMps2.isFinite() || timestampMs < 0L) return
        val mag = sqrt(axMps2 * axMps2 + ayMps2 * ayMps2 + azMps2 * azMps2)
        if (!mag.isFinite()) return

        if (lastAccelTimestampMs > Long.MIN_VALUE && (timestampMs - lastAccelTimestampMs) > STALENESS_MS) {
            accResampler.reset()
            impactBandPass.reset()
            impactRing.clear()
            firstAccelTimestampMs = timestampMs
        }
        if (firstAccelTimestampMs == Long.MIN_VALUE || timestampMs < firstAccelTimestampMs) {
            firstAccelTimestampMs = timestampMs
        }
        lastAccelTimestampMs = max(lastAccelTimestampMs, timestampMs)
        noteTimestamp(timestampMs)
        accResampler.push(timestampMs, axMps2, ayMps2, azMps2, mag) { _, _, _, _, iMag ->
            impactRing.add(impactBandPass.step(iMag))
        }
        pruneWindows(timestampMs)
    }

    @Synchronized
    fun ingestStepEvent(timestampMs: Long) {
        noteTimestamp(timestampMs)
        stepTimestampsMs.addLast(timestampMs)
        pruneWindows(timestampMs)
    }

    @Synchronized
    @JvmOverloads
    fun ingestGnssSpeed(
        speedMps: Double,
        bearingDeg: Double? = null,
        accuracyMeters: Double? = null,
        timestampMs: Long,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        noteTimestamp(timestampMs)
        if (speedMps.isFinite() && speedMps >= 0.0) {
            lastGnssSpeedMps = speedMps
            lastGnssTimestampMs = timestampMs
        }
        if (latitude != null && longitude != null &&
            latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            !(abs(latitude) < 1e-6 && abs(longitude) < 1e-6)
        ) {
            positionSamples.addLast(PositionSample(timestampMs, latitude, longitude))
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

        if (committedMode.isRunningMode() && rawCandidate == PhoneCarriageMode.RUN_HANDHELD && isGlanceActive(nowMs)) {
            committedMode = rawCandidate
            candidateMode = rawCandidate
            candidateSinceMs = Long.MIN_VALUE
            return committedMode
        }

        if (candidateMode != rawCandidate || candidateSinceMs == Long.MIN_VALUE) {
            candidateMode = rawCandidate
            candidateSinceMs = nowMs
            return committedMode
        }

        val requiredDwellMs = requiredDwellMs(committedMode, rawCandidate)
        if (nowMs - candidateSinceMs >= requiredDwellMs) {
            committedMode = rawCandidate
            candidateSinceMs = Long.MIN_VALUE
        }
        return committedMode
    }

    /**
     * Asymmetric dwell hysteresis (REQ-FIL-035.3, REQ-FIL-039.3):
     * - `RUN_* -> WALKING_TRANSITION`: `mountHysteresisMs` (4.0 s default) to reject brief canopy GPS dips.
     * - Non-glance `RUN_WAIST_HIP -> RUN_HANDHELD`: `maxOf(mountHysteresisMs, 10_000L)` to suppress belt chatter.
     * - Other non-glance intra-run `RUN_* -> RUN_*`: `maxOf(mountHysteresisMs, 6_000L)`.
     * - `WALKING_TRANSITION -> RUN_*`, `RUN_* -> STATIONARY`, `STATIONARY <-> *`, `BIKE_* <-> WALKING_TRANSITION`:
     *   fast `dismountHysteresisMs` (2.0 s default).
     */
    private fun requiredDwellMs(from: PhoneCarriageMode, to: PhoneCarriageMode): Long {
        return when {
            from.isRunningMode() && to == PhoneCarriageMode.WALKING_TRANSITION -> mountHysteresisMs
            from == PhoneCarriageMode.RUN_WAIST_HIP && to == PhoneCarriageMode.RUN_HANDHELD -> {
                maxOf(mountHysteresisMs, WAIST_TO_HANDHELD_HYSTERESIS_MIN_MS)
            }
            from.isRunningMode() && to.isRunningMode() -> maxOf(mountHysteresisMs, INTRA_RUN_HYSTERESIS_MIN_MS)
            from.isWalkingOrStationary() || to.isWalkingOrStationary() -> dismountHysteresisMs
            else -> mountHysteresisMs
        }
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
        val accelEval = evaluateAccelFootstrike(nowMs)
        val effectiveRunStepFreqHz = stepFreqHz ?: accelEval.footstrikeFreqHz

        val hasOrientation = gravitySamples.size >= 3
        val orientationStats = if (hasOrientation) computeOrientationStats() else null
        val hasGyro = gyroSamples.size >= 3
        val yawResidualRms = if (hasGyro) computeGyroRms { it.yawResidualRadPerSec } else null
        val rawGyroRms = if (hasGyro) computeGyroRms { it.rawMagRadPerSec } else null
        val baroStepRms = computeOneHzBaroStepRms()
        val glanceCount = glanceTimestampsMs.size
        val dominantFreqHz = estimateDominantOscillationHz(orientationStats, rawGyroRms)

        val quietImu = (!hasOrientation || (orientationStats!!.swingSdDeg < 2.5)) &&
            (!hasGyro || (rawGyroRms!! < 0.14)) &&
            effectiveRunStepFreqHz == null
        val hadPriorSpeedOrMode = committedMode != PhoneCarriageMode.UNKNOWN ||
            lastGnssTimestampMs > Long.MIN_VALUE ||
            lastWheelTimestampMs > Long.MIN_VALUE

        // Without any active speed/step reference: transition indoor GNSS outage + quiet IMU to STATIONARY
        // once a speed source/mode existed, while keeping pure IMU-only unit checks at neutral UNKNOWN (REQ-FIL-034.1).
        if (effectiveSpeed == null && effectiveRunStepFreqHz == null) {
            return if (hadPriorSpeedOrMode && (hasOrientation || hasGyro) && quietImu) {
                PhoneCarriageMode.STATIONARY
            } else {
                PhoneCarriageMode.UNKNOWN
            }
        }

        if (effectiveSpeed == null && !hasOrientation && !hasGyro && baroStepRms == null && effectiveRunStepFreqHz == null) {
            return PhoneCarriageMode.UNKNOWN
        }

        val newerWalkEvidenceThanWheel = lastWheelTimestampMs == Long.MIN_VALUE ||
            (lastGnssTimestampMs > lastWheelTimestampMs + 1_500L && gnssSpeed != null && gnssSpeed <= WALK_MAX_SPEED_MPS)
        val wheelActiveMoving = wheelSpeed != null && wheelSpeed > 0.5 && !newerWalkEvidenceThanWheel

        // 1. Stationary detection (bakery counter, indoor outage, stagnant cached fix, traffic light stop; REQ-FIL-034.1)
        val lowSpeed = effectiveSpeed != null && effectiveSpeed <= STATIONARY_SPEED_MPS
        if (lowSpeed && (quietImu || (effectiveSpeed <= 0.10 && (wheelSpeed == null || wheelSpeed <= 0.10)))) {
            return PhoneCarriageMode.STATIONARY
        }
        if (!wheelActiveMoving && (hasOrientation || hasGyro) && quietImu &&
            ((effectiveSpeed != null && effectiveSpeed <= QUIET_IMU_STATIONARY_SPEED_MPS) || isPositionStagnant(nowMs))
        ) {
            return PhoneCarriageMode.STATIONARY
        }

        // Distinguish stepDetectorActiveAndZero / confirmed zero-footstrike accel from stepDetectorUnavailable
        // without misclassifying SensorHub step-batching gaps during high-impact trail running (REQ-FIL-034.4, REQ-FIL-039.1)
        val highRunningImpact = accelEval.impactRmsMps2 != null && accelEval.impactRmsMps2 >= EASY_RUN_IMPACT_RMS_MPS2
        val stepDetectorActiveAndZero = stepDetectorRegistered && effectiveRunStepFreqHz == null && !highRunningImpact
        val confirmedNoFootstrikes = stepDetectorActiveAndZero || accelEval.confirmedNoFootstrikes

        // Cycling guardrails: mask BIKE_* during Sport = RUN (< 5.5 m/s, no wheel) OR whenever active running
        // footstrikes (>= 2.25 Hz) hold without a moving wheel sensor (REQ-FIL-035.1, REQ-FIL-039.1)
        val noMovingWheel = !wheelActiveMoving && (wheelSpeed == null || wheelSpeed <= RUN_CYCLING_MASK_WHEEL_MAX_MPS)
        val suppressCyclingForRun = sportHint == SportContextHint.RUNNING &&
            noMovingWheel &&
            (effectiveSpeed == null || effectiveSpeed < RUN_CYCLING_MASK_SPEED_MPS)
        val suppressCyclingForFootstrikes = noMovingWheel &&
            effectiveRunStepFreqHz != null &&
            effectiveRunStepFreqHz >= ACTIVE_FOOTSTRIKE_MIN_HZ
        val suppressCycling = suppressCyclingForRun || suppressCyclingForFootstrikes

        // 2. Walking / Transition dismount detection (independent of cycling sportHint, with easy-pace run guard; REQ-FIL-035.2)
        val walkingSpeedBand = effectiveSpeed != null && effectiveSpeed in WALK_MIN_SPEED_MPS..WALK_MAX_SPEED_MPS
        val walkingStepBand = stepFreqHz != null && stepFreqHz in 1.15..2.30
        val easyRunSuppressWalk = sportHint == SportContextHint.RUNNING &&
            effectiveSpeed != null &&
            effectiveSpeed >= EASY_RUN_MIN_SPEED_MPS &&
            (
                (accelEval.impactRmsMps2 != null && accelEval.impactRmsMps2 >= EASY_RUN_IMPACT_RMS_MPS2) ||
                (effectiveRunStepFreqHz != null && effectiveRunStepFreqHz >= EASY_RUN_MIN_STEP_HZ) ||
                (effectiveSpeed >= EASY_RUN_FALLBACK_SPEED_MPS &&
                    (accelEval.impactRmsMps2 == null || accelEval.impactRmsMps2 >= ACCEL_IMPACT_MIN_RMS_MPS2))
            )
        if (!wheelActiveMoving) {
            if (!easyRunSuppressWalk && walkingStepBand && (effectiveSpeed == null || effectiveSpeed <= WALK_MAX_SPEED_MPS)) {
                return PhoneCarriageMode.WALKING_TRANSITION
            }
            if (!easyRunSuppressWalk && walkingSpeedBand) {
                val suppressWalkForNoFootstrikeClimb = !suppressCyclingForRun &&
                    confirmedNoFootstrikes &&
                    effectiveSpeed >= CONFIRMED_NO_FOOTSTRIKE_WALK_SUPPRESS_SPEED_MPS
                val gyroOrSwingPresent = yawResidualRms == null || yawResidualRms >= 0.08 ||
                    (orientationStats != null && orientationStats.swingSdDeg >= 4.0)
                val isPendulumWalking = dominantFreqHz == null || dominantFreqHz in 0.60..1.12
                val isStepHarmonicWalking = dominantFreqHz != null && dominantFreqHz in 1.12..2.35 &&
                    (effectiveSpeed <= 2.15 || sportHint != SportContextHint.RUNNING)
                if (effectiveRunStepFreqHz == null &&
                    !suppressWalkForNoFootstrikeClimb &&
                    gyroOrSwingPresent &&
                    (isPendulumWalking || isStepHarmonicWalking)
                ) {
                    return PhoneCarriageMode.WALKING_TRANSITION
                }
            }
        }

        // 3. Score cycling vs running carriage regimes (kinematic footstrikes prioritized over speed cutoffs; REQ-FIL-034.4, REQ-FIL-035.1)

        var cyclingPrior = 0.0
        var runningPrior = 0.0
        when {
            wheelActiveMoving -> cyclingPrior += 6.0
            effectiveRunStepFreqHz != null && effectiveRunStepFreqHz >= ACTIVE_FOOTSTRIKE_MIN_HZ -> runningPrior += 5.0
            else -> {
                if (sportHint == SportContextHint.CYCLING) cyclingPrior += 2.5
                if (sportHint == SportContextHint.RUNNING) runningPrior += 2.5
                if (effectiveSpeed != null) {
                    when {
                        effectiveSpeed >= 5.5 -> cyclingPrior += 3.0
                        confirmedNoFootstrikes && effectiveSpeed >= CONFIRMED_NO_FOOTSTRIKE_BIKE_PRIOR_SPEED_MPS -> {
                            cyclingPrior += 1.5
                        }
                        !confirmedNoFootstrikes && effectiveSpeed in 2.2..4.6 && wheelSpeed == null -> {
                            runningPrior += 0.6
                        }
                    }
                }
            }
        }

        // Handlebar vs other fallback if zero IMU is available
        if (!hasOrientation && !hasGyro && baroStepRms == null) {
            return if (suppressCycling || runningPrior > cyclingPrior) {
                PhoneCarriageMode.RUN_WAIST_HIP
            } else {
                PhoneCarriageMode.BIKE_HANDLEBAR
            }
        }

        val sBikeHandlebar = if (suppressCycling) {
            Double.NEGATIVE_INFINITY
        } else {
            cyclingPrior + scoreBikeHandlebar(orientationStats, yawResidualRms, baroStepRms, effectiveSpeed)
        }
        val sBikeJersey = if (suppressCycling) {
            Double.NEGATIVE_INFINITY
        } else {
            cyclingPrior + scoreBikeJerseyBack(orientationStats, yawResidualRms, baroStepRms)
        }
        val sBikeFrontPocket = if (suppressCycling) {
            Double.NEGATIVE_INFINITY
        } else {
            cyclingPrior + scoreBikeFrontPocket(orientationStats, yawResidualRms, baroStepRms, dominantFreqHz)
        }
        val sRunWaist = runningPrior + scoreRunWaistHip(
            orientationStats, yawResidualRms, baroStepRms, effectiveRunStepFreqHz, glanceCount
        )
        val sRunBicep = runningPrior + scoreRunBicepArm(
            orientationStats, yawResidualRms, baroStepRms, dominantFreqHz, effectiveRunStepFreqHz, glanceCount
        )
        val sRunHandheld = runningPrior + scoreRunHandheld(
            orientationStats, yawResidualRms, baroStepRms, glanceCount
        )

        var bestMode = if (suppressCycling) PhoneCarriageMode.RUN_WAIST_HIP else PhoneCarriageMode.BIKE_HANDLEBAR
        var bestScore = if (suppressCycling) sRunWaist else sBikeHandlebar
        if (!suppressCycling) {
            if (sBikeJersey > bestScore) { bestScore = sBikeJersey; bestMode = PhoneCarriageMode.BIKE_JERSEY_BACK }
            if (sBikeFrontPocket > bestScore) { bestScore = sBikeFrontPocket; bestMode = PhoneCarriageMode.BIKE_FRONT_POCKET }
            if (sRunWaist > bestScore) { bestScore = sRunWaist; bestMode = PhoneCarriageMode.RUN_WAIST_HIP }
        }
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
            score += when {
                o.swingSdDeg < 10.0 -> 2.6
                o.swingSdDeg < 16.0 -> 2.0
                o.swingSdDeg < 18.0 -> 0.8
                o.swingSdDeg < 22.0 -> -0.5
                else -> -2.0
            }
        }
        if (yawResidualRms != null) {
            score += when {
                yawResidualRms < 0.06 -> 0.8
                yawResidualRms <= 0.48 -> 2.2
                yawResidualRms <= 0.85 -> 1.8
                yawResidualRms <= 1.15 -> 0.4
                else -> -1.2
            }
        }
        if (baroStepRms != null) {
            score += when {
                baroStepRms < 0.45 -> 1.2
                baroStepRms < 0.60 -> 0.5
                else -> -0.6
            }
        }
        if (stepFreqHz != null && stepFreqHz >= EASY_RUN_MIN_STEP_HZ) {
            score += 0.8
        }
        score += if (glanceCount == 0) 1.4 else -1.8
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
                o.swingSdDeg >= 15.5 -> 2.6
                o.swingSdDeg >= 11.5 -> 0.8
                else -> -0.8
            }
        }
        if (yawResidualRms != null) {
            score += when {
                yawResidualRms < 0.16 -> -1.0
                yawResidualRms <= 0.48 -> 2.0
                yawResidualRms <= 0.60 -> 0.3
                else -> -0.8
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
        if (glanceCount == 0) score += 0.8 else score -= 2.2
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
            score += when {
                o.swingSdDeg >= 17.5 -> 1.8
                o.swingSdDeg >= 13.5 -> 0.4
                else -> -1.6
            }
        }
        if (yawResidualRms != null) {
            score += when {
                yawResidualRms >= 0.72 -> 2.4
                yawResidualRms >= 0.55 -> 0.6
                else -> -1.5
            }
        }
        if (baroStepRms != null) {
            score += when {
                baroStepRms >= 0.60 -> 2.4
                baroStepRms >= 0.45 -> 0.4
                else -> -0.4
            }
        }
        score += if (glanceCount >= 1) 3.8 else -1.8
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

    private fun evaluateAccelFootstrike(nowMs: Long): AccelFootstrikeEval {
        if (lastAccelTimestampMs == Long.MIN_VALUE || firstAccelTimestampMs == Long.MIN_VALUE) {
            return AccelFootstrikeEval(null, false)
        }
        if ((nowMs - lastAccelTimestampMs) > STALENESS_MS) {
            return AccelFootstrikeEval(null, false)
        }
        if ((lastAccelTimestampMs - firstAccelTimestampMs) < ACCEL_IMPACT_MIN_SPAN_MS ||
            impactRing.size < ACCEL_IMPACT_MIN_SAMPLES
        ) {
            return AccelFootstrikeEval(null, false)
        }
        val impactRms = impactRing.recentRms(impactRing.size)
        val finiteImpactRms = impactRms.takeIf { it.isFinite() }
        if (finiteImpactRms == null || finiteImpactRms < ACCEL_IMPACT_MIN_RMS_MPS2) {
            return AccelFootstrikeEval(null, true, finiteImpactRms)
        }
        val lagResult = findFundamentalLagResult(
            minLag = ACCEL_ACF_MIN_LAG,
            maxLag = ACCEL_ACF_MAX_LAG,
            minOverlap = ACCEL_ACF_MIN_OVERLAP,
            availableSamples = impactRing.size,
            minPeakCorrelation = ACCEL_ACF_MIN_CORRELATION
        ) { lag -> impactRing.normalizedCorrelation(lag) }
        if (lagResult != null && lagResult.peakCorrelation >= ACCEL_ACF_MIN_CORRELATION && lagResult.lagSamples > 0.0) {
            val freqHz = (ImuCadenceDspConstants.FS_HZ / lagResult.lagSamples).coerceIn(2.25, 4.0)
            return AccelFootstrikeEval(freqHz, false, finiteImpactRms)
        }
        // High running-grade vertical impacts (>= 1.8 m/s^2) during temporary uneven-trail ACF dips
        // must not confirm "no footstrikes" (REQ-FIL-039.1).
        return AccelFootstrikeEval(null, finiteImpactRms < EASY_RUN_IMPACT_RMS_MPS2, finiteImpactRms)
    }

    private fun isPositionStagnant(nowMs: Long): Boolean {
        if (positionSamples.size < 3) return false
        val first = positionSamples.peekFirst() ?: return false
        val last = positionSamples.peekLast() ?: return false
        if ((nowMs - last.timestampMs) > STALENESS_MS) return false
        if ((last.timestampMs - first.timestampMs) < STAGNANT_POSITION_MIN_SPAN_MS) return false
        val cosLat = cos(Math.toRadians(first.lat))
        for (s in positionSamples) {
            val dN = Math.toRadians(s.lat - first.lat) * EARTH_RADIUS_METERS
            val dE = Math.toRadians(s.lon - first.lon) * EARTH_RADIUS_METERS * cosLat
            if (hypot(dE, dN) >= STAGNANT_POSITION_MAX_SPREAD_M) return false
        }
        return true
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
        var prevMinTs = Long.MAX_VALUE
        var prevMaxTs = Long.MIN_VALUE

        for (s in gravitySamples) {
            val ageMs = nowMs - s.timestampMs
            if (ageMs in 0L..700L) {
                recX += s.gx
                recY += s.gy
                recZ += s.gz
                recN++
            } else if (ageMs in GLANCE_PREV_MIN_AGE_MS..GLANCE_PREV_MAX_AGE_MS) {
                prevX += s.gx
                prevY += s.gy
                prevZ += s.gz
                prevN++
                if (s.timestampMs < prevMinTs) prevMinTs = s.timestampMs
                if (s.timestampMs > prevMaxTs) prevMaxTs = s.timestampMs
            }
        }
        if (recN < 2 || prevN < 4 || (prevMaxTs - prevMinTs) < GLANCE_PREV_MIN_SPAN_MS) return

        val rNorm = sqrt(recX * recX + recY * recY + recZ * recZ).coerceAtLeast(1e-6)
        val pNorm = sqrt(prevX * prevX + prevY * prevY + prevZ * prevZ).coerceAtLeast(1e-6)
        val rmx = recX / rNorm
        val rmy = recY / rNorm
        val rmz = recZ / rNorm
        val pmx = prevX / pNorm
        val pmy = prevY / pNorm
        val pmz = prevZ / pNorm
        val dot = (rmx * pmx + rmy * pmy + rmz * pmz).coerceIn(-1.0, 1.0)
        val deltaDeg = Math.toDegrees(acos(dot))
        if (deltaDeg < GLANCE_SHIFT_THRESHOLD_DEG) return

        // Genuine screen-viewing wrist glances tilt toward a readable angle (<= 60 deg from horizontal)
        // with >= 10 deg drop in inclination; reject vertical-plane belt rolls/slips and stow transitions (REQ-FIL-039.2).
        val recInclinationDeg = Math.toDegrees(acos(abs(rmz).coerceIn(0.0, 1.0)))
        val prevInclinationDeg = Math.toDegrees(acos(abs(pmz).coerceIn(0.0, 1.0)))
        if (recInclinationDeg > GLANCE_MAX_VIEWING_INCLINATION_DEG ||
            (prevInclinationDeg - recInclinationDeg) < GLANCE_MIN_INCLINATION_DROP_DEG
        ) {
            return
        }

        // Reject periodic/cyclic thigh oscillation (0.8-1.8 Hz) while preserving genuine wrist glance holds (REQ-FIL-034.2)
        var recSumSq = 0.0
        var prevSumSq = 0.0
        var prevPeakDeg = 0.0
        for (s in gravitySamples) {
            val ageMs = nowMs - s.timestampMs
            if (ageMs in 0L..700L) {
                val rDot = (s.gx * rmx + s.gy * rmy + s.gz * rmz).coerceIn(-1.0, 1.0)
                val ang = Math.toDegrees(acos(rDot))
                recSumSq += ang * ang
            } else if (ageMs in GLANCE_PREV_MIN_AGE_MS..GLANCE_PREV_MAX_AGE_MS) {
                val pDot = (s.gx * pmx + s.gy * pmy + s.gz * pmz).coerceIn(-1.0, 1.0)
                val ang = Math.toDegrees(acos(pDot))
                prevSumSq += ang * ang
                if (ang > prevPeakDeg) prevPeakDeg = ang
            }
        }
        val recSdDeg = sqrt(recSumSq / recN)
        val prevSdDeg = sqrt(prevSumSq / prevN)
        if (recSdDeg > GLANCE_REC_OSCILLATING_SD_DEG && deltaDeg < GLANCE_REC_OSCILLATING_DELTA_RATIO * recSdDeg) {
            return
        }
        if (prevSdDeg >= GLANCE_PREV_CYCLIC_SD_DEG && deltaDeg <= GLANCE_PREV_ENVELOPE_RATIO * prevPeakDeg) {
            return
        }

        lastGlanceEventMs = nowMs
        glanceTimestampsMs.addLast(nowMs)
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
        while (positionSamples.isNotEmpty() && positionSamples.peekFirst()!!.timestampMs < cutoff) {
            positionSamples.removeFirst()
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
