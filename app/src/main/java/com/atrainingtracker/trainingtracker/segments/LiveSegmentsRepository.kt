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
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.google.maps.android.SphericalUtil
import com.atrainingtracker.trainingtracker.geo.PolylineGate
import com.atrainingtracker.trainingtracker.geo.PolylineProgressTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.sign

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
    var indexOfDistance: Int = 0,     // index of the segment flow of the current distance
    val startGate: PolylineGate? = null,
    val endGate: PolylineGate? = null
)

// data class that encapsulates all data for a live segment (summary, path, time and distances, as well as the math details)
data class LiveSegment(
    val staticData: SegmentWithPath,
    val liveData: LiveSegmentData,
    val math: LiveSegmentMath,
)


class LiveSegmentsRepository internal constructor(
    context: Context,
    val segmentsRepository: SegmentsRepository = SegmentsRepository.getInstance(context),
    val banalRepository: BANALServiceRepository = BANALServiceRepository.getInstance(context),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    // Helpers for formatting time and distance
    private val tf = TimeFormatter()
    private val df = DistanceFormatter()

    private val _liveSegments = MutableStateFlow<List<LiveSegment>>(emptyList())
    val liveSegments = _liveSegments.asStateFlow()

    init {
        // 1. Listen for new/changed segments from the Data Repository
        scope.launch {
            segmentsRepository.allSegmentsWithPath.collect { staticSegments ->
                reconcileSegments(staticSegments)
            }
        }

        // 2. Listen for Location updates
        scope.launch {
            banalRepository.currentLocation.collect{ location ->
                if (location != null) {
                    val currentDistance = banalRepository.currentDistance.value ?: 0.0
                    val currentBearing = banalRepository.currentBearing.value ?: 0.0
                    updateLiveSegments(location, currentBearing, currentDistance)
                }
            }.collect()
        }
    }

    /**
     * Handles adding new segments without resetting the state of existing ones.
     */
    internal fun reconcileSegments(newStaticData: List<SegmentWithPath>) {
        val currentLive = _liveSegments.value.associateBy { it.staticData.summary.stravaId }

        val updatedList = newStaticData.mapNotNull { segmentWithPath ->
            val stravaId = segmentWithPath.summary.stravaId

            // 1. Check if we already have this segment active
            if (currentLive.containsKey(stravaId)) {
                currentLive[stravaId]
            } else {
                // 2. If new, try to initialize it. If it returns null,
                // mapNotNull will simply skip this entry.
                calculateInitialLiveSegmentState(segmentWithPath)
            }
        }

        _liveSegments.value = updatedList
    }

    /**
     * Calculates the virtual gates (start/finish lines) and distances for a segment.
     * The gates are perpendicular to the path direction.
     */
    internal fun calculateInitialLiveSegmentState(segmentWithPath: SegmentWithPath): LiveSegment? {
        val path = segmentWithPath.path

        if (path.size < 6) return null

        val startGate = PolylineProgressTracker.createGate(
            points = path,
            isStartGate = true,
            gateThresholdMeters = SEGMENT_START_DISTANCE_THRESHOLD.toDouble(),
            sampleOffset = 5
        ) ?: return null

        val endGate = PolylineProgressTracker.createGate(
            points = path,
            isStartGate = false,
            gateThresholdMeters = SEGMENT_END_DISTANCE_THRESHOLD.toDouble(),
            sampleOffset = 5
        ) ?: return null

        return LiveSegment(
            staticData = segmentWithPath,
            liveData = LiveSegmentData(segmentStatus = LiveSegmentStatus.FAR_FAR_AWAY),
            math = LiveSegmentMath(
                start = path.first().latLng,
                start_a = startGate.gateStart,
                start_b = startGate.gateEnd,
                segmentStartBearing = startGate.bearing,
                start_cross_n = startGate.referenceCrossProduct,
                end = path.last().latLng,
                end_a = endGate.gateStart,
                end_b = endGate.gateEnd,
                end_cross_p = endGate.referenceCrossProduct,
                startGate = startGate,
                endGate = endGate
            )
        )
    }



    /**
     * Updates the live segment data whenever the user's location changes.
     */
    internal fun updateLiveSegments(currentLocation: LatLng, currentBearing: Double, currentDistance: Double) {
        if (DEBUG) Log.i(TAG, "updateLiveSegments ...")

        _liveSegments.value = _liveSegments.value.map { liveSegment ->
            if (DEBUG) Log.i(TAG, "  checking segment: ${liveSegment.staticData.summary.name}")

            var newSegmentStatus = liveSegment.liveData.segmentStatus

            val startGate = liveSegment.math.startGate ?: PolylineGate(
                gateStart = liveSegment.math.start_a,
                gateEnd = liveSegment.math.start_b,
                referencePoint = liveSegment.math.start,
                referenceCrossProduct = liveSegment.math.start_cross_n,
                gateThresholdMeters = SEGMENT_START_DISTANCE_THRESHOLD.toDouble(),
                bearing = liveSegment.math.segmentStartBearing
            )
            val endGate = liveSegment.math.endGate ?: PolylineGate(
                gateStart = liveSegment.math.end_a,
                gateEnd = liveSegment.math.end_b,
                referencePoint = liveSegment.math.end,
                referenceCrossProduct = liveSegment.math.end_cross_p,
                gateThresholdMeters = SEGMENT_END_DISTANCE_THRESHOLD.toDouble(),
                bearing = liveSegment.math.segmentStartBearing
            )

            val start_cross_loc =
                PolylineProgressTracker.crossProduct(startGate.gateStart, startGate.gateEnd, currentLocation)
            val distanceToStart = PolylineProgressTracker.distanceToGate(currentLocation, startGate)
            val end_cross_loc =
                PolylineProgressTracker.crossProduct(endGate.gateStart, endGate.gateEnd, currentLocation)
            val distanceToEnd = PolylineProgressTracker.distanceToGate(currentLocation, endGate)

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
                    val isHeadingCorrect = PolylineProgressTracker.isHeadingAligned(
                        currentBearing,
                        startGate.bearing,
                        45.0
                    )

                    if (distanceToStart <= SEGMENT_START_DISTANCE_THRESHOLD                // close enough to start
                        && isHeadingCorrect                                                // going in the right direction
                        && start_cross_loc.sign != liveSegment.math.start_cross_n.sign) {  // different sign as the 'first' point in the segment -> on the other side of the start line
                        if (DEBUG) Log.i(TAG, "  Segment is close to start: ${liveSegment.staticData.summary.name}")

                        newSegmentStatus = LiveSegmentStatus.APPROACHING
                    }
                }
                LiveSegmentStatus.APPROACHING -> {
                    // crossing the start line via 2D vector cross-product sign-flip (REQ-GEO-001)
                    val crossedStart = PolylineProgressTracker.checkGateCrossing(
                        gate = startGate,
                        prevCrossProduct = liveSegment.math.start_cross_loc,
                        currCrossProduct = start_cross_loc,
                        distanceToGate = distanceToStart,
                        isStartGate = true
                    )
                    if (crossedStart && distanceToStart <= SEGMENT_DISTANCE_THRESHOLD) { // close enough to the segment
                        if (DEBUG) Log.i(TAG, "  We crossed the start line of : ${liveSegment.staticData.summary.name}")

                        // remember the startTime and startDistance
                        liveSegment.math.startTime_ms = System.currentTimeMillis()
                        liveSegment.math.startDistance = currentDistance

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
                        if (DEBUG) Log.i(TAG, "  We are close to the finish line: ${liveSegment.staticData.summary.name}")

                        newSegmentStatus = LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH

                        // note that moving away from the segment is handled below.
                    }
                }
                LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH -> {
                    val crossedEnd = PolylineProgressTracker.checkGateCrossing(
                        gate = endGate,
                        prevCrossProduct = liveSegment.math.end_cross_loc,
                        currCrossProduct = end_cross_loc,
                        distanceToGate = distanceToEnd,
                        isStartGate = false
                    )
                    if (crossedEnd) {
                        if (DEBUG) Log.i(TAG, "  We crossed the finish line: ${liveSegment.staticData.summary.name}")

                        newSegmentStatus = LiveSegmentStatus.FINISHED

                        // note that moving away from the segment is handled below.
                    }
                }
                LiveSegmentStatus.FINISHED -> {
                    if (distanceToEnd > SEGMENT_POST_END_DISTANCE_THRESHOLD) {
                        if (DEBUG) Log.i(TAG, "  We are far enough away from the finish line: ${liveSegment.staticData.summary.name}")

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
                            remainingDistance = df.format_with_units(liveSegment.staticData.summary.distance_raw + distanceToStart)
                        )
                    )
                }

                LiveSegmentStatus.ON_SEGMENT, LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH -> {
                    if (DEBUG) Log.i(TAG, "  we are on this segment.  Thus, we update it ...")

                    val timeOnSegment =
                        (System.currentTimeMillis() - liveSegment.math.startTime_ms) / 1000

                    // Continuous orthogonal polyline projection (REQ-GEO-001)
                    val projection = PolylineProgressTracker.projectLocation(liveSegment.staticData.path, currentLocation)
                    val distanceOnSegment = projection?.alongTrackDistance ?: (currentDistance - liveSegment.math.startDistance)
                    val distanceToSegment = projection?.crossTrackDistanceMeters ?: 0.0

                    // Smooth finish remaining distance lambda-blending (REQ-GEO-001)
                    val remainingDistance = PolylineProgressTracker.calculateBlendedRemainingDistance(
                        totalDistance = liveSegment.staticData.summary.distance_raw,
                        completedDistance = distanceOnSegment,
                        distanceToEnd = distanceToEnd,
                        finishThresholdMeters = SEGMENT_END_DISTANCE_THRESHOLD.toDouble()
                    )

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
                        val projection = PolylineProgressTracker.projectLocation(liveSegment.staticData.path, currentLocation)
                        val distanceOnSegment = projection?.alongTrackDistance ?: (currentDistance - liveSegment.math.startDistance)
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
        return PolylineProgressTracker.crossProduct(a, b, c)
    }

    /**
     * Calculates the smallest difference between two bearings (0-360).
     * Result is between 0 and 180.
     */
    private fun getBearingDifference(bearing1: Double, bearing2: Double): Double {
        return PolylineProgressTracker.getBearingDifference(bearing1, bearing2)
    }





    fun cancelScope() {
        scope.cancel()
    }

    /***********************************************************************************************
     * Companion Object
     **********************************************************************************************/

    companion object {
        val DEBUG = true
        val TAG = "LiveSegmentsRepository"

        val SEGMENT_DISTANCE_THRESHOLD = 50 // m          distance between the current location and the segment to decide whether we are on the segment
        val SEGMENT_START_DISTANCE_THRESHOLD = 250 // m   distance between the current location and the start line to show that we are approaching a segment start
        val SEGMENT_END_DISTANCE_THRESHOLD = 500 // m     distance between the current location and the finish line to show that we are approaching the finish line
        val SEGMENT_POST_END_DISTANCE_THRESHOLD = 250 // m distance after the finish line and the current location to mark this segment as far far away


        @Volatile
        private var instance: LiveSegmentsRepository? = null

        fun getInstance(context: Context): LiveSegmentsRepository {
            return instance ?: synchronized(this) {
                instance ?: LiveSegmentsRepository(context.applicationContext).also { instance = it }
            }
        }

        fun resetForTesting(newInstance: LiveSegmentsRepository? = null) {
            instance?.cancelScope()
            instance = newInstance
        }
    }
}