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
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying speed-based multi-sport cluster candidate discovery and similarity evaluation
 * (REQ-SET-064, TST-SET-051, ATT-773).
 */
class SpeedBasedClusterMatchingTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private val clusterEngine = WorkoutClusterEngine.getInstance(mockContext)

    @Before
    fun setUp() {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.useSportTypeForClustering() } returns true
        every { TrainingApplication.getClusterTolEndpoints() } returns 200f
        every { TrainingApplication.getClusterTolApex() } returns 300f
        every { TrainingApplication.getClusterTolDistance() } returns 0.15f

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createSampleCluster(
        id: Long,
        name: String,
        bSportType: BSportType,
        start: LatLng = LatLng(48.775, 9.182),
        end: LatLng = LatLng(48.776, 9.183),
        apex: LatLng = LatLng(48.790, 9.200),
        distance: Double = 5000.0
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
            hitCount = 5,
            previewPaths = emptyList()
        )
    }

    @Test
    fun testCalculateSimilarity_ZeroPenaltyWhenClusterMatchesCandidateSports() {
        val start = LatLng(48.775, 9.182)
        val end = LatLng(48.776, 9.183)
        val apex = LatLng(48.790, 9.200)
        val distance = 5000.0

        val bikeCluster = createSampleCluster(1L, "Shopping by Bike", BSportType.BIKE, start, end, apex, distance)

        // Session speed supports both RUN and BIKE (e.g. ~3 m/s)
        val candidateSports = setOf(BSportType.RUN, BSportType.BIKE)

        val score = clusterEngine.calculateSimilarity(
            start, end, apex, distance, bikeCluster, null, candidateSports
        )

        // Geometrically identical route + sport match in candidate set -> score < 1.0 (no penalty)
        assertTrue("Score with candidate sport match should be < 1.0, was $score", score < 1.0)
        assertEquals(0.0, score, 0.001)
    }

    @Test
    fun testCalculateSimilarity_PenaltyAppliedWhenClusterNotInCandidateSports() {
        val start = LatLng(48.775, 9.182)
        val end = LatLng(48.776, 9.183)
        val apex = LatLng(48.790, 9.200)
        val distance = 5000.0

        val bikeCluster = createSampleCluster(1L, "Shopping by Bike", BSportType.BIKE, start, end, apex, distance)

        // Candidate sport is only RUN (e.g. runner at ~4.5 m/s)
        val candidateSports = setOf(BSportType.RUN)

        val score = clusterEngine.calculateSimilarity(
            start, end, apex, distance, bikeCluster, null, candidateSports
        )

        // Mismatched sport outside candidate set -> 5.0 penalty applied
        assertTrue("Score with mismatched sport should exceed 5.0, was $score", score >= 5.0)
    }

    @Test
    fun testCalculateSimilarity_UserPreSelectionPrecedence() {
        val start = LatLng(48.775, 9.182)
        val end = LatLng(48.776, 9.183)
        val apex = LatLng(48.790, 9.200)
        val distance = 5000.0

        val bikeCluster = createSampleCluster(1L, "Shopping by Bike", BSportType.BIKE, start, end, apex, distance)

        // User explicitly pre-selected RUN -> candidate set is strictly singleton {RUN}
        val candidateSports = setOf(BSportType.RUN)

        val score = clusterEngine.calculateSimilarity(
            start, end, apex, distance, bikeCluster, null, candidateSports
        )

        // Cycling cluster must be rejected with +5.0 penalty
        assertTrue("Bike cluster must receive 5.0 penalty when user pre-selected RUN, was $score", score >= 5.0)
    }

    @Test
    fun testCalculateSimilarity_HardwareSensorSovereignty() {
        val start = LatLng(48.775, 9.182)
        val end = LatLng(48.776, 9.183)
        val apex = LatLng(48.790, 9.200)
        val distance = 5000.0

        val runCluster = createSampleCluster(3L, "Park Loop Run", BSportType.RUN, start, end, apex, distance)

        // Dedicated cadence/power sensor connected -> candidate set is strictly singleton {BIKE}
        val candidateSports = setOf(BSportType.BIKE)

        val score = clusterEngine.calculateSimilarity(
            start, end, apex, distance, runCluster, null, candidateSports
        )

        // Running cluster must be rejected with +5.0 penalty
        assertTrue("Run cluster must receive 5.0 penalty when cadence sensor is active, was $score", score >= 5.0)
    }

    @Test
    fun testCalculateSimilarity_BackwardCompatibilitySingleSportOverload() {
        val start = LatLng(48.775, 9.182)
        val end = LatLng(48.776, 9.183)
        val apex = LatLng(48.790, 9.200)
        val distance = 5000.0

        val bikeCluster = createSampleCluster(1L, "Bike Route", BSportType.BIKE, start, end, apex, distance)

        // Single sport overload with matching sport
        val scoreMatch = clusterEngine.calculateSimilarity(start, end, apex, distance, bikeCluster, null, BSportType.BIKE)
        assertTrue("Score with single matching sport must be < 1.0, was $scoreMatch", scoreMatch < 1.0)

        // Single sport overload with mismatching sport
        val scoreMismatch = clusterEngine.calculateSimilarity(start, end, apex, distance, bikeCluster, null, BSportType.RUN)
        assertTrue("Score with single mismatching sport must exceed 5.0, was $scoreMismatch", scoreMismatch >= 5.0)

        // Single sport overload with UNKNOWN
        val scoreUnknown = clusterEngine.calculateSimilarity(start, end, apex, distance, bikeCluster, null, BSportType.UNKNOWN)
        assertTrue("Score with UNKNOWN must receive 2.0 penalty, was $scoreUnknown", scoreUnknown >= 2.0)
    }

    @Test
    fun testCandidateBSportTypes_DiscoveryManager() {
        val sportTypeManager = mockk<SportTypeDatabaseManager>()
        val discoveryManager = EquipmentAndSportTypeDiscoveryManager(
            mockContext,
            sportTypeManager = sportTypeManager,
            activeDevicesHelper = mockk(relaxed = true),
            equipmentDbHelper = mockk(relaxed = true),
            sportTypeEquipmentLinkHelper = mockk(relaxed = true),
            workoutSummariesManager = mockk(relaxed = true)
        )

        // Mock speed-based sport lookup returning sport IDs for Running (10L) and Cycling (20L)
        every { sportTypeManager.getSportTypesIdList(BSportType.UNKNOWN, 3.0) } returns listOf(10L, 20L)
        every { sportTypeManager.getBSportType(10L) } returns BSportType.RUN
        every { sportTypeManager.getBSportType(20L) } returns BSportType.BIKE

        val candidates = discoveryManager.getCandidateBSportTypes(BSportType.UNKNOWN, 3.0)

        assertEquals(2, candidates.size)
        assertTrue("Candidate sports must contain RUN", candidates.contains(BSportType.RUN))
        assertTrue("Candidate sports must contain BIKE", candidates.contains(BSportType.BIKE))
    }
}
