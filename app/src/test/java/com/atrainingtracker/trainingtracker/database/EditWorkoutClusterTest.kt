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
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.ExtremaType
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

    @Test
    fun testGetClusterScores_reconcilesStaleHitCountAgainstWorkoutSummaries() {
        val staleClusterId = 67L
        val staleCluster = WorkoutCluster(
            id = staleClusterId,
            name = "Walk to another Stone",
            probableSportId = 1L,
            startLat = 48.0,
            startLng = 9.0,
            endLat = 48.1,
            endLng = 9.1,
            maxDispLat = 48.2,
            maxDispLng = 9.2,
            refDistance = 2500.0,
            hitCount = 2, // Stale hit count reported as 2
            bSportType = BSportType.RUN
        )

        every { mockClusterDb.getAllClusters() } returns listOf(staleCluster)
        // Authoritative count in WorkoutSummaries is actually 1
        every { mockSummariesManager.workoutCountsForAllClusters } returns mapOf(staleClusterId to 1)

        val scores = clusterEngine.getClusterScores(
            start = LatLng(48.0, 9.0),
            end = LatLng(48.1, 9.1),
            apex = LatLng(48.2, 9.2),
            distance = 2500.0
        )

        assertEquals(1, scores.size)
        val candidate = scores.first().first
        // Verify candidate hit count is healed to 1
        assertEquals(1, candidate.hitCount)

        // Verify cluster database is updated with healed hit count
        verify {
            mockClusterDb.updateCluster(match { it.id == staleClusterId && it.hitCount == 1 })
        }
    }

    @Test
    fun testAssignClusterToWorkout_usesAuthoritativeCountFromWorkoutSummaries() {
        val workoutId = 2193L
        val clusterId = 67L
        val targetCluster = WorkoutCluster(
            id = clusterId,
            name = "Walk to another Stone",
            probableSportId = 1L,
            startLat = 48.0,
            startLng = 9.0,
            endLat = 48.1,
            endLng = 9.1,
            maxDispLat = 48.2,
            maxDispLng = 9.2,
            refDistance = 2500.0,
            hitCount = 0,
            bSportType = BSportType.RUN
        )

        every { mockSummariesManager.getLong(workoutId, WorkoutSummaries.CLUSTER_ID) } returns -1L
        every { mockSummariesManager.getString(workoutId, WorkoutSummaries.MAP_POLYLINE) } returns "polyline"
        every { mockSummariesManager.getString(workoutId, WorkoutSummaries.WORKOUT_NAME) } returns "Walk"
        every { mockSummariesManager.getString(workoutId, WorkoutSummaries.FILE_BASE_NAME) } returns "track_2193"
        every { mockClusterDb.getClusterById(clusterId) } returns targetCluster
        every { mockSummariesDb.update(any(), any(), any(), any()) } returns 1
        // After inserting/updating, WorkoutSummaries has 1 row
        every { mockSummariesManager.getWorkoutCountForCluster(clusterId) } returns 1

        clusterEngine.assignClusterToWorkout(mockContext, workoutId, clusterId)

        verify {
            mockClusterDb.updateCluster(match { it.id == clusterId && it.hitCount == 1 })
        }
    }

    @Test
    fun testStripHitCount_removesSequentialCounters() {
        assertEquals("Morning Run", WorkoutClusterEngine.stripHitCount("Morning Run #4"))
        assertEquals("Morning Run", WorkoutClusterEngine.stripHitCount("Morning Run #12"))
        assertEquals("Tempo Run", WorkoutClusterEngine.stripHitCount("Tempo Run"))
        assertEquals("Route", WorkoutClusterEngine.stripHitCount("Route"))
    }

    @Test
    fun testCreateNewClusterFromWorkout_formatsClusterWorkoutName_forcesIdentity() {
        val workoutId = 102L
        val dummyWorkout = WorkoutData(
            id = workoutId,
            finished = true,
            fileBaseName = "track_102",
            workoutName = "Old Workout Name",
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

        val newClusterId = 88L
        every { mockClusterDb.getClusterByName(any()) } returns null
        every { mockClusterDb.insertCluster(any()) } returns newClusterId
        every { mockSportDb.getBSportType(2L) } returns BSportType.RUN
        val createdCluster = WorkoutCluster(
            id = newClusterId,
            name = "Forest Loop",
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
        every { mockClusterDb.getClusterById(newClusterId) } returns createdCluster
        every { mockSummariesManager.getLong(workoutId, WorkoutSummaries.CLUSTER_ID) } returns -1L
        every { mockSummariesManager.getString(workoutId, WorkoutSummaries.MAP_POLYLINE) } returns "dummy_polyline"
        every { mockSummariesManager.getString(workoutId, WorkoutSummaries.WORKOUT_NAME) } returns "Old Workout Name"
        every { mockSummariesManager.getString(workoutId, WorkoutSummaries.FILE_BASE_NAME) } returns "track_102"
        every { mockSummariesDb.update(any(), any(), any(), any()) } returns 1

        val resultId = clusterEngine.createNewClusterFromWorkout(mockContext, dummyWorkout, "Forest Loop")
        assertEquals(newClusterId, resultId)

        // Verifies that forceIdentity = true updated WORKOUT_NAME to the cluster's formatted name ("Forest Loop" for 1st hit)
        verify {
            anyConstructed<ContentValues>().put(WorkoutSummaries.WORKOUT_NAME, "Forest Loop")
            anyConstructed<ContentValues>().put(WorkoutSummaries.CLUSTER_ID, newClusterId)
        }
    }

    @Test
    fun testCreateNewClusterFromWorkout_withCustomWorkoutName_preservesCustomName() {
        val workoutId = 103L
        val dummyWorkout = WorkoutData(
            id = workoutId,
            finished = true,
            fileBaseName = "track_103",
            workoutName = "My Custom Typed Name",
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

        val newClusterId = 89L
        every { mockClusterDb.getClusterByName(any()) } returns null
        every { mockClusterDb.insertCluster(any()) } returns newClusterId
        every { mockSportDb.getBSportType(2L) } returns BSportType.RUN
        val createdCluster = WorkoutCluster(
            id = newClusterId,
            name = "Forest Loop",
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
        every { mockClusterDb.getClusterById(newClusterId) } returns createdCluster
        every { mockSummariesManager.getLong(workoutId, WorkoutSummaries.CLUSTER_ID) } returns -1L
        every { mockSummariesManager.getString(workoutId, WorkoutSummaries.MAP_POLYLINE) } returns "dummy_polyline"
        every { mockSummariesManager.getString(workoutId, WorkoutSummaries.WORKOUT_NAME) } returns "Old Name In DB"
        every { mockSummariesManager.getString(workoutId, WorkoutSummaries.FILE_BASE_NAME) } returns "track_103"
        every { mockSummariesDb.update(any(), any(), any(), any()) } returns 1

        val resultId = clusterEngine.createNewClusterFromWorkout(
            context = mockContext,
            workout = dummyWorkout,
            customName = "Forest Loop",
            customWorkoutName = "My Custom Typed Name"
        )
        assertEquals(newClusterId, resultId)

        // Verifies that customWorkoutName is preserved and written to WORKOUT_NAME
        verify {
            anyConstructed<ContentValues>().put(WorkoutSummaries.WORKOUT_NAME, "My Custom Typed Name")
            anyConstructed<ContentValues>().put(WorkoutSummaries.CLUSTER_ID, newClusterId)
        }
    }

    /**
     * TST-REV-008.1 & TST-REV-008.2: moveWorkoutToCluster Centroid and Sport Preservation Verification.
     * Verifies that moveWorkoutToCluster recomputes and preserves weighted centroids on both
     * source and target clusters without overwriting them with stale snapshots, and that
     * getMostFrequentSportIdForCluster runs after assignClusterToWorkout updates the database.
     */
    @Test
    fun testMoveWorkoutToCluster_preservesUpdatedCentroidsAndSynchronizesSport() {
        val workoutId = 99L
        val currentClusterId = 10L
        val newClusterId = 20L

        val workoutStart = LatLng(12.0, 12.0)
        val workoutEnd = LatLng(12.1, 12.1)
        val workoutApex = LatLng(12.2, 12.2)
        val workoutDistance = 6000.0

        every { mockSummariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.START) } returns workoutStart
        every { mockSummariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.END) } returns workoutEnd
        every { mockSummariesManager.getExtremaPosition(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX) } returns workoutApex
        every { mockSummariesManager.getDouble(workoutId, WorkoutSummaries.DISTANCE_TOTAL_m) } returns workoutDistance

        val oldCluster = WorkoutCluster(
            id = currentClusterId,
            name = "Old Cluster",
            probableSportId = 1L,
            startLat = 11.0,
            startLng = 11.0,
            endLat = 11.1,
            endLng = 11.1,
            maxDispLat = 11.2,
            maxDispLng = 11.2,
            refDistance = 5500.0,
            hitCount = 2,
            bSportType = BSportType.RUN
        )

        val newCluster = WorkoutCluster(
            id = newClusterId,
            name = "New Cluster",
            probableSportId = 1L,
            startLat = 10.0,
            startLng = 10.0,
            endLat = 10.1,
            endLng = 10.1,
            maxDispLat = 10.2,
            maxDispLng = 10.2,
            refDistance = 5000.0,
            hitCount = 1,
            bSportType = BSportType.RUN
        )

        val clustersDbState = mutableMapOf<Long, WorkoutCluster>()
        clustersDbState[currentClusterId] = oldCluster
        clustersDbState[newClusterId] = newCluster

        every { mockClusterDb.getClusterById(any()) } answers {
            clustersDbState[firstArg<Long>()]
        }
        every { mockClusterDb.updateCluster(any()) } answers {
            val cluster = firstArg<WorkoutCluster>()
            clustersDbState[cluster.id] = cluster
        }

        every { mockSummariesManager.getLong(workoutId, WorkoutSummaries.CLUSTER_ID) } returns currentClusterId
        every { mockSummariesManager.getString(workoutId, WorkoutSummaries.MAP_POLYLINE) } returns null
        every { mockSummariesManager.getString(workoutId, WorkoutSummaries.WORKOUT_NAME) } returns "Track 99"
        every { mockSummariesManager.getString(workoutId, WorkoutSummaries.FILE_BASE_NAME) } returns "Track 99"
        every { mockSummariesDb.update(any(), any(), any(), any()) } returns 1

        // When queried after moving, newCluster majority sport is BIKE (sportId = 2L)
        every { mockSummariesManager.getMostFrequentSportIdForCluster(newClusterId) } returns 2L
        every { mockSummariesManager.getMostFrequentSportIdForCluster(currentClusterId) } returns 1L
        every { mockSportDb.getBSportType(2L) } returns BSportType.BIKE
        every { mockSportDb.getBSportType(1L) } returns BSportType.RUN

        // Execute move
        clusterEngine.moveWorkoutToCluster(mockContext, workoutId, currentClusterId, newClusterId)

        // Verify final state of both clusters
        val finalOld = clustersDbState[currentClusterId]!!
        val finalNew = clustersDbState[newClusterId]!!

        // Old cluster: (11.0 * 2 - 12.0) / 1 = 10.0
        assertEquals(10.0, finalOld.startLat, 0.0001)
        assertEquals(10.0, finalOld.startLng, 0.0001)
        assertEquals(10.1, finalOld.endLat, 0.0001)
        assertEquals(10.1, finalOld.endLng, 0.0001)
        assertEquals(10.2, finalOld.maxDispLat, 0.0001)
        assertEquals(10.2, finalOld.maxDispLng, 0.0001)
        assertEquals(5000.0, finalOld.refDistance, 0.0001)
        assertEquals(1, finalOld.hitCount)
        assertEquals(1L, finalOld.probableSportId)
        assertEquals(BSportType.RUN, finalOld.bSportType)

        // New cluster: (10.0 * 1 + 12.0) / 2 = 11.0
        assertEquals(11.0, finalNew.startLat, 0.0001)
        assertEquals(11.0, finalNew.startLng, 0.0001)
        assertEquals(11.1, finalNew.endLat, 0.0001)
        assertEquals(11.1, finalNew.endLng, 0.0001)
        assertEquals(11.2, finalNew.maxDispLat, 0.0001)
        assertEquals(11.2, finalNew.maxDispLng, 0.0001)
        assertEquals(5500.0, finalNew.refDistance, 0.0001)
        assertEquals(2, finalNew.hitCount)
        assertEquals(2L, finalNew.probableSportId)
        assertEquals(BSportType.BIKE, finalNew.bSportType)
    }
}
