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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import androidx.compose.runtime.Immutable
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import org.json.JSONObject

/**
 * Immutable domain model representing multi-dimensional filter criteria for workout lists.
 *
 * Holds filter values for free-text search, temporal ranges, sport subtypes, gear assignments,
 * workout flags (commute, trainer, GPS presence), and numerical distance/duration thresholds.
 * Provides high-performance predicate evaluation via [matches] and lightweight JSON serialization
 * for asynchronous preference persistence.
 */
@Immutable
data class WorkoutFilterCriteria(
    val query: String = "",
    val year: Int? = null,
    val month: Int? = null,
    val startDateS: Long? = null,
    val endDateS: Long? = null,
    val sportTypeId: Long? = null,
    val equipmentId: Long? = null,
    val isCommute: Boolean? = null,
    val isTrainer: Boolean? = null,
    val hasGpsTrack: Boolean? = null,
    val minDistanceMeters: Double? = null,
    val minDurationSec: Long? = null
) {
    /**
     * Total count of distinct active filter dimensions.
     */
    val activeFilterCount: Int
        get() {
            var count = 0
            if (query.isNotBlank()) count++
            if (year != null || month != null || startDateS != null || endDateS != null) count++
            if (sportTypeId != null) count++
            if (equipmentId != null) count++
            if (isCommute != null) count++
            if (isTrainer != null) count++
            if (hasGpsTrack == true) count++
            if (minDistanceMeters != null) count++
            if (minDurationSec != null) count++
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
     * Functional Description: Evaluates whether a given [WorkoutData] session satisfies all active filter criteria.
     *
     * Implementation Logic: Performs sequential short-circuiting checks against all non-null / non-empty filter
     * dimensions. Returns true if and only if every active filter dimension matches the session.
     *
     * @param workout The workout session to test.
     * @return True if the workout matches all active criteria; false otherwise.
     */
    fun matches(workout: WorkoutData): Boolean {
        // Text Query match (case-insensitive substring across title, notes, description, goal, method, sport, gear)
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            val matchesTitle = workout.workoutName.lowercase().contains(q)
            val matchesDesc = workout.description?.lowercase()?.contains(q) == true
            val matchesGoal = workout.goal?.lowercase()?.contains(q) == true
            val matchesMethod = workout.method?.lowercase()?.contains(q) == true
            val matchesSport = workout.sportName.lowercase().contains(q)
            val matchesEquip = workout.equipmentName?.lowercase()?.contains(q) == true
            if (!matchesTitle && !matchesDesc && !matchesGoal && !matchesMethod && !matchesSport && !matchesEquip) {
                return false
            }
        }

        // Year filter
        if (year != null && workout.localDateTime.year != year) {
            return false
        }

        // Month filter (1..12)
        if (month != null && workout.localDateTime.monthValue != month) {
            return false
        }

        // Date range
        if (startDateS != null && workout.startTimeS < startDateS) {
            return false
        }
        if (endDateS != null && workout.startTimeS > endDateS) {
            return false
        }

        // Specific Sport ID
        if (sportTypeId != null && workout.sportId != sportTypeId) {
            return false
        }

        // Specific Equipment ID
        if (equipmentId != null && workout.equipmentId != equipmentId) {
            return false
        }

        // Commute flag
        if (isCommute != null && workout.commute != isCommute) {
            return false
        }

        // Trainer / Indoor flag
        if (isTrainer != null && workout.trainer != isTrainer) {
            return false
        }

        // Has GPS track
        if (hasGpsTrack == true && workout.mapPolyline.isEmpty()) {
            return false
        }

        // Minimum Distance threshold (meters)
        if (minDistanceMeters != null && workout.totalDistance < minDistanceMeters) {
            return false
        }

        // Minimum Active Duration threshold (seconds)
        if (minDurationSec != null && workout.activeTimeSec < minDurationSec) {
            return false
        }

        return true
    }

    /**
     * Serializes this filter criteria object to a JSON string for preference persistence.
     *
     * @return JSON string representation.
     */
    fun toJson(): String {
        val json = JSONObject()
        if (query.isNotBlank()) json.put("query", query)
        year?.let { json.put("year", it) }
        month?.let { json.put("month", it) }
        startDateS?.let { json.put("startDateS", it) }
        endDateS?.let { json.put("endDateS", it) }
        sportTypeId?.let { json.put("sportTypeId", it) }
        equipmentId?.let { json.put("equipmentId", it) }
        isCommute?.let { json.put("isCommute", it) }
        isTrainer?.let { json.put("isTrainer", it) }
        hasGpsTrack?.let { json.put("hasGpsTrack", it) }
        minDistanceMeters?.let { json.put("minDistanceMeters", it) }
        minDurationSec?.let { json.put("minDurationSec", it) }
        return json.toString()
    }

    companion object {
        /**
         * Deserializes a [WorkoutFilterCriteria] instance from a JSON string.
         *
         * @param jsonStr Serialized JSON string, or null/empty.
         * @return Parsed [WorkoutFilterCriteria] or default empty criteria if parsing fails.
         */
        fun fromJson(jsonStr: String?): WorkoutFilterCriteria {
            if (jsonStr.isNullOrBlank()) return WorkoutFilterCriteria()
            return try {
                val json = JSONObject(jsonStr)
                WorkoutFilterCriteria(
                    query = json.optString("query", ""),
                    year = if (json.has("year")) json.optInt("year") else null,
                    month = if (json.has("month")) json.optInt("month") else null,
                    startDateS = if (json.has("startDateS")) json.optLong("startDateS") else null,
                    endDateS = if (json.has("endDateS")) json.optLong("endDateS") else null,
                    sportTypeId = if (json.has("sportTypeId")) json.optLong("sportTypeId") else null,
                    equipmentId = if (json.has("equipmentId")) json.optLong("equipmentId") else null,
                    isCommute = if (json.has("isCommute")) json.optBoolean("isCommute") else null,
                    isTrainer = if (json.has("isTrainer")) json.optBoolean("isTrainer") else null,
                    hasGpsTrack = if (json.has("hasGpsTrack")) json.optBoolean("hasGpsTrack") else null,
                    minDistanceMeters = if (json.has("minDistanceMeters")) json.optDouble("minDistanceMeters") else null,
                    minDurationSec = if (json.has("minDurationSec")) json.optLong("minDurationSec") else null
                )
            } catch (e: Exception) {
                WorkoutFilterCriteria()
            }
        }
    }
}
