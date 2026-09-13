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
 */

package com.atrainingtracker.trainingtracker.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit tests verifying TCX import cluster decision integrity and unclustered workout preservation
 * in accordance with REQ-MIG-025 and TST-MIG-022 (ATT-741).
 */
class WorkoutClusterImportIntegrityTest {

    private lateinit var mockContext: Context
    private lateinit var mockSummariesDb: SQLiteDatabase
    private lateinit var mockSummariesManager: WorkoutSummariesDatabaseManager
    private lateinit var mockClusterDb: WorkoutClusterDatabaseManager
    private lateinit var mockSportDb: SportTypeDatabaseManager
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
        every { TrainingApplication.useSportTypeForClustering() } returns true

        mockContext = mockk(relaxed = true)
        mockSummariesDb = mockk(relaxed = true)
        mockSummariesManager = mockk(relaxed = true)
        mockClusterDb = mockk(relaxed = true)
        mockSportDb = mockk(relaxed = true)

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        mockkStatic(SportTypeDatabaseManager::class)

        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesManager
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportDb
        every { mockSummariesManager.database } returns mockSummariesDb
        every { mockSportDb.getBSportType(any()) } returns BSportType.RUN

        val mockDiscovery = mockk<EquipmentAndSportTypeDiscoveryManager>(relaxed = true)
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(mockDiscovery)

        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<Long>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } returns Unit

        every { mockSummariesManager.getWorkoutCountForCluster(any()) } returns -1
        every { mockSummariesManager.workoutCountsForAllClusters } returns emptyMap()

        WorkoutClusterDatabaseManager.resetForTesting(mockClusterDb)
        WorkoutClusterEngine.resetForTesting(null)

        clusterEngine = WorkoutClusterEngine.getInstance(mockContext)
    }

    @After
    fun tearDown() {
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)
        WorkoutClusterEngine.resetForTesting(null)
        unmockkAll()
    }

    private fun createWorkout(
        id: Long = 100L,
        clusterId: Long = -1L,
        name: String = "2026-09-08-Run.tcx",
        finished: Boolean = true,
        startLat: Double = 48.7758,
        startLng: Double = 9.1829,
        endLat: Double = 48.7760,
        endLng: Double = 9.1835,
        totalDistance: Double = 5000.0,
        bSportType: BSportType = BSportType.RUN,
        sportId: Long = 1L
    ): WorkoutData {
        return WorkoutData(
            id = id,
            finished = finished,
            fileBaseName = name,
            workoutName = name,
            sportId = sportId,
            sportName = "Running",
            bSportType = bSportType,
            startTimeS = 1000L,
            formattedDate = "2026-09-08",
            formattedTime = "10:00",
            localDateTime = LocalDateTime.now(),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = "dummy_polyline",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = totalDistance,
            maxDisplacement = 2000.0,
            activeTimeSec = 1800L,
            totalTimeSec = 1900L,
            avgSpeedMps = 3.2,
            ascentMeters = 30L,
            descentMeters = 30L,
            minAltitude = 200.0,
            maxAltitude = 230.0,
            clusterId = clusterId,
            clusterName = null,
            startLatLng = LatLng(startLat, startLng),
            endLatLng = LatLng(endLat, endLng),
            maxDisplacementLatLng = LatLng(startLat + 0.02, startLng + 0.02),
            minLat = startLat,
            minLng = startLng,
            maxLat = startLat + 0.02,
            maxLng = startLng + 0.02,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null
        )
    }

    @Test
    fun testOnWorkoutFinished_UnclusteredWorkout_DoesNotCreateClusterOrAssign() {
        // GIVEN an imported or finished workout that is unclustered (clusterId == -1L)
        val unclusteredWorkout = createWorkout(
            id = 101L,
            clusterId = -1L,
            name = "Morning_Run_Imported"
        )

        // WHEN onWorkoutFinished is invoked on the cluster engine
        clusterEngine.onWorkoutFinished(mockContext, unclusteredWorkout)

        // THEN it MUST NOT insert a new cluster, update existing clusters, or update SQLite
        verify(exactly = 0) { mockClusterDb.insertCluster(any()) }
        verify(exactly = 0) { mockClusterDb.updateCluster(any()) }
        verify(exactly = 0) { mockSummariesDb.update(any(), any(), any(), any()) }
    }

    @Test
    fun testOnWorkoutFinished_ClusteredWorkout_UpdatesExistingClusterWithoutNewCluster() {
        // GIVEN an existing cluster in the database
        val clusterId = 55L
        val existingCluster = WorkoutCluster(
            id = clusterId,
            name = "Stuttgart Forest Loop",
            probableSportId = 1L,
            startLat = 48.7750,
            startLng = 9.1820,
            endLat = 48.7755,
            endLng = 9.1830,
            maxDispLat = 48.7950,
            maxDispLng = 9.2020,
            refDistance = 5000.0,
            hitCount = 2,
            bSportType = BSportType.RUN,
            minLat = 48.7700,
            minLng = 9.1800,
            maxLat = 48.8000,
            maxLng = 9.2100
        )
        every { mockClusterDb.getClusterById(clusterId) } returns existingCluster

        val clusteredWorkout = createWorkout(
            id = 102L,
            clusterId = clusterId,
            name = "Stuttgart Forest Loop #3",
            startLat = 48.7752,
            startLng = 9.1822,
            endLat = 48.7756,
            endLng = 9.1831,
            totalDistance = 5050.0
        )

        // WHEN onWorkoutFinished is invoked for a clustered workout
        clusterEngine.onWorkoutFinished(mockContext, clusteredWorkout)

        // THEN it updates the existing cluster's moving averages and bounds, and does NOT insert a new cluster
        verify(exactly = 0) { mockClusterDb.insertCluster(any()) }
        verify(exactly = 1) {
            mockClusterDb.updateCluster(match { updated ->
                updated.id == clusterId &&
                updated.name == "Stuttgart Forest Loop"
            })
        }
    }

    @Test
    fun testOnWorkoutSportChanged_UnclusteredWorkout_PreservesUnclusteredStatus() {
        // GIVEN an unclustered workout whose sport changed
        val oldWorkout = createWorkout(id = 103L, clusterId = -1L, bSportType = BSportType.RUN, sportId = 1L)
        val newWorkout = oldWorkout.copy(bSportType = BSportType.BIKE, sportId = 2L)

        // WHEN onWorkoutSportChanged is called
        clusterEngine.onWorkoutSportChanged(newWorkout, oldWorkout)

        // THEN it MUST NOT create or assign any cluster
        verify(exactly = 0) { mockClusterDb.insertCluster(any()) }
        verify(exactly = 0) { mockClusterDb.updateCluster(any()) }
    }
}
