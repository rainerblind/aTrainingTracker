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
import android.net.Uri
import android.util.Xml
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

class GpxRouteImporter(private val context: Context) {

    private val routesRepository = RoutesRepository.getInstance(context)

    /**
     * Parses a GPX file from a Uri and inserts it into the database.
     */
    suspend fun importRouteFromGpx(uri: Uri, sportType: BSportType = BSportType.UNKNOWN): Result<Long> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val (name, points) = parseGpx(inputStream)

                if (points.isEmpty()) {
                    return@withContext Result.failure(Exception("No points found in GPX file"))
                }

                val totalDistance = points.last().distance
                val elevationGain = calculateElevationGain(points)

                val summary = RouteSummary(
                    id = 0, // DB will generate this
                    externalId = uri.lastPathSegment ?: "unknown_file",
                    name = name ?: uri.lastPathSegment ?: "Imported Route",
                    distance = totalDistance,
                    elevationGain = elevationGain,
                    sportType = sportType,
                    source = RouteSource.LOCAL_GPX
                )

                val id = routesRepository.insertRoute(summary, points)
                Result.success(id)
            } ?: Result.failure(Exception("Could not open file"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseGpx(inputStream: InputStream): Pair<String?, List<PathPoint>> {
        val points = mutableListOf<PathPoint>()
        var routeName: String? = null
        var cumulativeDistance = 0.0
        var lastLatLng: LatLng? = null

        val parser = Xml.newPullParser()
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tagName) {
                        "name" -> if (routeName == null) routeName = parser.nextText()
                        "trkpt", "rtept" -> {
                            val lat = parser.getAttributeValue(null, "lat").toDouble()
                            val lon = parser.getAttributeValue(null, "lon").toDouble()
                            val currentLatLng = LatLng(lat, lon)

                            // Calculate elevation if <ele> tag exists inside trkpt
                            var ele = 0.0

                            // Search inside the trkpt tag for child tags
                            var interiorEventType = parser.next()
                            while (!(interiorEventType == XmlPullParser.END_TAG && (parser.name == "trkpt" || parser.name == "rtept"))) {
                                if (interiorEventType == XmlPullParser.START_TAG && parser.name == "ele") {
                                    ele = parser.nextText().toDouble()
                                }
                                interiorEventType = parser.next()
                            }

                            // Accumulate distance
                            lastLatLng?.let {
                                cumulativeDistance += SphericalUtil.computeDistanceBetween(it, currentLatLng)
                            }

                            points.add(PathPoint(
                                latLng = currentLatLng,
                                distance = cumulativeDistance,
                                altitude = ele
                            ))
                            lastLatLng = currentLatLng
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return Pair(routeName, points)
    }

    private fun calculateElevationGain(points: List<PathPoint>): Double {
        // TODO: Might be too noicy. -> Implement some filtering.
        var gain = 0.0
        for (i in 1 until points.size) {
            val diff = points[i].altitude - points[i - 1].altitude
            if (diff > 0) gain += diff
        }
        return gain
    }
}