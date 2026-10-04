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

package com.atrainingtracker.trainingtracker.climbs

import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng

/**
 * Standard cycling climb category classifications according to UCI climb score formulas.
 * (REQ-MAP-027).
 */
enum class ClimbCategory(val code: String, val minScore: Double) {
    HC("HC", 80000.0),
    CAT_1("1", 64000.0),
    CAT_2("2", 32000.0),
    CAT_3("3", 16000.0),
    CAT_4("4", 8000.0),
    UNCATEGORIZED("UC", 0.0);

    companion object {
        fun fromScore(score: Double): ClimbCategory {
            return when {
                score >= HC.minScore -> HC
                score >= CAT_1.minScore -> CAT_1
                score >= CAT_2.minScore -> CAT_2
                score >= CAT_3.minScore -> CAT_3
                score >= CAT_4.minScore -> CAT_4
                else -> UNCATEGORIZED
            }
        }

        fun fromCode(code: String): ClimbCategory {
            return entries.firstOrNull { it.name.equals(code, ignoreCase = true) || it.code.equals(code, ignoreCase = true) } ?: UNCATEGORIZED
        }
    }
}

/**
 * Lifecycle states of live climb tracking (REQ-MAP-027).
 */
enum class LiveClimbStatus {
    FAR_FAR_AWAY,
    APPROACHING,
    ON_CLIMB,
    FINISHED
}

/**
 * Represents a recognized geographical ascent/climb (REQ-MAP-027).
 *
 * @property id Unique identifier in the climbs database.
 * @property name Human-readable climb name (e.g. "Anstieg 1,8 km @ 6,2%").
 * @property routeId Associated route ID if extracted from a specific route, or null.
 * @property startLat Latitude of the climb start point.
 * @property startLng Longitude of the climb start point.
 * @property endLat Latitude of the summit / end point.
 * @property endLng Longitude of the summit / end point.
 * @property distanceMeters Total distance of the climb in meters.
 * @property elevationGainMeters Total vertical ascent in meters (Delta H).
 * @property avgGradePercent Average gradient in percent.
 * @property maxGradePercent Maximum gradient in percent.
 * @property category Categorization (HC, CAT_1, CAT_2, CAT_3, CAT_4, UNCATEGORIZED).
 * @property pathPoints The sequence of path points comprising the climb.
 */
data class Climb(
    val id: Long = 0L,
    val name: String,
    val routeId: Long? = null,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val avgGradePercent: Double,
    val maxGradePercent: Double,
    val category: ClimbCategory = ClimbCategory.UNCATEGORIZED,
    val pathPoints: List<PathPoint> = emptyList()
) {
    val startLatLng: LatLng get() = LatLng(startLat, startLng)
    val endLatLng: LatLng get() = LatLng(endLat, endLng)
}

/**
 * Dynamic live tracking telemetry for an active or approaching climb (REQ-MAP-027).
 *
 * @property climb The static climb reference.
 * @property status Current tracking state.
 * @property distanceToStart Distance from athlete to climb start in meters.
 * @property distanceToSummit Remaining distance from athlete to summit in meters.
 * @property remainingElevationGain Remaining vertical ascent in meters.
 * @property currentGradePercent Current instantaneous gradient in percent.
 * @property currentProgressFraction Progress fraction along climb [0.0..1.0].
 * @property routeIndex 1-based index if navigating along a route (e.g. 2 for "Anstieg 2 von 4").
 * @property totalRouteClimbs Total climbs on the active route.
 */
data class LiveClimbData(
    val climb: Climb,
    val status: LiveClimbStatus = LiveClimbStatus.FAR_FAR_AWAY,
    val distanceToStart: Double = 0.0,
    val distanceToSummit: Double = 0.0,
    val remainingElevationGain: Double = 0.0,
    val currentGradePercent: Double = 0.0,
    val currentProgressFraction: Float = 0.0f,
    val routeIndex: Int? = null,
    val totalRouteClimbs: Int? = null
)
