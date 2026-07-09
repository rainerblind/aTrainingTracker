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
 * along with this program.  See the GNU General Public License for more details.
 */

package com.atrainingtracker.trainingtracker.database

import android.content.Context
import android.location.Location
import android.util.Log
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.google.android.gms.maps.model.LatLng

class RouteClusterEngine private constructor(context: Context) {

    private val dbManager = RouteClusterDatabaseManager.getInstance(context)

    companion object {
        private const val TAG = "RouteClusterEngine"
        private val DEBUG = TrainingApplication.getDebug(true)

        @Volatile
        private var instance: RouteClusterEngine? = null

        fun getInstance(context: Context): RouteClusterEngine {
            return instance ?: synchronized(this) {
                instance ?: RouteClusterEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Suggests a cluster match for a workout based on spatial shape metrics and optional name.
     */
    fun suggestCluster(start: LatLng, end: LatLng, apex: LatLng, distance: Double, workoutName: String? = null): RouteCluster? {
        val candidates = dbManager.findCandidates(start.latitude, start.longitude, distance)
        if (DEBUG) Log.d(TAG, "Found ${candidates.size} candidates for shape [start=$start, dist=$distance, name=$workoutName]")

        return candidates.map { cluster ->
            val score = calculateSimilarity(start, end, apex, distance, cluster, workoutName)
            cluster to score
        }.filter { it.second < 1.0 } // 1.0 is the threshold for a "good" match
         .minByOrNull { it.second }?.first
    }

    /**
     * Records user feedback (name/sport edit) to update or create clusters.
     */
    fun learnFromWorkout(start: LatLng, end: LatLng, apex: LatLng, distance: Double, userSpecifiedName: String, userSportId: Long, clusterIdOverride: Long = -1) {
        val existingMatch = if (clusterIdOverride != -1L) dbManager.getClusterById(clusterIdOverride) 
                            else suggestCluster(start, end, apex, distance, userSpecifiedName)

        if (existingMatch != null) {
            // Update existing cluster (Moving Average logic for centroids)
            // Ensure unique name if it changed (SCRUM-190 refinement)
            val uniqueName = if (existingMatch.name == userSpecifiedName) userSpecifiedName 
                             else findUniqueClusterName(userSpecifiedName, existingMatch.id)

            val updatedCluster = existingMatch.copy(
                name = uniqueName,
                probableSportId = userSportId,
                startLat = (existingMatch.startLat * existingMatch.hitCount + start.latitude) / (existingMatch.hitCount + 1),
                startLng = (existingMatch.startLng * existingMatch.hitCount + start.longitude) / (existingMatch.hitCount + 1),
                endLat = (existingMatch.endLat * existingMatch.hitCount + end.latitude) / (existingMatch.hitCount + 1),
                endLng = (existingMatch.endLng * existingMatch.hitCount + end.longitude) / (existingMatch.hitCount + 1),
                maxDispLat = (existingMatch.maxDispLat * existingMatch.hitCount + apex.latitude) / (existingMatch.hitCount + 1),
                maxDispLng = (existingMatch.maxDispLng * existingMatch.hitCount + apex.longitude) / (existingMatch.hitCount + 1),
                refDistance = (existingMatch.refDistance * existingMatch.hitCount + distance) / (existingMatch.hitCount + 1),
                hitCount = existingMatch.hitCount + 1
            )
            dbManager.updateCluster(updatedCluster)
            if (DEBUG) Log.i(TAG, "Learned from existing route: ${updatedCluster.name} (hitCount=${updatedCluster.hitCount})")
        } else {
            // Create a new cluster with unique name (SCRUM-190)
            val uniqueName = findUniqueClusterName(userSpecifiedName)
            val newCluster = RouteCluster(
                name = uniqueName,
                probableSportId = userSportId,
                startLat = start.latitude,
                startLng = start.longitude,
                endLat = end.latitude,
                endLng = end.longitude,
                maxDispLat = apex.latitude,
                maxDispLng = apex.longitude,
                refDistance = distance,
                hitCount = 1
            )
            dbManager.insertCluster(newCluster)
            if (DEBUG) Log.i(TAG, "Created new route family: ${newCluster.name}")
        }
    }

    /**
     * Batch processes entire workout history to populate the cluster database.
     * Processes chronologically (ASC) so that the most recent names/sports stick.
     */
    fun migrateHistory(context: Context) {
        val summariesManager = WorkoutSummariesDatabaseManager.getInstance(context)
        val cursor = summariesManager.getCursorForAllWorkoutsAsc() ?: return

        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID)
            val nameIdx = c.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_NAME)
            val fileIdx = c.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME)
            val sportIdx = c.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.SPORT_ID)
            val distIdx = c.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_TOTAL_m)

            while (c.moveToNext()) {
                val workoutId = c.getLong(idIdx)
                val workoutName = c.getString(nameIdx)
                val fileBaseName = c.getString(fileIdx)
                val sportId = c.getLong(sportIdx)
                val distance = c.getDouble(distIdx)

                val start = summariesManager.getExtremaPosition(workoutId, com.atrainingtracker.banalservice.sensor.SensorType.LATITUDE, ExtremaType.START)
                val end = summariesManager.getExtremaPosition(workoutId, com.atrainingtracker.banalservice.sensor.SensorType.LATITUDE, ExtremaType.END)
                val apex = summariesManager.getExtremaPosition(workoutId, com.atrainingtracker.banalservice.sensor.SensorType.LINE_DISTANCE_m, ExtremaType.MAX)

                if (start != null && end != null && apex != null && distance > 100.0) {
                    val isDefaultName = workoutName.isNullOrEmpty() || workoutName == fileBaseName
                    
                    val match = suggestCluster(start, end, apex, distance, if (!isDefaultName) workoutName else null)
                    if (match != null) {
                        // Update centroids and hitCount. 
                        // Only update name if the current workout has a CUSTOM name.
                        val rawName = if (!isDefaultName) workoutName else match.name
                        val finalName = if (match.name == rawName) rawName 
                                        else findUniqueClusterName(rawName, match.id)

                        val finalSport = if (!isDefaultName) sportId else match.probableSportId

                        val updated = match.copy(
                            name = finalName,
                            probableSportId = finalSport,
                            startLat = (match.startLat * match.hitCount + start.latitude) / (match.hitCount + 1),
                            startLng = (match.startLng * match.hitCount + start.longitude) / (match.hitCount + 1),
                            endLat = (match.endLat * match.hitCount + end.latitude) / (match.hitCount + 1),
                            endLng = (match.endLng * match.hitCount + end.longitude) / (match.hitCount + 1),
                            maxDispLat = (match.maxDispLat * match.hitCount + apex.latitude) / (match.hitCount + 1),
                            maxDispLng = (match.maxDispLng * match.hitCount + apex.longitude) / (match.hitCount + 1),
                            refDistance = (match.refDistance * match.hitCount + distance) / (match.hitCount + 1),
                            hitCount = match.hitCount + 1
                        )
                        dbManager.updateCluster(updated)
                        updateWorkoutClusterId(context, workoutId, updated.id)
                    } else {
                        // No match: Create new cluster. 
                        // If it's a default name, use a generic descriptive name.
                        val clusterName = if (!isDefaultName) workoutName else "Route at ${fileBaseName?.take(10) ?: "Unknown"}"
                        val uniqueName = findUniqueClusterName(clusterName)
                        val newCluster = RouteCluster(
                            name = uniqueName,
                            probableSportId = sportId,
                            startLat = start.latitude,
                            startLng = start.longitude,
                            endLat = end.latitude,
                            endLng = end.longitude,
                            maxDispLat = apex.latitude,
                            maxDispLng = apex.longitude,
                            refDistance = distance,
                            hitCount = 1
                        )
                        val newId = dbManager.insertCluster(newCluster)
                        updateWorkoutClusterId(context, workoutId, newId)
                    }
                }
            }
        }
    }

    private fun findUniqueClusterName(baseName: String, excludeId: Long = -1): String {
        var candidate = baseName
        var counter = 2
        while (dbManager.isNameTaken(candidate, excludeId)) {
            candidate = "$baseName var $counter"
            counter++
        }
        return candidate
    }

    private fun updateWorkoutClusterId(context: Context, workoutId: Long, clusterId: Long) {
        val summariesManager = WorkoutSummariesDatabaseManager.getInstance(context)
        val values = android.content.ContentValues().apply {
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID, clusterId)
        }
        summariesManager.database.update(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
            values,
            "${WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID} = ?",
            arrayOf(workoutId.toString())
        )
    }

    private fun calculateSimilarity(
        start: LatLng, end: LatLng, apex: LatLng, distance: Double, cluster: RouteCluster,
        workoutName: String? = null
    ): Double {
        val startDist = distanceBetween(start, LatLng(cluster.startLat, cluster.startLng))
        val endDist = distanceBetween(end, LatLng(cluster.endLat, cluster.endLng))
        val apexDist = distanceBetween(apex, LatLng(cluster.maxDispLat, cluster.maxDispLng))
        val lengthDiff = Math.abs(distance - cluster.refDistance) / cluster.refDistance

        // Normalized weighted score using dynamic tuning parameters
        val s1 = (startDist / TrainingApplication.getClusterTolEndpoints()) * 0.25
        val s2 = (endDist / TrainingApplication.getClusterTolEndpoints()) * 0.25
        val s3 = (apexDist / TrainingApplication.getClusterTolApex()) * 0.25
        val s4 = (lengthDiff / TrainingApplication.getClusterTolDistance()) * 0.25
        
        var totalScore = s1 + s2 + s3 + s4

        // --- NAME BONUS (SCRUM-186) ---
        if (workoutName != null) {
            val normalizedWorkout = normalizeName(workoutName)
            val normalizedCluster = normalizeName(cluster.name)
            if (normalizedWorkout.isNotEmpty() && normalizedWorkout == normalizedCluster) {
                // Halve the score if names match exactly (lower score = better match)
                // This makes it 2x as likely to match if the user has consistently named it.
                totalScore *= 0.5 
            }
        }

        return totalScore
    }

    private fun normalizeName(name: String): String {
        // Strip both "#2" and "var 2" suffixes to get the core name
        return name.replace(Regex(" (?:#|var) \\d+$"), "").trim().lowercase()
    }

    /**
     * Wipes the cluster database and re-runs the migration logic with current parameters.
     */
    fun recalculateHistory(context: Context) {
        dbManager.deleteAllClusters()
        migrateHistory(context)
    }

    /**
     * Returns all clusters paired with their similarity score for a given workout shape.
     */
    fun getClusterScores(start: LatLng, end: LatLng, apex: LatLng, distance: Double, workoutName: String? = null): List<Pair<RouteCluster, Double>> {
        val allClusters = dbManager.getAllClusters()
        return allClusters.map { cluster ->
            cluster to calculateSimilarity(start, end, apex, distance, cluster, workoutName)
        }.sortedBy { it.second }
    }

    /**
     * Moves a workout from one cluster to another, recalculating centroids for both.
     */
    fun moveWorkoutToCluster(context: Context, workoutId: Long, currentClusterId: Long, newClusterId: Long) {
        val summariesManager = WorkoutSummariesDatabaseManager.getInstance(context)

        // 1. Fetch workout spatial data
        val start = summariesManager.getExtremaPosition(workoutId, com.atrainingtracker.banalservice.sensor.SensorType.LATITUDE, ExtremaType.START)
        val end = summariesManager.getExtremaPosition(workoutId, com.atrainingtracker.banalservice.sensor.SensorType.LATITUDE, ExtremaType.END)
        val apex = summariesManager.getExtremaPosition(workoutId, com.atrainingtracker.banalservice.sensor.SensorType.LINE_DISTANCE_m, ExtremaType.MAX)
        val distance = summariesManager.getDouble(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_TOTAL_m)

        if (start == null || end == null || apex == null || distance == null) return

        // 2. Remove from old cluster
        val oldCluster = dbManager.getClusterById(currentClusterId)
        if (oldCluster != null) {
            if (oldCluster.hitCount > 1) {
                val updatedOld = oldCluster.copy(
                    startLat = (oldCluster.startLat * oldCluster.hitCount - start.latitude) / (oldCluster.hitCount - 1),
                    startLng = (oldCluster.startLng * oldCluster.hitCount - start.longitude) / (oldCluster.hitCount - 1),
                    endLat = (oldCluster.endLat * oldCluster.hitCount - end.latitude) / (oldCluster.hitCount - 1),
                    endLng = (oldCluster.endLng * oldCluster.hitCount - end.longitude) / (oldCluster.hitCount - 1),
                    maxDispLat = (oldCluster.maxDispLat * oldCluster.hitCount - apex.latitude) / (oldCluster.hitCount - 1),
                    maxDispLng = (oldCluster.maxDispLng * oldCluster.hitCount - apex.longitude) / (oldCluster.hitCount - 1),
                    refDistance = (oldCluster.refDistance * oldCluster.hitCount - distance) / (oldCluster.hitCount - 1),
                    hitCount = oldCluster.hitCount - 1
                )
                dbManager.updateCluster(updatedOld)
            } else {
                dbManager.deleteCluster(currentClusterId)
            }
        }

        // 3. Add to new cluster
        val newCluster = dbManager.getClusterById(newClusterId)
        if (newCluster != null) {
            val updatedNew = newCluster.copy(
                startLat = (newCluster.startLat * newCluster.hitCount + start.latitude) / (newCluster.hitCount + 1),
                startLng = (newCluster.startLng * newCluster.hitCount + start.longitude) / (newCluster.hitCount + 1),
                endLat = (newCluster.endLat * newCluster.hitCount + end.latitude) / (newCluster.hitCount + 1),
                endLng = (newCluster.endLng * newCluster.hitCount + end.longitude) / (newCluster.hitCount + 1),
                maxDispLat = (newCluster.maxDispLat * newCluster.hitCount + apex.latitude) / (newCluster.hitCount + 1),
                maxDispLng = (newCluster.maxDispLng * newCluster.hitCount + apex.longitude) / (newCluster.hitCount + 1),
                refDistance = (newCluster.refDistance * newCluster.hitCount + distance) / (newCluster.hitCount + 1),
                hitCount = newCluster.hitCount + 1
            )
            dbManager.updateCluster(updatedNew)
        }

        // 4. Update workout record
        updateWorkoutClusterId(context, workoutId, newClusterId)
    }

    private fun distanceBetween(p1: LatLng, p2: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
        return results[0]
    }
}
