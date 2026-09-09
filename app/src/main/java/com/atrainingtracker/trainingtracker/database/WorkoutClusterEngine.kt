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

/**
 * The core logic engine for discovery and management of recurring route families (Workout Clusters).
 *
 * This engine implements the spatial fingerprinting algorithm used to group similar workouts
 * together. It maintains the "Favorite Tracks" knowledge base by:
 * 1. **Similarity Scoring**: Calculating a weighted mathematical score based on Start, End, Apex, and Distance.
 * 2. **Sport-Aware Grouping**: Optionally isolating different activities (e.g., Run vs. Bike) on the same path.
 * 3. **Identity Learning**: Reactively updating cluster centroids and probable sport/names from user feedback.
 *
 * Architectural Role: Business logic layer for spatial pattern recognition.
 * Threading: Operations should be executed on background threads due to database intensity.
 */
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

        @androidx.annotation.VisibleForTesting
        fun resetForTesting(newInstance: WorkoutClusterEngine? = null) {
            instance = newInstance
        }

        /**
         * Formats the user-facing workout name derived from a WorkoutCluster (REQ-SET-066, REQ-SET-067, ATT-785, ATT-714).
         *
         * If [hasCounter] is false or [hitCount] <= 1, returns the clean [clusterName] without any numerical suffix.
         * If [hasCounter] is true and [hitCount] >= 2, formats the name with the localized cluster_autoname_format (e.g., "%s #%d").
         *
         * @param context Application context for string resource resolution.
         * @param clusterName Base name of the workout cluster.
         * @param hitCount 1-based sequential display count of the workout within this cluster.
         * @param hasCounter Whether the cluster has sequential numbering enabled (defaults to true).
         * @return Formatted workout name string.
         */
        @JvmStatic
        @JvmOverloads
        fun formatClusterWorkoutName(
            context: Context,
            clusterName: String,
            hitCount: Int,
            hasCounter: Boolean = true
        ): String {
            return if (!hasCounter || hitCount <= 1) {
                clusterName
            } else {
                context.getString(R.string.cluster_autoname_format, clusterName, hitCount)
            }
        }

        fun distanceBetween(p1: LatLng, p2: LatLng): Float {
            return try {
                val res = FloatArray(1)
                Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, res)
                res[0]
            } catch (e: RuntimeException) {
                // Fallback for JVM unit test execution
                val earthRadius = 6371000.0
                val dLat = Math.toRadians(p2.latitude - p1.latitude)
                val dLon = Math.toRadians(p2.longitude - p1.longitude)
                val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                        Math.cos(Math.toRadians(p1.latitude)) * Math.cos(Math.toRadians(p2.latitude)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2)
                val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
                (earthRadius * c).toFloat()
            }
        }
    }

    fun distanceBetween(p1: LatLng, p2: LatLng): Float = Companion.distanceBetween(p1, p2)

    /**
     * Suggests a cluster match for a workout based on spatial shape metrics, sport type, and optional name.
     */
    /**
     * Finds the best matching WorkoutCluster candidate evaluating against a set of candidate sport types (ATT-773, REQ-SET-064)
     * and optional 3D altitude extrema coordinates (ATT-502, REQ-SET-065).
     */
    fun suggestCluster(
        start: LatLng, end: LatLng, apex: LatLng, distance: Double, 
        workoutName: String? = null, 
        candidateSportTypes: Set<BSportType>,
        minAltPos: LatLng? = null,
        maxAltPos: LatLng? = null
    ): WorkoutCluster? {
        val endpointTol = TrainingApplication.getClusterTolEndpoints().toDouble()
        val latToleranceDegrees = endpointTol / 111000.0
        val distToleranceMeters = distance * TrainingApplication.getClusterTolDistance().toDouble() * 4.0

        val candidates = dbManager.findCandidates(start.latitude, start.longitude, distance, latToleranceDegrees, distToleranceMeters)
        if (DEBUG) Log.d(TAG, "Found ${candidates.size} candidates for shape [start=$start, dist=$distance, name=$workoutName, sports=$candidateSportTypes, minAlt=$minAltPos, maxAlt=$maxAltPos]")

        return candidates.map { cluster ->
            val score = calculateSimilarity(start, end, apex, distance, cluster, workoutName, candidateSportTypes, minAltPos, maxAltPos)
            cluster to score
        }.filter { it.second < 1.0 }
         .minByOrNull { it.second }?.first
    }

    /**
     * Backward-compatible overload for single sport type.
     */
    @JvmOverloads
    fun suggestCluster(
        start: LatLng, end: LatLng, apex: LatLng, distance: Double, 
        workoutName: String? = null, 
        workoutSportType: BSportType = BSportType.UNKNOWN,
        minAltPos: LatLng? = null,
        maxAltPos: LatLng? = null
    ): WorkoutCluster? {
        val candidateSports = if (workoutSportType != BSportType.UNKNOWN) setOf(workoutSportType) else emptySet()
        return suggestCluster(start, end, apex, distance, workoutName, candidateSports, minAltPos, maxAltPos)
    }

    /**
     * Records user feedback (name/sport edit) or route seeding to update or create clusters.
     */
    @JvmOverloads
    fun learnFromWorkout(
        start: LatLng, end: LatLng, apex: LatLng, distance: Double, 
        userSpecifiedName: String, userSportId: Long, 
        clusterIdOverride: Long = -1,
        minLat: Double? = null, minLng: Double? = null,
        maxLat: Double? = null, maxLng: Double? = null,
        minAltPos: LatLng? = null, maxAltPos: LatLng? = null
    ): Long {
        val workoutSportType = SportTypeDatabaseManager.getInstance(appContext).getBSportType(userSportId)
        val normalizedInputName = stripHitCount(userSpecifiedName)
        val existingMatch = if (clusterIdOverride != -1L) dbManager.getClusterById(clusterIdOverride) 
                            else (suggestCluster(start, end, apex, distance, userSpecifiedName, workoutSportType, minAltPos, maxAltPos)
                                ?: dbManager.getClusterByName(normalizedInputName))

        return if (existingMatch != null) {
            val normalizedInputName = stripHitCount(userSpecifiedName)
            val uniqueName = if (clusterIdOverride != -1L && !isDefaultName(existingMatch.name)) {
                existingMatch.name
            } else {
                if (existingMatch.name == normalizedInputName) normalizedInputName
                else findUniqueClusterName(normalizedInputName, existingMatch.id)
            }

            // ATT-502: Running centroid update for min and max altitude coordinates
            val updatedMinAltLat = if (minAltPos != null) {
                if (existingMatch.minAltLat != null) (existingMatch.minAltLat * existingMatch.hitCount + minAltPos.latitude) / (existingMatch.hitCount + 1)
                else minAltPos.latitude
            } else existingMatch.minAltLat
            val updatedMinAltLng = if (minAltPos != null) {
                if (existingMatch.minAltLng != null) (existingMatch.minAltLng * existingMatch.hitCount + minAltPos.longitude) / (existingMatch.hitCount + 1)
                else minAltPos.longitude
            } else existingMatch.minAltLng

            val updatedMaxAltLat = if (maxAltPos != null) {
                if (existingMatch.maxAltLat != null) (existingMatch.maxAltLat * existingMatch.hitCount + maxAltPos.latitude) / (existingMatch.hitCount + 1)
                else maxAltPos.latitude
            } else existingMatch.maxAltLat
            val updatedMaxAltLng = if (maxAltPos != null) {
                if (existingMatch.maxAltLng != null) (existingMatch.maxAltLng * existingMatch.hitCount + maxAltPos.longitude) / (existingMatch.hitCount + 1)
                else maxAltPos.longitude
            } else existingMatch.maxAltLng

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
                refDistance = (existingMatch.refDistance * existingMatch.hitCount + distance) / (existingMatch.hitCount + 1),
                // ATT-371: Expand existing bounds
                minLat = if (minLat != null) minOf(existingMatch.minLat ?: 90.0, minLat) else existingMatch.minLat,
                minLng = if (minLng != null) minOf(existingMatch.minLng ?: 180.0, minLng) else existingMatch.minLng,
                maxLat = if (maxLat != null) maxOf(existingMatch.maxLat ?: -90.0, maxLat) else existingMatch.maxLat,
                maxLng = if (maxLng != null) maxOf(existingMatch.maxLng ?: -180.0, maxLng) else existingMatch.maxLng,
                // ATT-502
                minAltLat = updatedMinAltLat,
                minAltLng = updatedMinAltLng,
                maxAltLat = updatedMaxAltLat,
                maxAltLng = updatedMaxAltLng
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
                bSportType = SportTypeDatabaseManager.getInstance(appContext).getBSportType(userSportId),
                // ATT-371: Initialize bounds
                minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng,
                // ATT-502
                minAltLat = minAltPos?.latitude,
                minAltLng = minAltPos?.longitude,
                maxAltLat = maxAltPos?.latitude,
                maxAltLng = maxAltPos?.longitude
            )
            dbManager.insertCluster(newCluster)
        }
    }

    /**
     * O(1) Surgical update when a workout is finished (ATT-354).
     */
    /**
     * Identifies the coordinate among the given points that maximizes geodesic
     * distance from the specified start position (REQ-SET-063, ATT-498).
     */
    fun findApexFromPoints(start: LatLng, points: List<LatLng>): LatLng {
        if (points.isEmpty()) return start
        var maxDist = -1.0
        var apex = points.first()
        for (pt in points) {
            val d = distanceBetween(start, pt).toDouble()
            if (d > maxDist) {
                maxDist = d
                apex = pt
            }
        }
        return apex
    }

    fun onWorkoutFinished(context: Context, w: WorkoutData) {
        // REQ-MIG-025: Strictly respect unclustered status and never auto-create fallback clusters
        if (w.clusterId == -1L) return
        if (w.startLatLng == null || w.endLatLng == null) return // Ignore non-spatial
        
        val currentMatch = dbManager.getClusterById(w.clusterId) ?: return
        val apex = w.maxDisplacementLatLng ?: w.endLatLng

        // --- ATT-354 Refinement: Null-Safe Bounds Update ---
        val wMinLat = w.minLat; val wMinLng = w.minLng; val wMaxLat = w.maxLat; val wMaxLng = w.maxLng
        
        val minAltPos = w.minAltitudeLatLng ?: WorkoutSummariesDatabaseManager.getInstance(context).getExtremaPosition(w.id, SensorType.ALTITUDE, ExtremaType.MIN)
        val maxAltPos = w.maxAltitudeLatLng ?: WorkoutSummariesDatabaseManager.getInstance(context).getExtremaPosition(w.id, SensorType.ALTITUDE, ExtremaType.MAX)

        val updatedMinAltLat = if (minAltPos != null) {
            if (currentMatch.minAltLat != null) (currentMatch.minAltLat * currentMatch.hitCount + minAltPos.latitude) / (currentMatch.hitCount + 1)
            else minAltPos.latitude
        } else currentMatch.minAltLat
        val updatedMinAltLng = if (minAltPos != null) {
            if (currentMatch.minAltLng != null) (currentMatch.minAltLng * currentMatch.hitCount + minAltPos.longitude) / (currentMatch.hitCount + 1)
            else minAltPos.longitude
        } else currentMatch.minAltLng

        val updatedMaxAltLat = if (maxAltPos != null) {
            if (currentMatch.maxAltLat != null) (currentMatch.maxAltLat * currentMatch.hitCount + maxAltPos.latitude) / (currentMatch.hitCount + 1)
            else maxAltPos.latitude
        } else currentMatch.maxAltLat
        val updatedMaxAltLng = if (maxAltPos != null) {
            if (currentMatch.maxAltLng != null) (currentMatch.maxAltLng * currentMatch.hitCount + maxAltPos.longitude) / (currentMatch.hitCount + 1)
            else maxAltPos.longitude
        } else currentMatch.maxAltLng

        val updated = currentMatch.copy(
            startLat = (currentMatch.startLat * currentMatch.hitCount + w.startLatLng.latitude) / (currentMatch.hitCount + 1),
            startLng = (currentMatch.startLng * currentMatch.hitCount + w.startLatLng.longitude) / (currentMatch.hitCount + 1),
            endLat = (currentMatch.endLat * currentMatch.hitCount + w.endLatLng.latitude) / (currentMatch.hitCount + 1),
            endLng = (currentMatch.endLng * currentMatch.hitCount + w.endLatLng.longitude) / (currentMatch.hitCount + 1),
            maxDispLat = (currentMatch.maxDispLat * currentMatch.hitCount + apex.latitude) / (currentMatch.hitCount + 1),
            maxDispLng = (currentMatch.maxDispLng * currentMatch.hitCount + apex.longitude) / (currentMatch.hitCount + 1),
            refDistance = (currentMatch.refDistance * currentMatch.hitCount + w.totalDistance) / (currentMatch.hitCount + 1),
            
            minLat = if (wMinLat != null) minOf(currentMatch.minLat ?: 90.0, wMinLat) else currentMatch.minLat,
            minLng = if (wMinLng != null) minOf(currentMatch.minLng ?: 180.0, wMinLng) else currentMatch.minLng,
            maxLat = if (wMaxLat != null) maxOf(currentMatch.maxLat ?: -90.0, wMaxLat) else currentMatch.maxLat,
            maxLng = if (wMaxLng != null) maxOf(currentMatch.maxLng ?: -180.0, wMaxLng) else currentMatch.maxLng,

            minAltLat = updatedMinAltLat,
            minAltLng = updatedMinAltLng,
            maxAltLat = updatedMaxAltLat,
            maxAltLng = updatedMaxAltLng
        )
        dbManager.updateCluster(updated)
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

        val isAnchor = w.minLat == cluster.minLat || w.maxLat == cluster.maxLat ||
                       w.minLng == cluster.minLng || w.maxLng == cluster.maxLng

        if (isAnchor) {
            Log.i(TAG, "Surgical Recalc: Deleting spatial anchor ${w.id} from cluster ${cluster.name}. Recalculating family envelope.")
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

    /**
     * Surgically recalculates the spatial anchors (Bounds) for a single cluster (ATT-354/371).
     */
    fun recalculateClusterAnchors(context: Context, cluster: WorkoutCluster) {
        val summariesManager = WorkoutSummariesDatabaseManager.getInstance(context)
        val db = summariesManager.getDatabase()
        val cursor = db.query(WorkoutSummaries.TABLE, 
            arrayOf(WorkoutSummaries.C_ID, WorkoutSummaries.TIME_ACTIVE_s, 
                    WorkoutSummaries.BOUND_MIN_LAT, WorkoutSummaries.BOUND_MIN_LNG, 
                    WorkoutSummaries.BOUND_MAX_LAT, WorkoutSummaries.BOUND_MAX_LNG),
            "${WorkoutSummaries.CLUSTER_ID} = ?", arrayOf(cluster.id.toString()), null, null, null)

        var minLat = 90.0; var maxLat = -90.0; var minLng = 180.0; var maxLng = -180.0
        var hasPoints = false

        cursor.use { c ->
            val minLatIdx = c.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MIN_LAT)
            val minLngIdx = c.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MIN_LNG)
            val maxLatIdx = c.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MAX_LAT)
            val maxLngIdx = c.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MAX_LNG)

            while (c.moveToNext()) {
                val wMinLat = if (c.isNull(minLatIdx)) null else c.getDouble(minLatIdx)
                val wMinLng = if (c.isNull(minLngIdx)) null else c.getDouble(minLngIdx)
                val wMaxLat = if (c.isNull(maxLatIdx)) null else c.getDouble(maxLatIdx)
                val wMaxLng = if (c.isNull(maxLngIdx)) null else c.getDouble(maxLngIdx)

                if (wMinLat != null && wMinLat < minLat) minLat = wMinLat
                if (wMaxLat != null && wMaxLat > maxLat) maxLat = wMaxLat
                if (wMinLng != null && wMinLng < minLng) minLng = wMinLng
                if (wMaxLng != null && wMaxLng > maxLng) maxLng = wMaxLng
                if (wMinLat != null) hasPoints = true
            }
        }

        val refreshed = cluster.copy(
            minLat = if (hasPoints) minLat else null,
            minLng = if (hasPoints) minLng else null,
            maxLat = if (hasPoints) maxLat else null,
            maxLng = if (hasPoints) maxLng else null
        )
        dbManager.updateCluster(refreshed)
        
        val mostFrequentSport = summariesManager.getMostFrequentSportIdForCluster(refreshed.id)
        if (mostFrequentSport != -1L) {
            val bSport = SportTypeDatabaseManager.getInstance(context).getBSportType(mostFrequentSport)
            dbManager.updateCluster(refreshed.copy(probableSportId = mostFrequentSport, bSportType = bSport))
        }
    }

    /**
     * Non-destructive enrichment of all existing clusters with full spatial bounds (ATT-371).
     */
    fun enrichAllClusterMetadata(context: Context, listener: ClusterMigrationListener? = null) {
        val clusters = dbManager.getAllClusters()
        clusters.forEachIndexed { index, cluster ->
            listener?.onPhase2Progress(index + 1, clusters.size)
            recalculateClusterAnchors(context, cluster)
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
        
        var minLat = 90.0; var maxLat = -90.0; var minLng = 180.0; var maxLng = -180.0
        val apex = findApexFromPoints(start, path.map { it.latLng })
        
        path.forEach { point ->
            val lat = point.latLng.latitude
            val lon = point.latLng.longitude
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lon < minLng) minLng = lon
            if (lon > maxLng) maxLng = lon
        }
        
        val sportId = SportTypeDatabaseManager.getSportTypeId(route.summary.bSportType)
        val clusterId = learnFromWorkout(
            start, end, apex, distance, route.summary.name, sportId,
            minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng
        )

        // ATT-498: Authoritative route anchors the cluster apex
        if (clusterId != -1L) {
            val cluster = dbManager.getClusterById(clusterId)
            if (cluster != null) {
                dbManager.updateCluster(cluster.copy(maxDispLat = apex.latitude, maxDispLng = apex.longitude))
            }
        }
        return clusterId
    }

    /**
     * O(1) Hierarchical sync pass for history (ATT-371/392).
     */
    @JvmOverloads
    fun migrateHistory(context: Context, listener: ClusterMigrationListener? = null) {
        val routesDb = RoutesDatabaseManager.getInstance(context)
        val routes = routesDb.getAllRoutes()
        routes.forEachIndexed { index, routeWithPath ->
            listener?.onPhase1Progress(index + 1, routes.size)
            val clusterId = learnFromRoute(routeWithPath)
            if (clusterId != -1L) {
                routesDb.updateRouteSummary(routeWithPath.summary.copy(clusterId = clusterId))
            }
        }
        
        // Ensure Phase 1 hits 100% if no routes existed
        if (routes.isEmpty()) {
            listener?.onPhase1Progress(0, 0)
        }

        val summariesManager = WorkoutSummariesDatabaseManager.getInstance(context)
        val cursor = summariesManager.getCursorForAllWorkoutsAsc() ?: return

        cursor.use { c ->
            val total = c.count
            val idIdx = c.getColumnIndexOrThrow(WorkoutSummaries.C_ID)
            val nameIdx = c.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_NAME)
            val fileIdx = c.getColumnIndexOrThrow(WorkoutSummaries.FILE_BASE_NAME)
            val sportIdx = c.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID)
            val distIdx = c.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_TOTAL_m)

            var count = 0
            while (c.moveToNext()) {
                count++
                if (count % 5 == 0 || count == total) {
                    listener?.onPhase2Progress(count, total)
                }

                val workoutId = c.getLong(idIdx)
                val workoutName = c.getString(nameIdx)
                val fileBaseName = c.getString(fileIdx)
                val sportId = c.getLong(sportIdx)
                val distance = c.getDouble(distIdx)

                val start = summariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.START)
                val end = summariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.END)
                val apex = summariesManager.getExtremaPosition(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX)
                val minAltPos = summariesManager.getExtremaPosition(workoutId, SensorType.ALTITUDE, ExtremaType.MIN)
                val maxAltPos = summariesManager.getExtremaPosition(workoutId, SensorType.ALTITUDE, ExtremaType.MAX)

                if (start != null && end != null && apex != null && distance > 100.0) {
                    val isDefault = workoutName.isNullOrEmpty() || workoutName == fileBaseName
                    val normalizedName = if (!isDefault) stripHitCount(workoutName) else null
                    val workoutSportType = SportTypeDatabaseManager.getInstance(context).getBSportType(sportId)
                    
                    val match = suggestCluster(start, end, apex, distance, normalizedName, workoutSportType, minAltPos, maxAltPos)
                    if (match != null) {
                        val rawName = normalizedName ?: match.name
                        val finalName = if (match.name == rawName) rawName else findUniqueClusterName(rawName, match.id)
                        val finalSport = if (!isDefault) sportId else match.probableSportId

                        // ATT-354: Migrate bounds
                        val wMinLat = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MIN_LAT)
                        val wMinLng = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MIN_LNG)
                        val wMaxLat = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MAX_LAT)
                        val wMaxLng = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MAX_LNG)

                        val minLat = if (wMinLat != null) minOf(match.minLat ?: 90.0, wMinLat) else match.minLat
                        val minLng = if (wMinLng != null) minOf(match.minLng ?: 180.0, wMinLng) else match.minLng
                        val maxLat = if (wMaxLat != null) maxOf(match.maxLat ?: -90.0, wMaxLat) else match.maxLat
                        val maxLng = if (wMaxLng != null) maxOf(match.maxLng ?: -180.0, wMaxLng) else match.maxLng

                        val updatedMinAltLat = if (minAltPos != null) {
                            if (match.minAltLat != null) (match.minAltLat * match.hitCount + minAltPos.latitude) / (match.hitCount + 1)
                            else minAltPos.latitude
                        } else match.minAltLat
                        val updatedMinAltLng = if (minAltPos != null) {
                            if (match.minAltLng != null) (match.minAltLng * match.hitCount + minAltPos.longitude) / (match.hitCount + 1)
                            else minAltPos.longitude
                        } else match.minAltLng

                        val updatedMaxAltLat = if (maxAltPos != null) {
                            if (match.maxAltLat != null) (match.maxAltLat * match.hitCount + maxAltPos.latitude) / (match.hitCount + 1)
                            else maxAltPos.latitude
                        } else match.maxAltLat
                        val updatedMaxAltLng = if (maxAltPos != null) {
                            if (match.maxAltLng != null) (match.maxAltLng * match.hitCount + maxAltPos.longitude) / (match.hitCount + 1)
                            else maxAltPos.longitude
                        } else match.maxAltLng

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
                            minAltLat = updatedMinAltLat,
                            minAltLng = updatedMinAltLng,
                            maxAltLat = updatedMaxAltLat,
                            maxAltLng = updatedMaxAltLng
                        )
                        dbManager.updateCluster(updated)
                        assignClusterToWorkout(context, workoutId, updated.id)
                    } else {
                        val clusterName = if (!isDefault) normalizedName!! else context.getString(R.string.cluster_default_name_format, fileBaseName?.take(10) ?: "Workout")
                        
                        val wMinLat = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MIN_LAT)
                        val wMinLng = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MIN_LNG)
                        val wMaxLat = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MAX_LAT)
                        val wMaxLng = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MAX_LNG)

                        val newId = learnFromWorkout(
                            start, end, apex, distance, clusterName, sportId,
                            minLat = wMinLat, minLng = wMinLng, maxLat = wMaxLat, maxLng = wMaxLng,
                            minAltPos = minAltPos, maxAltPos = maxAltPos
                        )
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
        if (clusterId <= 0L) {
            unassignClusterFromWorkout(context, workoutId)
            return
        }
        val summariesManager = WorkoutSummariesDatabaseManager.getInstance(context)
        val clusterDb = WorkoutClusterDatabaseManager.getInstance(context)
        val previousClusterId = summariesManager.getLong(workoutId, WorkoutSummaries.CLUSTER_ID) ?: -1L
        val cluster = clusterDb.getClusterById(clusterId) ?: return

        // 1. Update WorkoutSummaries first so it remains the authoritative single source of truth
        val values = android.content.ContentValues().apply {
            put(WorkoutSummaries.CLUSTER_ID, clusterId)
            val currentName = summariesManager.getString(workoutId, WorkoutSummaries.WORKOUT_NAME)
            val fileBaseName = summariesManager.getString(workoutId, WorkoutSummaries.FILE_BASE_NAME)
            if (forceIdentity || currentName.isNullOrEmpty() || currentName == fileBaseName) {
                val displayCount = if (previousClusterId == clusterId) cluster.hitCount else cluster.hitCount + 1
                put(WorkoutSummaries.WORKOUT_NAME, formatClusterWorkoutName(context, cluster.name, displayCount, cluster.hasCounter))
            }
        }
        summariesManager.database.update(WorkoutSummaries.TABLE, values, "${WorkoutSummaries.C_ID} = ?", arrayOf(workoutId.toString()))

        // 2. Decrement/reconcile the old cluster if changed (ATT-318)
        if (previousClusterId != -1L && previousClusterId != clusterId) {
            val oldCluster = clusterDb.getClusterById(previousClusterId)
            if (oldCluster != null) {
                val realOldCount = summariesManager.getWorkoutCountForCluster(previousClusterId)
                val newOldHitCount = if (realOldCount >= 0) realOldCount else (oldCluster.hitCount - 1).coerceAtLeast(0)
                val updatedOld = oldCluster.copy(
                    hitCount = newOldHitCount,
                    previewPaths = if (newOldHitCount == 0) emptyList() else oldCluster.previewPaths
                )
                clusterDb.updateCluster(updatedOld)
            }
        }

        // 3. Increment/reconcile new cluster hitCount from authoritative WorkoutSummaries (ATT-318)
        val polyline = summariesManager.getString(workoutId, WorkoutSummaries.MAP_POLYLINE)
        val realCount = summariesManager.getWorkoutCountForCluster(clusterId)
        val newHitCount = if (realCount > 0) realCount else (if (previousClusterId != clusterId) cluster.hitCount + 1 else cluster.hitCount)
        val newPreviews = if (!polyline.isNullOrEmpty() && !cluster.previewPaths.contains(polyline)) {
            (listOf(polyline) + cluster.previewPaths).take(5)
        } else cluster.previewPaths
        
        val refreshedCluster = cluster.copy(hitCount = newHitCount, previewPaths = newPreviews)
        clusterDb.updateCluster(refreshedCluster)

        val sportStr = summariesManager.getString(workoutId, WorkoutSummaries.B_SPORT)
        val currentBSport = if (!sportStr.isNullOrBlank()) {
            try {
                BSportType.valueOf(sportStr)
            } catch (e: Exception) {
                BSportType.UNKNOWN
            }
        } else BSportType.UNKNOWN
        val avgSpeed = summariesManager.getDouble(workoutId, WorkoutSummaries.SPEED_AVERAGE_mps) ?: 0.0
        val discoveryManager = EquipmentAndSportTypeDiscoveryManager.getInstance(context)
        val hardwareIdentity = discoveryManager.resolveIdentity(workoutId, currentBSport, avgSpeed)
        if (!forceIdentity && hardwareIdentity.isHighConfidence) summariesManager.applyInferredIdentity(workoutId, hardwareIdentity)
        else summariesManager.applyInferredIdentity(workoutId, discoveryManager.inferIdentityFromSport(cluster.probableSportId))
    }

    /**
     * Unassigns a workout from its workout cluster, atomically decrementing the cluster's hit count (REQ-SET-059, ATT-318).
     */
    fun unassignClusterFromWorkout(context: Context, workoutId: Long) {
        val summariesManager = WorkoutSummariesDatabaseManager.getInstance(context)
        val clusterDb = WorkoutClusterDatabaseManager.getInstance(context)
        val previousClusterId = summariesManager.getLong(workoutId, WorkoutSummaries.CLUSTER_ID) ?: -1L
        
        val values = android.content.ContentValues().apply {
            put(WorkoutSummaries.CLUSTER_ID, -1L)
        }
        summariesManager.database.update(WorkoutSummaries.TABLE, values, "${WorkoutSummaries.C_ID} = ?", arrayOf(workoutId.toString()))

        if (previousClusterId > 0L) {
            val oldCluster = clusterDb.getClusterById(previousClusterId)
            if (oldCluster != null) {
                val realOldCount = summariesManager.getWorkoutCountForCluster(previousClusterId)
                val newOldHitCount = if (realOldCount >= 0) realOldCount else (oldCluster.hitCount - 1).coerceAtLeast(0)
                val updatedOld = oldCluster.copy(
                    hitCount = newOldHitCount,
                    previewPaths = if (newOldHitCount == 0) emptyList() else oldCluster.previewPaths
                )
                clusterDb.updateCluster(updatedOld)
            }
        }
    }

    /**
     * Creates a new workout cluster from the workout's spatial fingerprint and assigns the workout to it (REQ-SET-059).
     */
    fun createNewClusterFromWorkout(context: Context, workout: WorkoutData, customName: String? = null, hasCounter: Boolean = true): Long {
        val summariesManager = WorkoutSummariesDatabaseManager.getInstance(context)
        val start = workout.startLatLng ?: summariesManager.getExtremaPosition(workout.id, SensorType.LATITUDE, ExtremaType.START) ?: return -1L
        val end = workout.endLatLng ?: summariesManager.getExtremaPosition(workout.id, SensorType.LATITUDE, ExtremaType.END) ?: return -1L
        val apex = workout.maxDisplacementLatLng ?: summariesManager.getExtremaPosition(workout.id, SensorType.LINE_DISTANCE_m, ExtremaType.MAX) ?: end
        val distance = workout.totalDistance
        val sportId = workout.sportId
        val fallbackName = workout.workoutName.ifBlank { context.getString(R.string.cluster_default_name_format, "Workout") }
        val clusterName = if (!customName.isNullOrBlank()) customName else fallbackName

        val minAltPos = workout.minAltitudeLatLng ?: summariesManager.getExtremaPosition(workout.id, SensorType.ALTITUDE, ExtremaType.MIN)
        val maxAltPos = workout.maxAltitudeLatLng ?: summariesManager.getExtremaPosition(workout.id, SensorType.ALTITUDE, ExtremaType.MAX)

        val uniqueName = findUniqueClusterName(stripHitCount(clusterName))
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
            bSportType = SportTypeDatabaseManager.getInstance(context).getBSportType(sportId),
            minLat = workout.minLat, minLng = workout.minLng, maxLat = workout.maxLat, maxLng = workout.maxLng,
            minAltLat = minAltPos?.latitude,
            minAltLng = minAltPos?.longitude,
            maxAltLat = maxAltPos?.latitude,
            maxAltLng = maxAltPos?.longitude,
            hasCounter = hasCounter
        )
        val newClusterId = dbManager.insertCluster(newCluster)
        assignClusterToWorkout(context, workout.id, newClusterId, forceIdentity = false)
        return newClusterId
    }

    /**
     * Creates a new workout cluster from raw database metadata and assigns the workout to it (REQ-SET-059).
     */
    fun createNewClusterFromWorkout(context: Context, workoutId: Long, customName: String? = null, hasCounter: Boolean = true): Long {
        val summariesManager = WorkoutSummariesDatabaseManager.getInstance(context)
        val start = summariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.START) ?: return -1L
        val end = summariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.END) ?: return -1L
        val apex = summariesManager.getExtremaPosition(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX) ?: end
        val distance = summariesManager.getDouble(workoutId, WorkoutSummaries.DISTANCE_TOTAL_m) ?: 0.0
        val sportId = summariesManager.getLong(workoutId, WorkoutSummaries.SPORT_ID) ?: -1L
        val wMinLat = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MIN_LAT)
        val wMinLng = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MIN_LNG)
        val wMaxLat = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MAX_LAT)
        val wMaxLng = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MAX_LNG)
        val workoutName = summariesManager.getString(workoutId, WorkoutSummaries.WORKOUT_NAME)
            ?: context.getString(R.string.cluster_default_name_format, "Workout")
        val clusterName = if (!customName.isNullOrBlank()) customName else workoutName

        val minAltPos = summariesManager.getExtremaPosition(workoutId, SensorType.ALTITUDE, ExtremaType.MIN)
        val maxAltPos = summariesManager.getExtremaPosition(workoutId, SensorType.ALTITUDE, ExtremaType.MAX)

        val uniqueName = findUniqueClusterName(stripHitCount(clusterName))
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
            bSportType = SportTypeDatabaseManager.getInstance(context).getBSportType(sportId),
            minLat = wMinLat, minLng = wMinLng, maxLat = wMaxLat, maxLng = wMaxLng,
            minAltLat = minAltPos?.latitude,
            minAltLng = minAltPos?.longitude,
            maxAltLat = maxAltPos?.latitude,
            maxAltLng = maxAltPos?.longitude,
            hasCounter = hasCounter
        )
        val newClusterId = dbManager.insertCluster(newCluster)
        assignClusterToWorkout(context, workoutId, newClusterId, forceIdentity = false)
        return newClusterId
    }

    /**
     * Calculates similarity between a spatial signature and a WorkoutCluster,
     * supporting multi-sport candidate evaluation (ATT-773, REQ-SET-064).
     */
    /**
     * Calculates similarity between a spatial signature and a WorkoutCluster,
     * supporting multi-sport candidate evaluation (ATT-773, REQ-SET-064)
     * and optional 3D altitude extrema coordinates (ATT-502, REQ-SET-065).
     */
    fun calculateSimilarity(
        start: LatLng, end: LatLng, apex: LatLng, distance: Double, 
        cluster: WorkoutCluster, 
        workoutName: String? = null,
        candidateSportTypes: Set<BSportType>,
        minAltPos: LatLng? = null,
        maxAltPos: LatLng? = null
    ): Double {
        val useAlt = minAltPos != null && maxAltPos != null &&
                     cluster.minAltLatLng != null && cluster.maxAltLatLng != null &&
                     try { TrainingApplication.useAltitudePosForClustering() } catch (e: Exception) { true }

        var totalScore = if (useAlt) {
            val tolEndpoints = TrainingApplication.getClusterTolEndpoints()
            val tolApex = TrainingApplication.getClusterTolApex()
            val tolDistance = TrainingApplication.getClusterTolDistance()
            val tolAlt = try { TrainingApplication.getClusterTolAltitudePos() } catch (e: Exception) { 400f }

            val s1 = (distanceBetween(start, LatLng(cluster.startLat, cluster.startLng)) / tolEndpoints) * 0.20
            val s2 = (distanceBetween(end, LatLng(cluster.endLat, cluster.endLng)) / tolEndpoints) * 0.20
            val s3 = (distanceBetween(apex, LatLng(cluster.maxDispLat, cluster.maxDispLng)) / tolApex) * 0.20
            val s4 = (Math.abs(distance - cluster.refDistance) / cluster.refDistance / tolDistance) * 0.20
            val sMinAlt = (distanceBetween(minAltPos, cluster.minAltLatLng!!) / tolAlt) * 0.10
            val sMaxAlt = (distanceBetween(maxAltPos, cluster.maxAltLatLng!!) / tolAlt) * 0.10
            s1 + s2 + s3 + s4 + sMinAlt + sMaxAlt
        } else {
            val s1 = (distanceBetween(start, LatLng(cluster.startLat, cluster.startLng)) / TrainingApplication.getClusterTolEndpoints()) * 0.25
            val s2 = (distanceBetween(end, LatLng(cluster.endLat, cluster.endLng)) / TrainingApplication.getClusterTolEndpoints()) * 0.25
            val s3 = (distanceBetween(apex, LatLng(cluster.maxDispLat, cluster.maxDispLng)) / TrainingApplication.getClusterTolApex()) * 0.25
            val s4 = (Math.abs(distance - cluster.refDistance) / cluster.refDistance / TrainingApplication.getClusterTolDistance()) * 0.25
            s1 + s2 + s3 + s4
        }
        
        // ATT-412 / ATT-773: Tiered Sport Type Awareness with Multi-Sport Candidate Evaluation
        if (TrainingApplication.useSportTypeForClustering()) {
            if (cluster.bSportType in candidateSportTypes) {
                // Zero penalty if cluster matches any candidate sport supported by speed/hardware
            } else if (candidateSportTypes.isEmpty()) {
                if (cluster.bSportType != BSportType.UNKNOWN) {
                    totalScore += 2.0
                }
            } else {
                val penalty = if (cluster.bSportType != BSportType.UNKNOWN) 5.0 else 2.0
                totalScore += penalty
            }
        }

        if (workoutName != null) {
            val normalizedWorkout = normalizeName(workoutName); val normalizedCluster = normalizeName(cluster.name)
            if (normalizedWorkout.isNotEmpty() && normalizedWorkout == normalizedCluster) totalScore *= 0.5 
        }
        return totalScore
    }

    /**
     * Backward-compatible single sport type overload.
     */
    @JvmOverloads
    fun calculateSimilarity(
        start: LatLng, end: LatLng, apex: LatLng, distance: Double, 
        cluster: WorkoutCluster, 
        workoutName: String? = null,
        workoutSportType: BSportType = BSportType.UNKNOWN,
        minAltPos: LatLng? = null,
        maxAltPos: LatLng? = null
    ): Double = calculateSimilarity(
        start, end, apex, distance, cluster, workoutName,
        if (workoutSportType != BSportType.UNKNOWN) setOf(workoutSportType) else emptySet(),
        minAltPos, maxAltPos
    )

    private fun normalizeName(name: String): String = name.replace(Regex(" (?:# ?|var )\\d+$", RegexOption.IGNORE_CASE), "").trim().lowercase()
    private fun stripHitCount(name: String): String = name.replace(Regex(" #\\d+$"), "").trim()
    @JvmOverloads
    fun recalculateHistory(context: Context, listener: ClusterMigrationListener? = null) { 
        dbManager.deleteAllClusters()
        migrateHistory(context, listener) 
    }

    fun getClusterScores(
        start: LatLng, end: LatLng, apex: LatLng, distance: Double, 
        workoutName: String? = null, 
        candidateSportTypes: Set<BSportType>,
        minAltPos: LatLng? = null,
        maxAltPos: LatLng? = null
    ): List<Pair<WorkoutCluster, Double>> {
        val summariesManager = WorkoutSummariesDatabaseManager.getInstance(appContext)
        val actualCounts = summariesManager.workoutCountsForAllClusters
        val clusters = dbManager.getAllClusters().map { cluster ->
            val realCount = actualCounts[cluster.id] ?: 0
            if (cluster.hitCount != realCount) {
                val healed = cluster.copy(
                    hitCount = realCount,
                    previewPaths = if (realCount == 0) emptyList() else cluster.previewPaths
                )
                dbManager.updateCluster(healed)
                healed
            } else {
                cluster
            }
        }
        return scoreClusters(clusters, start, end, apex, distance, workoutName, candidateSportTypes, minAltPos, maxAltPos)
    }

    @JvmOverloads
    fun getClusterScores(
        start: LatLng, end: LatLng, apex: LatLng, distance: Double, 
        workoutName: String? = null, 
        workoutSportType: BSportType = BSportType.UNKNOWN,
        minAltPos: LatLng? = null,
        maxAltPos: LatLng? = null
    ): List<Pair<WorkoutCluster, Double>> = getClusterScores(
        start, end, apex, distance, workoutName,
        if (workoutSportType != BSportType.UNKNOWN) setOf(workoutSportType) else emptySet(),
        minAltPos, maxAltPos
    )

    fun scoreClusters(
        clusters: List<WorkoutCluster>, 
        start: LatLng, end: LatLng, apex: LatLng, distance: Double, 
        workoutName: String? = null, 
        candidateSportTypes: Set<BSportType>,
        minAltPos: LatLng? = null,
        maxAltPos: LatLng? = null
    ): List<Pair<WorkoutCluster, Double>> = clusters.map { 
        it to calculateSimilarity(start, end, apex, distance, it, workoutName, candidateSportTypes, minAltPos, maxAltPos) 
    }.sortedBy { it.second }

    @JvmOverloads
    fun scoreClusters(
        clusters: List<WorkoutCluster>, 
        start: LatLng, end: LatLng, apex: LatLng, distance: Double, 
        workoutName: String? = null, 
        workoutSportType: BSportType = BSportType.UNKNOWN,
        minAltPos: LatLng? = null,
        maxAltPos: LatLng? = null
    ): List<Pair<WorkoutCluster, Double>> = scoreClusters(
        clusters, start, end, apex, distance, workoutName,
        if (workoutSportType != BSportType.UNKNOWN) setOf(workoutSportType) else emptySet(),
        minAltPos, maxAltPos
    )

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
