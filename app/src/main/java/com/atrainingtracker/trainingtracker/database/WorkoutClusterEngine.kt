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

package com.atrainingtracker.trainingtracker.database

import android.content.Context
import android.location.Location
import android.util.Log
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.google.android.gms.maps.model.LatLng

class WorkoutClusterEngine private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val dbManager = WorkoutClusterDatabaseManager.getInstance(appContext)

    companion object {
        private const val TAG = "WorkoutClusterEngine"
        private val DEBUG = TrainingApplication.getDebug(true)

        @Volatile
        private var instance: WorkoutClusterEngine? = null

        @JvmStatic
        fun getInstance(context: Context): WorkoutClusterEngine {
            return instance ?: synchronized(this) {
                instance ?: WorkoutClusterEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Suggests a cluster match for a workout based on spatial shape metrics and optional name.
     */
    fun suggestCluster(start: LatLng, end: LatLng, apex: LatLng, distance: Double, workoutName: String? = null): WorkoutCluster? {
        val candidates = dbManager.findCandidates(start.latitude, start.longitude, distance)
        if (DEBUG) Log.d(TAG, "Found ${candidates.size} candidates for shape [start=$start, dist=$distance, name=$workoutName]")

        return candidates.map { cluster ->
            val score = calculateSimilarity(start, end, apex, distance, cluster, workoutName)
            cluster to score
        }.filter { it.second < 1.0 } // 1.0 is the threshold for a "good" match
         .minByOrNull { it.second }?.first
    }

    /**
     * Records user feedback (name/sport edit) or route seeding to update or create clusters.
     * Returns the cluster ID.
     */
    fun learnFromWorkout(
        start: LatLng, end: LatLng, apex: LatLng, distance: Double, 
        userSpecifiedName: String, userSportId: Long, 
        clusterIdOverride: Long = -1
    ): Long {
        val existingMatch = if (clusterIdOverride != -1L) dbManager.getClusterById(clusterIdOverride) 
                            else suggestCluster(start, end, apex, distance, userSpecifiedName)

        return if (existingMatch != null) {
            // Update existing cluster (Moving Average logic for centroids)
            // Ensure unique name if it changed (SCRUM-190 refinement)
            val normalizedInputName = stripHitCount(userSpecifiedName)
            val uniqueName = if (existingMatch.name == normalizedInputName) normalizedInputName 
                             else findUniqueClusterName(normalizedInputName, existingMatch.id)

            val updatedCluster = existingMatch.copy(
                name = uniqueName,
                probableSportId = userSportId,
                bSportType = SportTypeDatabaseManager.getInstance(appContext).getBSportType(userSportId),
                startLat = (existingMatch.startLat * existingMatch.hitCount + start.latitude) / (existingMatch.hitCount + 1),
                startLng = (existingMatch.startLng * existingMatch.hitCount + start.longitude) / (existingMatch.hitCount + 1),
                endLat = (existingMatch.endLat * existingMatch.hitCount + end.latitude) / (existingMatch.hitCount + 1),
                endLng = (existingMatch.endLng * existingMatch.hitCount + end.longitude) / (existingMatch.hitCount + 1),
                maxDispLat = (existingMatch.maxDispLat * existingMatch.hitCount + apex.latitude) / (existingMatch.hitCount + 1),
                maxDispLng = (existingMatch.maxDispLng * existingMatch.hitCount + apex.longitude) / (existingMatch.hitCount + 1),
                refDistance = (existingMatch.refDistance * existingMatch.hitCount + distance) / (existingMatch.hitCount + 1)
            )
            dbManager.updateCluster(updatedCluster)
            if (DEBUG) Log.i(TAG, "Updated shape for cluster: ${updatedCluster.name}")
            
            // Re-evaluate probable sport based on majority (SCRUM-182)
            val mostFrequentSport = WorkoutSummariesDatabaseManager.getInstance(appContext).getMostFrequentSportIdForCluster(updatedCluster.id)
            if (mostFrequentSport != -1L && mostFrequentSport != updatedCluster.probableSportId) {
                val newBSport = SportTypeDatabaseManager.getInstance(appContext).getBSportType(mostFrequentSport)
                dbManager.updateCluster(updatedCluster.copy(probableSportId = mostFrequentSport, bSportType = newBSport))
            }
            updatedCluster.id
        } else {
            // Create a new cluster with unique name (SCRUM-190)
            val uniqueName = findUniqueClusterName(stripHitCount(userSpecifiedName))
            val newCluster = WorkoutCluster(
                name = uniqueName,
                probableSportId = userSportId,
                startLat = start.latitude,
                startLng = start.longitude,
                endLat = end.latitude,
                endLng = end.longitude,
                maxDispLat = apex.latitude,
                maxDispLng = apex.longitude,
                refDistance = distance,
                hitCount = 0, // Hit count is 0 until assigned via assignClusterToWorkout
                bSportType = SportTypeDatabaseManager.getInstance(appContext).getBSportType(userSportId)
            )
            val newId = dbManager.insertCluster(newCluster)
            if (DEBUG) Log.i(TAG, "Created new route family: ${newCluster.name}")
            newId
        }
    }

    /**
     * Updates the name of a WorkoutCluster when its originating Route is renamed (SCRUM-207).
     * Uses ID link for 100% accuracy.
     */
    fun syncRouteNameChange(clusterId: Long, newName: String) {
        if (clusterId == -1L) return
        val match = dbManager.getClusterById(clusterId)
        if (match != null) {
            val uniqueName = findUniqueClusterName(newName, match.id)
            dbManager.updateCluster(match.copy(name = uniqueName))
            if (DEBUG) Log.i(TAG, "Synced cluster name change (ID=$clusterId): $uniqueName")
        }
    }

    /**
     * Similar to learnFromWorkout, but for explicit Route entities.
     * Ensures that imported or created routes seed the cluster database (SCRUM-207).
     * Returns the cluster ID.
     */
    fun learnFromRoute(route: RouteWithPath): Long {
        val path = route.path
        if (path.size < 2) return -1L

        val start = path.first().latLng
        val end = path.last().latLng
        val distance = route.summary.distance

        var maxLineDist = -1.0
        var apex = start

        path.forEach { point ->
            val dist = distanceBetween(point.latLng, start).toDouble()
            if (dist > maxLineDist) {
                maxLineDist = dist
                apex = point.latLng
            }
        }

        val sportId = SportTypeDatabaseManager.getSportTypeId(route.summary.bSportType)

        // Routes are high-confidence seeds, but we use a hitCount of 0 to allow workouts to influence them (SCRUM-216 Refinement).
        return learnFromWorkout(
            start = start,
            end = end,
            apex = apex,
            distance = distance,
            userSpecifiedName = route.summary.name,
            userSportId = sportId
        )
    }

    /**
     * Batch processes entire workout and route history to populate the cluster database.
     * Processes chronologically (ASC) so that the most recent names/sports stick.
     */
    fun migrateHistory(context: Context) {
        // 1. Process all Routes first as they are authoritative seeds (SCRUM-207)
        val routesDb = RoutesDatabaseManager.getInstance(context)
        routesDb.getAllRoutes().forEach { routeWithPath ->
            val clusterId = learnFromRoute(routeWithPath)
            if (clusterId != -1L) {
                routesDb.updateRouteSummary(routeWithPath.summary.copy(clusterId = clusterId))
            }
        }

        // 2. Process all Workouts
        val summariesManager = WorkoutSummariesDatabaseManager.getInstance(context)
        val cursor = summariesManager.getCursorForAllWorkoutsAsc() ?: return

        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(WorkoutSummaries.C_ID)
            val nameIdx = c.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_NAME)
            val fileIdx = c.getColumnIndexOrThrow(WorkoutSummaries.FILE_BASE_NAME)
            val sportIdx = c.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID)
            val distIdx = c.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_TOTAL_m)

            while (c.moveToNext()) {
                val workoutId = c.getLong(idIdx)
                val workoutName = c.getString(nameIdx)
                val fileBaseName = c.getString(fileIdx)
                val sportId = c.getLong(sportIdx)
                val distance = c.getDouble(distIdx)

                val start = summariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.START)
                val end = summariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.END)
                val apex = summariesManager.getExtremaPosition(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX)

                if (start != null && end != null && apex != null && distance > 100.0) {
                    val isDefaultName = workoutName.isNullOrEmpty() || workoutName == fileBaseName
                    val normalizedWorkoutName = if (!isDefaultName) stripHitCount(workoutName) else null
                    
                    val match = suggestCluster(start, end, apex, distance, normalizedWorkoutName)
                    if (match != null) {
                        // Update centroids and hitCount. 
                        // Only update name if the current workout has a CUSTOM name.
                        val rawName = normalizedWorkoutName ?: match.name
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
                            bSportType = SportTypeDatabaseManager.getInstance(context).getBSportType(finalSport)
                        )
                        dbManager.updateCluster(updated)
                        assignClusterToWorkout(context, workoutId, updated.id)

                        // Re-evaluate probable sport based on majority (SCRUM-182)
                        val mostFrequentSport = WorkoutSummariesDatabaseManager.getInstance(context).getMostFrequentSportIdForCluster(updated.id)
                        if (mostFrequentSport != -1L && mostFrequentSport != updated.probableSportId) {
                            val newBSport = SportTypeDatabaseManager.getInstance(context).getBSportType(mostFrequentSport)
                            dbManager.updateCluster(updated.copy(probableSportId = mostFrequentSport, bSportType = newBSport))
                        }
                    } else {
                        // No match: Create new cluster. 
                        // If it's a default name, use a generic descriptive name.
                        val clusterName = if (!isDefaultName) normalizedWorkoutName!! else context.getString(R.string.cluster_default_name_format, fileBaseName?.take(10) ?: context.getString(R.string.unknown_manufacturer))
                        val uniqueName = findUniqueClusterName(clusterName)
                        val newCluster = WorkoutCluster(
                            name = uniqueName,
                            probableSportId = sportId,
                            startLat = start.latitude,
                            startLng = start.longitude,
                            endLat = end.latitude,
                            endLng = end.longitude,
                            maxDispLat = apex.latitude,
                            maxDispLng = apex.longitude,
                            refDistance = distance,
                            hitCount = 0,
                            bSportType = SportTypeDatabaseManager.getInstance(context).getBSportType(sportId)
                        )
                        val newId = dbManager.insertCluster(newCluster)
                        assignClusterToWorkout(context, workoutId, newId)
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

    fun assignClusterToWorkout(context: Context, workoutId: Long, clusterId: Long) {
        val summariesManager = WorkoutSummariesDatabaseManager.getInstance(context)
        val discoveryManager = EquipmentAndSportTypeDiscoveryManager.getInstance(context)
        val clusterDb = WorkoutClusterDatabaseManager.getInstance(context)

        // 1. Check if workout was previously linked to a DIFFERENT cluster
        val previousClusterId = summariesManager.getLong(workoutId, WorkoutSummaries.CLUSTER_ID) ?: -1L
        if (previousClusterId != -1L && previousClusterId != clusterId) {
            // Decrement the old cluster (simple count decrement)
            val oldCluster = clusterDb.getClusterById(previousClusterId)
            if (oldCluster != null) {
                clusterDb.updateCluster(oldCluster.copy(hitCount = (oldCluster.hitCount - 1).coerceAtLeast(0)))
            }
        }

        val cluster = clusterDb.getClusterById(clusterId) ?: return

        // 2. Increment hit count for the new cluster if link is new
        if (previousClusterId != clusterId) {
            clusterDb.updateCluster(cluster.copy(hitCount = cluster.hitCount + 1))
        }

        val values = android.content.ContentValues().apply {
            put(WorkoutSummaries.CLUSTER_ID, clusterId)

            // Auto-Name logic
            val currentName = summariesManager.getString(workoutId, WorkoutSummaries.WORKOUT_NAME)
            val fileBaseName = summariesManager.getString(workoutId, WorkoutSummaries.FILE_BASE_NAME)

            if (currentName.isNullOrEmpty() || currentName == fileBaseName) {
                // Use current cluster hitCount + 1 for the auto-name if we just linked it
                val displayCount = if (previousClusterId == clusterId) cluster.hitCount else cluster.hitCount + 1
                val autoName = context.getString(R.string.cluster_autoname_format, cluster.name, displayCount)
                put(WorkoutSummaries.WORKOUT_NAME, autoName)
            }
        }
        summariesManager.database.update(
            WorkoutSummaries.TABLE,
            values,
            "${WorkoutSummaries.C_ID} = ?",
            arrayOf(workoutId.toString())
        )

        // ARBITRATION (SCRUM-200): Only propagate sport-identity if NOT determined by hardware
        val sportStr = summariesManager.getString(workoutId, WorkoutSummaries.B_SPORT)
        val currentBSport = if (sportStr != null) BSportType.valueOf(sportStr) else BSportType.UNKNOWN
        val avgSpeed = summariesManager.getDouble(workoutId, WorkoutSummaries.SPEED_AVERAGE_mps) ?: 0.0

        val hardwareIdentity = discoveryManager.resolveIdentity(workoutId, currentBSport, avgSpeed)

        if (hardwareIdentity.isHighConfidence) {
            // Hardware confidence is high -> keep hardware-based sport and equipment
            summariesManager.applyInferredIdentity(workoutId, hardwareIdentity)
            if (DEBUG) Log.i(TAG, "Hardware confidence high for workout $workoutId. Vetoing cluster-based override.")
        } else {
            // Low confidence -> let the cluster majority win
            val clusterIdentity = discoveryManager.inferIdentityFromSport(cluster.probableSportId)
            summariesManager.applyInferredIdentity(workoutId, clusterIdentity)
            if (DEBUG) Log.i(TAG, "Workout Cluster majority winning for workout $workoutId.")
        }
    }

    private fun calculateSimilarity(
        start: LatLng, end: LatLng, apex: LatLng, distance: Double, cluster: WorkoutCluster,
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
        return name.replace(Regex(" (?:#|var) \\d+$", RegexOption.IGNORE_CASE), "").trim().lowercase()
    }

    /**
     * Strips ONLY the hit count suffix (e.g., " #2") from a workout name.
     */
    private fun stripHitCount(name: String): String {
        return name.replace(Regex(" #\\d+$"), "").trim()
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
    fun getClusterScores(start: LatLng, end: LatLng, apex: LatLng, distance: Double, workoutName: String? = null): List<Pair<WorkoutCluster, Double>> {
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
        val start = summariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.START)
        val end = summariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.END)
        val apex = summariesManager.getExtremaPosition(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX)
        val distance = summariesManager.getDouble(workoutId, WorkoutSummaries.DISTANCE_TOTAL_m)

        if (start == null || end == null || apex == null || distance == null) return

        // 2. Remove spatial influence from old cluster
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
                    refDistance = (oldCluster.refDistance * oldCluster.hitCount - distance) / (oldCluster.hitCount - 1)
                    // hitCount decrement is handled by assignClusterToWorkout
                )
                dbManager.updateCluster(updatedOld)
                
                // Re-evaluate probable sport based on majority (SCRUM-182)
                val mostFrequentSport = WorkoutSummariesDatabaseManager.getInstance(context).getMostFrequentSportIdForCluster(updatedOld.id)
                if (mostFrequentSport != -1L && mostFrequentSport != updatedOld.probableSportId) {
                    val newBSport = SportTypeDatabaseManager.getInstance(context).getBSportType(mostFrequentSport)
                    dbManager.updateCluster(updatedOld.copy(probableSportId = mostFrequentSport, bSportType = newBSport))
                }
            } else {
                // If this is the last workout (hitCount=1), we skip centroid recalculation 
                // to avoid division by zero, but keep the cluster (it persists with hitCount=0).
            }
        }

        // 3. Add spatial influence to new cluster
        val newCluster = dbManager.getClusterById(newClusterId)
        if (newCluster != null) {
            val updatedNew = newCluster.copy(
                startLat = (newCluster.startLat * newCluster.hitCount + start.latitude) / (newCluster.hitCount + 1),
                startLng = (newCluster.startLng * newCluster.hitCount + start.longitude) / (newCluster.hitCount + 1),
                endLat = (newCluster.endLat * newCluster.hitCount + end.latitude) / (newCluster.hitCount + 1),
                endLng = (newCluster.endLng * newCluster.hitCount + end.longitude) / (newCluster.hitCount + 1),
                maxDispLat = (newCluster.maxDispLat * newCluster.hitCount + apex.latitude) / (newCluster.hitCount + 1),
                maxDispLng = (newCluster.maxDispLng * newCluster.hitCount + apex.longitude) / (newCluster.hitCount + 1),
                refDistance = (newCluster.refDistance * newCluster.hitCount + distance) / (newCluster.hitCount + 1)
                // hitCount increment is handled by assignClusterToWorkout
            )
            dbManager.updateCluster(updatedNew)
            
            // Re-evaluate probable sport based on majority (SCRUM-182)
            val mostFrequentSport = WorkoutSummariesDatabaseManager.getInstance(context).getMostFrequentSportIdForCluster(updatedNew.id)
            if (mostFrequentSport != -1L && mostFrequentSport != updatedNew.probableSportId) {
                val newBSport = SportTypeDatabaseManager.getInstance(context).getBSportType(mostFrequentSport)
                dbManager.updateCluster(updatedNew.copy(probableSportId = mostFrequentSport, bSportType = newBSport))
            }
        }

        // 4. Update workout record (delegating assignment and target increment)
        assignClusterToWorkout(context, workoutId, newClusterId)
    }

    /**
     * Manually creates a new cluster with the given spatial fingerprint.
     */
    fun manuallyCreateCluster(
        name: String,
        sportId: Long,
        start: LatLng,
        end: LatLng,
        apex: LatLng,
        distance: Double
    ): Long {
        val uniqueName = findUniqueClusterName(name)
        val newCluster = WorkoutCluster(
            name = uniqueName,
            probableSportId = sportId,
            startLat = start.latitude,
            startLng = start.longitude,
            endLat = end.latitude,
            endLng = end.longitude,
            maxDispLat = apex.latitude,
            maxDispLng = apex.longitude,
            refDistance = distance,
            hitCount = 0, // Hit count is 0 for manually created clusters until workouts are associated
            bSportType = SportTypeDatabaseManager.getInstance(appContext).getBSportType(sportId)
        )
        return dbManager.insertCluster(newCluster)
    }

    private fun distanceBetween(p1: LatLng, p2: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
        return results[0]
    }
}
