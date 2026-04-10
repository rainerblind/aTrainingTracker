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
import com.google.maps.android.PolyUtil
import com.atrainingtracker.banalservice.BSportType
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
    val averageGrade: String,
    val maxGrade: String,
    val elevationGain: String,
    val elevationMin: String,
    val elevationMax: String,
)

enum class LiveSegmentStatus {
    FAR_FAR_AWAY,
    APPROACHING,
    ON_SEGMENT,
    ON_SEGMENT_CLOSE_TO_FINISH,
    FINISHED
}

// data class with the resulting data for a segment (time and distance)
data class LiveSegment(
    val segmentSummary: SegmentSummary,
    val path: List<PathPoint>,
    var liveSegmentStatus: LiveSegmentStatus,
    var timeOnSegment: Int = -1,
    var distanceToStart: Double = Double.MAX_VALUE,
    var distanceOnSegment: Double = -1.0,
    var distanceToEnd: Double = Double.MAX_VALUE,
    var distanceToSegment: Double = Double.MAX_VALUE,

    internal val start: LatLng,             // The start point of the segment
    internal val start_a: LatLng,           // one end of the start line
    internal val start_b: LatLng,           // the other end of the start line
    internal val start_cross_n: Double,     // the cross product of the 'next' point to the start line
    internal var start_cross_loc: Double,   // the cross product of the current/past location to the start line
    internal val end: LatLng,               // the end point of the segment
    internal val end_a: LatLng,             // one end of the finish line
    internal val end_b: LatLng,             // the other end of the finish line
    internal val end_cross_p: Double,       // the cross product of the 'previous' point to the finish line
    internal var end_cross_loc: Double,     // the cross product of the current/past location to the finish line
    internal var startTime_ms: Long,        // the time (in milliseconds) when we started the segment
    internal var startDistance: Double,     // the distance, when we started the segment
    internal var indexOfDistance: Int      // index of the segment flow of the current distance
)

class SegmentsRepository private constructor(context: Context) {

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
            segmentSummary = segmentSummary,
            path = path,
            start = start,
            start_a = startA,
            start_b = startB,
            start_cross_n = startCrossN,
            start_cross_loc = 0.0, // Initial state
            liveSegmentStatus = LiveSegmentStatus.FAR_FAR_AWAY,
            startTime_ms = -1,
            startDistance = 0.0,
            indexOfDistance = 0,
            end = end,
            end_a = endA,
            end_b = endB,
            end_cross_p = endCrossP,
            end_cross_loc = 0.0  // Initial state
        )
    }

    /**
     * Updates the live segment data whenever the user's location changes.
     */
    private fun updateLiveSegments(currentLocation: LatLng, currentDistance: Double) {
        if (DEBUG) Log.i(TAG, "updateLiveSegments ...")

        _liveSegments.value.forEach { liveSegment ->
            if (DEBUG) Log.i(TAG, "  checking segment: ${liveSegment.segmentSummary.name}")

            val start_cross_loc = crossProduct(liveSegment.start_a, liveSegment.start_b, currentLocation)
            val distanceToStart = PolyUtil.distanceToLine(currentLocation, liveSegment.start_a, liveSegment.start_b)
            val end_cross_loc = crossProduct(liveSegment.end_a, liveSegment.end_b, currentLocation)
            val distanceToEnd = PolyUtil.distanceToLine(currentLocation, liveSegment.end_a, liveSegment.end_b)

            if (DEBUG) Log.i(TAG, "  distanceToStart: ${distanceToStart}, distanceToEnd: ${distanceToEnd}")
            if (DEBUG) Log.i(TAG, "  start_cross_loc: ${start_cross_loc}, end_cross_loc: ${end_cross_loc}")

            // close to start
            if (distanceToStart <= SEGMENT_START_DISTANCE_THRESHOLD) {
                if (start_cross_loc.sign != liveSegment.start_cross_n.sign) {  // different sign as the 'first' point in the segment -> on the other side of the start line
                    if (DEBUG) Log.i(TAG, "  Segment is close to start: ${liveSegment.segmentSummary.name}")

                    liveSegment.liveSegmentStatus = LiveSegmentStatus.APPROACHING
                    liveSegment.distanceToStart = distanceToStart
                }

                // crossing the start line
                else if (liveSegment.start_cross_loc.sign != start_cross_loc.sign    // sign has changed -> crossed the start line
                    && start_cross_loc.sign == liveSegment.start_cross_n.sign) {     // same sign as the 'first' point in the segment -> crossed the start line in the right direction
                    // && distanceToStart <= SEGMENT_DISTANCE_THRESHOLD) {           // TODO: does this make sense here?
                    if (DEBUG) Log.i(TAG, "  We crossed the start line of : ${liveSegment.segmentSummary.name}")

                    // remember the startTime and startDistance
                    liveSegment.liveSegmentStatus = LiveSegmentStatus.ON_SEGMENT
                    liveSegment.startTime_ms = System.currentTimeMillis()
                    liveSegment.startDistance = banalRepo.currentDistance.value ?: 0.0
                }
            }
            else if (liveSegment.liveSegmentStatus == LiveSegmentStatus.APPROACHING) {  // no longer close to start but formerly approaching
                liveSegment.liveSegmentStatus = LiveSegmentStatus.FAR_FAR_AWAY          // -> mark as far far away
            }

            // close to the finish line
            if (distanceToEnd <= SEGMENT_END_DISTANCE_THRESHOLD) {
                if ((liveSegment.liveSegmentStatus == LiveSegmentStatus.ON_SEGMENT || liveSegment.liveSegmentStatus == LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH)
                    && end_cross_loc.sign == liveSegment.end_cross_p.sign) { // same sign as the 'last' point in the segment -> not yet over the finish line
                    if (DEBUG) Log.i(TAG, "  We are close to the finish line: ${liveSegment.segmentSummary.name}")

                    liveSegment.liveSegmentStatus = LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH
                    liveSegment.distanceToEnd = distanceToEnd
                }
                // crossing the finish line
                if ((liveSegment.liveSegmentStatus == LiveSegmentStatus.ON_SEGMENT || liveSegment.liveSegmentStatus == LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH)
                    && liveSegment.end_cross_loc.sign != end_cross_loc.sign       // sign has changed -> crossed the finish line
                    && end_cross_loc.sign != liveSegment.end_cross_p.sign) {   // different sign as the 'last' point in the segment -> crossed the finish line in the right direction
                    // && distanceToEnd <= SEGMENT_DISTANCE_THRESHOLD) {       // close enough

                    if (DEBUG) Log.i(TAG, "  We crossed the finish line: ${liveSegment.segmentSummary.name}")

                    liveSegment.liveSegmentStatus = LiveSegmentStatus.FINISHED
                }
            }

            // Now, remember the cross products
            liveSegment.start_cross_loc = start_cross_loc
            liveSegment.end_cross_loc = end_cross_loc


             // update the liveSegments
            if (liveSegment.liveSegmentStatus == LiveSegmentStatus.ON_SEGMENT
                || liveSegment.liveSegmentStatus == LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH) {
                if (DEBUG) Log.i(TAG, "  we are on this segment.  Thus, we update it ...")

                liveSegment.timeOnSegment = ((System.currentTimeMillis() - liveSegment.startTime_ms) / 1000).toInt()
                liveSegment.distanceOnSegment = currentDistance - liveSegment.startDistance

                // find the index that matches the current distance
                while (liveSegment.indexOfDistance < liveSegment.path.size - 1
                    && liveSegment.path[liveSegment.indexOfDistance].distance <= liveSegment.distanceOnSegment
                ) {
                    liveSegment.indexOfDistance++
                }
                // -> indexOfDistance is such that it points to the distance that is bigger than the current distance
                // when we assume that the first distance in the segment stream is zero and the distance on segment is zero, when we just start the segment,
                // we should get indexOfDistance = 1

                // get the distance of the current location to the segment
                val distanceToSegment = if (liveSegment.indexOfDistance > 0) {
                    PolyUtil.distanceToLine(
                        currentLocation,
                        liveSegment.path[liveSegment.indexOfDistance -1].latLng,
                        liveSegment.path[liveSegment.indexOfDistance].latLng
                    )
                } else {
                    0.0  // simply 0.
                }
                liveSegment.distanceToSegment = distanceToSegment

                // if distance to segment is too far away, we set its state to FAR_FAR_AWAY
                if (distanceToSegment > SEGMENT_DISTANCE_THRESHOLD) {
                    liveSegment.liveSegmentStatus = LiveSegmentStatus.FAR_FAR_AWAY
                }
            }
        }

        _liveSegments.value = _liveSegments.value.toList() // This forces the StateFlow to emit
    }

    private fun crossProduct(a: LatLng, b: LatLng, c: LatLng): Double {
        return (b.latitude - a.latitude) * (c.longitude - a.longitude) - (b.longitude - a.longitude) * (c.latitude - a.latitude)
    }



    companion object {
        val DEBUG = true
        val TAG = "SegmentsRepository"

        val SEGMENT_DISTANCE_THRESHOLD = 50 // m   distance between the current location and the segment to decide whether we are on the segment
        val SEGMENT_START_DISTANCE_THRESHOLD = 200 // m   distance between the current location and the start line to show that we are approaching a segment start
        val SEGMENT_END_DISTANCE_THRESHOLD = 500 // m   distance between the current location and the finish line to show that we are approaching the finish line


        @Volatile
        private var instance: SegmentsRepository? = null

        fun getInstance(context: Context): SegmentsRepository {
            return instance ?: synchronized(this) {
                instance ?: SegmentsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}