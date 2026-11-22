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

package com.atrainingtracker.trainingtracker.util

import com.atrainingtracker.trainingtracker.ui.util.NumericalEncodingUtils
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil

/**
 * Stateless, parameterized calculator consolidating spatial bounding-box extraction, polyline
 * simplification/encoding, scalar stream downsampling, and ascent/descent aggregation across
 * workout finalization, altitude shift, cropping, legacy import, ERTS recomputation, routes,
 * and segments ({@code REQ-DAT-047.4}, {@code ATT-6036}).
 *
 * <p>Resides in {@code com.atrainingtracker.trainingtracker.util} ({@code docs/coding_standards.md} §1.1)
 * so persistence-layer callers avoid upward {@code database -> ui.*} imports.</p>
 */
object StreamSummaryCalculator {

    const val DEFAULT_ENCODING_STEP_SIZE = 20
    const val DEFAULT_POLYLINE_SIMPLIFY_TOLERANCE_M = 10.0
    const val DEFAULT_MOVING_AVERAGE_WINDOW = 300
    const val DEFAULT_LEGACY_STRIDE = 5

    /**
     * Geographic axis-aligned bounding box in WGS-84 degrees.
     */
    data class GeoBounds(
        val minLat: Double,
        val minLng: Double,
        val maxLat: Double,
        val maxLng: Double
    )

    /**
     * Aggregated cumulative ascent and descent in meters.
     */
    data class AscentDescent(
        val ascending: Double,
        val descending: Double
    )

    /**
     * Downsampled altitude and distance stream pair produced with distance-weighted interpolation
     * for sparse altitude gaps (used by {@code WorkoutRepository.cropWorkout}).
     */
    data class InterpolatedAltitudeDistanceStreams(
        val sampledAltitudes: List<Double>,
        val sampledDistances: List<Double>
    )

    /**
     * Computes the spatial bounding box for [points], or returns {@code null} if empty.
     */
    @JvmStatic
    fun computeBounds(points: List<LatLng>?): GeoBounds? {
        if (points.isNullOrEmpty()) return null
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        var minLng = Double.POSITIVE_INFINITY
        var maxLng = Double.NEGATIVE_INFINITY
        for (p in points) {
            if (p.latitude < minLat) minLat = p.latitude
            if (p.latitude > maxLat) maxLat = p.latitude
            if (p.longitude < minLng) minLng = p.longitude
            if (p.longitude > maxLng) maxLng = p.longitude
        }
        return GeoBounds(minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng)
    }

    /**
     * Decodes [polyline] via [PolyUtil.decode] and computes its [GeoBounds], or returns {@code null}
     * if [polyline] is null, blank, or decodes to zero points.
     */
    @JvmStatic
    fun computeBoundsFromPolyline(polyline: String?): GeoBounds? {
        if (polyline.isNullOrEmpty()) return null
        return computeBounds(PolyUtil.decode(polyline))
    }

    /**
     * Computes cumulative positive and negative altitude deltas across non-null adjacent samples
     * (matches {@code WorkoutSummariesDatabaseManager.shiftAltitudeData} Case A & Case B).
     */
    @JvmStatic
    fun computeAscentDescentRaw(altitudes: List<Double?>?): AscentDescent {
        if (altitudes == null || altitudes.size < 2) {
            return AscentDescent(0.0, 0.0)
        }
        var ascending = 0.0
        var descending = 0.0
        for (i in 1 until altitudes.size) {
            val prev = altitudes[i - 1]
            val curr = altitudes[i]
            if (prev != null && curr != null) {
                val diff = curr - prev
                if (diff > 0.0) {
                    ascending += diff
                } else if (diff < 0.0) {
                    descending += -diff
                }
            }
        }
        return AscentDescent(ascending = ascending, descending = descending)
    }

    /**
     * Computes cumulative ascent and descent using a sliding moving-average window of [windowSize]
     * samples ({@code ATT-301}, default 300 samples = 5 minutes @ 1 Hz, matching
     * {@code LegacyImportEngine.recalculateStats}).
     */
    @JvmStatic
    @JvmOverloads
    fun computeAscentDescentMovingAverage(
        altitudes: List<Double>?,
        windowSize: Int = DEFAULT_MOVING_AVERAGE_WINDOW
    ): AscentDescent {
        if (altitudes.isNullOrEmpty()) {
            return AscentDescent(0.0, 0.0)
        }
        val effectiveWindow = windowSize.coerceAtLeast(1)
        var ascent = 0.0
        var descent = 0.0
        var windowSum = 0.0
        val windowQueue = java.util.ArrayDeque<Double>(effectiveWindow + 1)
        var lastFiltered: Double? = null

        for (rawAlt in altitudes) {
            windowSum += rawAlt
            windowQueue.addLast(rawAlt)
            if (windowQueue.size > effectiveWindow) {
                windowSum -= windowQueue.removeFirst()
            }
            val filtered = windowSum / windowQueue.size
            val prevFiltered = lastFiltered
            if (prevFiltered != null) {
                val delta = filtered - prevFiltered
                if (delta > 0.0) {
                    ascent += delta
                } else if (delta < 0.0) {
                    descent += -delta
                }
            }
            lastFiltered = filtered
        }
        return AscentDescent(ascending = ascent, descending = descent)
    }

    /**
     * Subsamples [values] at every [stepSize]-th index, optionally retaining the final element
     * (matches {@code ErtsPostWorkoutRecomputer.subsampleStream}).
     */
    @JvmStatic
    @JvmOverloads
    fun subsampleStream(
        values: List<Double>,
        stepSize: Int = DEFAULT_ENCODING_STEP_SIZE,
        includeLast: Boolean = true
    ): List<Double> {
        if (values.isEmpty() || stepSize <= 1) return values
        val lastIdx = values.lastIndex
        val result = ArrayList<Double>((values.size / stepSize) + 2)
        for (i in values.indices) {
            if (i % stepSize == 0 || (includeLast && i == lastIdx)) {
                result.add(values[i])
            }
        }
        return result
    }

    /**
     * Subsamples [values] using the legacy 1-based stride rule {@code (index + 1) % stride == 0}
     * whenever {@code values.size > stride}, otherwise returning [values] verbatim
     * (matches {@code LegacyImportEngine.recalculateStats}).
     */
    @JvmStatic
    @JvmOverloads
    fun subsampleLegacyStride(
        values: List<Double>,
        stride: Int = DEFAULT_LEGACY_STRIDE
    ): List<Double> {
        if (stride <= 1 || values.size <= stride) return values
        return values.filterIndexed { index, _ -> (index + 1) % stride == 0 }
    }

    /**
     * Downsamples parallel [altitudes] and [distances] arrays at [stepSize] (always including the
     * last index), linearly interpolating missing ({@code null} or {@code NaN}) altitudes by
     * distance when at least one finite altitude sample exists (matches {@code WorkoutRepository.cropWorkout}).
     */
    @JvmStatic
    @JvmOverloads
    fun subsampleWithAltitudeInterpolation(
        altitudes: List<Double?>,
        distances: List<Double>,
        distanceOrigin: Double = 0.0,
        stepSize: Int = DEFAULT_ENCODING_STEP_SIZE
    ): InterpolatedAltitudeDistanceStreams {
        require(altitudes.size == distances.size) {
            "altitudes (${altitudes.size}) and distances (${distances.size}) must have identical length"
        }
        if (altitudes.isEmpty()) {
            return InterpolatedAltitudeDistanceStreams(emptyList(), emptyList())
        }
        val effectiveStep = stepSize.coerceAtLeast(1)
        val hasAnyAltitude = altitudes.any { it != null && !it.isNaN() }
        val sampledAltitudes = mutableListOf<Double>()
        val sampledDistances = mutableListOf<Double>()
        val lastIndex = altitudes.lastIndex

        if (hasAnyAltitude) {
            for (i in altitudes.indices) {
                if (i % effectiveStep == 0 || i == lastIndex) {
                    val rawAlt = altitudes[i]
                    val alt = if (rawAlt != null && !rawAlt.isNaN()) {
                        rawAlt
                    } else {
                        var prevAlt: Double? = null
                        var prevDist = 0.0
                        for (k in i - 1 downTo 0) {
                            val a = altitudes[k]
                            if (a != null && !a.isNaN()) {
                                prevAlt = a
                                prevDist = distances[k]
                                break
                            }
                        }
                        var nextAlt: Double? = null
                        var nextDist = 0.0
                        for (k in i + 1 until altitudes.size) {
                            val a = altitudes[k]
                            if (a != null && !a.isNaN()) {
                                nextAlt = a
                                nextDist = distances[k]
                                break
                            }
                        }
                        when {
                            prevAlt != null && nextAlt != null -> {
                                val distSpan = nextDist - prevDist
                                if (distSpan > 1e-6) {
                                    prevAlt + (nextAlt - prevAlt) * ((distances[i] - prevDist) / distSpan)
                                } else {
                                    prevAlt
                                }
                            }
                            prevAlt != null -> prevAlt
                            nextAlt != null -> nextAlt
                            else -> 0.0
                        }
                    }
                    sampledAltitudes.add(alt)
                    sampledDistances.add((distances[i] - distanceOrigin).coerceAtLeast(0.0))
                }
            }
        } else {
            for (i in distances.indices) {
                if (i % effectiveStep == 0 || i == lastIndex) {
                    sampledDistances.add((distances[i] - distanceOrigin).coerceAtLeast(0.0))
                }
            }
        }
        return InterpolatedAltitudeDistanceStreams(
            sampledAltitudes = sampledAltitudes,
            sampledDistances = sampledDistances
        )
    }

    /**
     * Optionally applies Douglas-Peucker simplification ([simplifyToleranceMeters] > 0) and
     * Google-encodes [points], returning {@code null} when [points] is null or empty.
     */
    @JvmStatic
    @JvmOverloads
    fun encodePolyline(
        points: List<LatLng>?,
        simplifyToleranceMeters: Double? = null
    ): String? {
        if (points.isNullOrEmpty()) return null
        val target = if (simplifyToleranceMeters != null && simplifyToleranceMeters > 0.0) {
            PolyUtil.simplify(points, simplifyToleranceMeters)
        } else {
            points
        }
        return if (target.isNotEmpty()) PolyUtil.encode(target) else null
    }

    /**
     * Encodes [values] via [NumericalEncodingUtils.encodeDoubles], returning {@code null} when
     * [values] is null or empty.
     */
    @JvmStatic
    fun encodeDoubleStream(values: List<Double>?): String? {
        if (values.isNullOrEmpty()) return null
        return NumericalEncodingUtils.encodeDoubles(values)
    }

    /**
     * Encodes [values] via [NumericalEncodingUtils.encodeDoubles], returning {@code ""} when
     * [values] is null or empty (matching {@code TrackerService.finalizeLiveSession}).
     */
    @JvmStatic
    fun encodeDoubleStreamOrEmpty(values: List<Double>?): String {
        if (values.isNullOrEmpty()) return ""
        return NumericalEncodingUtils.encodeDoubles(values)
    }
}
