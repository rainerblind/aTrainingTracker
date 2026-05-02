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
import android.database.DatabaseUtils
import android.util.Log
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.intl.Locale
import com.atrainingtracker.R
import com.google.maps.android.PolyUtil
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper
import com.atrainingtracker.trainingtracker.ui.map.MapSegment
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.tracking.BANALServiceRepository
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.sign
import kotlin.text.equals

data class SegmentSummary(
    val stravaId: Long,
    val name: String,
    val bSportType: BSportType,
    val climbCategory: String,
    val prTime_raw: Int,
    val prTime: String,
    val city: String,
    val distance: String,
    val distance_raw: Double,
    val averageGrade: String,
    val maxGrade: String,
    val elevationGain: String,
    val elevationMin: String,
    val elevationMax: String,
)

enum class LiveSegmentStatus(val resId: Int) {
    FAR_FAR_AWAY(R.string.segment_status_far_far_away),
    APPROACHING(R.string.segment_status_approaching),
    ON_SEGMENT(R.string.segment_status_on_segment),
    ON_SEGMENT_CLOSE_TO_FINISH(R.string.segment_status_near_finish),
    FINISHED(R.string.segment_status_finished)
}

data class LiveSegmentData(
    val segmentStatus: LiveSegmentStatus,
    val timeOnSegment: String = "--:--",
    val distanceToStart: String = "--",
    val distanceOnSegment: String = "--",
    val distanceOnSegment_raw: Double = 0.0,
    val remainingDistance: String = "--",
    val segmentOffset: String = "--",
    val segmentOffset_raw: Double = 0.0
)

data class LiveSegmentMath(
    val start: LatLng,             // The start point of the segment
    val start_a: LatLng,           // one end of the start line
    val start_b: LatLng,           // the other end of the start line
    val segmentStartBearing: Double,   // The bearing of the segment start point
    val start_cross_n: Double,     // the cross product of the 'next' point to the start line
    var start_cross_loc: Double = 0.0,   // the cross product of the current/past location to the start line
    val end: LatLng,               // the end point of the segment
    val end_a: LatLng,             // one end of the finish line
    val end_b: LatLng,             // the other end of the finish line
    val end_cross_p: Double,       // the cross product of the 'previous' point to the finish line
    var end_cross_loc: Double = 0.0,  // the cross product of the current/past location to the finish line
    var startTime_ms: Long = -1,      // the time (in milliseconds) when we started the segment
    var startDistance: Double = 0.0,  // the distance, when we started the segment
    var indexOfDistance: Int = 0      // index of the segment flow of the current distance
)


// data class that encapsulates all data for a live segment (summary, path, time and distances, as well as the math details)
data class LiveSegment(
    val summary: SegmentSummary,
    val path: List<PathPoint>,
    val liveData: LiveSegmentData,
    val math: LiveSegmentMath,
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
    val pr_time: Int? = null
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
    val locale = java.util.Locale.getDefault()

    return SegmentSummary(
        stravaId = this.id,
        name = this.name,
        bSportType = sportType,
        climbCategory = StravaHelper.translateClimbCategory(this.climb_category),
        prTime_raw = this.pr_time ?: 0,
        prTime = if (this.pr_time != null) tf.format(this.pr_time) else "",
        city = this.city ?: "",
        distance = df.format_with_units(this.distance),
        distance_raw = this.distance,
        averageGrade = String.format(locale, "Ø %.1f%%", this.average_grade),
        maxGrade = String.format(locale, "%.1f%% Max", this.maximum_grade),
        elevationGain = if (this.total_elevation_gain != null) {
            String.format(locale, "%d m", this.total_elevation_gain)
        }
        else {
            String.format(locale, "%d m", Math.round(this.elevation_high - this.elevation_low))
        },
        elevationMin = String.format(locale, "%d m", Math.round(this.elevation_low)),
        elevationMax = String.format(locale, "%d m", Math.round(this.elevation_high))
    )
}


@Serializable
data class StravaMap(
    val id: String,
    val polyline: String? = null
)

@Serializable
data class AthleteSegmentStats(
    val pr_elapsed_time: Int? = null,
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


    private val tf = TimeFormatter()
    private val df = DistanceFormatter()

    private val segmentsDb = SegmentsDatabaseManager.getInstance(context)

    private val banalRepo = BANALServiceRepository.getInstance(context)

    // Repository scope for background loading
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // In-memory cache of segments
    @Deprecated("can be removed later")
    private var _allMapSegments = MutableStateFlow<List<MapSegment>?>(null)

    private val _liveSegments = MutableStateFlow(emptyList<LiveSegment>())
    val liveSegments: StateFlow<List<LiveSegment>> = _liveSegments

    init {
        if (DEBUG) Log.i(TAG, "init")

        // Load segments from DB into memory immediately upon creation
        repositoryScope.launch {
            _allMapSegments.value = segmentsDb.getAllMapSegments()

            val segmentSummaries = segmentsDb.getAllSegmentSummaries()

            // Calculate LiveSegments for all loaded segments
            segmentSummaries.forEach { segmentSummary ->
                val path = segmentsDb.getSegmentPath(segmentSummary.stravaId)

                calculateInitialLiveSegmentState(segmentSummary, path)?.let { liveSegment ->
                    _liveSegments.value += liveSegment
                }
            }

            // 2. Observe the BANALServiceRepository for Location and Bearing updates
            launch {
                banalRepo.currentLocation.collect { location ->
                    val currentDistance = banalRepo.currentDistance.value
                    val currentBearing = banalRepo.currentBearing.value ?: 0.0
                    if (location != null && currentDistance != null) {
                        updateLiveSegments(location, currentBearing, currentDistance)
                    }
                }
            }
        }
    }

    /***********************************************************************************************
     * Live Segment stuff
     **********************************************************************************************/

    /**
     * Calculates the virtual gates (start/finish lines) and distances for a segment.
     * The gates are perpendicular to the path direction.
     */
    private fun calculateInitialLiveSegmentState(segmentSummary: SegmentSummary, path: List<PathPoint>): LiveSegment? {
        if (path.size < 6) return null

        val start = path.first().latLng
        val next = path[5].latLng
        val end = path.last().latLng
        val prevToEnd = path[path.size - 6].latLng

        // 1. Calculate Start Gate (Perpendicular to first path segment)
        val startA = LatLng(start.latitude + (start.longitude - next.longitude), start.longitude - (start.latitude - next.latitude))
        val startB = LatLng(start.latitude - (start.longitude - next.longitude), start.longitude + (start.latitude - next.latitude))

        // Calculate the bearing of the segment start
        val segmentStartBearing = SphericalUtil.computeHeading(start, next)

        // 2. Calculate End Gate (Perpendicular to last path segment)
        val endA = LatLng(end.latitude + (end.longitude - prevToEnd.longitude), end.longitude - (end.latitude - prevToEnd.latitude))
        val endB = LatLng(end.latitude - (end.longitude - prevToEnd.longitude), end.longitude + (end.latitude - prevToEnd.latitude))

        // 3. Pre-calculate cross products for next start/ prev end points
        val startCrossN = crossProduct(startA, startB, next)
        val endCrossP = crossProduct(endA, endB, prevToEnd)

        return LiveSegment(
            summary = segmentSummary,
            path = path,
            liveData = LiveSegmentData(segmentStatus = LiveSegmentStatus.FAR_FAR_AWAY),
            math = LiveSegmentMath(
                start = start,
                start_a = startA,
                start_b = startB,
                segmentStartBearing = segmentStartBearing,
                start_cross_n = startCrossN,
                end = end,
                end_a = endA,
                end_b = endB,
                end_cross_p = endCrossP,
            )
        )
    }

    /**
     * Updates the live segment data whenever the user's location changes.
     */
    private fun updateLiveSegments(currentLocation: LatLng, currentBearing: Double, currentDistance: Double) {
        if (DEBUG) Log.i(TAG, "updateLiveSegments ...")

        _liveSegments.value = _liveSegments.value.map { liveSegment ->
            if (DEBUG) Log.i(TAG, "  checking segment: ${liveSegment.summary.name}")

            var newSegmentStatus = liveSegment.liveData.segmentStatus

            val start_cross_loc =
                crossProduct(liveSegment.math.start_a, liveSegment.math.start_b, currentLocation)
            val distanceToStart = PolyUtil.distanceToLine(
                currentLocation,
                liveSegment.math.start_a,
                liveSegment.math.start_b
            )
            val end_cross_loc =
                crossProduct(liveSegment.math.end_a, liveSegment.math.end_b, currentLocation)
            val distanceToEnd = PolyUtil.distanceToLine(
                currentLocation,
                liveSegment.math.end_a,
                liveSegment.math.end_b
            )

            if (DEBUG) Log.i(
                TAG,
                "  distanceToStart: ${distanceToStart}, distanceToEnd: ${distanceToEnd}"
            )
            if (DEBUG) Log.i(
                TAG,
                "  start_cross_loc: ${start_cross_loc}, end_cross_loc: ${end_cross_loc}"
            )

            // -- state handling
            when (liveSegment.liveData.segmentStatus) {
                LiveSegmentStatus.FAR_FAR_AWAY -> {
                    // Calculate bearing alignment
                    val bearingDiff = getBearingDifference(currentBearing, liveSegment.math.segmentStartBearing)
                    val isHeadingCorrect = bearingDiff <= 45f // 45 degrees tolerance

                    if (distanceToStart <= SEGMENT_START_DISTANCE_THRESHOLD                // close enough to start
                        && isHeadingCorrect                                                // going in the right direction
                        && start_cross_loc.sign != liveSegment.math.start_cross_n.sign) {  // different sign as the 'first' point in the segment -> on the other side of the start line
                        if (DEBUG) Log.i(TAG, "  Segment is close to start: ${liveSegment.summary.name}")

                        newSegmentStatus = LiveSegmentStatus.APPROACHING
                    }
                }
                LiveSegmentStatus.APPROACHING -> {
                    // crossing the start line
                    if (liveSegment.math.start_cross_loc.sign != start_cross_loc.sign      // sign has changed -> crossed the start line
                        && start_cross_loc.sign == liveSegment.math.start_cross_n.sign     // same sign as the 'first' point in the segment -> crossed the start line in the right direction
                        && distanceToStart <= SEGMENT_DISTANCE_THRESHOLD) {                // close enough to the segment.
                        if (DEBUG) Log.i(TAG, "  We crossed the start line of : ${liveSegment.summary.name}")

                        // remember the startTime and startDistance
                        liveSegment.math.startTime_ms = System.currentTimeMillis()
                        liveSegment.math.startDistance = banalRepo.currentDistance.value ?: 0.0

                        newSegmentStatus = LiveSegmentStatus.ON_SEGMENT
                    }

                    // moved away from the start line
                    if (distanceToStart > SEGMENT_START_DISTANCE_THRESHOLD) {
                        newSegmentStatus = LiveSegmentStatus.FAR_FAR_AWAY
                    }
                }
                LiveSegmentStatus.ON_SEGMENT -> {
                    if (distanceToEnd <= SEGMENT_END_DISTANCE_THRESHOLD // close to the finish line
                        && end_cross_loc.sign == liveSegment.math.end_cross_p.sign) { // same sign as the 'last' point in the segment -> not yet over the finish line
                            if (DEBUG) Log.i(TAG, "  We are close to the finish line: ${liveSegment.summary.name}")

                        newSegmentStatus = LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH

                        // note that moving away from the segment is handled below.
                    }
                }
                LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH -> {
                    if (liveSegment.math.end_cross_loc.sign != end_cross_loc.sign       // sign has changed -> crossed the finish line
                        && end_cross_loc.sign != liveSegment.math.end_cross_p.sign) {   // different sign as the 'last' point in the segment -> crossed the finish line in the right direction
                        if (DEBUG) Log.i(TAG, "  We crossed the finish line: ${liveSegment.summary.name}")

                        newSegmentStatus = LiveSegmentStatus.FINISHED

                        // note that moving away from the segment is handled below.
                    }
                }
                LiveSegmentStatus.FINISHED -> {
                    if (distanceToEnd > SEGMENT_POST_END_DISTANCE_THRESHOLD) {
                        if (DEBUG) Log.i(TAG, "  We are far enough away from the finish line: ${liveSegment.summary.name}")

                        newSegmentStatus = LiveSegmentStatus.FAR_FAR_AWAY
                    }
                }
            }

            // remember the cross products
            liveSegment.math.start_cross_loc = start_cross_loc
            liveSegment.math.end_cross_loc = end_cross_loc

            // now, do an update based on the new state
            when (newSegmentStatus) {
                LiveSegmentStatus.APPROACHING -> {
                    liveSegment.copy(
                        liveData = LiveSegmentData(
                            segmentStatus = LiveSegmentStatus.APPROACHING,
                            distanceToStart = df.format_with_units(distanceToStart),
                            remainingDistance = df.format_with_units(liveSegment.summary.distance_raw + distanceToStart)
                        )
                    )
                }

                LiveSegmentStatus.ON_SEGMENT, LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH -> {
                    if (DEBUG) Log.i(TAG, "  we are on this segment.  Thus, we update it ...")

                    val timeOnSegment =
                        (System.currentTimeMillis() - liveSegment.math.startTime_ms) / 1000
                    val distanceOnSegment = currentDistance - liveSegment.math.startDistance

                    // The remainingDistance can be calculated by subtracting the distance on the segment form the segments distance or by the line distance to the finish line.
                    // The difference between the segments distance and the distance on the segment is perfect as long as we are not close to the finish line.
                    // The line distance to the finish line is perfect when we are close to the finish line.
                    // To get the best of both, we use a linear combination of this difference and the line distance to the finish line when we are close to the finish line.
                    // In doing so, we get a remainingDistance of zero at the finish line but no jumps of the remainingDistance.
                    val lambda = (distanceToEnd / SEGMENT_END_DISTANCE_THRESHOLD).coerceAtMost(1.0)
                    val remainingDistance =
                        (liveSegment.summary.distance_raw - distanceOnSegment) * lambda + (1 - lambda) * distanceToEnd

                    // find the index that matches the current distance
                    while (liveSegment.math.indexOfDistance < liveSegment.path.size - 1
                        && liveSegment.path[liveSegment.math.indexOfDistance].distance <= distanceOnSegment
                    ) {
                        liveSegment.math.indexOfDistance++
                    }
                    // -> indexOfDistance is such that it points to the distance that is bigger than the current distance
                    // when we assume that the first distance in the segment stream is zero and the distance on segment is zero, when we just start the segment,
                    // we should get indexOfDistance = 1

                    // get the distance of the current location to the segment
                    val distanceToSegment = if (liveSegment.math.indexOfDistance > 0) {
                        PolyUtil.distanceToLine(
                            currentLocation,
                            liveSegment.path[liveSegment.math.indexOfDistance - 1].latLng,
                            liveSegment.path[liveSegment.math.indexOfDistance].latLng
                        )
                    } else {
                        0.0  // simply 0.
                    }

                    // if distance to segment is too far away, we set its state to FAR_FAR_AWAY
                    if (distanceToSegment > SEGMENT_DISTANCE_THRESHOLD) {
                        liveSegment.copy(
                            liveData = LiveSegmentData(
                                segmentStatus = LiveSegmentStatus.FAR_FAR_AWAY,
                            )
                        )
                    } else {
                        liveSegment.copy(
                            liveData = LiveSegmentData(
                                segmentStatus = newSegmentStatus,
                                timeOnSegment = tf.format_with_units(timeOnSegment),
                                distanceOnSegment = df.format_with_units(distanceOnSegment),
                                distanceOnSegment_raw = distanceOnSegment,
                                remainingDistance = df.format_with_units(remainingDistance),
                                segmentOffset = df.format_with_units(distanceToSegment),
                                segmentOffset_raw = distanceToSegment
                            )
                        )
                    }
                }

                LiveSegmentStatus.FINISHED -> {
                    if (liveSegment.liveData.segmentStatus != LiveSegmentStatus.FINISHED) {   // just finished
                        val timeOnSegment =
                            (System.currentTimeMillis() - liveSegment.math.startTime_ms) / 1000
                        val distanceOnSegment = currentDistance - liveSegment.math.startDistance
                        liveSegment.copy(
                            liveData = LiveSegmentData(
                                segmentStatus = newSegmentStatus,
                                timeOnSegment = tf.format_with_units(timeOnSegment),
                                distanceOnSegment = df.format_with_units(distanceOnSegment),
                                distanceOnSegment_raw = distanceOnSegment,
                                remainingDistance = df.format_with_units(0),
                                segmentOffset = df.format_with_units(distanceToEnd)
                            )
                        )
                    }
                    else {  // finished some time ago -> only update the segmentOffset as the distance to the end.
                        liveSegment.copy(
                            liveData = liveSegment.liveData.copy(
                                segmentOffset = df.format_with_units(distanceToEnd)
                            )
                        )
                    }
                }

                LiveSegmentStatus.FAR_FAR_AWAY -> {
                    if (liveSegment.liveData.segmentStatus != LiveSegmentStatus.FAR_FAR_AWAY) {  // changed to FAR_FAR_AWAY -> copy to force an update
                        liveSegment.copy(
                            liveData = LiveSegmentData(
                                segmentStatus = newSegmentStatus
                            )
                        )
                    }
                    else {   // remain quiet
                        liveSegment
                    }
                }
            }
        }
    }

    private fun crossProduct(a: LatLng, b: LatLng, c: LatLng): Double {
        return (b.latitude - a.latitude) * (c.longitude - a.longitude) - (b.longitude - a.longitude) * (c.latitude - a.latitude)
    }

    /**
     * Calculates the smallest difference between two bearings (0-360).
     * Result is between 0 and 180.
     */
    private fun getBearingDifference(bearing1: Double, bearing2: Double): Double {
        val diff = Math.abs(bearing1 - bearing2) % 360
        return if (diff > 180) 360 - diff else diff
    }

    /***********************************************************************************************
     * Get segments from Strava
     **********************************************************************************************/
    suspend fun syncStarredSegments(bSportType: BSportType) = withContext(Dispatchers.IO) {

        // sport to ignore:  For Run we ignore Ride and for Run we ignore Bike.  For Unknown we should not ignore.
        val ignoreSport = when (bSportType) {
            BSportType.RUN -> "Ride"
            BSportType.BIKE -> "Run"
            else -> "Foo"
        }

        // Store existing IDs to find what to delete later
        val oldIds = if (bSportType == BSportType.UNKNOWN) {
            liveSegments.value.map { it.summary.stravaId }.toSet() }
        else {
            liveSegments.value.filter { it.summary.bSportType == bSportType }.map { it.summary.stravaId }.toSet()
        }

        val newIds = mutableSetOf<Long>()
        // Create a local copy of the current list to modify
        val currentLiveSegments = _liveSegments.value.toMutableList()

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

                newIds.add(segment.id)
                if (!oldIds.contains(segment.id)) {

                    // store the segment
                    addOrUpdateSegment(detailedSegment)

                    // get the path
                    val path = fetchAndInsertStream(segment.id) ?: emptyList()

                    // add it to the live segment list
                    val newLiveSegment = calculateInitialLiveSegmentState(detailedSegment.toSummary(), path)
                    if (newLiveSegment != null) {
                        currentLiveSegments.add(newLiveSegment)
                    }
                }
                else {
                    // only update (PB might have changed)
                    addOrUpdateSegment(detailedSegment)
                    // update list of LiveSegments
                    val index = currentLiveSegments.indexOfFirst { it.summary.stravaId == segment.id }
                    if (index != -1) {
                        val existing = currentLiveSegments[index]
                        currentLiveSegments[index] = existing.copy(
                            summary = segment.toSummary() // Update PB, name, etc but keep path/state
                        )
                    }
                }

                // Emit progress after every segment if you want the list to grow live
                _liveSegments.value = currentLiveSegments.toList()
            }
            page++
            hasMore = segments.size >= 30 // Strava default page size
        }

        // 4. Cleanup: Remove segments no longer starred on Strava
        val toDelete = oldIds - newIds
        if (toDelete.isNotEmpty()) {
            toDelete.forEach { deleteSegment(it) }
            currentLiveSegments.removeAll { toDelete.contains(it.summary.stravaId) }
            _liveSegments.value = currentLiveSegments.toList()
        }
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

    private fun addOrUpdateSegment(segment: StravaSegment) {
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

        val SEGMENT_DISTANCE_THRESHOLD = 50 // m          distance between the current location and the segment to decide whether we are on the segment
        val SEGMENT_START_DISTANCE_THRESHOLD = 250 // m   distance between the current location and the start line to show that we are approaching a segment start
        val SEGMENT_END_DISTANCE_THRESHOLD = 500 // m     distance between the current location and the finish line to show that we are approaching the finish line
        val SEGMENT_POST_END_DISTANCE_THRESHOLD = 250 // m distance after the finish line and the current location to mark this segment as far far away


        @Volatile
        private var instance: SegmentsRepository? = null

        fun getInstance(context: Context): SegmentsRepository {
            return instance ?: synchronized(this) {
                instance ?: SegmentsRepository(context.applicationContext).also { instance = it }
            }
        }
    }

}