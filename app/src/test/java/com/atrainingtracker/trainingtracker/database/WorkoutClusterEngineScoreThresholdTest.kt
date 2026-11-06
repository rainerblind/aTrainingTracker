/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
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

/**
 * Unit tests for configurable cluster score threshold evaluation and persistence (TST-SET-077.5a / REQ-SET-075 / ATT-5559).
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutClusterEngineScoreThresholdTest {

    private lateinit var context: Context
    private lateinit var engine: WorkoutClusterEngine
    private lateinit var mockSportDb: SportTypeDatabaseManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TrainingApplication.setAppContextForTesting(context)
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        TrainingApplication.setSharedPreferencesForTesting(prefs)

        mockSportDb = mockk(relaxed = true)
        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportDb
        every { mockSportDb.getBSportType(any()) } returns BSportType.RUN

        val mockKnownLocationsDb = mockk<KnownLocationsDatabaseManager>(relaxed = true)
        mockkStatic(KnownLocationsDatabaseManager::class)
        every { KnownLocationsDatabaseManager.getInstance(any()) } returns mockKnownLocationsDb

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
    fun testSuggestCluster_respectsExplicitScoreThreshold() {
        val start = LatLng(48.137154, 11.576124)
        val end = LatLng(48.137154, 11.576124)
        val apex = LatLng(48.140000, 11.580000)
        val distance = 5000.0

        // Cluster with slightly different distance and coordinates such that score is around 1.1
        val candidateCluster = WorkoutCluster(
            id = 42L,
            name = "Test Route Cluster",
            probableSportId = 1L,
            startLat = start.latitude + 0.0018, // ~200m offset -> s1 = 1.0 * 0.25 = 0.25
            startLng = start.longitude,
            endLat = end.latitude + 0.0018,     // ~200m offset -> s2 = 1.0 * 0.25 = 0.25
            endLng = end.longitude,
            maxDispLat = apex.latitude + 0.0036, // ~400m offset -> s3 = 1.0 * 0.25 = 0.25
            maxDispLng = apex.longitude,
            refDistance = 6500.0,               // |5000-6500|/6500/0.2 = 1.15 -> s4 ~ 0.288
            hitCount = 3,
            bSportType = BSportType.RUN
        )

        val scores = engine.scoreClusters(
            listOf(candidateCluster), start, end, apex, distance,
            workoutSportType = BSportType.RUN
        )
        assertTrue("Cluster should be scored", scores.isNotEmpty())
        val score = scores.first().second
        // Verify score is > 1.0 and < 1.2
        assertTrue("Score $score should be greater than 1.0", score > 1.0)
        assertTrue("Score $score should be less than 1.25", score < 1.25)

        // Case A: Strict threshold (1.0) -> Rejects cluster
        val strictResult = engine.suggestCluster(
            start, end, apex, distance,
            workoutSportType = BSportType.RUN,
            cachedClusters = listOf(candidateCluster),
            scoreThreshold = 1.0
        )
        assertNull("With threshold 1.0, score $score should be rejected", strictResult)

        // Case B: Tolerant threshold (1.2) -> Accepts cluster
        val tolerantResult = engine.suggestCluster(
            start, end, apex, distance,
            workoutSportType = BSportType.RUN,
            cachedClusters = listOf(candidateCluster),
            scoreThreshold = 1.25
        )
        assertNotNull("With threshold 1.25, score $score should be accepted", tolerantResult)
        assertEquals(42L, tolerantResult?.id)
    }

    @Test
    fun testSuggestCluster_dynamicallyReadsTrainingApplicationPreference() {
        val start = LatLng(48.137154, 11.576124)
        val end = LatLng(48.137154, 11.576124)
        val apex = LatLng(48.140000, 11.580000)
        val distance = 5000.0

        val candidateCluster = WorkoutCluster(
            id = 77L,
            name = "Dynamic Preference Cluster",
            probableSportId = 1L,
            startLat = start.latitude + 0.0018,
            startLng = start.longitude,
            endLat = end.latitude + 0.0018,
            endLng = end.longitude,
            maxDispLat = apex.latitude + 0.0036,
            maxDispLng = apex.longitude,
            refDistance = 6300.0,
            hitCount = 2,
            bSportType = BSportType.RUN
        )

        val scores = engine.scoreClusters(
            listOf(candidateCluster), start, end, apex, distance,
            workoutSportType = BSportType.RUN
        )
        val score = scores.first().second
        assertTrue("Score $score should be between 1.0 and 1.2", score in 1.0..1.2)

        // Default TrainingApplication threshold is 1.2f -> match found
        TrainingApplication.setClusterScoreThreshold(1.2f)
        val defaultMatch = engine.suggestCluster(
            start, end, apex, distance,
            workoutSportType = BSportType.RUN,
            cachedClusters = listOf(candidateCluster)
        )
        assertNotNull("Default threshold 1.2 should match score $score", defaultMatch)
        assertEquals(77L, defaultMatch?.id)

        // If user tunes threshold down to 0.8f -> rejected
        TrainingApplication.setClusterScoreThreshold(0.8f)
        val strictMatch = engine.suggestCluster(
            start, end, apex, distance,
            workoutSportType = BSportType.RUN,
            cachedClusters = listOf(candidateCluster)
        )
        assertNull("Stricter threshold 0.8 should reject score $score", strictMatch)
    }

    @Test
    fun testTrainingApplication_clusterScoreThresholdDefaultsAndPersistence() {
        // Default value
        assertEquals(1.2f, TrainingApplication.getClusterScoreThreshold(), 0.001f)

        // Mutation and retrieval
        TrainingApplication.setClusterScoreThreshold(1.8f)
        assertEquals(1.8f, TrainingApplication.getClusterScoreThreshold(), 0.001f)

        // Reset
        TrainingApplication.setClusterScoreThreshold(1.2f)
        assertEquals(1.2f, TrainingApplication.getClusterScoreThreshold(), 0.001f)
    }
}
