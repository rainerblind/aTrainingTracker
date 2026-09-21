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

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import android.location.Location
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlin.math.*

/**
 * Configuration constants for spatial region clustering.
 * (REQ-PER-013)
 */
object SpatialRegionConfig {
    /** Geodesic distance threshold in meters to partition workouts into distinct regions (75 km). */
    const val REGION_CLUSTER_THRESHOLD_METERS = 75_000.0

    /** Envelope diagonal threshold below which spatial partitioning fast-paths to a single region (60 km). */
    const val SINGLE_REGION_ENVELOPE_METERS = 60_000.0
}

/**
 * Represents a distinct geographical activity area during a training period.
 * (REQ-PER-013)
 */
data class SpatialRegion(
    val id: String,
    val label: String,
    val bounds: LatLngBounds,
    val center: LatLng,
    val workoutCount: Int,
    val totalDistanceMeters: Double,
    val mostRecentTimestampS: Long,
    val isPrimary: Boolean = false,
    val workoutIds: Set<Long> = emptySet()
)

/**
 * Bundles a [SpatialRegion] with the concrete polyline paths belonging to that region.
 * Used for regional thumbnail rendering in period summary cards (ATT-1151).
 */
data class SpatialPathRegion(
    val region: SpatialRegion,
    val paths: List<List<LatLng>>
)

/**
 * Core mathematical engine for partitioning period workouts into geographic activity regions.
 *
 * Solves the "Greenland Anomaly" (ATT-1151) where periods spanning multiple continents/regions
 * (e.g. Europe and USA) result in empty ocean-centered bounding boxes.
 *
 * Threading Contract: All operations are pure mathematical computations and should execute
 * on Dispatchers.Default inside PeriodsViewModel during background loading.
 */
object SpatialRegionEngine {

    /** Minimum coordinate buffer (~500m) to prevent degenerate zero-area bounding boxes in Google Maps. */
    const val MIN_BOUNDS_DELTA_DEGREES = 0.005

    /**
     * Computes the Great-Circle Haversine distance between two coordinates in meters.
     * Includes fallback calculation for JVM unit test execution where android.location.Location is stubbed.
     */
    fun distanceBetween(p1: LatLng, p2: LatLng): Double {
        return try {
            val res = FloatArray(1)
            Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, res)
            res[0].toDouble()
        } catch (_: RuntimeException) {
            val earthRadius = 6371000.0
            val dLat = Math.toRadians(p2.latitude - p1.latitude)
            val dLon = Math.toRadians(p2.longitude - p1.longitude)
            val a = sin(dLat / 2).pow(2.0) +
                    cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) *
                    sin(dLon / 2).pow(2.0)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            earthRadius * c
        }
    }

    /**
     * Constructs normalized [LatLngBounds] for a list of coordinates with antimeridian (180°) support
     * and polar Web Mercator clamping ([-85°, 85°]).
     *
     * Ensures bounds have non-zero latitude and longitude span so that Google Maps [CameraUpdateFactory.newLatLngBounds]
     * does not throw [IllegalArgumentException].
     */
    fun buildNormalizedBounds(points: List<LatLng>): LatLngBounds {
        require(points.isNotEmpty()) { "Cannot build bounds from empty points list" }
        if (points.size == 1) {
            val p = clampPoint(points[0])
            val minLat = clampLatitude(p.latitude - MIN_BOUNDS_DELTA_DEGREES)
            val maxLat = clampLatitude(p.latitude + MIN_BOUNDS_DELTA_DEGREES)
            val minLng = p.longitude - MIN_BOUNDS_DELTA_DEGREES
            val maxLng = p.longitude + MIN_BOUNDS_DELTA_DEGREES
            return LatLngBounds(LatLng(minLat, minLng), LatLng(maxLat, maxLng))
        }

        var minLat = 90.0
        var maxLat = -90.0
        for (p in points) {
            val lat = clampLatitude(p.latitude)
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
        }

        // Longitude calculation with antimeridian wrapping detection
        // 1. Standard longitude range [-180, 180]
        val lngs = points.map { it.longitude }
        val standardMinLng = lngs.minOrNull() ?: 0.0
        val standardMaxLng = lngs.maxOrNull() ?: 0.0
        val standardSpan = standardMaxLng - standardMinLng

        // 2. Wrapped longitude range into [0, 360) space
        val wrappedLngs = lngs.map { if (it < 0) it + 360.0 else it }
        val wrappedMinLng = wrappedLngs.minOrNull() ?: 0.0
        val wrappedMaxLng = wrappedLngs.maxOrNull() ?: 0.0
        val wrappedSpan = wrappedMaxLng - wrappedMinLng

        // Guard against zero/near-zero latitude span to prevent IllegalArgumentException in Google Maps
        if (maxLat - minLat < 0.001) {
            minLat = clampLatitude(minLat - MIN_BOUNDS_DELTA_DEGREES)
            maxLat = clampLatitude(maxLat + MIN_BOUNDS_DELTA_DEGREES)
        }

        return if (standardSpan > 180.0 && wrappedSpan < standardSpan) {
            // Antimeridian crossing: the cluster is compact across the 180° meridian.
            // Google Maps LatLngBounds supports southwest.longitude > northeast.longitude
            // representing a bounding box that crosses the antimeridian eastward.
            var westLng = if (wrappedMinLng > 180.0) wrappedMinLng - 360.0 else wrappedMinLng
            var eastLng = if (wrappedMaxLng > 180.0) wrappedMaxLng - 360.0 else wrappedMaxLng
            if (wrappedSpan < 0.001) {
                westLng -= MIN_BOUNDS_DELTA_DEGREES
                eastLng += MIN_BOUNDS_DELTA_DEGREES
            }
            LatLngBounds(LatLng(minLat, westLng), LatLng(maxLat, eastLng))
        } else {
            var westLng = standardMinLng
            var eastLng = standardMaxLng
            if (standardSpan < 0.001) {
                westLng -= MIN_BOUNDS_DELTA_DEGREES
                eastLng += MIN_BOUNDS_DELTA_DEGREES
            }
            LatLngBounds(LatLng(minLat, westLng), LatLng(maxLat, eastLng))
        }
    }

    private fun clampLatitude(lat: Double): Double = lat.coerceIn(-85.0, 85.0)

    private fun clampPoint(p: LatLng): LatLng = LatLng(clampLatitude(p.latitude), p.longitude)

    private data class IntermediateRegion(
        val workouts: MutableList<WorkoutData> = mutableListOf(),
        val points: MutableList<LatLng> = mutableListOf()
    ) {
        fun center(): LatLng {
            if (points.isEmpty()) return LatLng(0.0, 0.0)
            val avgLat = points.map { it.latitude }.average()
            val avgLng = points.map { it.longitude }.average()
            return LatLng(avgLat, avgLng)
        }

        fun distanceTo(point: LatLng): Double {
            // Geodesic distance to cluster center or nearest anchor point
            var minDist = distanceBetween(center(), point)
            for (p in points) {
                val d = distanceBetween(p, point)
                if (d < minDist) minDist = d
            }
            return minDist
        }
    }

    /**
     * Extracts an authoritative reference point for a workout.
     * Prefers startLatLng, then coordinate extrema midpoint. Bypasses null and (0,0) sentinels.
     */
    private fun getWorkoutReferencePoint(w: WorkoutData): LatLng? {
        val start = w.startLatLng
        if (start != null && isValidCoordinate(start.latitude, start.longitude)) {
            return start
        }
        val minLat = w.minLat
        val maxLat = w.maxLat
        val minLng = w.minLng
        val maxLng = w.maxLng
        if (minLat != null && maxLat != null && minLng != null && maxLng != null &&
            isValidCoordinate(minLat, minLng) && isValidCoordinate(maxLat, maxLng)) {
            return LatLng((minLat + maxLat) / 2.0, (minLng + maxLng) / 2.0)
        }
        return null
    }

    private fun isValidCoordinate(lat: Double, lng: Double): Boolean {
        if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) return false
        if (abs(lat) < 0.0001 && abs(lng) < 0.0001) return false
        return true
    }

    /**
     * Partitions a list of coordinates into distinct [SpatialRegion]s directly.
     * Useful for lightweight thumbnail clustering without requiring full WorkoutData objects.
     */
    fun detectRegionsFromPoints(points: List<LatLng>): List<SpatialRegion> {
        val validPoints = points.filter { isValidCoordinate(it.latitude, it.longitude) }
        if (validPoints.isEmpty()) return emptyList()

        if (validPoints.size == 1) {
            val p = validPoints[0]
            val bounds = buildNormalizedBounds(listOf(p))
            return listOf(
                SpatialRegion(
                    id = "region_0",
                    label = "Region 1",
                    bounds = bounds,
                    center = p,
                    workoutCount = 1,
                    totalDistanceMeters = 0.0,
                    mostRecentTimestampS = 0L,
                    isPrimary = true
                )
            )
        }

        val minLat = validPoints.minOf { it.latitude }
        val maxLat = validPoints.maxOf { it.latitude }
        val minLng = validPoints.minOf { it.longitude }
        val maxLng = validPoints.maxOf { it.longitude }
        val diagonal = distanceBetween(LatLng(minLat, minLng), LatLng(maxLat, maxLng))

        if (diagonal < SpatialRegionConfig.SINGLE_REGION_ENVELOPE_METERS) {
            val bounds = buildNormalizedBounds(validPoints)
            val center = LatLng((minLat + maxLat) / 2.0, (minLng + maxLng) / 2.0)
            return listOf(
                SpatialRegion(
                    id = "region_0",
                    label = "Region 1",
                    bounds = bounds,
                    center = center,
                    workoutCount = validPoints.size,
                    totalDistanceMeters = 0.0,
                    mostRecentTimestampS = 0L,
                    isPrimary = true
                )
            )
        }

        // Multi-Region clustering for points
        val intermediateClusters = mutableListOf<MutableList<LatLng>>()
        for (point in validPoints) {
            val matchingIndices = mutableListOf<Int>()
            for (i in intermediateClusters.indices) {
                val clusterPoints = intermediateClusters[i]
                var minDist = Double.MAX_VALUE
                for (p in clusterPoints) {
                    val d = distanceBetween(p, point)
                    if (d < minDist) minDist = d
                }
                if (minDist <= SpatialRegionConfig.REGION_CLUSTER_THRESHOLD_METERS) {
                    matchingIndices.add(i)
                }
            }

            when {
                matchingIndices.isEmpty() -> {
                    intermediateClusters.add(mutableListOf(point))
                }
                matchingIndices.size == 1 -> {
                    intermediateClusters[matchingIndices[0]].add(point)
                }
                else -> {
                    val base = intermediateClusters[matchingIndices[0]]
                    base.add(point)
                    for (k in matchingIndices.size - 1 downTo 1) {
                        base.addAll(intermediateClusters.removeAt(matchingIndices[k]))
                    }
                }
            }
        }

        val sorted = intermediateClusters.sortedByDescending { it.size }
        return sorted.mapIndexed { index, clusterPts ->
            val bounds = buildNormalizedBounds(clusterPts)
            val avgLat = clusterPts.map { it.latitude }.average()
            val avgLng = clusterPts.map { it.longitude }.average()
            SpatialRegion(
                id = "region_$index",
                label = "Region ${index + 1}",
                bounds = bounds,
                center = LatLng(avgLat, avgLng),
                workoutCount = clusterPts.size,
                totalDistanceMeters = 0.0,
                mostRecentTimestampS = 0L,
                isPrimary = (index == 0)
            )
        }
    }

    /**
     * Partitions a list of workouts into distinct [SpatialRegion]s.
     *
     * @param workouts The list of workouts belonging to the aggregation period.
     * @return List of detected regions, ranked deterministically with primary region first.
     */
    fun detectRegions(workouts: List<WorkoutData>): List<SpatialRegion> {
        val validWorkoutsWithPoints = workouts.mapNotNull { w ->
            val refPoint = getWorkoutReferencePoint(w) ?: return@mapNotNull null
            w to refPoint
        }

        if (validWorkoutsWithPoints.isEmpty()) {
            return emptyList()
        }

        // Single-Region Fast-Path: 1 workout
        if (validWorkoutsWithPoints.size == 1) {
            val (w, p) = validWorkoutsWithPoints[0]
            val workoutPoints = extractWorkoutBoundsPoints(w)
            val bounds = buildNormalizedBounds(workoutPoints.ifEmpty { listOf(p) })
            return listOf(
                SpatialRegion(
                    id = "region_0",
                    label = "Region 1",
                    bounds = bounds,
                    center = p,
                    workoutCount = 1,
                    totalDistanceMeters = w.totalDistance,
                    mostRecentTimestampS = w.startTimeS,
                    isPrimary = true,
                    workoutIds = setOf(w.id)
                )
            )
        }

        // Single-Region Fast-Path: Diagonal envelope check (< SINGLE_REGION_ENVELOPE_METERS)
        val allPoints = validWorkoutsWithPoints.map { it.second }
        val minLat = allPoints.minOf { it.latitude }
        val maxLat = allPoints.maxOf { it.latitude }
        val minLng = allPoints.minOf { it.longitude }
        val maxLng = allPoints.maxOf { it.longitude }
        val diagonal = distanceBetween(LatLng(minLat, minLng), LatLng(maxLat, maxLng))

        if (diagonal < SpatialRegionConfig.SINGLE_REGION_ENVELOPE_METERS) {
            val allBoundsPoints = validWorkoutsWithPoints.flatMap { (w, p) ->
                extractWorkoutBoundsPoints(w).ifEmpty { listOf(p) }
            }
            val bounds = buildNormalizedBounds(allBoundsPoints)
            val center = LatLng((minLat + maxLat) / 2.0, (minLng + maxLng) / 2.0)
            val totalDistance = validWorkoutsWithPoints.sumOf { it.first.totalDistance }
            val mostRecent = validWorkoutsWithPoints.maxOf { it.first.startTimeS }
            return listOf(
                SpatialRegion(
                    id = "region_0",
                    label = "Region 1",
                    bounds = bounds,
                    center = center,
                    workoutCount = validWorkoutsWithPoints.size,
                    totalDistanceMeters = totalDistance,
                    mostRecentTimestampS = mostRecent,
                    isPrimary = true,
                    workoutIds = validWorkoutsWithPoints.map { it.first.id }.toSet()
                )
            )
        }

        // Multi-Region Connected Component Partitioning
        val intermediateClusters = mutableListOf<IntermediateRegion>()
        for ((workout, point) in validWorkoutsWithPoints) {
            val matchingIndices = mutableListOf<Int>()
            for (i in intermediateClusters.indices) {
                if (intermediateClusters[i].distanceTo(point) <= SpatialRegionConfig.REGION_CLUSTER_THRESHOLD_METERS) {
                    matchingIndices.add(i)
                }
            }

            when {
                matchingIndices.isEmpty() -> {
                    val newCluster = IntermediateRegion()
                    newCluster.workouts.add(workout)
                    newCluster.points.add(point)
                    intermediateClusters.add(newCluster)
                }
                matchingIndices.size == 1 -> {
                    val cluster = intermediateClusters[matchingIndices[0]]
                    cluster.workouts.add(workout)
                    cluster.points.add(point)
                }
                else -> {
                    // Bridges multiple clusters: merge them
                    val baseCluster = intermediateClusters[matchingIndices[0]]
                    baseCluster.workouts.add(workout)
                    baseCluster.points.add(point)
                    for (k in matchingIndices.size - 1 downTo 1) {
                        val toMerge = intermediateClusters.removeAt(matchingIndices[k])
                        baseCluster.workouts.addAll(toMerge.workouts)
                        baseCluster.points.addAll(toMerge.points)
                    }
                }
            }
        }

        // Deterministic Ranking of Clusters:
        // 1. Workout Count (descending)
        // 2. Total Distance (descending)
        // 3. Most Recent Workout Timestamp (descending)
        val sortedClusters = intermediateClusters.sortedWith(
            compareByDescending<IntermediateRegion> { it.workouts.size }
                .thenByDescending { cluster -> cluster.workouts.sumOf { it.totalDistance } }
                .thenByDescending { cluster -> cluster.workouts.maxOfOrNull { it.startTimeS } ?: 0L }
        )

        return sortedClusters.mapIndexed { index, cluster ->
            val clusterWorkoutPoints = cluster.workouts.flatMap { extractWorkoutBoundsPoints(it) }
            val allClusterPoints = if (clusterWorkoutPoints.isNotEmpty()) clusterWorkoutPoints else cluster.points
            val bounds = buildNormalizedBounds(allClusterPoints)
            val center = cluster.center()
            val totalDistance = cluster.workouts.sumOf { it.totalDistance }
            val mostRecent = cluster.workouts.maxOfOrNull { it.startTimeS } ?: 0L
            SpatialRegion(
                id = "region_$index",
                label = "Region ${index + 1}",
                bounds = bounds,
                center = center,
                workoutCount = cluster.workouts.size,
                totalDistanceMeters = totalDistance,
                mostRecentTimestampS = mostRecent,
                isPrimary = (index == 0),
                workoutIds = cluster.workouts.map { it.id }.toSet()
            )
        }
    }

    private fun extractWorkoutBoundsPoints(w: WorkoutData): List<LatLng> {
        val points = mutableListOf<LatLng>()
        if (w.minLat != null && w.maxLat != null && w.minLng != null && w.maxLng != null &&
            w.minLat < 90.0 && w.maxLat > -90.0 && w.minLat <= w.maxLat
        ) {
            points.add(LatLng(w.minLat, w.minLng))
            points.add(LatLng(w.maxLat, w.maxLng))
        }
        w.startLatLng?.let { if (isValidCoordinate(it.latitude, it.longitude)) points.add(it) }
        w.endLatLng?.let { if (isValidCoordinate(it.latitude, it.longitude)) points.add(it) }
        w.maxDisplacementLatLng?.let { if (isValidCoordinate(it.latitude, it.longitude)) points.add(it) }
        return points
    }

    /**
     * Partitions a list of decoded polyline paths into distinct [SpatialPathRegion]s.
     * Encloses ALL route coordinates belonging to each cluster within the region bounds.
     * Used by the period summary card thumbnail to isolate the dominant region's route paths.
     */
    fun detectRegionsFromPaths(paths: List<List<LatLng>>): List<SpatialPathRegion> {
        val validPathsWithRef = paths.mapNotNull { path ->
            if (path.isEmpty()) return@mapNotNull null
            val refPoint = path.first()
            if (!isValidCoordinate(refPoint.latitude, refPoint.longitude)) return@mapNotNull null
            path to refPoint
        }

        if (validPathsWithRef.isEmpty()) {
            return emptyList()
        }

        if (validPathsWithRef.size == 1) {
            val (path, ref) = validPathsWithRef[0]
            val bounds = buildNormalizedBounds(path)
            val region = SpatialRegion(
                id = "region_0",
                label = "Region 1",
                bounds = bounds,
                center = ref,
                workoutCount = 1,
                totalDistanceMeters = 0.0,
                mostRecentTimestampS = 0L,
                isPrimary = true
            )
            return listOf(SpatialPathRegion(region, listOf(path)))
        }

        val allRefPoints = validPathsWithRef.map { it.second }
        val minLat = allRefPoints.minOf { it.latitude }
        val maxLat = allRefPoints.maxOf { it.latitude }
        val minLng = allRefPoints.minOf { it.longitude }
        val maxLng = allRefPoints.maxOf { it.longitude }
        val diagonal = distanceBetween(LatLng(minLat, minLng), LatLng(maxLat, maxLng))

        if (diagonal < SpatialRegionConfig.SINGLE_REGION_ENVELOPE_METERS) {
            val allPoints = validPathsWithRef.flatMap { it.first }
            val bounds = buildNormalizedBounds(allPoints)
            val center = LatLng((minLat + maxLat) / 2.0, (minLng + maxLng) / 2.0)
            val region = SpatialRegion(
                id = "region_0",
                label = "Region 1",
                bounds = bounds,
                center = center,
                workoutCount = validPathsWithRef.size,
                totalDistanceMeters = 0.0,
                mostRecentTimestampS = 0L,
                isPrimary = true
            )
            return listOf(SpatialPathRegion(region, validPathsWithRef.map { it.first }))
        }

        // Multi-region clustering for paths
        data class IntermediatePathCluster(
            val paths: MutableList<List<LatLng>> = mutableListOf(),
            val refPoints: MutableList<LatLng> = mutableListOf()
        ) {
            fun center(): LatLng {
                if (refPoints.isEmpty()) return LatLng(0.0, 0.0)
                val avgLat = refPoints.map { it.latitude }.average()
                val avgLng = refPoints.map { it.longitude }.average()
                return LatLng(avgLat, avgLng)
            }

            fun distanceTo(point: LatLng): Double {
                var minDist = distanceBetween(center(), point)
                for (p in refPoints) {
                    val d = distanceBetween(p, point)
                    if (d < minDist) minDist = d
                }
                return minDist
            }
        }

        val clusters = mutableListOf<IntermediatePathCluster>()
        for ((path, ref) in validPathsWithRef) {
            val matchingIndices = mutableListOf<Int>()
            for (i in clusters.indices) {
                if (clusters[i].distanceTo(ref) <= SpatialRegionConfig.REGION_CLUSTER_THRESHOLD_METERS) {
                    matchingIndices.add(i)
                }
            }

            when {
                matchingIndices.isEmpty() -> {
                    val newCluster = IntermediatePathCluster()
                    newCluster.paths.add(path)
                    newCluster.refPoints.add(ref)
                    clusters.add(newCluster)
                }
                matchingIndices.size == 1 -> {
                    val cluster = clusters[matchingIndices[0]]
                    cluster.paths.add(path)
                    cluster.refPoints.add(ref)
                }
                else -> {
                    val base = clusters[matchingIndices[0]]
                    base.paths.add(path)
                    base.refPoints.add(ref)
                    for (k in matchingIndices.size - 1 downTo 1) {
                        val toMerge = clusters.removeAt(matchingIndices[k])
                        base.paths.addAll(toMerge.paths)
                        base.refPoints.addAll(toMerge.refPoints)
                    }
                }
            }
        }

        // Deterministic Ranking:
        // 1. Path count (descending)
        // 2. Coordinate count (descending)
        val sorted = clusters.sortedWith(
            compareByDescending<IntermediatePathCluster> { it.paths.size }
                .thenByDescending { it.paths.sumOf { p -> p.size } }
        )

        return sorted.mapIndexed { index, cluster ->
            val allClusterPoints = cluster.paths.flatten()
            val bounds = buildNormalizedBounds(allClusterPoints)
            val center = cluster.center()
            val region = SpatialRegion(
                id = "region_$index",
                label = "Region ${index + 1}",
                bounds = bounds,
                center = center,
                workoutCount = cluster.paths.size,
                totalDistanceMeters = 0.0,
                mostRecentTimestampS = 0L,
                isPrimary = (index == 0)
            )
            SpatialPathRegion(region, cluster.paths)
        }
    }
}
