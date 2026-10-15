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

package com.atrainingtracker.trainingtracker.domain.crop

import com.google.android.gms.maps.model.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure domain math engine for cropping recorded workouts (ATT-3274 / REQ-UI-368).
 *
 * Encapsulates cumulative metric shifting with universal EPSILON clamping,
 * zero-anchoring of new start points, row-by-row Haversine line distance recalculation,
 * input sanitization, and non-linear sensor extrema re-derivation.
 */
object CropWorkoutMath {

    /**
     * Universal floating-point comparison and clamping epsilon.
     * Prevents IEEE-754 subtraction drift from causing negative metrics.
     */
    const val EPSILON = 1e-6

    /**
     * Batch size for executing SQLite update statements during time-series recalculations.
     */
    const val BATCH_SIZE = 1000

    /**
     * Minimum free storage headroom required before executing crop transactions (50 MB).
     */
    const val MIN_STORAGE_HEADROOM_BYTES = 50L * 1024L * 1024L

    /**
     * Multiplier for dynamic database file size storage pre-flight safety check.
     */
    const val STORAGE_DB_MULTIPLIER = 5

    /**
     * Earth radius in meters used for Haversine straight-line displacement calculations.
     */
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Encapsulates recalculated extrema metrics for an instantaneous telemetry stream.
     */
    data class CropExtremaResult(
        val min: Double,
        val max: Double,
        val avg: Double,
        val start: Double,
        val end: Double,
        val hasData: Boolean
    )

    /**
     * Zero-anchors row [startIndex] and shifts all subsequent cumulative double values
     * by subtracting baseline values at [startIndex], applying [EPSILON] clamping.
     *
     * @param values The full cumulative time-series stream.
     * @param startIndex The index of the new start sample row.
     * @return Shifted cumulative stream where values at or before [startIndex] are 0.0,
     *         and subsequent values with |v'| < EPSILON or v' < 0 are clamped to 0.0.
     */
    fun zeroAnchorAndShiftCumulative(values: List<Double>, startIndex: Int): List<Double> {
        if (values.isEmpty() || startIndex !in values.indices) return emptyList()
        val baseline = values[startIndex]
        return values.mapIndexed { index, value ->
            when {
                index < startIndex -> 0.0
                index == startIndex -> 0.0
                else -> {
                    val shifted = value - baseline
                    if (shifted < 0.0 || kotlin.math.abs(shifted) < EPSILON) 0.0 else shifted
                }
            }
        }
    }

    /**
     * Zero-anchors row [startIndex] and shifts all subsequent cumulative integer/long values
     * by subtracting baseline values at [startIndex], preventing negative counter values.
     */
    fun zeroAnchorAndShiftCumulativeLong(values: List<Long>, startIndex: Int): List<Long> {
        if (values.isEmpty() || startIndex !in values.indices) return emptyList()
        val baseline = values[startIndex]
        return values.mapIndexed { index, value ->
            when {
                index <= startIndex -> 0L
                else -> maxOf(0L, value - baseline)
            }
        }
    }

    /**
     * Recalculates straight-line Haversine displacement (LINE_DISTANCE_m) row-by-row
     * from the new start coordinate at [startIndex].
     *
     * For indoor or GPS-less sessions where coordinates are null, all rows are safely
     * anchored to 0.0 without throwing exceptions.
     */
    fun recalculateLineDistance(latLngs: List<LatLng?>, startIndex: Int): List<Double> {
        if (latLngs.isEmpty() || startIndex !in latLngs.indices) return emptyList()
        val startCoord = latLngs[startIndex] ?: return List(latLngs.size) { 0.0 }

        return latLngs.mapIndexed { index, coord ->
            when {
                index <= startIndex -> 0.0
                coord == null -> 0.0
                else -> haversineDistanceMeters(startCoord, coord)
            }
        }
    }

    /**
     * Pure Haversine formula distance between two coordinates in meters.
     */
    fun haversineDistanceMeters(start: LatLng, end: LatLng): Double {
        val dLat = Math.toRadians(end.latitude - start.latitude)
        val dLon = Math.toRadians(end.longitude - start.longitude)
        val lat1 = Math.toRadians(start.latitude)
        val lat2 = Math.toRadians(end.latitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distance = EARTH_RADIUS_METERS * c
        return if (distance < EPSILON) 0.0 else distance
    }

    /**
     * Strictly re-derives instantaneous sensor extrema (MIN, MAX, AVG, START, END)
     * exclusively from finite, non-null samples in the retained subset.
     *
     * If zero valid readings exist in the window (sensor disconnected or idle),
     * all fields return 0.0 with [CropExtremaResult.hasData] = false.
     */
    fun recalculateExtrema(values: List<Double?>): CropExtremaResult {
        val valid = values.filterNotNull().filter { java.lang.Double.isFinite(it) }
        if (valid.isEmpty()) {
            return CropExtremaResult(
                min = 0.0,
                max = 0.0,
                avg = 0.0,
                start = 0.0,
                end = 0.0,
                hasData = false
            )
        }

        return CropExtremaResult(
            min = valid.minOrNull() ?: 0.0,
            max = valid.maxOrNull() ?: 0.0,
            avg = valid.average(),
            start = valid.first(),
            end = valid.last(),
            hasData = true
        )
    }

    /**
     * Hard boundary guard evaluating whether a proposed crop selection is valid.
     *
     * Enforces:
     * 1. Valid index bounds within total recorded samples.
     * 2. Minimum handle step separation (endIndex - startIndex >= minIndexStep).
     * 3. At least 2 retained sample points.
     * 4. Cropped total duration >= 1.0 second.
     */
    fun isCropValid(
        startIndex: Int,
        endIndex: Int,
        totalPoints: Int,
        durationSec: Double,
        minIndexStep: Int = 1
    ): Boolean {
        if (totalPoints < 2) return false
        if (startIndex !in 0 until totalPoints) return false
        if (endIndex !in 0 until totalPoints) return false
        if (startIndex >= endIndex) return false
        if (endIndex - startIndex < minIndexStep) return false
        if ((endIndex - startIndex + 1) < 2) return false
        if (!java.lang.Double.isFinite(durationSec) || durationSec < 1.0) return false
        return true
    }

    /**
     * Computes average speed in meters per second with division-by-zero protection.
     */
    fun calculateAverageSpeed(distanceMeters: Double, activeTimeSec: Int): Double {
        if (activeTimeSec < 1 || !java.lang.Double.isFinite(distanceMeters) || distanceMeters <= 0.0) {
            return 0.0
        }
        val speed = distanceMeters / activeTimeSec.toDouble()
        return if (java.lang.Double.isFinite(speed)) speed else 0.0
    }

    /**
     * Calculates dynamic required storage headroom for pre-flight safety check.
     */
    fun calculateRequiredStorageBytes(dbFileSizeBytes: Long): Long {
        return maxOf(MIN_STORAGE_HEADROOM_BYTES, STORAGE_DB_MULTIPLIER * dbFileSizeBytes)
    }
}
