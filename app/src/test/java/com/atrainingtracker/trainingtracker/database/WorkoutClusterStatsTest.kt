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

import android.app.Application
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.preference.PreferenceManager
import com.atrainingtracker.banalservice.BSportType
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Verification test suite for REQ-SET-070 and TST-SET-070 (ATT-221):
 * Aggregate performance statistics for workout clusters.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutClusterStatsTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var mockContext: Application
    private lateinit var mockSummariesDb: SQLiteDatabase
    private lateinit var testSummariesManager: WorkoutSummariesDatabaseManager
    private lateinit var mockClusterDb: WorkoutClusterDatabaseManager
    private lateinit var mockRoutesDb: RoutesDatabaseManager

    private class TestWorkoutSummariesDatabaseManager(
        private val db: SQLiteDatabase
    ) : WorkoutSummariesDatabaseManager(null) {
        override fun getDatabase(): SQLiteDatabase = db
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockContext = mockk(relaxed = true)
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        every { mockContext.applicationContext } returns mockContext

        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs
        every { mockPrefs.getInt(any(), any()) } returns 10

        mockSummariesDb = mockk(relaxed = true)
        testSummariesManager = TestWorkoutSummariesDatabaseManager(mockSummariesDb)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(testSummariesManager)

        mockClusterDb = mockk(relaxed = true)
        mockRoutesDb = mockk(relaxed = true)

        WorkoutClusterDatabaseManager.resetForTesting(mockClusterDb)
        RoutesDatabaseManager.resetForTesting(mockRoutesDb)
        every { mockRoutesDb.getRouteByClusterId(any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)
        RoutesDatabaseManager.resetForTesting(null)
        WorkoutClusterRepository.resetForTesting(null)
    }

    private fun createMockCursor(rows: List<Map<String, Any?>>): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)
        var currentIndex = -1

        every { cursor.moveToFirst() } answers {
            if (rows.isNotEmpty()) {
                currentIndex = 0
                true
            } else {
                false
            }
        }

        every { cursor.moveToNext() } answers {
            if (currentIndex + 1 < rows.size) {
                currentIndex++
                true
            } else {
                false
            }
        }

        every { cursor.getColumnIndexOrThrow(any()) } answers {
            val colName = firstArg<String>()
            colName.hashCode()
        }

        every { cursor.isNull(any()) } answers {
            val colHash = firstArg<Int>()
            if (currentIndex in rows.indices) {
                val entry = rows[currentIndex].entries.firstOrNull { it.key.hashCode() == colHash }
                entry == null || entry.value == null
            } else {
                true
            }
        }

        every { cursor.getInt(any()) } answers {
            val colHash = firstArg<Int>()
            val entry = rows[currentIndex].entries.firstOrNull { it.key.hashCode() == colHash }
            (entry?.value as? Number)?.toInt() ?: 0
        }

        every { cursor.getLong(any()) } answers {
            val colHash = firstArg<Int>()
            val entry = rows[currentIndex].entries.firstOrNull { it.key.hashCode() == colHash }
            (entry?.value as? Number)?.toLong() ?: 0L
        }

        every { cursor.getDouble(any()) } answers {
            val colHash = firstArg<Int>()
            val entry = rows[currentIndex].entries.firstOrNull { it.key.hashCode() == colHash }
            (entry?.value as? Number)?.toDouble() ?: 0.0
        }

        every { cursor.getString(any()) } answers {
            val colHash = firstArg<Int>()
            val entry = rows[currentIndex].entries.firstOrNull { it.key.hashCode() == colHash }
            entry?.value?.toString()
        }

        return cursor
    }

    @Test
    fun testDefaultEmptyStats() {
        val emptyStats = WorkoutClusterStats.EMPTY
        assertEquals(0L, emptyStats.clusterId)
        assertEquals(0, emptyStats.workoutCount)
        assertNull(emptyStats.lastHitTimestampS)
        assertNull(emptyStats.lastHitDateStr)
        assertEquals(0.0, emptyStats.avgSpeedMps, 0.001)
        assertEquals(0L, emptyStats.avgDurationSec)
        assertNull(emptyStats.bestDurationSec)
        assertEquals(0.0, emptyStats.totalDistanceMeters, 0.001)
        assertEquals(0L, emptyStats.totalAscentMeters)
        assertEquals(0L, emptyStats.totalActiveTimeSec)

        // Computed helper properties
        assertEquals(0.0, emptyStats.avgSpeedKmh, 0.001)
        assertEquals(0.0, emptyStats.avgDistanceMeters, 0.001)
        assertEquals(0L, emptyStats.avgAscentMeters)
        assertEquals(0.0, emptyStats.avgPaceSecPerKm, 0.001)
    }

    @Test
    fun testSingleWorkoutStats() {
        val stats = WorkoutClusterStats(
            clusterId = 42L,
            workoutCount = 1,
            lastHitTimestampS = 1700000000L,
            lastHitDateStr = "2023-11-14 10:00:00",
            avgSpeedMps = 3.3333, // ~12 km/h
            avgDurationSec = 3000L,
            bestDurationSec = 3000L,
            totalDistanceMeters = 10000.0,
            totalAscentMeters = 150L,
            totalActiveTimeSec = 3000L
        )

        assertEquals(42L, stats.clusterId)
        assertEquals(1, stats.workoutCount)
        assertEquals(1700000000L, stats.lastHitTimestampS)
        assertEquals("2023-11-14 10:00:00", stats.lastHitDateStr)
        assertEquals(3000L, stats.bestDurationSec)
        assertEquals(10000.0, stats.totalDistanceMeters, 0.001)
        assertEquals(150L, stats.totalAscentMeters)
        assertEquals(3000L, stats.totalActiveTimeSec)

        // Computed helper properties
        assertEquals(12.0, stats.avgSpeedKmh, 0.01)
        assertEquals(10000.0, stats.avgDistanceMeters, 0.01)
        assertEquals(150L, stats.avgAscentMeters)
        // 1000 / 3.3333 = 300.0 s/km (5:00 min/km)
        assertEquals(300.0, stats.avgPaceSecPerKm, 0.1)
    }

    @Test
    fun testMultipleWorkoutsAggregation() {
        val stats = WorkoutClusterStats(
            clusterId = 101L,
            workoutCount = 4,
            lastHitTimestampS = 1720000000L,
            lastHitDateStr = "2024-07-03 18:30:00",
            avgSpeedMps = 4.1667, // ~15 km/h
            avgDurationSec = 2400L,
            bestDurationSec = 2200L,
            totalDistanceMeters = 40000.0,
            totalAscentMeters = 800L,
            totalActiveTimeSec = 9600L
        )

        assertEquals(4, stats.workoutCount)
        assertEquals(40000.0, stats.totalDistanceMeters, 0.001)
        assertEquals(9600L, stats.totalActiveTimeSec)
        assertEquals(10000.0, stats.avgDistanceMeters, 0.01)
        assertEquals(2400L, stats.avgDurationSec)
        assertEquals(2200L, stats.bestDurationSec)
        assertEquals(800L, stats.totalAscentMeters)
        assertEquals(200L, stats.avgAscentMeters)
        assertEquals(15.0, stats.avgSpeedKmh, 0.01)
        // 1000 / 4.1667 = 240 s/km (4:00 min/km)
        assertEquals(240.0, stats.avgPaceSecPerKm, 0.1)
    }

    @Test
    fun testCursorExtractionSingleCluster() {
        val row = mapOf<String, Any?>(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID to 5L,
            "cnt" to 3,
            "lastHitEpochS" to 1710000000L,
            "lastHitDateStr" to "2024-03-09 08:00:00",
            "avgSpeedMps" to 3.5,
            "avgDurationSec" to 3600L,
            "bestDurationSec" to 3400L,
            "totalDistanceMeters" to 37800.0,
            "totalAscentMeters" to 450L,
            "totalActiveTimeSec" to 10800L
        )
        val cursor = createMockCursor(listOf(row))

        every {
            mockSummariesDb.rawQuery(any<String>(), any<Array<String>>())
        } returns cursor

        val manager = WorkoutSummariesDatabaseManager.getInstance(mockContext)
        val stats = manager.getWorkoutClusterStats(5L)

        assertEquals(5L, stats.clusterId)
        assertEquals(3, stats.workoutCount)
        assertEquals(1710000000L, stats.lastHitTimestampS)
        assertEquals("2024-03-09 08:00:00", stats.lastHitDateStr)
        assertEquals(3.5, stats.avgSpeedMps, 0.001)
        assertEquals(3600L, stats.avgDurationSec)
        assertEquals(3400L, stats.bestDurationSec)
        assertEquals(37800.0, stats.totalDistanceMeters, 0.001)
        assertEquals(450L, stats.totalAscentMeters)
        assertEquals(10800L, stats.totalActiveTimeSec)
    }

    @Test
    fun testNullHandlingInCursor() {
        val emptyCursor = createMockCursor(emptyList())
        every {
            mockSummariesDb.rawQuery(any<String>(), any<Array<String>>())
        } returns emptyCursor

        val manager = WorkoutSummariesDatabaseManager.getInstance(mockContext)
        val stats = manager.getWorkoutClusterStats(999L)

        assertEquals(999L, stats.clusterId)
        assertEquals(0, stats.workoutCount)
        assertNull(stats.lastHitTimestampS)
        assertNull(stats.lastHitDateStr)
        assertEquals(0.0, stats.avgSpeedMps, 0.001)
        assertEquals(0L, stats.avgDurationSec)
        assertNull(stats.bestDurationSec)
        assertEquals(0.0, stats.totalDistanceMeters, 0.001)
        assertEquals(0L, stats.totalAscentMeters)
        assertEquals(0L, stats.totalActiveTimeSec)
    }

    @Test
    fun testGroupByAllClustersQuery() {
        val row1 = mapOf<String, Any?>(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID to 1L,
            "cnt" to 2,
            "lastHitEpochS" to 1700000000L,
            "lastHitDateStr" to "2023-11-14 10:00:00",
            "avgSpeedMps" to 3.0,
            "avgDurationSec" to 3000L,
            "bestDurationSec" to 2900L,
            "totalDistanceMeters" to 18000.0,
            "totalAscentMeters" to 100L,
            "totalActiveTimeSec" to 6000L
        )
        val row2 = mapOf<String, Any?>(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID to 2L,
            "cnt" to 5,
            "lastHitEpochS" to 1710000000L,
            "lastHitDateStr" to "2024-03-09 08:00:00",
            "avgSpeedMps" to 4.0,
            "avgDurationSec" to 4000L,
            "bestDurationSec" to 3800L,
            "totalDistanceMeters" to 80000.0,
            "totalAscentMeters" to 500L,
            "totalActiveTimeSec" to 20000L
        )
        val cursor = createMockCursor(listOf(row1, row2))

        every {
            mockSummariesDb.rawQuery(any<String>(), null)
        } returns cursor

        val manager = WorkoutSummariesDatabaseManager.getInstance(mockContext)
        val allStats = manager.workoutClusterStatsForAllClusters

        assertEquals(2, allStats.size)
        assertTrue(allStats.containsKey(1L))
        assertTrue(allStats.containsKey(2L))
        assertEquals(2, allStats[1L]?.workoutCount)
        assertEquals(5, allStats[2L]?.workoutCount)
        assertEquals(3.0, allStats[1L]?.avgSpeedMps ?: 0.0, 0.001)
        assertEquals(4.0, allStats[2L]?.avgSpeedMps ?: 0.0, 0.001)
    }

    @Test
    fun testRepositoryStatsFlowIntegration() = runBlocking {
        val testStats = mapOf(
            1L to WorkoutClusterStats(
                clusterId = 1L,
                workoutCount = 3,
                lastHitTimestampS = 1710000000L,
                lastHitDateStr = "2024-03-09 08:00:00",
                avgSpeedMps = 3.5,
                avgDurationSec = 3600L,
                bestDurationSec = 3400L,
                totalDistanceMeters = 37800.0,
                totalAscentMeters = 450L,
                totalActiveTimeSec = 10800L
            )
        )
        val mockManager = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
        every { mockManager.workoutClusterStatsForAllClusters } returns testStats
        WorkoutSummariesDatabaseManager.setInstanceForTesting(mockManager)

        every { mockClusterDb.getAllClusters() } returns listOf(
            WorkoutCluster(
                id = 1L,
                name = "River Run",
                probableSportId = 1L,
                startLat = 10.0,
                startLng = 10.0,
                endLat = 10.1,
                endLng = 10.1,
                maxDispLat = 10.05,
                maxDispLng = 10.05,
                refDistance = 10000.0,
                hitCount = 3,
                bSportType = BSportType.RUN
            )
        )

        val repo = WorkoutClusterRepository.getInstance(mockContext)
        repo.refreshClusters()

        val emitted = repo.clusterStats.first()
        assertEquals(1, emitted.size)
        assertEquals(3, emitted[1L]?.workoutCount)
        assertEquals(3.5, emitted[1L]?.avgSpeedMps ?: 0.0, 0.001)
    }

    @Test
    fun testRelativeRecencyFormatter() {
        val nowS = 1725900000L // Reference time

        every { mockContext.getString(com.atrainingtracker.R.string.cluster_stats_today) } returns "Today"
        every { mockContext.getString(com.atrainingtracker.R.string.cluster_stats_yesterday) } returns "Yesterday"
        every { mockContext.getString(com.atrainingtracker.R.string.cluster_stats_days_ago, *anyVararg()) } answers {
            val formatArgs = args[1]
            val days = if (formatArgs is Array<*>) formatArgs[0] else formatArgs
            "$days days ago"
        }
        every { mockContext.getString(com.atrainingtracker.R.string.cluster_stats_weeks_ago, *anyVararg()) } answers {
            val formatArgs = args[1]
            val weeks = if (formatArgs is Array<*>) formatArgs[0] else formatArgs
            "$weeks weeks ago"
        }


        // 0 days diff -> Today
        val todayStr = WorkoutClusterStats.formatRelativeRecency(mockContext, nowS - 3600L, nowS)
        assertTrue("Expected Today but got: $todayStr", todayStr.contains("Today"))

        // 1 day diff -> Yesterday
        val yesterdayStr = WorkoutClusterStats.formatRelativeRecency(mockContext, nowS - 86400L, nowS)
        assertTrue("Expected Yesterday but got: $yesterdayStr", yesterdayStr.contains("Yesterday"))

        // 4 days diff -> 4 days ago
        val daysAgoStr = WorkoutClusterStats.formatRelativeRecency(mockContext, nowS - 4 * 86400L, nowS)
        assertTrue("Expected 4 days ago but got: $daysAgoStr", daysAgoStr.contains("4 days ago"))

        // 14 days diff -> 2 weeks ago
        val weeksAgoStr = WorkoutClusterStats.formatRelativeRecency(mockContext, nowS - 14 * 86400L, nowS)
        assertTrue("Expected 2 weeks ago but got: $weeksAgoStr", weeksAgoStr.contains("2 weeks ago"))

        // Null timestamp -> empty
        val nullStr = WorkoutClusterStats.formatRelativeRecency(mockContext, null, nowS)
        assertEquals("", nullStr)
    }

    @Test
    fun testSportAdaptiveVelocity() {
        val stats = WorkoutClusterStats(
            clusterId = 1L,
            workoutCount = 1,
            avgSpeedMps = 3.3333333333333335 // 12.0 km/h = 5:00 min/km
        )

        // For running: pace in s/km
        assertEquals(300.0, stats.avgPaceSecPerKm, 0.1)

        // For cycling: speed in km/h
        assertEquals(12.0, stats.avgSpeedKmh, 0.01)
    }
}

