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
import com.atrainingtracker.util.safeEnumValueOf
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil

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
    private val dbManager get() = WorkoutClusterRepository.getInstance(appContext).databaseManager
    internal val clusterLock = Any()

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
            WorkoutClusterRepository.resetForTesting(null)
            com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository.resetForTesting(null)
            com.atrainingtracker.trainingtracker.repositories.SportTypesRepository.resetForTesting(null)
            com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository.resetForTesting(null)
            com.atrainingtracker.trainingtracker.segments.SegmentsRepository.resetForTesting(null)
            com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusRepository.resetForTesting(null)
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

        @JvmStatic
        fun stripHitCount(name: String): String = name.replace(Regex(" #\\d+$"), "").trim()

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
     * Lossless candidate evaluation ensures 100% parity with scoreClusters() and prevents false-negative pruning (ATT-1133, REQ-MIG-029).
     */
    @JvmOverloads
    fun suggestCluster(
        start: LatLng, end: LatLng, apex: LatLng, distance: Double, 
        workoutName: String? = null, 
        candidateSportTypes: Set<BSportType>,
        minAltPos: LatLng? = null,
        maxAltPos: LatLng? = null,
        cachedClusters: List<WorkoutCluster>? = null,
        scoreThreshold: Double = runCatching { TrainingApplication.getClusterScoreThreshold().toDouble() }.getOrDefault(1.2),
        workoutPolyline: List<LatLng>? = null
    ): WorkoutCluster? {
        val allClusters = cachedClusters ?: dbManager.getAllClusters()
        if (DEBUG) Log.d(TAG, "Evaluating ${allClusters.size} clusters for shape [start=$start, dist=$distance, name=$workoutName, sports=$candidateSportTypes, minAlt=$minAltPos, maxAlt=$maxAltPos, threshold=$scoreThreshold, hasPoly=${workoutPolyline != null}]")

        return scoreClusters(allClusters, start, end, apex, distance, workoutName, candidateSportTypes, minAltPos, maxAltPos, workoutPolyline)
            .firstOrNull { it.second < scoreThreshold }?.first
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
        maxAltPos: LatLng? = null,
        cachedClusters: List<WorkoutCluster>? = null,
        scoreThreshold: Double = runCatching { TrainingApplication.getClusterScoreThreshold().toDouble() }.getOrDefault(1.2),
        workoutPolyline: List<LatLng>? = null
    ): WorkoutCluster? {
        val candidateSports = if (workoutSportType != BSportType.UNKNOWN) setOf(workoutSportType) else emptySet()
        return suggestCluster(start, end, apex, distance, workoutName, candidateSports, minAltPos, maxAltPos, cachedClusters, scoreThreshold, workoutPolyline)
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
        minAltPos: LatLng? = null, maxAltPos: LatLng? = null,
        cachedClusters: MutableList<WorkoutCluster>? = null
    ): Long {
        synchronized(clusterLock) {
            val workoutSportType = SportTypesRepository.getInstance(appContext).getBSportType(userSportId)
            val normalizedInputName = stripHitCount(userSpecifiedName)
            val existingMatch = if (clusterIdOverride != -1L) {
                cachedClusters?.find { it.id == clusterIdOverride } ?: dbManager.getClusterById(clusterIdOverride)
            } else {
                (suggestCluster(start, end, apex, distance, userSpecifiedName, workoutSportType, minAltPos, maxAltPos, cachedClusters)
                    ?: (cachedClusters?.find { it.name == normalizedInputName } ?: dbManager.getClusterByName(normalizedInputName)))
            }

        return if (existingMatch != null) {
            val uniqueName = if (clusterIdOverride != -1L && !isDefaultName(existingMatch.name)) {
                existingMatch.name
            } else {
                if (existingMatch.name == normalizedInputName) normalizedInputName
                else findUniqueClusterName(normalizedInputName, existingMatch.id, cachedClusters)
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
                bSportType = SportTypesRepository.getInstance(appContext).getBSportType(userSportId),
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
            if (cachedClusters != null) {
                val idx = cachedClusters.indexOfFirst { it.id == updatedCluster.id }
                if (idx != -1) cachedClusters[idx] = updatedCluster else cachedClusters.add(updatedCluster)
            }
            ensureStartingLocationAnchor(start)
            updatedCluster.id
        } else {
            val uniqueName = findUniqueClusterName(stripHitCount(userSpecifiedName), -1, cachedClusters)
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
                bSportType = SportTypesRepository.getInstance(appContext).getBSportType(userSportId),
                // ATT-371: Initialize bounds
                minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng,
                // ATT-502
                minAltLat = minAltPos?.latitude,
                minAltLng = minAltPos?.longitude,
                maxAltLat = maxAltPos?.latitude,
                maxAltLng = maxAltPos?.longitude
            )
            val insertedId = dbManager.insertCluster(newCluster)
            if (cachedClusters != null) {
                cachedClusters.add(newCluster.copy(id = insertedId))
            }
            ensureStartingLocationAnchor(start)
            insertedId
        }
        }
    }

    /**
     * Mandatory starting location invariant (REQ-UI-186, ATT-1402):
     * Guarantees that every route cluster originates at a recognized KnownLocation in StartLocation2Altitude.db.
     * If no location exists within the geofence radius of [start], automatically seeds a new location.
     */
    fun ensureStartingLocationAnchor(start: LatLng) {
        val knownLocManager = KnownLocationsRepository.getInstance(appContext)
        if (knownLocManager.getMyLocation(start) == null) {
            knownLocManager.recordWorkoutStart(start, null)
        }
    }

    /**
     * O(1) Surgical update when a workout is finished (ATT-354).
     */
    /**
     * Identifies the coordinate among the given points that defines the route's spatial apex (REQ-SET-063, REQ-SET-097).
     *
     * - For point-to-point routes (end != null and distanceBetween(start, end) >= 150m):
     *   Maximizes the elliptical focal distance sum S(P) = d(start, P) + d(P, end).
     *   If max S(P) < d(start, end) + 20m (indicating a substantially collinear straight path),
     *   selects the track point closest to the 50% cumulative track distance midpoint to prevent
     *   apex=end degeneracy.
     * - For closed-loop routes (end == null or distanceBetween(start, end) < 150m):
     *   Maximizes geodesic distance from start alone (since for closed loops A ≈ B,
     *   d(start, P) + d(P, end) ≡ 2 * d(start, P)), preserving legacy behavior 100%.
     */
    @JvmOverloads
    fun findApexFromPoints(start: LatLng, points: List<LatLng>, end: LatLng? = null): LatLng {
        if (points.isEmpty()) return start
        if (points.size == 1) return points.first()

        val directEndDist = if (end != null) distanceBetween(start, end) else 0f
        val isPointToPoint = end != null && directEndDist >= 150f

        if (!isPointToPoint) {
            // Legacy closed loop or unconstrained: maximize distance from start
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

        // Point-to-point (A -> B) route: evaluate elliptical focal distance sum
        var maxEllipticalSum = -1.0
        var ellipticalApex = points.first()
        for (pt in points) {
            val dStart = distanceBetween(start, pt).toDouble()
            val dEnd = distanceBetween(end!!, pt).toDouble()
            val sum = dStart + dEnd
            if (sum > maxEllipticalSum) {
                maxEllipticalSum = sum
                ellipticalApex = pt
            }
        }

        // If the path has significant lateral deflection (>= direct distance + 20m), return the apex
        if (maxEllipticalSum >= directEndDist + 20.0) {
            return ellipticalApex
        }

        // Substantially straight / collinear path: pick point closest to 50% cumulative track length
        val cumDistances = DoubleArray(points.size)
        for (i in 0 until points.size - 1) {
            cumDistances[i + 1] = cumDistances[i] + distanceBetween(points[i], points[i + 1])
        }
        val totalLength = cumDistances.last()
        if (totalLength <= 0.0) return points.first()

        val targetMidpoint = totalLength * 0.5
        var bestIdx = 0
        var minDiff = Double.MAX_VALUE
        for (i in points.indices) {
            val diff = Math.abs(cumDistances[i] - targetMidpoint)
            if (diff < minDiff) {
                minDiff = diff
                bestIdx = i
            }
        }
        return points[bestIdx]
    }

    fun onWorkoutFinished(context: Context, w: WorkoutData) {
        synchronized(clusterLock) {
            // REQ-MIG-025: Strictly respect unclustered status and never auto-create fallback clusters
            if (w.clusterId == -1L) return
            if (w.startLatLng == null || w.endLatLng == null) return // Ignore non-spatial
            
            val currentMatch = dbManager.getClusterById(w.clusterId) ?: return
            val apex = w.maxDisplacementLatLng ?: w.endLatLng

            // --- ATT-354 Refinement: Null-Safe Bounds Update ---
            val wMinLat = w.minLat; val wMinLng = w.minLng; val wMaxLat = w.maxLat; val wMaxLng = w.maxLng
            
            val minAltPos = w.minAltitudeLatLng ?: WorkoutRepository.getInstance(context).getExtremaPosition(w.id, SensorType.ALTITUDE, ExtremaType.MIN)
            val maxAltPos = w.maxAltitudeLatLng ?: WorkoutRepository.getInstance(context).getExtremaPosition(w.id, SensorType.ALTITUDE, ExtremaType.MAX)

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
    }

    /**
     * O(1) Surgical removal of a workout's influence from its cluster (ATT-354).
     */
    fun onWorkoutDeleted(context: Context, w: WorkoutData) {
        synchronized(clusterLock) {
            val clusterId = w.clusterId
            if (clusterId == -1L) return
            
            val cluster = dbManager.getClusterById(clusterId) ?: return
            
            if (cluster.hitCount <= 1) {
                dbManager.updateCluster(cluster.copy(hitCount = 0))
                return
            }

            val newHitCount = cluster.hitCount - 1
            val wApex = w.maxDisplacementLatLng ?: w.endLatLng ?: w.startLatLng
            val updated = cluster.copy(
                hitCount = newHitCount,
                startLat = (cluster.startLat * cluster.hitCount - (w.startLatLng?.latitude ?: cluster.startLat)) / newHitCount,
                startLng = (cluster.startLng * cluster.hitCount - (w.startLatLng?.longitude ?: cluster.startLng)) / newHitCount,
                endLat = (cluster.endLat * cluster.hitCount - (w.endLatLng?.latitude ?: cluster.endLat)) / newHitCount,
                endLng = (cluster.endLng * cluster.hitCount - (w.endLatLng?.longitude ?: cluster.endLng)) / newHitCount,
                maxDispLat = (cluster.maxDispLat * cluster.hitCount - (wApex?.latitude ?: cluster.maxDispLat)) / newHitCount,
                maxDispLng = (cluster.maxDispLng * cluster.hitCount - (wApex?.longitude ?: cluster.maxDispLng)) / newHitCount,
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
        synchronized(clusterLock) {
            val summariesManager = WorkoutRepository.getInstance(context).workoutSummariesDatabaseManager
            val db = summariesManager.getDatabase()
            val cursor = db.query(WorkoutSummaries.TABLE, 
                arrayOf(WorkoutSummaries.C_ID, WorkoutSummaries.TIME_ACTIVE_s, 
                        WorkoutSummaries.BOUND_MIN_LAT, WorkoutSummaries.BOUND_MIN_LNG, 
                        WorkoutSummaries.BOUND_MAX_LAT, WorkoutSummaries.BOUND_MAX_LNG),
                "${WorkoutSummaries.CLUSTER_ID} = ?", arrayOf(cluster.id.toString()), null, null, null)

            var minLat = 90.0; var maxLat = -90.0; var minLng = 180.0; var maxLng = -180.0
            var hasPoints = false

            cursor.use { c ->
                while (c.moveToNext()) {
                    val bounds = WorkoutBoundsDto.fromCursor(c)
                    val wMinLat = bounds.minLat
                    val wMinLng = bounds.minLng
                    val wMaxLat = bounds.maxLat
                    val wMaxLng = bounds.maxLng

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
                val bSport = SportTypesRepository.getInstance(context).getBSportType(mostFrequentSport)
                dbManager.updateCluster(refreshed.copy(probableSportId = mostFrequentSport, bSportType = bSport))
            }
        }
    }

    /**
     * One-time self-healing migration to recompute non-degenerate elliptical apex coordinates
     * for existing point-to-point (A->B) routes and update cluster centroids (REQ-DAT-039, ATT-6319).
     *
     * @return Number of healed workout apex records.
     */
    fun healLegacyPointToPointApexes(context: Context): Int {
        return synchronized(clusterLock) {
            val summariesManager = WorkoutRepository.getInstance(context).workoutSummariesDatabaseManager
            val summariesDb = summariesManager.database
            val affectedClusterIds = mutableSetOf<Long>()
            var healedCount = 0

            val selection = "${WorkoutSummaries.MAP_POLYLINE} IS NOT NULL AND ${WorkoutSummaries.MAP_POLYLINE} != ''"
            val projection = arrayOf(
                WorkoutSummaries.C_ID,
                WorkoutSummaries.CLUSTER_ID,
                WorkoutSummaries.MAP_POLYLINE
            )

            try {
                summariesDb.query(WorkoutSummaries.TABLE, projection, selection, null, null, null, null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val candidate = WorkoutPolylineCandidateDto.fromCursor(cursor)
                        val workoutId = candidate.workoutId
                        val clusterId = candidate.clusterId
                        val polyline = candidate.polyline ?: continue

                        val start = summariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.START) ?: continue
                        val end = summariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.END) ?: continue

                        val directDist = distanceBetween(start, end)
                        // REQ-DAT-039.5: Preserve closed-loop routes (< 150m) 100%
                        if (directDist < 150f) continue

                        val recordedApex = summariesManager.getExtremaPosition(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX)
                        val distToEnd = if (recordedApex != null) distanceBetween(recordedApex, end) else 0f

                        // A->B workout with degenerate apex (collapsed onto end or missing)
                        if (recordedApex == null || distToEnd < 50f) {
                            val points = PolyUtil.decode(polyline)
                            if (points.isNotEmpty()) {
                                val trueApex = findApexFromPoints(start, points, end)
                                val trueDisp = distanceBetween(start, trueApex).toDouble()

                                if (recordedApex == null || distanceBetween(recordedApex, trueApex) > 10f) {
                                    summariesManager.updateExtremaValue(
                                        workoutId,
                                        SensorType.LINE_DISTANCE_m,
                                        ExtremaType.MAX,
                                        trueDisp,
                                        trueApex
                                    )
                                    healedCount++
                                    if (clusterId > 0L) {
                                        affectedClusterIds.add(clusterId)
                                    }
                                }
                            }
                        }
                    }
                }

                // Recalculate centroids for all affected clusters
                for (clusterId in affectedClusterIds) {
                    val cluster = dbManager.getClusterById(clusterId) ?: continue
                    summariesDb.query(
                        WorkoutSummaries.TABLE,
                        arrayOf(WorkoutSummaries.C_ID, WorkoutSummaries.DISTANCE_TOTAL_m),
                        "${WorkoutSummaries.CLUSTER_ID} = ?",
                        arrayOf(clusterId.toString()),
                        null, null, null
                    ).use { wCursor ->
                        var count = 0
                        var sumStartLat = 0.0
                        var sumStartLng = 0.0
                        var sumEndLat = 0.0
                        var sumEndLng = 0.0
                        var sumApexLat = 0.0
                        var sumApexLng = 0.0
                        var sumDist = 0.0

                        while (wCursor.moveToNext()) {
                            val candidate = WorkoutDistanceCandidateDto.fromCursor(wCursor)
                            val wId = candidate.workoutId
                            val dist = candidate.distance
                            val s = summariesManager.getExtremaPosition(wId, SensorType.LATITUDE, ExtremaType.START)
                            val e = summariesManager.getExtremaPosition(wId, SensorType.LATITUDE, ExtremaType.END)
                            val a = summariesManager.getExtremaPosition(wId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX) ?: e
                            if (s != null && e != null && a != null) {
                                sumStartLat += s.latitude
                                sumStartLng += s.longitude
                                sumEndLat += e.latitude
                                sumEndLng += e.longitude
                                sumApexLat += a.latitude
                                sumApexLng += a.longitude
                                sumDist += dist
                                count++
                            }
                        }

                        if (count > 0) {
                            val updatedCluster = cluster.copy(
                                startLat = sumStartLat / count,
                                startLng = sumStartLng / count,
                                endLat = sumEndLat / count,
                                endLng = sumEndLng / count,
                                maxDispLat = sumApexLat / count,
                                maxDispLng = sumApexLng / count,
                                refDistance = sumDist / count
                            )
                            dbManager.updateCluster(updatedCluster)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("WorkoutClusterEngine", "Failed to heal legacy point-to-point apexes", e)
            }

            healedCount
        }
    }

    /**
     * Non-destructive enrichment of all existing clusters with full spatial bounds (ATT-371).
     */
    fun enrichAllClusterMetadata(context: Context, listener: ClusterMigrationListener? = null) {
        synchronized(clusterLock) {
            val clusters = dbManager.getAllClusters()
            clusters.forEachIndexed { index, cluster ->
                listener?.onPhase2Progress(index + 1, clusters.size)
                recalculateClusterAnchors(context, cluster)
            }
        }
    }

    fun syncRouteNameChange(clusterId: Long, newName: String) {
        synchronized(clusterLock) {
            if (clusterId == -1L) return
            val match = dbManager.getClusterById(clusterId)
            if (match != null) {
                val uniqueName = findUniqueClusterName(newName, match.id)
                dbManager.updateCluster(match.copy(name = uniqueName))
            }
        }
    }

    fun learnFromRoute(route: RouteWithPath): Long {
        synchronized(clusterLock) {
            val path = route.path
            if (path.size < 2) return -1L
            val start = path.first().latLng
            val end = path.last().latLng
            val distance = route.summary.distance
            
            var minLat = 90.0; var maxLat = -90.0; var minLng = 180.0; var maxLng = -180.0
            val apex = findApexFromPoints(start, path.map { it.latLng }, end)
            
            path.forEach { point ->
                val lat = point.latLng.latitude
                val lon = point.latLng.longitude
                if (lat < minLat) minLat = lat
                if (lat > maxLat) maxLat = lat
                if (lon < minLng) minLng = lon
                if (lon > maxLng) maxLng = lon
            }
            
            val sportId = SportTypesRepository.getSportTypeId(route.summary.bSportType)
            return learnFromWorkout(
                start, end, apex, distance, route.summary.name, sportId,
                minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng
            )
        }
    }

    /**
     * O(1) Hierarchical sync pass for history (ATT-371/392).
     */
    @JvmOverloads
    fun migrateHistory(context: Context, listener: ClusterMigrationListener? = null) {
        synchronized(clusterLock) {
        val routesRepo = RoutesRepository.getInstance(context)
        val routesDbInstance = routesRepo.getDatabase()
        val routes = routesRepo.getAllRoutesFromDb()
        
        routesDbInstance.beginTransaction()
        try {
            routes.forEachIndexed { index, routeWithPath ->
                listener?.onPhase1Progress(index + 1, routes.size)
                val clusterId = learnFromRoute(routeWithPath)
                if (clusterId != -1L) {
                    routesRepo.updateRouteSummarySync(routeWithPath.summary.copy(clusterId = clusterId))
                }
            }
            routesDbInstance.setTransactionSuccessful()
        } finally {
            routesDbInstance.endTransaction()
        }
        
        // Ensure Phase 1 hits 100% if no routes existed
        if (routes.isEmpty()) {
            listener?.onPhase1Progress(0, 0)
        }

        val summariesManager = WorkoutRepository.getInstance(context).workoutSummariesDatabaseManager
        val cursor = summariesManager.getCursorForAllWorkoutsAsc() ?: return
        val summariesDb = summariesManager.getDatabase()
        val clusterDbInstance = dbManager.getDatabase()
        val localClusters = dbManager.getAllClusters().toMutableList()

        summariesDb.beginTransaction()
        try {
            clusterDbInstance.beginTransaction()
            try {
                cursor.use { c ->
                    val total = c.count
                    var count = 0
                    while (c.moveToNext()) {
                        count++
                        if (count % 5 == 0 || count == total) {
                            listener?.onPhase2Progress(count, total)
                        }

                        val candidate = ClusterWorkoutCandidateDto.fromCursor(c)
                        val workoutId = candidate.workoutId
                        val workoutName = candidate.workoutName
                        val fileBaseName = candidate.fileBaseName
                        val sportId = candidate.sportId
                        val distance = candidate.distance

                        val start = summariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.START)
                        val end = summariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.END)
                        val apex = summariesManager.getExtremaPosition(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX)
                        val minAltPos = summariesManager.getExtremaPosition(workoutId, SensorType.ALTITUDE, ExtremaType.MIN)
                        val maxAltPos = summariesManager.getExtremaPosition(workoutId, SensorType.ALTITUDE, ExtremaType.MAX)

                        if (start != null && end != null && apex != null && distance > 100.0) {
                            val isDefault = workoutName.isNullOrEmpty() || workoutName == fileBaseName
                            val normalizedName = if (!isDefault) stripHitCount(workoutName) else null
                            val workoutSportType = SportTypesRepository.getInstance(context).getBSportType(sportId)
                            
                            val match = suggestCluster(start, end, apex, distance, normalizedName, workoutSportType, minAltPos, maxAltPos, localClusters)
                            if (match != null) {
                                val rawName = normalizedName ?: match.name
                                val finalName = if (match.name == rawName) rawName else findUniqueClusterName(rawName, match.id, localClusters)
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
                                    bSportType = SportTypesRepository.getInstance(context).getBSportType(finalSport),
                                    minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng,
                                    minAltLat = updatedMinAltLat,
                                    minAltLng = updatedMinAltLng,
                                    maxAltLat = updatedMaxAltLat,
                                    maxAltLng = updatedMaxAltLng
                                )
                                dbManager.updateCluster(updated)
                                assignClusterToWorkout(context, workoutId, updated.id)
                                val refreshed = dbManager.getClusterById(updated.id) ?: updated
                                val idx = localClusters.indexOfFirst { it.id == refreshed.id }
                                if (idx != -1) localClusters[idx] = refreshed else localClusters.add(refreshed)
                            } else {
                                val clusterName = if (!isDefault) {
                                    normalizedName!!
                                } else {
                                    val seedName = try {
                                        val startLoc = KnownLocationsRepository.getInstance(context).getMyLocation(start)
                                        WorkoutAutoNamingHelper.generateClusterSeedName(context, startLoc)
                                    } catch (e: Exception) {
                                        null
                                    }
                                    seedName ?: context.getString(R.string.cluster_default_name_format, fileBaseName?.take(10) ?: "Workout")
                                }
                                
                                val wMinLat = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MIN_LAT)
                                val wMinLng = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MIN_LNG)
                                val wMaxLat = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MAX_LAT)
                                val wMaxLng = summariesManager.getDouble(workoutId, WorkoutSummaries.BOUND_MAX_LNG)

                                val newId = learnFromWorkout(
                                    start, end, apex, distance, clusterName, sportId,
                                    minLat = wMinLat, minLng = wMinLng, maxLat = wMaxLat, maxLng = wMaxLng,
                                    minAltPos = minAltPos, maxAltPos = maxAltPos,
                                    cachedClusters = localClusters
                                )
                                assignClusterToWorkout(context, workoutId, newId)
                                val refreshed = dbManager.getClusterById(newId)
                                if (refreshed != null) {
                                    val idx = localClusters.indexOfFirst { it.id == refreshed.id }
                                    if (idx != -1) localClusters[idx] = refreshed else localClusters.add(refreshed)
                                }
                            }
                        }
                    }
                }
                clusterDbInstance.setTransactionSuccessful()
            } finally {
                clusterDbInstance.endTransaction()
            }
            summariesDb.setTransactionSuccessful()
        } finally {
            summariesDb.endTransaction()
        }
        }
    }

    private fun findUniqueClusterName(baseName: String, excludeId: Long = -1, cachedClusters: List<WorkoutCluster>? = null): String {
        var candidate = baseName; var counter = 2
        val isTaken: (String) -> Boolean = if (cachedClusters != null) {
            { name -> cachedClusters.any { it.name == name && it.id != excludeId } }
        } else {
            { name -> dbManager.isNameTaken(name, excludeId) }
        }
        while (isTaken(candidate)) { candidate = "$baseName var $counter"; counter++ }
        return candidate
    }

    @JvmOverloads
    fun assignClusterToWorkout(
        context: Context,
        workoutId: Long,
        clusterId: Long,
        forceIdentity: Boolean = false,
        customWorkoutName: String? = null
    ) {
        synchronized(clusterLock) {
            if (clusterId <= 0L) {
                unassignClusterFromWorkout(context, workoutId)
                return
            }
            val summariesManager = WorkoutRepository.getInstance(context).workoutSummariesDatabaseManager
            val clusterDb = WorkoutClusterRepository.getInstance(context).databaseManager
            val previousClusterId = summariesManager.getLong(workoutId, WorkoutSummaries.CLUSTER_ID) ?: -1L
            val cluster = clusterDb.getClusterById(clusterId) ?: return

            // 1. Update WorkoutSummaries first so it remains the authoritative single source of truth
            val values = android.content.ContentValues().apply {
                put(WorkoutSummaries.CLUSTER_ID, clusterId)
                val currentName = summariesManager.getString(workoutId, WorkoutSummaries.WORKOUT_NAME)
                val fileBaseName = summariesManager.getString(workoutId, WorkoutSummaries.FILE_BASE_NAME)
                if (customWorkoutName != null) {
                    put(WorkoutSummaries.WORKOUT_NAME, customWorkoutName)
                } else if (forceIdentity || currentName.isNullOrEmpty() || currentName == fileBaseName) {
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
            val currentBSport = safeEnumValueOf(sportStr, BSportType.UNKNOWN, TAG)
            val avgSpeed = summariesManager.getDouble(workoutId, WorkoutSummaries.SPEED_AVERAGE_mps) ?: 0.0
            val discoveryManager = EquipmentAndSportTypeDiscoveryManager.getInstance(context)
            val hardwareIdentity = discoveryManager.resolveIdentity(workoutId, currentBSport, avgSpeed)
            if (!forceIdentity && hardwareIdentity.isHighConfidence) {
                summariesManager.applyInferredIdentity(workoutId, hardwareIdentity)
            } else if (cluster.probableSportId > 0) {
                summariesManager.applyInferredIdentity(workoutId, discoveryManager.inferIdentityFromSport(cluster.probableSportId))
            } else if (hardwareIdentity.sportId > 0) {
                summariesManager.applyInferredIdentity(workoutId, hardwareIdentity)
            }
        }
    }

    /**
     * Unassigns a workout from its workout cluster, atomically decrementing the cluster's hit count (REQ-SET-059, ATT-318).
     */
    fun unassignClusterFromWorkout(context: Context, workoutId: Long) {
        synchronized(clusterLock) {
            val summariesManager = WorkoutRepository.getInstance(context).workoutSummariesDatabaseManager
            val clusterDb = WorkoutClusterRepository.getInstance(context).databaseManager
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
    }

    /**
     * Creates a new workout cluster from the workout's spatial fingerprint and assigns the workout to it (REQ-SET-059, REQ-SET-072).
     */
    @JvmOverloads
    fun createNewClusterFromWorkout(
        context: Context,
        workout: WorkoutData,
        customName: String? = null,
        hasCounter: Boolean = true,
        customWorkoutName: String? = null
    ): Long {
        synchronized(clusterLock) {
            val summariesManager = WorkoutRepository.getInstance(context).workoutSummariesDatabaseManager
            val start = workout.startLatLng ?: summariesManager.getExtremaPosition(workout.id, SensorType.LATITUDE, ExtremaType.START) ?: return -1L
            val end = workout.endLatLng ?: summariesManager.getExtremaPosition(workout.id, SensorType.LATITUDE, ExtremaType.END) ?: return -1L
            val apex = workout.maxDisplacementLatLng ?: summariesManager.getExtremaPosition(workout.id, SensorType.LINE_DISTANCE_m, ExtremaType.MAX) ?: end
            val distance = workout.totalDistance
            val sportId = workout.sportId
            val clusterName = if (!customName.isNullOrBlank()) {
                customName
            } else if (workout.workoutName.isNotBlank()) {
                workout.workoutName
            } else {
                val seedName = try {
                    val startLoc = KnownLocationsRepository.getInstance(context).getMyLocation(start)
                    WorkoutAutoNamingHelper.generateClusterSeedName(context, startLoc)
                } catch (e: Exception) {
                    null
                }
                seedName ?: context.getString(R.string.cluster_default_name_format, "Workout")
            }

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
                bSportType = SportTypesRepository.getInstance(context).getBSportType(sportId),
                minLat = workout.minLat, minLng = workout.minLng, maxLat = workout.maxLat, maxLng = workout.maxLng,
                minAltLat = minAltPos?.latitude,
                minAltLng = minAltPos?.longitude,
                maxAltLat = maxAltPos?.latitude,
                maxAltLng = maxAltPos?.longitude,
                hasCounter = hasCounter
            )
            val newClusterId = dbManager.insertCluster(newCluster)
            assignClusterToWorkout(
                context,
                workout.id,
                newClusterId,
                forceIdentity = true,
                customWorkoutName = customWorkoutName
            )
            return newClusterId
        }
    }

    /**
     * Creates a new workout cluster from raw database metadata and assigns the workout to it (REQ-SET-059, REQ-SET-072).
     */
    @JvmOverloads
    fun createNewClusterFromWorkout(
        context: Context,
        workoutId: Long,
        customName: String? = null,
        hasCounter: Boolean = true,
        customWorkoutName: String? = null
    ): Long {
        synchronized(clusterLock) {
            val summariesManager = WorkoutRepository.getInstance(context).workoutSummariesDatabaseManager
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
            val clusterName = if (!customName.isNullOrBlank()) {
                customName
            } else if (!workoutName.isNullOrBlank()) {
                workoutName
            } else {
                val seedName = try {
                    val startLoc = KnownLocationsRepository.getInstance(context).getMyLocation(start)
                    WorkoutAutoNamingHelper.generateClusterSeedName(context, startLoc)
                } catch (e: Exception) {
                    null
                }
                seedName ?: context.getString(R.string.cluster_default_name_format, "Workout")
            }

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
                bSportType = SportTypesRepository.getInstance(context).getBSportType(sportId),
                minLat = wMinLat, minLng = wMinLng, maxLat = wMaxLat, maxLng = wMaxLng,
                minAltLat = minAltPos?.latitude,
                minAltLng = minAltPos?.longitude,
                maxAltLat = maxAltPos?.latitude,
                maxAltLng = maxAltPos?.longitude,
                hasCounter = hasCounter
            )
            val newClusterId = dbManager.insertCluster(newCluster)
            assignClusterToWorkout(
                context,
                workoutId,
                newClusterId,
                forceIdentity = true,
                customWorkoutName = customWorkoutName
            )
            return newClusterId
        }
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
    @JvmOverloads
    fun calculateSimilarity(
        start: LatLng, end: LatLng, apex: LatLng, distance: Double, 
        cluster: WorkoutCluster, 
        workoutName: String? = null,
        candidateSportTypes: Set<BSportType>,
        minAltPos: LatLng? = null,
        maxAltPos: LatLng? = null,
        workoutPolyline: List<LatLng>? = null
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

        // Corridor deviation tie-breaking (REQ-SET-097.3, ATT-5899)
        if (workoutPolyline != null && workoutPolyline.isNotEmpty() && cluster.previewPaths.isNotEmpty()) {
            try {
                val workoutStations = WorkoutClusterCorridorDetector.samplePolylineStations(workoutPolyline)
                val minCorridorDev = cluster.previewPaths.mapNotNull { previewPoly ->
                    val clusterPts = PolyUtil.decode(previewPoly)
                    if (clusterPts.isNotEmpty()) {
                        val clusterStations = WorkoutClusterCorridorDetector.samplePolylineStations(clusterPts)
                        WorkoutClusterCorridorDetector.computeCorridorDistance(workoutStations, clusterStations)
                    } else null
                }.minOrNull()

                if (minCorridorDev != null) {
                    val corridorCost = (minCorridorDev / 500.0) * 0.30
                    totalScore += corridorCost
                }
            } catch (e: Exception) {
                if (DEBUG) Log.w(TAG, "Failed to compute corridor deviation for cluster ${cluster.name}", e)
            }
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
        maxAltPos: LatLng? = null,
        workoutPolyline: List<LatLng>? = null
    ): Double = calculateSimilarity(
        start, end, apex, distance, cluster, workoutName,
        if (workoutSportType != BSportType.UNKNOWN) setOf(workoutSportType) else emptySet(),
        minAltPos, maxAltPos, workoutPolyline
    )

    private fun normalizeName(name: String): String = name.replace(Regex(" (?:# ?|var )\\d+$", RegexOption.IGNORE_CASE), "").trim().lowercase()
    @JvmOverloads
    fun recalculateHistory(context: Context, listener: ClusterMigrationListener? = null) { 
        dbManager.deleteAllClusters()
        migrateHistory(context, listener) 
    }

    @JvmOverloads
    fun getClusterScores(
        start: LatLng, end: LatLng, apex: LatLng, distance: Double, 
        workoutName: String? = null, 
        candidateSportTypes: Set<BSportType>,
        minAltPos: LatLng? = null,
        maxAltPos: LatLng? = null,
        workoutPolyline: List<LatLng>? = null
    ): List<Pair<WorkoutCluster, Double>> {
        val summariesManager = WorkoutRepository.getInstance(appContext).workoutSummariesDatabaseManager
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
        return scoreClusters(clusters, start, end, apex, distance, workoutName, candidateSportTypes, minAltPos, maxAltPos, workoutPolyline)
    }

    @JvmOverloads
    fun getClusterScores(
        start: LatLng, end: LatLng, apex: LatLng, distance: Double, 
        workoutName: String? = null, 
        workoutSportType: BSportType = BSportType.UNKNOWN,
        minAltPos: LatLng? = null,
        maxAltPos: LatLng? = null,
        workoutPolyline: List<LatLng>? = null
    ): List<Pair<WorkoutCluster, Double>> = getClusterScores(
        start, end, apex, distance, workoutName,
        if (workoutSportType != BSportType.UNKNOWN) setOf(workoutSportType) else emptySet(),
        minAltPos, maxAltPos, workoutPolyline
    )

    @JvmOverloads
    fun scoreClusters(
        clusters: List<WorkoutCluster>, 
        start: LatLng, end: LatLng, apex: LatLng, distance: Double, 
        workoutName: String? = null, 
        candidateSportTypes: Set<BSportType>,
        minAltPos: LatLng? = null,
        maxAltPos: LatLng? = null,
        workoutPolyline: List<LatLng>? = null
    ): List<Pair<WorkoutCluster, Double>> = clusters.map { 
        it to calculateSimilarity(start, end, apex, distance, it, workoutName, candidateSportTypes, minAltPos, maxAltPos, workoutPolyline) 
    }.sortedBy { it.second }

    @JvmOverloads
    fun scoreClusters(
        clusters: List<WorkoutCluster>, 
        start: LatLng, end: LatLng, apex: LatLng, distance: Double, 
        workoutName: String? = null, 
        workoutSportType: BSportType = BSportType.UNKNOWN,
        minAltPos: LatLng? = null,
        maxAltPos: LatLng? = null,
        workoutPolyline: List<LatLng>? = null
    ): List<Pair<WorkoutCluster, Double>> = scoreClusters(
        clusters, start, end, apex, distance, workoutName,
        if (workoutSportType != BSportType.UNKNOWN) setOf(workoutSportType) else emptySet(),
        minAltPos, maxAltPos, workoutPolyline
    )

    fun moveWorkoutToCluster(context: Context, workoutId: Long, currentClusterId: Long, newClusterId: Long) {
        synchronized(clusterLock) {
            val summariesManager = WorkoutRepository.getInstance(context).workoutSummariesDatabaseManager
            val start = summariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.START)
            val end = summariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.END)
            val apex = summariesManager.getExtremaPosition(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX)
            val distance = summariesManager.getDouble(workoutId, WorkoutSummaries.DISTANCE_TOTAL_m)
            if (start == null || end == null || apex == null || distance == null) return

            // 1. Compute and persist updated weighted centroids using pre-move hitCount
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
            }

            // 2. Reassign workout in WorkoutSummaries and reconcile hitCount / previews
            assignClusterToWorkout(context, workoutId, newClusterId)

            // 3. Re-evaluate sport frequency against post-move distribution on freshly fetched cluster records
            val refreshedOld = dbManager.getClusterById(currentClusterId)
            if (refreshedOld != null) {
                val mostFrequentSport = summariesManager.getMostFrequentSportIdForCluster(refreshedOld.id)
                if (mostFrequentSport != -1L && mostFrequentSport != refreshedOld.probableSportId) {
                    dbManager.updateCluster(refreshedOld.copy(
                        probableSportId = mostFrequentSport,
                        bSportType = SportTypesRepository.getInstance(context).getBSportType(mostFrequentSport)
                    ))
                }
            }
            val refreshedNew = dbManager.getClusterById(newClusterId)
            if (refreshedNew != null) {
                val mostFrequentSport = summariesManager.getMostFrequentSportIdForCluster(refreshedNew.id)
                if (mostFrequentSport != -1L && mostFrequentSport != refreshedNew.probableSportId) {
                    dbManager.updateCluster(refreshedNew.copy(
                        probableSportId = mostFrequentSport,
                        bSportType = SportTypesRepository.getInstance(context).getBSportType(mostFrequentSport)
                    ))
                }
            }
        }
    }

    /**
     * Atomically merges a source cluster into a target cluster (ATT-5666 / REQ-SET-089).
     *
     * 1. Reassigns all workouts and routes with clusterId = sourceId to targetId.
     * 2. Synthesizes combined weighted centroid, bounds, hit count, and probable sport.
     * 3. Updates targetCluster and deletes sourceCluster from database.
     *
     * @return true if merge succeeded, false if either cluster was not found
     */
    fun mergeClusters(context: Context, sourceId: Long, targetId: Long): Boolean {
        synchronized(clusterLock) {
            val sourceCluster = dbManager.getClusterById(sourceId) ?: return false
            val targetCluster = dbManager.getClusterById(targetId) ?: return false

            val summariesManager = WorkoutRepository.getInstance(context).workoutSummariesDatabaseManager
            val routesManager = RoutesRepository.getInstance(context).databaseManager

            // 1. Reassign workouts & routes
            summariesManager.reassignClusterWorkouts(sourceId, targetId)
            routesManager.reassignClusterRoutes(sourceId, targetId)

            // 2. Synthesize combined weighted centroid & statistics
            val nSource = sourceCluster.hitCount
            val nTarget = targetCluster.hitCount
            val totalHits = nSource + nTarget

            val updatedCluster = if (totalHits > 0) {
                val weightSource = nSource.toDouble() / totalHits
                val weightTarget = nTarget.toDouble() / totalHits

                val newStartLat = targetCluster.startLat * weightTarget + sourceCluster.startLat * weightSource
                val newStartLng = targetCluster.startLng * weightTarget + sourceCluster.startLng * weightSource
                val newEndLat = targetCluster.endLat * weightTarget + sourceCluster.endLat * weightSource
                val newEndLng = targetCluster.endLng * weightTarget + sourceCluster.endLng * weightSource
                val newMaxDispLat = targetCluster.maxDispLat * weightTarget + sourceCluster.maxDispLat * weightSource
                val newMaxDispLng = targetCluster.maxDispLng * weightTarget + sourceCluster.maxDispLng * weightSource
                val newRefDistance = targetCluster.refDistance * weightTarget + sourceCluster.refDistance * weightSource

                val newMinAltLat = when {
                    targetCluster.minAltLat != null && sourceCluster.minAltLat != null ->
                        targetCluster.minAltLat * weightTarget + sourceCluster.minAltLat * weightSource
                    else -> targetCluster.minAltLat ?: sourceCluster.minAltLat
                }
                val newMinAltLng = when {
                    targetCluster.minAltLng != null && sourceCluster.minAltLng != null ->
                        targetCluster.minAltLng * weightTarget + sourceCluster.minAltLng * weightSource
                    else -> targetCluster.minAltLng ?: sourceCluster.minAltLng
                }
                val newMaxAltLat = when {
                    targetCluster.maxAltLat != null && sourceCluster.maxAltLat != null ->
                        targetCluster.maxAltLat * weightTarget + sourceCluster.maxAltLat * weightSource
                    else -> targetCluster.maxAltLat ?: sourceCluster.maxAltLat
                }
                val newMaxAltLng = when {
                    targetCluster.maxAltLng != null && sourceCluster.maxAltLng != null ->
                        targetCluster.maxAltLng * weightTarget + sourceCluster.maxAltLng * weightSource
                    else -> targetCluster.maxAltLng ?: sourceCluster.maxAltLng
                }

                val newMinLat = listOfNotNull(targetCluster.minLat, sourceCluster.minLat).minOrNull()
                val newMaxLat = listOfNotNull(targetCluster.maxLat, sourceCluster.maxLat).maxOrNull()
                val newMinLng = listOfNotNull(targetCluster.minLng, sourceCluster.minLng).minOrNull()
                val newMaxLng = listOfNotNull(targetCluster.maxLng, sourceCluster.maxLng).maxOrNull()

                val combinedPreviewPaths = (targetCluster.previewPaths + sourceCluster.previewPaths).distinct().take(10)

                val mostFrequentSport = summariesManager.getMostFrequentSportIdForCluster(targetId)
                val sportId = if (mostFrequentSport != -1L) mostFrequentSport else targetCluster.probableSportId
                val bSport = SportTypesRepository.getInstance(context).getBSportType(sportId)

                targetCluster.copy(
                    hitCount = totalHits,
                    startLat = newStartLat,
                    startLng = newStartLng,
                    endLat = newEndLat,
                    endLng = newEndLng,
                    maxDispLat = newMaxDispLat,
                    maxDispLng = newMaxDispLng,
                    refDistance = newRefDistance,
                    minAltLat = newMinAltLat,
                    minAltLng = newMinAltLng,
                    maxAltLat = newMaxAltLat,
                    maxAltLng = newMaxAltLng,
                    minLat = newMinLat,
                    maxLat = newMaxLat,
                    minLng = newMinLng,
                    maxLng = newMaxLng,
                    previewPaths = combinedPreviewPaths,
                    probableSportId = sportId,
                    bSportType = bSport
                )
            } else {
                targetCluster
            }

            dbManager.updateCluster(updatedCluster)
            dbManager.deleteCluster(sourceId)

            return true
        }
    }

    /**
     * Splits a cluster into one or more newly created clusters and reconciles the original cluster (REQ-SET-096.5, ATT-5900).
     *
     * @param context Application context.
     * @param primaryClusterId Database ID of the cluster being split.
     * @param variantsToSplit List of pairs mapping suggested/custom cluster names to member [WorkoutData] lists.
     * @param renameWorkouts Whether to rename member workouts according to the new cluster name and sequential numbering.
     * @return List of newly created cluster IDs.
     */
    fun splitCluster(
        context: Context,
        primaryClusterId: Long,
        variantsToSplit: List<Pair<String, List<WorkoutData>>>,
        renameWorkouts: Boolean = true
    ): List<Long> {
        synchronized(clusterLock) {
            val primaryCluster = dbManager.getClusterById(primaryClusterId) ?: return emptyList()
            if (variantsToSplit.isEmpty()) return emptyList()

            val summariesManager = WorkoutRepository.getInstance(context).workoutSummariesDatabaseManager
            val summariesDb = summariesManager.database
            val createdClusterIds = mutableListOf<Long>()
            val allSplitWorkoutIds = mutableSetOf<Long>()

            summariesDb.beginTransaction()
            try {
                for ((rawName, workouts) in variantsToSplit) {
                    if (workouts.isEmpty()) continue
                    val uniqueName = findUniqueClusterName(stripHitCount(rawName))
                    val mCount = workouts.size

                    val startLat = workouts.mapNotNull { it.startLatLng?.latitude }.average().takeIf { !it.isNaN() } ?: primaryCluster.startLat
                    val startLng = workouts.mapNotNull { it.startLatLng?.longitude }.average().takeIf { !it.isNaN() } ?: primaryCluster.startLng
                    val endLat = workouts.mapNotNull { it.endLatLng?.latitude }.average().takeIf { !it.isNaN() } ?: primaryCluster.endLat
                    val endLng = workouts.mapNotNull { it.endLatLng?.longitude }.average().takeIf { !it.isNaN() } ?: primaryCluster.endLng
                    val maxDispLat = workouts.mapNotNull { (it.maxDisplacementLatLng ?: it.endLatLng)?.latitude }.average().takeIf { !it.isNaN() } ?: primaryCluster.maxDispLat
                    val maxDispLng = workouts.mapNotNull { (it.maxDisplacementLatLng ?: it.endLatLng)?.longitude }.average().takeIf { !it.isNaN() } ?: primaryCluster.maxDispLng
                    val refDist = workouts.map { it.totalDistance }.average().takeIf { !it.isNaN() } ?: primaryCluster.refDistance

                    val minLat = workouts.mapNotNull { it.minLat }.minOrNull()
                    val maxLat = workouts.mapNotNull { it.maxLat }.maxOrNull()
                    val minLng = workouts.mapNotNull { it.minLng }.minOrNull()
                    val maxLng = workouts.mapNotNull { it.maxLng }.maxOrNull()

                    val minAltLat = workouts.mapNotNull { it.minAltitudeLatLng?.latitude }.average().takeIf { !it.isNaN() }
                    val minAltLng = workouts.mapNotNull { it.minAltitudeLatLng?.longitude }.average().takeIf { !it.isNaN() }
                    val maxAltLat = workouts.mapNotNull { it.maxAltitudeLatLng?.latitude }.average().takeIf { !it.isNaN() }
                    val maxAltLng = workouts.mapNotNull { it.maxAltitudeLatLng?.longitude }.average().takeIf { !it.isNaN() }

                    val previewPaths = workouts.mapNotNull { it.mapPolyline.takeIf { p -> p.isNotEmpty() } }.distinct().take(5)
                    val mostFrequentSport = workouts.groupBy { it.sportId }.maxByOrNull { it.value.size }?.key ?: primaryCluster.probableSportId
                    val bSport = SportTypesRepository.getInstance(context).getBSportType(mostFrequentSport)

                    val newCluster = WorkoutCluster(
                        name = uniqueName,
                        probableSportId = mostFrequentSport,
                        startLat = startLat,
                        startLng = startLng,
                        endLat = endLat,
                        endLng = endLng,
                        maxDispLat = maxDispLat,
                        maxDispLng = maxDispLng,
                        refDistance = refDist,
                        hitCount = mCount,
                        bSportType = bSport,
                        minLat = minLat,
                        minLng = minLng,
                        maxLat = maxLat,
                        maxLng = maxLng,
                        minAltLat = minAltLat,
                        minAltLng = minAltLng,
                        maxAltLat = maxAltLat,
                        maxAltLng = maxAltLng,
                        previewPaths = previewPaths,
                        hasCounter = primaryCluster.hasCounter
                    )

                    val newClusterId = dbManager.insertCluster(newCluster)
                    createdClusterIds.add(newClusterId)

                    // Reassign workouts and optionally rename
                    workouts.sortedBy { it.startTimeS }.forEachIndexed { index, w ->
                        allSplitWorkoutIds.add(w.id)
                        val values = android.content.ContentValues().apply {
                            put(WorkoutSummaries.CLUSTER_ID, newClusterId)
                            if (renameWorkouts) {
                                put(WorkoutSummaries.WORKOUT_NAME, formatClusterWorkoutName(context, uniqueName, index + 1, newCluster.hasCounter))
                            }
                        }
                        summariesDb.update(WorkoutSummaries.TABLE, values, "${WorkoutSummaries.C_ID} = ?", arrayOf(w.id.toString()))
                    }
                }

                // Recalculate remaining primary cluster
                val remainingCount = summariesManager.getWorkoutCountForCluster(primaryClusterId)
                if (remainingCount > 0) {
                    val remainingCursor = summariesDb.query(
                        WorkoutSummaries.TABLE,
                        null,
                        "${WorkoutSummaries.CLUSTER_ID} = ?",
                        arrayOf(primaryClusterId.toString()),
                        null, null,
                        "${WorkoutSummaries.TIME_START} DESC"
                    )
                    var remStartLat = 0.0; var remStartLng = 0.0
                    var remEndLat = 0.0; var remEndLng = 0.0
                    var remMaxDispLat = 0.0; var remMaxDispLng = 0.0
                    var remRefDist = 0.0
                    var remMinLat: Double? = null; var remMaxLat: Double? = null
                    var remMinLng: Double? = null; var remMaxLng: Double? = null
                    val remPreviews = mutableListOf<String>()
                    var count = 0

                    remainingCursor.use { c ->
                        while (c.moveToNext()) {
                            val candidate = ClusterWorkoutCandidateDto.fromCursor(c)
                            val wId = candidate.workoutId
                            val dist = candidate.distance
                            val start = summariesManager.getExtremaPosition(wId, SensorType.LATITUDE, ExtremaType.START)
                            val end = summariesManager.getExtremaPosition(wId, SensorType.LATITUDE, ExtremaType.END)
                            val wMinLat = summariesManager.getDouble(wId, WorkoutSummaries.BOUND_MIN_LAT)
                            val wMinLng = summariesManager.getDouble(wId, WorkoutSummaries.BOUND_MIN_LNG)
                            val wMaxLat = summariesManager.getDouble(wId, WorkoutSummaries.BOUND_MAX_LAT)
                            val wMaxLng = summariesManager.getDouble(wId, WorkoutSummaries.BOUND_MAX_LNG)
                            val poly = summariesManager.getString(wId, WorkoutSummaries.MAP_POLYLINE)

                            if (start != null && end != null) {
                                val apex = summariesManager.getExtremaPosition(wId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX) ?: end
                                remStartLat += start.latitude
                                remStartLng += start.longitude
                                remEndLat += end.latitude
                                remEndLng += end.longitude
                                remMaxDispLat += apex.latitude
                                remMaxDispLng += apex.longitude
                                remRefDist += dist
                                count++

                                if (wMinLat != null) remMinLat = minOf(remMinLat ?: 90.0, wMinLat)
                                if (wMaxLat != null) remMaxLat = maxOf(remMaxLat ?: -90.0, wMaxLat)
                                if (wMinLng != null) remMinLng = minOf(remMinLng ?: 180.0, wMinLng)
                                if (wMaxLng != null) remMaxLng = maxOf(remMaxLng ?: -180.0, wMaxLng)
                                if (!poly.isNullOrEmpty() && remPreviews.size < 5 && !remPreviews.contains(poly)) {
                                    remPreviews.add(poly)
                                }
                            }
                        }
                    }

                    if (count > 0) {
                        val updatedPrimary = primaryCluster.copy(
                            hitCount = remainingCount,
                            startLat = remStartLat / count,
                            startLng = remStartLng / count,
                            endLat = remEndLat / count,
                            endLng = remEndLng / count,
                            maxDispLat = remMaxDispLat / count,
                            maxDispLng = remMaxDispLng / count,
                            refDistance = remRefDist / count,
                            minLat = remMinLat ?: primaryCluster.minLat,
                            maxLat = remMaxLat ?: primaryCluster.maxLat,
                            minLng = remMinLng ?: primaryCluster.minLng,
                            maxLng = remMaxLng ?: primaryCluster.maxLng,
                            previewPaths = remPreviews
                        )
                        dbManager.updateCluster(updatedPrimary)
                    }
                } else {
                    dbManager.updateCluster(primaryCluster.copy(hitCount = 0, previewPaths = emptyList()))
                }

                summariesDb.setTransactionSuccessful()
            } finally {
                summariesDb.endTransaction()
            }

            return createdClusterIds
        }
    }

    fun manuallyCreateCluster(name: String, sportId: Long, start: LatLng, end: LatLng, apex: LatLng, distance: Double): Long {
        synchronized(clusterLock) {
            return dbManager.insertCluster(WorkoutCluster(
                name = findUniqueClusterName(name), probableSportId = sportId, startLat = start.latitude, startLng = start.longitude,
                endLat = end.latitude, endLng = end.longitude, maxDispLat = apex.latitude, maxDispLng = apex.longitude,
                refDistance = distance, hitCount = 0, bSportType = SportTypesRepository.getInstance(appContext).getBSportType(sportId)
            ))
        }
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
