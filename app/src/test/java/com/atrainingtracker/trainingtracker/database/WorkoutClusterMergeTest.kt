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
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for transactional cluster merge and centroid synthesis (ATT-5666 / REQ-SET-089 / TST-SET-089).
 */
class WorkoutClusterMergeTest {

    private lateinit var mockContext: Context
    private lateinit var mockSummariesManager: WorkoutSummariesDatabaseManager
    private lateinit var mockRoutesManager: RoutesDatabaseManager
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
        every { mockContext.applicationContext } returns mockContext

        mockSummariesManager = mockk(relaxed = true)
        mockRoutesManager = mockk(relaxed = true)
        mockClusterDb = mockk(relaxed = true)
        mockSportDb = mockk(relaxed = true)

        WorkoutSummariesDatabaseManager.setInstanceForTesting(mockSummariesManager)
        RoutesDatabaseManager.resetForTesting(mockRoutesManager)
        SportTypeDatabaseManager.resetForTesting(mockSportDb)
        WorkoutClusterDatabaseManager.resetForTesting(mockClusterDb)
        WorkoutClusterEngine.resetForTesting(null)

        every { mockSportDb.getBSportType(any()) } returns BSportType.BIKE

        clusterEngine = WorkoutClusterEngine.getInstance(mockContext)
    }

    @After
    fun tearDown() {
        WorkoutClusterDatabaseManager.resetForTesting(null)
        WorkoutClusterEngine.resetForTesting(null)
        RoutesDatabaseManager.resetForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        SportTypeDatabaseManager.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testMergeClusters_weightedCentroidAndHitCountReassignment() {
        val sourceId = 1L
        val targetId = 2L

        val sourceCluster = WorkoutCluster(
            id = sourceId,
            name = "Morning Ride",
            probableSportId = 1L,
            startLat = 10.0,
            startLng = 20.0,
            endLat = 11.0,
            endLng = 21.0,
            maxDispLat = 12.0,
            maxDispLng = 22.0,
            refDistance = 10000.0,
            hitCount = 2,
            bSportType = BSportType.BIKE,
            minLat = 9.0,
            maxLat = 13.0,
            minLng = 19.0,
            maxLng = 23.0,
            previewPaths = listOf("pathA")
        )

        val targetCluster = WorkoutCluster(
            id = targetId,
            name = "Commute Home",
            probableSportId = 1L,
            startLat = 16.0,
            startLng = 26.0,
            endLat = 17.0,
            endLng = 27.0,
            maxDispLat = 18.0,
            maxDispLng = 28.0,
            refDistance = 10600.0,
            hitCount = 4,
            bSportType = BSportType.BIKE,
            minLat = 15.0,
            maxLat = 19.0,
            minLng = 25.0,
            maxLng = 29.0,
            previewPaths = listOf("pathB")
        )

        every { mockClusterDb.getClusterById(sourceId) } returns sourceCluster
        every { mockClusterDb.getClusterById(targetId) } returns targetCluster
        every { mockSummariesManager.reassignClusterWorkouts(sourceId, targetId) } returns 2
        every { mockRoutesManager.reassignClusterRoutes(sourceId, targetId) } returns 1
        every { mockSummariesManager.getMostFrequentSportIdForCluster(targetId) } returns 1L

        val result = clusterEngine.mergeClusters(mockContext, sourceId, targetId)
        assertTrue(result)

        // 1. Verify database reassignments
        verify { mockSummariesManager.reassignClusterWorkouts(sourceId, targetId) }
        verify { mockRoutesManager.reassignClusterRoutes(sourceId, targetId) }

        // 2. Verify weighted centroid synthesis
        val slot = slot<WorkoutCluster>()
        verify { mockClusterDb.updateCluster(capture(slot)) }
        val updated = slot.captured

        assertEquals(targetId, updated.id)
        assertEquals("Commute Home", updated.name)
        assertEquals(6, updated.hitCount)

        // Weight = (16.0 * 4 + 10.0 * 2) / 6 = 84 / 6 = 14.0
        assertEquals(14.0, updated.startLat, 1e-6)
        assertEquals(24.0, updated.startLng, 1e-6)
        assertEquals(15.0, updated.endLat, 1e-6)
        assertEquals(25.0, updated.endLng, 1e-6)
        assertEquals(16.0, updated.maxDispLat, 1e-6)
        assertEquals(26.0, updated.maxDispLng, 1e-6)
        // Distance = (10600 * 4 + 10000 * 2) / 6 = 62400 / 6 = 10400.0
        assertEquals(10400.0, updated.refDistance, 1e-6)

        // Bounding box union
        assertEquals(9.0, updated.minLat!!, 1e-6)
        assertEquals(19.0, updated.maxLat!!, 1e-6)
        assertEquals(19.0, updated.minLng!!, 1e-6)
        assertEquals(29.0, updated.maxLng!!, 1e-6)

        // Merged preview paths
        assertEquals(listOf("pathB", "pathA"), updated.previewPaths)

        // 3. Verify deletion of source cluster
        verify { mockClusterDb.deleteCluster(sourceId) }
    }

    @Test
    fun testMergeClusters_returnsFalseWhenClusterNotFound() {
        every { mockClusterDb.getClusterById(1L) } returns null
        every { mockClusterDb.getClusterById(2L) } returns null

        val result = clusterEngine.mergeClusters(mockContext, 1L, 2L)
        assertFalse(result)

        verify(exactly = 0) { mockSummariesManager.reassignClusterWorkouts(any(), any()) }
        verify(exactly = 0) { mockClusterDb.updateCluster(any()) }
        verify(exactly = 0) { mockClusterDb.deleteCluster(any()) }
    }
}
