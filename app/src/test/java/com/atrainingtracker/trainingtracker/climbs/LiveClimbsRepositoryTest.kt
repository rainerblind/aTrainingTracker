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

package com.atrainingtracker.trainingtracker.climbs

import android.content.Context
import android.location.Location
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.database.ClimbsDatabaseManager
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.*

/**
 * Unit tests verifying real-time tracking, state transitions, and climb telemetry
 * in [LiveClimbsRepository] (REQ-MAP-027, TST-MAP-029 Group 3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveClimbsRepositoryTest {

    private lateinit var mockContext: Context
    private lateinit var mockBanalRepo: BANALServiceRepository
    private lateinit var mockRoutesRepo: RoutesRepository
    private lateinit var mockClimbsDb: ClimbsDatabaseManager

    private val currentLocationFlow = MutableStateFlow<LatLng?>(null)
    private val currentBearingFlow = MutableStateFlow<Double?>(0.0)
    private val activeNavigatedRouteIdFlow = MutableStateFlow<Long?>(null)

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var repository: LiveClimbsRepository

    private val sampleClimb = Climb(
        id = 1L,
        name = "Test Alp",
        routeId = 100L,
        startLat = 47.0,
        startLng = 10.0,
        endLat = 47.01,
        endLng = 10.0,
        distanceMeters = 1000.0,
        elevationGainMeters = 100.0,
        avgGradePercent = 10.0,
        maxGradePercent = 12.0,
        category = ClimbCategory.CAT_4,
        pathPoints = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(47.0, 10.0), altitude = 500.0),
            PathPoint(distance = 500.0, latLng = LatLng(47.005, 10.0), altitude = 550.0),
            PathPoint(distance = 1000.0, latLng = LatLng(47.01, 10.0), altitude = 600.0)
        )
    )

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockBanalRepo = mockk(relaxed = true)
        mockRoutesRepo = mockk(relaxed = true)
        mockClimbsDb = mockk(relaxed = true)

        every { mockBanalRepo.currentLocation } returns currentLocationFlow
        every { mockBanalRepo.currentBearing } returns currentBearingFlow
        every { mockRoutesRepo.activeNavigatedRouteId } returns activeNavigatedRouteIdFlow

        mockkStatic(Location::class)
        every { Location.distanceBetween(any(), any(), any(), any(), any()) } answers {
            val startLat = arg<Double>(0)
            val startLng = arg<Double>(1)
            val endLat = arg<Double>(2)
            val endLng = arg<Double>(3)
            val results = arg<FloatArray>(4)
            // Equirectangular approximation for testing
            val latDiff = (endLat - startLat) * 111139.0
            val lngDiff = (endLng - startLng) * 111139.0 * cos(Math.toRadians(startLat))
            results[0] = sqrt(latDiff * latDiff + lngDiff * lngDiff).toFloat()
        }

        mockkStatic(SphericalUtil::class)
        every { SphericalUtil.computeHeading(any(), any()) } returns 0.0 // North heading

        coEvery { mockClimbsDb.getAllClimbs() } returns listOf(sampleClimb)
        coEvery { mockClimbsDb.getClimbsForRoute(any()) } returns listOf(sampleClimb)

        // Instantiate repository using reflection or test constructor
        val constructor = LiveClimbsRepository::class.java.getDeclaredConstructor(
            Context::class.java,
            BANALServiceRepository::class.java,
            RoutesRepository::class.java,
            ClimbsDatabaseManager::class.java,
            kotlinx.coroutines.CoroutineScope::class.java
        )
        constructor.isAccessible = true
        repository = constructor.newInstance(
            mockContext,
            mockBanalRepo,
            mockRoutesRepo,
            mockClimbsDb,
            testScope
        )
    }

    @After
    fun tearDown() {
        repository.cancelScope()
        unmockkStatic(Location::class)
        unmockkStatic(SphericalUtil::class)
    }

    @Test
    fun testApproachingClimb_detectsClimbWithinThresholdAndAlignedHeading() = runTest(testDispatcher) {
        repository.refreshClimbs(null)
        assertEquals(1, repository.allClimbs.value.size)

        // Position 100 meters south of start, heading North (0 deg)
        // 1 deg lat ~ 111139m -> 100m ~ 0.0009 deg
        val athletePos = LatLng(47.0 - 0.0009, 10.0)
        repository.updateTracking(athletePos, bearing = 0.0)

        val live = repository.activeLiveClimb.value
        assertNotNull("Should detect approaching climb", live)
        assertEquals(LiveClimbStatus.APPROACHING, live!!.status)
        assertTrue("Distance to start should be ~100m", live.distanceToStart in 80.0..120.0)
    }

    @Test
    fun testApproachingClimb_ignoresWhenHeadingDivergesBeyond45Degrees() = runTest(testDispatcher) {
        repository.refreshClimbs(null)

        // Position 100 meters south of start, but heading South (180 deg)
        val athletePos = LatLng(47.0 - 0.0009, 10.0)
        repository.updateTracking(athletePos, bearing = 180.0)

        val live = repository.activeLiveClimb.value
        assertNull("Should ignore climb when heading diverges beyond 45 deg", live)
    }

    @Test
    fun testStartGate_transitionsToOnClimbWhenWithin30Meters() = runTest(testDispatcher) {
        repository.refreshClimbs(null)

        // Position 10 meters from start
        val athletePos = LatLng(47.0 - 0.00008, 10.0)
        repository.updateTracking(athletePos, bearing = 0.0)

        val live = repository.activeLiveClimb.value
        assertNotNull(live)
        assertEquals(LiveClimbStatus.ON_CLIMB, live!!.status)
        assertEquals(0.0, live.distanceToStart, 0.01)
    }

    @Test
    fun testOnClimb_calculatesProgressAndRemainingElevationCorrectly() = runTest(testDispatcher) {
        repository.refreshClimbs(null)

        // First enter climb
        repository.updateTracking(LatLng(47.0, 10.0), bearing = 0.0)
        assertEquals(LiveClimbStatus.ON_CLIMB, repository.activeLiveClimb.value?.status)

        // Move to middle point (500m along climb, altitude 550m, summit is 600m)
        repository.updateTracking(LatLng(47.005, 10.0), bearing = 0.0)

        val live = repository.activeLiveClimb.value
        assertNotNull(live)
        assertEquals(LiveClimbStatus.ON_CLIMB, live!!.status)
        assertEquals(0.5f, live.currentProgressFraction, 0.05f)
        assertEquals(500.0, live.distanceToSummit, 50.0)
        assertEquals(50.0, live.remainingElevationGain, 5.0)
    }

    @Test
    fun testSummitGate_transitionsToFinished() = runTest(testDispatcher) {
        repository.refreshClimbs(null)

        // Start climb
        repository.updateTracking(LatLng(47.0, 10.0), bearing = 0.0)
        assertEquals(LiveClimbStatus.ON_CLIMB, repository.activeLiveClimb.value?.status)

        // Reach summit (within 35m of summit: 47.01, 10.0)
        repository.updateTracking(LatLng(47.01, 10.0), bearing = 0.0)

        val live = repository.activeLiveClimb.value
        assertNotNull(live)
        assertEquals(LiveClimbStatus.FINISHED, live!!.status)
        assertEquals(0.0, live.distanceToSummit, 0.01)
        assertEquals(0.0, live.remainingElevationGain, 0.01)
        assertEquals(1.0f, live.currentProgressFraction, 0.01f)
    }

    @Test
    fun testOffRoute_clearsActiveClimbWhenRiderDeviatesBeyond100Meters() = runTest(testDispatcher) {
        repository.refreshClimbs(null)

        // Start climb
        repository.updateTracking(LatLng(47.0, 10.0), bearing = 0.0)
        assertEquals(LiveClimbStatus.ON_CLIMB, repository.activeLiveClimb.value?.status)

        // Move 500m east off-route (lng diff ~ 0.005 deg)
        val offRoutePos = LatLng(47.005, 10.008)
        repository.updateTracking(offRoutePos, bearing = 0.0)

        val live = repository.activeLiveClimb.value
        assertNull("Should clear climb when off-route beyond 100m", live)
    }

    @Test
    fun testRouteContext_providesRouteClimbIndexAndTotal() = runTest(testDispatcher) {
        activeNavigatedRouteIdFlow.value = 100L
        repository.refreshClimbs(100L)

        repository.updateTracking(LatLng(47.0, 10.0), bearing = 0.0)

        val live = repository.activeLiveClimb.value
        assertNotNull(live)
        assertEquals(1, live!!.routeIndex)
        assertEquals(1, live.totalRouteClimbs)
    }
}
