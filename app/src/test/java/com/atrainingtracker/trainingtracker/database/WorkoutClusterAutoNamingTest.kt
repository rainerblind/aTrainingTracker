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
}
