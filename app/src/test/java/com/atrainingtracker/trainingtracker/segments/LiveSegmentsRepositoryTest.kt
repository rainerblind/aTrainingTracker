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

    @Test
    fun testFinished_populatesDistancePastFinishAndPrDelta_withoutOverloadingOffset() = runTest(testDispatcher) {
        val segmentWithPr = sampleSegment.copy(
            summary = sampleSegment.summary.copy(prTime_raw = 229, prTime = "03:49")
        )
        allSegmentsWithPathFlow.value = listOf(segmentWithPr)
        testScope.testScheduler.advanceUntilIdle()

        // 1. Approach & start segment
        repository.updateLiveSegments(LatLng(46.9995, 10.0), currentBearing = 0.0, currentDistance = 100.0)
        repository.updateLiveSegments(LatLng(47.0001, 10.0), currentBearing = 0.0, currentDistance = 150.0)
        assertEquals(LiveSegmentStatus.ON_SEGMENT, repository.liveSegments.value.first().liveData.segmentStatus)

        // 2. Approach finish line
        repository.updateLiveSegments(LatLng(47.0075, 10.0), currentBearing = 0.0, currentDistance = 950.0)
        assertEquals(LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH, repository.liveSegments.value.first().liveData.segmentStatus)

        // 3. Set start time to simulate 219s duration (PR is 229s -> delta is -10s)
        repository.updateLiveSegmentsForTesting { list ->
            list.map { it.copy(math = it.math.copy(startTime_ms = System.currentTimeMillis() - 219_000L)) }
        }

        // 4. Cross finish gate forward
        repository.updateLiveSegments(LatLng(47.0082, 10.0), currentBearing = 0.0, currentDistance = 1020.0)

        val liveFinished = repository.liveSegments.value.first()
        assertEquals(LiveSegmentStatus.FINISHED, liveFinished.liveData.segmentStatus)

        // Verify distancePastFinish is populated and segmentOffset is not overloaded
        assertTrue("distancePastFinish_raw should be > 0", liveFinished.liveData.distancePastFinish_raw > 0.0)
        assertNotEquals("--", liveFinished.liveData.distancePastFinish)
        assertEquals("--", liveFinished.liveData.segmentOffset)
        assertEquals(0.0, liveFinished.liveData.segmentOffset_raw, 0.001)

        // Verify remainingDistance is reset to 0
        assertEquals(0.0, liveFinished.liveData.remainingDistance_raw, 0.001)

        // Verify PR delta: 219s - 229s = -10s -> "-0:10"
        assertEquals(-10L, liveFinished.liveData.prDeltaSeconds)
        assertEquals("-0:10", liveFinished.liveData.prDeltaFormatted)

        // Subsequent update while in FINISHED updates distancePastFinish without touching offset
        repository.updateLiveSegments(LatLng(47.0085, 10.0), currentBearing = 0.0, currentDistance = 1050.0)
        val liveAfterRollout = repository.liveSegments.value.first()
        assertEquals(LiveSegmentStatus.FINISHED, liveAfterRollout.liveData.segmentStatus)
        assertTrue(liveAfterRollout.liveData.distancePastFinish_raw > liveFinished.liveData.distancePastFinish_raw)
        assertEquals("--", liveAfterRollout.liveData.segmentOffset)
        assertEquals(0.0, liveAfterRollout.liveData.segmentOffset_raw, 0.001)
    }

    @Test
    fun testFinished_prDelta_exactTie() = runTest(testDispatcher) {
        val segmentTie = sampleSegment.copy(summary = sampleSegment.summary.copy(prTime_raw = 229))
        allSegmentsWithPathFlow.value = listOf(segmentTie)
        testScope.testScheduler.advanceUntilIdle()

        repository.updateLiveSegments(LatLng(46.9995, 10.0), currentBearing = 0.0, currentDistance = 100.0)
        repository.updateLiveSegments(LatLng(47.0001, 10.0), currentBearing = 0.0, currentDistance = 150.0)
        repository.updateLiveSegments(LatLng(47.0075, 10.0), currentBearing = 0.0, currentDistance = 950.0)
        repository.updateLiveSegmentsForTesting { list ->
            list.map { it.copy(math = it.math.copy(startTime_ms = System.currentTimeMillis() - 229_000L)) }
        }
        repository.updateLiveSegments(LatLng(47.0082, 10.0), currentBearing = 0.0, currentDistance = 1020.0)

        val finishedTie = repository.liveSegments.value.first()
        assertEquals(LiveSegmentStatus.FINISHED, finishedTie.liveData.segmentStatus)
        assertEquals(0L, finishedTie.liveData.prDeltaSeconds)
        assertEquals("-0:00", finishedTie.liveData.prDeltaFormatted)
    }

    @Test
    fun testFinished_prDelta_slowerThanPr() = runTest(testDispatcher) {
        val segmentTie = sampleSegment.copy(summary = sampleSegment.summary.copy(prTime_raw = 229))
        allSegmentsWithPathFlow.value = listOf(segmentTie)
        testScope.testScheduler.advanceUntilIdle()

        repository.updateLiveSegments(LatLng(46.9995, 10.0), currentBearing = 0.0, currentDistance = 100.0)
        repository.updateLiveSegments(LatLng(47.0001, 10.0), currentBearing = 0.0, currentDistance = 150.0)
        repository.updateLiveSegments(LatLng(47.0075, 10.0), currentBearing = 0.0, currentDistance = 950.0)
        repository.updateLiveSegmentsForTesting { list ->
            list.map { it.copy(math = it.math.copy(startTime_ms = System.currentTimeMillis() - 234_000L)) }
        }
        repository.updateLiveSegments(LatLng(47.0082, 10.0), currentBearing = 0.0, currentDistance = 1020.0)

        val finishedSlower = repository.liveSegments.value.first()
        assertEquals(LiveSegmentStatus.FINISHED, finishedSlower.liveData.segmentStatus)
        assertEquals(5L, finishedSlower.liveData.prDeltaSeconds)
        assertEquals("+0:05", finishedSlower.liveData.prDeltaFormatted)
    }

    @Test
    fun testFinished_prDelta_extremeOutlier() = runTest(testDispatcher) {
        val segmentTie = sampleSegment.copy(summary = sampleSegment.summary.copy(prTime_raw = 229))
        allSegmentsWithPathFlow.value = listOf(segmentTie)
        testScope.testScheduler.advanceUntilIdle()

        repository.updateLiveSegments(LatLng(46.9995, 10.0), currentBearing = 0.0, currentDistance = 100.0)
        repository.updateLiveSegments(LatLng(47.0001, 10.0), currentBearing = 0.0, currentDistance = 150.0)
        repository.updateLiveSegments(LatLng(47.0075, 10.0), currentBearing = 0.0, currentDistance = 950.0)
        repository.updateLiveSegmentsForTesting { list ->
            list.map { it.copy(math = it.math.copy(startTime_ms = System.currentTimeMillis() - 3829_000L)) }
        }
        repository.updateLiveSegments(LatLng(47.0082, 10.0), currentBearing = 0.0, currentDistance = 1020.0)

        val finishedOutlier = repository.liveSegments.value.first()
        assertEquals(LiveSegmentStatus.FINISHED, finishedOutlier.liveData.segmentStatus)
        assertEquals(3600L, finishedOutlier.liveData.prDeltaSeconds)
        assertEquals("+60:00", finishedOutlier.liveData.prDeltaFormatted)
    }

    @Test
    fun testFinished_prDelta_missingPr() = runTest(testDispatcher) {
        val segmentNoPr = sampleSegment.copy(summary = sampleSegment.summary.copy(prTime_raw = 0, prTime = "--:--"))
        allSegmentsWithPathFlow.value = listOf(segmentNoPr)
        testScope.testScheduler.advanceUntilIdle()

        repository.updateLiveSegments(LatLng(46.9995, 10.0), currentBearing = 0.0, currentDistance = 100.0)
        repository.updateLiveSegments(LatLng(47.0001, 10.0), currentBearing = 0.0, currentDistance = 150.0)
        repository.updateLiveSegments(LatLng(47.0075, 10.0), currentBearing = 0.0, currentDistance = 950.0)
        repository.updateLiveSegmentsForTesting { list ->
            list.map { it.copy(math = it.math.copy(startTime_ms = System.currentTimeMillis() - 219_000L)) }
        }
        repository.updateLiveSegments(LatLng(47.0082, 10.0), currentBearing = 0.0, currentDistance = 1020.0)

        val finishedNoPr = repository.liveSegments.value.first()
        assertEquals(LiveSegmentStatus.FINISHED, finishedNoPr.liveData.segmentStatus)
        assertNull(finishedNoPr.liveData.prDeltaSeconds)
        assertNull(finishedNoPr.liveData.prDeltaFormatted)
    }

    @Test
    fun testLiveSegment_lastSegmentIndex_propagatesAndAdvancesOnSegment() = runTest(testDispatcher) {
        allSegmentsWithPathFlow.value = listOf(sampleSegment)
        testScope.testScheduler.advanceUntilIdle()

        // 1. Approach and cross start gate
        repository.updateLiveSegments(LatLng(46.9995, 10.0), currentBearing = 0.0, currentDistance = 100.0)
        assertEquals(LiveSegmentStatus.APPROACHING, repository.liveSegments.value.first().liveData.segmentStatus)

        repository.updateLiveSegments(LatLng(47.0001, 10.0), currentBearing = 0.0, currentDistance = 150.0)
        val onSegment = repository.liveSegments.value.first()
        assertEquals(LiveSegmentStatus.ON_SEGMENT, onSegment.liveData.segmentStatus)
        assertEquals("lastSegmentIndex should be initialized to 0 on start crossing", 0, onSegment.math.lastSegmentIndex)

        // 2. Advance to segment 2 (near lat 47.003, halfway between 47.002 and 47.004)
        repository.updateLiveSegments(LatLng(47.003, 10.0), currentBearing = 0.0, currentDistance = 400.0)
        val advanced = repository.liveSegments.value.first()
        assertEquals(LiveSegmentStatus.ON_SEGMENT, advanced.liveData.segmentStatus)
        assertEquals("lastSegmentIndex should advance to 2", 2, advanced.math.lastSegmentIndex)

        // 3. Advance to segment 4 (near lat 47.006, halfway between 47.0055 and 47.007)
        repository.updateLiveSegments(LatLng(47.006, 10.0), currentBearing = 0.0, currentDistance = 780.0)
        val advancedFurther = repository.liveSegments.value.first()
        assertEquals(4, advancedFurther.math.lastSegmentIndex)
    }

    @Test
    fun testLiveSegment_offSegmentDeviation_resetsLastSegmentIndexToNull() = runTest(testDispatcher) {
        allSegmentsWithPathFlow.value = listOf(sampleSegment)
        testScope.testScheduler.advanceUntilIdle()

        // Start segment
        repository.updateLiveSegments(LatLng(46.9995, 10.0), currentBearing = 0.0, currentDistance = 100.0)
        repository.updateLiveSegments(LatLng(47.0001, 10.0), currentBearing = 0.0, currentDistance = 150.0)
        assertEquals(0, repository.liveSegments.value.first().math.lastSegmentIndex)

        // Deviate > 60m laterally (longitude offset at lat 47.002)
        val lngOffset60m = 60.0 / (111139.0 * kotlin.math.cos(Math.toRadians(47.0)))
        repository.updateLiveSegments(LatLng(47.002, 10.0 + lngOffset60m), currentBearing = 0.0, currentDistance = 350.0)

        val offSegment = repository.liveSegments.value.first()
        assertEquals(LiveSegmentStatus.FAR_FAR_AWAY, offSegment.liveData.segmentStatus)
        assertNull("lastSegmentIndex should reset to null on FAR_FAR_AWAY", offSegment.math.lastSegmentIndex)
    }

    @Test
    fun testLiveSegment_finish_resetsLastSegmentIndexToNull() = runTest(testDispatcher) {
        allSegmentsWithPathFlow.value = listOf(sampleSegment)
        testScope.testScheduler.advanceUntilIdle()

        // Start -> Close to finish -> Finish
        repository.updateLiveSegments(LatLng(46.9995, 10.0), currentBearing = 0.0, currentDistance = 100.0)
        repository.updateLiveSegments(LatLng(47.0001, 10.0), currentBearing = 0.0, currentDistance = 150.0)
        repository.updateLiveSegments(LatLng(47.0075, 10.0), currentBearing = 0.0, currentDistance = 950.0)
        repository.updateLiveSegmentsForTesting { list ->
            list.map { it.copy(math = it.math.copy(startTime_ms = System.currentTimeMillis() - 200_000L)) }
        }
        repository.updateLiveSegments(LatLng(47.0082, 10.0), currentBearing = 0.0, currentDistance = 1020.0)

        val finished = repository.liveSegments.value.first()
        assertEquals(LiveSegmentStatus.FINISHED, finished.liveData.segmentStatus)
        assertNull("lastSegmentIndex must reset to null on segment finish", finished.math.lastSegmentIndex)
    }

    @Test
    fun testFarFarAwaySegments_zeroPerTickLogcatFlood_across60Segments() = runTest(testDispatcher) {
        // Seed 60 saved segments ~450 km away from athlete location (REQ-LIV-006, TST-LIV-002)
        val distantSegments = (1..60).map { idx ->
            val latBase = 51.0 + idx * 0.01
            sampleSegment.copy(
                summary = sampleSegment.summary.copy(
                    stravaId = 90000L + idx,
                    name = "Distant Segment #$idx"
                ),
                path = listOf(
                    PathPoint(distance = 0.0, latLng = LatLng(latBase, 10.0), altitude = 500.0),
                    PathPoint(distance = 200.0, latLng = LatLng(latBase + 0.001, 10.0), altitude = 510.0),
                    PathPoint(distance = 400.0, latLng = LatLng(latBase + 0.002, 10.0), altitude = 520.0),
                    PathPoint(distance = 600.0, latLng = LatLng(latBase + 0.004, 10.0), altitude = 530.0),
                    PathPoint(distance = 800.0, latLng = LatLng(latBase + 0.006, 10.0), altitude = 540.0),
                    PathPoint(distance = 1000.0, latLng = LatLng(latBase + 0.008, 10.0), altitude = 550.0)
                )
            )
        }
        allSegmentsWithPathFlow.value = distantSegments
        testScope.testScheduler.advanceUntilIdle()
        assertEquals(60, repository.liveSegments.value.size)

        // Simulate 20 consecutive 1-Hz GNSS ticks far away from all 60 segments
        repeat(20) { tick ->
            repository.updateLiveSegments(
                currentLocation = LatLng(47.0 + tick * 0.0001, 10.0),
                currentBearing = 0.0,
                currentDistance = tick * 10.0
            )
        }

        assertTrue(
            "All 60 distant segments must remain FAR_FAR_AWAY",
            repository.liveSegments.value.all { it.liveData.segmentStatus == LiveSegmentStatus.FAR_FAR_AWAY }
        )
        verify(exactly = 0) { Log.v(any<String>(), any<String>()) }
        verify(exactly = 0) { Log.d(any<String>(), any<String>()) }
        verify(exactly = 0) { Log.i(any<String>(), any<String>()) }
        verify(exactly = 0) { Log.w(any<String>(), any<String>()) }
        verify(exactly = 0) { Log.e(any<String>(), any<String>()) }
    }

    @Test
    fun testStatusTransitions_logOnlyOnActualStatusChanges_neverOnSteadyStateTicks() = runTest(testDispatcher) {
        allSegmentsWithPathFlow.value = listOf(sampleSegment)
        testScope.testScheduler.advanceUntilIdle()

        // 5 steady-state FAR_FAR_AWAY ticks -> 0 logs
        repeat(5) { tick ->
            repository.updateLiveSegments(LatLng(46.90 + tick * 0.0001, 10.0), currentBearing = 0.0, currentDistance = tick * 10.0)
        }
        verify(exactly = 0) { Log.i(any<String>(), any<String>()) }

        // Transition 1: FAR_FAR_AWAY -> APPROACHING (+1 log), followed by 3 steady-state APPROACHING ticks (+0 logs)
        repository.updateLiveSegments(LatLng(46.9990, 10.0), currentBearing = 0.0, currentDistance = 100.0)
        repository.updateLiveSegments(LatLng(46.9992, 10.0), currentBearing = 0.0, currentDistance = 120.0)
        repository.updateLiveSegments(LatLng(46.9994, 10.0), currentBearing = 0.0, currentDistance = 140.0)
        repository.updateLiveSegments(LatLng(46.9996, 10.0), currentBearing = 0.0, currentDistance = 160.0)
        assertEquals(LiveSegmentStatus.APPROACHING, repository.liveSegments.value.first().liveData.segmentStatus)
        verify(exactly = 1) { Log.i(LiveSegmentsRepository.TAG, any<String>()) }

        // Transition 2: APPROACHING -> ON_SEGMENT (+1 log), followed by 3 steady-state ON_SEGMENT ticks (>500m from end, +0 logs)
        repository.updateLiveSegments(LatLng(47.0001, 10.0), currentBearing = 0.0, currentDistance = 210.0)
        repository.updateLiveSegments(LatLng(47.0010, 10.0), currentBearing = 0.0, currentDistance = 310.0)
        repository.updateLiveSegments(LatLng(47.0020, 10.0), currentBearing = 0.0, currentDistance = 420.0)
        repository.updateLiveSegments(LatLng(47.0030, 10.0), currentBearing = 0.0, currentDistance = 530.0)
        assertEquals(LiveSegmentStatus.ON_SEGMENT, repository.liveSegments.value.first().liveData.segmentStatus)
        verify(exactly = 2) { Log.i(LiveSegmentsRepository.TAG, any<String>()) }

        // Transition 3: ON_SEGMENT -> ON_SEGMENT_CLOSE_TO_FINISH (+1 log)
        repository.updateLiveSegments(LatLng(47.0075, 10.0), currentBearing = 0.0, currentDistance = 950.0)
        assertEquals(LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH, repository.liveSegments.value.first().liveData.segmentStatus)
        verify(exactly = 3) { Log.i(LiveSegmentsRepository.TAG, any<String>()) }

        // Transition 4: ON_SEGMENT_CLOSE_TO_FINISH -> FINISHED (+1 log), followed by 2 steady-state FINISHED ticks (+0 logs)
        repository.updateLiveSegments(LatLng(47.0082, 10.0), currentBearing = 0.0, currentDistance = 1020.0)
        repository.updateLiveSegments(LatLng(47.0085, 10.0), currentBearing = 0.0, currentDistance = 1050.0)
        repository.updateLiveSegments(LatLng(47.0088, 10.0), currentBearing = 0.0, currentDistance = 1080.0)
        assertEquals(LiveSegmentStatus.FINISHED, repository.liveSegments.value.first().liveData.segmentStatus)
        verify(exactly = 4) { Log.i(LiveSegmentsRepository.TAG, any<String>()) }

        // Transition 5: FINISHED -> FAR_FAR_AWAY (+1 log)
        repository.updateLiveSegments(LatLng(47.0110, 10.0), currentBearing = 0.0, currentDistance = 1350.0)
        assertEquals(LiveSegmentStatus.FAR_FAR_AWAY, repository.liveSegments.value.first().liveData.segmentStatus)
        verify(exactly = 5) { Log.i(LiveSegmentsRepository.TAG, any<String>()) }
    }
}
