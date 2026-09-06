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
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit tests verifying explicit workout cluster management (unassign, create new, assign)
 * in accordance with REQ-SET-059 and TST-SET-050 (ATT-318, ATT-666).
 */
class EditWorkoutClusterTest {

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

        val mockDiscovery = mockk<EquipmentAndSportTypeDiscoveryManager>(relaxed = true)
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(mockDiscovery)

        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<Long>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } returns Unit

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

    @Test
    fun testUnassignClusterFromWorkout_decrementsOldClusterHitCountAndClearsClusterId() {
        val workoutId = 42L
        val oldClusterId = 10L
        val oldCluster = WorkoutCluster(
            id = oldClusterId,
            name = "Test Route",
            probableSportId = 1L,
            startLat = 48.0,
            startLng = 9.0,
            endLat = 48.1,
            endLng = 9.1,
            maxDispLat = 48.2,
            maxDispLng = 9.2,
            refDistance = 5000.0,
            hitCount = 3,
            bSportType = BSportType.RUN,
            previewPaths = listOf("path1", "path2")
        )

        every { mockSummariesManager.getLong(workoutId, WorkoutSummaries.CLUSTER_ID) } returns oldClusterId
        every { mockClusterDb.getClusterById(oldClusterId) } returns oldCluster
        every { mockSummariesDb.update(any(), any(), any(), any()) } returns 1

        clusterEngine.unassignClusterFromWorkout(mockContext, workoutId)

        // Verify old cluster updated with hitCount decremented to 2
        val clusterSlot = slot<WorkoutCluster>()
        verify { mockClusterDb.updateCluster(capture(clusterSlot)) }
        assertEquals(2, clusterSlot.captured.hitCount)
        assertEquals(listOf("path1", "path2"), clusterSlot.captured.previewPaths)

        // Verify SQLite database updated with CLUSTER_ID = -1L
        verify {
            anyConstructed<ContentValues>().put(WorkoutSummaries.CLUSTER_ID, -1L)
            mockSummariesDb.update(
                WorkoutSummaries.TABLE,
                any(),
                "${WorkoutSummaries.C_ID} = ?",
                arrayOf(workoutId.toString())
            )
        }
    }

    @Test
    fun testUnassignClusterFromWorkout_hitCountDropsToZero_clearsPreviewPaths() {
        val workoutId = 42L
        val oldClusterId = 10L
        val oldCluster = WorkoutCluster(
            id = oldClusterId,
            name = "Single Workout Route",
            probableSportId = 1L,
            startLat = 48.0,
            startLng = 9.0,
            endLat = 48.1,
            endLng = 9.1,
            maxDispLat = 48.2,
            maxDispLng = 9.2,
            refDistance = 5000.0,
            hitCount = 1,
            bSportType = BSportType.RUN,
            previewPaths = listOf("path1")
        )

        every { mockSummariesManager.getLong(workoutId, WorkoutSummaries.CLUSTER_ID) } returns oldClusterId
        every { mockClusterDb.getClusterById(oldClusterId) } returns oldCluster
        every { mockSummariesDb.update(any(), any(), any(), any()) } returns 1

        clusterEngine.unassignClusterFromWorkout(mockContext, workoutId)

        val clusterSlot = slot<WorkoutCluster>()
        verify { mockClusterDb.updateCluster(capture(clusterSlot)) }
        assertEquals(0, clusterSlot.captured.hitCount)
        assertTrue(clusterSlot.captured.previewPaths.isEmpty())
    }

    @Test
    fun testAssignClusterToWorkout_negativeOrZeroClusterId_delegatesToUnassign() {
        val workoutId = 42L
        val oldClusterId = 10L
        val oldCluster = WorkoutCluster(
            id = oldClusterId,
            name = "Test Route",
            probableSportId = 1L,
            startLat = 48.0,
            startLng = 9.0,
            endLat = 48.1,
            endLng = 9.1,
            maxDispLat = 48.2,
            maxDispLng = 9.2,
            refDistance = 5000.0,
            hitCount = 2,
            bSportType = BSportType.RUN
        )

        every { mockSummariesManager.getLong(workoutId, WorkoutSummaries.CLUSTER_ID) } returns oldClusterId
        every { mockClusterDb.getClusterById(oldClusterId) } returns oldCluster
        every { mockSummariesDb.update(any(), any(), any(), any()) } returns 1

        clusterEngine.assignClusterToWorkout(mockContext, workoutId, -1L)

        // Verify update to CLUSTER_ID = -1L
        verify {
            anyConstructed<ContentValues>().put(WorkoutSummaries.CLUSTER_ID, -1L)
            mockSummariesDb.update(
                WorkoutSummaries.TABLE,
                any(),
                "${WorkoutSummaries.C_ID} = ?",
                arrayOf(workoutId.toString())
            )
        }
    }

    @Test
    fun testCreateNewClusterFromWorkout_insertsNewClusterAndAssignsWorkout() {
        val workoutId = 101L
        val dummyWorkout = WorkoutData(
            id = workoutId,
            finished = true,
            fileBaseName = "track_101",
            workoutName = "Evening Lake Run",
            sportId = 2L,
            sportName = "Running",
            bSportType = BSportType.RUN,
            startTimeS = 1000L,
            formattedDate = "2026-09-06",
            formattedTime = "18:00",
            localDateTime = LocalDateTime.now(),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = "dummy_polyline",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 8500.0,
            maxDisplacement = 3200.0,
            activeTimeSec = 2400L,
            totalTimeSec = 2500L,
            avgSpeedMps = 3.5,
            ascentMeters = 50L,
            descentMeters = 50L,
            minAltitude = 200.0,
            maxAltitude = 250.0,
            startLatLng = LatLng(48.5, 9.2),
            endLatLng = LatLng(48.51, 9.21),
            maxDisplacementLatLng = LatLng(48.55, 9.25),
            description = null,
            goal = null,
            method = null,
            stravaSportName = null,
            clusterId = -1L,
            clusterName = null
        )

        val newGeneratedClusterId = 77L
        every { mockClusterDb.getClusterByName(any()) } returns null
        every { mockClusterDb.insertCluster(any()) } returns newGeneratedClusterId
        every { mockSportDb.getBSportType(2L) } returns BSportType.RUN

        val createdCluster = WorkoutCluster(
            id = newGeneratedClusterId,
            name = "My Lake Route",
            probableSportId = 2L,
            startLat = 48.5,
            startLng = 9.2,
            endLat = 48.51,
            endLng = 9.21,
            maxDispLat = 48.55,
            maxDispLng = 9.25,
            refDistance = 8500.0,
            hitCount = 0,
            bSportType = BSportType.RUN
        )
        every { mockClusterDb.getClusterById(newGeneratedClusterId) } returns createdCluster
        every { mockSummariesManager.getLong(workoutId, WorkoutSummaries.CLUSTER_ID) } returns -1L
        every { mockSummariesManager.getString(workoutId, WorkoutSummaries.MAP_POLYLINE) } returns "dummy_polyline"
        every { mockSummariesManager.getString(workoutId, WorkoutSummaries.WORKOUT_NAME) } returns "Evening Lake Run"
        every { mockSummariesManager.getString(workoutId, WorkoutSummaries.FILE_BASE_NAME) } returns "track_101"
        every { mockSummariesDb.update(any(), any(), any(), any()) } returns 1

        val resultId = clusterEngine.createNewClusterFromWorkout(mockContext, dummyWorkout, "My Lake Route")

        assertEquals(newGeneratedClusterId, resultId)
        val insertedSlot = slot<WorkoutCluster>()
        verify { mockClusterDb.insertCluster(capture(insertedSlot)) }
        assertEquals("My Lake Route", insertedSlot.captured.name)
        assertEquals(8500.0, insertedSlot.captured.refDistance, 0.001)

        // Verify cluster hitCount is incremented during assignment
        verify {
            mockClusterDb.updateCluster(match { it.id == newGeneratedClusterId && it.hitCount == 1 })
        }
    }
}
