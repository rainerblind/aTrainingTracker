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

package com.atrainingtracker.trainingtracker.database

import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Verifies geometric apex resolution, arithmetic running mean maintenance,
 * and deletion reversal for route clusters (REQ-SET-063, TST-SET-049, ATT-1138).
 */
class WorkoutClusterApexTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockDbManager = mockk<WorkoutClusterDatabaseManager>(relaxed = true)
    private val mockSportTypeDb = mockk<SportTypeDatabaseManager>(relaxed = true)
    private lateinit var clusterEngine: WorkoutClusterEngine

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getClusterTolEndpoints() } returns 200f
        every { TrainingApplication.getClusterTolApex() } returns 400f
        every { TrainingApplication.getClusterTolDistance() } returns 0.20f
        every { TrainingApplication.useSportTypeForClustering() } returns false
        every { TrainingApplication.useAltitudePosForClustering() } returns false

        mockkObject(WorkoutClusterDatabaseManager.Companion)
        every { WorkoutClusterDatabaseManager.getInstance(any()) } returns mockDbManager

        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportTypeDb
        every { mockSportTypeDb.getBSportType(any()) } returns BSportType.RUN

        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getString(any()) } returns "Cluster #%d"
        every { mockContext.getString(any(), any()) } returns "Cluster"

        WorkoutClusterEngine.resetForTesting(null)
        clusterEngine = WorkoutClusterEngine.getInstance(mockContext)
    }

    @After
    fun tearDown() {
        WorkoutClusterEngine.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testFindApexFromPoints_CurvedRouteSelectsMaxDisplacementOnTrack() {
        // Topology modeled after "Run to work (Uni)" from ATT-498
        val rohrStart = LatLng(48.715, 9.105)
        val rohrerHoehe = LatLng(48.710, 9.080)
        val rosental = LatLng(48.725, 9.075)
        val lauchaecker = LatLng(48.735, 9.085)
        val nobelstrasse = LatLng(48.740, 9.095)
        val campusVaihingenEnd = LatLng(48.745, 9.100)
        val dachswaldOffTrack = LatLng(48.735, 9.115) // Erroneous centroid location

        val trackPoints = listOf(
            rohrStart,
            rohrerHoehe,
            rosental,
            lauchaecker,
            nobelstrasse,
            campusVaihingenEnd
        )

        val resolvedApex = clusterEngine.findApexFromPoints(rohrStart, trackPoints)

        // The apex MUST be on the track and specifically at Campus Vaihingen (furthest from Rohr)
        assertEquals(campusVaihingenEnd, resolvedApex)
        assertNotEquals(dachswaldOffTrack, resolvedApex)
        assertTrue("Resolved apex must be an existing point on the track", trackPoints.contains(resolvedApex))
    }

    @Test
    fun testFindApexFromPoints_OutAndBackRoute() {
        val start = LatLng(48.700, 9.100)
        val waypoint1 = LatLng(48.710, 9.105)
        val turnaroundApex = LatLng(48.730, 9.120) // Furthest point
        val waypoint2 = LatLng(48.715, 9.102)
        val end = LatLng(48.701, 9.101)

        val track = listOf(start, waypoint1, turnaroundApex, waypoint2, end)

        val resolvedApex = clusterEngine.findApexFromPoints(start, track)
        assertEquals(turnaroundApex, resolvedApex)
    }

    @Test
    fun testFindApexFromPoints_SinglePointOrEmptyFallback() {
        val start = LatLng(48.700, 9.100)
        assertEquals(start, clusterEngine.findApexFromPoints(start, emptyList()))

        val singlePoint = LatLng(48.750, 9.200)
        assertEquals(singlePoint, clusterEngine.findApexFromPoints(start, listOf(singlePoint)))
    }

    @Test
    fun testOnWorkoutDeleted_reversesArithmeticMeanOfApex() {
        // Initial cluster with 3 member workouts and mean apex (48.020, 9.020)
        val cluster = WorkoutCluster(
            id = 42L,
            name = "Test Cluster",
            probableSportId = 1L,
            startLat = 48.0,
            startLng = 9.0,
            endLat = 48.0,
            endLng = 9.0,
            maxDispLat = 48.020,
            maxDispLng = 9.020,
            refDistance = 10000.0,
            hitCount = 3,
            bSportType = BSportType.RUN,
            minLat = 47.0,
            maxLat = 49.0,
            minLng = 8.0,
            maxLng = 10.0
        )

        every { mockDbManager.getClusterById(42L) } returns cluster
        val slot = slot<WorkoutCluster>()
        every { mockDbManager.updateCluster(capture(slot)) } just Runs

        // Delete workout with apex (48.030, 9.030)
        val workoutToDelete = mockk<WorkoutData>(relaxed = true)
        every { workoutToDelete.id } returns 103L
        every { workoutToDelete.clusterId } returns 42L
        every { workoutToDelete.startLatLng } returns LatLng(48.0, 9.0)
        every { workoutToDelete.endLatLng } returns LatLng(48.0, 9.0)
        every { workoutToDelete.maxDisplacementLatLng } returns LatLng(48.030, 9.030)
        every { workoutToDelete.totalDistance } returns 10000.0
        every { workoutToDelete.minLat } returns 47.5
        every { workoutToDelete.maxLat } returns 48.5
        every { workoutToDelete.minLng } returns 8.5
        every { workoutToDelete.maxLng } returns 9.5

        clusterEngine.onWorkoutDeleted(mockContext, workoutToDelete)

        verify(exactly = 1) { mockDbManager.updateCluster(any()) }
        val updated = slot.captured
        assertEquals(2, updated.hitCount)
        // Expected apex reversed: (48.020 * 3 - 48.030) / 2 = 48.015
        assertEquals(48.015, updated.maxDispLat, 0.0001)
        assertEquals(9.015, updated.maxDispLng, 0.0001)
    }

    @Test
    fun testLearnFromWorkout_maintainsRunningArithmeticMeanOfApex() {
        val existingCluster = WorkoutCluster(
            id = 10L,
            name = "Campus Loop",
            probableSportId = 1L,
            startLat = 48.700,
            startLng = 9.100,
            endLat = 48.700,
            endLng = 9.100,
            maxDispLat = 48.7050,
            maxDispLng = 9.1050,
            refDistance = 5000.0,
            hitCount = 1,
            bSportType = BSportType.RUN
        )

        every { mockDbManager.getAllClusters() } returns listOf(existingCluster)
        every { mockDbManager.getClusterById(10L) } returns existingCluster
        val slot = slot<WorkoutCluster>()
        every { mockDbManager.updateCluster(capture(slot)) } just Runs

        // Second matching workout with apex (48.7070, 9.1070) - ~220m from cluster apex (< 400m tolerance)
        val newApex = LatLng(48.7070, 9.1070)
        clusterEngine.learnFromWorkout(
            start = LatLng(48.700, 9.100),
            end = LatLng(48.700, 9.100),
            apex = newApex,
            distance = 5000.0,
            userSpecifiedName = "Campus Loop",
            userSportId = 1L
        )

        verify(atLeast = 1) { mockDbManager.updateCluster(any()) }
        val updated = slot.captured
        // Mean apex: (48.7050 * 1 + 48.7070) / 2 = 48.7060
        assertEquals(48.7060, updated.maxDispLat, 0.0001)
        assertEquals(9.1060, updated.maxDispLng, 0.0001)
    }

    @Test
    fun testLearnFromRoute_doesNotOverwriteAccumulatedApexMean() {
        val existingCluster = WorkoutCluster(
            id = 10L,
            name = "Campus Loop",
            probableSportId = 1L,
            startLat = 48.700,
            startLng = 9.100,
            endLat = 48.700,
            endLng = 9.100,
            maxDispLat = 48.7050,
            maxDispLng = 9.1050,
            refDistance = 5000.0,
            hitCount = 2,
            bSportType = BSportType.RUN
        )

        every { mockDbManager.getAllClusters() } returns listOf(existingCluster)
        every { mockDbManager.getClusterById(10L) } returns existingCluster
        val slot = slot<WorkoutCluster>()
        every { mockDbManager.updateCluster(capture(slot)) } just Runs

        val routePoints = listOf(
            PathPoint(altitude = 0.0, latLng = LatLng(48.700, 9.100), distance = 0.0),
            PathPoint(altitude = 0.0, latLng = LatLng(48.7080, 9.1080), distance = 2500.0),
            PathPoint(altitude = 0.0, latLng = LatLng(48.700, 9.100), distance = 5000.0)
        )
        val route = RouteWithPath(
            summary = RouteSummary(
                id = 5L,
                externalId = "ext-5",
                name = "Campus Loop",
                description = "",
                isSelected = false,
                distance = 5000.0,
                elevationGain = 0.0,
                bSportType = BSportType.RUN,
                source = RouteSource.LOCAL_GPX,
                clusterId = 10L
            ),
            path = routePoints
        )

        clusterEngine.learnFromRoute(route)

        // The cluster should update as running average via learnFromWorkout:
        // (48.7050 * 2 + 48.7080) / 3 = 48.7060
        // It must NOT be forcibly overwritten to route's apex (48.7080)
        verify(atLeast = 1) { mockDbManager.updateCluster(any()) }
        val updated = slot.captured
        assertEquals(48.7060, updated.maxDispLat, 0.0001)
        assertEquals(9.1060, updated.maxDispLng, 0.0001)
    }
}
