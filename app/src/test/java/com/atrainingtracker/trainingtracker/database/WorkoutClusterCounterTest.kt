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
 * Comprehensive verification test suite for REQ-SET-067 and TST-SET-067 (ATT-714 / ATT-808):
 * Optional Activity Counter for Workout Clusters.
 *
 * Validates:
 * 1. formatClusterWorkoutName logic when hasCounter is true vs false across hit counts.
 * 2. WorkoutCluster data model hasCounter defaults and mutations.
 * 3. assignClusterToWorkout name formatting behavior when hasCounter is toggled.
 */
class WorkoutClusterCounterTest {

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

    // --- TST-SET-067.1: formatClusterWorkoutName with Counter Enabled vs Disabled ---

    @Test
    fun formatClusterWorkoutName_whenCounterEnabled_hitCount1_returnsBaseName() {
        val result = WorkoutClusterEngine.formatClusterWorkoutName(mockContext, "Lake Loop", 1, hasCounter = true)
        assertEquals("Lake Loop", result)
    }

    @Test
    fun formatClusterWorkoutName_whenCounterEnabled_hitCount2_returnsNumberedName() {
        val result = WorkoutClusterEngine.formatClusterWorkoutName(mockContext, "Lake Loop", 2, hasCounter = true)
        assertEquals("Lake Loop #2", result)
    }

    @Test
    fun formatClusterWorkoutName_whenCounterEnabled_hitCount10_returnsNumberedName() {
        val result = WorkoutClusterEngine.formatClusterWorkoutName(mockContext, "Lake Loop", 10, hasCounter = true)
        assertEquals("Lake Loop #10", result)
    }

    @Test
    fun formatClusterWorkoutName_whenCounterDisabled_hitCount1_returnsBaseName() {
        val result = WorkoutClusterEngine.formatClusterWorkoutName(mockContext, "Lake Loop", 1, hasCounter = false)
        assertEquals("Lake Loop", result)
    }

    @Test
    fun formatClusterWorkoutName_whenCounterDisabled_hitCount2_returnsBaseNameWithoutCounter() {
        val result = WorkoutClusterEngine.formatClusterWorkoutName(mockContext, "Lake Loop", 2, hasCounter = false)
        assertEquals("Lake Loop", result)
    }

    @Test
    fun formatClusterWorkoutName_whenCounterDisabled_hitCount15_returnsBaseNameWithoutCounter() {
        val result = WorkoutClusterEngine.formatClusterWorkoutName(mockContext, "Mountain Trail", 15, hasCounter = false)
        assertEquals("Mountain Trail", result)
    }

    @Test
    fun formatClusterWorkoutName_defaultParameter_preservesBackwardCompatibilityWithCounterEnabled() {
        val resultCount1 = WorkoutClusterEngine.formatClusterWorkoutName(mockContext, "Park Run", 1)
        assertEquals("Park Run", resultCount1)

        val resultCount3 = WorkoutClusterEngine.formatClusterWorkoutName(mockContext, "Park Run", 3)
        assertEquals("Park Run #3", resultCount3)
    }

    // --- TST-SET-067.2: Data Model Default & State Verification ---

    @Test
    fun workoutClusterModel_hasCounterDefaultsToTrue() {
        val cluster = WorkoutCluster(
            name = "Default Counter Cluster",
            probableSportId = 1L,
            startLat = 48.0,
            startLng = 11.0,
            endLat = 48.0,
            endLng = 11.0,
            maxDispLat = 48.01,
            maxDispLng = 11.01,
            refDistance = 5000.0,
            hitCount = 5
        )
        assertTrue(cluster.hasCounter)
    }

    @Test
    fun workoutClusterModel_hasCounterCanBeExplicitlyFalse() {
        val cluster = WorkoutCluster(
            name = "No Counter Cluster",
            probableSportId = 1L,
            startLat = 48.0,
            startLng = 11.0,
            endLat = 48.0,
            endLng = 11.0,
            maxDispLat = 48.01,
            maxDispLng = 11.01,
            refDistance = 5000.0,
            hitCount = 5,
            hasCounter = false
        )
        assertFalse(cluster.hasCounter)
    }

    // --- TST-SET-067.3: Engine assignClusterToWorkout Integration with hasCounter ---

    @Test
    fun assignClusterToWorkout_whenCounterEnabled_assignsNumberedNameOnSubsequentHits() {
        val cluster = WorkoutCluster(
            id = 42L,
            name = "Chiemsee-Runde",
            probableSportId = 2L,
            startLat = 48.0,
            startLng = 11.0,
            endLat = 48.0,
            endLng = 11.0,
            maxDispLat = 48.05,
            maxDispLng = 11.05,
            refDistance = 12000.0,
            hitCount = 3,
            bSportType = BSportType.BIKE,
            hasCounter = true
        )
        every { mockClusterDb.getClusterById(42L) } returns cluster
        every { mockSummariesManager.getLong(100L, WorkoutSummaries.CLUSTER_ID) } returns -1L
        every { mockSummariesManager.getString(100L, WorkoutSummaries.WORKOUT_NAME) } returns "2026-09-09_Run"
        every { mockSummariesManager.getString(100L, WorkoutSummaries.FILE_BASE_NAME) } returns "2026-09-09_Run"

        clusterEngine.assignClusterToWorkout(mockContext, 100L, 42L)

        assertEquals("Chiemsee-Runde #4", capturedValuesMap[WorkoutSummaries.WORKOUT_NAME])
        assertEquals(42L, capturedValuesMap[WorkoutSummaries.CLUSTER_ID])
    }

    @Test
    fun assignClusterToWorkout_whenCounterDisabled_assignsCleanNameWithoutSuffixOnSubsequentHits() {
        val cluster = WorkoutCluster(
            id = 43L,
            name = "Chiemsee-Runde",
            probableSportId = 2L,
            startLat = 48.0,
            startLng = 11.0,
            endLat = 48.0,
            endLng = 11.0,
            maxDispLat = 48.05,
            maxDispLng = 11.05,
            refDistance = 12000.0,
            hitCount = 3,
            bSportType = BSportType.BIKE,
            hasCounter = false
        )
        every { mockClusterDb.getClusterById(43L) } returns cluster
        every { mockSummariesManager.getLong(101L, WorkoutSummaries.CLUSTER_ID) } returns -1L
        every { mockSummariesManager.getString(101L, WorkoutSummaries.WORKOUT_NAME) } returns "2026-09-09_Run"
        every { mockSummariesManager.getString(101L, WorkoutSummaries.FILE_BASE_NAME) } returns "2026-09-09_Run"

        clusterEngine.assignClusterToWorkout(mockContext, 101L, 43L)

        assertEquals("Chiemsee-Runde", capturedValuesMap[WorkoutSummaries.WORKOUT_NAME])
        assertEquals(43L, capturedValuesMap[WorkoutSummaries.CLUSTER_ID])
    }
}
