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

package com.atrainingtracker.banalservice.devices

import java.util.ArrayDeque

/**
 * Causal non-linear outlier-rejecting filter for barometric altitude streams (REQ-TRK-057.1, ATT-6265).
 *
 * Rejects high-frequency pocket-compression pressure transients (±20m to ±96m, p95 = 20-29 m/s)
 * via a 3-stage causal pipeline:
 * 1. Sliding Median Window (size 7): Annihilates isolated 1-3s compression pulses before they trip ascent hysteresis.
 * 2. Physical Vertical Slew-Rate Limiter (1.2 m/s): Clamps step changes to physiological climb bounds.
 * 3. Exponential Moving Average (EMA, alpha = 0.18): Smooths residual sensor quantization noise.
 *
 * Invariant: Genuine mountain climbs up to 3,000 m/h (0.83 m/s) pass with zero gradient attenuation.
 */
class BarometricAltitudeFilter @JvmOverloads constructor(
    val maxSlewRateMps: Double = DEFAULT_MAX_SLEW_RATE_MPS,
    val medianWindowSize: Int = DEFAULT_MEDIAN_WINDOW_SIZE,
    val emaAlpha: Double = DEFAULT_EMA_ALPHA
) {

    companion object {
        const val DEFAULT_MAX_SLEW_RATE_MPS = 1.2
        const val DEFAULT_MEDIAN_WINDOW_SIZE = 7
        const val DEFAULT_EMA_ALPHA = 0.18
        private const val MAX_TIME_GAP_SEC = 10.0
    }

    private val window: ArrayDeque<Double> = ArrayDeque(medianWindowSize)
    private var lastSlewAltitude: Double = Double.NaN
    private var lastFilteredAltitude: Double = Double.NaN
    private var lastTimestampMs: Long = -1L

    /**
     * Filters a raw barometric altitude sample through the 3-stage non-linear pipeline.
     *
     * @param rawAltitude Altitude derived from barometric formula in meters.
     * @param timestampMs Fix epoch timestamp in milliseconds.
     * @return Outlier-rejected, rate-clamped, and smoothed altitude in meters.
     */
    @Synchronized
    fun filter(rawAltitude: Double, timestampMs: Long): Double {
        if (rawAltitude.isNaN() || rawAltitude.isInfinite()) {
            return if (!lastFilteredAltitude.isNaN()) lastFilteredAltitude else Double.NaN
        }

        if (lastFilteredAltitude.isNaN() || lastTimestampMs < 0L) {
            window.clear()
            repeat(medianWindowSize) { window.add(rawAltitude) }
            lastSlewAltitude = rawAltitude
            lastFilteredAltitude = rawAltitude
            lastTimestampMs = timestampMs
            return rawAltitude
        }

        val elapsedMs = timestampMs - lastTimestampMs
        val dtSec = if (elapsedMs >= 0) elapsedMs / 1000.0 else 1.0

        if (dtSec > MAX_TIME_GAP_SEC) {
            // Sensor gap: reinitialize to prevent slew-rate lag after long pause
            window.clear()
            repeat(medianWindowSize) { window.add(rawAltitude) }
            lastSlewAltitude = rawAltitude
            lastFilteredAltitude = rawAltitude
            lastTimestampMs = timestampMs
            return rawAltitude
        }

        // Stage 1: Sliding Median Window
        if (window.size >= medianWindowSize) {
            window.removeFirst()
        }
        window.addLast(rawAltitude)
        val medianValue = computeMedian(window)

        // Stage 2: Physical Vertical Slew-Rate Limiter
        val maxDelta = maxSlewRateMps * dtSec
        val delta = medianValue - lastSlewAltitude
        val clampedDelta = delta.coerceIn(-maxDelta, maxDelta)
        lastSlewAltitude += clampedDelta

        // Stage 3: Exponential Moving Average (EMA)
        val filtered = lastFilteredAltitude + emaAlpha * (lastSlewAltitude - lastFilteredAltitude)

        lastFilteredAltitude = filtered
        lastTimestampMs = timestampMs
        return filtered
    }

    @Synchronized
    fun getFilteredAltitude(): Double = lastFilteredAltitude

    @Synchronized
    fun isInitialized(): Boolean = !lastFilteredAltitude.isNaN()

    @Synchronized
    fun reset() {
        window.clear()
        lastSlewAltitude = Double.NaN
        lastFilteredAltitude = Double.NaN
        lastTimestampMs = -1L
    }

    private fun computeMedian(samples: Collection<Double>): Double {
        val sorted = samples.sorted()
        val size = sorted.size
        return if (size % 2 == 1) {
            sorted[size / 2]
        } else {
            (sorted[size / 2 - 1] + sorted[size / 2]) / 2.0
        }
    }
}
