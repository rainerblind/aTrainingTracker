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
import com.atrainingtracker.trainingtracker.ui.util.MigrationStatus
import java.time.LocalDate

/**
 * Daily activity aggregation for a single calendar day and sport type.
 */
data class DailySportActivity(
    val date: LocalDate,
    val bSportType: BSportType,
    val count: Int,
    val durationSec: Long,
    val distanceMeters: Double,
    val ascentMeters: Long,
    val workoutIds: List<Long> = emptyList(),
    val workoutNames: List<String> = emptyList()
)

/**
 * Yearly activity contributions for a single sport type across a calendar year.
 * Encapsulates daily activities and max daily metric values for calibrated quartile shading.
 */
data class YearlySportContribution(
    val sportType: BSportType,
    val year: Int,
    val dailyActivities: Map<LocalDate, DailySportActivity> = emptyMap(),
    val maxDailyDurationSec: Long = 0L,
    val maxDailyDistanceMeters: Double = 0.0,
    val maxDailyAscentMeters: Long = 0L
)

/**
 * Daily activity contributions for a single sport type across a week or month (REQ-PER-027, ATT-5195).
 * Encapsulates daily activities and max daily metric values for proportional mini-bar scaling.
 */
data class DailySportContribution(
    val sportType: BSportType,
    val dailyActivities: Map<LocalDate, DailySportActivity> = emptyMap(),
    val maxDailyDurationSec: Long = 0L,
    val maxDailyDistanceMeters: Double = 0.0,
    val maxDailyAscentMeters: Long = 0L
)

/**
 * Safety threshold for rendering detailed vector tracks and markers in Period Maps.
 * When a period contains more than this number of workouts, individual vector tracks
 * and technical markers are suppressed in favor of the aggregate raster Heatmap.
 * (REQ-PER-012)
 */
const val MAX_PERIOD_VECTOR_TRACKS = 30

/**
 * Hard vertex budget for period summary card mini-map previews (REQ-PER-019.2 / ATT-4930).
 * Bounds aggregate memory consumption and avoids LazyColumn scroll jank across cards.
 */
const val MAX_CARD_MINI_MAP_VERTICES = 2_000

/**
 * Hard vertex budget for detailed interactive period map screens (REQ-PER-019.2 / ATT-4930).
 * Bounds aggregate memory consumption when viewing high-volume periods (e.g. Year tab).
 */
const val MAX_DETAILED_MAP_VERTICES = 8_000

/**
 * Maximum number of workout vector tracks hydrated for summary card thumbnail previews.
 * Balances rich route visualization against memory consumption in LazyColumn.
 * (ATT-1151 / REQ-PER-012)
 */
const val MAX_SUMMARY_THUMBNAIL_TRACKS = 8

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
    val minLat: Double = 90.0,
    val minLng: Double = 180.0,
    val maxLat: Double = -90.0,
    val maxLng: Double = -180.0,
    val longestId: Long = -1L,
    val longestDurationS: Long = 0,
    val northId: Long = -1L,
    val southId: Long = -1L,
    val eastId: Long = -1L,
    val westId: Long = -1L,

    val polylines: List<String> = emptyList(), // List of encoded polylines for the map
    val workoutIdToPolylineMap: Map<Long, String> = emptyMap(), // ID -> Encoded Polyline
    val workoutIdToAllPolylinesMap: Map<Long, String> = emptyMap(), // ID -> Encoded Polyline for all workouts (ATT-4770)
    val workoutIdToPathMap: Map<Long, List<com.google.android.gms.maps.model.LatLng>> = emptyMap(), // ID -> Rich Path
    val workoutIdToSportMap: Map<Long, BSportType> = emptyMap(),
    val extremaMarkers: List<PeriodPeakMarker> = emptyList(),
    val yearlySportContributions: Map<BSportType, YearlySportContribution> = emptyMap(),
    val dailySportContributions: Map<BSportType, DailySportContribution> = emptyMap(),
    val sortKey: String // Used for sorting
)

/**
 * Resolves the localized date range dynamically for weekly periods according to [locale],
 * falling back to stored [periodDateRange] (REQ-PER-028.2).
 */
fun PeriodSummary.getLocalizedDateRange(locale: java.util.Locale = java.util.Locale.getDefault()): String {
    if (periodType == PeriodType.WEEK && startTimestampS > 0 && endTimestampS > 0) {
        val formatted = com.atrainingtracker.trainingtracker.ui.common.DateTimeUtils.formatPeriodDateRange(
            startTimestampS,
            endTimestampS,
            locale
        )
        if (formatted.isNotBlank()) return formatted
    }
    return periodDateRange
}

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
    var longestWorkout: LongestWorkout?
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