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
import com.atrainingtracker.trainingtracker.migration.BackupRestoreViewModel
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying TCX import cluster auto-matching parity and lossless candidate discovery
 * in accordance with REQ-MIG-029 and TST-MIG-026 (ATT-1133).
 */
class TcxImportClusterParityTest {

    private lateinit var mockContext: Context
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

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getClusterTolEndpoints() } returns 200f
        every { TrainingApplication.getClusterTolApex() } returns 400f
        every { TrainingApplication.getClusterTolDistance() } returns 0.20f
        every { TrainingApplication.getClusterTolAltitudePos() } returns 400f
        every { TrainingApplication.useSportTypeForClustering() } returns true
        every { TrainingApplication.useAltitudePosForClustering() } returns true

        mockContext = mockk(relaxed = true)
        mockClusterDb = mockk(relaxed = true)
        mockSportDb = mockk(relaxed = true)

        WorkoutClusterDatabaseManager.resetForTesting(mockClusterDb)
        WorkoutClusterEngine.resetForTesting(null)
        clusterEngine = WorkoutClusterEngine.getInstance(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * TST-MIG-026 Step 2-3:
     * When start point is displaced by 350m (> 200m endpoint tolerance), but composite similarity is < 1.0,
     * suggestCluster() MUST evaluate the cluster without premature SQLite pruning and return it.
     */
    @Test
    fun testSuggestClusterMatchesWhenStartPointDisplacedBeyondEndpointTol() {
        val existingCluster = WorkoutCluster(
            id = 42L,
            name = "River Loop",
            probableSportId = 1L,
            startLat = 48.0000,
            startLng = 11.0000,
            endLat = 48.0000,
            endLng = 11.0000,
            maxDispLat = 48.0500,
            maxDispLng = 11.0000,
            refDistance = 10000.0,
            hitCount = 5,
            bSportType = BSportType.BIKE
        )
        every { mockClusterDb.getAllClusters() } returns listOf(existingCluster)

        // Workout starting 350m north (~0.00315 degrees lat), same end, apex, distance, and sport
        val workoutStart = LatLng(48.00315, 11.0000)
        val workoutEnd = LatLng(48.0000, 11.0000)
        val workoutApex = LatLng(48.0500, 11.0000)
        val distance = 10000.0

        val suggestion = clusterEngine.suggestCluster(
            start = workoutStart,
            end = workoutEnd,
            apex = workoutApex,
            distance = distance,
            workoutName = null,
            candidateSportTypes = setOf(BSportType.BIKE)
        )

        assertNotNull("suggestCluster must match cluster when composite score < 1.0 even with start point > 200m", suggestion)
        assertEquals(42L, suggestion?.id)
        assertEquals("River Loop", suggestion?.name)

        // Verify that score is indeed < 1.0
        val score = clusterEngine.calculateSimilarity(
            workoutStart, workoutEnd, workoutApex, distance, existingCluster,
            candidateSportTypes = setOf(BSportType.BIKE)
        )
        assertTrue("Composite score ($score) should be well below 1.0", score < 1.0)
    }

    /**
     * TST-MIG-026 Step 6:
     * Verify 100% mathematical parity between suggestCluster() and scoreClusters().
     */
    @Test
    fun testSuggestClusterParityWithScoreClusters() {
        val clusterA = WorkoutCluster(
            id = 1L, name = "Route A", probableSportId = 1L,
            startLat = 48.0000, startLng = 11.0000,
            endLat = 48.0000, endLng = 11.0000,
            maxDispLat = 48.0300, maxDispLng = 11.0000,
            refDistance = 5000.0, hitCount = 10,
            bSportType = BSportType.RUN
        )
        val clusterB = WorkoutCluster(
            id = 2L, name = "Route B", probableSportId = 1L,
            startLat = 48.0020, startLng = 11.0000,
            endLat = 48.0000, endLng = 11.0000,
            maxDispLat = 48.0300, maxDispLng = 11.0000,
            refDistance = 5100.0, hitCount = 3,
            bSportType = BSportType.RUN
        )
        val allClusters = listOf(clusterA, clusterB)
        every { mockClusterDb.getAllClusters() } returns allClusters

        val start = LatLng(48.0005, 11.0000)
        val end = LatLng(48.0000, 11.0000)
        val apex = LatLng(48.0300, 11.0000)
        val distance = 5000.0
        val sports = setOf(BSportType.RUN)

        val suggestion = clusterEngine.suggestCluster(start, end, apex, distance, "Route A", sports)
        val scoredList = clusterEngine.scoreClusters(allClusters, start, end, apex, distance, "Route A", sports)

        val bestScored = scoredList.firstOrNull { it.second < 1.0 }?.first
        assertNotNull(suggestion)
        assertEquals("suggestCluster must return the identical best candidate as scoreClusters", bestScored?.id, suggestion?.id)
    }

    /**
     * TST-MIG-026 Step 5:
     * Verify that ClusterInteraction properly stores and preserves all scoring parameters
     * (workoutName, candidateSportTypes, minAltPos, maxAltPos).
     */
    @Test
    fun testClusterInteractionParameterPreservation() {
        val start = LatLng(48.0, 11.0)
        val end = LatLng(48.0, 11.0)
        val apex = LatLng(48.05, 11.0)
        val minAlt = LatLng(48.01, 11.01)
        val maxAlt = LatLng(48.04, 11.04)
        val deferred = CompletableDeferred<Pair<Long?, String?>>()

        val interaction = BackupRestoreViewModel.ClusterInteraction(
            date = "2026-09-18 10:00:00",
            start = start,
            end = end,
            apex = apex,
            distance = 10000.0,
            bSportType = BSportType.BIKE,
            polyline = "mock_polyline",
            deferred = deferred,
            workoutName = "Morning Ride",
            candidateSportTypes = setOf(BSportType.BIKE),
            minAltPos = minAlt,
            maxAltPos = maxAlt
        )

        assertEquals("Morning Ride", interaction.workoutName)
        assertEquals(setOf(BSportType.BIKE), interaction.candidateSportTypes)
        assertEquals(minAlt, interaction.minAltPos)
        assertEquals(maxAlt, interaction.maxAltPos)
    }

    /**
     * TST-MIG-026 Step 7:
     * When all clusters genuinely diverge (similarity score >= 1.0), suggestCluster returns null.
     */
    @Test
    fun testSuggestClusterReturnsNullWhenScoreExceedsThreshold() {
        val existingCluster = WorkoutCluster(
            id = 99L, name = "Far Away", probableSportId = 1L,
            startLat = 40.0000, startLng = 10.0000,
            endLat = 40.0000, endLng = 10.0000,
            maxDispLat = 40.0500, maxDispLng = 10.0000,
            refDistance = 10000.0, hitCount = 1,
            bSportType = BSportType.BIKE
        )
        every { mockClusterDb.getAllClusters() } returns listOf(existingCluster)

        val workoutStart = LatLng(48.0, 11.0) // Hundreds of kilometers away
        val suggestion = clusterEngine.suggestCluster(
            start = workoutStart,
            end = workoutStart,
            apex = workoutStart,
            distance = 10000.0,
            candidateSportTypes = setOf(BSportType.BIKE)
        )

        assertNull("suggestCluster must return null when no cluster has similarity < 1.0", suggestion)
    }
}
