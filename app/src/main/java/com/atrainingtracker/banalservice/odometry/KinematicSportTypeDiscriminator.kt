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

import com.atrainingtracker.banalservice.BSportType
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Stop-filtered moving speed statistics and coasting duration captured over a workout session
 * (`ATT-8891`, `REQ-FIL-030.1`).
 *
 * Only moving samples (`v > 0.8 m/s`) contribute to [movingMeanMps], [p50Mps], [p90Mps], and
 * [movingSampleCount] so unpaused shopping/traffic stops do not dilute cycling speed profiles.
 */
data class MovingSpeedSummary(
    val movingMeanMps: Double,
    val p50Mps: Double,
    val p90Mps: Double,
    val movingSampleCount: Int,
    val coastingSeconds: Double = 0.0
)

/**
 * $O(1)$ constant-memory moving-speed histogram (`293` bins of `0.1 m/s` spanning `[0.8, 30.0] m/s`,
 * `~1.2 KB` fixed heap) producing exact online moving mean and midpoint-bounded quantiles
 * (`<= 0.05 m/s` error) (`ATT-8891`, `REQ-FIL-030.1`, `REQ-FIL-030.4`).
 *
 * Exposes two ingestion entrypoints:
 * - [recordThrottled]: enforces a `>= 1000 ms` minimum interval between accepted samples for
 *   high-rate `SpeedAndLocationDevice_ATTFused` callbacks so [movingSampleCount] counts genuine
 *   moving seconds.
 * - [recordOneHzSample]: unthrottled 1-Hz ingestion for `LiveWorkoutSession.addSample()` ticks.
 */
@Suppress("MagicNumber")
class MovingSpeedHistogram {
    companion object {
        const val MIN_MOVING_SPEED_MPS = 0.8
        const val MAX_HISTOGRAM_SPEED_MPS = 30.0
        const val BIN_WIDTH_MPS = 0.1
        const val BIN_COUNT = 293 // [0.8, 30.1) in 0.1 m/s steps
        const val MIN_SAMPLE_INTERVAL_MS = 1_000L
    }

    private val bins = IntArray(BIN_COUNT)
    private var movingSampleCount: Int = 0
    private var movingSpeedSumMps: Double = 0.0
    private var lastAcceptedTimestampMs: Long = Long.MIN_VALUE

    @Synchronized
    fun recordThrottled(speedMps: Double, timestampMs: Long): Boolean {
        if (!speedMps.isFinite() || speedMps <= MIN_MOVING_SPEED_MPS || timestampMs < 0L) {
            return false
        }
        if (lastAcceptedTimestampMs != Long.MIN_VALUE &&
            (timestampMs - lastAcceptedTimestampMs) < MIN_SAMPLE_INTERVAL_MS
        ) {
            return false
        }
        lastAcceptedTimestampMs = timestampMs
        return recordOneHzSample(speedMps)
    }

    @Synchronized
    fun recordOneHzSample(speedMps: Double): Boolean {
        if (!speedMps.isFinite() || speedMps <= MIN_MOVING_SPEED_MPS) {
            return false
        }
        val bin = floor((speedMps - MIN_MOVING_SPEED_MPS) / BIN_WIDTH_MPS)
            .toInt()
            .coerceIn(0, BIN_COUNT - 1)
        bins[bin]++
        movingSampleCount++
        movingSpeedSumMps += speedMps
        return true
    }

    @Synchronized
    fun sampleCount(): Int = movingSampleCount

    @Synchronized
    fun movingMeanMps(): Double =
        if (movingSampleCount > 0) movingSpeedSumMps / movingSampleCount else 0.0

    @Synchronized
    fun quantileMps(q: Double): Double {
        if (movingSampleCount <= 0) return 0.0
        val clampedQ = q.coerceIn(0.0, 1.0)
        val targetRank = ceil(clampedQ * movingSampleCount).toInt().coerceIn(1, movingSampleCount)
        var cumulative = 0
        for (bin in 0 until BIN_COUNT) {
            cumulative += bins[bin]
            if (cumulative >= targetRank) {
                return MIN_MOVING_SPEED_MPS + (bin + 0.5) * BIN_WIDTH_MPS
            }
        }
        return MIN_MOVING_SPEED_MPS + (BIN_COUNT - 0.5) * BIN_WIDTH_MPS
    }

    @Synchronized
    fun snapshot(coastingSeconds: Double = 0.0): MovingSpeedSummary? {
        if (movingSampleCount <= 0) return null
        val safeCoasting = if (coastingSeconds.isFinite() && coastingSeconds > 0.0) coastingSeconds else 0.0
        return MovingSpeedSummary(
            movingMeanMps = movingSpeedSumMps / movingSampleCount,
            p50Mps = quantileMps(0.50),
            p90Mps = quantileMps(0.90),
            movingSampleCount = movingSampleCount,
            coastingSeconds = safeCoasting
        )
    }

    @Synchronized
    fun reset() {
        bins.fill(0)
        movingSampleCount = 0
        movingSpeedSumMps = 0.0
        lastAcceptedTimestampMs = Long.MIN_VALUE
    }
}

/**
 * Pure-Kotlin live kinematic [BSportType] discriminator (`ATT-8891`, `REQ-FIL-030.1`), fusing four
 * orthogonal physical cues with zero Android UI/SQLite coupling:
 *
 * 1. **Coasting / Freewheeling**: sustained speed `v > 3.0 m/s` for `>= 3.0 s` with near-zero step
 *    rate (`< 0.5 Hz`) and low angular/footstrike oscillation (`< 0.25 rad/s` gyro RMS when gyro is
 *    active, low `2.4–3.6 Hz` impact RMS) -> strong [BSportType.BIKE] evidence.
 * 2. **Step-Length & Permission-Free Footstrike Impact Coherence**: `TYPE_STEP_DETECTOR` cadence
 *    (`2.0–3.65 Hz`) or, when unavailable (`ACTIVITY_RECOGNITION` denied, absent hardware, or OEM
 *    screen-off throttling), permission-free `50-Hz` `TYPE_ACCELEROMETER` `2.4–3.6 Hz` vertical/magnitude
 *    impact periodicity via [ImuCadenceDsp] (preallocated buffers evaluated at `<= 1 Hz` with
 *    normalized prominence `>= 0.45`) with physiological step length `L_step = v / f_step in [0.60, 1.82] m`
 *    -> strong [BSportType.RUN] evidence; `f_step == 0` or apparent cycle distance `> 3.5 m` at
 *    `v > 5.0 m/s` -> [BSportType.BIKE] evidence.
 * 3. **Carriage Mode Coupling**: `BIKE_*` modes vote [BSportType.BIKE]; `RUN_*` modes vote
 *    [BSportType.RUN] (guarded against overriding footstrikes or forcing `RUN` when 50-Hz `ACC`
 *    shows zero footstrike periodicity); `STATIONARY`/`WALKING_TRANSITION`/`UNKNOWN` stay neutral.
 * 4. **Stop-Filtered Moving Speed Distribution**: maintained via [MovingSpeedHistogram] with
 *    kinematic cues taking precedence over hard speed cutoffs (`5.6 m/s` runners -> `RUN`,
 *    `2.0 m/s` uphill MTB -> `BIKE` / no false `RUN` latch).
 *
 * Uses bounded signed evidence with hysteresis to suppress single-frame flicker while allowing
 * clean mid-session recovery (`RUN <-> BIKE`), and degrades deterministically when barometer,
 * gyroscope, rotation vector, or `ACTIVITY_RECOGNITION` permission are unavailable.
 */
@Suppress("MagicNumber", "ComplexMethod", "ReturnCount")
class KinematicSportTypeDiscriminator @JvmOverloads constructor(
    sportSpeedPriors: SportSpeedPriors = SportSpeedPriors.DEFAULT
) {
    companion object {
        private const val FS_HZ = ImuCadenceDspConstants.FS_HZ
        private const val GRID_STEP_MS = ImuCadenceDspConstants.GRID_STEP_MS
        private const val WINDOW_SAMPLES = ImuCadenceDspConstants.WINDOW_SAMPLES // 5.0 s at 50 Hz
        private const val MIN_ACF_SAMPLES = 150 // 3.0 s at 50 Hz
        private const val IMPACT_HPF_HZ = 2.0
        private const val IMPACT_LPF_HZ = 4.0
        private const val ACF_MIN_LAG = 13 // ~3.85 Hz
        private const val ACF_MAX_LAG = 25 // 2.00 Hz
        private const val MIN_IMPACT_ACF_PROMINENCE = 0.45
        private const val MIN_IMPACT_RMS_MPS2 = 1.2

        private const val HW_STEP_MIN_HZ = 2.0
        private const val HW_STEP_MAX_HZ = 3.65
        private const val ACC_IMPACT_MIN_HZ = 2.35
        private const val ACC_IMPACT_MAX_HZ = 3.65
        private const val MIN_STEP_LENGTH_M = 0.60
        private const val MAX_STEP_LENGTH_M = 1.82 // Allows 0.02 Hz ACF interpolation jitter at 5.6 m/s / 3.2 Hz
        private const val BIKE_CYCLE_DISTANCE_M = 3.5
        private const val HIGH_BIKE_SPEED_MPS = 5.0
        private const val MIN_BIKE_CARRIAGE_VOTE_SPEED_MPS = 1.8

        private const val COASTING_MIN_SPEED_MPS = 3.0
        private const val COASTING_MIN_DURATION_MS = 3_000L
        private const val COASTING_MAX_STEP_HZ = 0.5
        private const val COASTING_MAX_GYRO_RMS_RADPS = 0.25
        private const val COASTING_MAX_IMPACT_RMS_MPS2 = 1.35

        private const val EVAL_INTERVAL_MS = 1_000L
        private const val SPEED_STALE_MS = 6_000L
        private const val IMU_STALE_MS = 2_500L
        private const val STEP_WINDOW_MS = 5_000L
        private const val STEP_STALE_MS = 2_200L
        private const val GYRO_RMS_WINDOW_SAMPLES = 75 // 1.5 s at 50 Hz

        private const val SCORE_CLAMP = 18.0
        private const val ENTER_SCORE_THRESHOLD = 6.0
    }

    private val movingHistogram = MovingSpeedHistogram()

    // 50-Hz accelerometer impact bandpass & preallocated zero-alloc ACF scratch buffer
    private val accResampler = UniformGridResampler(GRID_STEP_MS)
    private val impactBandPass = BiquadBandPass(FS_HZ, IMPACT_HPF_HZ, IMPACT_LPF_HZ)
    private val impactRing = DoubleRingBuffer(WINDOW_SAMPLES)
    private val acfScratch = DoubleArray(ACF_MAX_LAG - ACF_MIN_LAG + 3)

    // Short 1.5-s gyro RMS ring buffer for coasting detection (zero allocation)
    private val gyroMagSqRing = DoubleArray(GYRO_RMS_WINDOW_SAMPLES)
    private var gyroRingHead = 0
    private var gyroRingSize = 0
    private var gyroMagSqSum = 0.0

    // Hardware TYPE_STEP_DETECTOR timestamps in 5-s rolling window
    private val stepTimestampsMs = ArrayDeque<Long>()

    private var lastSpeedMps: Double = Double.NaN
    private var lastSpeedTimestampMs: Long = Long.MIN_VALUE
    private var lastAccTimestampMs: Long = Long.MIN_VALUE
    private var lastGyroTimestampMs: Long = Long.MIN_VALUE
    private var lastCarriageMode: PhoneCarriageMode = PhoneCarriageMode.UNKNOWN
    private var lastEvaluateTimestampMs: Long = Long.MIN_VALUE

    private var coastingStreakStartMs: Long = Long.MIN_VALUE
    private var coastingCreditedInStreak: Boolean = false
    private var totalCoastingSeconds: Double = 0.0

    // Signed evidence accumulator: positive -> BIKE, negative -> RUN
    private var sportSpeedPriors: SportSpeedPriors = sportSpeedPriors.clamped()
    private var evidenceScore: Double = 0.0
    private var committedSportType: BSportType = BSportType.UNKNOWN

    val currentSportSpeedPriors: SportSpeedPriors
        @Synchronized get() = sportSpeedPriors

    @Synchronized
    fun setSportSpeedPriors(priors: SportSpeedPriors) {
        sportSpeedPriors = priors.clamped()
    }

    val inferredBSportType: BSportType
        @Synchronized get() = committedSportType

    val coastingSeconds: Double
        @Synchronized get() = totalCoastingSeconds

    @Synchronized
    fun getMovingSpeedSummary(): MovingSpeedSummary? = movingHistogram.snapshot(totalCoastingSeconds)

    @Synchronized
    fun onAccSample(axMps2: Double, ayMps2: Double, azMps2: Double, timestampMs: Long) {
        if (!axMps2.isFinite() || !ayMps2.isFinite() || !azMps2.isFinite() || timestampMs < 0L) return
        val mag = sqrt(axMps2 * axMps2 + ayMps2 * ayMps2 + azMps2 * azMps2)
        if (!mag.isFinite()) return
        lastAccTimestampMs = max(lastAccTimestampMs, timestampMs)
        accResampler.push(timestampMs, axMps2, ayMps2, azMps2, mag) { _, _, _, _, iMag ->
            impactRing.add(impactBandPass.step(iMag))
        }
    }

    @Synchronized
    fun onGyroSample(rawGyroMagRadPerSec: Double, timestampMs: Long) {
        if (!rawGyroMagRadPerSec.isFinite() || timestampMs < 0L) return
        lastGyroTimestampMs = max(lastGyroTimestampMs, timestampMs)
        val sq = rawGyroMagRadPerSec * rawGyroMagRadPerSec
        if (gyroRingSize < GYRO_RMS_WINDOW_SAMPLES) {
            gyroMagSqRing[gyroRingHead] = sq
            gyroMagSqSum += sq
            gyroRingHead = (gyroRingHead + 1) % GYRO_RMS_WINDOW_SAMPLES
            gyroRingSize++
        } else {
            gyroMagSqSum = max(0.0, gyroMagSqSum - gyroMagSqRing[gyroRingHead]) + sq
            gyroMagSqRing[gyroRingHead] = sq
            gyroRingHead = (gyroRingHead + 1) % GYRO_RMS_WINDOW_SAMPLES
        }
    }

    @Synchronized
    fun onStepDetected(timestampMs: Long) {
        if (timestampMs < 0L) return
        val last = stepTimestampsMs.peekLast()
        if (last != null && timestampMs < last) return
        stepTimestampsMs.addLast(timestampMs)
        pruneSteps(timestampMs)
    }

    @Synchronized
    fun onCarriageMode(mode: PhoneCarriageMode, timestampMs: Long): BSportType {
        lastCarriageMode = mode
        return evaluateIfDue(timestampMs)
    }

    @Synchronized
    fun onSpeedSample(speedMps: Double, timestampMs: Long): BSportType {
        if (!speedMps.isFinite() || speedMps < 0.0 || timestampMs < 0L) return committedSportType
        lastSpeedMps = speedMps
        lastSpeedTimestampMs = max(lastSpeedTimestampMs, timestampMs)
        movingHistogram.recordThrottled(speedMps, timestampMs)
        return evaluateIfDue(timestampMs)
    }

    @Synchronized
    fun evaluate(timestampMs: Long): BSportType {
        return evaluateIfDue(timestampMs, force = true)
    }

    @Synchronized
    fun reset() {
        movingHistogram.reset()
        accResampler.reset()
        impactBandPass.reset()
        impactRing.clear()
        acfScratch.fill(0.0)
        gyroMagSqRing.fill(0.0)
        gyroRingHead = 0
        gyroRingSize = 0
        gyroMagSqSum = 0.0
        stepTimestampsMs.clear()
        lastSpeedMps = Double.NaN
        lastSpeedTimestampMs = Long.MIN_VALUE
        lastAccTimestampMs = Long.MIN_VALUE
        lastGyroTimestampMs = Long.MIN_VALUE
        lastCarriageMode = PhoneCarriageMode.UNKNOWN
        lastEvaluateTimestampMs = Long.MIN_VALUE
        coastingStreakStartMs = Long.MIN_VALUE
        coastingCreditedInStreak = false
        totalCoastingSeconds = 0.0
        evidenceScore = 0.0
        committedSportType = BSportType.UNKNOWN
    }

    private fun evaluateIfDue(timestampMs: Long, force: Boolean = false): BSportType {
        if (timestampMs < 0L) return committedSportType
        if (lastEvaluateTimestampMs != Long.MIN_VALUE) {
            if (timestampMs < lastEvaluateTimestampMs) return committedSportType
            if (!force && (timestampMs - lastEvaluateTimestampMs) < EVAL_INTERVAL_MS) {
                return committedSportType
            }
        }
        val dtSec = if (lastEvaluateTimestampMs == Long.MIN_VALUE) {
            1.0
        } else {
            ((timestampMs - lastEvaluateTimestampMs) / 1000.0).coerceIn(0.25, 3.0)
        }
        lastEvaluateTimestampMs = timestampMs
        pruneSteps(timestampMs)

        val speedFresh = lastSpeedTimestampMs != Long.MIN_VALUE &&
            (timestampMs - lastSpeedTimestampMs) <= SPEED_STALE_MS &&
            lastSpeedMps.isFinite()
        val speedMps = if (speedFresh) lastSpeedMps else null

        val hwStepHz = computeHardwareStepHz(timestampMs)
        val accFresh = lastAccTimestampMs != Long.MIN_VALUE && (timestampMs - lastAccTimestampMs) <= IMU_STALE_MS
        val accHasWindow = accFresh && impactRing.size >= MIN_ACF_SAMPLES
        val impactRms = if (accFresh && impactRing.size >= 25) impactRing.recentRms(100) else null
        val accImpactHz = if (accHasWindow) detectAccFootstrikeHz(impactRms ?: 0.0) else null

        val gyroFresh = lastGyroTimestampMs != Long.MIN_VALUE &&
            (timestampMs - lastGyroTimestampMs) <= IMU_STALE_MS &&
            gyroRingSize >= 5
        val gyroRms = if (gyroFresh) sqrt(gyroMagSqSum / gyroRingSize) else null

        // Stationary or neutral low-speed periods produce zero drift (protects multi-stop errand rides)
        if (speedMps == null || speedMps <= MovingSpeedHistogram.MIN_MOVING_SPEED_MPS) {
            coastingStreakStartMs = Long.MIN_VALUE
            coastingCreditedInStreak = false
            return committedSportType
        }

        // 1. Coasting / Freewheeling detection (v > 3.0 m/s for >= 3.0 s, near-zero step & low oscillation)
        val noStepForCoast = hwStepHz == null || hwStepHz < COASTING_MAX_STEP_HZ
        val lowGyroForCoast = gyroRms == null || gyroRms < COASTING_MAX_GYRO_RMS_RADPS
        val lowImpactForCoast = accImpactHz == null &&
            (impactRms == null || impactRms < COASTING_MAX_IMPACT_RMS_MPS2)
        val isCoastingTick = speedMps > COASTING_MIN_SPEED_MPS &&
            noStepForCoast &&
            lowGyroForCoast &&
            lowImpactForCoast

        var deltaScore = 0.0
        var confirmedCoasting = false
        if (isCoastingTick) {
            if (coastingStreakStartMs == Long.MIN_VALUE) {
                coastingStreakStartMs = timestampMs
                coastingCreditedInStreak = false
            } else {
                val streakMs = timestampMs - coastingStreakStartMs
                if (streakMs >= COASTING_MIN_DURATION_MS) {
                    confirmedCoasting = true
                    if (!coastingCreditedInStreak) {
                        coastingCreditedInStreak = true
                        totalCoastingSeconds += streakMs / 1000.0
                        deltaScore += 4.0
                    } else {
                        totalCoastingSeconds += dtSec
                        deltaScore += 3.0 * dtSec
                    }
                }
            }
        } else {
            coastingStreakStartMs = Long.MIN_VALUE
            coastingCreditedInStreak = false
        }

        // 2. Step-length & permission-free 50-Hz ACC footstrike coherence
        val hwRunningValid = hwStepHz != null && hwStepHz in HW_STEP_MIN_HZ..HW_STEP_MAX_HZ
        val accRunningValid = accImpactHz != null && accImpactHz in ACC_IMPACT_MIN_HZ..ACC_IMPACT_MAX_HZ
        val activeStepHz = when {
            hwRunningValid -> hwStepHz
            accRunningValid -> accImpactHz
            else -> null
        }

        val priors = sportSpeedPriors
        val bikeCarriageVoteFloorMps = minOf(
            priors.minBikeSpeedMps,
            maxOf(MIN_BIKE_CARRIAGE_VOTE_SPEED_MPS, priors.maxWalkSpeedMps)
        )
        val highBikeSpeedMps = priors.maxRunSpeedMps.coerceIn(HIGH_BIKE_SPEED_MPS, SportSpeedPriors.MAX_RUN_MAX_MPS)

        var hasPhysiologicalRunFootstrike = false
        if (activeStepHz != null && activeStepHz > 0.0) {
            val stepLengthM = speedMps / activeStepHz
            if (stepLengthM in MIN_STEP_LENGTH_M..MAX_STEP_LENGTH_M) {
                hasPhysiologicalRunFootstrike = true
                val runWeight = when {
                    hwRunningValid && accRunningValid -> 2.8
                    hwRunningValid -> 2.5
                    else -> 2.2
                }
                deltaScore -= runWeight * dtSec
            } else if (speedMps > highBikeSpeedMps && stepLengthM > BIKE_CYCLE_DISTANCE_M) {
                deltaScore += 2.0 * dtSec
            }
        } else if (!confirmedCoasting && speedMps > highBikeSpeedMps) {
            // High speed (> highBikeSpeedMps) with zero step/footstrike coherence -> cycling
            deltaScore += 2.0 * dtSec
        }

        // 3. Carriage mode coupling (subordinated to active physiological footstrikes & bounded cycling speed floor)
        if (!hasPhysiologicalRunFootstrike) {
            when (lastCarriageMode) {
                PhoneCarriageMode.BIKE_HANDLEBAR,
                PhoneCarriageMode.BIKE_JERSEY_BACK,
                PhoneCarriageMode.BIKE_FRONT_POCKET -> {
                    // Prevent walking-speed pocket carriage flickers from falsely latching BIKE
                    // while preserving steep 2.0 m/s uphill MTB climbs (REQ-FIL-030.1, REQ-FIL-046.1, REQ-FIL-047.2).
                    if (speedMps >= bikeCarriageVoteFloorMps) {
                        deltaScore += 1.2 * dtSec
                    }
                }
                PhoneCarriageMode.RUN_WAIST_HIP,
                PhoneCarriageMode.RUN_BICEP_ARM,
                PhoneCarriageMode.RUN_HANDHELD -> {
                    // Require either non-contradicted IMU absence or corroborated footstrikes when 50-Hz ACC is live
                    if (!confirmedCoasting && speedMps <= highBikeSpeedMps && !accHasWindow) {
                        deltaScore -= 1.0 * dtSec
                    }
                }
                PhoneCarriageMode.STATIONARY,
                PhoneCarriageMode.WALKING_TRANSITION,
                PhoneCarriageMode.UNKNOWN -> Unit
            }
        } else if (lastCarriageMode.isRunningMode()) {
            deltaScore -= 0.4 * dtSec
        }

        evidenceScore = (evidenceScore + deltaScore).coerceIn(-SCORE_CLAMP, SCORE_CLAMP)
        if (evidenceScore >= ENTER_SCORE_THRESHOLD) {
            committedSportType = BSportType.BIKE
        } else if (evidenceScore <= -ENTER_SCORE_THRESHOLD) {
            committedSportType = BSportType.RUN
        }
        return committedSportType
    }

    private fun computeHardwareStepHz(nowMs: Long): Double? {
        if (stepTimestampsMs.size < 3) return null
        val newest = stepTimestampsMs.peekLast() ?: return null
        if (nowMs - newest > STEP_STALE_MS) return null
        val oldest = stepTimestampsMs.peekFirst() ?: return null
        val spanSec = (newest - oldest) / 1000.0
        if (spanSec < 1.0) return null
        return ((stepTimestampsMs.size - 1) / spanSec).coerceIn(0.4, 5.0)
    }

    /**
     * Zero-allocation `2.35–3.65 Hz` accelerometer footstrike periodicity detector reusing
     * [impactRing] normalized autocorrelation and parabolic sub-sample peak refinement.
     */
    private fun detectAccFootstrikeHz(recentImpactRms: Double): Double? {
        if (recentImpactRms < MIN_IMPACT_RMS_MPS2) return null
        val available = impactRing.size
        val maxSearchLag = min(ACF_MAX_LAG, available - 60)
        if (maxSearchLag <= ACF_MIN_LAG + 1) return null

        val evalStart = ACF_MIN_LAG - 1
        val evalEnd = maxSearchLag + 1
        for (lag in evalStart..evalEnd) {
            val r = impactRing.normalizedCorrelation(lag)
            acfScratch[lag - evalStart] = if (r.isFinite()) r else 0.0
        }

        var bestLag = -1
        var bestR = MIN_IMPACT_ACF_PROMINENCE
        for (lag in ACF_MIN_LAG..maxSearchLag) {
            val idx = lag - evalStart
            val r = acfScratch[idx]
            val left = acfScratch[idx - 1]
            val right = acfScratch[idx + 1]
            if (r >= left && r > right && r >= bestR) {
                bestR = r
                bestLag = lag
            }
        }
        if (bestLag < 0) return null

        val cIdx = bestLag - evalStart
        val yLeft = acfScratch[cIdx - 1]
        val yCenter = acfScratch[cIdx]
        val yRight = acfScratch[cIdx + 1]
        val denom = yLeft - 2.0 * yCenter + yRight
        val delta = if (abs(denom) > ImuCadenceDspConstants.EPSILON) {
            (0.5 * (yLeft - yRight) / denom).coerceIn(
                -ImuCadenceDspConstants.MAX_PARABOLIC_DELTA,
                ImuCadenceDspConstants.MAX_PARABOLIC_DELTA
            )
        } else {
            0.0
        }
        val refinedLag = bestLag + delta
        if (!refinedLag.isFinite() || refinedLag <= 0.0) return null
        val freqHz = FS_HZ / refinedLag
        return if (freqHz in ACC_IMPACT_MIN_HZ..ACC_IMPACT_MAX_HZ) freqHz else null
    }

    private fun pruneSteps(nowMs: Long) {
        val cutoff = nowMs - STEP_WINDOW_MS
        while (stepTimestampsMs.isNotEmpty() && stepTimestampsMs.peekFirst()!! < cutoff) {
            stepTimestampsMs.removeFirst()
        }
    }
}
