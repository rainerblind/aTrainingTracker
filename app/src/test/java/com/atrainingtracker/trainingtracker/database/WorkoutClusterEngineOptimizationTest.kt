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
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests verifying transactional batching, in-memory cluster caching, and concurrency synchronization
 * in WorkoutClusterEngine (REQ-DAT-034.3, REQ-DAT-034.4, TST-DAT-029.3, TST-DAT-029.4).
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutClusterEngineOptimizationTest {

    private lateinit var context: Context
    private lateinit var engine: WorkoutClusterEngine
    private lateinit var mockSportDb: SportTypeDatabaseManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TrainingApplication.setAppContextForTesting(context)
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
        TrainingApplication.setSharedPreferencesForTesting(prefs)

        mockSportDb = mockk(relaxed = true)
        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportDb
        every { mockSportDb.getBSportType(any()) } returns BSportType.RUN

        context.deleteDatabase("WorkoutClusters.db")
        context.deleteDatabase("Routes.db")
        context.deleteDatabase("WorkoutSummaries.db")
        WorkoutClusterEngine.resetForTesting(null)
        engine = WorkoutClusterEngine.getInstance(context)
    }

    @After
    fun tearDown() {
        WorkoutClusterEngine.resetForTesting(null)
        TrainingApplication.setAppContextForTesting(null)
        TrainingApplication.setSharedPreferencesForTesting(null)
        unmockkAll()
        context.deleteDatabase("WorkoutClusters.db")
        context.deleteDatabase("Routes.db")
        context.deleteDatabase("WorkoutSummaries.db")
    }

    @Test
    fun testSuggestCluster_evaluatesCachedClustersInMemory() {
        val start = LatLng(48.137154, 11.576124)
        val end = LatLng(48.137154, 11.576124)
        val apex = LatLng(48.140000, 11.580000)
        val distance = 5000.0

        val inMemoryCluster = WorkoutCluster(
            id = 999L,
            name = "Englischer Garten Loop",
            probableSportId = 1L,
            startLat = start.latitude,
            startLng = start.longitude,
            endLat = end.latitude,
            endLng = end.longitude,
            maxDispLat = apex.latitude,
            maxDispLng = apex.longitude,
            refDistance = distance,
            hitCount = 5,
            bSportType = BSportType.RUN
        )

        // Empty database: suggestCluster without cache returns null
        val noCacheMatch = engine.suggestCluster(start, end, apex, distance, workoutSportType = BSportType.RUN)
        assertNull("Without cache, DB is empty so match must be null", noCacheMatch)

        // Pass cachedClusters: suggestCluster resolves against in-memory list
        val cacheMatch = engine.suggestCluster(
            start, end, apex, distance,
            workoutSportType = BSportType.RUN,
            cachedClusters = listOf(inMemoryCluster)
        )
        assertNotNull("With in-memory cachedClusters, match must be found", cacheMatch)
        assertEquals(999L, cacheMatch?.id)
        assertEquals("Englischer Garten Loop", cacheMatch?.name)
    }

    @Test
    fun testLearnFromWorkout_maintainsCachedClustersList() {
        val start = LatLng(48.137154, 11.576124)
        val end = LatLng(48.137154, 11.576124)
        val apex = LatLng(48.140000, 11.580000)
        val distance = 5000.0

        val localCache = mutableListOf<WorkoutCluster>()

        // First learn call creates new cluster and populates localCache
        val newId = engine.learnFromWorkout(
            start, end, apex, distance,
            userSpecifiedName = "River Run",
            userSportId = 1L,
            cachedClusters = localCache
        )

        assertTrue(newId > 0)
        assertEquals(1, localCache.size)
        assertEquals(newId, localCache[0].id)
        assertEquals("River Run", localCache[0].name)

        // Second learn call matches existing cluster from localCache and updates centroid in place
        val matchId = engine.learnFromWorkout(
            start, end, apex, distance,
            userSpecifiedName = "River Run",
            userSportId = 1L,
            cachedClusters = localCache
        )

        assertEquals("Must match existing cluster id", newId, matchId)
        assertEquals("Cache size must not grow for matching cluster", 1, localCache.size)
        assertEquals("River Run", localCache[0].name)
    }

    @Test
    fun testClusterLock_serializesConcurrentMutations() {
        val start = LatLng(48.137154, 11.576124)
        val end = LatLng(48.137154, 11.576124)
        val apex = LatLng(48.140000, 11.580000)
        val distance = 5000.0

        val threadCount = 4
        val iterationsPerThread = 25
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val exceptions = mutableListOf<Throwable>()

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (i in 0 until iterationsPerThread) {
                        val name = "Concurrent Route $t-$i"
                        val id = engine.learnFromWorkout(
                            start, end, apex, distance,
                            userSpecifiedName = name,
                            userSportId = 1L
                        )
                        if (id > 0) successCount.incrementAndGet()
                    }
                } catch (e: Throwable) {
                    synchronized(exceptions) { exceptions.add(e) }
                } finally {
                    latch.countDown()
                }
            }
        }

        val completed = latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("All concurrent worker threads must complete within timeout", completed)
        assertTrue("Zero exceptions expected during synchronized mutations: $exceptions", exceptions.isEmpty())
        assertEquals(threadCount * iterationsPerThread, successCount.get())
    }

    @Test
    fun testMigrateHistory_executesCleanlyInsideTransactions() {
        var p1Called = false
        var p2Called = false

        val listener = object : ClusterMigrationListener {
            override fun onPhase1Progress(current: Int, total: Int) {
                p1Called = true
            }
            override fun onPhase2Progress(current: Int, total: Int) {
                p2Called = true
            }
        }

        // Migrate empty database: Phase 1 reaches 100% completion
        engine.migrateHistory(context, listener)
        assertTrue("Phase 1 progress must be reported", p1Called)
    }
}
