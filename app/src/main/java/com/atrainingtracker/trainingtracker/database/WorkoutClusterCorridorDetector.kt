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

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil

/**
 * Data model representing a detected route sub-corridor variant (REQ-SET-096.2, ATT-5900).
 *
 * @param groupId 1-based index of the detected variant group (Group 1 is primary).
 * @param isPrimary true if this group represents the largest route corridor retained under the original cluster.
 * @param workoutIds list of member workout IDs in this variant group.
 * @param workouts list of member [WorkoutData] instances.
 * @param avgDistance mean total distance of workouts in this variant group in meters.
 * @param suggestedName auto-generated cluster name suggestion for the variant.
 * @param representativePath medoid/seed polyline coordinates representing the corridor on the map.
 * @param allPaths all member workout polylines belonging to this variant group.
 */
data class ClusterCorridorVariant(
    val groupId: Int,
    val isPrimary: Boolean,
    val workoutIds: List<Long>,
    val workouts: List<WorkoutData>,
    val avgDistance: Double,
    val suggestedName: String,
    val representativePath: List<LatLng> = emptyList(),
    val allPaths: List<List<LatLng>> = emptyList()
)

/**
 * Algorithmic utility for polyline sub-corridor clustering (REQ-SET-096.2, ATT-5900).
 *
 * Samples decoded workout polylines at 5 fixed fractional distance stations (15%, 30%, 50%, 70%, 85%)
 * and computes maximum geodesic station deviation to partition workouts into distinct route variants.
 */
object WorkoutClusterCorridorDetector {

    /**
     * Fixed fractional distance sampling stations along the normalized polyline track length.
     */
    val STATIONS = floatArrayOf(0.15f, 0.30f, 0.50f, 0.70f, 0.85f)

    /**
     * Samples coordinates along a polyline at the 5 canonical fractional distance stations.
     */
    fun samplePolylineStations(path: List<LatLng>): List<LatLng> {
        if (path.isEmpty()) return List(STATIONS.size) { LatLng(0.0, 0.0) }
        if (path.size == 1) return List(STATIONS.size) { path[0] }

        val cumDistances = DoubleArray(path.size)
        for (i in 0 until path.size - 1) {
            cumDistances[i + 1] = cumDistances[i] + WorkoutClusterEngine.distanceBetween(path[i], path[i + 1])
        }

        val totalDist = cumDistances.last()
        if (totalDist <= 0.0) return List(STATIONS.size) { path[0] }

        return STATIONS.map { s ->
            val targetDist = s * totalDist
            var idx = 0
            while (idx < path.size - 2 && cumDistances[idx + 1] < targetDist) {
                idx++
            }
            val segDist = cumDistances[idx + 1] - cumDistances[idx]
            val frac = if (segDist > 0.0) ((targetDist - cumDistances[idx]) / segDist).coerceIn(0.0, 1.0) else 0.0
            LatLng(
                path[idx].latitude + frac * (path[idx + 1].latitude - path[idx].latitude),
                path[idx].longitude + frac * (path[idx + 1].longitude - path[idx].longitude)
            )
        }
    }

    /**
     * Computes the corridor distance between two polylines as the maximum geodesic deviation across sampled stations.
     */
    fun computeCorridorDistance(sampledA: List<LatLng>, sampledB: List<LatLng>): Double {
        var maxDeviation = 0.0
        val count = minOf(sampledA.size, sampledB.size)
        for (i in 0 until count) {
            val dist = WorkoutClusterEngine.distanceBetween(sampledA[i], sampledB[i]).toDouble()
            if (dist > maxDeviation) {
                maxDeviation = dist
            }
        }
        return maxDeviation
    }

    internal data class WorkoutPathItem(
        val workout: WorkoutData,
        val path: List<LatLng>,
        val stations: List<LatLng>
    )

    /**
     * Partitions a cluster's workouts into sub-corridor route variants based on polyline similarity.
     *
     * @param workouts list of member workouts in the source cluster.
     * @param baseClusterName original cluster name used for suggested variant naming.
     * @param corridorThresholdMeters maximum station deviation in meters to group polylines together (default 500m).
     * @param decodedPaths optional cache of already decoded polylines to avoid redundant decoding.
     * @return list of [ClusterCorridorVariant]s ordered descending by member count (Group 1 is primary).
     */
    fun detectCorridors(
        workouts: List<WorkoutData>,
        baseClusterName: String,
        corridorThresholdMeters: Double = 500.0,
        decodedPaths: Map<Long, List<LatLng>>? = null
    ): List<ClusterCorridorVariant> {
        if (workouts.isEmpty()) return emptyList()

        val validItems = mutableListOf<WorkoutPathItem>()
        val unspatialWorkouts = mutableListOf<WorkoutData>()

        for (w in workouts) {
            val path = decodedPaths?.get(w.id) ?: if (w.mapPolyline.isNotEmpty()) {
                try {
                    PolyUtil.decode(w.mapPolyline)
                } catch (_: Exception) {
                    emptyList()
                }
            } else emptyList()

            if (path.size >= 2) {
                val stations = samplePolylineStations(path)
                validItems.add(WorkoutPathItem(w, path, stations))
            } else {
                unspatialWorkouts.add(w)
            }
        }

        if (validItems.isEmpty()) {
            return listOf(
                ClusterCorridorVariant(
                    groupId = 1,
                    isPrimary = true,
                    workoutIds = workouts.map { it.id },
                    workouts = workouts,
                    avgDistance = workouts.map { it.totalDistance }.average().takeIf { !it.isNaN() } ?: 0.0,
                    suggestedName = baseClusterName,
                    representativePath = emptyList(),
                    allPaths = emptyList()
                )
            )
        }

        // Greedy medoid corridor clustering
        val unassigned = validItems.toMutableList()
        val groups = mutableListOf<MutableList<WorkoutPathItem>>()

        while (unassigned.isNotEmpty()) {
            val seed = unassigned.removeAt(0)
            val currentGroup = mutableListOf(seed)
            val iter = unassigned.iterator()
            while (iter.hasNext()) {
                val candidate = iter.next()
                if (computeCorridorDistance(seed.stations, candidate.stations) <= corridorThresholdMeters) {
                    currentGroup.add(candidate)
                    iter.remove()
                }
            }
            groups.add(currentGroup)
        }

        val sortedGroups = groups.sortedByDescending { it.size }
        val result = mutableListOf<ClusterCorridorVariant>()

        for (i in sortedGroups.indices) {
            val isPrimary = (i == 0)
            val groupItems = sortedGroups[i]
            val groupWorkouts = if (isPrimary) {
                groupItems.map { it.workout } + unspatialWorkouts
            } else {
                groupItems.map { it.workout }
            }
            val groupPaths = groupItems.map { it.path }
            val avgDist = groupWorkouts.map { it.totalDistance }.average().takeIf { !it.isNaN() } ?: 0.0
            val cleanBase = baseClusterName.replace(Regex(" (?:# ?|var )\\d+$", RegexOption.IGNORE_CASE), "").trim()
            val suggestedName = if (isPrimary) {
                baseClusterName
            } else {
                "$cleanBase var ${i + 1}"
            }

            result.add(
                ClusterCorridorVariant(
                    groupId = i + 1,
                    isPrimary = isPrimary,
                    workoutIds = groupWorkouts.map { it.id },
                    workouts = groupWorkouts,
                    avgDistance = avgDist,
                    suggestedName = suggestedName,
                    representativePath = groupPaths.firstOrNull() ?: emptyList(),
                    allPaths = groupPaths
                )
            )
        }

        return result
    }

    /**
     * Safely samples [workoutPolyline] once when corridor scoring is applicable, returning `null`
     * when [workoutPolyline] is null/empty, [hasAnyPreviewPaths] is false, or sampling fails.
     */
    internal fun samplePolylineStationsOrNull(
        workoutPolyline: List<LatLng>?,
        hasAnyPreviewPaths: Boolean
    ): List<LatLng>? {
        if (workoutPolyline.isNullOrEmpty() || !hasAnyPreviewPaths) return null
        return runCatching { samplePolylineStations(workoutPolyline) }.getOrNull()
    }
}

/**
 * Thread-safe, generation-guarded bounded LRU cache of decoded & station-sampled cluster
 * preview polylines (`REQ-MIG-062.2`, `ATT-9019`).
 *
 * Eliminates repeated `PolyUtil.decode` + [WorkoutClusterCorridorDetector.samplePolylineStations]
 * allocations across bulk import scoring while remaining immune to stale entries via both
 * explicit mutation invalidation ([invalidateCluster], [invalidateAll]) and structural
 * `previewPaths` equality validation on every lookup.
 */
internal class ClusterPreviewStationCache(
    private val maxClusters: Int = DEFAULT_MAX_CLUSTERS,
    private val maxUnsavedEntries: Int = DEFAULT_MAX_UNSAVED_ENTRIES
) {
    private data class ClusterStationsEntry(
        val previewPaths: List<String>,
        val byPolyline: Map<String, List<LatLng>?>,
        val sampledPaths: List<List<LatLng>>
    )

    private val cacheLock = Any()
    private var invalidationGeneration: Long = 0L

    private val clusterEntries = object : LinkedHashMap<Long, ClusterStationsEntry>(64, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Long, ClusterStationsEntry>?
        ): Boolean = size > maxClusters
    }

    private val unsavedEntries = object : LinkedHashMap<List<String>, List<List<LatLng>>>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<List<String>, List<List<LatLng>>>?
        ): Boolean = size > maxUnsavedEntries
    }

    /**
     * Returns the sampled station lists for each non-empty decoded polyline in [previewPaths].
     * Decoding/sampling executes outside [cacheLock] and never writes back if an invalidation
     * occurred concurrently.
     */
    fun getOrCompute(clusterId: Long, previewPaths: List<String>): List<List<LatLng>> {
        if (previewPaths.isEmpty()) return emptyList()

        if (clusterId > 0L) {
            val snapshotPaths = previewPaths.toList()
            val (existing, genBefore) = synchronized(cacheLock) {
                val entry = clusterEntries[clusterId]
                if (entry != null && entry.previewPaths == snapshotPaths) {
                    return entry.sampledPaths
                }
                entry to invalidationGeneration
            }

            val existingByPoly = existing?.byPolyline ?: emptyMap()
            val updatedByPoly = LinkedHashMap<String, List<LatLng>?>(snapshotPaths.size)
            val sampledPaths = ArrayList<List<LatLng>>(snapshotPaths.size)

            for (previewPoly in snapshotPaths) {
                val stations = if (updatedByPoly.containsKey(previewPoly)) {
                    updatedByPoly[previewPoly]
                } else if (existingByPoly.containsKey(previewPoly)) {
                    existingByPoly[previewPoly].also { updatedByPoly[previewPoly] = it }
                } else {
                    val decoded = PolyUtil.decode(previewPoly)
                    val computed = if (decoded.isNotEmpty()) {
                        WorkoutClusterCorridorDetector.samplePolylineStations(decoded)
                    } else {
                        null
                    }
                    updatedByPoly[previewPoly] = computed
                    computed
                }
                if (stations != null) {
                    sampledPaths.add(stations)
                }
            }

            val immutableSampled = sampledPaths.toList()
            val newEntry = ClusterStationsEntry(
                previewPaths = snapshotPaths,
                byPolyline = updatedByPoly,
                sampledPaths = immutableSampled
            )
            synchronized(cacheLock) {
                if (invalidationGeneration == genBefore) {
                    clusterEntries[clusterId] = newEntry
                }
            }
            return immutableSampled
        }

        val snapshotPaths = previewPaths.toList()
        val genBefore = synchronized(cacheLock) {
            unsavedEntries[snapshotPaths]?.let { return it }
            invalidationGeneration
        }

        val sampledPaths = snapshotPaths.mapNotNull { previewPoly ->
            val decoded = PolyUtil.decode(previewPoly)
            if (decoded.isNotEmpty()) {
                WorkoutClusterCorridorDetector.samplePolylineStations(decoded)
            } else {
                null
            }
        }

        synchronized(cacheLock) {
            if (invalidationGeneration == genBefore) {
                unsavedEntries[snapshotPaths] = sampledPaths
            }
        }
        return sampledPaths
    }

    /**
     * Computes the weighted corridor deviation penalty (`(minCorridorDev / 500.0) * 0.30`) against
     * cached cluster preview stations, preserving exact `REQ-SET-097.3` similarity math.
     */
    fun computeCorridorCost(
        clusterId: Long,
        previewPaths: List<String>,
        workoutStations: List<LatLng>?
    ): Double {
        if (workoutStations == null || previewPaths.isEmpty()) return 0.0
        val minCorridorDev = getOrCompute(clusterId, previewPaths)
            .minOfOrNull { clusterStations ->
                WorkoutClusterCorridorDetector.computeCorridorDistance(workoutStations, clusterStations)
            }
        return if (minCorridorDev != null) (minCorridorDev / 500.0) * 0.30 else 0.0
    }

    fun invalidateCluster(clusterId: Long) {
        synchronized(cacheLock) {
            invalidationGeneration++
            if (clusterId > 0L) {
                clusterEntries.remove(clusterId)
            } else {
                unsavedEntries.clear()
            }
        }
    }

    fun invalidateAll() {
        synchronized(cacheLock) {
            invalidationGeneration++
            clusterEntries.clear()
            unsavedEntries.clear()
        }
    }

    companion object {
        internal const val DEFAULT_MAX_CLUSTERS = 512
        internal const val DEFAULT_MAX_UNSAVED_ENTRIES = 64
    }
}
