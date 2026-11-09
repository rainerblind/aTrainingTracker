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
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
}
