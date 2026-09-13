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

package com.atrainingtracker.trainingtracker.ui.segments.segmentlist

import androidx.compose.runtime.Immutable
import com.atrainingtracker.trainingtracker.segments.SegmentWithPath
import org.json.JSONObject

/**
 * Immutable domain model representing multi-dimensional filter criteria for starred segment lists.
 *
 * Holds filter values for free-text search (name/city), minimum Strava climb category,
 * distance and elevation gain thresholds, and has-PR status.
 * Provides high-performance predicate evaluation via [matches] and lightweight JSON serialization
 * for asynchronous persistence via DataStore in [MyPreferenceManager].
 *
 * ### Climb Category Semantic
 * When [minClimbCategory] is active (non-null), segments with [climbCategory_raw == 0]
 * (Strava "uncategorized") are **always excluded**, regardless of threshold value.
 * This mirrors the Strava UI convention where category 0 is not a ranked climb.
 */
@Immutable
data class SegmentFilterCriteria(
    val query: String = "",
    val minClimbCategory: Int? = null,
    val minDistanceMeters: Double? = null,
    val minElevationGainMeters: Double? = null,
    val hasPR: Boolean? = null
) {
    /**
     * Total count of distinct active filter dimensions.
     */
    val activeFilterCount: Int
        get() {
            var count = 0
            if (query.isNotBlank()) count++
            if (minClimbCategory != null) count++
            if (minDistanceMeters != null) count++
            if (minElevationGainMeters != null) count++
            if (hasPR != null) count++
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
     * Evaluates whether a given [SegmentWithPath] satisfies all active filter criteria.
     *
     * Performs sequential short-circuiting checks against all non-null / non-empty filter
     * dimensions. Returns true if and only if every active filter dimension matches the segment.
     *
     * @param segment The segment to test.
     * @return True if the segment matches all active criteria; false otherwise.
     */
    fun matches(segment: SegmentWithPath): Boolean {
        // Text Query match (case-insensitive substring across segment name and city)
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            val matchesName = segment.summary.name.lowercase().contains(q)
            val matchesCity = segment.summary.city.lowercase().contains(q)
            if (!matchesName && !matchesCity) return false
        }

        // Minimum Climb Category filter
        // Strava climbCategory_raw == 0 means "uncategorized" — always excluded when filter active
        if (minClimbCategory != null) {
            val raw = segment.summary.climbCategory_raw
            if (raw == 0 || raw < minClimbCategory) return false
        }

        // Minimum Distance threshold (meters)
        if (minDistanceMeters != null && segment.summary.distance_raw < minDistanceMeters) {
            return false
        }

        // Minimum Elevation Gain threshold (meters)
        if (minElevationGainMeters != null && segment.summary.elevationGain_raw < minElevationGainMeters) {
            return false
        }

        // Has PR filter (prTime_raw > 0 means the user holds a personal record on this segment)
        if (hasPR == true && segment.summary.prTime_raw <= 0) {
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
        minClimbCategory?.let { json.put("minClimbCategory", it) }
        minDistanceMeters?.let { json.put("minDistanceMeters", it) }
        minElevationGainMeters?.let { json.put("minElevationGainMeters", it) }
        hasPR?.let { json.put("hasPR", it) }
        return json.toString()
    }

    companion object {
        /**
         * Reconstructs a [SegmentFilterCriteria] instance from its persisted JSON representation.
         * Returns an empty (default) criteria instance if [jsonStr] is null, empty, or unparseable.
         */
        fun fromJson(jsonStr: String?): SegmentFilterCriteria {
            if (jsonStr.isNullOrBlank()) return SegmentFilterCriteria()
            return try {
                val json = JSONObject(jsonStr)
                SegmentFilterCriteria(
                    query = json.optString("query", ""),
                    minClimbCategory = if (json.has("minClimbCategory")) json.getInt("minClimbCategory") else null,
                    minDistanceMeters = if (json.has("minDistanceMeters")) json.getDouble("minDistanceMeters") else null,
                    minElevationGainMeters = if (json.has("minElevationGainMeters")) json.getDouble("minElevationGainMeters") else null,
                    hasPR = if (json.has("hasPR")) json.getBoolean("hasPR") else null
                )
            } catch (_: Exception) {
                SegmentFilterCriteria()
            }
        }
    }
}
