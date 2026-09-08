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

package com.atrainingtracker.trainingtracker.ui.routes

import androidx.compose.runtime.Immutable
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import org.json.JSONObject

/**
 * Immutable domain model representing multi-dimensional filter criteria for route lists.
 *
 * Holds filter values for free-text search, route source/origin (Strava, Local GPX, Workout),
 * map selection/visibility flag, and numerical distance / elevation gain thresholds.
 * Provides high-performance predicate evaluation via [matches] and lightweight JSON serialization
 * for asynchronous preference persistence.
 */
@Immutable
data class RouteFilterCriteria(
    val query: String = "",
    val source: RouteSource? = null,
    val isSelected: Boolean? = null,
    val minDistanceMeters: Double? = null,
    val minElevationGainMeters: Double? = null
) {
    /**
     * Total count of distinct active filter dimensions.
     */
    val activeFilterCount: Int
        get() {
            var count = 0
            if (query.isNotBlank()) count++
            if (source != null) count++
            if (isSelected != null) count++
            if (minDistanceMeters != null) count++
            if (minElevationGainMeters != null) count++
            return count
        }

    /**
     * Whether no filter criteria are currently active.
     */
    val isEmpty: Boolean
        get() = activeFilterCount == 0

    /**
     * Whether at least one filter criterion is active.
     */
    val isNotEmpty: Boolean
        get() = !isEmpty

    /**
     * Evaluates whether a given [RouteWithPath] satisfies all active filter criteria.
     *
     * Performs sequential short-circuiting checks against all non-null / non-empty filter
     * dimensions. Returns true if and only if every active filter dimension matches the route.
     *
     * @param route The route to test.
     * @return True if the route matches all active criteria; false otherwise.
     */
    fun matches(route: RouteWithPath): Boolean {
        // Text Query match (case-insensitive substring across route name and description)
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            val matchesName = route.summary.name.lowercase().contains(q)
            val matchesDesc = route.summary.description.lowercase().contains(q)
            if (!matchesName && !matchesDesc) {
                return false
            }
        }

        // Route Source filter (Strava, Local GPX, Workout)
        if (source != null && route.summary.source != source) {
            return false
        }

        // Selection / Visibility on Map filter
        if (isSelected != null && route.summary.isSelected != isSelected) {
            return false
        }

        // Minimum Distance threshold (meters)
        if (minDistanceMeters != null && route.summary.distance < minDistanceMeters) {
            return false
        }

        // Minimum Elevation Gain threshold (meters)
        if (minElevationGainMeters != null && route.summary.elevationGain < minElevationGainMeters) {
            return false
        }

        return true
    }

    /**
     * Serializes this criteria instance into a compact JSON string for persistence in DataStore.
     */
    fun toJson(): String {
        val json = JSONObject()
        if (query.isNotBlank()) json.put("query", query)
        source?.let { json.put("source", it.name) }
        isSelected?.let { json.put("isSelected", it) }
        minDistanceMeters?.let { json.put("minDistanceMeters", it) }
        minElevationGainMeters?.let { json.put("minElevationGainMeters", it) }
        return json.toString()
    }

    companion object {
        /**
         * Reconstructs a [RouteFilterCriteria] instance from its persisted JSON representation.
         * Returns an empty criteria instance if [jsonStr] is null, empty, or unparseable.
         */
        fun fromJson(jsonStr: String?): RouteFilterCriteria {
            if (jsonStr.isNullOrBlank()) return RouteFilterCriteria()
            return try {
                val json = JSONObject(jsonStr)
                RouteFilterCriteria(
                    query = json.optString("query", ""),
                    source = if (json.has("source")) RouteSource.fromString(json.getString("source")) else null,
                    isSelected = if (json.has("isSelected")) json.getBoolean("isSelected") else null,
                    minDistanceMeters = if (json.has("minDistanceMeters")) json.getDouble("minDistanceMeters") else null,
                    minElevationGainMeters = if (json.has("minElevationGainMeters")) json.getDouble("minElevationGainMeters") else null
                )
            } catch (_: Exception) {
                RouteFilterCriteria()
            }
        }
    }
}
