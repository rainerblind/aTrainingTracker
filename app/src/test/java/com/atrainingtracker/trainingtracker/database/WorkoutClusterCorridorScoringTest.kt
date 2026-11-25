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
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for corridor-aware scoring and discrimination of parallel point-to-point (A -> B) route variants
 * (REQ-SET-097.3, TST-SET-097.2, ATT-5899).
 */
class WorkoutClusterCorridorScoringTest {

    private lateinit var mockContext: Context
    private lateinit var clusterEngine: WorkoutClusterEngine

    private val startSindelfingen = LatLng(48.7083, 9.0028)
    private val endSchoenaich = LatLng(48.6570, 9.0640)

    // Forest Corridor: curves eastward (apex around 48.6950, 9.0450)
    private val forestApex = LatLng(48.6950, 9.0450)
    private val forestPath = listOf(
        startSindelfingen,
        LatLng(48.7050, 9.0150),
        LatLng(48.7000, 9.0300),
        forestApex,
        LatLng(48.6750, 9.0550),
        LatLng(48.6650, 9.0600),
        endSchoenaich
    )

    // City Corridor: curves westward through Böblingen center (apex around 48.6850, 9.0100)
    private val cityApex = LatLng(48.6850, 9.0100)
    private val cityPath = listOf(
        startSindelfingen,
        LatLng(48.7000, 9.0050),
        cityApex,
        LatLng(48.6700, 9.0250),
        LatLng(48.6600, 9.0450),
        endSchoenaich
    )

    private val forestPolyline = PolyUtil.encode(forestPath)
    private val cityPolyline = PolyUtil.encode(cityPath)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext

        WorkoutClusterEngine.resetForTesting(null)
        clusterEngine = WorkoutClusterEngine.getInstance(mockContext)
    }

    @After
    fun tearDown() {
        WorkoutClusterEngine.resetForTesting(null)
        unmockkAll()
    }

    private fun createCluster(
        id: Long,
        name: String,
        apex: LatLng,
        polyline: String
    ): WorkoutCluster {
        return WorkoutCluster(
            id = id,
            name = name,
            probableSportId = 1L,
            bSportType = BSportType.BIKE,
            startLat = startSindelfingen.latitude,
            startLng = startSindelfingen.longitude,
            endLat = endSchoenaich.latitude,
            endLng = endSchoenaich.longitude,
            maxDispLat = apex.latitude,
            maxDispLng = apex.longitude,
            refDistance = 9400.0,
            hitCount = 50,
            previewPaths = listOf(polyline)
        )
    }

    @Test
    fun testCorridorScoring_accuratelyRanksForestWorkoutForForestCluster() {
        val clusterForest = createCluster(101L, "Bike2Home (Wald)", forestApex, forestPolyline)
        val clusterCity = createCluster(102L, "Bike2Home (Stadt)", cityApex, cityPolyline)

        val candidates = listOf(clusterCity, clusterForest)

        // Evaluate Forest workout
        val scores = clusterEngine.scoreClusters(
            clusters = candidates,
            start = startSindelfingen,
            end = endSchoenaich,
            apex = forestApex,
            distance = 9400.0,
            candidateSportTypes = setOf(BSportType.BIKE),
            workoutPolyline = forestPath
        )

        // Forest cluster must be ranked first (lowest score)
        assertEquals(2, scores.size)
        assertEquals("Bike2Home (Wald)", scores[0].first.name)
        assertEquals("Bike2Home (Stadt)", scores[1].first.name)

        assertTrue("Forest cluster score must be highly favorable (< 0.5)", scores[0].second < 0.5)
        assertTrue("City cluster score must be penalised due to corridor lateral deviation", scores[1].second > scores[0].second + 0.3)
    }

    @Test
    fun testCorridorScoring_accuratelyRanksCityWorkoutForCityCluster() {
        val clusterForest = createCluster(101L, "Bike2Home (Wald)", forestApex, forestPolyline)
        val clusterCity = createCluster(102L, "Bike2Home (Stadt)", cityApex, cityPolyline)

        val candidates = listOf(clusterForest, clusterCity)

        // Evaluate City workout
        val scores = clusterEngine.scoreClusters(
            clusters = candidates,
            start = startSindelfingen,
            end = endSchoenaich,
            apex = cityApex,
            distance = 9400.0,
            candidateSportTypes = setOf(BSportType.BIKE),
            workoutPolyline = cityPath
        )

        // City cluster must be ranked first
        assertEquals(2, scores.size)
        assertEquals("Bike2Home (Stadt)", scores[0].first.name)
        assertEquals("Bike2Home (Wald)", scores[1].first.name)

        assertTrue("City cluster score must be highly favorable (< 0.5)", scores[0].second < 0.5)
        assertTrue("Forest cluster score must be penalised due to corridor lateral deviation", scores[1].second > scores[0].second + 0.3)
    }

    @Test
    fun testApexDiscrimination_separatesCorridorsEvenWithoutPolyline() {
        val clusterForest = createCluster(101L, "Bike2Home (Wald)", forestApex, forestPolyline)
        val clusterCity = createCluster(102L, "Bike2Home (Stadt)", cityApex, cityPolyline)

        val candidates = listOf(clusterCity, clusterForest)

        // Score forest workout WITHOUT providing workoutPolyline (apex alone)
        val scoresWithoutPoly = clusterEngine.scoreClusters(
            clusters = candidates,
            start = startSindelfingen,
            end = endSchoenaich,
            apex = forestApex,
            distance = 9400.0,
            candidateSportTypes = setOf(BSportType.BIKE),
            workoutPolyline = null
        )

        // Even without polyline, the non-degenerate elliptical apex s3 term separates them!
        assertEquals("Bike2Home (Wald)", scoresWithoutPoly[0].first.name)
        assertTrue(scoresWithoutPoly[1].second > scoresWithoutPoly[0].second)
    }

    @Test
    fun testScoreClusters_hoistsWorkoutPolylineSamplingToAtMostOncePerCall_TST_MIG_062() {
        // TST-MIG-062.2 / REQ-MIG-062.2: samplePolylineStations(workoutPolyline) executes at most 1x per scoreClusters/suggestCluster call
        val clusters = (1..25).map { idx ->
            createCluster(
                id = 200L + idx,
                name = "Route $idx",
                apex = if (idx % 2 == 0) forestApex else cityApex,
                polyline = if (idx % 2 == 0) forestPolyline else cityPolyline
            )
        }

        val workoutSampleCalls = AtomicInteger(0)
        mockkObject(WorkoutClusterCorridorDetector)
        every { WorkoutClusterCorridorDetector.samplePolylineStations(any()) } answers {
            val arg = firstArg<List<LatLng>>()
            if (arg === forestPath) {
                workoutSampleCalls.incrementAndGet()
            }
            callOriginal()
        }

        // 1. scoreClusters over 25 clusters samples workoutPolyline exactly once
        val scored = clusterEngine.scoreClusters(
            clusters = clusters,
            start = startSindelfingen,
            end = endSchoenaich,
            apex = forestApex,
            distance = 9400.0,
            candidateSportTypes = setOf(BSportType.BIKE),
            workoutPolyline = forestPath
        )
        assertEquals(25, scored.size)
        assertEquals(
            "workoutPolyline must be sampled exactly once across 25 clusters in scoreClusters",
            1,
            workoutSampleCalls.get()
        )

        // 2. suggestCluster over the same 25 clusters samples workoutPolyline exactly once more
        val suggested = clusterEngine.suggestCluster(
            start = startSindelfingen,
            end = endSchoenaich,
            apex = forestApex,
            distance = 9400.0,
            candidateSportTypes = setOf(BSportType.BIKE),
            cachedClusters = clusters,
            workoutPolyline = forestPath
        )
        assertNotNull(suggested)
        assertEquals(
            "workoutPolyline must be sampled exactly once in suggestCluster",
            2,
            workoutSampleCalls.get()
        )

        // 3. When no cluster has previewPaths, workoutPolyline must not be sampled at all (0x)
        val previewlessClusters = clusters.map { it.copy(previewPaths = emptyList()) }
        clusterEngine.scoreClusters(
            clusters = previewlessClusters,
            start = startSindelfingen,
            end = endSchoenaich,
            apex = forestApex,
            distance = 9400.0,
            candidateSportTypes = setOf(BSportType.BIKE),
            workoutPolyline = forestPath
        )
        assertEquals(
            "workoutPolyline must not be sampled when no cluster has previewPaths",
            2,
            workoutSampleCalls.get()
        )
    }

    @Test
    fun testScoreClusters_cachesClusterPreviewDecodingAcrossWorkoutsAndInvalidatesOnMutation_TST_MIG_062() {
        // TST-MIG-062.2 / REQ-MIG-062.2: Cluster preview stations are decoded once, reused across workouts,
        // invalidated on mutation, and yield 100% bit-identical similarity scores.
        val clusters = (1..10).map { idx ->
            createCluster(
                id = 300L + idx,
                name = "Cached Route $idx",
                apex = if (idx == 1) forestApex else cityApex,
                polyline = if (idx == 1) forestPolyline else cityPolyline
            )
        }

        // Capture uncached reference scores on a fresh engine for strict equality assertion
        val referenceScores = clusters.associate { cluster ->
            clusterEngine.invalidatePreviewStationCache()
            cluster.id to clusterEngine.calculateSimilarity(
                start = startSindelfingen,
                end = endSchoenaich,
                apex = forestApex,
                distance = 9400.0,
                cluster = cluster,
                candidateSportTypes = setOf(BSportType.BIKE),
                workoutPolyline = forestPath
            )
        }
        clusterEngine.invalidatePreviewStationCache()

        val decodeCalls = AtomicInteger(0)
        mockkStatic(PolyUtil::class)
        every { PolyUtil.decode(any()) } answers {
            decodeCalls.incrementAndGet()
            callOriginal()
        }

        // Workout 1: populates cache for all 10 clusters (10 decodes)
        val pass1 = clusterEngine.scoreClusters(
            clusters = clusters,
            start = startSindelfingen,
            end = endSchoenaich,
            apex = forestApex,
            distance = 9400.0,
            candidateSportTypes = setOf(BSportType.BIKE),
            workoutPolyline = forestPath
        )
        assertEquals("First pass must decode each cluster preview exactly once", 10, decodeCalls.get())
        pass1.forEach { (cluster, score) ->
            assertEquals(
                "Cached score for cluster ${cluster.id} must match uncached baseline identically",
                referenceScores.getValue(cluster.id),
                score,
                0.0
            )
        }

        // Workout 2 & Workout 3: zero additional PolyUtil.decode calls
        clusterEngine.scoreClusters(
            clusters = clusters,
            start = startSindelfingen,
            end = endSchoenaich,
            apex = cityApex,
            distance = 9400.0,
            candidateSportTypes = setOf(BSportType.BIKE),
            workoutPolyline = cityPath
        )
        val pass3 = clusterEngine.scoreClusters(
            clusters = clusters,
            start = startSindelfingen,
            end = endSchoenaich,
            apex = forestApex,
            distance = 9400.0,
            candidateSportTypes = setOf(BSportType.BIKE),
            workoutPolyline = forestPath
        )
        assertEquals("Subsequent workouts must reuse cached cluster stations with 0 extra decodes", 10, decodeCalls.get())
        pass3.forEach { (cluster, score) ->
            assertEquals(referenceScores.getValue(cluster.id), score, 0.0)
        }

        // Explicit invalidation for cluster 301 must force re-decode strictly for cluster 301
        clusterEngine.invalidatePreviewStationCache(301L)
        clusterEngine.scoreClusters(
            clusters = clusters,
            start = startSindelfingen,
            end = endSchoenaich,
            apex = forestApex,
            distance = 9400.0,
            candidateSportTypes = setOf(BSportType.BIKE),
            workoutPolyline = forestPath
        )
        assertEquals("Invalidating 1 cluster must re-decode only that cluster", 11, decodeCalls.get())

        // Mutating cluster 301's previewPaths from forestPolyline -> cityPolyline must immediately update score
        val mutatedCluster = clusters.first().copy(previewPaths = listOf(cityPolyline))
        val mutatedList = listOf(mutatedCluster) + clusters.drop(1)
        val mutatedScore = clusterEngine.scoreClusters(
            clusters = mutatedList,
            start = startSindelfingen,
            end = endSchoenaich,
            apex = forestApex,
            distance = 9400.0,
            candidateSportTypes = setOf(BSportType.BIKE),
            workoutPolyline = forestPath
        ).first { it.first.id == 301L }.second

        assertEquals("Mutating previewPaths must decode the new polyline", 12, decodeCalls.get())
        assertTrue(
            "Mutated cluster score ($mutatedScore) must reflect city corridor penalty vs original (${referenceScores.getValue(301L)})",
            mutatedScore > referenceScores.getValue(301L) + 0.3
        )
    }

    @Test
    fun testPreviewStationCache_concurrentScoringAndInvalidationIsThreadSafe_TST_MIG_062() {
        val clusterForest = createCluster(401L, "Concurrent Forest", forestApex, forestPolyline)
        val clusterCity = createCluster(402L, "Concurrent City", cityApex, cityPolyline)
        val candidates = listOf(clusterForest, clusterCity)

        val expectedForestScore = clusterEngine.calculateSimilarity(
            start = startSindelfingen,
            end = endSchoenaich,
            apex = forestApex,
            distance = 9400.0,
            cluster = clusterForest,
            candidateSportTypes = setOf(BSportType.BIKE),
            workoutPolyline = forestPath
        )
        val expectedCityScore = clusterEngine.calculateSimilarity(
            start = startSindelfingen,
            end = endSchoenaich,
            apex = forestApex,
            distance = 9400.0,
            cluster = clusterCity,
            candidateSportTypes = setOf(BSportType.BIKE),
            workoutPolyline = forestPath
        )

        val threads = 6
        val iterations = 40
        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)
        val failures = mutableListOf<Throwable>()

        for (t in 0 until threads) {
            executor.submit {
                try {
                    for (i in 0 until iterations) {
                        if (i % 5 == 0) {
                            clusterEngine.invalidatePreviewStationCache(401L)
                        } else if (i % 13 == 0) {
                            clusterEngine.invalidatePreviewStationCache(null)
                        }
                        val scored = clusterEngine.scoreClusters(
                            clusters = candidates,
                            start = startSindelfingen,
                            end = endSchoenaich,
                            apex = forestApex,
                            distance = 9400.0,
                            candidateSportTypes = setOf(BSportType.BIKE),
                            workoutPolyline = forestPath
                        )
                        val forestScore = scored.first { it.first.id == 401L }.second
                        val cityScore = scored.first { it.first.id == 402L }.second
                        assertEquals(expectedForestScore, forestScore, 0.0)
                        assertEquals(expectedCityScore, cityScore, 0.0)
                    }
                } catch (e: Throwable) {
                    synchronized(failures) { failures.add(e) }
                } finally {
                    latch.countDown()
                }
            }
        }

        val completed = latch.await(15, TimeUnit.SECONDS)
        executor.shutdown()
        assertTrue("Concurrent workers must finish within timeout", completed)
        assertTrue("Zero concurrency failures expected: $failures", failures.isEmpty())
    }

    @Test
    fun testPreviewStationCache_unsavedClustersAndRepositoryMutationInvalidation_TST_MIG_062() {
        // Verify unsaved clusters (id == 0L) also benefit from bounded caching and invalidateAll clears them
        val unsavedForest = createCluster(0L, "Unsaved Forest", forestApex, forestPolyline)
        val decodeCalls = AtomicInteger(0)
        mockkStatic(PolyUtil::class)
        every { PolyUtil.decode(any()) } answers {
            decodeCalls.incrementAndGet()
            callOriginal()
        }

        clusterEngine.scoreClusters(
            clusters = listOf(unsavedForest),
            start = startSindelfingen,
            end = endSchoenaich,
            apex = forestApex,
            distance = 9400.0,
            candidateSportTypes = setOf(BSportType.BIKE),
            workoutPolyline = forestPath
        )
        clusterEngine.scoreClusters(
            clusters = listOf(unsavedForest),
            start = startSindelfingen,
            end = endSchoenaich,
            apex = forestApex,
            distance = 9400.0,
            candidateSportTypes = setOf(BSportType.BIKE),
            workoutPolyline = forestPath
        )
        assertEquals("Unsaved cluster preview must be decoded once and reused", 1, decodeCalls.get())

        WorkoutClusterEngine.invalidatePreviewStationCacheIfInitialized(null)
        clusterEngine.scoreClusters(
            clusters = listOf(unsavedForest),
            start = startSindelfingen,
            end = endSchoenaich,
            apex = forestApex,
            distance = 9400.0,
            candidateSportTypes = setOf(BSportType.BIKE),
            workoutPolyline = forestPath
        )
        assertEquals("Global invalidation must evict unsaved cluster cache entry", 2, decodeCalls.get())
    }
}
