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
    val prTime: String,
    val city: String,
    val distance: String,
    val averageGrade: String,
    val maxGrade: String,
    val elevationGain: String,
    val elevationMin: String,
    val elevationMax: String,
)

// data class with the values to calculate whether we are currently on a segment
private data class LiveSegmentState(
    val stravaId: Long,
    val path: List<PathPoint>,
    val start: LatLng,             // The start point of the segment
    val start_a: LatLng,           // one end of the start line
    val start_b: LatLng,           // the other end of the start line
    val start_cross_n: Double,     // the cross product of the 'next' point to the start line
    var start_cross_loc: Double,   // the cross product of the current/past location to the start line
    var onSegment: Boolean,        // whether we are currently on the segment
    var startTime: Long,           // the time when we started the segment
    var startDistance: Double,     // the distance, when we started the segment
    var indexOfDistance: Int,      // index of the segment flow of the current distance
    val end: LatLng,               // the end point of the segment
    val end_a: LatLng,             // one end of the finish line
    val end_b: LatLng,             // the other end of the finish line
    val end_cross_p: Double,       // the cross product of the 'previous' point to the finish line
    var end_cross_loc: Double,     // the cross product of the current/past location to the finish line
)

// data class with the resulting data for a segment (time and distance)
data class LiveSegment(
    val stravaId: Long,
    val name: String,
    val prTime: String,
    var distanceToStart: Double = Double.MAX_VALUE,
    var timeOnSegment: Int = -1,
    var distanceOnSegment: Double = -1.0,
    var distanceToEnd: Double = Double.MAX_VALUE,
    var distanceToSegment: Double = Double.MAX_VALUE,
)

class SegmentsRepository private constructor(context: Context) {

    private val dbManager = SegmentsDatabaseManager.getInstance(context)

    private val banalRepo = BANALServiceRepository.getInstance(context)

    // Repository scope for background loading
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // In-memory cache of segments
    private val _allSegments = MutableStateFlow<List<MapSegment>?>(null)

    // In-memory cache of live calculation states
    private val _liveSegmentStates = mutableMapOf<Long, LiveSegmentState>()

    private val _liveSegments = mutableMapOf<Long, LiveSegment>()

    init {
        if (DEBUG) Log.i(TAG, "init")

        // Load segments from DB into memory immediately upon creation
        repositoryScope.launch {
            val segments = dbManager.allSegments ?: emptyList()
            _allSegments.value = segments

            // Calculate LiveStates for all loaded segments
            segments.forEach { segment ->
                calculateInitialLiveSegmentState(segment)?.let { state ->
                    _liveSegmentStates[segment.id] = state
                }
            }

            // 2. Observe the BANALServiceRepository for Location and Bearing updates
            launch {
                banalRepo.currentLocation.collect { location ->
                    if (location != null) {
                        updateSegmentProximity(location)
                    }
                }
            }
        }
    }

    /**
     * Fetches all segments. If they haven't loaded yet, it waits for the background task.
     */
    suspend fun getAllSegments(): List<MapSegment> = withContext(Dispatchers.IO) {
        // Wait until the flow has a non-null value (meaning DB load finished)
        _allSegments.first { it != null } ?: emptyList()
    }

    /**
     * Fetches a specific segment by its ID from the in-memory cache.
     */
    suspend fun getSegmentById(segmentId: Long): MapSegment? = withContext(Dispatchers.IO) {
        // Ensure data is loaded before filtering
        val segments = _allSegments.first { it != null }
        segments?.find { it.id == segmentId }
    }

    /**
     * Fetches the summary details for a specific segment.
     */
    suspend fun getSegmentSummary(segmentId: Long): SegmentSummary? = withContext(Dispatchers.IO) {
        // This assumes your dbManager has a corresponding method to return this data class
        dbManager.getSegmentSummary(segmentId)
    }

    /**
     * Optional: Trigger a refresh if the user adds/edits segments
     */
    fun refreshSegments() {
        repositoryScope.launch {
            _allSegments.value = dbManager.allSegments ?: emptyList()
        }
    }

    /**
     * Calculates the virtual gates (start/finish lines) and distances for a segment.
     * The gates are perpendicular to the path direction.
     */
    private fun calculateInitialLiveSegmentState(segment: MapSegment): LiveSegmentState? {
        val path = segment.path
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


        // 3. Pre-calculate cross products for start/end points
        val startCrossN = crossProduct(startA, startB, next)
        val endCrossP = crossProduct(endA, endB, prevToEnd)

        return LiveSegmentState(
            stravaId = segment.id,
            path = path,
            start = start,
            start_a = startA,
            start_b = startB,
            start_cross_n = startCrossN,
            start_cross_loc = 0.0, // Initial state
            onSegment = false,  // not yet on the segment
            startTime = -1,
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
     * Updates the start_dist_loc and end_dist_loc for every segment
     * whenever the user's location changes.
     */
    private fun updateSegmentProximity(currentLocation: LatLng) {
        if (DEBUG) Log.i(TAG, "updateSegmentProximity...")

        _liveSegmentStates.values.forEach { state ->
            val start_cross_loc = crossProduct(state.start_a, state.start_b, currentLocation)
            val distanceToStart = PolyUtil.distanceToLine(currentLocation, state.start_a, state.start_b)
            val end_cross_loc = crossProduct(state.end_a, state.end_b, currentLocation)
            val distanceToEnd = PolyUtil.distanceToLine(currentLocation, state.end_a, state.end_b)

            // close to start
            if (start_cross_loc.sign != state.start_cross_n.sign  // different sign as the 'first' point in the segment -> on the other side of the start line
                && distanceToStart <= SEGMENT_START_DISTANCE_THRESHOLD) {  // close enough to the start line
                if (DEBUG) Log.i(TAG, "updateSegmentProximity: A segment is close to start: ${state.stravaId}")

                if (!_liveSegments.containsKey(state.stravaId)) {
                    // Find the base segment to get the Name and PR
                    val baseSegment = _allSegments.value?.find { it.id == state.stravaId }
                    _liveSegments[state.stravaId] = LiveSegment(
                        stravaId = state.stravaId,
                        name = baseSegment?.name ?: "Unknown",
                        prTime = "--:--", // TODO: This must be pulled from the database or cache or ...
                        distanceToStart = distanceToStart,
                        timeOnSegment = 0,
                        distanceOnSegment = 0.0,
                        distanceToEnd = Double.MAX_VALUE,
                        distanceToSegment = distanceToStart
                    )
                } else {
                    // Update existing "approaching" segment distance
                    _liveSegments[state.stravaId]?.let { liveSegment ->
                        _liveSegments[state.stravaId] = liveSegment.copy(
                            distanceToStart = distanceToStart,
                            distanceToSegment = distanceToStart)
                    }
                }
            }

            else if (_liveSegments.containsKey(state.stravaId)       // already added to the list of liveSegments.
                && !state.onSegment                                  // but we are not on the segment
                && start_cross_loc.sign != state.start_cross_n.sign  // different sign as the 'first' point in the segment -> on the other side of the start line
                && distanceToStart > SEGMENT_START_DISTANCE_THRESHOLD  // far away from the start line
            ) {
                // clean up ->
                if (DEBUG) Log.i(TAG, "updateSegmentProximity: Not yet started segment will be removed: ${state.stravaId}")
                _liveSegments.remove(state.stravaId)
            }

            // crossing the start line
            else if (state.start_cross_loc.sign != start_cross_loc.sign // sign has changed -> crossed the start line
                && start_cross_loc.sign == state.start_cross_n.sign  // same sign as the 'first' point in the segment -> crossed the start line in the right direction
                && distanceToStart <= SEGMENT_DISTANCE_THRESHOLD) {  // close enough to the start line
                // -> really crossed the start line
                if (DEBUG) Log.i(TAG, "updateSegmentProximity: We crossed the start line: ${state.stravaId}")

                // remember the startTime and startDistance
                state.onSegment = true
                state.startTime = System.currentTimeMillis()
                state.startDistance = banalRepo.currentDistance.value ?: 0.0

                // set the distanceToStart to 0.0.
                _liveSegments[state.stravaId]?.let { liveSegment ->
                    _liveSegments[state.stravaId] = liveSegment.copy(
                        distanceToStart = 0.0)
                }
            }

            // close to the finish line
            else if (end_cross_loc.sign == state.end_cross_p.sign // same sign as the 'last' point in the segment -> not yet over the finish line
                && distanceToEnd <= SEGMENT_END_DISTANCE_THRESHOLD) {
                if (DEBUG) Log.i(TAG, "updateSegmentProximity: We are close to the finish line: ${state.stravaId}")

                _liveSegments[state.stravaId]?.let { liveSegment ->
                    _liveSegments[state.stravaId] = liveSegment.copy(distanceToEnd = distanceToEnd)
                }
            }

            // crossing the finish line
            else if (state.end_cross_loc.sign != end_cross_loc.sign  // sign has changed -> crossed the finish line
                && end_cross_loc.sign != state.end_cross_p.sign      // different sign as the 'last' point in the segment -> crossed the finish line in the right direction
                && distanceToEnd <= SEGMENT_DISTANCE_THRESHOLD) { // close enough

                if (DEBUG) Log.i(TAG, "updateSegmentProximity: We crossed the finish line: ${state.stravaId}")

                // simply remove the segment from the map
                if (_liveSegments.containsKey(state.stravaId)) {
                    _liveSegments.remove(state.stravaId)
                }
            }

            // finally, remember the cross products
            state.start_cross_loc = start_cross_loc
            state.end_cross_loc = end_cross_loc


            /*
             * update the liveSegments
             */

            val currentLocation = banalRepo.currentLocation.value!!
            val currentDistance = banalRepo.currentDistance.value!!

            //Loop over all liveSegments
            // update time, distance on segment, and distance to segment, ...
            // remove segments that are too far away.
            for (liveSegment in _liveSegments.values) {
                if (DEBUG) Log.i(TAG, "updateSegmentProximity: Updating liveSegment: ${liveSegment.stravaId}")

                val liveSegmentState = _liveSegmentStates[liveSegment.stravaId]!!

                if (liveSegmentState.onSegment) {
                    val timeOnSegment =
                        (System.currentTimeMillis() - liveSegmentState.startTime) / 1000
                    val distanceOnSegment =
                        currentDistance - liveSegmentState.startDistance

                    // find the index that matches the current distance
                    while (liveSegmentState.path[liveSegmentState.indexOfDistance].distance < distanceOnSegment
                        && liveSegmentState.indexOfDistance < liveSegmentState.path.size - 1
                    ) {
                        liveSegmentState.indexOfDistance++
                    }
                    // -> indexOfDistance is such that it points to the distance that is bigger than the current distance

                    // get the distance of the current location to the segment
                    val distanceToSegment = PolyUtil.distanceToLine(
                        currentLocation,
                        liveSegmentState.path[liveSegmentState.indexOfDistance - 1].latLng,
                        liveSegmentState.path[liveSegmentState.indexOfDistance].latLng
                    )

                    liveSegment.timeOnSegment = timeOnSegment.toInt()
                    liveSegment.distanceOnSegment = distanceOnSegment
                    liveSegment.distanceToSegment = distanceToSegment

                    // if distance to segment is too far away, remove the segment from the map
                    if (distanceToSegment > SEGMENT_DISTANCE_THRESHOLD) {
                        _liveSegments.remove(liveSegment.stravaId)
                    }
                }
            }
        }
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