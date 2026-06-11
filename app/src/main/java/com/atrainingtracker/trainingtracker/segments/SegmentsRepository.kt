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

package com.atrainingtracker.trainingtracker.segments

import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.AltitudeFormatter
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper
import com.atrainingtracker.trainingtracker.ui.map.MapSegment
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.text.equals

data class SegmentSummary(
    val stravaId: Long,
    val name: String,
    val bSportType: BSportType,
    val climbCategory_raw: Int,
    val climbCategory: String,
    val prTime_raw: Int,
    val prTime: String,
    val city: String,
    val distance: String,
    val distance_raw: Double,
    val averageGrade_raw: Double,
    val averageGrade: String,
    val maxGrade: String,
    val elevationGain_raw: Double,  // necessary for sorting
    val elevationGain: String,
    val elevationMin: String,
    val elevationMax: String,
    val map_polyline: String,
)

data class SegmentWithPath(
    val summary: SegmentSummary,
    val path: List<PathPoint>
)


// data class for the segment (Strava API)
@Serializable
data class StravaSegment(
    val id: Long,
    val name: String,
    val activity_type: String,
    val distance: Double,
    val average_grade: Double,
    val maximum_grade: Double,
    val elevation_high: Double,
    val elevation_low: Double,
    val total_elevation_gain: Double? = null,
    val start_latlng: List<Double>,
    val end_latlng: List<Double>,
    val climb_category: Int,
    val city: String? = null, // Strava sometimes returns null for city
    val state: String? = null,
    val country: String? = null,
    val map: StravaMap? = null,
    var pr_time: Int? = null    // must be updated because the detailed segment does not have this property directly.
)
/**
 * Extension function to convert a StravaSegment (API Model)
 * to a SegmentSummary (Internal App Model).
 */
private fun StravaSegment.toSummary(): SegmentSummary {
    // Map Strava's activity_type string to our BSportType enum
    val sportType = when (this.activity_type) {
        "Ride" -> BSportType.BIKE
        "Run" -> BSportType.RUN
        else -> BSportType.UNKNOWN
    }
    val tf = TimeFormatter()
    val df = DistanceFormatter()
    val af = AltitudeFormatter()
    val locale = java.util.Locale.getDefault()
    val elevationGain = this.total_elevation_gain ?: (this.elevation_high - this.elevation_low)

    return SegmentSummary(
        stravaId = this.id,
        name = this.name,
        bSportType = sportType,
        climbCategory_raw = this.climb_category,
        climbCategory = StravaHelper.translateClimbCategory(this.climb_category),
        prTime_raw = this.pr_time ?: 0,
        prTime = if (this.pr_time != null) tf.format(this.pr_time) else "",
        city = this.city ?: "",
        distance = df.format_with_units(this.distance),
        distance_raw = this.distance,
        averageGrade_raw = this.average_grade,
        averageGrade = String.format(locale, "Ø %.1f%%", this.average_grade),
        maxGrade = String.format(locale, "%.1f%% Max", this.maximum_grade),
        elevationGain_raw = elevationGain,
        elevationGain = af.format_with_units(elevationGain),
        elevationMin = af.format_with_units(this.elevation_low),
        elevationMax = af.format_with_units(this.elevation_high),
        map_polyline = this.map?.polyline ?: ""
    )
}


@Serializable
data class StravaMap(
    val id: String,
    val polyline: String? = null
)

@Serializable
data class StravaStream(
    val type: String,
    val data: List<JsonElement>, // Flexible type to handle both numbers and arrays
    @SerialName("series_type") val seriesType: String,
    val resolution: String,
    @SerialName("original_size") val originalSize: Int
)

private val json = Json {
    ignoreUnknownKeys = true // CRITICAL: Strava sends many fields we don't need
    coerceInputValues = true
}

class SegmentsRepository private constructor(context: Context) {

    private val segmentsDb = SegmentsDatabaseManager.getInstance(context)


    // Repository scope for background loading
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)


    private val _allSegmentsWithPath = MutableStateFlow(emptyList<SegmentWithPath>())
    val allSegmentsWithPath: StateFlow<List<SegmentWithPath>> = _allSegmentsWithPath

    val connectedToStrava: Boolean
        get() = TrainingApplication.getStravaAccessToken() != null

    init {
        if (DEBUG) Log.i(TAG, "init")

        // Load segments from DB into memory immediately upon creation
        repositoryScope.launch {
            val segmentSummaries = segmentsDb.getAllSegmentSummaries()

            // Load the path of all segments into memory (one by one)
            segmentSummaries.forEach { segmentSummary ->
                val path = segmentsDb.getSegmentPath(segmentSummary.stravaId)
                _allSegmentsWithPath.value += SegmentWithPath(segmentSummary, path)
            }

        }
    }

    /***********************************************************************************************
     * Get segments from Strava
     **********************************************************************************************/
    // Set containing the sport types currently being refreshed
    private val _refreshingSports = MutableStateFlow<Set<BSportType>>(emptySet())
    val refreshingSports: StateFlow<Set<BSportType>> = _refreshingSports.asStateFlow()

    /**
     * Java-friendly method to trigger a sync without dealing with Coroutines in Java code.
     */
    fun syncSegmentsAsync(bSportType: BSportType) {
        // Use GlobalScope or pass a scope in. For a one-time sync after login,
        // a background scope is appropriate.
        CoroutineScope(Dispatchers.IO).launch {
            // try {
                syncStarredSegments(bSportType)
            //} catch (e: Exception) {
            //    Log.e("SegmentsRepository", "Async sync failed", e)
            //}
        }
    }

    suspend fun syncStarredSegments(bSportType: BSportType) {
        if (bSportType == BSportType.UNKNOWN) {
            syncStarredSegmentsWorker(BSportType.BIKE)
            syncStarredSegmentsWorker(BSportType.RUN)
        }
        else {
            syncStarredSegmentsWorker(bSportType)
        }
    }

    private suspend fun syncStarredSegmentsWorker(bSportType: BSportType) = withContext(Dispatchers.IO) {
            _refreshingSports.update { it + bSportType } // Add sport to refreshing set

        // sport to ignore:  For Run we ignore Ride and for Run we ignore Bike.  For Unknown we should not ignore.
        val ignoreSport = when (bSportType) {
            BSportType.RUN -> "Ride"
            BSportType.BIKE -> "Run"
            else -> "Foo"
        }

        // Store existing IDs to find what to delete later
        val oldIds = if (bSportType == BSportType.UNKNOWN) {
            allSegmentsWithPath.value.map { it.summary.stravaId }.toSet() }
        else {
            allSegmentsWithPath.value.filter { it.summary.bSportType == bSportType }.map { it.summary.stravaId }.toSet()
        }

        val newIds = mutableSetOf<Long>()
        // Create a local copy of the current list to modify
        // val currentLiveSegments = _liveSegments.value.toMutableList()

        var page = 1
        var hasMore = true

        // 2. Pagination Loop
        while (hasMore) {
            val responseBody = fetchStarredSegmentsFromStrava(page)

            // AUTOMATIC PARSING: Converts the whole string into a List of objects
            val segments = json.decodeFromString<List<StravaSegment>>(responseBody)

            if (segments.isEmpty()) {
                hasMore = false
                continue
            }

            for (segment in segments) {
                if (ignoreSport.equals(segment.activity_type, ignoreCase = true)) continue

                // get the detailed segment
                val detailedSegment = fetchDetailedSegment(segment.id) ?: segment

                // unfortunately, the detailedSegment does not contain the pr_time directly, so we have to copy it from the original segment.
                detailedSegment.pr_time = segment.pr_time

                // store or update the database
                addOrUpdateSegmentOnDb(detailedSegment)
                newIds.add(segment.id)

                if (!oldIds.contains(segment.id)) {
                    // get the path and add the SegmentWithPath to the list
                    val path = fetchAndInsertStream(segment.id) ?: emptyList()
                    _allSegmentsWithPath.value += SegmentWithPath(detailedSegment.toSummary(), path)
                }
                else {
                    // Update the item in the list if it exists
                    _allSegmentsWithPath.update { currentList ->
                        currentList.map { item ->
                            if (item.summary.stravaId == segment.id) {
                                item.copy(summary = detailedSegment.toSummary())
                            } else {
                                item
                            }
                        }
                    }
                }
            }
            page++
            hasMore = segments.size >= 30 // Strava default page size
        }

        // 4. Cleanup: Remove segments no longer starred on Strava
        val toDelete = oldIds - newIds
        // val toDelete = oldIds + newIds // uncomment to delete all segments (ONLY FOR TESTING!)
        if (toDelete.isNotEmpty()) {
            toDelete.forEach { deleteSegment(it) }

            // Filter the StateFlow list directly
            _allSegmentsWithPath.update { currentList ->
                currentList.filterNot { toDelete.contains(it.summary.stravaId) }
            }
        }

        _refreshingSports.update { it - bSportType } // Remove sport when done
    }

    private suspend fun fetchStarredSegmentsFromStrava(page: Int): String = withContext(Dispatchers.IO) {
        // 1. Get the access token from your existing StravaHelper
        val accessToken = StravaHelper.getRefreshedAccessToken()
        if (accessToken.isNullOrEmpty()) {
            Log.e(TAG, "Strava Access Token is null or empty")
            return@withContext "[]"
        }

        // 2. Build the URL for starred segments
        // Strava API: GET /segments/starred
        val url = "https://www.strava.com/api/v3/segments/starred?page=$page&per_page=30"

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to fetch segments: ${response.code} ${response.message}")
                    return@withContext "[]"
                }
                return@withContext response.body?.string() ?: "[]"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching Strava segments", e)
            "[]"
        }
    }

    private fun addOrUpdateSegmentOnDb(segment: StravaSegment) {
        segmentsDb.addOrUpdateSegment(segment)
    }

    private fun deleteSegment(segmentId: Long) {
        segmentsDb.deleteSegment(segmentId)
    }

    private suspend fun fetchDetailedSegment(segmentId: Long): StravaSegment? = withContext(Dispatchers.IO) {
        val accessToken = StravaHelper.getRefreshedAccessToken() ?: return@withContext null
        val url = "https://www.strava.com/api/v3/segments/$segmentId"

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        return@withContext try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                json.decodeFromString<StravaSegment>(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching details for segment $segmentId", e)
            null
        }
    }

    private suspend fun fetchAndInsertStream(segmentId: Long): List<PathPoint>? = withContext(Dispatchers.IO) {

        // 2. Fetch from Strava
        val accessToken = StravaHelper.getRefreshedAccessToken() ?: return@withContext null
        val url = "https://www.strava.com/api/v3/segments/$segmentId/streams/latlng,distance,altitude,time"

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        val responseBody = try {
            client.newCall(request).execute().use { it.body?.string() }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching streams for $segmentId", e)
            null
        } ?: return@withContext null

        // 3. Decode JSON into our List<StravaStream>
        val streams = try {
            json.decodeFromString<List<StravaStream>>(responseBody)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding stream JSON", e)
            return@withContext null
        }

        if (streams.isEmpty()) return@withContext emptyList()

        // 4. Transform parallel streams into rows of ContentValues
        val streamSize = streams.first().data.size
        val effortRows = List(streamSize) { ContentValues() }
        val pathPoints = mutableListOf<PathPoint>()
        var haveTime = false

        for (stream in streams) {
            for (i in 0 until streamSize) {
                val element = stream.data[i]
                when (stream.type) {
                    "latlng" -> {
                        // latlng is a JsonArray [lat, lng]
                        val coords = element.jsonArray
                        effortRows[i].put(SegmentsDatabaseManager.Segments.LATITUDE, coords[0].jsonPrimitive.double)
                        effortRows[i].put(SegmentsDatabaseManager.Segments.LONGITUDE, coords[1].jsonPrimitive.double)
                    }
                    "time" -> {
                        effortRows[i].put("time", element.jsonPrimitive.int)
                        haveTime = true
                    }
                    "distance" -> effortRows[i].put(SegmentsDatabaseManager.Segments.DISTANCE, element.jsonPrimitive.double)
                    "altitude" -> effortRows[i].put(SegmentsDatabaseManager.Segments.ALTITUDE, element.jsonPrimitive.double)
                }
            }
        }
        effortRows.forEach { row ->
            pathPoints.add(PathPoint(
                latLng = LatLng(row.getAsDouble(SegmentsDatabaseManager.Segments.LATITUDE), row.getAsDouble(SegmentsDatabaseManager.Segments.LONGITUDE)),
                distance = row.getAsDouble(SegmentsDatabaseManager.Segments.DISTANCE) ?: 0.0,
                altitude = row.getAsDouble(SegmentsDatabaseManager.Segments.ALTITUDE) ?: 0.0
            ))
        }

        // 5. Delegate database insertion and interpolation to the Manager
        segmentsDb.insertSegmentStreams(segmentId, effortRows, haveTime)

        return@withContext pathPoints
    }


    /***********************************************************************************************
     * Companion Object
     **********************************************************************************************/

    companion object {
        val DEBUG = true
        val TAG = "SegmentsRepository"

        @Volatile
        private var instance: SegmentsRepository? = null

        fun getInstance(context: Context): SegmentsRepository {
            return instance ?: synchronized(this) {
                instance ?: SegmentsRepository(context.applicationContext).also { instance = it }
            }
        }
    }

}