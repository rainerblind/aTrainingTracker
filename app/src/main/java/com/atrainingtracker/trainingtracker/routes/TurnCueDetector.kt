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

package com.atrainingtracker.trainingtracker.routes

import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Configuration parameters for geometric turn cue synthesis (REQ-MAP-028 / ATT-3494).
 */
data class TurnCueDetectorConfig(
    val shortWindowMeters: Double = 10.0,
    val longWindowMeters: Double = 40.0,
    val smoothingRadiusMeters: Double = 2.5,
    val minSeparationMeters: Double = 30.0,
    val minChicaneSeparationMeters: Double = 15.0,
    val startDepartureWindowMeters: Double = 10.0,
    val finishWindowMeters: Double = 10.0,
    val minRouteLengthMeters: Double = 20.0,
    val minPointsCount: Int = 3,
    val thresholdSlightDeg: Float = 25.0f,
    val thresholdRegularDeg: Float = 60.0f,
    val thresholdSharpDeg: Float = 120.0f,
    val thresholdUTurnDeg: Float = 155.0f,
    val hysteresisBufferDeg: Float = 5.0f,
    val apexMinSpatialShiftMeters: Double = 1.0,
    val spikeMaxLegMeters: Double = 5.0,
    val spikeDeflectionThresholdDeg: Float = 165.0f
)

/**
 * Intelligent detector and extractor of turn cues along a route (REQ-MAP-028 / ATT-1450, ATT-3494).
 *
 * Supports two sources:
 * 1. Explicit course points and waypoints stored with [WaypointCategory.TURN_CUE].
 * 2. Geometric polyline analysis synthesizing turn cues from curvature angles with
 *    multi-scale forward sliding windows, distance-weighted circular vector smoothing,
 *    and junction-aware apex conflict resolution.
 */
object TurnCueDetector {

    /**
     * Extracts existing turn waypoints from [route] or synthesizes them from [route.path] geometry.
     */
    fun extractOrDetectCues(
        route: RouteWithPath,
        config: TurnCueDetectorConfig = TurnCueDetectorConfig()
    ): List<TurnCue> {
        val turnWaypoints = route.waypoints.filter { it.type.category == WaypointCategory.TURN_CUE }
        if (turnWaypoints.isNotEmpty()) {
            return turnWaypoints.mapNotNull { wpt ->
                val dir = TurnDirection.fromWaypointType(wpt.type) ?: return@mapNotNull null
                TurnCue(
                    id = wpt.id,
                    latLng = wpt.latLng,
                    distanceFromStart = wpt.distanceFromStart,
                    direction = dir,
                    instruction = wpt.description.ifEmpty { wpt.name },
                    wayName = wpt.name
                )
            }.sortedBy { it.distanceFromStart }
        }

        return detectGeometricCues(route.path, config)
    }

    /**
     * Synthesizes turn cues directly from a list of geographic [LatLng] coordinates.
     * Pure, stateless, thread-safe, and re-entrant.
     */
    fun detectGeometricCuesFromLatLng(
        coordinates: List<LatLng>,
        config: TurnCueDetectorConfig = TurnCueDetectorConfig()
    ): List<TurnCue> {
        if (coordinates.size < config.minPointsCount) return emptyList()
        var cumDist = 0.0
        val path = ArrayList<PathPoint>(coordinates.size)
        path.add(PathPoint(distance = 0.0, latLng = coordinates[0], altitude = 0.0))
        for (i in 1 until coordinates.size) {
            val p1 = coordinates[i - 1]
            val p2 = coordinates[i]
            val d = RouteAutoDetector.computeDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude).toDouble()
            cumDist += d
            path.add(PathPoint(distance = cumDist, latLng = p2, altitude = 0.0))
        }
        return detectGeometricCues(path, config)
    }

    /**
     * Synthesizes turn cues by analyzing trackpoint heading changes across multi-scale forward sliding windows.
     * Pure, stateless, thread-safe, and re-entrant.
     */
    fun detectGeometricCues(
        path: List<PathPoint>,
        config: TurnCueDetectorConfig = TurnCueDetectorConfig()
    ): List<TurnCue> {
        if (path.size < config.minPointsCount) return emptyList()

        // 1. Pre-filtering: Duplicate point elimination (delta s < 10^-4 m)
        val deduplicated = ArrayList<PathPoint>(path.size)
        deduplicated.add(path[0])
        for (i in 1 until path.size) {
            val prev = deduplicated.last()
            val curr = path[i]
            val d = RouteAutoDetector.computeDistanceMeters(
                prev.latLng.latitude, prev.latLng.longitude,
                curr.latLng.latitude, curr.latLng.longitude
            )
            if (d >= 1e-4f) {
                deduplicated.add(curr)
            }
        }
        if (deduplicated.size < config.minPointsCount) return emptyList()

        // 2. Pre-filtering: Single-pass multipath rebound spike rejection
        val sanitized = ArrayList<PathPoint>(deduplicated.size)
        sanitized.add(deduplicated[0])
        var sIdx = 1
        while (sIdx < deduplicated.size - 1) {
            val prev = sanitized.last()
            val curr = deduplicated[sIdx]
            val next = deduplicated[sIdx + 1]

            val d1 = RouteAutoDetector.computeDistanceMeters(
                prev.latLng.latitude, prev.latLng.longitude,
                curr.latLng.latitude, curr.latLng.longitude
            )
            val d2 = RouteAutoDetector.computeDistanceMeters(
                curr.latLng.latitude, curr.latLng.longitude,
                next.latLng.latitude, next.latLng.longitude
            )

            if (d1 < config.spikeMaxLegMeters && d2 < config.spikeMaxLegMeters) {
                val bIn = RouteAutoDetector.computeBearing(prev.latLng, curr.latLng)
                val bOut = RouteAutoDetector.computeBearing(curr.latLng, next.latLng)
                val spikeDelta = abs(computeSignedAngleDelta(bIn, bOut))
                if (spikeDelta >= config.spikeDeflectionThresholdDeg) {
                    val bCorridor = RouteAutoDetector.computeBearing(prev.latLng, next.latLng)
                    val corridorDelta = abs(computeSignedAngleDelta(bIn, bCorridor))
                    if (corridorDelta <= 15.0f) {
                        // Multipath spike detected, omit curr
                        sIdx++
                        continue
                    }
                }
            }
            sanitized.add(curr)
            sIdx++
        }
        if (deduplicated.size > 1) {
            sanitized.add(deduplicated.last())
        }
        if (sanitized.size < config.minPointsCount) return emptyList()

        // 3. Pre-allocated flat primitive buffers & spherical geodesics
        val m = sanitized.size
        val distances = DoubleArray(m)
        val segmentLengths = DoubleArray(m - 1)
        val rawBearings = FloatArray(m - 1)
        val smoothedBearings = FloatArray(m - 1)

        distances[0] = 0.0
        for (k in 0 until m - 1) {
            val p1 = sanitized[k].latLng
            val p2 = sanitized[k + 1].latLng
            val segLen = RouteAutoDetector.computeDistanceMeters(
                p1.latitude, p1.longitude,
                p2.latitude, p2.longitude
            ).toDouble()
            segmentLengths[k] = segLen
            distances[k + 1] = distances[k] + segLen
            rawBearings[k] = RouteAutoDetector.computeBearing(p1, p2)
        }

        val totalDistance = distances[m - 1]
        if (totalDistance < config.minRouteLengthMeters) {
            return emptyList()
        }

        // 4. Distance-weighted spatial circular vector mean smoothing over envelope
        for (k in 0 until m - 1) {
            val sMid = (distances[k] + distances[k + 1]) / 2.0
            val sMin = sMid - config.smoothingRadiusMeters
            val sMax = sMid + config.smoothingRadiusMeters

            var sumX = 0.0
            var sumY = 0.0
            var totalW = 0.0

            var j = k
            while (j >= 0 && distances[j + 1] >= sMin) {
                val segStart = distances[j]
                val segEnd = distances[j + 1]
                val overlap = maxOf(0.0, minOf(segEnd, sMax) - maxOf(segStart, sMin))
                if (overlap > 0.0) {
                    val rad = Math.toRadians(rawBearings[j].toDouble())
                    sumX += overlap * cos(rad)
                    sumY += overlap * sin(rad)
                    totalW += overlap
                }
                j--
            }

            j = k + 1
            while (j < m - 1 && distances[j] <= sMax) {
                val segStart = distances[j]
                val segEnd = distances[j + 1]
                val overlap = maxOf(0.0, minOf(segEnd, sMax) - maxOf(segStart, sMin))
                if (overlap > 0.0) {
                    val rad = Math.toRadians(rawBearings[j].toDouble())
                    sumX += overlap * cos(rad)
                    sumY += overlap * sin(rad)
                    totalW += overlap
                }
                j++
            }

            if (totalW <= 1e-4) {
                smoothedBearings[k] = rawBearings[k]
            } else {
                var deg = Math.toDegrees(Math.atan2(sumY, sumX)).toFloat()
                deg = (deg % 360f + 360f) % 360f
                smoothedBearings[k] = deg
            }
        }

        // Helper: distance-weighted circular mean of smoothed bearings over segment index range
        fun calcWindowBearing(fromIdx: Int, toIdx: Int): Float {
            if (fromIdx > toIdx) return 0f
            if (fromIdx == toIdx) return smoothedBearings[fromIdx]
            var sumX = 0.0
            var sumY = 0.0
            var totalW = 0.0
            for (idx in fromIdx..toIdx) {
                val w = segmentLengths[idx]
                val rad = Math.toRadians(smoothedBearings[idx].toDouble())
                sumX += w * cos(rad)
                sumY += w * sin(rad)
                totalW += w
            }
            if (totalW <= 1e-4) return smoothedBearings[fromIdx]
            var deg = Math.toDegrees(Math.atan2(sumY, sumX)).toFloat()
            return (deg % 360f + 360f) % 360f
        }

        // 5. Multi-scale lookaround chords with monotonic pointers
        val deltaArray = FloatArray(m)
        val inBearings = FloatArray(m)

        var prevShort = 0
        var nextShort = 0
        var prevLong = 0
        var nextLong = 0

        for (i in 1 until m - 1) {
            val dCur = distances[i]

            // Advance short window pointers
            while (prevShort < i - 1 && (dCur - distances[prevShort + 1]) >= config.shortWindowMeters) {
                prevShort++
            }
            if (nextShort < i) nextShort = i
            while (nextShort < m - 1 && (distances[nextShort] - dCur) < config.shortWindowMeters) {
                nextShort++
            }

            // Advance long window pointers
            while (prevLong < i - 1 && (dCur - distances[prevLong + 1]) >= config.longWindowMeters) {
                prevLong++
            }
            if (nextLong < i) nextLong = i
            while (nextLong < m - 1 && (distances[nextLong] - dCur) < config.longWindowMeters) {
                nextLong++
            }

            val isDepartureZone = dCur <= config.startDepartureWindowMeters
            val bInShort = if (prevShort >= i || isDepartureZone) rawBearings[maxOf(0, i - 1)] else RouteAutoDetector.computeBearing(sanitized[prevShort].latLng, sanitized[i].latLng)
            val bOutShort = if (nextShort <= i) rawBearings[minOf(m - 2, i)] else RouteAutoDetector.computeBearing(sanitized[i].latLng, sanitized[nextShort].latLng)
            inBearings[i] = bInShort
            val deltaShort = computeSignedAngleDelta(bInShort, bOutShort)

            val bInLong = if (prevLong >= i || isDepartureZone) rawBearings[maxOf(0, i - 1)] else RouteAutoDetector.computeBearing(sanitized[prevLong].latLng, sanitized[i].latLng)
            val bOutLong = if (nextLong <= i) rawBearings[minOf(m - 2, i)] else RouteAutoDetector.computeBearing(sanitized[i].latLng, sanitized[nextLong].latLng)
            val deltaLong = computeSignedAngleDelta(bInLong, bOutLong)

            // Multi-scale selection: Local corners take precedence, long window for sweeping curves
            deltaArray[i] = when {
                isDepartureZone -> if (abs(deltaShort) >= config.thresholdRegularDeg) deltaShort else 0.0f
                abs(deltaLong) >= config.thresholdUTurnDeg && abs(deltaShort) >= 70.0f -> deltaLong
                abs(deltaShort) >= 40.0f -> deltaShort
                abs(deltaLong) >= 50.0f && abs(deltaShort) >= 15.0f -> deltaLong
                abs(deltaShort) >= config.thresholdSlightDeg -> deltaShort
                else -> 0.0f
            }
        }

        // 6. Contiguous cluster apex localization & conflict resolution
        val cues = ArrayList<TurnCue>()
        var lastDelta = 0.0f
        val finishLimit = totalDistance - config.finishWindowMeters
        var i = 1

        while (i < m - 1 && distances[i] <= finishLimit) {
            val d0 = deltaArray[i]
            if (abs(d0) >= config.thresholdSlightDeg) {
                val maneuverSign = if (d0 >= 0f) 1 else -1

                var clusterStart = i
                var clusterEnd = i
                var maxDelta = d0

                var scan = i + 1
                while (scan < m - 1 && distances[scan] <= finishLimit) {
                    val dScan = deltaArray[scan]
                    if (abs(dScan) >= config.thresholdSlightDeg) {
                        val sSign = if (dScan >= 0f) 1 else -1
                        if (sSign == maneuverSign) {
                            clusterEnd = scan
                            if (abs(dScan) > abs(maxDelta)) {
                                maxDelta = dScan
                            }
                            scan++
                            continue
                        }
                    }
                    break
                }

                // Locate apex vertex with peak delta & midpoint tie-breaking
                var peakStart = clusterStart
                var peakEnd = clusterStart
                val peakAbs = abs(maxDelta)

                for (cIdx in clusterStart..clusterEnd) {
                    val absD = abs(deltaArray[cIdx])
                    if (absD >= peakAbs - 0.5f) {
                        if (peakStart == clusterStart && cIdx > clusterStart && abs(deltaArray[clusterStart]) < peakAbs - 0.5f) {
                            peakStart = cIdx
                        }
                        peakEnd = cIdx
                    }
                }

                val iApex = peakStart + (peakEnd - peakStart) / 2

                // Departure window clamping: turns within [0, 10m] are pinned to 0.0m
                val isDeparture = distances[iApex] <= config.startDepartureWindowMeters && abs(maxDelta) >= 60.0f

                // Perpendicular displacement jitter guard (filters lateral GPS jitter <= 3.5m)
                if (!isDeparture) {
                    var prevIdx = iApex
                    while (prevIdx > 0 && distances[iApex] - distances[prevIdx] < 20.0) {
                        prevIdx--
                    }
                    var nextIdx = iApex
                    while (nextIdx < m - 1 && distances[nextIdx] - distances[iApex] < 20.0) {
                        nextIdx++
                    }
                    val h = RouteAutoDetector.distanceToSegmentMeters(
                        sanitized[iApex].latLng.latitude, sanitized[iApex].latLng.longitude,
                        sanitized[prevIdx].latLng, sanitized[nextIdx].latLng
                    )
                    if (h < 3.5f) {
                        i = clusterEnd + 1
                        continue
                    }
                }

                val candidateDist = if (isDeparture) 0.0 else distances[iApex]
                val candidateLatLng = if (isDeparture) sanitized.first().latLng else sanitized[iApex].latLng

                val candidateDirection = classifyTurn(maxDelta, config)
                val candidateCue = TurnCue(
                    id = iApex.toLong(),
                    latLng = candidateLatLng,
                    distanceFromStart = candidateDist,
                    direction = candidateDirection
                )

                // Junction-aware conflict resolution
                if (cues.isNotEmpty()) {
                    val lastCue = cues.last()
                    val distDiff = candidateDist - lastCue.distanceFromStart

                    if (distDiff < config.minSeparationMeters - 1.0) {
                        val isOppositeDirection = (maxDelta * lastDelta < 0.0f)
                        if (isOppositeDirection && distDiff >= config.minChicaneSeparationMeters) {
                            cues.add(candidateCue)
                            lastDelta = maxDelta
                        } else if (!isOppositeDirection && abs(maxDelta) >= abs(lastDelta) + config.hysteresisBufferDeg) {
                            val targetDist = if (abs(candidateDist - lastCue.distanceFromStart) >= config.apexMinSpatialShiftMeters) candidateDist else lastCue.distanceFromStart
                            val targetLatLng = if (abs(candidateDist - lastCue.distanceFromStart) >= config.apexMinSpatialShiftMeters) candidateLatLng else lastCue.latLng
                            cues[cues.size - 1] = candidateCue.copy(distanceFromStart = targetDist, latLng = targetLatLng)
                            lastDelta = maxDelta
                        }
                    } else {
                        cues.add(candidateCue)
                        lastDelta = maxDelta
                    }
                } else {
                    cues.add(candidateCue)
                    lastDelta = maxDelta
                }

                i = clusterEnd + 1
            } else {
                i++
            }
        }

        return cues
    }

    /**
     * Backward-compatible overload for legacy callers.
     */
    fun detectGeometricCues(
        path: List<PathPoint>,
        minAngle: Float,
        minSeparationMeters: Double = 30.0
    ): List<TurnCue> = detectGeometricCues(
        path,
        TurnCueDetectorConfig(
            thresholdSlightDeg = minAngle,
            minSeparationMeters = minSeparationMeters
        )
    )

    /**
     * Calculates the signed difference in heading between two bearings:
     * - Positive value: Clockwise / Turn Right.
     * - Negative value: Counter-clockwise / Turn Left.
     */
    fun computeSignedAngleDelta(bearingFrom: Float, bearingTo: Float): Float {
        var diff = (bearingTo - bearingFrom) % 360f
        if (diff > 180f) diff -= 360f
        if (diff <= -180f) diff += 360f
        return diff
    }

    /**
     * Classifies a signed angular heading change into a [TurnDirection].
     */
    fun classifyTurn(
        deltaDegrees: Float,
        config: TurnCueDetectorConfig = TurnCueDetectorConfig()
    ): TurnDirection {
        val absDelta = abs(deltaDegrees)
        return when {
            absDelta >= config.thresholdUTurnDeg -> TurnDirection.U_TURN
            deltaDegrees > 0f -> when {
                absDelta < config.thresholdRegularDeg -> TurnDirection.SLIGHT_RIGHT
                absDelta < config.thresholdSharpDeg -> TurnDirection.RIGHT
                else -> TurnDirection.SHARP_RIGHT
            }
            else -> when {
                absDelta < config.thresholdRegularDeg -> TurnDirection.SLIGHT_LEFT
                absDelta < config.thresholdSharpDeg -> TurnDirection.LEFT
                else -> TurnDirection.SHARP_LEFT
            }
        }
    }
}
