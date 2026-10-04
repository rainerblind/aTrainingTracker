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

/**
 * Intelligent detector and extractor of turn cues along a route (REQ-MAP-028 / ATT-1450).
 *
 * Supports two sources:
 * 1. Explicit course points and waypoints stored with [WaypointCategory.TURN_CUE].
 * 2. Geometric polyline analysis synthesizing turn cues from curvature angles ($\ge 30^\circ$).
 */
object TurnCueDetector {

    private const val MIN_TURN_ANGLE_DEGREES = 30f
    private const val MIN_SEPARATION_METERS = 30.0
    private const val WINDOW_LOOKAROUND_METERS = 15.0

    /**
     * Extracts existing turn waypoints from [route] or synthesizes them from [route.path] geometry.
     */
    fun extractOrDetectCues(route: RouteWithPath): List<TurnCue> {
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

        return detectGeometricCues(route.path)
    }

    /**
     * Synthesizes turn cues by analyzing trackpoint heading changes across windowed polyline chords.
     */
    fun detectGeometricCues(
        path: List<PathPoint>,
        minAngle: Float = MIN_TURN_ANGLE_DEGREES,
        minSeparationMeters: Double = MIN_SEPARATION_METERS
    ): List<TurnCue> {
        if (path.size < 3) return emptyList()

        val cues = mutableListOf<TurnCue>()
        var lastCueDistance = -Double.MAX_VALUE

        for (i in 1 until path.size - 1) {
            val current = path[i]

            // Find previous point at least WINDOW_LOOKAROUND_METERS back
            var prevIdx = i - 1
            while (prevIdx > 0 && (current.distance - path[prevIdx].distance) < WINDOW_LOOKAROUND_METERS) {
                prevIdx--
            }

            // Find next point at least WINDOW_LOOKAROUND_METERS ahead
            var nextIdx = i + 1
            while (nextIdx < path.size - 1 && (path[nextIdx].distance - current.distance) < WINDOW_LOOKAROUND_METERS) {
                nextIdx++
            }

            val pPrev = path[prevIdx]
            val pNext = path[nextIdx]

            val distBefore = current.distance - pPrev.distance
            val distAfter = pNext.distance - current.distance
            if (distBefore < 5.0 || distAfter < 5.0) continue

            val bearingIn = RouteAutoDetector.computeBearing(pPrev.latLng, current.latLng)
            val bearingOut = RouteAutoDetector.computeBearing(current.latLng, pNext.latLng)

            val delta = computeSignedAngleDelta(bearingIn, bearingOut)
            val absDelta = abs(delta)

            if (absDelta >= minAngle) {
                val direction = classifyTurn(delta)
                val distanceFromStart = current.distance

                if (distanceFromStart - lastCueDistance >= minSeparationMeters) {
                    cues.add(
                        TurnCue(
                            id = i.toLong(),
                            latLng = current.latLng,
                            distanceFromStart = distanceFromStart,
                            direction = direction
                        )
                    )
                    lastCueDistance = distanceFromStart
                } else if (cues.isNotEmpty()) {
                    // Update previous cue if current vertex has sharper angle
                    val lastCue = cues.last()
                    if (lastCue.direction == direction && absDelta > 60f) {
                        cues[cues.size - 1] = lastCue.copy(
                            latLng = current.latLng,
                            distanceFromStart = distanceFromStart
                        )
                        lastCueDistance = distanceFromStart
                    }
                }
            }
        }

        return cues
    }

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
    fun classifyTurn(deltaDegrees: Float): TurnDirection {
        val absDelta = abs(deltaDegrees)
        return when {
            absDelta >= 155f -> TurnDirection.U_TURN
            deltaDegrees > 0f -> when {
                absDelta < 60f -> TurnDirection.SLIGHT_RIGHT
                absDelta < 120f -> TurnDirection.RIGHT
                else -> TurnDirection.SHARP_RIGHT
            }
            else -> when {
                absDelta < 60f -> TurnDirection.SLIGHT_LEFT
                absDelta < 120f -> TurnDirection.LEFT
                else -> TurnDirection.SHARP_LEFT
            }
        }
    }
}
