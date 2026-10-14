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

import android.content.Context
import android.location.Location
import android.net.Uri
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.routes.parsers.TcxTrackpointParser
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Streaming parser for Garmin TCX courses (<Courses><Course>) extracting trackpoints,
 * cumulative distance, elevation, and <CoursePoint> elements.
 *
 * Traceability:
 * - REQ-MAP-026: Support Waypoints, POIs (Benches, Water, Summits) and TCX Course Points.
 * - REQ-MIG-037: Unified Streaming TCX and GPX XML Trackpoint Parsing Engine (Rule 36).
 * - TST-MAP-028: TCX course and course point extraction verification.
 * - TST-MIG-034: Unified Streaming TCX and GPX XML Trackpoint Parsing Engine Verification.
 */
object TcxCourseParser {

    /**
     * Parses a TCX Course from an [InputStream] using canonical [TcxTrackpointParser].
     */
    fun parse(inputStream: InputStream, defaultExternalId: String = "tcx_course"): Result<RouteImportResult> {
        return try {
            val parseResult = TcxTrackpointParser.parse(inputStream)
            if (parseResult.isFailure) {
                return Result.failure(parseResult.exceptionOrNull() ?: IllegalArgumentException("Failed to parse TCX course"))
            }
            val data = parseResult.getOrThrow()
            val rawPoints = data.laps.flatMap { it.trackpoints }

            if (rawPoints.isEmpty()) {
                return Result.failure(IllegalArgumentException("No valid trackpoints found in TCX course"))
            }

            val trackPoints = rawPoints.map { pt ->
                PathPoint(
                    latLng = LatLng(pt.lat, pt.lng),
                    altitude = pt.altitude ?: 0.0,
                    distance = pt.distanceMeters ?: 0.0
                )
            }.toMutableList()

            val rawCoursePoints = data.coursePoints.map { cp ->
                RouteWaypoint(
                    latLng = LatLng(cp.lat, cp.lng),
                    altitude = cp.altitude ?: 0.0,
                    name = cp.name,
                    description = cp.notes ?: "",
                    type = WaypointType.fromTcx(cp.type)
                )
            }

            // Calculate / normalize cumulative distances if missing or zero
            val needsDistanceCalc = trackPoints.size > 1 && trackPoints.last().distance <= 0.0
            if (needsDistanceCalc) {
                var totalDist = 0.0
                for (i in 1 until trackPoints.size) {
                    val p1 = trackPoints[i - 1].latLng
                    val p2 = trackPoints[i].latLng
                    val res = FloatArray(1)
                    Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, res)
                    totalDist += res[0]
                    trackPoints[i] = trackPoints[i].copy(distance = totalDist)
                }
            }

            // Project waypoints along track polyline
            val projectedWaypoints = WaypointDistanceCalculator.projectWaypoints(rawCoursePoints, trackPoints)

            val courseName = data.name ?: ""
            val summary = RouteSummary(
                id = 0,
                externalId = defaultExternalId,
                name = if (courseName.isNotBlank()) courseName else "TCX Course",
                description = data.notes ?: "",
                isSelected = false,
                distance = trackPoints.last().distance,
                elevationGain = calculateElevationGain(trackPoints),
                bSportType = BSportType.UNKNOWN,
                source = RouteSource.LOCAL_GPX
            )

            Result.success(RouteImportResult(summary, trackPoints, projectedWaypoints))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parses TCX XML content from a [String].
     */
    fun parse(xmlContent: String, defaultExternalId: String = "tcx_course"): Result<RouteImportResult> {
        return parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)), defaultExternalId)
    }

    /**
     * Parses TCX course from a content [Uri].
     */
    fun parse(context: Context, uri: Uri): Result<RouteImportResult> {
        return try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(IllegalArgumentException("Unable to open input stream for URI: "))
            stream.use { parse(it, uri.lastPathSegment ?: "tcx_course") }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun calculateElevationGain(points: List<PathPoint>): Double {
        var gain = 0.0
        for (i in 1 until points.size) {
            val diff = points[i].altitude - points[i - 1].altitude
            if (diff > 0) gain += diff
        }
        return gain
    }
}
