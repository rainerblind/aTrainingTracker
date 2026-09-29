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

import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Verification test suite for REQ-UI-186.1 and TST-UI-139.1 (ATT-1402):
 * Mandatory Starting Location Invariant & Automated Location Seeding in WorkoutClusterEngine.
 */
class WorkoutClusterEngineLocationSeedTest {

    private lateinit var mockContext: Context
    private lateinit var mockClusterDb: WorkoutClusterDatabaseManager
    private lateinit var mockSportDb: SportTypeDatabaseManager
    private lateinit var mockKnownLocationsDb: KnownLocationsDatabaseManager
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
        mockClusterDb = mockk(relaxed = true)
        mockSportDb = mockk(relaxed = true)
        mockKnownLocationsDb = mockk(relaxed = true)

        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportDb
        every { mockSportDb.getBSportType(any()) } returns BSportType.RUN

        mockkStatic(KnownLocationsDatabaseManager::class)
        every { KnownLocationsDatabaseManager.getInstance(any()) } returns mockKnownLocationsDb

        WorkoutClusterDatabaseManager.resetForTesting(mockClusterDb)
        WorkoutClusterEngine.resetForTesting(null)

        clusterEngine = WorkoutClusterEngine.getInstance(mockContext)
    }

    @After
    fun tearDown() {
        WorkoutClusterDatabaseManager.resetForTesting(null)
        WorkoutClusterEngine.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun `learnFromWorkout creates cluster and seeds known location when starting coordinate is unmapped`() {
        val start = LatLng(48.137, 11.576)
        val end = LatLng(48.140, 11.580)
        val apex = LatLng(48.145, 11.585)
        val distance = 5000.0
        val sportId = 1L

        every { mockClusterDb.getClusterById(any()) } returns null
        every { mockClusterDb.getClusterByName(any()) } returns null
        every { mockClusterDb.getAllClusters() } returns emptyList()
        every { mockClusterDb.insertCluster(any()) } returns 101L

        // No existing location at starting coordinate
        every { mockKnownLocationsDb.getMyLocation(start) } returns null

        val clusterId = clusterEngine.learnFromWorkout(
            start = start,
            end = end,
            apex = apex,
            distance = distance,
            userSpecifiedName = "Trailhead Run",
            userSportId = sportId
        )

        assertEquals(101L, clusterId)
        verify(exactly = 1) { mockClusterDb.insertCluster(any()) }
        // Verify automated location seeding was triggered
        verify(exactly = 1) { mockKnownLocationsDb.recordWorkoutStart(start, null) }
    }

    @Test
    fun `learnFromWorkout creates cluster and preserves existing known location without re-seeding`() {
        val start = LatLng(48.137, 11.576)
        val end = LatLng(48.140, 11.580)
        val apex = LatLng(48.145, 11.585)
        val distance = 5000.0
        val sportId = 1L

        every { mockClusterDb.getClusterById(any()) } returns null
        every { mockClusterDb.getClusterByName(any()) } returns null
        every { mockClusterDb.getAllClusters() } returns emptyList()
        every { mockClusterDb.insertCluster(any()) } returns 102L

        // Existing location found at start coordinate
        val existingLoc = KnownLocationsDatabaseManager.MyLocation(
            1L, start.latitude, start.longitude, "Zuhause", 520.0, 200, 10
        )
        every { mockKnownLocationsDb.getMyLocation(start) } returns existingLoc

        val clusterId = clusterEngine.learnFromWorkout(
            start = start,
            end = end,
            apex = apex,
            distance = distance,
            userSpecifiedName = "Zuhause Loop",
            userSportId = sportId
        )

        assertEquals(102L, clusterId)
        verify(exactly = 1) { mockClusterDb.insertCluster(any()) }
        // Verify recordWorkoutStart was NOT invoked since location already exists
        verify(exactly = 0) { mockKnownLocationsDb.recordWorkoutStart(any(), any()) }
    }
}
