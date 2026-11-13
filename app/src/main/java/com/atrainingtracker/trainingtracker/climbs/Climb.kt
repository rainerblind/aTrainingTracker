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
import kotlin.math.cos

/**
 * Axis-aligned geographic bounding box in WGS-84 coordinates (REQ-CLM-011.1).
 * Provides O(1) spatial pre-filtering to prevent quadratic trigonometric operations in climb deduplication.
 *
 * @property minLat Minimum latitude boundary in degrees.
 * @property maxLat Maximum latitude boundary in degrees.
 * @property minLng Minimum longitude boundary in degrees.
 * @property maxLng Maximum longitude boundary in degrees.
 */
data class GeoBoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double
) {
    /**
     * Evaluates whether this bounding box intersects [other] when expanded by [marginMeters].
     * Uses equirectangular coordinate projection for fast O(1) boundary comparison.
     */
    fun overlaps(other: GeoBoundingBox, marginMeters: Double = 100.0): Boolean {
        val meanLat = (minLat + maxLat + other.minLat + other.maxLat) / 4.0
        val deltaLat = marginMeters / 111_000.0
        val cosLat = cos(Math.toRadians(meanLat)).coerceAtLeast(0.01)
        val deltaLng = marginMeters / (111_000.0 * cosLat)

        if (maxLat + deltaLat < other.minLat || other.maxLat + deltaLat < minLat) return false
        if (maxLng + deltaLng < other.minLng || other.maxLng + deltaLng < minLng) return false
        return true
    }
}

/**
 * Supported climb difficulty categorization formulas (REQ-CLM-010.1).
 */
enum class ClimbScoringFormula {
    /**
     * Open FIETS Index: (H^2 / (10 * D)) + max(0, (summitAlt - 1000) / 1000).
     * Weights both vertical gain, steepness gradient, and high-altitude difficulty. Default formula.
     */
    FIETS,

    /**
     * Traditional Strava / Garmin formula: distance * avgGrade = 100 * elevationGain.
     * Categorizes purely based on total vertical gain.
     */
    STRAVA_GARMIN
}

/**
 * Standard cycling climb category classifications according to UCI and FIETS formulas (REQ-MAP-027, REQ-CLM-010).
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

        fun calculateFietsIndex(
            distanceMeters: Double,
            elevationGainMeters: Double,
            summitAltitudeMeters: Double? = null
        ): Double {
            val base = if (distanceMeters > 0.0) {
                (elevationGainMeters * elevationGainMeters) / (10.0 * distanceMeters)
            } else {
                0.0
            }
            val altitudeBonus = if (summitAltitudeMeters != null && summitAltitudeMeters > 1000.0) {
                (summitAltitudeMeters - 1000.0) / 1000.0
            } else {
                0.0
            }
            return base + altitudeBonus
        }

        fun fromFietsIndex(
            distanceMeters: Double,
            elevationGainMeters: Double,
            summitAltitudeMeters: Double? = null
        ): ClimbCategory {
            val score = calculateFietsIndex(distanceMeters, elevationGainMeters, summitAltitudeMeters)
            return when {
                score >= 6.5 -> HC
                score >= 5.0 -> CAT_1
                score >= 3.5 -> CAT_2
                score >= 2.0 -> CAT_3
                score >= 0.5 -> CAT_4
                else -> UNCATEGORIZED
            }
        }

        fun fromFormula(
            formula: ClimbScoringFormula,
            distanceMeters: Double,
            elevationGainMeters: Double,
            avgGradePercent: Double,
            summitAltitudeMeters: Double? = null
        ): ClimbCategory {
            return when (formula) {
                ClimbScoringFormula.FIETS -> fromFietsIndex(distanceMeters, elevationGainMeters, summitAltitudeMeters)
                ClimbScoringFormula.STRAVA_GARMIN -> fromScore(distanceMeters * avgGradePercent)
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

    /**
     * Cached axis-aligned bounding box encompassing climb start, summit, and all path points (REQ-CLM-011.1).
     */
    val boundingBox: GeoBoundingBox by lazy {
        var minLa = minOf(startLat, endLat)
        var maxLa = maxOf(startLat, endLat)
        var minLn = minOf(startLng, endLng)
        var maxLn = maxOf(startLng, endLng)
        for (pt in pathPoints) {
            val la = pt.latLng.latitude
            val ln = pt.latLng.longitude
            if (la < minLa) minLa = la
            if (la > maxLa) maxLa = la
            if (ln < minLn) minLn = ln
            if (ln > maxLn) maxLn = ln
        }
        GeoBoundingBox(minLa, maxLa, minLn, maxLn)
    }

    /**
     * Sequence of path points normalized to local climb distance starting at 0.0 meters.
     * Preserves latLng, altitude, time, and telemetry fields intact (REQ-UI-338, ATT-3105).
     */
    val normalizedPathPoints: List<PathPoint>
        get() {
            if (pathPoints.isEmpty()) return emptyList()
            val startDist = pathPoints.first().distance
            if (startDist == 0.0) return pathPoints
            return pathPoints.map { it.copy(distance = (it.distance - startDist).coerceAtLeast(0.0)) }
        }

    /**
     * Dynamically resolves the climb category according to the selected [formula] (REQ-CLM-010.2).
     * Uses [distanceMeters], [elevationGainMeters], and the summit altitude from [pathPoints].
     */
    fun resolveCategory(formula: ClimbScoringFormula): ClimbCategory {
        val summitAlt = pathPoints.lastOrNull()?.altitude
        return ClimbCategory.fromFormula(formula, distanceMeters, elevationGainMeters, avgGradePercent, summitAlt)
    }
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
    val totalRouteClimbs: Int? = null,
    val lastSegmentIndex: Int? = null
)
