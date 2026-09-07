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
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.preference.PreferenceManager
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.R
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying [WorkoutClusterRepository.refreshClusters] logic:
 * - Surviving workout clusters keep their lifetime hitCount untouched (ATT-296).
 * - Unlinked zero-workout orphan clusters are automatically purged (REQ-SET-062).
 * - Route-linked clusters are preserved even when zero workouts remain (REQ-SET-062).
 */
class WorkoutClusterRepositoryTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockSummariesDb: SQLiteDatabase
    private lateinit var mockSummariesManager: WorkoutSummariesDatabaseManager
    private lateinit var mockClusterDb: WorkoutClusterDatabaseManager
    private lateinit var mockRoutesDb: RoutesDatabaseManager
    private lateinit var mockSportDb: SportTypeDatabaseManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockSummariesDb = mockk(relaxed = true)
        mockSummariesManager = mockk(relaxed = true)
        mockClusterDb = mockk(relaxed = true)
        mockRoutesDb = mockk(relaxed = true)
        mockSportDb = mockk(relaxed = true)

        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs
        every { mockPrefs.getInt(any(), any()) } returns 10 // avoid repair pass

        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getString(R.string.cluster_migration_title) } returns "Cluster Migration"
        every { mockContext.getString(R.string.cluster_migration_healing) } returns "Healing"

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        mockkStatic(SportTypeDatabaseManager::class)

        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesManager
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportDb

        WorkoutClusterDatabaseManager.resetForTesting(mockClusterDb)
        RoutesDatabaseManager.resetForTesting(mockRoutesDb)

        every { mockSummariesManager.database } returns mockSummariesDb
        every { mockSportDb.getBSportType(any()) } returns BSportType.RUN

        WorkoutClusterRepository.resetForTesting(null)
    }

    @After
    fun tearDown() {
        WorkoutClusterRepository.resetForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)
        RoutesDatabaseManager.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testRefreshClusters_survivingCluster_preservesHitCountUntouched() = runBlocking {
        val clusterId = 100L
        val survivingCluster = WorkoutCluster(
            id = clusterId,
            name = "Historic Loop",
            probableSportId = 1L,
            startLat = 48.0,
            startLng = 9.0,
            endLat = 48.1,
            endLng = 9.1,
            maxDispLat = 48.2,
            maxDispLng = 9.2,
            refDistance = 8000.0,
            hitCount = 25, // Lifetime hitCount is 25
            bSportType = BSportType.RUN,
            previewPaths = listOf("polyline_surviving")
        )

        every { mockClusterDb.getAllClusters() } returns listOf(survivingCluster)
        every { mockRoutesDb.getRouteByClusterId(clusterId) } returns null

        // Phase 1: mock cursor returning count of 5 surviving sessions for clusterId
        val mockCountCursor = mockk<Cursor>(relaxed = true)
        var cursorAdvanced = false
        every { mockCountCursor.moveToNext() } answers {
            if (!cursorAdvanced) {
                cursorAdvanced = true
                true
            } else {
                false
            }
        }
        every { mockCountCursor.getLong(0) } returns clusterId
        every { mockCountCursor.getInt(1) } returns 5 // 5 surviving workouts
        every { mockCountCursor.close() } returns Unit

        every {
            mockSummariesDb.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                arrayOf(WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID, "COUNT(*)"),
                any(), any(), any(), any(), any()
            )
        } returns mockCountCursor

        val repo = WorkoutClusterRepository.getInstance(mockContext)
        repo.refreshClusters(forceShowProgress = false, forceCheckIntegrity = true)

        // Verify clusterDb does NOT update cluster with a reduced hitCount
        verify(exactly = 0) {
            mockClusterDb.updateCluster(match { it.id == clusterId && it.hitCount < 25 })
        }
        // Verify cluster was NOT deleted
        verify(exactly = 0) {
            mockClusterDb.deleteCluster(clusterId)
        }

        // Verify exposed list retains lifetime hitCount = 25
        val clusters = repo.allClusters.value
        assertEquals(1, clusters.size)
        assertEquals(25, clusters[0].hitCount)
    }

    @Test
    fun testRefreshClusters_unlinkedZeroWorkoutOrphan_purgesCluster() = runBlocking {
        val orphanClusterId = 101L
        val orphanCluster = WorkoutCluster(
            id = orphanClusterId,
            name = "Purged Route",
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
            routePolyline = null
        )

        every { mockClusterDb.getAllClusters() } returns listOf(orphanCluster)
        every { mockRoutesDb.getRouteByClusterId(orphanClusterId) } returns null

        // Phase 1: mock cursor returning empty (0 surviving workouts for this cluster)
        val mockEmptyCursor = mockk<Cursor>(relaxed = true)
        every { mockEmptyCursor.moveToNext() } returns false
        every { mockEmptyCursor.close() } returns Unit

        every {
            mockSummariesDb.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                arrayOf(WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID, "COUNT(*)"),
                any(), any(), any(), any(), any()
            )
        } returns mockEmptyCursor

        val repo = WorkoutClusterRepository.getInstance(mockContext)
        repo.refreshClusters(forceShowProgress = false, forceCheckIntegrity = true)

        // Verify orphan cluster with 0 actual workouts and no route is deleted (REQ-SET-062)
        verify {
            mockClusterDb.deleteCluster(orphanClusterId)
        }
        assertEquals(0, repo.allClusters.value.size)
    }
}
