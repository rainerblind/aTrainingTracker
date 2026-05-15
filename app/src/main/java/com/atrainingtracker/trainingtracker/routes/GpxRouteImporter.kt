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
import androidx.compose.animation.core.copy
import androidx.compose.foundation.layout.size
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import io.ticofab.androidgpxparser.parser.GPXParser
import io.ticofab.androidgpxparser.parser.domain.Gpx


class GpxRouteImporter(private val context: Context) {

    private val routesRepository = RoutesRepository.getInstance(context)

    /**
     * Parses a GPX file from a Uri and inserts it into the database.
     */
    suspend fun importRouteFromGpx(uri: Uri): Result<Long> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val parser = GPXParser()
                val parsedGpx: Gpx? = parser.parse(inputStream)

                if (parsedGpx == null || (parsedGpx.tracks.isEmpty() && parsedGpx.routes.isEmpty())) {
                    return@withContext Result.failure(Exception("No tracks or routes found"))
                }

                // GPX can have multiple tracks; we'll take the first one or combine them
                // Most GPX files use <trk>, but some older ones use <rte>
                val firstTrack = parsedGpx.tracks.firstOrNull()
                val trackPoints = firstTrack?.trackSegments?.flatMap { it.trackPoints }
                    ?: parsedGpx.routes.firstOrNull()?.routePoints
                    ?: emptyList()

                // Convert library points to PathPoint
                val pathPoints = trackPoints.mapIndexed { index, pt ->
                    PathPoint(
                        latLng = LatLng(pt.latitude, pt.longitude),
                        altitude = pt.elevation ?: 0.0,
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

                val summary = RouteSummary(
                    id = 0,
                    externalId = uri.lastPathSegment ?: "unknown",
                    name = firstTrack?.trackName ?: parsedGpx.metadata?.name ?: "Imported Route",
                    // Extract description from track or global metadata
                    description = firstTrack?.trackDesc ?: parsedGpx.metadata?.desc ?: "",
                    isSelected = false,
                    distance = totalDist,
                    elevationGain = calculateElevationGain(pathPoints),
                    bSportType = BSportType.UNKNOWN,
                    source = RouteSource.LOCAL_GPX
                )

                val id = routesRepository.insertRoute(summary, pathPoints)
                Result.success(id)
            } ?: Result.failure(Exception("Stream null"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun calculateElevationGain(points: List<PathPoint>): Double {
        // TODO: Might be too noisy. -> Implement some filtering.
        var gain = 0.0
        for (i in 1 until points.size) {
            val diff = points[i].altitude - points[i - 1].altitude
            if (diff > 0) gain += diff
        }
        return gain
    }
}