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

package com.atrainingtracker.trainingtracker.ui.clusters

import androidx.compose.runtime.Immutable
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import org.json.JSONObject

/**
 * Immutable domain model representing multi-dimensional filter criteria for Favorite Tracks / Workout Clusters.
 *
 * Encapsulates orthogonal filter dimensions:
 * - [query]: Case-insensitive substring match against cluster name.
 * - [equipmentName]: Linked gear name inferred via the cluster's sport type.
 * - [minDistanceMeters]: Minimum reference distance threshold in meters.
 * - [minHitCount]: Minimum number of recorded workouts associated with the cluster.
 *
 * Provides high-performance predicate evaluation via [matches] and lightweight JSON
 * serialization for asynchronous preference persistence via DataStore.
 */
@Immutable
data class ClusterFilterCriteria(
    val query: String = "",
    val equipmentName: String? = null,
    val minDistanceMeters: Double? = null,
    val minHitCount: Int? = null
) {
    /**
     * Total count of distinct active filter dimensions.
     */
    val activeFilterCount: Int
        get() {
            var count = 0
            if (query.isNotBlank()) count++
            if (equipmentName != null) count++
            if (minDistanceMeters != null) count++
            if (minHitCount != null) count++
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
     * Evaluates whether a given [WorkoutCluster] satisfies all active filter criteria.
     *
     * Performs sequential short-circuiting checks against non-null / non-empty filter
     * dimensions. Returns true if and only if every active filter dimension matches the cluster.
     *
     * @param cluster The cluster to test.
     * @param linkedEquipment The set of equipment names associated with the cluster's sport.
     * @return True if the cluster matches all active criteria; false otherwise.
     */
    fun matches(cluster: WorkoutCluster, linkedEquipment: Set<String> = emptySet()): Boolean {
        // Text Query match (case-insensitive substring across cluster name)
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            if (!cluster.name.lowercase().contains(q)) {
                return false
            }
        }

        // Equipment filter: Cluster must have linked equipment containing the target gear name
        if (equipmentName != null && !linkedEquipment.contains(equipmentName)) {
            return false
        }

        // Minimum Reference Distance threshold (meters)
        if (minDistanceMeters != null && cluster.refDistance < minDistanceMeters) {
            return false
        }

        // Minimum Recordings / Hit Count threshold
        if (minHitCount != null && cluster.hitCount < minHitCount) {
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
        equipmentName?.let { json.put("equipmentName", it) }
        minDistanceMeters?.let { json.put("minDistanceMeters", it) }
        minHitCount?.let { json.put("minHitCount", it) }
        return json.toString()
    }

    companion object {
        /**
         * Reconstructs a [ClusterFilterCriteria] instance from its persisted JSON representation.
         * Returns an empty criteria instance if [jsonStr] is null, empty, or unparseable.
         */
        fun fromJson(jsonStr: String?): ClusterFilterCriteria {
            if (jsonStr.isNullOrBlank()) return ClusterFilterCriteria()
            return try {
                val json = JSONObject(jsonStr)
                ClusterFilterCriteria(
                    query = json.optString("query", ""),
                    equipmentName = if (json.has("equipmentName")) json.getString("equipmentName") else null,
                    minDistanceMeters = if (json.has("minDistanceMeters")) json.getDouble("minDistanceMeters") else null,
                    minHitCount = if (json.has("minHitCount")) json.getInt("minHitCount") else null
                )
            } catch (_: Exception) {
                ClusterFilterCriteria()
            }
        }
    }
}
