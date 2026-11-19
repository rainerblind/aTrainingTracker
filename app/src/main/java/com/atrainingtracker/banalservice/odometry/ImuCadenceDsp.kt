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

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

/** Shared constants for `50 Hz` (`20 ms`) IMU cadence resampling, filtering, and `5 s` ACF. */
internal object ImuCadenceDspConstants {
    const val FS_HZ = 50.0
    const val GRID_STEP_MS = 20L
    const val WINDOW_SAMPLES = 250 // 5.0 s at 50 Hz
    const val MAX_GAP_MS = 500L
    const val SQRT2 = 1.4142135623730951
    const val MILLIS_PER_SECOND = 1000.0
    const val EPSILON = 1e-9
    const val MIN_ACF_PEAK_CORRELATION = 0.42
    const val FUNDAMENTAL_PEAK_RATIO = 0.78
    const val SMOOTH_JUMP_THRESHOLD = 6.0
    const val SMOOTH_FAST_ALPHA = 0.7
    const val SMOOTH_SLOW_ALPHA = 0.5
    const val INITIAL_PEAK_CAPACITY = 8
    const val MAX_PARABOLIC_DELTA = 0.5
    const val SUBHARMONIC_TOLERANCE_MIN_LAGS = 2
    const val SUBHARMONIC_TOLERANCE_FRACTION = 0.12
}

/** Selected normalized autocorrelation peak lag (sub-sample interpolated) and peak prominence. */
internal data class AcfLagResult(
    val lagSamples: Double,
    val peakCorrelation: Double
)

/**
 * Finds the fundamental lag using normalized autocorrelation and parabolic sub-sample interpolation,
 * preserving `PolarLiveRunningCadenceEstimator` semantics while supporting optional stricter
 * prominence gates (`minPeakCorrelation`) and `f`/`2f` subharmonic disambiguation
 * (`subharmonicGainThreshold`).
 */
internal fun findFundamentalLag(
    minLag: Int,
    maxLag: Int,
    minOverlap: Int,
    availableSamples: Int,
    minPeakCorrelation: Double = ImuCadenceDspConstants.MIN_ACF_PEAK_CORRELATION,
    fundamentalPeakRatio: Double = ImuCadenceDspConstants.FUNDAMENTAL_PEAK_RATIO,
    correlationAt: (Int) -> Double
): Double? = findFundamentalLagResult(
    minLag = minLag,
    maxLag = maxLag,
    minOverlap = minOverlap,
    availableSamples = availableSamples,
    minPeakCorrelation = minPeakCorrelation,
    fundamentalPeakRatio = fundamentalPeakRatio,
    subharmonicGainThreshold = null,
    correlationAt = correlationAt
)?.lagSamples

internal fun findFundamentalLagResult(
    minLag: Int,
    maxLag: Int,
    minOverlap: Int,
    availableSamples: Int,
    minPeakCorrelation: Double = ImuCadenceDspConstants.MIN_ACF_PEAK_CORRELATION,
    fundamentalPeakRatio: Double = ImuCadenceDspConstants.FUNDAMENTAL_PEAK_RATIO,
    subharmonicGainThreshold: Double? = null,
    correlationAt: (Int) -> Double
): AcfLagResult? {
    if (minLag < 1 || minOverlap < 1 || maxLag < minLag || availableSamples <= 0) return null
    if (!minPeakCorrelation.isFinite() || !fundamentalPeakRatio.isFinite()) return null
    val searchMax = min(maxLag, availableSamples - minOverlap)
    if (searchMax <= minLag + 1) return null

    val evalStart = minLag - 1
    val evalEnd = searchMax + 1
    val rValues = DoubleArray(evalEnd - evalStart + 1)
    for (lag in evalStart..evalEnd) {
        val raw = correlationAt(lag)
        rValues[lag - evalStart] = if (raw.isFinite()) raw else 0.0
    }

    val (peakLags, maxPeakR) = collectLocalAcfPeaks(minLag, searchMax, evalStart, rValues)
    if (peakLags.isEmpty() || !maxPeakR.isFinite() || maxPeakR < minPeakCorrelation) return null

    val acceptThreshold = max(minPeakCorrelation, fundamentalPeakRatio * maxPeakR)
    val baseLag = peakLags.firstOrNull { rValues[it - evalStart] >= acceptThreshold } ?: return null
    val chosenLag = maybePromoteDoublePeriodLag(
        baseLag = baseLag,
        peakLags = peakLags,
        evalStart = evalStart,
        rValues = rValues,
        acceptThreshold = acceptThreshold,
        subharmonicGainThreshold = subharmonicGainThreshold
    )

    val delta = refineParabolicPeak(chosenLag - evalStart, rValues)
    val lagSamples = chosenLag + delta
    val peakCorrelation = rValues[chosenLag - evalStart]
    if (!lagSamples.isFinite() || !peakCorrelation.isFinite()) return null
    return AcfLagResult(
        lagSamples = lagSamples,
        peakCorrelation = peakCorrelation
    )
}

private fun collectLocalAcfPeaks(
    minLag: Int,
    searchMax: Int,
    evalStart: Int,
    rValues: DoubleArray
): Pair<List<Int>, Double> {
    var maxPeakR = Double.NEGATIVE_INFINITY
    val peakLags = ArrayList<Int>(ImuCadenceDspConstants.INITIAL_PEAK_CAPACITY)
    for (lag in minLag..searchMax) {
        val idx = lag - evalStart
        val r = rValues[idx]
        val left = rValues[idx - 1]
        val right = rValues[idx + 1]
        if (!r.isFinite() || !left.isFinite() || !right.isFinite()) continue
        if (r >= left && r > right) {
            peakLags.add(lag)
            if (r > maxPeakR) maxPeakR = r
        }
    }
    return peakLags to maxPeakR
}

private fun maybePromoteDoublePeriodLag(
    baseLag: Int,
    peakLags: List<Int>,
    evalStart: Int,
    rValues: DoubleArray,
    acceptThreshold: Double,
    subharmonicGainThreshold: Double?
): Int {
    if (subharmonicGainThreshold == null) return baseLag
    val baseR = rValues[baseLag - evalStart]
    val expectedDoubleLag = baseLag * 2
    val tolerance = max(
        ImuCadenceDspConstants.SUBHARMONIC_TOLERANCE_MIN_LAGS,
        (ImuCadenceDspConstants.SUBHARMONIC_TOLERANCE_FRACTION * baseLag).roundToInt()
    )
    var bestDoubleLag = baseLag
    var bestDoubleR = baseR
    val minDoubleR = baseR + subharmonicGainThreshold
    for (lag in peakLags) {
        if (lag > baseLag && abs(lag - expectedDoubleLag) <= tolerance) {
            val r = rValues[lag - evalStart]
            if (r >= acceptThreshold && r >= minDoubleR && r > bestDoubleR) {
                bestDoubleLag = lag
                bestDoubleR = r
            }
        }
    }
    return bestDoubleLag
}

private fun refineParabolicPeak(centerIdx: Int, rValues: DoubleArray): Double {
    val yLeft = rValues[centerIdx - 1]
    val yCenter = rValues[centerIdx]
    val yRight = rValues[centerIdx + 1]
    val denom = yLeft - 2.0 * yCenter + yRight
    return if (abs(denom) > ImuCadenceDspConstants.EPSILON) {
        (0.5 * (yLeft - yRight) / denom).coerceIn(
            -ImuCadenceDspConstants.MAX_PARABOLIC_DELTA,
            ImuCadenceDspConstants.MAX_PARABOLIC_DELTA
        )
    } else {
        0.0
    }
}

internal fun smoothCadence(prev: Double, candidate: Double): Double {
    if (!candidate.isFinite()) return if (prev.isFinite() && prev > 0.0) prev else 0.0
    if (!prev.isFinite() || prev <= 0.0) return candidate
    val alpha = if (abs(candidate - prev) > ImuCadenceDspConstants.SMOOTH_JUMP_THRESHOLD) {
        ImuCadenceDspConstants.SMOOTH_FAST_ALPHA
    } else {
        ImuCadenceDspConstants.SMOOTH_SLOW_ALPHA
    }
    return prev + alpha * (candidate - prev)
}

/** Linearly resamples arbitrary-rate sensor streams onto an exact `50 Hz` (`20 ms`) grid. */
internal class UniformGridResampler(
    private val stepMs: Long = ImuCadenceDspConstants.GRID_STEP_MS
) {
    private var hasPrev = false
    private var prevTs = 0L
    private var prevX = 0.0
    private var prevY = 0.0
    private var prevZ = 0.0
    private var prevMag = 0.0
    private var nextGridTs = 0L

    fun reset() {
        hasPrev = false
        prevTs = 0L
        nextGridTs = 0L
    }

    inline fun push(
        tsMs: Long,
        x: Double,
        y: Double,
        z: Double,
        mag: Double,
        onTick: (Long, Double, Double, Double, Double) -> Unit
    ) {
        if (stepMs <= 0L || tsMs < 0L || !x.isFinite() || !y.isFinite() || !z.isFinite() || !mag.isFinite()) {
            return
        }
        if (!hasPrev || tsMs - prevTs > ImuCadenceDspConstants.MAX_GAP_MS) {
            hasPrev = true
            prevTs = tsMs
            prevX = x
            prevY = y
            prevZ = z
            prevMag = mag
            nextGridTs = tsMs + stepMs
            onTick(tsMs, x, y, z, mag)
            return
        }

        if (tsMs <= prevTs) {
            prevX = x
            prevY = y
            prevZ = z
            prevMag = mag
            return
        }

        val dt = (tsMs - prevTs).toDouble()
        while (nextGridTs <= tsMs) {
            val frac = ((nextGridTs - prevTs) / dt).coerceIn(0.0, 1.0)
            val ix = prevX + frac * (x - prevX)
            val iy = prevY + frac * (y - prevY)
            val iz = prevZ + frac * (z - prevZ)
            val iMag = prevMag + frac * (mag - prevMag)
            if (ix.isFinite() && iy.isFinite() && iz.isFinite() && iMag.isFinite()) {
                onTick(nextGridTs, ix, iy, iz, iMag)
            }
            nextGridTs += stepMs
        }

        prevTs = tsMs
        prevX = x
        prevY = y
        prevZ = z
        prevMag = mag
    }
}

/** 2nd-order Butterworth HPF + LPF cascade in Direct Form II Transposed. */
internal class BiquadBandPass(fsHz: Double, lowCutHz: Double, highCutHz: Double) {
    private val hpf = BiquadSection.highPass(fsHz, lowCutHz)
    private val lpf = BiquadSection.lowPass(fsHz, highCutHz)
    private var initialized = false
    private var baseline = 0.0

    fun reset() {
        hpf.reset()
        lpf.reset()
        initialized = false
        baseline = 0.0
    }

    fun step(input: Double): Double {
        if (!input.isFinite()) return 0.0
        if (!initialized) {
            initialized = true
            baseline = input
        }
        val hp = hpf.process(input - baseline)
        val out = lpf.process(hp)
        if (!out.isFinite()) {
            hpf.reset()
            lpf.reset()
            return 0.0
        }
        return out
    }
}

/** Three independent `BiquadBandPass` filters operating on a 3D IMU vector stream. */
internal class Vector3BandPass(fsHz: Double, lowCutHz: Double, highCutHz: Double) {
    private val xBp = BiquadBandPass(fsHz, lowCutHz, highCutHz)
    private val yBp = BiquadBandPass(fsHz, lowCutHz, highCutHz)
    private val zBp = BiquadBandPass(fsHz, lowCutHz, highCutHz)

    fun reset() {
        xBp.reset()
        yBp.reset()
        zBp.reset()
    }

    fun step(x: Double, y: Double, z: Double): Triple<Double, Double, Double> =
        Triple(xBp.step(x), yBp.step(y), zBp.step(z))
}

internal class BiquadSection(
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
        if (!x.isFinite()) return 0.0
        val y = b0 * x + z1
        val nextZ1 = b1 * x - a1 * y + z2
        val nextZ2 = b2 * x - a2 * y
        if (!y.isFinite() || !nextZ1.isFinite() || !nextZ2.isFinite()) {
            reset()
            return 0.0
        }
        z1 = nextZ1
        z2 = nextZ2
        return y
    }

    companion object {
        fun lowPass(fsHz: Double, fcHz: Double): BiquadSection {
            val k = tan(PI * fcHz / fsHz)
            val k2 = k * k
            val norm = 1.0 / (1.0 + ImuCadenceDspConstants.SQRT2 * k + k2)
            val b0 = k2 * norm
            return BiquadSection(
                b0 = b0,
                b1 = 2.0 * b0,
                b2 = b0,
                a1 = 2.0 * (k2 - 1.0) * norm,
                a2 = (1.0 - ImuCadenceDspConstants.SQRT2 * k + k2) * norm
            )
        }

        fun highPass(fsHz: Double, fcHz: Double): BiquadSection {
            val k = tan(PI * fcHz / fsHz)
            val k2 = k * k
            val norm = 1.0 / (1.0 + ImuCadenceDspConstants.SQRT2 * k + k2)
            return BiquadSection(
                b0 = norm,
                b1 = -2.0 * norm,
                b2 = norm,
                a1 = 2.0 * (k2 - 1.0) * norm,
                a2 = (1.0 - ImuCadenceDspConstants.SQRT2 * k + k2) * norm
            )
        }
    }
}

/** Fixed-capacity scalar sliding buffer supporting RMS and normalized autocorrelation queries. */
internal class DoubleRingBuffer(private val capacity: Int) {
    private val data = DoubleArray(capacity)
    private var head = 0
    var size = 0
        private set

    fun clear() {
        head = 0
        size = 0
    }

    fun add(value: Double) {
        if (!value.isFinite() || capacity <= 0) return
        data[head] = value
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    private fun get(indexFromOldest: Int): Double {
        val start = if (size < capacity) 0 else head
        return data[(start + indexFromOldest) % capacity]
    }

    fun recentRms(count: Int): Double {
        val n = min(count, size)
        if (n <= 0) return 0.0
        var sumSq = 0.0
        val offset = size - n
        for (i in 0 until n) {
            val v = get(offset + i)
            if (v.isFinite()) {
                sumSq += v * v
            }
        }
        val rms = sqrt(sumSq / n)
        return if (rms.isFinite()) rms else 0.0
    }

    fun normalizedCorrelation(lag: Int): Double {
        if (lag <= 0 || lag >= size) return 0.0
        val overlap = size - lag
        var num = 0.0
        var e0 = 0.0
        var e1 = 0.0
        for (i in 0 until overlap) {
            val a = get(i)
            val b = get(i + lag)
            num += a * b
            e0 += a * a
            e1 += b * b
        }
        val denom = sqrt(e0 * e1)
        if (!denom.isFinite() || denom <= ImuCadenceDspConstants.EPSILON) return 0.0
        val corr = num / denom
        return if (corr.isFinite()) corr else 0.0
    }
}

/** Fixed-capacity 3D vector sliding buffer supporting 3D RMS and vector autocorrelation queries. */
internal class Vector3RingBuffer(private val capacity: Int) {
    private val xs = DoubleArray(capacity)
    private val ys = DoubleArray(capacity)
    private val zs = DoubleArray(capacity)
    private var head = 0
    var size = 0
        private set

    fun clear() {
        head = 0
        size = 0
    }

    fun add(x: Double, y: Double, z: Double) {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite() || capacity <= 0) return
        xs[head] = x
        ys[head] = y
        zs[head] = z
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    private fun physicalIndex(indexFromOldest: Int): Int {
        val start = if (size < capacity) 0 else head
        return (start + indexFromOldest) % capacity
    }

    fun recentRms(count: Int): Double {
        val n = min(count, size)
        if (n <= 0) return 0.0
        var sumSq = 0.0
        val offset = size - n
        for (i in 0 until n) {
            val idx = physicalIndex(offset + i)
            val x = xs[idx]
            val y = ys[idx]
            val z = zs[idx]
            if (x.isFinite() && y.isFinite() && z.isFinite()) {
                sumSq += x * x + y * y + z * z
            }
        }
        val rms = sqrt(sumSq / n)
        return if (rms.isFinite()) rms else 0.0
    }

    fun normalizedVectorCorrelation(lag: Int): Double {
        if (lag <= 0 || lag >= size) return 0.0
        val overlap = size - lag
        var num = 0.0
        var e0 = 0.0
        var e1 = 0.0
        for (i in 0 until overlap) {
            val i0 = physicalIndex(i)
            val i1 = physicalIndex(i + lag)
            val x0 = xs[i0]
            val y0 = ys[i0]
            val z0 = zs[i0]
            val x1 = xs[i1]
            val y1 = ys[i1]
            val z1 = zs[i1]
            num += x0 * x1 + y0 * y1 + z0 * z1
            e0 += x0 * x0 + y0 * y0 + z0 * z0
            e1 += x1 * x1 + y1 * y1 + z1 * z1
        }
        val denom = sqrt(e0 * e1)
        if (!denom.isFinite() || denom <= ImuCadenceDspConstants.EPSILON) return 0.0
        val corr = num / denom
        return if (corr.isFinite()) corr else 0.0
    }
}
