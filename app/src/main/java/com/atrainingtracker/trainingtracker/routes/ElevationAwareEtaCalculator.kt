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

package com.atrainingtracker.trainingtracker.routes

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Result data class for elevation-aware dynamic ETA predictions (REQ-MAP-029 / ATT-1953).
 */
data class EtaResult(
    val remainingDistanceMeters: Double,
    val remainingElevationGainMeters: Double,
    val durationSeconds: Double,
    val clockTimeMillis: Long,
    val formattedClockTime: String,
    val formattedDuration: String
)

/**
 * Standalone calculation engine for elevation-aware dynamic ETA predictions.
 *
 * Models athletic travel duration using Naismith's rule and VAM (Velocità Ascensionale Media):
 *   T_remaining = (D_remaining / V_flat) + (H_climb / VAM_climb)
 */
object ElevationAwareEtaCalculator {

    // Default flat speeds in m/s (20 km/h for bike, 10 km/h for run, 5 km/h for walk)
    const val DEFAULT_CYCLING_FLAT_SPEED_MPS = 20.0 / 3.6  // ~5.556 m/s
    const val DEFAULT_RUNNING_FLAT_SPEED_MPS = 10.0 / 3.6  // ~2.778 m/s
    const val DEFAULT_WALKING_FLAT_SPEED_MPS = 5.0 / 3.6   // ~1.389 m/s

    // Default VAM (vertical ascent rate) in m/s (500 m/h for bike, 400 m/h for run)
    const val DEFAULT_CYCLING_VAM_MPS = 500.0 / 3600.0     // ~0.1389 m/s
    const val DEFAULT_RUNNING_VAM_MPS = 400.0 / 3600.0     // ~0.1111 m/s
    const val DEFAULT_WALKING_VAM_MPS = 350.0 / 3600.0     // ~0.0972 m/s

    /**
     * Calculates the positive elevation gain along the remaining path segments.
     */
    @JvmStatic
    fun calculateClimbAlongPath(
        path: List<PathPoint>,
        startIndex: Int,
        tFraction: Double = 0.0
    ): Double {
        if (path.isEmpty() || startIndex >= path.size - 1) return 0.0

        var totalClimb = 0.0

        // 1. Initial segment interpolation
        val p1 = path[startIndex]
        val p2 = path[startIndex + 1]
        val interpolatedAlt = p1.altitude + tFraction.coerceIn(0.0, 1.0) * (p2.altitude - p1.altitude)
        if (p2.altitude > interpolatedAlt) {
            totalClimb += (p2.altitude - interpolatedAlt)
        }

        // 2. Subsequent segments
        for (i in (startIndex + 1) until (path.size - 1)) {
            val curr = path[i]
            val next = path[i + 1]
            val delta = next.altitude - curr.altitude
            if (delta > 0.0) {
                totalClimb += delta
            }
        }

        return totalClimb
    }

    /**
     * Calculates the positive elevation gain along the path in reverse direction (backtracking to start).
     */
    @JvmStatic
    fun calculateReverseClimbAlongPath(
        path: List<PathPoint>,
        endIndex: Int,
        tFraction: Double = 0.0
    ): Double {
        if (path.isEmpty() || endIndex < 0) return 0.0

        var totalClimb = 0.0

        // 1. Initial segment reverse interpolation (moving from interpolated point back to path[endIndex])
        if (endIndex < path.size - 1) {
            val p1 = path[endIndex]
            val p2 = path[endIndex + 1]
            val interpolatedAlt = p1.altitude + tFraction.coerceIn(0.0, 1.0) * (p2.altitude - p1.altitude)
            if (p1.altitude > interpolatedAlt) {
                totalClimb += (p1.altitude - interpolatedAlt)
            }
        }

        // 2. Preceding segments moving backwards: (i+1) -> i
        for (i in (endIndex - 1) downTo 0) {
            val fromPoint = path[i + 1]
            val toPoint = path[i]
            val delta = toPoint.altitude - fromPoint.altitude
            if (delta > 0.0) {
                totalClimb += delta
            }
        }

        return totalClimb
    }

    /**
     * Calculates the dynamic elevation-aware ETA.
     */
    @JvmStatic
    fun calculateEta(
        remainingDistanceMeters: Double,
        remainingClimbMeters: Double,
        currentSpeedMps: Double? = null,
        sportType: BSportType = BSportType.BIKE,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): EtaResult {
        val dist = remainingDistanceMeters.coerceAtLeast(0.0)
        val climb = remainingClimbMeters.coerceAtLeast(0.0)

        val defaultFlatSpeed = when (sportType) {
            BSportType.BIKE -> DEFAULT_CYCLING_FLAT_SPEED_MPS
            BSportType.RUN -> DEFAULT_RUNNING_FLAT_SPEED_MPS
            else -> DEFAULT_WALKING_FLAT_SPEED_MPS
        }

        val defaultVam = when (sportType) {
            BSportType.BIKE -> DEFAULT_CYCLING_VAM_MPS
            BSportType.RUN -> DEFAULT_RUNNING_VAM_MPS
            else -> DEFAULT_WALKING_VAM_MPS
        }

        val effectiveFlatSpeed = if (currentSpeedMps != null && currentSpeedMps > 1.5) {
            when (sportType) {
                BSportType.BIKE -> currentSpeedMps.coerceIn(3.0, 15.0)
                BSportType.RUN -> currentSpeedMps.coerceIn(1.5, 6.0)
                else -> currentSpeedMps.coerceIn(0.8, 3.0)
            }
        } else {
            defaultFlatSpeed
        }

        val tFlatSeconds = dist / effectiveFlatSpeed
        val tClimbSeconds = climb / defaultVam
        val totalDurationSeconds = tFlatSeconds + tClimbSeconds

        val arrivalClockMillis = currentTimeMillis + (totalDurationSeconds * 1000.0).roundToLong()

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val formattedClock = timeFormat.format(Date(arrivalClockMillis))

        val formattedDuration = formatDuration(totalDurationSeconds)

        return EtaResult(
            remainingDistanceMeters = dist,
            remainingElevationGainMeters = climb,
            durationSeconds = totalDurationSeconds,
            clockTimeMillis = arrivalClockMillis,
            formattedClockTime = formattedClock,
            formattedDuration = formattedDuration
        )
    }

    @JvmStatic
    fun formatDuration(durationSeconds: Double): String {
        val totalSecs = durationSeconds.roundToLong().coerceAtLeast(0L)
        val totalMinutes = (totalSecs + 30L) / 60L

        return when {
            totalMinutes < 1L -> "< 1 min"
            totalMinutes < 60L -> "~$totalMinutes min"
            else -> {
                val hours = totalMinutes / 60L
                val mins = totalMinutes % 60L
                if (mins == 0L) {
                    "~$hours h"
                } else {
                    "~$hours h $mins min"
                }
            }
        }
    }
}
