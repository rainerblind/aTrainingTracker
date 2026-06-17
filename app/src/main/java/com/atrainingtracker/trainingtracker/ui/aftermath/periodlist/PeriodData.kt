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

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import com.atrainingtracker.banalservice.BSportType

enum class PeriodType {
    DAY, WEEK, MONTH, YEAR
}

data class PeriodSummary(
    val periodLabel: String,         // e.g., "Week20, 2026" or "May 2026"
    val periodDateRange: String,     // e.g., "May 11 - May 17"
    val periodType: PeriodType,
    val startTimestampS: Long,       // Start of the period in seconds
    val endTimestampS: Long,         // End of the period in seconds
    val totalWorkouts: Int,
    val totalDurationSec: Long,
    val sportStats: Map<BSportType, SportStats>,
    val polylines: List<String>, // List of encoded polylines for the map
    val workoutIdToPolylineMap: Map<Long, String>, // ID -> Encoded Polyline
    val workoutIdToSportMap: Map<Long, BSportType>,
    val sortKey: String // Used for sorting
)

data class SportStats(
    val count: Int,
    val totalDurationSec: Long,
    val totalDistanceMeters: Double,
    val totalAscentMeters: Long,
    val detailedSportStats: Map<String, DetailedStats>, // Key is sportName
    val longestWorkout: LongestWorkout?
)

data class DetailedStats(
    val count: Int,
    val totalDurationSec: Long,
    val totalDistanceMeters: Double,
    val totalAscentMeters: Long
)

data class LongestWorkout(
    val id: Long,
    val name: String,
    val durationSec: Long,
    val distanceMeters: Double,
    val ascentMeters: Long
)