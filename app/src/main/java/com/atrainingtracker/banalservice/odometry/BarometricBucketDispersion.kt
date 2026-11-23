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

import kotlin.math.max
import kotlin.math.min

/**
 * Pure-Kotlin intra-bucket barometric dispersion estimator & multi-second anti-aliasing aggregator
 * (`REQ-FIL-040.1`, `ATT-9602`).
 *
 * Detects high-amplitude cyclic pocket-compression pressure waves whose intra-second interquartile
 * range (`IQR`) exceeds the speed-aware physical elevation envelope while imposing zero extra lag
 * and `0.0 m^2` dispersion variance on calm handlebar rides, climbs, and fast alpine descents.
 *
 * Not thread-safe on its own; callers invoke all methods under the owning device's `@Synchronized` lock.
 */
@Suppress("MagicNumber")
class BarometricBucketDispersion {

    companion object {
        const val MULTI_SECOND_WINDOW_MS = 3_000L
        const val MAX_ROLLING_SAMPLES = 128
        const val BASE_PHYSICAL_SPAN_M = 2.5
        const val SPEED_SPAN_FACTOR = 0.20
        const val DISPERSION_VAR_SCALE = 2.0
        const val MAX_DISPERSION_VAR_M2 = 2_500.0
        const val DECAY_RETENTION = 0.65
        const val DECAY_FLOOR_M2 = 0.05
    }

    data class FlushResult(
        val representativeAltitudeM: Double,
        val dispersionVarM2: Double,
        val bucketIqrM: Double,
        val bucketSpanM: Double
    )

    private data class Sample(
        val timestampMs: Long,
        val altitudeM: Double
    )

    private val bucketSamples: MutableList<Double> = mutableListOf()
    private val rollingWindow: ArrayDeque<Sample> = ArrayDeque()
    private var lastTimestampMs: Long = Long.MIN_VALUE

    var dispersionVarM2: Double = 0.0
        private set

    val isBucketEmpty: Boolean
        get() = bucketSamples.isEmpty()

    /**
     * Records a raw barometric altitude sample at deterministic [timestampMs].
     * Ignores non-finite samples and resets state on backward timestamps or pause/resume gaps
     * exceeding [MULTI_SECOND_WINDOW_MS].
     */
    fun addSample(rawAltM: Double, timestampMs: Long) {
        if (!rawAltM.isFinite()) return

        if (lastTimestampMs != Long.MIN_VALUE &&
            (timestampMs < lastTimestampMs || timestampMs - lastTimestampMs > MULTI_SECOND_WINDOW_MS)
        ) {
            reset()
        }

        lastTimestampMs = timestampMs
        bucketSamples.add(rawAltM)
        rollingWindow.addLast(Sample(timestampMs, rawAltM))
        pruneRollingWindow(timestampMs)
    }

    /**
     * Flushes the current 1-Hz bucket using deterministic [flushTimestampMs] and horizontal [speedMps].
     *
     * Returns `null` if no samples were accumulated in the current bucket.
     */
    fun flushBucket(flushTimestampMs: Long, speedMps: Double): FlushResult? {
        if (bucketSamples.isEmpty()) return null

        val sortedBucket = bucketSamples.sorted()
        bucketSamples.clear()

        val bucketMedian = computeMedianSorted(sortedBucket)
        val bucketSpan = sortedBucket.last() - sortedBucket.first()
        val bucketIqr = computeIqrSorted(sortedBucket)

        val safeSpeed = if (speedMps.isFinite() && speedMps > 0.0) speedMps else 0.0
        val physicalSpan = max(BASE_PHYSICAL_SPAN_M, SPEED_SPAN_FACTOR * safeSpeed)
        val excessSpread = max(0.0, bucketIqr - physicalSpan)
        val targetDispVar = if (excessSpread > 0.0) {
            min(MAX_DISPERSION_VAR_M2, DISPERSION_VAR_SCALE * excessSpread * excessSpread)
        } else {
            0.0
        }

        dispersionVarM2 = if (targetDispVar >= dispersionVarM2) {
            targetDispVar
        } else {
            val decayed = targetDispVar + DECAY_RETENTION * (dispersionVarM2 - targetDispVar)
            if (decayed < DECAY_FLOOR_M2) 0.0 else decayed
        }

        val referenceTs = if (lastTimestampMs != Long.MIN_VALUE && flushTimestampMs < lastTimestampMs) {
            lastTimestampMs
        } else {
            flushTimestampMs
        }
        pruneRollingWindow(referenceTs)

        val representativeAlt = if (excessSpread == 0.0 && dispersionVarM2 == 0.0) {
            bucketMedian
        } else if (rollingWindow.isEmpty()) {
            bucketMedian
        } else {
            val sortedWindow = rollingWindow.map { it.altitudeM }.sorted()
            computeInterquartileTrimmedMeanSorted(sortedWindow)
        }

        return FlushResult(
            representativeAltitudeM = representativeAlt,
            dispersionVarM2 = dispersionVarM2,
            bucketIqrM = bucketIqr,
            bucketSpanM = bucketSpan
        )
    }

    fun reset() {
        bucketSamples.clear()
        rollingWindow.clear()
        dispersionVarM2 = 0.0
        lastTimestampMs = Long.MIN_VALUE
    }

    private fun pruneRollingWindow(referenceTimestampMs: Long) {
        while (rollingWindow.isNotEmpty() &&
            referenceTimestampMs - rollingWindow.first().timestampMs > MULTI_SECOND_WINDOW_MS
        ) {
            rollingWindow.removeFirst()
        }
        while (rollingWindow.size > MAX_ROLLING_SAMPLES) {
            rollingWindow.removeFirst()
        }
    }

    private fun computeMedianSorted(sorted: List<Double>): Double {
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else 0.5 * (sorted[mid - 1] + sorted[mid])
    }

    private fun computePercentileSorted(sorted: List<Double>, q: Double): Double {
        val pos = q * (sorted.size - 1)
        val lo = pos.toInt()
        val hi = min(sorted.size - 1, lo + 1)
        val frac = pos - lo
        return sorted[lo] + frac * (sorted[hi] - sorted[lo])
    }

    private fun computeIqrSorted(sorted: List<Double>): Double {
        return when {
            sorted.size < 2 -> 0.0
            sorted.size < 4 -> 0.5 * (sorted.last() - sorted.first())
            else -> max(0.0, computePercentileSorted(sorted, 0.75) - computePercentileSorted(sorted, 0.25))
        }
    }

    private fun computeInterquartileTrimmedMeanSorted(sorted: List<Double>): Double {
        if (sorted.size < 4) return computeMedianSorted(sorted)
        val q1 = computePercentileSorted(sorted, 0.25)
        val q3 = computePercentileSorted(sorted, 0.75)
        var sum = 0.0
        var count = 0
        for (v in sorted) {
            if (v >= q1 && v <= q3) {
                sum += v
                count++
            }
        }
        return if (count > 0) sum / count else computeMedianSorted(sorted)
    }
}
