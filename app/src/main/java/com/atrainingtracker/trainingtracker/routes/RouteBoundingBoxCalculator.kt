package com.atrainingtracker.trainingtracker.routes

import com.atrainingtracker.trainingtracker.ui.map.PathPoint

/**
 * Geographic bounding box representation for route previews.
 */
data class RouteBoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double
) {
    val centerLat: Double get() = (minLat + maxLat) / 2.0
    val centerLng: Double get() = (minLng + maxLng) / 2.0
    val spanLat: Double get() = maxLat - minLat
    val spanLng: Double get() = maxLng - minLng
}

/**
 * Pure mathematical calculator for global and differentiating middle-loop bounding boxes.
 *
 * Implements requirement REQ-MAP-030.
 */
object RouteBoundingBoxCalculator {

    private const val MIN_SPAN_DEGREES = 0.002 // ~200 meters minimum dimension
    private const val MIN_LOOP_FRACTION = 0.20 // Unique loop must be at least 20% of route length

    /**
     * Calculates the standard global bounding box encompassing all points in the polyline.
     */
    fun calculateGlobalBounds(path: List<PathPoint>): RouteBoundingBox {
        if (path.isEmpty()) {
            return RouteBoundingBox(0.0, 0.0, 0.0, 0.0)
        }

        var minLat = path[0].latLng.latitude
        var maxLat = path[0].latLng.latitude
        var minLng = path[0].latLng.longitude
        var maxLng = path[0].latLng.longitude

        for (p in path) {
            val lat = p.latLng.latitude
            val lng = p.latLng.longitude
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lng < minLng) minLng = lng
            if (lng > maxLng) maxLng = lng
        }

        return RouteBoundingBox(minLat, maxLat, minLng, maxLng)
    }

    /**
     * Calculates a focused bounding box centered on the unique middle loop by cropping
     * common departure and arrival corridors.
     *
     * @param path List of route trackpoints
     * @param departureCorridorDistMeters Distance from start considered departure corridor (default 1500m)
     * @param returnCorridorDistMeters Distance from end considered return corridor (default 1500m)
     * @param paddingPercent Safety padding multiplier applied to middle loop bounds (default 0.15 = 15%)
     * @return [RouteBoundingBox] focused on the unique middle loop, or global bounds if loop is negligible
     */
    fun calculateDifferentiatingBounds(
        path: List<PathPoint>,
        departureCorridorDistMeters: Double = 1500.0,
        returnCorridorDistMeters: Double = 1500.0,
        paddingPercent: Double = 0.15
    ): RouteBoundingBox {
        if (path.size < 4) {
            return calculateGlobalBounds(path)
        }

        // 1. Calculate cumulative distance along polyline
        val cumulativeDists = DoubleArray(path.size)
        var runningDist = 0.0
        cumulativeDists[0] = 0.0

        for (i in 0 until path.size - 1) {
            val p1 = path[i].latLng
            val p2 = path[i + 1].latLng
            val d = RouteCorridorClassifier.haversineDistanceMeters(
                p1.latitude, p1.longitude, p2.latitude, p2.longitude
            )
            runningDist += d
            cumulativeDists[i + 1] = runningDist
        }

        val totalDist = runningDist
        val uniqueLoopDist = totalDist - (departureCorridorDistMeters + returnCorridorDistMeters)

        // If route is too short or unique loop fraction is under 20%, fall back cleanly to global bounds
        if (uniqueLoopDist <= 0 || (uniqueLoopDist / totalDist) < MIN_LOOP_FRACTION) {
            return calculateGlobalBounds(path)
        }

        val startDist = departureCorridorDistMeters
        val endDist = totalDist - returnCorridorDistMeters

        // 2. Filter points strictly within the unique middle segment
        val middlePoints = mutableListOf<PathPoint>()
        for (i in path.indices) {
            val d = cumulativeDists[i]
            if (d in startDist..endDist) {
                middlePoints.add(path[i])
            }
        }

        if (middlePoints.size < 2) {
            return calculateGlobalBounds(path)
        }

        var minLat = middlePoints[0].latLng.latitude
        var maxLat = middlePoints[0].latLng.latitude
        var minLng = middlePoints[0].latLng.longitude
        var maxLng = middlePoints[0].latLng.longitude

        for (p in middlePoints) {
            val lat = p.latLng.latitude
            val lng = p.latLng.longitude
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lng < minLng) minLng = lng
            if (lng > maxLng) maxLng = lng
        }

        // 3. Apply padding
        var latSpan = maxLat - minLat
        var lngSpan = maxLng - minLng

        if (latSpan < MIN_SPAN_DEGREES) latSpan = MIN_SPAN_DEGREES
        if (lngSpan < MIN_SPAN_DEGREES) lngSpan = MIN_SPAN_DEGREES

        val latPad = latSpan * paddingPercent
        val lngPad = lngSpan * paddingPercent

        val paddedMinLat = (minLat - latPad).coerceIn(-90.0, 90.0)
        val paddedMaxLat = (maxLat + latPad).coerceIn(-90.0, 90.0)
        val paddedMinLng = (minLng - lngPad).coerceIn(-180.0, 180.0)
        val paddedMaxLng = (maxLng + lngPad).coerceIn(-180.0, 180.0)

        return RouteBoundingBox(paddedMinLat, paddedMaxLat, paddedMinLng, paddedMaxLng)
    }
}
