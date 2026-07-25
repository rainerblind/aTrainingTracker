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
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
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
        val endpointTol = TrainingApplication.getClusterTolEndpoints().toDouble()
        val latToleranceDegrees = endpointTol / 111000.0
        val distToleranceMeters = distance * TrainingApplication.getClusterTolDistance().toDouble() * 4.0

        val candidates = dbManager.findCandidates(start.latitude, start.longitude, distance, latToleranceDegrees, distToleranceMeters)
        if (DEBUG) Log.d(TAG, "Found ${candidates.size} candidates for shape [start=$start, dist=$distance, name=$workoutName]")

        return candidates.map { cluster ->
            val score = calculateSimilarity(start, end, apex, distance, cluster, workoutName)
            cluster to score
        }.filter { it.second < 1.0 }
         .minByOrNull { it.second }?.first
    }

    /**
     * Records user feedback (name/sport edit) or route seeding to update or create clusters.
     */
    fun learnFromWorkout(
        start: LatLng, end: LatLng, apex: LatLng, distance: Double, 
        userSpecifiedName: String, userSportId: Long, 
        clusterIdOverride: Long = -1
    ): Long {
        val existingMatch = if (clusterIdOverride != -1L) dbManager.getClusterById(clusterIdOverride) 
                            else suggestCluster(start, end, apex, distance, userSpecifiedName)

        return if (existingMatch != null) {
            val normalizedInputName = stripHitCount(userSpecifiedName)
            val uniqueName = if (clusterIdOverride != -1L && !isDefaultName(existingMatch.name)) {
                existingMatch.name
            } else {
                if (existingMatch.name == normalizedInputName) normalizedInputName
                else findUniqueClusterName(normalizedInputName, existingMatch.id)
            }

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
            updatedCluster.id
        } else {
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
                hitCount = 0,
                bSportType = SportTypeDatabaseManager.getInstance(appContext).getBSportType(userSportId)
            )
            dbManager.insertCluster(newCluster)
        }
    }

    /**
     * O(1) Surgical update when a workout is finished (ATT-354).
     */
    fun onWorkoutFinished(context: Context, w: WorkoutData) {
        if (w.startLatLng == null || w.endLatLng == null || w.maxDisplacement == null) return
        
        val normalizedName = if (w.workoutName != w.fileBaseName) stripHitCount(w.workoutName) else null
        val match = suggestCluster(w.startLatLng, w.endLatLng, w.startLatLng, w.totalDistance, normalizedName)

        if (match != null) {
            assignClusterToWorkout(context, w.id, match.id, false)
            // Refresh match after hitCount increment
            val currentMatch = dbManager.getClusterById(match.id) ?: return
            
            // Centroids and Bounds update (Moving Average)
            val updated = currentMatch.copy(
                startLat = (currentMatch.startLat * currentMatch.hitCount + w.startLatLng.latitude) / (currentMatch.hitCount + 1),
                startLng = (currentMatch.startLng * currentMatch.hitCount + w.startLatLng.longitude) / (currentMatch.hitCount + 1),
                endLat = (currentMatch.endLat * currentMatch.hitCount + w.endLatLng.latitude) / (currentMatch.hitCount + 1),
                endLng = (currentMatch.endLng * currentMatch.hitCount + w.endLatLng.longitude) / (currentMatch.hitCount + 1),
                refDistance = (currentMatch.refDistance * currentMatch.hitCount + w.totalDistance) / (currentMatch.hitCount + 1),
                minLat = minOf(currentMatch.minLat ?: 90.0, w.minLat ?: 90.0),
                minLng = minOf(currentMatch.minLng ?: 180.0, w.minLng ?: 180.0),
                maxLat = maxOf(currentMatch.maxLat ?: -90.0, w.maxLat ?: -90.0),
                maxLng = maxOf(currentMatch.maxLng ?: -180.0, w.maxLng ?: -180.0),
                longestWorkoutId = if (w.activeTimeSec > currentMatch.longestDurationS) w.id else currentMatch.longestWorkoutId,
                longestDurationS = if (w.activeTimeSec > currentMatch.longestDurationS) w.activeTimeSec else currentMatch.longestDurationS
            )
            dbManager.updateCluster(updated)
        } else {
            val clusterName = normalizedName ?: context.getString(R.string.cluster_default_name_format, w.fileBaseName?.take(10) ?: "Workout")
            val newId = learnFromWorkout(w.startLatLng, w.endLatLng, w.startLatLng, w.totalDistance, clusterName, w.sportId)
            
            // Initialize bounds and longest for the new family
            val newCluster = dbManager.getClusterById(newId)
            if (newCluster != null) {
                dbManager.updateCluster(newCluster.copy(
                    minLat = w.minLat, minLng = w.minLng, maxLat = w.maxLat, maxLng = w.maxLng,
                    longestWorkoutId = w.id, longestDurationS = w.activeTimeSec
                ))
            }
            assignClusterToWorkout(context, w.id, newId, false)
        }
    }

    /**
     * O(1) Surgical removal of a workout's influence from its cluster (ATT-354).
     */
    fun onWorkoutDeleted(context: Context, w: WorkoutData) {
        val clusterId = w.clusterId
        if (clusterId == -1L) return
        
        val cluster = dbManager.getClusterById(clusterId) ?: return
        
        if (cluster.hitCount <= 1) {
            dbManager.updateCluster(cluster.copy(hitCount = 0))
            return
        }

        val newHitCount = cluster.hitCount - 1
        val updated = cluster.copy(
            hitCount = newHitCount,
            startLat = (cluster.startLat * cluster.hitCount - (w.startLatLng?.latitude ?: cluster.startLat)) / newHitCount,
            startLng = (cluster.startLng * cluster.hitCount - (w.startLatLng?.longitude ?: cluster.startLng)) / newHitCount,
            endLat = (cluster.endLat * cluster.hitCount - (w.endLatLng?.latitude ?: cluster.endLat)) / newHitCount,
            endLng = (cluster.endLng * cluster.hitCount - (w.endLatLng?.longitude ?: cluster.endLng)) / newHitCount,
            refDistance = (cluster.refDistance * cluster.hitCount - w.totalDistance) / newHitCount
        )

        val isAnchor = w.id == cluster.longestWorkoutId || 
                       w.minLat == cluster.minLat || w.maxLat == cluster.maxLat ||
                       w.minLng == cluster.minLng || w.maxLng == cluster.maxLng

        if (isAnchor) {
            Log.i(TAG, "Surgical Recalc: Deleting anchor ${w.id} from cluster ${cluster.name}. Recalculating family anchors.")
            recalculateClusterAnchors(context, updated)
        } else {
            dbManager.updateCluster(updated)
        }
    }

    /**
     * Re-evaluates cluster metadata when a workout's sport changes (ATT-354).
     */
    fun onWorkoutSportChanged(newW: WorkoutData, oldW: WorkoutData) {
        // Since sport is part of the majority re-evaluation, we just process the new state.
        // The old state's influence on centroids is already handled by standard moving average update in finished logic.
        onWorkoutFinished(appContext, newW)
    }

    private fun recalculateClusterAnchors(context: Context, cluster: WorkoutCluster) {
        val summariesManager = WorkoutSummariesDatabaseManager.getInstance(context)
        val db = summariesManager.getDatabase()
        val cursor = db.query(WorkoutSummaries.TABLE, 
            arrayOf(WorkoutSummaries.C_ID, WorkoutSummaries.TIME_ACTIVE_s, 
                    WorkoutSummaries.BOUND_MIN_LAT, WorkoutSummaries.BOUND_MIN_LNG, 
                    WorkoutSummaries.BOUND_MAX_LAT, WorkoutSummaries.BOUND_MAX_LNG),
            "${WorkoutSummaries.CLUSTER_ID} = ?", arrayOf(cluster.id.toString()), null, null, null)

        var minLat = 90.0; var maxLat = -90.0; var minLng = 180.0; var maxLng = -180.0
        var longestId = -1L; var longestDuration = 0L
        var hasPoints = false

        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(WorkoutSummaries.C_ID)
            val durIdx = c.getColumnIndexOrThrow(WorkoutSummaries.TIME_ACTIVE_s)
            val minLatIdx = c.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MIN_LAT)
            val minLngIdx = c.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MIN_LNG)
            val maxLatIdx = c.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MAX_LAT)
            val maxLngIdx = c.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MAX_LNG)

            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                val dur = c.getLong(durIdx)
                val wMinLat = if (c.isNull(minLatIdx)) null else c.getDouble(minLatIdx)
                val wMinLng = if (c.isNull(minLngIdx)) null else c.getDouble(minLngIdx)
                val wMaxLat = if (c.isNull(maxLatIdx)) null else c.getDouble(maxLatIdx)
                val wMaxLng = if (c.isNull(maxLngIdx)) null else c.getDouble(maxLngIdx)

                if (wMinLat != null && wMinLat < minLat) minLat = wMinLat
                if (wMaxLat != null && wMaxLat > maxLat) maxLat = wMaxLat
                if (wMinLng != null && wMinLng < minLng) minLng = wMinLng
                if (wMaxLng != null && wMaxLng > maxLng) maxLng = wMaxLng
                if (wMinLat != null) hasPoints = true

                if (dur > longestDuration) {
                    longestDuration = dur
                    longestId = id
                }
            }
        }

        val refreshed = cluster.copy(
            minLat = if (hasPoints) minLat else null,
            minLng = if (hasPoints) minLng else null,
            maxLat = if (hasPoints) maxLat else null,
            maxLng = if (hasPoints) maxLng else null,
            longestWorkoutId = longestId,
            longestDurationS = longestDuration
        )
        dbManager.updateCluster(refreshed)
        
        val mostFrequentSport = summariesManager.getMostFrequentSportIdForCluster(refreshed.id)
        if (mostFrequentSport != -1L) {
            val bSport = SportTypeDatabaseManager.getInstance(context).getBSportType(mostFrequentSport)
            dbManager.updateCluster(refreshed.copy(probableSportId = mostFrequentSport, bSportType = bSport))
        }
    }

    fun syncRouteNameChange(clusterId: Long, newName: String) {
        if (clusterId == -1L) return
        val match = dbManager.getClusterById(clusterId)
        if (match != null) {
            val uniqueName = findUniqueClusterName(newName, match.id)
            dbManager.updateCluster(match.copy(name = uniqueName))
        }
    }

    fun learnFromRoute(route: RouteWithPath): Long {
        val path = route.path
        if (path.size < 2) return -1L
        val start = path.first().latLng
        val end = path.last().latLng
        val distance = route.summary.distance
        var maxLineDist = -1.0; var apex = start
        path.forEach { point ->
            val dist = distanceBetween(point.latLng, start).toDouble()
            if (dist > maxLineDist) { maxLineDist = dist; apex = point.latLng }
        }
        val sportId = SportTypeDatabaseManager.getSportTypeId(route.summary.bSportType)
        return learnFromWorkout(start, end, apex, distance, route.summary.name, sportId)
    }

    fun migrateHistory(context: Context) {
        val routesDb = RoutesDatabaseManager.getInstance(context)
        routesDb.getAllRoutes().forEach { routeWithPath ->
            val clusterId = learnFromRoute(routeWithPath)
            if (clusterId != -1L) {
                routesDb.updateRouteSummary(routeWithPath.summary.copy(clusterId = clusterId))
            }
        }

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
                    val isDefault = workoutName.isNullOrEmpty() || workoutName == fileBaseName
                    val normalizedName = if (!isDefault) stripHitCount(workoutName) else null
                    
                    val match = suggestCluster(start, end, apex, distance, normalizedName)
                    if (match != null) {
                        val rawName = normalizedName ?: match.name
                        val finalName = if (match.name == rawName) rawName else findUniqueClusterName(rawName, match.id)
                        val finalSport = if (!isDefault) sportId else match.probableSportId

                        // ATT-354: Migrate bounds and longest
                        val wMinLat = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MIN_LAT)
                        val wMinLng = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MIN_LNG)
                        val wMaxLat = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MAX_LAT)
                        val wMaxLng = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MAX_LNG)
                        val wDuration = summariesManager.getLong(workoutId, WorkoutSummaries.TIME_ACTIVE_s) ?: 0L

                        val minLat = if (wMinLat != null) minOf(match.minLat ?: 90.0, wMinLat) else match.minLat
                        val minLng = if (wMinLng != null) minOf(match.minLng ?: 180.0, wMinLng) else match.minLng
                        val maxLat = if (wMaxLat != null) maxOf(match.maxLat ?: -90.0, wMaxLat) else match.maxLat
                        val maxLng = if (wMaxLng != null) maxOf(match.maxLng ?: -180.0, wMaxLng) else match.maxLng
                        
                        val isLonger = wDuration > match.longestDurationS
                        val longestId = if (isLonger) workoutId else match.longestWorkoutId
                        val longestDuration = if (isLonger) wDuration else match.longestDurationS

                        val updated = match.copy(
                            name = finalName, probableSportId = finalSport,
                            startLat = (match.startLat * match.hitCount + start.latitude) / (match.hitCount + 1),
                            startLng = (match.startLng * match.hitCount + start.longitude) / (match.hitCount + 1),
                            endLat = (match.endLat * match.hitCount + end.latitude) / (match.hitCount + 1),
                            endLng = (match.endLng * match.hitCount + end.longitude) / (match.hitCount + 1),
                            maxDispLat = (match.maxDispLat * match.hitCount + apex.latitude) / (match.hitCount + 1),
                            maxDispLng = (match.maxDispLng * match.hitCount + apex.longitude) / (match.hitCount + 1),
                            refDistance = (match.refDistance * match.hitCount + distance) / (match.hitCount + 1),
                            bSportType = SportTypeDatabaseManager.getInstance(context).getBSportType(finalSport),
                            minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng,
                            longestWorkoutId = longestId, longestDurationS = longestDuration,
                            hitCount = match.hitCount + 1
                        )
                        dbManager.updateCluster(updated)
                        assignClusterToWorkout(context, workoutId, updated.id)
                    } else {
                        val clusterName = if (!isDefault) normalizedName!! else context.getString(R.string.cluster_default_name_format, fileBaseName?.take(10) ?: "Workout")
                        val newId = learnFromWorkout(start, end, apex, distance, clusterName, sportId)
                        
                        val wMinLat = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MIN_LAT)
                        val wMinLng = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MIN_LNG)
                        val wMaxLat = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MAX_LAT)
                        val wMaxLng = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MAX_LNG)
                        val wDuration = summariesManager.getLong(workoutId, WorkoutSummaries.TIME_ACTIVE_s) ?: 0L

                        dbManager.getClusterById(newId)?.let {
                            dbManager.updateCluster(it.copy(
                                minLat = wMinLat, minLng = wMinLng, maxLat = wMaxLat, maxLng = wMaxLng,
                                longestWorkoutId = workoutId, longestDurationS = wDuration
                            ))
                        }
                        assignClusterToWorkout(context, workoutId, newId)
                    }
                }
            }
        }
    }

    private fun findUniqueClusterName(baseName: String, excludeId: Long = -1): String {
        var candidate = baseName; var counter = 2
        while (dbManager.isNameTaken(candidate, excludeId)) { candidate = "$baseName var $counter"; counter++ }
        return candidate
    }

    @JvmOverloads
    fun assignClusterToWorkout(context: Context, workoutId: Long, clusterId: Long, forceIdentity: Boolean = false) {
        val summariesManager = WorkoutSummariesDatabaseManager.getInstance(context)
        val clusterDb = WorkoutClusterDatabaseManager.getInstance(context)
        val previousClusterId = summariesManager.getLong(workoutId, WorkoutSummaries.CLUSTER_ID) ?: -1L
        if (previousClusterId != -1L && previousClusterId != clusterId) {
            val oldCluster = clusterDb.getClusterById(previousClusterId)
            if (oldCluster != null) clusterDb.updateCluster(oldCluster.copy(hitCount = (oldCluster.hitCount - 1).coerceAtLeast(0)))
        }
        val cluster = clusterDb.getClusterById(clusterId) ?: return
        if (previousClusterId != clusterId) clusterDb.updateCluster(cluster.copy(hitCount = cluster.hitCount + 1))
        val values = android.content.ContentValues().apply {
            put(WorkoutSummaries.CLUSTER_ID, clusterId)
            val currentName = summariesManager.getString(workoutId, WorkoutSummaries.WORKOUT_NAME)
            val fileBaseName = summariesManager.getString(workoutId, WorkoutSummaries.FILE_BASE_NAME)
            if (forceIdentity || currentName.isNullOrEmpty() || currentName == fileBaseName) {
                val displayCount = if (previousClusterId == clusterId) cluster.hitCount else cluster.hitCount + 1
                put(WorkoutSummaries.WORKOUT_NAME, context.getString(R.string.cluster_autoname_format, cluster.name, displayCount))
            }
        }
        summariesManager.database.update(WorkoutSummaries.TABLE, values, "${WorkoutSummaries.C_ID} = ?", arrayOf(workoutId.toString()))
        val sportStr = summariesManager.getString(workoutId, WorkoutSummaries.B_SPORT)
        val currentBSport = if (sportStr != null) BSportType.valueOf(sportStr) else BSportType.UNKNOWN
        val avgSpeed = summariesManager.getDouble(workoutId, WorkoutSummaries.SPEED_AVERAGE_mps) ?: 0.0
        val discoveryManager = EquipmentAndSportTypeDiscoveryManager.getInstance(context)
        val hardwareIdentity = discoveryManager.resolveIdentity(workoutId, currentBSport, avgSpeed)
        if (!forceIdentity && hardwareIdentity.isHighConfidence) summariesManager.applyInferredIdentity(workoutId, hardwareIdentity)
        else summariesManager.applyInferredIdentity(workoutId, discoveryManager.inferIdentityFromSport(cluster.probableSportId))
    }

    fun calculateSimilarity(start: LatLng, end: LatLng, apex: LatLng, distance: Double, cluster: WorkoutCluster, workoutName: String? = null): Double {
        val s1 = (distanceBetween(start, LatLng(cluster.startLat, cluster.startLng)) / TrainingApplication.getClusterTolEndpoints()) * 0.25
        val s2 = (distanceBetween(end, LatLng(cluster.endLat, cluster.endLng)) / TrainingApplication.getClusterTolEndpoints()) * 0.25
        val s3 = (distanceBetween(apex, LatLng(cluster.maxDispLat, cluster.maxDispLng)) / TrainingApplication.getClusterTolApex()) * 0.25
        val s4 = (Math.abs(distance - cluster.refDistance) / cluster.refDistance / TrainingApplication.getClusterTolDistance()) * 0.25
        var totalScore = s1 + s2 + s3 + s4
        if (workoutName != null) {
            val normalizedWorkout = normalizeName(workoutName); val normalizedCluster = normalizeName(cluster.name)
            if (normalizedWorkout.isNotEmpty() && normalizedWorkout == normalizedCluster) totalScore *= 0.5 
        }
        return totalScore
    }

    private fun normalizeName(name: String): String = name.replace(Regex(" (?:#|var) \\d+$", RegexOption.IGNORE_CASE), "").trim().lowercase()
    private fun stripHitCount(name: String): String = name.replace(Regex(" #\\d+$"), "").trim()
    fun recalculateHistory(context: Context) { dbManager.deleteAllClusters(); migrateHistory(context) }
    fun getClusterScores(start: LatLng, end: LatLng, apex: LatLng, distance: Double, workoutName: String? = null): List<Pair<WorkoutCluster, Double>> = scoreClusters(dbManager.getAllClusters(), start, end, apex, distance, workoutName)
    fun scoreClusters(clusters: List<WorkoutCluster>, start: LatLng, end: LatLng, apex: LatLng, distance: Double, workoutName: String? = null): List<Pair<WorkoutCluster, Double>> = clusters.map { it to calculateSimilarity(start, end, apex, distance, it, workoutName) }.sortedBy { it.second }

    fun moveWorkoutToCluster(context: Context, workoutId: Long, currentClusterId: Long, newClusterId: Long) {
        val summariesManager = WorkoutSummariesDatabaseManager.getInstance(context)
        val start = summariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.START)
        val end = summariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.END)
        val apex = summariesManager.getExtremaPosition(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX)
        val distance = summariesManager.getDouble(workoutId, WorkoutSummaries.DISTANCE_TOTAL_m)
        if (start == null || end == null || apex == null || distance == null) return
        val oldCluster = dbManager.getClusterById(currentClusterId)
        if (oldCluster != null && oldCluster.hitCount > 1) {
            dbManager.updateCluster(oldCluster.copy(
                startLat = (oldCluster.startLat * oldCluster.hitCount - start.latitude) / (oldCluster.hitCount - 1),
                startLng = (oldCluster.startLng * oldCluster.hitCount - start.longitude) / (oldCluster.hitCount - 1),
                endLat = (oldCluster.endLat * oldCluster.hitCount - end.latitude) / (oldCluster.hitCount - 1),
                endLng = (oldCluster.endLng * oldCluster.hitCount - end.longitude) / (oldCluster.hitCount - 1),
                maxDispLat = (oldCluster.maxDispLat * oldCluster.hitCount - apex.latitude) / (oldCluster.hitCount - 1),
                maxDispLng = (oldCluster.maxDispLng * oldCluster.hitCount - apex.longitude) / (oldCluster.hitCount - 1),
                refDistance = (oldCluster.refDistance * oldCluster.hitCount - distance) / (oldCluster.hitCount - 1)
            ))
            val mostFrequentSport = summariesManager.getMostFrequentSportIdForCluster(oldCluster.id)
            if (mostFrequentSport != -1L && mostFrequentSport != oldCluster.probableSportId) {
                dbManager.updateCluster(oldCluster.copy(probableSportId = mostFrequentSport, bSportType = SportTypeDatabaseManager.getInstance(context).getBSportType(mostFrequentSport)))
            }
        }
        val newCluster = dbManager.getClusterById(newClusterId)
        if (newCluster != null) {
            dbManager.updateCluster(newCluster.copy(
                startLat = (newCluster.startLat * newCluster.hitCount + start.latitude) / (newCluster.hitCount + 1),
                startLng = (newCluster.startLng * newCluster.hitCount + start.longitude) / (newCluster.hitCount + 1),
                endLat = (newCluster.endLat * newCluster.hitCount + end.latitude) / (newCluster.hitCount + 1),
                endLng = (newCluster.endLng * newCluster.hitCount + end.longitude) / (newCluster.hitCount + 1),
                maxDispLat = (newCluster.maxDispLat * newCluster.hitCount + apex.latitude) / (newCluster.hitCount + 1),
                maxDispLng = (newCluster.maxDispLng * newCluster.hitCount + apex.longitude) / (newCluster.hitCount + 1),
                refDistance = (newCluster.refDistance * newCluster.hitCount + distance) / (newCluster.hitCount + 1)
            ))
            val mostFrequentSport = summariesManager.getMostFrequentSportIdForCluster(newCluster.id)
            if (mostFrequentSport != -1L && mostFrequentSport != newCluster.probableSportId) {
                dbManager.updateCluster(newCluster.copy(probableSportId = mostFrequentSport, bSportType = SportTypeDatabaseManager.getInstance(context).getBSportType(mostFrequentSport)))
            }
        }
        assignClusterToWorkout(context, workoutId, newClusterId)
    }

    fun manuallyCreateCluster(name: String, sportId: Long, start: LatLng, end: LatLng, apex: LatLng, distance: Double): Long {
        return dbManager.insertCluster(WorkoutCluster(
            name = findUniqueClusterName(name), probableSportId = sportId, startLat = start.latitude, startLng = start.longitude,
            endLat = end.latitude, endLng = end.longitude, maxDispLat = apex.latitude, maxDispLng = apex.longitude,
            refDistance = distance, hitCount = 0, bSportType = SportTypeDatabaseManager.getInstance(appContext).getBSportType(sportId)
        ))
    }

    fun distanceBetween(p1: LatLng, p2: LatLng): Float {
        val res = FloatArray(1); Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, res); return res[0]
    }

    private fun isDefaultName(name: String): Boolean {
        val format = appContext.getString(R.string.cluster_default_name_format)
        val parts = format.split("%s")
        if (parts.size < 2) return name.contains(appContext.getString(R.string.unknown_manufacturer))
        val prefix = parts[0]; val suffix = parts[1]
        val isMatch = when {
            prefix.isNotEmpty() && suffix.isNotEmpty() -> name.startsWith(prefix) && name.endsWith(suffix)
            prefix.isNotEmpty() -> name.startsWith(prefix)
            suffix.isNotEmpty() -> name.endsWith(suffix)
            else -> false
        }
        return isMatch || name.contains(appContext.getString(R.string.unknown_manufacturer))
    }
}
