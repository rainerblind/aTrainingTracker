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

enum class PeriodMarkerType {
    ALTITUDE, DISTANCE, START, END
}

data class PeriodSummary(
    val periodLabel: String,         // e.g., "Week20, 2026" or "May 2026"
    val periodDateRange: String,     // e.g., "May 11 - May 17"
    val periodType: PeriodType,
    val startTimestampS: Long,       // Start of the period in seconds
    val endTimestampS: Long,         // End of the period in seconds
    val totalWorkouts: Int,
    val totalDurationSec: Long,
    val totalDistance: Double,
    val sportStats: Map<BSportType, SportStats>,
    
    // Fast Outline Metadata (ATT-346)
    val minLat: Double = 0.0,
    val minLng: Double = 0.0,
    val maxLat: Double = 0.0,
    val maxLng: Double = 0.0,
    val longestId: Long = -1L,
    val longestDurationS: Long = 0,
    val northId: Long = -1L,
    val southId: Long = -1L,
    val eastId: Long = -1L,
    val westId: Long = -1L,

    val polylines: List<String> = emptyList(), // List of encoded polylines for the map
    val workoutIdToPolylineMap: Map<Long, String> = emptyMap(), // ID -> Encoded Polyline
    val workoutIdToPathMap: Map<Long, List<com.google.android.gms.maps.model.LatLng>> = emptyMap(), // ID -> Rich Path
    val workoutIdToSportMap: Map<Long, BSportType> = emptyMap(),
    val extremaMarkers: List<PeriodPeakMarker> = emptyList(),
    val sortKey: String // Used for sorting
)

data class PeriodPeakMarker(
    val workoutId: Long,
    val pos: com.google.android.gms.maps.model.LatLng,
    val iconResId: Int,
    val title: String,
    val markerType: PeriodMarkerType
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