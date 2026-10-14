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
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.elevation.ElevationResult
import com.atrainingtracker.trainingtracker.elevation.ElevationService
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.atrainingtracker.trainingtracker.routes.parsers.GpxTrackpointParser

/**
 * Result data class for route file imports (GPX / TCX).
 */
data class RouteImportResult(
    val summary: RouteSummary,
    val pathPoints: List<PathPoint>,
    val waypoints: List<RouteWaypoint> = emptyList()
)

/**
 * Importer for GPX files with automatic DEM elevation enrichment for routes lacking altitude data,
 * and POI waypoint extraction (REQ-MAP-026).
 *
 * Traceability:
 * - REQ-MAP-025: Automatic DEM Elevation Enrichment for Imported GPX Routes Lacking Altitude Data.
 * - REQ-MAP-026: Support Waypoints, POIs (Benches, Water, Summits) and TCX Course Points.
 * - TST-MAP-027: Automatic DEM Elevation Enrichment for Imported GPX Routes Lacking Altitude Verification.
 * - TST-MAP-028: Waypoint extraction, classification, and visualization verification.
 */
class GpxRouteImporter @JvmOverloads constructor(
    private val context: Context,
    private val elevationService: ElevationService = ElevationService.getInstance()
) {

    companion object {
        private const val TAG = "GpxRouteImporter"
        const val MAX_BATCH_SIZE = 100
    }

    /**
     * Parses a GPX file from a Uri and returns the RouteSummary, PathPoints, and Waypoints.
     * If the imported track lacks elevation data, it automatically enriches coordinates
     * with DEM elevations from Open-Meteo.
     */
    suspend fun importRouteFromGpx(
        uri: Uri,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null
    ): Result<RouteImportResult> = withContext(Dispatchers.IO) {
        try {
            val isTcx = uri.lastPathSegment?.endsWith(".tcx", ignoreCase = true) == true ||
                    uri.toString().endsWith(".tcx", ignoreCase = true)
            if (isTcx) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    return@withContext TcxCourseParser.parse(stream, uri.lastPathSegment ?: "tcx_course")
                } ?: return@withContext Result.failure(Exception("Cannot open stream for TCX"))
            }

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val gpxResult = GpxTrackpointParser.parse(inputStream)
                val parsedGpx = gpxResult.getOrNull()

                val trackPoints = parsedGpx?.laps?.flatMap { it.trackpoints } ?: emptyList()

                if (parsedGpx == null || trackPoints.isEmpty()) {
                    // Fallback attempt to TCX course parser
                    val tcxResult = context.contentResolver.openInputStream(uri)?.use { tcxStream ->
                        TcxCourseParser.parse(tcxStream, uri.lastPathSegment ?: "course")
                    }
                    if (tcxResult != null && tcxResult.isSuccess) {
                        return@withContext tcxResult
                    }
                    return@withContext Result.failure(Exception("No tracks or routes found"))
                }

                // Check whether valid non-zero altitude data is present
                val hasExistingElevation = trackPoints.any { it.altitude != null && it.altitude != 0.0 }

                // Convert points to PathPoint
                val pathPoints = trackPoints.map { pt ->
                    PathPoint(
                        latLng = LatLng(pt.lat, pt.lng),
                        altitude = pt.altitude ?: 0.0,
                        distance = 0.0 // We calculate this below
                    )
                }.toMutableList()

                // Calculate cumulative distance
                var totalDist = 0.0
                for (i in 1 until pathPoints.size) {
                    val p1 = pathPoints[i-1].latLng
                    val p2 = pathPoints[i].latLng
                    val results = FloatArray(1)
                    Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
                    totalDist += results[0]
                    pathPoints[i] = pathPoints[i].copy(distance = totalDist)
                }

                // If no elevation data exists, perform chunked DEM elevation enrichment
                if (!hasExistingElevation && pathPoints.isNotEmpty()) {
                    enrichElevations(pathPoints, onProgress)
                }

                // Parse waypoints (<wpt>) if present (REQ-MAP-026)
                val rawWaypoints = parsedGpx.waypoints.map { wpt ->
                    RouteWaypoint(
                        latLng = LatLng(wpt.lat, wpt.lng),
                        altitude = wpt.altitude ?: 0.0,
                        name = wpt.name,
                        description = wpt.notes ?: "",
                        type = WaypointType.fromGpx(wpt.sym, wpt.type, wpt.name, wpt.notes)
                    )
                }
                val waypoints = WaypointDistanceCalculator.projectWaypoints(rawWaypoints, pathPoints)

                val summary = RouteSummary(
                    id = 0,
                    externalId = uri.lastPathSegment ?: "unknown",
                    name = parsedGpx.trackName ?: parsedGpx.name ?: "Imported Route",
                    // Extract description from track or global metadata
                    description = parsedGpx.trackDesc ?: parsedGpx.description ?: "",
                    isSelected = false,
                    distance = totalDist,
                    elevationGain = calculateElevationGain(pathPoints),
                    bSportType = BSportType.UNKNOWN,
                    source = RouteSource.LOCAL_GPX
                )

                Result.success(RouteImportResult(summary, pathPoints, waypoints))
            } ?: Result.failure(Exception("Stream null"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun enrichElevations(
        pathPoints: MutableList<PathPoint>,
        onProgress: ((completed: Int, total: Int) -> Unit)?
    ) {
        val totalPoints = pathPoints.size
        val chunkIndices = (0 until totalPoints step MAX_BATCH_SIZE)

        for (startIndex in chunkIndices) {
            val endIndex = minOf(startIndex + MAX_BATCH_SIZE, totalPoints)
            val batchCoords = pathPoints.subList(startIndex, endIndex).map { it.latLng }

            onProgress?.invoke(startIndex, totalPoints)

            try {
                when (val result = elevationService.getBatchElevationsAsync(batchCoords)) {
                    is ElevationResult.BatchSuccess -> {
                        val batchElevations = result.elevations
                        for (i in batchElevations.indices) {
                            val elev = batchElevations[i]
                            if (elev != null) {
                                pathPoints[startIndex + i] = pathPoints[startIndex + i].copy(altitude = elev)
                            }
                        }
                    }
                    is ElevationResult.RateLimited -> {
                        Log.w(TAG, "DEM API rate limit hit during route enrichment. Backoff: ${result.retryAfterSeconds}s. Stopping further queries.")
                        break
                    }
                    is ElevationResult.CircuitBreakerOpen -> {
                        Log.w(TAG, "DEM API circuit breaker is open. Skipping remaining enrichment.")
                        break
                    }
                    is ElevationResult.ServerError -> {
                        Log.w(TAG, "DEM API server error (${result.statusCode}). Stopping further queries.")
                        break
                    }
                    is ElevationResult.NetworkError -> {
                        Log.w(TAG, "DEM API network error (${result.message}). Stopping further queries.")
                        break
                    }
                    is ElevationResult.Success -> {
                        pathPoints[startIndex] = pathPoints[startIndex].copy(altitude = result.elevationMeters)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception during elevation batch query: ${e.message}", e)
                break
            }
        }
        onProgress?.invoke(totalPoints, totalPoints)
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