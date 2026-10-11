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

package com.atrainingtracker.trainingtracker.geo

import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.google.maps.android.SphericalUtil
import kotlin.math.abs
import kotlin.math.sign

/**
 * Directional virtual gate model representing a virtual start or finish gate line perpendicular
 * to a polyline trajectory (REQ-GEO-001).
 *
 * @param gateStart One endpoint of the perpendicular virtual line
 * @param gateEnd The other endpoint of the perpendicular virtual line
 * @param referencePoint Reference forward/backward track point used to determine directional crossing side
 * @param referenceCrossProduct Pre-calculated cross-product of [referencePoint] to gate line [[gateStart], [gateEnd]]
 * @param gateThresholdMeters Proximity threshold within which gate crossings are evaluated
 * @param bearing Geographic forward heading of the track at the gate in degrees (0..360)
 */
data class PolylineGate(
    val gateStart: LatLng,
    val gateEnd: LatLng,
    val referencePoint: LatLng,
    val referenceCrossProduct: Double,
    val gateThresholdMeters: Double = 50.0,
    val bearing: Double
)

/**
 * Result of orthogonal coordinate projection onto a polyline trajectory (REQ-GEO-001).
 *
 * @param projectedLatLng Closest coordinate on the polyline segment
 * @param segmentIndex Index of the polyline segment [i] where projection fell
 * @param t Linear interpolation parameter along the segment, clamped to [0.0, 1.0]
 * @param alongTrackDistance Cumulative distance along the polyline from track start to [projectedLatLng] in meters
 * @param crossTrackDistanceMeters Geodesic perpendicular distance from user location to [projectedLatLng] in meters
 * @param progressFraction Normalized progress along the polyline in [0.0f, 1.0f]
 * @param interpolatedAltitude Linear interpolated altitude at the projected point in meters, if available
 * @param instantaneousGradePercent Instantaneous grade of the active segment as a percentage, if available
 */
data class PolylineProjection(
    val projectedLatLng: LatLng,
    val segmentIndex: Int,
    val t: Double,
    val alongTrackDistance: Double,
    val crossTrackDistanceMeters: Double,
    val progressFraction: Float,
    val interpolatedAltitude: Double?,
    val instantaneousGradePercent: Double?
)

/**
 * High-level state lifecycle shared across polyline tracking domains (Strava Live Segments & Live Climbs).
 */
enum class PolylineTrackingState {
    FAR_FAR_AWAY,
    APPROACHING,
    ON_TRACK,
    FINISHED
}

/**
 * Unified mathematical tracking engine for polyline progress tracking (REQ-GEO-001).
 *
 * Combines directional virtual gates (2D vector cross-product sign flips), orthogonal segment projection,
 * 3D altitude/grade interpolation, smooth finish remaining distance blending, and approach heading filtering.
 * Decoupled from Android UI/database layers to enable pure JVM execution.
 */
object PolylineProgressTracker {

    /**
     * Computes the 2D vector cross-product (z-component) of vector AB and AC in latitude/longitude coordinates:
     * cross(A, B, C) = (B.lat - A.lat) * (C.lng - A.lng) - (B.lng - A.lng) * (C.lat - A.lat)
     *
     * A sign change in this product indicates crossing the line AB.
     */
    fun crossProduct(a: LatLng, b: LatLng, c: LatLng): Double {
        return (b.latitude - a.latitude) * (c.longitude - a.longitude) -
                (b.longitude - a.longitude) * (c.latitude - a.latitude)
    }

    /**
     * Calculates perpendicular virtual gates (start or finish lines) for a polyline path.
     *
     * @param points Ordered list of [PathPoint] defining the track.
     * @param isStartGate True to construct start gate at path start; false for finish gate at path end.
     * @param gateThresholdMeters Proximity threshold in meters for crossing detection.
     * @param sampleOffset Number of points forward/backward from pivot to derive heading vector.
     */
    fun createGate(
        points: List<PathPoint>,
        isStartGate: Boolean,
        gateThresholdMeters: Double = 50.0,
        sampleOffset: Int = 5
    ): PolylineGate? {
        if (points.size < 2) return null
        val effectiveOffset = sampleOffset.coerceIn(1, points.size - 1)

        return if (isStartGate) {
            val start = points.first().latLng
            val next = points[effectiveOffset].latLng
            val startA = LatLng(start.latitude + (start.longitude - next.longitude), start.longitude - (start.latitude - next.latitude))
            val startB = LatLng(start.latitude - (start.longitude - next.longitude), start.longitude + (start.latitude - next.latitude))
            val bearing = SphericalUtil.computeHeading(start, next)
            val startCrossN = crossProduct(startA, startB, next)
            PolylineGate(
                gateStart = startA,
                gateEnd = startB,
                referencePoint = next,
                referenceCrossProduct = startCrossN,
                gateThresholdMeters = gateThresholdMeters,
                bearing = bearing
            )
        } else {
            val end = points.last().latLng
            val prevToEnd = points[points.size - 1 - effectiveOffset].latLng
            val endA = LatLng(end.latitude + (end.longitude - prevToEnd.longitude), end.longitude - (end.latitude - prevToEnd.latitude))
            val endB = LatLng(end.latitude - (end.longitude - prevToEnd.longitude), end.longitude + (end.latitude - prevToEnd.latitude))
            val bearing = SphericalUtil.computeHeading(prevToEnd, end)
            val endCrossP = crossProduct(endA, endB, prevToEnd)
            PolylineGate(
                gateStart = endA,
                gateEnd = endB,
                referencePoint = prevToEnd,
                referenceCrossProduct = endCrossP,
                gateThresholdMeters = gateThresholdMeters,
                bearing = bearing
            )
        }
    }

    /**
     * Evaluates whether an athlete crossed a virtual gate in the designated direction.
     *
     * A crossing occurs if:
     * 1. The distance to the gate is within [PolylineGate.gateThresholdMeters].
     * 2. The cross-product sign flipped relative to [prevCrossProduct].
     * 3. For start gate: [currCrossProduct] matches [gate.referenceCrossProduct] sign (moving forward into track).
     *    For finish gate: [currCrossProduct] differs from [gate.referenceCrossProduct] sign (moving past finish).
     */
    fun checkGateCrossing(
        gate: PolylineGate,
        prevCrossProduct: Double,
        currCrossProduct: Double,
        distanceToGate: Double,
        isStartGate: Boolean
    ): Boolean {
        if (distanceToGate > gate.gateThresholdMeters) return false
        val signFlipped = prevCrossProduct.sign != currCrossProduct.sign && prevCrossProduct != 0.0
        return if (isStartGate) {
            signFlipped && currCrossProduct.sign == gate.referenceCrossProduct.sign
        } else {
            signFlipped && currCrossProduct.sign != gate.referenceCrossProduct.sign
        }
    }

    /**
     * Computes the perpendicular geodesic distance from a location to a virtual gate line.
     */
    fun distanceToGate(location: LatLng, gate: PolylineGate): Double {
        return PolyUtil.distanceToLine(location, gate.gateStart, gate.gateEnd)
    }

    /**
     * Projects a coordinate orthogonally onto the nearest segment of a polyline track,
     * calculating completed along-track distance, cross-track error, progress fraction, altitude, and grade.
     */
    fun projectLocation(
        path: List<PathPoint>,
        location: LatLng
    ): PolylineProjection? {
        if (path.isEmpty()) return null
        if (path.size == 1) {
            val p0 = path.first()
            val d = SphericalUtil.computeDistanceBetween(location, p0.latLng)
            return PolylineProjection(
                projectedLatLng = p0.latLng,
                segmentIndex = 0,
                t = 0.0,
                alongTrackDistance = 0.0,
                crossTrackDistanceMeters = d,
                progressFraction = 0f,
                interpolatedAltitude = p0.altitude,
                instantaneousGradePercent = p0.slope
            )
        }

        var bestSegIdx = 0
        var bestT = 0.0
        var minCrossTrackDist = Double.MAX_VALUE
        var bestProjectedLatLng = path.first().latLng

        for (i in 0 until path.size - 1) {
            val p1 = path[i]
            val p2 = path[i + 1]
            val dLat = p2.latLng.latitude - p1.latLng.latitude
            val dLng = p2.latLng.longitude - p1.latLng.longitude
            val segLenSq = dLat * dLat + dLng * dLng

            val t = if (segLenSq > 0.0) {
                val u = ((location.latitude - p1.latLng.latitude) * dLat +
                        (location.longitude - p1.latLng.longitude) * dLng) / segLenSq
                u.coerceIn(0.0, 1.0)
            } else {
                0.0
            }

            val projLat = p1.latLng.latitude + t * dLat
            val projLng = p1.latLng.longitude + t * dLng
            val projPoint = LatLng(projLat, projLng)

            val d = SphericalUtil.computeDistanceBetween(location, projPoint)
            if (d < minCrossTrackDist) {
                minCrossTrackDist = d
                bestSegIdx = i
                bestT = t
                bestProjectedLatLng = projPoint
            }
        }

        val p1 = path[bestSegIdx]
        val p2 = path[bestSegIdx + 1]
        val segDist = p2.distance - p1.distance
        val segAlt = p2.altitude - p1.altitude

        val totalPathDist = (path.last().distance - path.first().distance).coerceAtLeast(0.0)
        val alongTrackDist = ((p1.distance - path.first().distance) + bestT * segDist).coerceAtLeast(0.0)
        val progressFraction = if (totalPathDist > 0.0) {
            (alongTrackDist / totalPathDist).coerceIn(0.0, 1.0).toFloat()
        } else {
            0f
        }

        val interpolatedAlt = p1.altitude + bestT * segAlt
        val grade = if (segDist > 0.0) {
            (segAlt / segDist) * 100.0
        } else {
            p1.slope
        }

        return PolylineProjection(
            projectedLatLng = bestProjectedLatLng,
            segmentIndex = bestSegIdx,
            t = bestT,
            alongTrackDistance = alongTrackDist,
            crossTrackDistanceMeters = minCrossTrackDist,
            progressFraction = progressFraction,
            interpolatedAltitude = interpolatedAlt,
            instantaneousGradePercent = grade
        )
    }

    /**
     * Blends along-track remaining distance with direct geodesic line distance to the finish gate
     * when within [finishThresholdMeters], guaranteeing a smooth convergence to 0.0m without discrete jumps.
     *
     * lambda = min(1.0, distanceToEnd / finishThresholdMeters)
     * remainingDistance = (totalDistance - completedDistance) * lambda + (1.0 - lambda) * distanceToEnd
     */
    fun calculateBlendedRemainingDistance(
        totalDistance: Double,
        completedDistance: Double,
        distanceToEnd: Double,
        finishThresholdMeters: Double = 500.0
    ): Double {
        val rawRemaining = (totalDistance - completedDistance).coerceAtLeast(0.0)
        if (finishThresholdMeters <= 0.0 || distanceToEnd > finishThresholdMeters) {
            return rawRemaining
        }
        val lambda = (distanceToEnd / finishThresholdMeters).coerceIn(0.0, 1.0)
        return (rawRemaining * lambda + (1.0 - lambda) * distanceToEnd).coerceAtLeast(0.0)
    }

    /**
     * Calculates the minimal angular difference between two bearings in degrees (0..180).
     */
    fun getBearingDifference(b1: Double, b2: Double): Double {
        val diff = abs(b1 - b2) % 360
        return if (diff > 180) 360 - diff else diff
    }

    /**
     * Evaluates whether [userBearing] is aligned with [targetBearing] within [toleranceDegrees] (default 45°).
     */
    fun isHeadingAligned(userBearing: Double, targetBearing: Double, toleranceDegrees: Double = 45.0): Boolean {
        return getBearingDifference(userBearing, targetBearing) <= toleranceDegrees
    }
}
