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
    fun learnFromWorkout(start: LatLng, end: LatLng, apex: LatLng, distance: Double, userSpecifiedName: String, userSportId: Long) {
        val existingMatch = suggestCluster(start, end, apex, distance, userSpecifiedName)

        if (existingMatch != null) {
            // Update existing cluster (Moving Average logic for centroids)
            val updatedCluster = existingMatch.copy(
                name = userSpecifiedName,
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
            // Create a new cluster
            val newCluster = RouteCluster(
                name = userSpecifiedName,
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
                        val finalName = if (!isDefaultName) workoutName else match.name
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
                        val newCluster = RouteCluster(
                            name = clusterName,
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
        return name.replace(Regex(" #\\d+$"), "").trim().lowercase()
    }

    /**
     * Wipes the cluster database and re-runs the migration logic with current parameters.
     */
    fun recalculateHistory(context: Context) {
        dbManager.deleteAllClusters()
        migrateHistory(context)
    }

    private fun distanceBetween(p1: LatLng, p2: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
        return results[0]
    }
}
