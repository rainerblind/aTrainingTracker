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

package com.atrainingtracker.trainingtracker.ui.utils

/**
 * Provides high-efficiency delta-encoding for numerical streams and geographical paths.
 *
 * This utility implements an algorithm similar to Google's Encoded Polyline Algorithm Format,
 * but specialized for [Double] streams (Altitudes, Distances) and incremental tracking updates.
 *
 * It maximizes database storage efficiency by:
 * 1. **Fixed-Point Conversion**: Multiplying values by a precision factor (e.g., 100 for cm-precision).
 * 2. **Delta Encoding**: Only storing the difference between consecutive points.
 * 3. **Variable-Length Base64**: Using a 7-bit ASCII representation to minimize string length.
 *
 * Architectural Role: Data compression utility for persistent streams.
 */
/**
 * Provides high-efficiency delta-encoding for numerical streams and geographical paths.
 *
 * This utility implements an algorithm similar to Google's Encoded Polyline Algorithm Format,
 * but specialized for [Double] streams (Altitudes, Distances) and incremental tracking updates.
 *
 * It maximizes database storage efficiency by:
 * 1. **Fixed-Point Conversion**: Multiplying values by a precision factor (e.g., 100 for cm-precision).
 * 2. **Delta Encoding**: Only storing the difference between consecutive points.
 * 3. **Variable-Length Base64**: Using a 7-bit ASCII representation to minimize string length.
 *
 * Architectural Role: Data compression utility for persistent streams.
 */
object NumericalEncodingUtils {
    /**
     * Encodes a list of doubles (like altitudes or distances) into a compact String.
     * It uses delta encoding (storing the difference between points) to maximize compression.
     */
    fun encodeDoubles(numbers: List<Double>): String {
        val result = StringBuilder()
        var lastValue = 0L

        for (num in numbers) {
            // Convert to fixed-point (e.g., 2 decimal places precision)
            val current = Math.round(num * 100)
            encodeSingle(current - lastValue, result)
            lastValue = current
        }
        return result.toString()
    }

    /**
     * Helper to encode a single delta value.
     */
    fun encodeSingle(delta: Long, result: StringBuilder) {
        var b = if (delta < 0) (delta shl 1).inv() else delta shl 1
        while (b >= 0x20) {
            result.append(((0x20 or (b.toInt() and 0x1f)) + 63).toChar())
            b = b shr 5
        }
        result.append((b + 63).toInt().toChar())
    }

    /**
     * Encodes a single LatLng point incrementally.
     * @param lastLatE5 The last latitude multiplied by 1e5
     * @param lastLngE5 The last longitude multiplied by 1e5
     * @return The encoded string for this point alone.
     */
    fun encodeLatLng(lat: Double, lng: Double, lastLatE5: Long, lastLngE5: Long): String {
        val result = StringBuilder()
        val latE5 = Math.round(lat * 1e5)
        val lngE5 = Math.round(lng * 1e5)
        
        encodeSingle(latE5 - lastLatE5, result)
        encodeSingle(lngE5 - lastLngE5, result)

        return result.toString()
    }

    /**
     * Decodes a compressed string back into a list of [Double] values.
     *
     * Implementation: Iteratively parses the variable-length bit segments, reconstructs
     * the signed deltas, and integrates them into a running absolute value.
     */
    fun decodeDoubles(encoded: String): List<Double> {
        val result = mutableListOf<Double>()
        var index = 0
        var lastValue = 0L

        while (index < encoded.length) {
            var b: Int
            var shift = 0
            var resultValue = 0L
            do {
                b = encoded[index++].code - 63
                resultValue = resultValue or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20)

            val delta = if (resultValue and 1L != 0L) (resultValue shr 1).inv() else resultValue shr 1
            lastValue += delta
            result.add(lastValue / 100.0)
        }
        return result
    }
}