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

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.map.PolylineHeatmapTileProvider
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PeriodCardMapState] and [prepareCardMapPreviewSync].
 * Verifies full-period route coverage, Douglas-Peucker decimation, and background clustering.
 * (ATT-4477 / REQ-PER-019 / TST-PER-025)
 */
class PeriodCardMapStateTest {

    private fun createTestPolyline(startLat: Double, startLng: Double, count: Int = 10): String {
        val points = (0 until count).map { i ->
            LatLng(startLat + i * 0.001, startLng + i * 0.001)
        }
        return PolyUtil.encode(points)
    }

    private fun createMockSummary(
        periodType: PeriodType = PeriodType.MONTH,
        polylinesMap: Map<Long, String> = emptyMap(),
        rawPolylines: List<String> = emptyList(),
        minLat: Double = 48.0,
        maxLat: Double = 48.1,
        minLng: Double = 9.0,
        maxLng: Double = 9.1
    ): PeriodSummary {
        return PeriodSummary(
            periodLabel = "Test Period",
            periodDateRange = "Oct 1 - Oct 31, 2026",
            periodType = periodType,
            startTimestampS = 1000L,
            endTimestampS = 5000L,
            totalWorkouts = polylinesMap.size.coerceAtLeast(rawPolylines.size),
            totalDurationSec = 36000L,
            totalDistance = 50000.0,
            sportStats = emptyMap(),
            minLat = minLat,
            maxLat = maxLat,
            minLng = minLng,
            maxLng = maxLng,
            polylines = rawPolylines,
            workoutIdToPolylineMap = polylinesMap,
            sortKey = "2026_10"
        )
    }

    @Test
    fun testTstPer025_1_FullPeriodRouteIngestionWithoutEightTrackTruncation() {
        // Create 20 distinct workout polylines (exceeding the historical 8-track compromise)
        val workoutMap = (1L..20L).associateWith { id ->
            createTestPolyline(48.0 + id * 0.002, 9.0 + id * 0.002)
        }
        val summary = createMockSummary(
            periodType = PeriodType.MONTH,
            polylinesMap = workoutMap
        )

        val state = prepareCardMapPreviewSync(summary)

        assertTrue("State must be ready", state.isReady)
        assertEquals("Must ingest 100% of workouts without 8-track truncation", 20, state.primaryRegionPathsCount)
        assertEquals("Primary paths must contain all 20 decoded/simplified tracks", 20, state.primaryPaths.size)
        assertNotNull("Bounds must be resolved", state.bounds)
    }

    @Test
    fun testTstPer025_2_AdaptiveSimplificationToleranceScaling() {
        assertEquals(5.0, getPeriodSimplificationToleranceMeters(PeriodType.DAY), 0.001)
        assertEquals(15.0, getPeriodSimplificationToleranceMeters(PeriodType.WEEK), 0.001)
        assertEquals(35.0, getPeriodSimplificationToleranceMeters(PeriodType.MONTH), 0.001)
        assertEquals(75.0, getPeriodSimplificationToleranceMeters(PeriodType.YEAR), 0.001)

        // Verify that decimation reduces points on dense collinear tracks
        val densePoints = (0..100).map { i -> LatLng(48.0 + i * 0.0001, 9.0 + i * 0.0001) }
        val encodedDense = PolyUtil.encode(densePoints)
        val yearSummary = createMockSummary(
            periodType = PeriodType.YEAR,
            rawPolylines = listOf(encodedDense)
        )

        val state = prepareCardMapPreviewSync(yearSummary)
        assertTrue(state.isReady)
        assertEquals(1, state.primaryPaths.size)
        val decimatedPointsCount = state.primaryPaths[0].size
        assertTrue("Collinear 101 points must be decimated (was $decimatedPointsCount)", decimatedPointsCount < 20)
    }

    @Test
    fun testTstPer025_3_SpatialRegionClusteringAndFramingOffload() {
        // Create 2 geographically distant clusters (> 75 km apart: Stuttgart ~48.7°N, Munich ~48.1°N, span ~190km)
        val stuttgartTracks = (1L..5L).associateWith { createTestPolyline(48.78, 9.18) }
        val munichTracks = (6L..7L).associateWith { createTestPolyline(48.13, 11.58) }
        val combinedMap = stuttgartTracks + munichTracks

        val multiRegionSummary = createMockSummary(
            periodType = PeriodType.MONTH,
            polylinesMap = combinedMap,
            minLat = 48.13,
            maxLat = 48.78,
            minLng = 9.18,
            maxLng = 11.58
        )

        val state = prepareCardMapPreviewSync(multiRegionSummary)

        assertTrue(state.isReady)
        assertEquals("Multi-region period must detect 2 clusters", 2, state.regionCount)
        assertEquals("Primary cluster (Stuttgart, 5 workouts) must be selected", 5, state.primaryRegionPathsCount)
        assertNotNull(state.bounds)
        // Camera bounds must fit the primary cluster tightly without ocean / cross-country gap
        assertTrue("Bounds must encompass Stuttgart cluster", state.bounds!!.southwest.latitude >= 48.5)
    }

    @Test
    fun testTstPer025_4_PolylineHeatmapTileProviderIntegration() {
        val workoutMap = mapOf(1L to createTestPolyline(48.0, 9.0))

        // Week period: Heatmap non-null
        val weekSummary = createMockSummary(periodType = PeriodType.WEEK, polylinesMap = workoutMap)
        val weekState = prepareCardMapPreviewSync(weekSummary)
        assertNotNull("Week period must instantiate heatmap provider", weekState.visuals?.heatmapProvider)
        assertTrue(weekState.visuals?.heatmapProvider is PolylineHeatmapTileProvider)

        // Month period: Heatmap non-null
        val monthSummary = createMockSummary(periodType = PeriodType.MONTH, polylinesMap = workoutMap)
        val monthState = prepareCardMapPreviewSync(monthSummary)
        assertNotNull("Month period must instantiate heatmap provider", monthState.visuals?.heatmapProvider)
        assertTrue(monthState.visuals?.heatmapProvider is PolylineHeatmapTileProvider)

        // Year period: Heatmap non-null
        val yearSummary = createMockSummary(periodType = PeriodType.YEAR, polylinesMap = workoutMap)
        val yearState = prepareCardMapPreviewSync(yearSummary)
        assertNotNull("Year period must instantiate heatmap provider", yearState.visuals?.heatmapProvider)
        assertTrue(yearState.visuals?.heatmapProvider is PolylineHeatmapTileProvider)

        // Day period: Heatmap null (vector paths only)
        val daySummary = createMockSummary(periodType = PeriodType.DAY, polylinesMap = workoutMap)
        val dayState = prepareCardMapPreviewSync(daySummary)
        assertNull("Day period must NOT instantiate heatmap provider", dayState.visuals?.heatmapProvider)
    }

    @Test
    fun testTstPer025_5_EmptyAndMalformedGpsResilience() {
        // Empty summary
        val emptySummary = createMockSummary(polylinesMap = emptyMap(), rawPolylines = emptyList())
        val emptyState = prepareCardMapPreviewSync(emptySummary)

        assertTrue("Empty state must still report ready", emptyState.isReady)
        assertTrue("Primary paths must be empty", emptyState.primaryPaths.isEmpty())
        assertNull("Bounds must be null", emptyState.bounds)
        assertEquals(0, emptyState.regionCount)

        // Malformed encoded polyline strings
        val malformedSummary = createMockSummary(rawPolylines = listOf("%%%invalid_polyline%%%", ""))
        val malformedState = prepareCardMapPreviewSync(malformedSummary)

        assertTrue("Malformed state must not throw and report ready", malformedState.isReady)
        assertTrue("Primary paths must be empty after dropping malformed strings", malformedState.primaryPaths.isEmpty())
    }
}
