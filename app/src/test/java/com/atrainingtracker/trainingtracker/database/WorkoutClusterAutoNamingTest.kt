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
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Verification test suite for REQ-SET-066 and TST-SET-053 (ATT-785):
 * Workout Cluster Sequential Auto-Naming Counter Ergonomics.
 *
 * Validates that the initial session of a cluster receives the clean cluster name
 * without any "#1" suffix, while subsequent sessions (count >= 2) receive sequential
 * numbered suffixes ("#2", "#3", etc.).
 */
class WorkoutClusterAutoNamingTest {

    private lateinit var mockContext: Context
    private lateinit var mockSummariesDb: SQLiteDatabase
    private lateinit var mockSummariesManager: WorkoutSummariesDatabaseManager
    private lateinit var mockClusterDb: WorkoutClusterDatabaseManager
    private lateinit var mockSportDb: SportTypeDatabaseManager
    private lateinit var clusterEngine: WorkoutClusterEngine
    private val capturedValuesMap = mutableMapOf<String, Any?>()

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
        every {
            mockContext.getString(R.string.cluster_autoname_format, *anyVararg())
        } answers {
            val formatArgs = args[1] as Array<*>
            val name = formatArgs[0]
            val count = formatArgs[1]
            "$name #$count"
        }

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

        capturedValuesMap.clear()
        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<Long>()) } answers {
            capturedValuesMap[firstArg()] = secondArg<Long>()
        }
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } answers {
            capturedValuesMap[firstArg()] = secondArg<String>()
        }
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } answers {
            capturedValuesMap[firstArg()] = secondArg<Int>()
        }
        every { anyConstructed<ContentValues>().getAsString(any()) } answers {
            capturedValuesMap[firstArg()] as? String
        }
        every { anyConstructed<ContentValues>().getAsLong(any()) } answers {
            capturedValuesMap[firstArg()] as? Long
        }

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
    fun testFormatClusterWorkoutName_initialHitCount_omitsSuffix() {
        // Count 0 (unlinked/new cluster)
        val name0 = WorkoutClusterEngine.formatClusterWorkoutName(mockContext, "Morning Run", 0)
        assertEquals("Morning Run", name0)

        // Count 1 (first workout in cluster)
        val name1 = WorkoutClusterEngine.formatClusterWorkoutName(mockContext, "Lake Tahoe Loop", 1)
        assertEquals("Lake Tahoe Loop", name1)
    }

    @Test
    fun testFormatClusterWorkoutName_subsequentHitCount_appendsSuffix() {
        // Count 2 (second workout)
        val name2 = WorkoutClusterEngine.formatClusterWorkoutName(mockContext, "Morning Run", 2)
        assertEquals("Morning Run #2", name2)

        // Count 5 (fifth workout)
        val name5 = WorkoutClusterEngine.formatClusterWorkoutName(mockContext, "Lake Tahoe Loop", 5)
        assertEquals("Lake Tahoe Loop #5", name5)
    }

    @Test
    fun testAssignClusterToWorkout_initialSession_setsBaseNameWithoutSuffix() {
        val workoutId = 101L
        val clusterId = 201L
        val initialCluster = WorkoutCluster(
            id = clusterId,
            name = "Chiemsee-Runde",
            probableSportId = 1L,
            startLat = 47.85,
            startLng = 12.45,
            endLat = 47.85,
            endLng = 12.45,
            maxDispLat = 47.90,
            maxDispLng = 12.50,
            refDistance = 15000.0,
            hitCount = 0,
            bSportType = BSportType.BIKE
        )

        every { mockSummariesManager.getLong(workoutId, WorkoutSummaries.CLUSTER_ID) } returns -1L
        every { mockSummariesManager.getString(workoutId, WorkoutSummaries.WORKOUT_NAME) } returns ""
        every { mockSummariesManager.getString(workoutId, WorkoutSummaries.FILE_BASE_NAME) } returns "track_101"
        every { mockSummariesManager.getString(workoutId, WorkoutSummaries.MAP_POLYLINE) } returns "polyline_101"
        every { mockClusterDb.getClusterById(clusterId) } returns initialCluster
        every { mockSummariesManager.getWorkoutCountForCluster(clusterId) } returns 1
        every { mockSummariesDb.update(WorkoutSummaries.TABLE, any(), any(), any()) } returns 1

        clusterEngine.assignClusterToWorkout(mockContext, workoutId, clusterId)

        // First workout must NOT have "#1"
        assertEquals("Chiemsee-Runde", capturedValuesMap[WorkoutSummaries.WORKOUT_NAME])
        assertEquals(clusterId, capturedValuesMap[WorkoutSummaries.CLUSTER_ID])
    }

    @Test
    fun testAssignClusterToWorkout_subsequentSession_appendsSuffix() {
        val workoutId = 102L
        val clusterId = 201L
        val existingCluster = WorkoutCluster(
            id = clusterId,
            name = "Chiemsee-Runde",
            probableSportId = 1L,
            startLat = 47.85,
            startLng = 12.45,
            endLat = 47.85,
            endLng = 12.45,
            maxDispLat = 47.90,
            maxDispLng = 12.50,
            refDistance = 15000.0,
            hitCount = 1,
            bSportType = BSportType.BIKE
        )

        every { mockSummariesManager.getLong(workoutId, WorkoutSummaries.CLUSTER_ID) } returns -1L
        every { mockSummariesManager.getString(workoutId, WorkoutSummaries.WORKOUT_NAME) } returns ""
        every { mockSummariesManager.getString(workoutId, WorkoutSummaries.FILE_BASE_NAME) } returns "track_102"
        every { mockSummariesManager.getString(workoutId, WorkoutSummaries.MAP_POLYLINE) } returns "polyline_102"
        every { mockClusterDb.getClusterById(clusterId) } returns existingCluster
        every { mockSummariesManager.getWorkoutCountForCluster(clusterId) } returns 2
        every { mockSummariesDb.update(WorkoutSummaries.TABLE, any(), any(), any()) } returns 1

        clusterEngine.assignClusterToWorkout(mockContext, workoutId, clusterId)

        // Second workout MUST have "#2"
        assertEquals("Chiemsee-Runde #2", capturedValuesMap[WorkoutSummaries.WORKOUT_NAME])
        assertEquals(clusterId, capturedValuesMap[WorkoutSummaries.CLUSTER_ID])
    }

    @Test
    fun testNormalizationAndHitCountStripping_handlesBaseNameAndNumberedNameIdentically() {
        // Access private functions stripHitCount and normalizeName via reflection
        val stripHitCountMethod = WorkoutClusterEngine::class.java.getDeclaredMethod("stripHitCount", String::class.java).apply {
            isAccessible = true
        }
        val normalizeNameMethod = WorkoutClusterEngine::class.java.getDeclaredMethod("normalizeName", String::class.java).apply {
            isAccessible = true
        }

        // 1. stripHitCount
        val strippedBase = stripHitCountMethod.invoke(clusterEngine, "Morning Run") as String
        val strippedNumbered = stripHitCountMethod.invoke(clusterEngine, "Morning Run #2") as String
        val strippedOne = stripHitCountMethod.invoke(clusterEngine, "Morning Run #1") as String
        assertEquals("Morning Run", strippedBase)
        assertEquals("Morning Run", strippedNumbered)
        assertEquals("Morning Run", strippedOne)

        // 2. normalizeName
        val normalizedBase = normalizeNameMethod.invoke(clusterEngine, "Morning Run") as String
        val normalizedNumbered = normalizeNameMethod.invoke(clusterEngine, "Morning Run #2") as String
        val normalizedOne = normalizeNameMethod.invoke(clusterEngine, "Morning Run #1") as String
        val normalizedVar = normalizeNameMethod.invoke(clusterEngine, "Morning Run var 2") as String
        assertEquals("morning run", normalizedBase)
        assertEquals("morning run", normalizedNumbered)
        assertEquals("morning run", normalizedOne)
        assertEquals("morning run", normalizedVar)
    }

    /**
     * TST-PER-049.1: formatClusterWorkoutName strips leading/trailing whitespace so double spaces
     * like "Kurze Runde  #2" cannot occur (REQ-PER-041.1).
     */
    @Test
    fun testFormatClusterWorkoutName_trimsLeadingAndTrailingWhitespace_TST_PER_049_1() {
        assertEquals(
            "Kurze Runde",
            WorkoutClusterEngine.formatClusterWorkoutName(mockContext, "  Kurze Runde  ", 1)
        )
        assertEquals(
            "Kurze Runde #2",
            WorkoutClusterEngine.formatClusterWorkoutName(mockContext, "Kurze Runde ", 2)
        )
    }

    /**
     * TST-PER-049.1: manuallyCreateCluster and syncRouteNameChange normalize
     * leading/trailing spaces, resolve collisions, and reject blank-only names (REQ-PER-041.1).
     */
    @Test
    fun testManuallyCreateAndSyncRoute_trimsAndRejectsBlank_TST_PER_049_1() {
        val mockSportRepo = mockk<SportTypesRepository>(relaxed = true)
        every { mockSportRepo.getBSportType(any()) } returns BSportType.BIKE
        SportTypesRepository.resetForTesting(mockSportRepo)

        try {
            val pos = LatLng(48.1, 11.5)
            assertEquals(-1L, clusterEngine.manuallyCreateCluster("   ", 1L, pos, pos, pos, 5000.0))
            verify(exactly = 0) { mockClusterDb.insertCluster(any()) }

            every { mockClusterDb.isNameTaken("Kurze Runde", -1L) } returns true
            every { mockClusterDb.isNameTaken("Kurze Runde var 2", -1L) } returns false
            every { mockClusterDb.insertCluster(any()) } returns 77L
            val createdId = clusterEngine.manuallyCreateCluster(
                "  Kurze Runde  ",
                1L,
                pos,
                pos,
                pos,
                5000.0
            )
            assertEquals(77L, createdId)
            verify { mockClusterDb.insertCluster(match { it.name == "Kurze Runde var 2" }) }

            val cluster77 = WorkoutCluster(
                id = 77L, name = "Kurze Runde var 2", probableSportId = 1L,
                startLat = 48.1, startLng = 11.5, endLat = 48.1, endLng = 11.5,
                maxDispLat = 48.1, maxDispLng = 11.5, refDistance = 5000.0,
                hitCount = 1, bSportType = BSportType.BIKE
            )
            every { mockClusterDb.getClusterById(77L) } returns cluster77
            every { mockClusterDb.isNameTaken("Isar Loop", 77L) } returns false

            clusterEngine.syncRouteNameChange(77L, "  \t ")
            verify(exactly = 0) { mockClusterDb.updateCluster(any()) }

            clusterEngine.syncRouteNameChange(77L, "  Isar Loop  ")
            verify(exactly = 1) { mockClusterDb.updateCluster(match { it.name == "Isar Loop" }) }
        } finally {
            SportTypesRepository.resetForTesting(null)
        }
    }

    /**
     * TST-PER-049.1: splitCluster skips blank-only variants and trims valid names (REQ-PER-041.1).
     */
    @Test
    fun testSplitCluster_trimsVariantsAndSkipsBlank_TST_PER_049_1() {
        val mockSportRepo = mockk<SportTypesRepository>(relaxed = true)
        every { mockSportRepo.getBSportType(any()) } returns BSportType.BIKE
        SportTypesRepository.resetForTesting(mockSportRepo)
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
        WorkoutRepository.resetForTesting(mockWorkoutRepo)

        try {
            val pos = LatLng(48.1, 11.5)
            val cluster77 = WorkoutCluster(
                id = 77L, name = "Main Loop", probableSportId = 1L,
                startLat = 48.1, startLng = 11.5, endLat = 48.1, endLng = 11.5,
                maxDispLat = 48.1, maxDispLng = 11.5, refDistance = 5000.0,
                hitCount = 1, bSportType = BSportType.BIKE
            )
            every { mockClusterDb.getClusterById(77L) } returns cluster77
            val splitWorkout = mockk<WorkoutData>(relaxed = true) {
                every { id } returns 501L
                every { startLatLng } returns pos
                every { endLatLng } returns pos
                every { maxDisplacementLatLng } returns pos
                every { totalDistance } returns 5000.0
                every { sportId } returns 1L
                every { startTimeS } returns 1000L
            }
            every { mockWorkoutRepo.runInTransaction(any<() -> Any?>()) } answers {
                firstArg<() -> Any?>().invoke()
            }
            every { mockClusterDb.isNameTaken("Variant Nord", -1L) } returns false
            every { mockClusterDb.insertCluster(match { it.name == "Variant Nord" }) } returns 88L

            val splitIds = clusterEngine.splitCluster(
                context = mockContext,
                primaryClusterId = 77L,
                variantsToSplit = listOf(
                    "   " to listOf(splitWorkout),
                    "  Variant Nord  " to listOf(splitWorkout)
                ),
                renameWorkouts = true
            )
            assertEquals(listOf(88L), splitIds)
            verify { mockClusterDb.insertCluster(match { it.name == "Variant Nord" }) }
        } finally {
            WorkoutRepository.resetForTesting(null)
            SportTypesRepository.resetForTesting(null)
        }
    }
}
