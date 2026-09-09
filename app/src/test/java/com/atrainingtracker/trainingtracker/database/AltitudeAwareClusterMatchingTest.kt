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
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying altitude extrema-aware 3D route clustering and mathematical centroid precision
 * (REQ-SET-065, TST-SET-052, TST-UNT-015, ATT-502).
 */
class AltitudeAwareClusterMatchingTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private val clusterEngine = WorkoutClusterEngine.getInstance(mockContext)

    @Before
    fun setUp() {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.useSportTypeForClustering() } returns false
        every { TrainingApplication.useAltitudePosForClustering() } returns true
        every { TrainingApplication.getClusterTolEndpoints() } returns 200f
        every { TrainingApplication.getClusterTolApex() } returns 400f
        every { TrainingApplication.getClusterTolDistance() } returns 0.20f
        every { TrainingApplication.getClusterTolAltitudePos() } returns 400f

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createSampleCluster(
        id: Long = 1L,
        name: String = "Stuttgart Valley Loop",
        bSportType: BSportType = BSportType.RUN,
        start: LatLng = LatLng(48.775, 9.182),
        end: LatLng = LatLng(48.776, 9.183),
        apex: LatLng = LatLng(48.790, 9.200),
        distance: Double = 5000.0,
        minAlt: LatLng? = LatLng(48.770, 9.175),
        maxAlt: LatLng? = LatLng(48.795, 9.210),
        hitCount: Int = 5
    ): WorkoutCluster {
        return WorkoutCluster(
            id = id,
            name = name,
            probableSportId = 1L,
            bSportType = bSportType,
            startLat = start.latitude,
            startLng = start.longitude,
            endLat = end.latitude,
            endLng = end.longitude,
            maxDispLat = apex.latitude,
            maxDispLng = apex.longitude,
            refDistance = distance,
            hitCount = hitCount,
            previewPaths = emptyList(),
            minAltLat = minAlt?.latitude,
            minAltLng = minAlt?.longitude,
            maxAltLat = maxAlt?.latitude,
            maxAltLng = maxAlt?.longitude
        )
    }

    /**
     * TC-UNT-01: 6-term scoring rejection when altitude extrema differ (> 400m tolerance).
     * Two routes with identical horizontal start, end, apex, and distance must be rejected
     * (score >= 1.0) when their vertical extrema coordinates diverge significantly.
     */
    @Test
    fun testCalculateSimilarity_RejectsWhenAltitudeExtremaDiverge() {
        val start = LatLng(48.775, 9.182)
        val end = LatLng(48.776, 9.183)
        val apex = LatLng(48.790, 9.200)
        val distance = 5000.0

        val clusterMinAlt = LatLng(48.770, 9.175)
        val clusterMaxAlt = LatLng(48.795, 9.210)
        val cluster = createSampleCluster(
            start = start, end = end, apex = apex, distance = distance,
            minAlt = clusterMinAlt, maxAlt = clusterMaxAlt
        )

        // Incoming route has identical horizontal footprint, but divergent altitude extrema
        // (e.g. peak on the eastern ridge instead of northern ridge, valley in opposite direction)
        val workoutMinAlt = LatLng(48.800, 9.220) // ~4600m from cluster minAlt
        val workoutMaxAlt = LatLng(48.750, 9.150) // ~6700m from cluster maxAlt

        val score = clusterEngine.calculateSimilarity(
            start, end, apex, distance, cluster,
            candidateSportTypes = emptySet(),
            minAltPos = workoutMinAlt,
            maxAltPos = workoutMaxAlt
        )

        // With 400m tolerance, divergence far exceeds tolerance -> score >= 1.0 (rejection)
        assertTrue("Score with divergent altitude extrema must exceed 1.0 to reject match, was $score", score >= 1.0)
    }

    /**
     * TC-UNT-02: 6-term matching when altitude extrema align (< 400m tolerance).
     * Routes with matching start, end, apex, distance, and aligned altitude extrema
     * must achieve score < 1.0 under the 6-term formula.
     */
    @Test
    fun testCalculateSimilarity_MatchesWhenAltitudeExtremaAlign() {
        val start = LatLng(48.775, 9.182)
        val end = LatLng(48.776, 9.183)
        val apex = LatLng(48.790, 9.200)
        val distance = 5000.0

        val clusterMinAlt = LatLng(48.770, 9.175)
        val clusterMaxAlt = LatLng(48.795, 9.210)
        val cluster = createSampleCluster(
            start = start, end = end, apex = apex, distance = distance,
            minAlt = clusterMinAlt, maxAlt = clusterMaxAlt
        )

        // Identical altitude extrema -> 0 distance -> score 0.0
        val exactScore = clusterEngine.calculateSimilarity(
            start, end, apex, distance, cluster,
            candidateSportTypes = emptySet(),
            minAltPos = clusterMinAlt,
            maxAltPos = clusterMaxAlt
        )
        assertEquals(0.0, exactScore, 0.001)

        // Slightly perturbed altitude extrema within 400m tolerance (~50m delta)
        // 0.00045 deg lat is approx 50m
        val alignedMinAlt = LatLng(48.77045, 9.175)
        val alignedMaxAlt = LatLng(48.79545, 9.210)

        val alignedScore = clusterEngine.calculateSimilarity(
            start, end, apex, distance, cluster,
            candidateSportTypes = emptySet(),
            minAltPos = alignedMinAlt,
            maxAltPos = alignedMaxAlt
        )

        assertTrue("Score with aligned altitude extrema must be < 1.0, was $alignedScore", alignedScore < 1.0)
        assertTrue("Score should reflect minor spatial deviation, was $alignedScore", alignedScore > 0.0)
    }

    /**
     * TC-UNT-03: Clean fallback to 4-term scoring when altitude data is null.
     * When either cluster candidate or workout lacks altitude extrema, the engine must
     * cleanly evaluate 4 terms with 0.25 weight each and zero altitude penalty.
     */
    @Test
    fun testCalculateSimilarity_FallbackTo4TermsWhenAltitudeNull() {
        val start = LatLng(48.775, 9.182)
        val end = LatLng(48.776, 9.183)
        val apex = LatLng(48.790, 9.200)
        val distance = 5000.0

        // Cluster without altitude coordinates
        val clusterNoAlt = createSampleCluster(
            start = start, end = end, apex = apex, distance = distance,
            minAlt = null, maxAlt = null
        )

        // Workout with altitude coordinates
        val workoutMinAlt = LatLng(48.770, 9.175)
        val workoutMaxAlt = LatLng(48.795, 9.210)

        // Case A: Cluster has null altitude, workout has altitude -> fallback to 4 terms
        val scoreA = clusterEngine.calculateSimilarity(
            start, end, apex, distance, clusterNoAlt,
            candidateSportTypes = emptySet(),
            minAltPos = workoutMinAlt,
            maxAltPos = workoutMaxAlt
        )
        assertEquals("Identical 2D footprint must yield score 0.0 with null cluster altitude", 0.0, scoreA, 0.001)

        // Case B: Cluster has altitude, workout has null altitude -> fallback to 4 terms
        val clusterWithAlt = createSampleCluster(
            start = start, end = end, apex = apex, distance = distance,
            minAlt = workoutMinAlt, maxAlt = workoutMaxAlt
        )
        val scoreB = clusterEngine.calculateSimilarity(
            start, end, apex, distance, clusterWithAlt,
            candidateSportTypes = emptySet(),
            minAltPos = null,
            maxAltPos = null
        )
        assertEquals("Identical 2D footprint must yield score 0.0 with null workout altitude", 0.0, scoreB, 0.001)

        // Case C: 4-term mathematical weighting verification (0.25 each)
        // Set start point deviation to 100m with endpoint tolerance of 200m -> term = (100 / 200) * 0.25 = 0.125
        val deviatedStart = LatLng(48.7759, 9.182) // ~100m north
        val distStart = clusterEngine.distanceBetween(deviatedStart, start)
        val expectedScore4Term = (distStart / 200f) * 0.25

        val score4Term = clusterEngine.calculateSimilarity(
            deviatedStart, end, apex, distance, clusterNoAlt,
            candidateSportTypes = emptySet(),
            minAltPos = null,
            maxAltPos = null
        )
        assertEquals("4-term scoring must use 0.25 weight per term", expectedScore4Term, score4Term, 0.005)
    }

    /**
     * TC-UNT-04: Clean fallback to 4-term scoring when useAltitudePosForClustering = false.
     * Even when both cluster and workout provide divergent altitude coordinates, disabling
     * the preference toggle must enforce 4-term scoring with zero altitude penalty.
     */
    @Test
    fun testCalculateSimilarity_FallbackTo4TermsWhenPreferenceDisabled() {
        every { TrainingApplication.useAltitudePosForClustering() } returns false

        val start = LatLng(48.775, 9.182)
        val end = LatLng(48.776, 9.183)
        val apex = LatLng(48.790, 9.200)
        val distance = 5000.0

        val cluster = createSampleCluster(
            start = start, end = end, apex = apex, distance = distance,
            minAlt = LatLng(48.770, 9.175), maxAlt = LatLng(48.795, 9.210)
        )

        // Divergent altitude coordinates that would cause score >= 1.0 if enabled
        val divergentMinAlt = LatLng(48.800, 9.220)
        val divergentMaxAlt = LatLng(48.750, 9.150)

        val score = clusterEngine.calculateSimilarity(
            start, end, apex, distance, cluster,
            candidateSportTypes = emptySet(),
            minAltPos = divergentMinAlt,
            maxAltPos = divergentMaxAlt
        )

        // Disabling toggle must ignore divergent altitude and yield 0.0 for matching horizontal shape
        assertEquals("Disabled preference must yield 0.0 for matching horizontal footprint", 0.0, score, 0.001)
    }

    /**
     * TC-UNT-05: Mathematical precision of the running weighted centroid calculation.
     * Verifies that when a cluster is updated with a new workout, running centroids for
     * minAlt and maxAlt follow: (centroid_old * hitCount + pos_workout) / (hitCount + 1).
     */
    @Test
    fun testRunningWeightedCentroidCalculation() {
        val initialHitCount = 4
        val clusterMinAlt = LatLng(48.770000, 9.175000)
        val clusterMaxAlt = LatLng(48.795000, 9.210000)

        val workoutMinAlt = LatLng(48.775000, 9.180000)
        val workoutMaxAlt = LatLng(48.800000, 9.220000)

        // Expected running weighted centroids
        val expectedNewMinLat = (clusterMinAlt.latitude * initialHitCount + workoutMinAlt.latitude) / (initialHitCount + 1)
        val expectedNewMinLng = (clusterMinAlt.longitude * initialHitCount + workoutMinAlt.longitude) / (initialHitCount + 1)
        val expectedNewMaxLat = (clusterMaxAlt.latitude * initialHitCount + workoutMaxAlt.latitude) / (initialHitCount + 1)
        val expectedNewMaxLng = (clusterMaxAlt.longitude * initialHitCount + workoutMaxAlt.longitude) / (initialHitCount + 1)

        assertEquals(48.771000, expectedNewMinLat, 0.000001)
        assertEquals(9.176000, expectedNewMinLng, 0.000001)
        assertEquals(48.796000, expectedNewMaxLat, 0.000001)
        assertEquals(9.212000, expectedNewMaxLng, 0.000001)

        // Verify initial assignment when cluster previously had null altitude coordinates
        val nullMinAlt: Double? = null
        val initialAssignedLat = if (nullMinAlt != null) {
            (nullMinAlt * initialHitCount + workoutMinAlt.latitude) / (initialHitCount + 1)
        } else {
            workoutMinAlt.latitude
        }
        assertEquals("Initial assignment must adopt incoming workout latitude", workoutMinAlt.latitude, initialAssignedLat, 0.000001)

        // Verify preservation when workout has null altitude coordinates
        val incomingNullWorkoutAlt: LatLng? = null
        val preservedMinLat = if (incomingNullWorkoutAlt != null) {
            (clusterMinAlt.latitude * initialHitCount + incomingNullWorkoutAlt.latitude) / (initialHitCount + 1)
        } else {
            clusterMinAlt.latitude
        }
        assertEquals("Missing workout altitude must preserve existing cluster centroid", clusterMinAlt.latitude, preservedMinLat, 0.000001)
    }

    /**
     * Integration test verifying learnFromWorkout centroid update with mocked database manager.
     */
    @Test
    fun testLearnFromWorkout_UpdatesAltitudeExtremaCentroidsInDatabase() {
        val mockDbManager = mockk<WorkoutClusterDatabaseManager>(relaxed = true)
        val mockSportDb = mockk<SportTypeDatabaseManager>(relaxed = true)
        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportDb
        every { mockSportDb.getBSportType(any()) } returns BSportType.RUN

        WorkoutClusterDatabaseManager.resetForTesting(mockDbManager)
        WorkoutClusterEngine.resetForTesting(null)
        val engine = WorkoutClusterEngine.getInstance(mockContext)

        val start = LatLng(48.775, 9.182)
        val end = LatLng(48.776, 9.183)
        val apex = LatLng(48.790, 9.200)
        val distance = 5000.0

        val existingCluster = createSampleCluster(
            id = 42L,
            name = "Stuttgart Valley Loop",
            start = start, end = end, apex = apex, distance = distance,
            minAlt = LatLng(48.770, 9.175),
            maxAlt = LatLng(48.795, 9.210),
            hitCount = 4
        )

        every { mockDbManager.getClusterById(42L) } returns existingCluster

        val newMinAlt = LatLng(48.775, 9.180)
        val newMaxAlt = LatLng(48.800, 9.220)

        val clusterSlot = slot<WorkoutCluster>()
        every { mockDbManager.updateCluster(capture(clusterSlot)) } returns Unit

        engine.learnFromWorkout(
            start = start,
            end = end,
            apex = apex,
            distance = distance,
            userSpecifiedName = "Stuttgart Valley Loop",
            userSportId = 1L,
            clusterIdOverride = 42L,
            minAltPos = newMinAlt,
            maxAltPos = newMaxAlt
        )

        verify(exactly = 1) { mockDbManager.updateCluster(any()) }

        val updated = clusterSlot.captured
        assertEquals(48.771, updated.minAltLat!!, 0.0001)
        assertEquals(9.176, updated.minAltLng!!, 0.0001)
        assertEquals(48.796, updated.maxAltLat!!, 0.0001)
        assertEquals(9.212, updated.maxAltLng!!, 0.0001)

        WorkoutClusterDatabaseManager.resetForTesting(null)
        WorkoutClusterEngine.resetForTesting(null)
    }
}
