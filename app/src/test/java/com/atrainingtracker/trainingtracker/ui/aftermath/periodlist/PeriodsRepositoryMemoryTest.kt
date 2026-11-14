/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import android.app.Application
import android.graphics.Color
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * TST-PER-047: OOM Remediation & Map Churn Validation (ATT-7666 / REQ-PER-039).
 * Verifies:
 * 1. TST-PER-047.1: workoutIdToPathMap and workoutIdToAllPolylinesMap are kept empty in enrich().
 * 2. TST-PER-047.2: summary.polylines is bounded to MAX_SUMMARY_THUMBNAIL_TRACKS (8).
 * 3. TST-PER-047.3: PeriodMapUtils LRU preview cache hits avoid re-decoding, bounded to 64.
 * 4. TST-PER-047.4: PeriodSummaryCard configures liteMode(true) for vector polylines.
 */
class PeriodsRepositoryMemoryTest {

    private lateinit var mockApp: Application
    private lateinit var repo: PeriodsRepository

    @Before
    fun setUp() {
        mockkStatic(Color::class)
        every { Color.argb(any<Int>(), any<Int>(), any<Int>(), any<Int>()) } returns 0
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        val mockDbManager = mockk<PeriodSummariesDatabaseManager>(relaxed = true)
        val mockSummariesManager = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
        PeriodSummariesDatabaseManager.setInstanceForTesting(mockDbManager)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(mockSummariesManager)
        WorkoutRepository.resetForTesting(mockWorkoutRepo)

        mockApp = mockk(relaxed = true)
        repo = PeriodsRepository(mockApp)
        clearPeriodCardMapStateCache()
    }

    @After
    fun tearDown() {
        repo.scope.cancel()
        clearPeriodCardMapStateCache()
        PeriodSummariesDatabaseManager.setInstanceForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        WorkoutRepository.resetForTesting(null)
        PeriodsRepository.resetInstanceForTesting()
        unmockkStatic(Color::class)
        unmockkStatic(Log::class)
    }

    private fun createSampleWorkout(
        id: Long,
        sport: BSportType = BSportType.BIKE,
        points: List<LatLng> = listOf(LatLng(48.0, 9.0), LatLng(48.01, 9.01), LatLng(48.02, 9.02))
    ): WorkoutData {
        val now = LocalDateTime.now()
        val polyline = PolyUtil.encode(points)
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "workout_$id",
            workoutName = "Workout $id",
            sportId = 1L,
            sportName = sport.name,
            bSportType = sport,
            startTimeS = 1000L + id * 100L,
            formattedDate = now.toLocalDate().toString(),
            formattedTime = now.toLocalTime().toString(),
            localDateTime = now,
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = polyline,
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 15000.0,
            maxDisplacement = 5000.0,
            activeTimeSec = 2400L + id * 10L,
            totalTimeSec = 2500L,
            avgSpeedMps = 6.25,
            ascentMeters = 150L,
            descentMeters = 150L,
            minAltitude = 200.0,
            maxAltitude = 350.0,
            maxAltitudeLatLng = LatLng(48.01, 9.01),
            maxDisplacementLatLng = LatLng(48.02, 9.02),
            startLatLng = points.first(),
            endLatLng = points.last(),
            description = null,
            goal = null,
            method = null,
            stravaSportName = null,
            stravaActivityData = null,
            clusterId = 0L,
            clusterName = null
        )
    }

    private fun createBasePeriodSummary(
        totalWorkouts: Int = 10,
        longestId: Long = 1L,
        northId: Long = 2L,
        southId: Long = 3L,
        eastId: Long = 4L,
        westId: Long = 5L
    ): PeriodSummary {
        return PeriodSummary(
            periodLabel = "October 2026",
            periodDateRange = "Oct 1 - Oct 31",
            periodType = PeriodType.MONTH,
            startTimestampS = 1761955200L,
            endTimestampS = 1764633600L,
            totalWorkouts = totalWorkouts,
            totalDurationSec = 36000L,
            totalDistance = 250000.0,
            sportStats = emptyMap(),
            longestId = longestId,
            northId = northId,
            southId = southId,
            eastId = eastId,
            westId = westId,
            sortKey = "2026_10"
        )
    }

    @Test
    fun testEnrich_setsWorkoutIdToPathMapEmpty_eliminatingEagerLatLngRetention() {
        // TST-PER-047.1: Verify workoutIdToPathMap is empty to prevent retaining tens of thousands of LatLng objects
        val workouts = (1L..10L).map { createSampleWorkout(it) }
        val baseSummary = createBasePeriodSummary(totalWorkouts = 10)

        val enriched = repo.enrich(baseSummary, workouts)

        assertTrue(
            "workoutIdToPathMap must be empty in enriched PeriodSummary (REQ-PER-039.1)",
            enriched.workoutIdToPathMap.isEmpty()
        )
        assertTrue(
            "workoutIdToAllPolylinesMap must be empty to eliminate redundant polyline duplication (REQ-PER-039.2)",
            enriched.workoutIdToAllPolylinesMap.isEmpty()
        )
        assertFalse(
            "workoutIdToPolylineMap must retain spatial anchors for instant framing",
            enriched.workoutIdToPolylineMap.isEmpty()
        )
        assertTrue(
            "workoutIdToPolylineMap must contain at most 5 anchors",
            enriched.workoutIdToPolylineMap.size <= 5
        )
    }

    @Test
    fun testEnrich_boundsPolylinesCountToMaxSummaryThumbnailTracks() {
        // TST-PER-047.2: Verify summary.polylines is bounded to MAX_SUMMARY_THUMBNAIL_TRACKS (8)
        val workouts = (1L..25L).map { createSampleWorkout(it) }
        val baseSummary = createBasePeriodSummary(totalWorkouts = 25)

        val enriched = repo.enrich(baseSummary, workouts)

        assertTrue(
            "polylines count (${enriched.polylines.size}) must be <= MAX_SUMMARY_THUMBNAIL_TRACKS (8) (REQ-PER-039.2)",
            enriched.polylines.size <= MAX_SUMMARY_THUMBNAIL_TRACKS
        )
        assertEquals(
            "polylines count must equal MAX_SUMMARY_THUMBNAIL_TRACKS when sufficient workouts exist",
            MAX_SUMMARY_THUMBNAIL_TRACKS,
            enriched.polylines.size
        )
    }

    @Test
    fun testPrepareCardMapPreview_servesFromLruCacheOnHit() {
        // TST-PER-047.3: Verify identical state returned on cache hit without re-decoding
        val path1 = listOf(LatLng(48.0, 9.0), LatLng(48.05, 9.05))
        val path2 = listOf(LatLng(48.1, 9.1), LatLng(48.15, 9.15))
        val summary = createBasePeriodSummary().copy(
            polylines = listOf(PolyUtil.encode(path1), PolyUtil.encode(path2))
        )

        assertEquals(0, periodCardMapStateCache.size())

        val state1 = prepareCardMapPreviewSync(summary)
        assertEquals(1, periodCardMapStateCache.size())
        assertTrue(state1.isReady)

        val state2 = prepareCardMapPreviewSync(summary)
        assertSame(
            "Subsequent preview calls must return identical cached instance without re-decoding",
            state1,
            state2
        )
    }

    @Test
    fun testPrepareCardMapPreview_boundsCacheSizeTo64() {
        // TST-PER-047.3: Verify cache eviction when exceeding capacity
        val path = listOf(LatLng(48.0, 9.0), LatLng(48.05, 9.05))
        val poly = PolyUtil.encode(path)

        for (i in 1..80) {
            val summary = createBasePeriodSummary().copy(
                sortKey = "2026_$i",
                polylines = listOf(poly)
            )
            prepareCardMapPreviewSync(summary)
        }

        assertTrue(
            "LRU cache size (${periodCardMapStateCache.size()}) must be bounded to 64",
            periodCardMapStateCache.size() <= 64
        )
        assertEquals(64, periodCardMapStateCache.size())
    }

    @Test
    fun testPeriodSummaryCard_configuresLiteModeForVectorPolylines() {
        // TST-PER-047.4: Verify createPeriodMapOptions sets liteMode(true) when heatmap is absent,
        // and liteMode(false) when heatmap is present
        val vectorOptions = createPeriodMapOptions(hasHeatmap = false)
        assertEquals(
            "Vector polyline maps must enable lite mode to eliminate OpenGL thread churn (REQ-PER-039.4)",
            true,
            vectorOptions.liteMode
        )

        val heatmapOptions = createPeriodMapOptions(hasHeatmap = true)
        assertEquals(
            "Heatmap tile overlay maps must disable lite mode because TileOverlay requires standard map",
            false,
            heatmapOptions.liteMode
        )
    }
}
