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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.atrainingtracker.R
import com.google.android.gms.maps.model.LatLng

/**
 * High-level categorization of waypoints along a route.
 */
enum class WaypointCategory {
    LANDMARK,
    TURN_CUE,
    HAZARD
}

/**
 * Standardized semantic types for route waypoints, POIs, and course points.
 *
 * Traceability:
 * - REQ-MAP-026: Support Waypoints, POIs (Benches, Water, Summits) and TCX Course Points.
 * - TST-MAP-028: Waypoint extraction, classification, and visualization verification.
 */
enum class WaypointType(
    val category: WaypointCategory,
    @DrawableRes val iconResId: Int,
    @StringRes val displayNameResId: Int
) {
    POI_BENCH(WaypointCategory.LANDMARK, R.drawable.ic_poi_bench, R.string.waypoint_type_bench),
    POI_WATER(WaypointCategory.LANDMARK, R.drawable.ic_poi_water, R.string.waypoint_type_water),
    POI_SUMMIT(WaypointCategory.LANDMARK, R.drawable.ic_poi_summit, R.string.waypoint_type_summit),
    POI_FOOD(WaypointCategory.LANDMARK, R.drawable.ic_poi_food, R.string.waypoint_type_food),
    POI_VIEWPOINT(WaypointCategory.LANDMARK, R.drawable.ic_poi_viewpoint, R.string.waypoint_type_viewpoint),
    POI_FIRST_AID(WaypointCategory.LANDMARK, R.drawable.ic_poi_first_aid, R.string.waypoint_type_first_aid),
    POI_DANGER(WaypointCategory.HAZARD, R.drawable.ic_poi_danger, R.string.waypoint_type_danger),
    TURN_LEFT(WaypointCategory.TURN_CUE, R.drawable.ic_turn_left, R.string.waypoint_type_turn_left),
    TURN_RIGHT(WaypointCategory.TURN_CUE, R.drawable.ic_turn_right, R.string.waypoint_type_turn_right),
    TURN_STRAIGHT(WaypointCategory.TURN_CUE, R.drawable.ic_turn_straight, R.string.waypoint_type_turn_straight),
    GENERIC(WaypointCategory.LANDMARK, R.drawable.ic_poi_generic, R.string.waypoint_type_generic);

    companion object {
        /**
         * Heuristically classifies GPX symbol, type, and name into a standard [WaypointType].
         */
        fun fromGpx(sym: String?, type: String?, name: String?, desc: String? = null): WaypointType {
            val text = "${sym ?: ""} ${type ?: ""} ${name ?: ""} ${desc ?: ""}".lowercase()
            return when {
                text.contains("water") || text.contains("wasser") || text.contains("quelle") || text.contains("fountain") || text.contains("trinkwasser") -> POI_WATER
                text.contains("bench") || text.contains("bank") || text.contains("rast") || text.contains("picnic") || text.contains("picknick") || text.contains("sitzgelegenheit") || text.contains("unterstand") -> POI_BENCH
                text.contains("summit") || text.contains("gipfel") || text.contains("peak") || text.contains("pass") || text.contains("kreuz") -> POI_SUMMIT
                text.contains("food") || text.contains("cafe") || text.contains("café") || text.contains("restaurant") || text.contains("bäcker") || text.contains("bakery") || text.contains("essen") || text.contains("gasthof") || text.contains("hütte") || text.contains("grillplatz") || text.contains("schutzhütte") -> POI_FOOD
                text.contains("danger") || text.contains("gefahr") || text.contains("hazard") || text.contains("caution") || text.contains("warnung") -> POI_DANGER
                text.contains("view") || text.contains("aussicht") || text.contains("panorama") || text.contains("lookout") || text.contains("blick") -> POI_VIEWPOINT
                text.contains("first aid") || text.contains("erste hilfe") || text.contains("hospital") || text.contains("arzt") -> POI_FIRST_AID
                text.contains("left") || text.contains("links") -> TURN_LEFT
                text.contains("right") || text.contains("rechts") -> TURN_RIGHT
                text.contains("straight") || text.contains("geradeaus") -> TURN_STRAIGHT
                else -> GENERIC
            }
        }

        /**
         * Maps Garmin TCX standard PointType strings into [WaypointType].
         */
        fun fromTcx(pointType: String?): WaypointType {
            return when (pointType?.trim()?.lowercase()) {
                "water" -> POI_WATER
                "food" -> POI_FOOD
                "summit" -> POI_SUMMIT
                "valley" -> POI_VIEWPOINT
                "danger" -> POI_DANGER
                "first aid" -> POI_FIRST_AID
                "left" -> TURN_LEFT
                "right" -> TURN_RIGHT
                "straight" -> TURN_STRAIGHT
                else -> GENERIC
            }
        }
    }
}

/**
 * Represents a geographical Point of Interest (POI) or course navigation cue along a route.
 *
 * @param id Unique database identifier.
 * @param routeId Foreign key referencing the parent route in Routes.db.
 * @param latLng Geographic coordinate.
 * @param altitude Elevation in meters.
 * @param name Localized or descriptive title.
 * @param description Detailed notes or comments.
 * @param type Classified [WaypointType].
 * @param distanceFromStart Cumulative distance along route path in meters.
 */
data class RouteWaypoint(
    val id: Long = 0L,
    val routeId: Long = 0L,
    val latLng: LatLng,
    val altitude: Double = 0.0,
    val name: String = "",
    val description: String = "",
    val type: WaypointType = WaypointType.GENERIC,
    val distanceFromStart: Double = 0.0
)
