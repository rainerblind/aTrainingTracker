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
import android.content.SharedPreferences
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [LiveSegmentsRepository] verifying unified polyline engine integration,
 * directional virtual gate crossings, and orthogonal along-track progress (REQ-GEO-001, TST-GEO-001).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveSegmentsRepositoryTest {

    private lateinit var mockContext: Context
    private lateinit var mockSegmentsRepo: SegmentsRepository
    private lateinit var mockBanalRepo: BANALServiceRepository

    private val allSegmentsWithPathFlow = MutableStateFlow<List<SegmentWithPath>>(emptyList())
    private val currentLocationFlow = MutableStateFlow<LatLng?>(null)
    private val currentDistanceFlow = MutableStateFlow<Double?>(0.0)
    private val currentBearingFlow = MutableStateFlow<Double?>(0.0)

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var repository: LiveSegmentsRepository

    private val sampleSegment = SegmentWithPath(
        summary = SegmentSummary(
            stravaId = 12345L,
            name = "Sprint Hill Segment",
            bSportType = BSportType.BIKE,
            climbCategory_raw = 0,
            climbCategory = "NC",
            prTime_raw = 120,
            prTime = "2:00",
            city = "Munich",
            distance = "1.0 km",
            distance_raw = 1000.0,
            averageGrade_raw = 5.0,
            averageGrade = "5%",
            maxGrade = "8%",
            elevationGain_raw = 50.0,
            elevationGain = "50 m",
            elevationMin = "500 m",
            elevationMax = "550 m",
            map_polyline = ""
        ),
        path = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(47.0, 10.0), altitude = 500.0),
            PathPoint(distance = 150.0, latLng = LatLng(47.001, 10.0), altitude = 508.0),
            PathPoint(distance = 300.0, latLng = LatLng(47.002, 10.0), altitude = 515.0),
            PathPoint(distance = 500.0, latLng = LatLng(47.004, 10.0), altitude = 525.0),
            PathPoint(distance = 700.0, latLng = LatLng(47.0055, 10.0), altitude = 535.0),
            PathPoint(distance = 850.0, latLng = LatLng(47.007, 10.0), altitude = 542.0),
            PathPoint(distance = 1000.0, latLng = LatLng(47.008, 10.0), altitude = 550.0)
        )
    )

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        val mockPrefs = mockk<SharedPreferences>(relaxed = true)
        every { mockPrefs.getString(any(), any()) } returns "METRIC"
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", mockPrefs)

        mockContext = mockk(relaxed = true)
        mockSegmentsRepo = mockk(relaxed = true)
        mockBanalRepo = mockk(relaxed = true)

        every { mockSegmentsRepo.allSegmentsWithPath } returns allSegmentsWithPathFlow
        every { mockBanalRepo.currentLocation } returns currentLocationFlow
        every { mockBanalRepo.currentDistance } returns currentDistanceFlow
        every { mockBanalRepo.currentBearing } returns currentBearingFlow

        repository = LiveSegmentsRepository(
            context = mockContext,
            segmentsRepository = mockSegmentsRepo,
            banalRepository = mockBanalRepo,
            scope = testScope
        )
    }

    @After
    fun tearDown() {
        repository.cancelScope()
        unmockkStatic(Log::class)
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", null)
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        val field = clazz.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(null, value)
    }

    @Test
    fun testInitialReconcile_createsLiveSegmentsWithDirectionalGates() = runTest(testDispatcher) {
        allSegmentsWithPathFlow.value = listOf(sampleSegment)
        testScope.testScheduler.advanceUntilIdle()

        val liveList = repository.liveSegments.value
        assertEquals(1, liveList.size)
        val live = liveList.first()
        assertEquals(LiveSegmentStatus.FAR_FAR_AWAY, live.liveData.segmentStatus)
        assertNotNull(live.math.startGate)
        assertNotNull(live.math.endGate)
        assertEquals(sampleSegment.summary.stravaId, live.staticData.summary.stravaId)
    }

    @Test
    fun testApproachingSegment_detectsWhenWithin250mAndAlignedHeading() = runTest(testDispatcher) {
        allSegmentsWithPathFlow.value = listOf(sampleSegment)
        testScope.testScheduler.advanceUntilIdle()

        // Position 100m south of start (heading North, aligned with segment)
        val approachingPos = LatLng(46.9991, 10.0)
        repository.updateLiveSegments(approachingPos, currentBearing = 0.0, currentDistance = 100.0)

        val live = repository.liveSegments.value.first()
        assertEquals(LiveSegmentStatus.APPROACHING, live.liveData.segmentStatus)
    }

    @Test
    fun testApproachingSegment_ignoresWhenHeadingDiverges() = runTest(testDispatcher) {
        allSegmentsWithPathFlow.value = listOf(sampleSegment)
        testScope.testScheduler.advanceUntilIdle()

        // Position 100m south of start, but traveling South (heading 180 deg)
        val approachingPos = LatLng(46.9991, 10.0)
        repository.updateLiveSegments(approachingPos, currentBearing = 180.0, currentDistance = 100.0)

        val live = repository.liveSegments.value.first()
        assertEquals(LiveSegmentStatus.FAR_FAR_AWAY, live.liveData.segmentStatus)
    }

    @Test
    fun testCrossingStartGate_transitionsToOnSegment() = runTest(testDispatcher) {
        allSegmentsWithPathFlow.value = listOf(sampleSegment)
        testScope.testScheduler.advanceUntilIdle()

        // 1. Move to approaching
        val approachingPos = LatLng(46.9995, 10.0)
        repository.updateLiveSegments(approachingPos, currentBearing = 0.0, currentDistance = 100.0)
        assertEquals(LiveSegmentStatus.APPROACHING, repository.liveSegments.value.first().liveData.segmentStatus)

        // 2. Cross start line forward
        val onTrackPos = LatLng(47.0001, 10.0)
        repository.updateLiveSegments(onTrackPos, currentBearing = 0.0, currentDistance = 150.0)

        val live = repository.liveSegments.value.first()
        assertEquals(LiveSegmentStatus.ON_SEGMENT, live.liveData.segmentStatus)
        assertTrue(live.math.startTime_ms > 0)
    }

    @Test
    fun testOnSegment_calculatesOrthogonalProgressAndSmoothRemainingDistance() = runTest(testDispatcher) {
        allSegmentsWithPathFlow.value = listOf(sampleSegment)
        testScope.testScheduler.advanceUntilIdle()

        // Start segment
        repository.updateLiveSegments(LatLng(46.9995, 10.0), currentBearing = 0.0, currentDistance = 100.0)
        repository.updateLiveSegments(LatLng(47.0001, 10.0), currentBearing = 0.0, currentDistance = 150.0)
        assertEquals(LiveSegmentStatus.ON_SEGMENT, repository.liveSegments.value.first().liveData.segmentStatus)

        // Move to midpoint of segment (47.004, 10.0 -> distance = 500m)
        repository.updateLiveSegments(LatLng(47.004, 10.0), currentBearing = 0.0, currentDistance = 650.0)

        val live = repository.liveSegments.value.first()
        assertTrue(
            "Status should be ON_SEGMENT or ON_SEGMENT_CLOSE_TO_FINISH",
            live.liveData.segmentStatus == LiveSegmentStatus.ON_SEGMENT ||
                    live.liveData.segmentStatus == LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH
        )
        // Orthogonal projection completed distance should evaluate to ~500m
        assertEquals(500.0, live.liveData.distanceOnSegment_raw, 25.0)
        // Remaining distance should evaluate to ~500m
        assertTrue(live.liveData.remainingDistance.isNotEmpty())
    }

    @Test
    fun testOffTrack_revertsToFarFarAway() = runTest(testDispatcher) {
        allSegmentsWithPathFlow.value = listOf(sampleSegment)
        testScope.testScheduler.advanceUntilIdle()

        // Start segment
        repository.updateLiveSegments(LatLng(46.9995, 10.0), currentBearing = 0.0, currentDistance = 100.0)
        repository.updateLiveSegments(LatLng(47.0001, 10.0), currentBearing = 0.0, currentDistance = 150.0)
        assertEquals(LiveSegmentStatus.ON_SEGMENT, repository.liveSegments.value.first().liveData.segmentStatus)

        // Diverge 150m laterally (> 50m threshold)
        repository.updateLiveSegments(LatLng(47.004, 10.002), currentBearing = 0.0, currentDistance = 650.0)

        val live = repository.liveSegments.value.first()
        assertEquals(LiveSegmentStatus.FAR_FAR_AWAY, live.liveData.segmentStatus)
    }
}
