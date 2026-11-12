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

package com.atrainingtracker.trainingtracker.climbs

import com.google.android.gms.maps.model.LatLng
import kotlin.math.*

/**
 * Pure geometric and corridor engine for identifying, deduplicating, and consolidating
 * overlapping climbs along identical road segments (REQ-CLM-011, TST-CLM-011).
 */
object ClimbDeduplicator {

    const val DEFAULT_ENDPOINT_THRESHOLD_METERS = 100.0f
    const val DEFAULT_CORRIDOR_TOLERANCE_METERS = 40.0
    const val DEFAULT_MIN_OVERLAP_FRACTION = 0.70
    const val MAX_HEADING_DIFFERENCE_DEGREES = 45.0

    /**
     * Pure spherical distance calculation (Haversine formula) in meters.
     * Free of Android framework dependencies for fast, deterministic execution across all environments.
     */
    fun computeDistanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2.0).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2.0).pow(2)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return 6371000.0 * c
    }

    /**
     * Calculates the forward azimuth / heading in degrees (0..360) from (lat1, lng1) to (lat2, lng2).
     */
    fun computeHeading(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLng = Math.toRadians(lng2 - lng1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val y = sin(dLng) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLng)
        val heading = Math.toDegrees(atan2(y, x))
        return (heading + 360.0) % 360.0
    }

    /**
     * Computes the perpendicular distance in meters from point [p] to segment [[a], [b]]
     * in local equirectangular coordinate space.
     */
    fun distanceToSegmentMeters(p: LatLng, a: LatLng, b: LatLng): Double {
        val meanLatRad = Math.toRadians((a.latitude + b.latitude) / 2.0)
        val cosPhi = cos(meanLatRad).coerceAtLeast(1e-6)

        val dX = (b.longitude - a.longitude) * cosPhi
        val dY = b.latitude - a.latitude
        val segLenSq = dX * dX + dY * dY

        val t = if (segLenSq > 0.0) {
            val pX = (p.longitude - a.longitude) * cosPhi
            val pY = p.latitude - a.latitude
            val u = (pX * dX + pY * dY) / segLenSq
            u.coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        val projLat = a.latitude + t * (b.latitude - a.latitude)
        val projLng = a.longitude + t * (b.longitude - a.longitude)
        return computeDistanceMeters(p.latitude, p.longitude, projLat, projLng)
    }

    /**
     * Evaluates whether two climbs represent identical or heavily overlapping ascents along
     * the same road segment (REQ-CLM-011.1).
     *
     * Criteria:
     * 1. Start and summit endpoints are within [thresholdMeters] (clamped to at least 100m).
     * OR
     * 2. Start-to-summit heading aligns within 45°, elevation gains are compatible,
     *    and spatial corridor overlap is >= 70% within 40m cross-track corridor.
     */
    fun isDuplicateOrOverlapping(
        climbA: Climb,
        climbB: Climb,
        thresholdMeters: Float = DEFAULT_ENDPOINT_THRESHOLD_METERS
    ): Boolean {
        if (climbA.id != 0L && climbA.id == climbB.id) return true

        val effectiveThreshold = maxOf(thresholdMeters, DEFAULT_ENDPOINT_THRESHOLD_METERS)

        // 0. O(1) Fast Geographic Bounding-Box Pre-Filter (REQ-CLM-011.1)
        if (!climbA.boundingBox.overlaps(climbB.boundingBox, effectiveThreshold.toDouble())) {
            return false
        }

        val startDist = computeDistanceMeters(climbA.startLat, climbA.startLng, climbB.startLat, climbB.startLng)
        val endDist = computeDistanceMeters(climbA.endLat, climbA.endLng, climbB.endLat, climbB.endLng)

        // 1. Direct endpoint proximity match
        if (startDist <= effectiveThreshold && endDist <= effectiveThreshold) {
            return true
        }

        // 2. Heading alignment check
        val headingA = computeHeading(climbA.startLat, climbA.startLng, climbA.endLat, climbA.endLng)
        val headingB = computeHeading(climbB.startLat, climbB.startLng, climbB.endLat, climbB.endLng)
        val diff = abs(headingA - headingB)
        val bearingDiff = minOf(diff, 360.0 - diff)
        if (bearingDiff > MAX_HEADING_DIFFERENCE_DEGREES) {
            return false
        }

        // 3. Elevation gain compatibility check
        val maxGain = maxOf(climbA.elevationGainMeters, climbB.elevationGainMeters)
        if (abs(climbA.elevationGainMeters - climbB.elevationGainMeters) > maxGain * 0.50 + 25.0) {
            return false
        }

        // 4. Spatial corridor overlap check with segment pruning and early-exit (REQ-CLM-011.1)
        val shorter = if (climbA.distanceMeters <= climbB.distanceMeters) climbA else climbB
        val longer = if (climbA.distanceMeters <= climbB.distanceMeters) climbB else climbA

        val shorterPoints = if (shorter.pathPoints.size >= 2) {
            shorter.pathPoints.map { it.latLng }
        } else {
            listOf(shorter.startLatLng, shorter.endLatLng)
        }

        val longerPoints = if (longer.pathPoints.size >= 2) {
            longer.pathPoints.map { it.latLng }
        } else {
            listOf(longer.startLatLng, longer.endLatLng)
        }

        val step = maxOf(1, shorterPoints.size / 15)
        val samplePoints = shorterPoints.filterIndexed { index, _ -> index % step == 0 || index == shorterPoints.lastIndex }
        if (samplePoints.isEmpty()) return false

        val totalSamples = samplePoints.size
        val requiredMatches = ceil(totalSamples * DEFAULT_MIN_OVERLAP_FRACTION).toInt()
        var matchedCount = 0

        for ((index, p) in samplePoints.withIndex()) {
            val remainingSamples = totalSamples - index - 1
            if (matchedCount + remainingSamples + 1 < requiredMatches) {
                // Early exit: impossible to reach required match fraction
                return false
            }

            val pLat = p.latitude
            val pLng = p.longitude
            val cosLat = cos(Math.toRadians(pLat)).coerceAtLeast(0.01)
            val deltaLat = DEFAULT_CORRIDOR_TOLERANCE_METERS / 111_000.0
            val deltaLng = DEFAULT_CORRIDOR_TOLERANCE_METERS / (111_000.0 * cosLat)
            val pMinLat = pLat - deltaLat
            val pMaxLat = pLat + deltaLat
            val pMinLng = pLng - deltaLng
            val pMaxLng = pLng + deltaLng

            var matched = false
            for (i in 0 until longerPoints.size - 1) {
                val a = longerPoints[i]
                val b = longerPoints[i + 1]

                val segMinLat = minOf(a.latitude, b.latitude)
                val segMaxLat = maxOf(a.latitude, b.latitude)
                if (pMinLat > segMaxLat || pMaxLat < segMinLat) continue

                val segMinLng = minOf(a.longitude, b.longitude)
                val segMaxLng = maxOf(a.longitude, b.longitude)
                if (pMinLng > segMaxLng || pMaxLng < segMinLng) continue

                val d = distanceToSegmentMeters(p, a, b)
                if (d <= DEFAULT_CORRIDOR_TOLERANCE_METERS) {
                    matched = true
                    break
                }
            }

            if (matched) {
                matchedCount++
                if (matchedCount >= requiredMatches) {
                    return true
                }
            }
        }

        return matchedCount >= requiredMatches
    }

    /**
     * Filters a list of climbs for presentation / map rendering, ensuring that only non-overlapping
     * canonical climbs are displayed (REQ-CLM-011.3).
     */
    fun deduplicateForDisplay(
        climbs: List<Climb>,
        thresholdMeters: Float = DEFAULT_ENDPOINT_THRESHOLD_METERS
    ): List<Climb> {
        if (climbs.size < 2) return climbs
        val result = mutableListOf<Climb>()
        for (climb in climbs) {
            if (result.none { isDuplicateOrOverlapping(climb, it, thresholdMeters) }) {
                result.add(climb)
            }
        }
        return result
    }
}
