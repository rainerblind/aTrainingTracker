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

package com.atrainingtracker.banalservice.devices.bluetooth_le.polar

import com.atrainingtracker.banalservice.odometry.BiquadBandPass
import com.atrainingtracker.banalservice.odometry.DoubleRingBuffer
import com.atrainingtracker.banalservice.odometry.ImuCadenceDspConstants
import com.atrainingtracker.banalservice.odometry.UniformGridResampler
import com.atrainingtracker.banalservice.odometry.Vector3BandPass
import com.atrainingtracker.banalservice.odometry.Vector3RingBuffer
import com.atrainingtracker.banalservice.odometry.findFundamentalLag
import com.atrainingtracker.banalservice.odometry.smoothCadence
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure-Kotlin live running cadence (`strides/min`) and monotonic stride-counter estimator for
 * Polar H10 (`ACC`) and Polar Verity Sense (`ACC` + `GYRO`) IMU streams (`ATT-8017`, `REQ-SET-114`).
 *
 * - Outputs canonical **strides/min** (`60–100 strides/min` = `120–200 steps/min`, clamped to
 *   `36.0–120.0` while moving), matching `ANTRunSpeedDevice`, `BTLERunSpeedDevice`, `TCXFileWriter`,
 *   and `FitFileWriter`.
 * - **H10 Chest (`Mode.H10_CHEST`)**: `1.2–4.0 Hz` band-pass filter, `5 s` sliding normalized
 *   autocorrelation with parabolic sub-sample interpolation and harmonic disambiguation, plus
 *   adaptive step peak detection emitting `+1` stride per `2` steps.
 * - **Verity Sense Armband (`Mode.VERITY_SENSE_ARM`)**: `0.6–2.0 Hz` rotation-invariant 3D
 *   vector autocorrelation fusing `GYRO` and `ACC` arm-swing cycles (falling back to chest-style
 *   `ACC` step cadence when `GYRO` is absent), emitting `+1` stride per full arm-swing cycle.
 * - Emits steady `1 Hz` cadence updates and deterministically zeros to `0.0` within `3 s` of
 *   standstill without phantom stride increments.
 */
class PolarLiveRunningCadenceEstimator(
    private val mode: Mode,
    private val onCadence: (Double) -> Unit = {},
    private val onStride: (Int) -> Unit = {}
) {

    /** Sensor placement / modality configuration. */
    enum class Mode {
        /** Chest-worn Polar H10 (`ACC`-only footfall cadence at `1.2–4.0 Hz`, halved to strides). */
        H10_CHEST,

        /** Arm-worn Polar Verity Sense (`GYRO + ACC` arm-swing fusion at `0.6–2.0 Hz` strides). */
        VERITY_SENSE_ARM
    }

    private val accResampler = UniformGridResampler(GRID_STEP_MS)
    private val gyroResampler = UniformGridResampler(GRID_STEP_MS)

    // H10 chest 1.2-4.0 Hz scalar bandpass & 5s sliding buffer
    private val h10Bp = BiquadBandPass(FS_HZ, H10_HPF_HZ, H10_LPF_HZ)
    private val h10Ring = DoubleRingBuffer(WINDOW_SAMPLES)

    // Verity Sense 0.6-2.0 Hz 3-axis vector bandpass & 5s sliding buffers
    private val verityGyroBp = Vector3BandPass(FS_HZ, VERITY_HPF_HZ, VERITY_LPF_HZ)
    private val verityAccBp = Vector3BandPass(FS_HZ, VERITY_HPF_HZ, VERITY_LPF_HZ)
    private val gyroVecRing = Vector3RingBuffer(WINDOW_SAMPLES)
    private val accVecRing = Vector3RingBuffer(WINDOW_SAMPLES)

    // Adaptive peak state (H10 step peaks -> strides; Verity projected arm peaks -> strides)
    private var h10Prev2 = 0.0
    private var h10Prev1 = 0.0
    private var h10Prev1TsMs = 0L
    private var h10PeakEnvelope = 0.0
    private var h10LastPeakTsMs = 0L
    private var h10StepCount = 0

    private var armAxisX = 0.0
    private var armAxisY = 0.0
    private var armAxisZ = 0.0
    private var armPrev2 = 0.0
    private var armPrev1 = 0.0
    private var armPrev1TsMs = 0L
    private var armPeakEnvelope = 0.0
    private var armLastPeakTsMs = 0L

    // Timing, smoothing, and standstill tracking
    private var totalStrides = 0
    private var currentAcfIntervalMs: Long? = null
    private var smoothedCadenceSpm = 0.0
    private var firstSampleTsMs = 0L
    private var lastMotionTsMs = 0L
    private var lastEmitTsMs = 0L
    private var isZeroedByStandstill = true
    private var lastRawAccTsMs = Long.MIN_VALUE
    private var lastRawGyroTsMs = Long.MIN_VALUE
    private var latestAccGridMs = 0L
    private var latestGyroGridMs = 0L

    /** Current filtered cadence estimate in `strides/min` (`0.0` at standstill). */
    var latestCadenceStridesPerMin: Double = 0.0
        private set

    /** Total monotonically accumulated strides since construction or last [reset]. */
    fun getTotalStrides(): Int = totalStrides

    /**
     * Resets DSP filter memories, sliding autocorrelation windows, and cadence/standstill state.
     */
    fun reset() {
        accResampler.reset()
        gyroResampler.reset()
        h10Bp.reset()
        h10Ring.clear()
        verityGyroBp.reset()
        verityAccBp.reset()
        gyroVecRing.clear()
        accVecRing.clear()

        h10Prev2 = 0.0
        h10Prev1 = 0.0
        h10Prev1TsMs = 0L
        h10PeakEnvelope = 0.0
        h10LastPeakTsMs = 0L
        h10StepCount = 0

        armAxisX = 0.0
        armAxisY = 0.0
        armAxisZ = 0.0
        armPrev2 = 0.0
        armPrev1 = 0.0
        armPrev1TsMs = 0L
        armPeakEnvelope = 0.0
        armLastPeakTsMs = 0L

        totalStrides = 0
        currentAcfIntervalMs = null
        smoothedCadenceSpm = 0.0
        latestCadenceStridesPerMin = 0.0
        firstSampleTsMs = 0L
        lastMotionTsMs = 0L
        lastEmitTsMs = 0L
        isZeroedByStandstill = true
        lastRawAccTsMs = Long.MIN_VALUE
        lastRawGyroTsMs = Long.MIN_VALUE
        latestAccGridMs = 0L
        latestGyroGridMs = 0L
    }

    /**
     * Ingests one 3-axis accelerometer sample (`mG`) at [timestampMs] (ms).
     */
    fun onAccSample(xMg: Int, yMg: Int, zMg: Int, timestampMs: Long) {
        val resolvedTs = resolveTimestamp(timestampMs, lastRawAccTsMs, defaultStepMs = DEFAULT_ACC_STEP_MS)
        lastRawAccTsMs = resolvedTs
        val magMg = sqrt(xMg.toDouble() * xMg + yMg.toDouble() * yMg + zMg.toDouble() * zMg)

        accResampler.push(resolvedTs, xMg.toDouble(), yMg.toDouble(), zMg.toDouble(), magMg) { tMs, gx, gy, gz, gMag ->
            processUniformAccTick(tMs, gx, gy, gz, gMag)
        }
    }

    /**
     * Ingests one 3-axis gyroscope sample (`deg/s`) at [timestampMs] (ms). Used by [Mode.VERITY_SENSE_ARM].
     */
    fun onGyroSample(xDps: Float, yDps: Float, zDps: Float, timestampMs: Long) {
        if (mode != Mode.VERITY_SENSE_ARM) return
        val resolvedTs = resolveTimestamp(timestampMs, lastRawGyroTsMs, defaultStepMs = DEFAULT_VERITY_STEP_MS)
        lastRawGyroTsMs = resolvedTs

        gyroResampler.push(resolvedTs, xDps.toDouble(), yDps.toDouble(), zDps.toDouble(), 0.0) { tMs, gx, gy, gz, _ ->
            processUniformGyroTick(tMs, gx, gy, gz)
        }
    }

    private fun resolveTimestamp(timestampMs: Long, prevRawTsMs: Long, defaultStepMs: Long): Long {
        if (timestampMs > 0L) {
            return if (prevRawTsMs != Long.MIN_VALUE && timestampMs < prevRawTsMs) prevRawTsMs else timestampMs
        }
        return if (prevRawTsMs == Long.MIN_VALUE) defaultStepMs else prevRawTsMs + defaultStepMs
    }

    private fun processUniformAccTick(tMs: Long, xMg: Double, yMg: Double, zMg: Double, magMg: Double) {
        latestAccGridMs = tMs

        // Scalar dynamic ACC bandpass (1.2-4.0 Hz) drives H10 cadence and Verity ACC-only fallback.
        val filteredMag = h10Bp.step(magMg)
        h10Ring.add(filteredMag)

        if (mode == Mode.VERITY_SENSE_ARM) {
            val (fx, fy, fz) = verityAccBp.step(xMg, yMg, zMg)
            accVecRing.add(fx, fy, fz)
        }

        val accMoving = abs(filteredMag) >= H10_MOTION_THRESHOLD_MG || h10Ring.recentRms(MOTION_RMS_WINDOW) >= H10_MOTION_RMS_MG
        if (accMoving) {
            lastMotionTsMs = max(lastMotionTsMs, tMs)
            isZeroedByStandstill = false
        }

        val useAccSteps = mode == Mode.H10_CHEST || !isGyroActive()
        if (useAccSteps && !isZeroedByStandstill) {
            detectH10StepPeak(filteredMag, tMs)
        }

        // In H10 mode ACC alone drives emission; in Verity mode align to the slower active stream
        // until it lags > 1.5 s so ACC+GYRO frames evaluate together cleanly.
        val evalTs = resolveVerityEvalTimestamp(tMs, otherStreamTs = latestGyroGridMs)
        if (evalTs != null) {
            evaluateEmissionAndStandstill(evalTs)
        }
    }

    private fun processUniformGyroTick(tMs: Long, xDps: Double, yDps: Double, zDps: Double) {
        latestGyroGridMs = tMs
        val (fx, fy, fz) = verityGyroBp.step(xDps, yDps, zDps)
        gyroVecRing.add(fx, fy, fz)

        val vecMag = sqrt(fx * fx + fy * fy + fz * fz)
        val gyroMoving = vecMag >= VERITY_GYRO_MOTION_DPS || gyroVecRing.recentRms(MOTION_RMS_WINDOW) >= VERITY_GYRO_RMS_DPS
        if (gyroMoving) {
            lastMotionTsMs = max(lastMotionTsMs, tMs)
            isZeroedByStandstill = false
        }

        if (!isZeroedByStandstill && gyroMoving) {
            updateArmAxisAndDetectStridePeak(fx, fy, fz, tMs)
        } else {
            armPrev2 = armPrev1
            armPrev1 = projectOntoArmAxis(fx, fy, fz)
            armPrev1TsMs = tMs
        }

        val evalTs = if (latestAccGridMs == 0L || latestAccGridMs - tMs > VERITY_STREAM_LEAD_GRACE_MS) {
            tMs
        } else {
            min(tMs, latestAccGridMs)
        }
        evaluateEmissionAndStandstill(evalTs)
    }

    private fun resolveVerityEvalTimestamp(accTsMs: Long, otherStreamTs: Long): Long? {
        if (firstSampleTsMs == 0L) firstSampleTsMs = accTsMs
        if (mode == Mode.H10_CHEST) return accTsMs
        if (otherStreamTs == 0L) {
            return if (lastEmitTsMs == 0L || accTsMs - firstSampleTsMs >= VERITY_STREAM_LEAD_GRACE_MS) {
                accTsMs
            } else {
                null
            }
        }
        return if (accTsMs - otherStreamTs > VERITY_STREAM_LEAD_GRACE_MS) {
            accTsMs
        } else {
            min(accTsMs, otherStreamTs)
        }
    }

    private fun isGyroActive(): Boolean {
        return gyroVecRing.size >= MOTION_RMS_WINDOW &&
            gyroVecRing.recentRms(MOTION_RMS_WINDOW) >= VERITY_GYRO_RMS_DPS
    }

    private fun detectH10StepPeak(current: Double, tMs: Long) {
        h10PeakEnvelope = max(abs(current), h10PeakEnvelope * PEAK_ENVELOPE_DECAY)
        val threshold = max(H10_MIN_PEAK_MG, H10_ADAPTIVE_PEAK_RATIO * h10PeakEnvelope)
        val minIntervalMs = currentAcfIntervalMs
            ?.let { max(MIN_STEP_INTERVAL_MS, (REFRACTORY_FRACTION * it).toLong()) }
            ?: MIN_STEP_INTERVAL_MS

        if (h10Prev1TsMs > 0L && h10Prev1 >= h10Prev2 && h10Prev1 > current && h10Prev1 >= threshold) {
            val delta = h10Prev1TsMs - h10LastPeakTsMs
            if (h10LastPeakTsMs == 0L || delta >= minIntervalMs) {
                h10LastPeakTsMs = h10Prev1TsMs
                h10StepCount++
                if (h10StepCount % 2 == 0) {
                    totalStrides++
                    onStride(totalStrides)
                }
            }
        }
        h10Prev2 = h10Prev1
        h10Prev1 = current
        h10Prev1TsMs = tMs
    }

    private fun updateArmAxisAndDetectStridePeak(fx: Double, fy: Double, fz: Double, tMs: Long) {
        val norm = sqrt(fx * fx + fy * fy + fz * fz)
        if (norm > EPSILON) {
            if (armAxisX == 0.0 && armAxisY == 0.0 && armAxisZ == 0.0) {
                armAxisX = fx / norm
                armAxisY = fy / norm
                armAxisZ = fz / norm
            } else {
                val dot = fx * armAxisX + fy * armAxisY + fz * armAxisZ
                val sign = if (dot >= 0.0) 1.0 else -1.0
                val nx = (1.0 - AXIS_ALPHA) * armAxisX + AXIS_ALPHA * sign * (fx / norm)
                val ny = (1.0 - AXIS_ALPHA) * armAxisY + AXIS_ALPHA * sign * (fy / norm)
                val nz = (1.0 - AXIS_ALPHA) * armAxisZ + AXIS_ALPHA * sign * (fz / norm)
                val nLen = sqrt(nx * nx + ny * ny + nz * nz)
                if (nLen > EPSILON) {
                    armAxisX = nx / nLen
                    armAxisY = ny / nLen
                    armAxisZ = nz / nLen
                }
            }
        }

        val current = projectOntoArmAxis(fx, fy, fz)
        armPeakEnvelope = max(abs(current), armPeakEnvelope * PEAK_ENVELOPE_DECAY)
        val threshold = max(VERITY_MIN_PEAK_DPS, H10_ADAPTIVE_PEAK_RATIO * armPeakEnvelope)
        val minIntervalMs = currentAcfIntervalMs
            ?.let { max(MIN_STRIDE_INTERVAL_MS, (REFRACTORY_FRACTION * it).toLong()) }
            ?: MIN_STRIDE_INTERVAL_MS

        if (armPrev1TsMs > 0L && armPrev1 >= armPrev2 && armPrev1 > current && armPrev1 >= threshold) {
            val delta = armPrev1TsMs - armLastPeakTsMs
            if (armLastPeakTsMs == 0L || delta >= minIntervalMs) {
                armLastPeakTsMs = armPrev1TsMs
                totalStrides++
                onStride(totalStrides)
            }
        }
        armPrev2 = armPrev1
        armPrev1 = current
        armPrev1TsMs = tMs
    }

    private fun projectOntoArmAxis(fx: Double, fy: Double, fz: Double): Double =
        fx * armAxisX + fy * armAxisY + fz * armAxisZ

    private fun evaluateEmissionAndStandstill(tMs: Long) {
        if (lastEmitTsMs == 0L) {
            lastEmitTsMs = tMs
            if (lastMotionTsMs == 0L) lastMotionTsMs = tMs
            return
        }

        // Zero deterministically within 3 s of standstill without emitting phantom strides.
        val standstillElapsedMs = tMs - lastMotionTsMs
        if (!isZeroedByStandstill && lastMotionTsMs > 0L && standstillElapsedMs >= STANDSTILL_TIMEOUT_MS) {
            triggerStandstillZero(tMs)
            return
        }

        if (tMs - lastEmitTsMs < EMIT_INTERVAL_MS) return
        lastEmitTsMs = tMs

        if (isZeroedByStandstill) {
            emitCadence(0.0)
            return
        }

        val estimated = computeCurrentCadenceStridesPerMin()
        if (estimated != null) {
            smoothedCadenceSpm = smoothCadence(smoothedCadenceSpm, estimated)
            emitCadence(smoothedCadenceSpm)
        } else if (smoothedCadenceSpm > 0.0) {
            emitCadence(smoothedCadenceSpm)
        } else {
            emitCadence(0.0)
        }
    }

    private fun triggerStandstillZero(tMs: Long) {
        isZeroedByStandstill = true
        currentAcfIntervalMs = null
        smoothedCadenceSpm = 0.0
        h10Ring.clear()
        gyroVecRing.clear()
        accVecRing.clear()
        h10PeakEnvelope = 0.0
        armPeakEnvelope = 0.0
        lastEmitTsMs = tMs
        emitCadence(0.0)
    }

    private fun emitCadence(cadenceSpm: Double) {
        val rounded = (cadenceSpm * 10.0).roundToInt() / 10.0
        latestCadenceStridesPerMin = rounded
        onCadence(rounded)
    }

    private fun computeCurrentCadenceStridesPerMin(): Double? {
        return when (mode) {
            Mode.H10_CHEST -> estimateH10CadenceFromAcf()
            Mode.VERITY_SENSE_ARM -> estimateVerityCadenceFromAcf() ?: estimateH10CadenceFromAcf()
        }
    }

    private fun estimateH10CadenceFromAcf(): Double? {
        val lagSamples = findFundamentalLag(
            minLag = H10_MIN_LAG_SAMPLES,
            maxLag = H10_MAX_LAG_SAMPLES,
            minOverlap = H10_MIN_OVERLAP_SAMPLES,
            availableSamples = h10Ring.size,
            correlationAt = { lag -> h10Ring.normalizedCorrelation(lag) }
        ) ?: return null

        val periodSec = lagSamples / FS_HZ
        currentAcfIntervalMs = (periodSec * MILLIS_PER_SECOND).toLong()
        val stepsPerMin = 60.0 / periodSec
        return (stepsPerMin / 2.0).coerceIn(MIN_CADENCE_STRIDES_PER_MIN, MAX_CADENCE_STRIDES_PER_MIN)
    }

    private fun estimateVerityCadenceFromAcf(): Double? {
        val gyroActive = isGyroActive()
        val accArmActive = accVecRing.size >= VERITY_MIN_OVERLAP_SAMPLES + VERITY_MIN_LAG_SAMPLES &&
            accVecRing.recentRms(MOTION_RMS_WINDOW) >= VERITY_ACC_ARM_RMS_MG

        if (!gyroActive && !accArmActive) return null
        val available = min(
            if (gyroActive) gyroVecRing.size else Int.MAX_VALUE,
            if (accArmActive) accVecRing.size else Int.MAX_VALUE
        )

        val lagSamples = findFundamentalLag(
            minLag = VERITY_MIN_LAG_SAMPLES,
            maxLag = VERITY_MAX_LAG_SAMPLES,
            minOverlap = VERITY_MIN_OVERLAP_SAMPLES,
            availableSamples = available,
            correlationAt = { lag ->
                when {
                    gyroActive && accArmActive ->
                        0.70 * gyroVecRing.normalizedVectorCorrelation(lag) +
                            0.30 * accVecRing.normalizedVectorCorrelation(lag)
                    gyroActive -> gyroVecRing.normalizedVectorCorrelation(lag)
                    else -> accVecRing.normalizedVectorCorrelation(lag)
                }
            }
        ) ?: return null

        val periodSec = lagSamples / FS_HZ
        currentAcfIntervalMs = (periodSec * MILLIS_PER_SECOND).toLong()
        return (60.0 / periodSec).coerceIn(MIN_CADENCE_STRIDES_PER_MIN, MAX_CADENCE_STRIDES_PER_MIN)
    }

    companion object {
        private const val FS_HZ = ImuCadenceDspConstants.FS_HZ
        private const val GRID_STEP_MS = ImuCadenceDspConstants.GRID_STEP_MS
        private const val WINDOW_SAMPLES = ImuCadenceDspConstants.WINDOW_SAMPLES
        private const val DEFAULT_ACC_STEP_MS = 10L
        private const val DEFAULT_VERITY_STEP_MS = 19L
        private const val MILLIS_PER_SECOND = ImuCadenceDspConstants.MILLIS_PER_SECOND
        private const val EPSILON = ImuCadenceDspConstants.EPSILON

        // H10 chest bandpass (1.2-4.0 Hz = 72-240 steps/min = 36-120 strides/min)
        private const val H10_HPF_HZ = 1.2
        private const val H10_LPF_HZ = 4.0
        private const val H10_MIN_LAG_SAMPLES = 12 // ~4.17 Hz
        private const val H10_MAX_LAG_SAMPLES = 42 // ~1.19 Hz
        private const val H10_MIN_OVERLAP_SAMPLES = 45

        // Verity Sense arm-swing bandpass (0.6-2.0 Hz = 36-120 strides/min)
        private const val VERITY_HPF_HZ = 0.6
        private const val VERITY_LPF_HZ = 2.0
        private const val VERITY_MIN_LAG_SAMPLES = 25 // 2.00 Hz = 120 strides/min
        private const val VERITY_MAX_LAG_SAMPLES = 83 // 0.60 Hz = 36 strides/min
        private const val VERITY_MIN_OVERLAP_SAMPLES = 35

        private const val MIN_CADENCE_STRIDES_PER_MIN = 36.0
        private const val MAX_CADENCE_STRIDES_PER_MIN = 120.0

        private const val EMIT_INTERVAL_MS = 1000L
        private const val STANDSTILL_TIMEOUT_MS = 2000L
        private const val VERITY_STREAM_LEAD_GRACE_MS = 1500L
        private const val MOTION_RMS_WINDOW = 25 // 0.5 s at 50 Hz

        private const val H10_MOTION_THRESHOLD_MG = 45.0
        private const val H10_MOTION_RMS_MG = 30.0
        private const val H10_MIN_PEAK_MG = 40.0

        private const val VERITY_GYRO_MOTION_DPS = 10.0
        private const val VERITY_GYRO_RMS_DPS = 6.0
        private const val VERITY_ACC_ARM_RMS_MG = 40.0
        private const val VERITY_MIN_PEAK_DPS = 10.0

        private const val PEAK_ENVELOPE_DECAY = 0.99
        private const val H10_ADAPTIVE_PEAK_RATIO = 0.35
        private const val REFRACTORY_FRACTION = 0.62
        private const val MIN_STEP_INTERVAL_MS = 250L
        private const val MIN_STRIDE_INTERVAL_MS = 480L
        private const val AXIS_ALPHA = 0.05
    }
}
