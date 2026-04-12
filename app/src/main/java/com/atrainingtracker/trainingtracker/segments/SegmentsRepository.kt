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

import android.content.Context
import android.util.Log
import com.atrainingtracker.R
import com.google.maps.android.PolyUtil
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.ui.map.MapSegment
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.tracking.BANALServiceRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sign

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
    val remainingDistance: String = "--",
    val segmentOffset: String = "--"
)

data class LiveSegmentMath(
    val start: LatLng,             // The start point of the segment
    val start_a: LatLng,           // one end of the start line
    val start_b: LatLng,           // the other end of the start line
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

class SegmentsRepository private constructor(context: Context) {

    companion object {
        val DEBUG = true
        val TAG = "SegmentsRepository"

        val SEGMENT_DISTANCE_THRESHOLD = 50 // m          distance between the current location and the segment to decide whether we are on the segment
        val SEGMENT_START_DISTANCE_THRESHOLD = 250 // m   distance between the current location and the start line to show that we are approaching a segment start
        val SEGMENT_END_DISTANCE_THRESHOLD = 500 // m     distance between the current location and the finish line to show that we are approaching the finish line


        @Volatile
        private var instance: SegmentsRepository? = null

        fun getInstance(context: Context): SegmentsRepository {
            return instance ?: synchronized(this) {
                instance ?: SegmentsRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val tf = TimeFormatter()
    private val df = DistanceFormatter()

    private val dbManager = SegmentsDatabaseManager.getInstance(context)

    private val banalRepo = BANALServiceRepository.getInstance(context)

    // Repository scope for background loading
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // In-memory cache of segments
    private var _allMapSegments = MutableStateFlow<List<MapSegment>?>(null)

    private val _liveSegments = MutableStateFlow(emptyList<LiveSegment>())
    val liveSegments: StateFlow<List<LiveSegment>> = _liveSegments

    init {
        if (DEBUG) Log.i(TAG, "init")

        // Load segments from DB into memory immediately upon creation
        repositoryScope.launch {
            _allMapSegments.value = dbManager.getAllMapSegments()

            val segmentSummaries = dbManager.getAllSegmentSummaries()

            // Calculate LiveSegments for all loaded segments
            segmentSummaries.forEach { segmentSummary ->
                val path = dbManager.getSegmentPath(segmentSummary.stravaId)

                calculateInitialLiveSegmentState(segmentSummary, path)?.let { liveSegment ->
                    _liveSegments.value += liveSegment
                }
            }

            // 2. Observe the BANALServiceRepository for Location and Bearing updates
            launch {
                banalRepo.currentLocation.collect { location ->
                    val currentDistance = banalRepo.currentDistance.value
                    if (location != null && currentDistance != null) {
                        updateLiveSegments(location, currentDistance)
                    }
                }
            }
        }
    }

    /**
     * Fetches all segments. If they haven't loaded yet, it waits for the background task.
     */
    suspend fun getAllMapSegments(): List<MapSegment> = withContext(Dispatchers.IO) {
        // Wait until the flow has a non-null value (meaning DB load finished)
        _allMapSegments.first { it != null } ?: emptyList()
    }

    /**
     * Fetches a specific segment by its ID from the in-memory cache.
     */
    suspend fun getMapSegmentById(segmentId: Long): MapSegment? = withContext(Dispatchers.IO) {
        // Ensure data is loaded before filtering
        val segments = _allMapSegments.first { it != null }
        segments?.find { it.id == segmentId }
    }

    /**
     * Fetches the summary details for a specific segment.
     */
    suspend fun getSegmentSummary(segmentId: Long): SegmentSummary? = withContext(Dispatchers.IO) {
        // This assumes your dbManager has a corresponding method to return this data class
        dbManager.getSegmentSummary(segmentId)  // TODO: rewrite: use the LiveSegmentData instead.
    }

    /**
     * Optional: Trigger a refresh if the user adds/edits segments
     */
    fun refreshSegments() {
        repositoryScope.launch {
            _allMapSegments.value = dbManager.allMapSegments ?: emptyList()
        }
    }

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
    private fun updateLiveSegments(currentLocation: LatLng, currentDistance: Double) {
        if (DEBUG) Log.i(TAG, "updateLiveSegments ...")

        _liveSegments.value = _liveSegments.value.map { liveSegment ->
            if (DEBUG) Log.i(TAG, "  checking segment: ${liveSegment.summary.name}")

            var newSegmentStatus = liveSegment.liveData.segmentStatus

            val start_cross_loc = crossProduct(liveSegment.math.start_a, liveSegment.math.start_b, currentLocation)
            val distanceToStart = PolyUtil.distanceToLine(currentLocation, liveSegment.math.start_a, liveSegment.math.start_b)
            val end_cross_loc = crossProduct(liveSegment.math.end_a, liveSegment.math.end_b, currentLocation)
            val distanceToEnd = PolyUtil.distanceToLine(currentLocation, liveSegment.math.end_a, liveSegment.math.end_b)

            if (DEBUG) Log.i(TAG, "  distanceToStart: ${distanceToStart}, distanceToEnd: ${distanceToEnd}")
            if (DEBUG) Log.i(TAG, "  start_cross_loc: ${start_cross_loc}, end_cross_loc: ${end_cross_loc}")

            // close to start
            if (distanceToStart <= SEGMENT_START_DISTANCE_THRESHOLD) {
                if (start_cross_loc.sign != liveSegment.math.start_cross_n.sign) {  // different sign as the 'first' point in the segment -> on the other side of the start line
                    if (DEBUG) Log.i(TAG, "  Segment is close to start: ${liveSegment.summary.name}")

                    newSegmentStatus = LiveSegmentStatus.APPROACHING
                }

                // crossing the start line
                else if (liveSegment.math.start_cross_loc.sign != start_cross_loc.sign    // sign has changed -> crossed the start line
                    && start_cross_loc.sign == liveSegment.math.start_cross_n.sign) {     // same sign as the 'first' point in the segment -> crossed the start line in the right direction
                    // && distanceToStart <= SEGMENT_DISTANCE_THRESHOLD) {           // TODO: does this make sense here?
                    if (DEBUG) Log.i(TAG, "  We crossed the start line of : ${liveSegment.summary.name}")

                    // remember the startTime and startDistance
                    liveSegment.math.startTime_ms = System.currentTimeMillis()
                    liveSegment.math.startDistance = banalRepo.currentDistance.value ?: 0.0

                    newSegmentStatus = LiveSegmentStatus.ON_SEGMENT
                }
            }
            else if (liveSegment.liveData.segmentStatus == LiveSegmentStatus.APPROACHING) {  // no longer close to start but formerly approaching
                // -> mark as far far away
                newSegmentStatus = LiveSegmentStatus.FAR_FAR_AWAY
            }

            // close to the finish line
            if (distanceToEnd <= SEGMENT_END_DISTANCE_THRESHOLD) {
                if ((liveSegment.liveData.segmentStatus == LiveSegmentStatus.ON_SEGMENT || liveSegment.liveData.segmentStatus == LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH)
                    && end_cross_loc.sign == liveSegment.math.end_cross_p.sign) { // same sign as the 'last' point in the segment -> not yet over the finish line
                    if (DEBUG) Log.i(TAG, "  We are close to the finish line: ${liveSegment.summary.name}")

                    newSegmentStatus = LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH
                }
                // crossing the finish line
                if ((liveSegment.liveData.segmentStatus == LiveSegmentStatus.ON_SEGMENT || liveSegment.liveData.segmentStatus == LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH)
                    && liveSegment.math.end_cross_loc.sign != end_cross_loc.sign       // sign has changed -> crossed the finish line
                    && end_cross_loc.sign != liveSegment.math.end_cross_p.sign) {   // different sign as the 'last' point in the segment -> crossed the finish line in the right direction
                    // && distanceToEnd <= SEGMENT_DISTANCE_THRESHOLD) {       // close enough

                    if (DEBUG) Log.i(TAG, "  We crossed the finish line: ${liveSegment.summary.name}")

                    newSegmentStatus = LiveSegmentStatus.FINISHED
                }
            }

            // Now, remember the cross products
            liveSegment.math.start_cross_loc = start_cross_loc
            liveSegment.math.end_cross_loc = end_cross_loc

            if (newSegmentStatus == LiveSegmentStatus.APPROACHING) {
                liveSegment.copy(
                    liveData = LiveSegmentData(
                        segmentStatus = LiveSegmentStatus.APPROACHING,
                        distanceToStart = df.format_with_units(distanceToStart)
                    )
                )
            }
            else if (newSegmentStatus == LiveSegmentStatus.ON_SEGMENT
                || newSegmentStatus == LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH) {
                if (DEBUG) Log.i(TAG, "  we are on this segment.  Thus, we update it ...")

                val timeOnSegment = (System.currentTimeMillis() - liveSegment.math.startTime_ms) / 1000
                val distanceOnSegment = currentDistance - liveSegment.math.startDistance

                // The reimainingDistance can be calculated by subtracting the distance on the segment form the segments distance.
                // When we are close to the finish line, this difference will be no longer precise.
                // To get a remainingDistance of zero at the finish line, we use a linear combination of this difference and the line distance to the finish line when we are close to the finish line.
                val lambda = (distanceToEnd/SEGMENT_END_DISTANCE_THRESHOLD).coerceAtMost(1.0)
                val remainingDistance = (liveSegment.summary.distance_raw - distanceOnSegment)*lambda + (1-lambda)*distanceToEnd


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
                        liveSegment.path[liveSegment.math.indexOfDistance -1].latLng,
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
                }
                else {
                    liveSegment.copy(
                        liveData = LiveSegmentData(
                            segmentStatus = newSegmentStatus,
                            timeOnSegment = tf.format_with_units(timeOnSegment),
                            distanceOnSegment = df.format_with_units(distanceOnSegment),
                            remainingDistance = df.format_with_units(remainingDistance),
                            segmentOffset = df.format_with_units(distanceToSegment)
                        )
                    )
                }
            }
            else {
                liveSegment
            }
        }
    }

    private fun crossProduct(a: LatLng, b: LatLng, c: LatLng): Double {
        return (b.latitude - a.latitude) * (c.longitude - a.longitude) - (b.longitude - a.longitude) * (c.latitude - a.latitude)
    }
}