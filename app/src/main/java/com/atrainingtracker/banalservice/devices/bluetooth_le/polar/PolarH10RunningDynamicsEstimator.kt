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

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Pure-Kotlin 200 Hz Polar H10 chest accelerometer biomechanics estimator (`ATT-8018`, `REQ-SET-116`).
 *
 * Pipeline:
 * 1. Continuous `~0.25 Hz` 3D low-pass gravity alignment projecting sternum acceleration onto true
 *    world vertical (`m/s^2`), making estimates immune to strap tilt or inverted orientation.
 * 2. Per-step zero-mean detrended double integration producing peak-to-trough **Vertical
 *    Oscillation** (`cm`, bounded within physiological `5.0..13.0 cm` plausibility, smoothed across
 *    a `6`-step rolling window without quadratic integration drift).
 * 3. `5 ms`-resolution stance-peak-centered foot-strike (`IC`) to toe-off (`TO`) detection for
 *    **Ground Contact Time** (`ms`, `140..400 ms` bounds, rolling smoothed), suppressed to `null`
 *    during walking (`< 130 steps/min`) where double-support invalidates flight-phase GCT.
 * 4. Requires nominal `200 Hz` (`5 ms`) temporal resolution and deterministically clears both metrics
 *    to `null` on standstill (`>= 2.5 s`) or lower sample rates.
 */
class PolarH10RunningDynamicsEstimator(
    private val onVerticalOscillation: (Double?) -> Unit = {},
    private val onGroundContactTime: (Double?) -> Unit = {}
) {

    // 0.25 Hz gravity direction tracker (mG)
    private var gravInitialized = false
    private var gravXMg = 0.0
    private var gravYMg = 0.0
    private var gravZMg = 0.0

    // Sample timing & 200 Hz guard
    private var lastRawTsMs = Long.MIN_VALUE
    private var avgDtMs = DEFAULT_DT_MS
    private var lastMotionTsMs = 0L
    private var isStandstillCleared = true

    // 1.2-4.0 Hz bandpass at 200 Hz for step-boundary peak detection
    private val stepBandPass = BiquadBandPass(FS_200_HZ, STEP_HPF_HZ, STEP_LPF_HZ)
    private var bpPrev2 = 0.0
    private var bpPrev1 = 0.0
    private var bpPrev1Idx = -1
    private var bpEnvelope = 0.0
    private var lastStepPeakTsMs = 0L
    private var externalCadenceStepsPerMin = 0.0

    // Continuous rolling sample buffers (stores ~3s at 200 Hz for centered step & stance analysis)
    private val historyDynMps2 = DoubleArray(HISTORY_CAPACITY)
    private val historySmoothMps2 = DoubleArray(HISTORY_CAPACITY)
    private val historyTsMs = LongArray(HISTORY_CAPACITY)
    private var totalPushed = 0

    // Pending peak waiting ~half-step for right flight valley completion to compute VO & GCT
    private var prevStepPeakSampleIdx = -1
    private var pendingPeakSampleIdx = -1
    private var prevConfirmedPeakSampleIdx = -1

    // 6-step rolling smoothing windows (within 5-8 step requirement)
    private val voWindow = DoubleArray(SMOOTH_WINDOW_STEPS)
    private var voHead = 0
    private var voCount = 0

    private val gctWindow = DoubleArray(SMOOTH_WINDOW_STEPS)
    private var gctHead = 0
    private var gctCount = 0

    /** Latest smoothed Vertical Oscillation in `cm`, or `null` when unavailable/standstill. */
    var latestVerticalOscillationCm: Double? = null
        private set

    /** Latest smoothed Ground Contact Time in `ms`, or `null` when suppressed (`< 130 spm` / standstill). */
    var latestGroundContactTimeMs: Double? = null
        private set

    /**
     * Supplies external running cadence in `steps/min` (e.g., `2 * strides/min` from
     * [PolarLiveRunningCadenceEstimator]) to reinforce `< 130 spm` walking GCT suppression.
     */
    fun setCurrentCadenceStepsPerMin(stepsPerMin: Double) {
        externalCadenceStepsPerMin = if (stepsPerMin.isFinite() && stepsPerMin > 0.0) stepsPerMin else 0.0
    }

    /**
     * Clears filter memories, rolling buffers, and emits `null` for both dynamics channels if active.
     */
    fun reset() {
        gravInitialized = false
        gravXMg = 0.0
        gravYMg = 0.0
        gravZMg = 0.0
        lastRawTsMs = Long.MIN_VALUE
        avgDtMs = DEFAULT_DT_MS
        lastMotionTsMs = 0L
        isStandstillCleared = true
        stepBandPass.reset()
        bpPrev2 = 0.0
        bpPrev1 = 0.0
        bpPrev1Idx = -1
        bpEnvelope = 0.0
        lastStepPeakTsMs = 0L
        externalCadenceStepsPerMin = 0.0
        totalPushed = 0
        prevStepPeakSampleIdx = -1
        pendingPeakSampleIdx = -1
        prevConfirmedPeakSampleIdx = -1
        clearVoWindow()
        clearGctWindow()
    }

    /**
     * Ingests one 3-axis Polar H10 `ACC` sample (`mG`) with timestamp `timestampMs` (`ms`).
     */
    fun onAccSample(xMg: Int, yMg: Int, zMg: Int, timestampMs: Long) {
        val resolvedTs = resolveTimestamp(timestampMs)
        val dtMs = if (lastRawTsMs == Long.MIN_VALUE) DEFAULT_DT_MS else (resolvedTs - lastRawTsMs).toDouble()
        if (lastRawTsMs != Long.MIN_VALUE && dtMs > MAX_GAP_MS) {
            resetForGap()
        } else if (lastRawTsMs != Long.MIN_VALUE && dtMs in 1.0..100.0) {
            avgDtMs = 0.92 * avgDtMs + 0.08 * dtMs
        }
        lastRawTsMs = resolvedTs

        val dtSec = (dtMs.coerceIn(1.0, 50.0)) / MILLIS_PER_SEC
        val aDynMps2 = projectOntoVerticalAxis(xMg.toDouble(), yMg.toDouble(), zMg.toDouble(), dtSec)

        val idx = totalPushed
        val ringSlot = idx % HISTORY_CAPACITY
        historyDynMps2[ringSlot] = aDynMps2
        historyTsMs[ringSlot] = resolvedTs
        historySmoothMps2[ringSlot] = compute3TapSmooth(idx, aDynMps2)
        totalPushed++

        val bpCurr = stepBandPass.step(aDynMps2)
        bpEnvelope = max(abs(bpCurr), bpEnvelope * ENVELOPE_DECAY_200HZ)

        if (abs(bpCurr) >= MOTION_THRESHOLD_MPS2) {
            lastMotionTsMs = resolvedTs
            isStandstillCleared = false
        } else if (!isStandstillCleared && lastMotionTsMs > 0L && resolvedTs - lastMotionTsMs >= STANDSTILL_TIMEOUT_MS) {
            clearStandstill()
        }

        val isHighRate200Hz = avgDtMs <= MAX_200HZ_DT_MS
        if (!isStandstillCleared && isHighRate200Hz) {
            detectAndAnalyzeStepPeak(bpCurr, idx, resolvedTs)
        }
    }

    private fun resolveTimestamp(timestampMs: Long): Long {
        if (timestampMs > 0L) {
            return if (lastRawTsMs != Long.MIN_VALUE && timestampMs < lastRawTsMs) lastRawTsMs else timestampMs
        }
        return if (lastRawTsMs == Long.MIN_VALUE) DEFAULT_DT_MS.toLong() else lastRawTsMs + DEFAULT_DT_MS.toLong()
    }

    private fun resetForGap() {
        avgDtMs = DEFAULT_DT_MS
        stepBandPass.reset()
        bpPrev2 = 0.0
        bpPrev1 = 0.0
        bpPrev1Idx = -1
        bpEnvelope = 0.0
        lastStepPeakTsMs = 0L
        prevStepPeakSampleIdx = -1
        pendingPeakSampleIdx = -1
        prevConfirmedPeakSampleIdx = -1
        totalPushed = 0
        clearVoWindow()
        clearGctWindow()
    }

    private fun projectOntoVerticalAxis(xMg: Double, yMg: Double, zMg: Double, dtSec: Double): Double {
        if (!gravInitialized) {
            gravInitialized = true
            gravXMg = xMg
            gravYMg = yMg
            gravZMg = zMg
        } else {
            val alpha = 1.0 - exp(-2.0 * PI * GRAVITY_LPF_HZ * dtSec)
            gravXMg += alpha * (xMg - gravXMg)
            gravYMg += alpha * (yMg - gravYMg)
            gravZMg += alpha * (zMg - gravZMg)
        }

        val gravMag = sqrt(gravXMg * gravXMg + gravYMg * gravYMg + gravZMg * gravZMg)
        if (gravMag < MIN_GRAVITY_MG) return 0.0

        val projMg = (xMg * gravXMg + yMg * gravYMg + zMg * gravZMg) / gravMag
        return ((projMg - gravMag) / MG_PER_G) * GRAVITY_MPS2
    }

    private fun compute3TapSmooth(idx: Int, currentVal: Double): Double {
        if (idx < 2) return currentVal
        val aPrev2 = historyDynMps2[(idx - 2) % HISTORY_CAPACITY]
        val aPrev1 = historyDynMps2[(idx - 1) % HISTORY_CAPACITY]
        return 0.25 * aPrev2 + 0.5 * aPrev1 + 0.25 * currentVal
    }

    private fun detectAndAnalyzeStepPeak(bpCurr: Double, currIdx: Int, currTsMs: Long) {
        val threshold = max(MIN_STEP_PEAK_MPS2, STEP_PEAK_RATIO * bpEnvelope)
        if (bpPrev1Idx >= 0 && bpPrev1 >= bpPrev2 && bpPrev1 > bpCurr && bpPrev1 >= threshold) {
            val peakTsMs = historyTsMs[bpPrev1Idx % HISTORY_CAPACITY]
            val deltaMs = peakTsMs - lastStepPeakTsMs
            if (lastStepPeakTsMs == 0L || deltaMs >= MIN_STEP_INTERVAL_MS) {
                lastStepPeakTsMs = peakTsMs
                onNewStepPeak(bpPrev1Idx)
            }
        }
        bpPrev2 = bpPrev1
        bpPrev1 = bpCurr
        bpPrev1Idx = currIdx

        // Evaluate pending stance peak once ~220 ms of post-peak toe-off trajectory has arrived.
        val pendingIdx = pendingPeakSampleIdx
        if (pendingIdx >= 0 && currTsMs - historyTsMs[pendingIdx % HISTORY_CAPACITY] >= TOE_OFF_LOOKAHEAD_MS) {
            if (totalPushed - pendingIdx <= HISTORY_CAPACITY - 8) {
                analyzeCompletedStancePeak(pendingIdx)
            }
            prevConfirmedPeakSampleIdx = pendingIdx
            pendingPeakSampleIdx = -1
        }
    }

    private fun onNewStepPeak(peakIdx: Int) {
        if (pendingPeakSampleIdx >= 0 && totalPushed - pendingPeakSampleIdx <= HISTORY_CAPACITY - 8) {
            analyzeCompletedStancePeak(pendingPeakSampleIdx)
            prevConfirmedPeakSampleIdx = pendingPeakSampleIdx
        }
        if (prevStepPeakSampleIdx >= 0) {
            val voCm = integrateVerticalOscillationCm(prevStepPeakSampleIdx, peakIdx)
            if (voCm != null) {
                publishRollingVo(voCm)
            }
        }
        prevStepPeakSampleIdx = peakIdx
        pendingPeakSampleIdx = peakIdx
    }

    private fun integrateVerticalOscillationCm(startIdx: Int, endIdx: Int): Double? {
        val count = endIdx - startIdx + 1
        if (count < MIN_STEP_SAMPLES || totalPushed - startIdx >= HISTORY_CAPACITY) return null

        val durationSec = (historyTsMs[endIdx % HISTORY_CAPACITY] - historyTsMs[startIdx % HISTORY_CAPACITY]) / MILLIS_PER_SEC
        if (durationSec !in MIN_STEP_DURATION_SEC..MAX_STEP_DURATION_SEC) return null
        val dt = durationSec / (count - 1)

        var sumA = 0.0
        for (k in 0 until count) {
            sumA += historyDynMps2[(startIdx + k) % HISTORY_CAPACITY]
        }
        val meanA = sumA / count

        val vel = DoubleArray(count)
        for (k in 1 until count) {
            val aPrev = historyDynMps2[(startIdx + k - 1) % HISTORY_CAPACITY] - meanA
            val aCurr = historyDynMps2[(startIdx + k) % HISTORY_CAPACITY] - meanA
            vel[k] = vel[k - 1] + 0.5 * (aPrev + aCurr) * dt
        }

        // Remove linear endpoint trend + residual mean from velocity before second integration.
        val slopeV = (vel[count - 1] - vel[0]) / (count - 1)
        var sumV = 0.0
        for (k in 0 until count) {
            vel[k] -= vel[0] + slopeV * k
            sumV += vel[k]
        }
        val meanV = sumV / count
        for (k in 0 until count) {
            vel[k] -= meanV
        }

        val pos = DoubleArray(count)
        for (k in 1 until count) {
            pos[k] = pos[k - 1] + 0.5 * (vel[k - 1] + vel[k]) * dt
        }
        val slopeZ = (pos[count - 1] - pos[0]) / (count - 1)
        var minZ = Double.POSITIVE_INFINITY
        var maxZ = Double.NEGATIVE_INFINITY
        for (k in 0 until count) {
            val zDetrend = pos[k] - (pos[0] + slopeZ * k)
            if (zDetrend < minZ) minZ = zDetrend
            if (zDetrend > maxZ) maxZ = zDetrend
        }
        val voCm = (maxZ - minZ) * CM_PER_METER
        return if (voCm.isFinite() && voCm in RAW_VO_SANITY_MIN_CM..RAW_VO_SANITY_MAX_CM) voCm else null
    }

    private fun analyzeCompletedStancePeak(peakIdx: Int) {
        val prevIdx = prevConfirmedPeakSampleIdx
        val stepIntervalMs = if (prevIdx >= 0 && totalPushed - prevIdx < HISTORY_CAPACITY) {
            historyTsMs[peakIdx % HISTORY_CAPACITY] - historyTsMs[prevIdx % HISTORY_CAPACITY]
        } else {
            return
        }
        if (stepIntervalMs < MIN_STEP_INTERVAL_MS || stepIntervalMs > MAX_STEP_INTERVAL_MS) return

        val stepCadenceSpm = MILLIS_PER_MINUTE / stepIntervalMs.toDouble()
        val effectiveCadenceSpm = if (externalCadenceStepsPerMin > 0.0) {
            0.5 * (externalCadenceStepsPerMin + stepCadenceSpm)
        } else {
            stepCadenceSpm
        }

        // Suppress GCT cleanly during walking (< 130 spm) where double-support occurs.
        if (effectiveCadenceSpm < WALKING_CADENCE_CUTOFF_SPM) {
            suppressGct()
            return
        }

        val gctMs = detectStanceDurationMs(peakIdx, (stepIntervalMs / avgDtMs).toInt()) ?: return
        publishRollingGct(gctMs)
    }

    private fun detectStanceDurationMs(peakIdx: Int, approxStepSamples: Int): Double? {
        val halfSpan = (approxStepSamples / 2).coerceIn(18, 70)
        val oldestValid = max(0, totalPushed - HISTORY_CAPACITY + 2)
        val leftBound = max(oldestValid, peakIdx - halfSpan)
        val rightBound = min(totalPushed - 2, peakIdx + halfSpan)
        if (peakIdx - leftBound < 8 || rightBound - peakIdx < 8) return null

        val aPeak = smoothAt(peakIdx)
        var leftMin = Double.POSITIVE_INFINITY
        for (i in leftBound until peakIdx) {
            val v = smoothAt(i)
            if (v < leftMin) leftMin = v
        }
        var rightMin = Double.POSITIVE_INFINITY
        for (i in (peakIdx + 1)..rightBound) {
            val v = smoothAt(i)
            if (v < rightMin) rightMin = v
        }

        val floor = 0.5 * (leftMin + rightMin)
        val amplitude = aPeak - floor
        if (amplitude < MIN_STANCE_AMPLITUDE_MPS2) return null

        val boundaryThreshold = floor + STANCE_BOUNDARY_FRACTION * amplitude

        var icIdx = peakIdx
        for (i in peakIdx downTo leftBound + 1) {
            if (smoothAt(i) <= boundaryThreshold) {
                icIdx = i
                break
            }
            icIdx = i
        }

        var toIdx = peakIdx
        for (i in peakIdx until rightBound) {
            if (smoothAt(i) <= boundaryThreshold) {
                toIdx = i
                break
            }
            toIdx = i
        }

        val rawGctMs = (historyTsMs[toIdx % HISTORY_CAPACITY] - historyTsMs[icIdx % HISTORY_CAPACITY]).toDouble()
        return if (rawGctMs in MIN_VALID_GCT_MS..MAX_VALID_GCT_MS) rawGctMs else null
    }

    private fun smoothAt(absIdx: Int): Double = historySmoothMps2[absIdx % HISTORY_CAPACITY]

    private fun publishRollingVo(rawVoCm: Double) {
        voWindow[voHead] = rawVoCm
        voHead = (voHead + 1) % SMOOTH_WINDOW_STEPS
        if (voCount < SMOOTH_WINDOW_STEPS) voCount++

        var sum = 0.0
        for (i in 0 until voCount) sum += voWindow[i]
        val smoothed = (sum / voCount).coerceIn(MIN_BOUNDED_VO_CM, MAX_BOUNDED_VO_CM)
        val rounded = (smoothed * 10.0).roundToInt() / 10.0
        latestVerticalOscillationCm = rounded
        onVerticalOscillation(rounded)
    }

    private fun publishRollingGct(rawGctMs: Double) {
        gctWindow[gctHead] = rawGctMs
        gctHead = (gctHead + 1) % SMOOTH_WINDOW_STEPS
        if (gctCount < SMOOTH_WINDOW_STEPS) gctCount++

        var sum = 0.0
        for (i in 0 until gctCount) sum += gctWindow[i]
        val smoothed = (sum / gctCount).coerceIn(MIN_VALID_GCT_MS, MAX_VALID_GCT_MS)
        val rounded = smoothed.roundToInt().toDouble()
        latestGroundContactTimeMs = rounded
        onGroundContactTime(rounded)
    }

    private fun suppressGct() {
        val hadValue = latestGroundContactTimeMs != null
        clearGctWindow()
        if (hadValue) {
            onGroundContactTime(null)
        }
    }

    private fun clearStandstill() {
        val hadVo = latestVerticalOscillationCm != null
        val hadGct = latestGroundContactTimeMs != null
        isStandstillCleared = true
        lastStepPeakTsMs = 0L
        prevStepPeakSampleIdx = -1
        pendingPeakSampleIdx = -1
        prevConfirmedPeakSampleIdx = -1
        clearVoWindow()
        clearGctWindow()
        if (hadVo) {
            onVerticalOscillation(null)
        }
        if (hadGct) {
            onGroundContactTime(null)
        }
    }

    private fun clearVoWindow() {
        voHead = 0
        voCount = 0
        latestVerticalOscillationCm = null
    }

    private fun clearGctWindow() {
        gctHead = 0
        gctCount = 0
        latestGroundContactTimeMs = null
    }

    /** 2nd-order Butterworth HPF + LPF cascade in Direct Form II Transposed. */
    private class BiquadBandPass(fsHz: Double, lowCutHz: Double, highCutHz: Double) {
        private val hpf = BiquadSection.highPass(fsHz, lowCutHz)
        private val lpf = BiquadSection.lowPass(fsHz, highCutHz)

        fun reset() {
            hpf.reset()
            lpf.reset()
        }

        fun step(x: Double): Double = lpf.process(hpf.process(x))
    }

    private class BiquadSection(
        private val b0: Double,
        private val b1: Double,
        private val b2: Double,
        private val a1: Double,
        private val a2: Double
    ) {
        private var z1 = 0.0
        private var z2 = 0.0

        fun reset() {
            z1 = 0.0
            z2 = 0.0
        }

        fun process(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }

        companion object {
            fun lowPass(fsHz: Double, fcHz: Double): BiquadSection {
                val k = tan(PI * fcHz / fsHz)
                val k2 = k * k
                val norm = 1.0 / (1.0 + SQRT2 * k + k2)
                val b0 = k2 * norm
                return BiquadSection(b0, 2.0 * b0, b0, 2.0 * (k2 - 1.0) * norm, (1.0 - SQRT2 * k + k2) * norm)
            }

            fun highPass(fsHz: Double, fcHz: Double): BiquadSection {
                val k = tan(PI * fcHz / fsHz)
                val k2 = k * k
                val norm = 1.0 / (1.0 + SQRT2 * k + k2)
                return BiquadSection(norm, -2.0 * norm, norm, 2.0 * (k2 - 1.0) * norm, (1.0 - SQRT2 * k + k2) * norm)
            }
        }
    }

    companion object {
        private const val FS_200_HZ = 200.0
        private const val DEFAULT_DT_MS = 5.0
        private const val MAX_200HZ_DT_MS = 7.5
        private const val MAX_GAP_MS = 500.0
        private const val STANDSTILL_TIMEOUT_MS = 2500L
        private const val HISTORY_CAPACITY = 600 // 3.0 s at 200 Hz

        private const val GRAVITY_LPF_HZ = 0.25
        private const val MIN_GRAVITY_MG = 300.0
        private const val MG_PER_G = 1000.0
        private const val GRAVITY_MPS2 = 9.80665
        private const val CM_PER_METER = 100.0
        private const val MILLIS_PER_SEC = 1000.0
        private const val MILLIS_PER_MINUTE = 60_000.0
        private const val SQRT2 = 1.4142135623730951

        private const val STEP_HPF_HZ = 1.2
        private const val STEP_LPF_HZ = 4.0
        private const val ENVELOPE_DECAY_200HZ = 0.9975
        private const val MOTION_THRESHOLD_MPS2 = 1.2
        private const val MIN_STEP_PEAK_MPS2 = 1.2
        private const val STEP_PEAK_RATIO = 0.35
        private const val MIN_STEP_INTERVAL_MS = 240L
        private const val MAX_STEP_INTERVAL_MS = 900L
        private const val TOE_OFF_LOOKAHEAD_MS = 220L

        private const val MIN_STEP_SAMPLES = 15
        private const val MIN_STEP_DURATION_SEC = 0.24
        private const val MAX_STEP_DURATION_SEC = 0.90
        private const val RAW_VO_SANITY_MIN_CM = 1.5
        private const val RAW_VO_SANITY_MAX_CM = 20.0
        private const val MIN_BOUNDED_VO_CM = 5.0
        private const val MAX_BOUNDED_VO_CM = 13.0

        private const val WALKING_CADENCE_CUTOFF_SPM = 130.0
        private const val MIN_STANCE_AMPLITUDE_MPS2 = 2.0
        private const val STANCE_BOUNDARY_FRACTION = 0.18
        private const val MIN_VALID_GCT_MS = 140.0
        private const val MAX_VALID_GCT_MS = 400.0
        private const val SMOOTH_WINDOW_STEPS = 6
    }
}
