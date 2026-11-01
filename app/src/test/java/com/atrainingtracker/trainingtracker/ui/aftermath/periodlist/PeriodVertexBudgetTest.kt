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

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit verification suite for Dual Vertex Budget Enforcement & Adaptive Decimation
 * (ATT-4930 / REQ-PER-019.2 / TST-PER-034).
 */
class PeriodVertexBudgetTest {

    private fun generateSyntheticWorkoutPath(workoutIndex: Int, pointCount: Int = 200): List<LatLng> {
        val baseLat = 48.68 + (workoutIndex % 5) * 0.001
        val baseLng = 9.00 + (workoutIndex % 5) * 0.001
        return (0 until pointCount).map { i ->
            val angle = i * 0.1
            val radius = 0.0001 * i
            LatLng(baseLat + radius * Math.sin(angle), baseLng + radius * Math.cos(angle))
        }
    }

    private fun createSyntheticYearSummary(workoutCount: Int, pointsPerWorkout: Int = 200): PeriodSummary {
        val polylinesMap = (1L..workoutCount.toLong()).associateWith { id ->
            val path = generateSyntheticWorkoutPath(id.toInt(), pointsPerWorkout)
            PolyUtil.encode(path)
        }
        return PeriodSummary(
            periodLabel = "Year 2026",
            periodDateRange = "Jan 1 - Dec 31, 2026",
            periodType = PeriodType.YEAR,
            startTimestampS = 1735689600L,
            endTimestampS = 1767225599L,
            totalWorkouts = workoutCount,
            totalDurationSec = workoutCount * 3600L,
            totalDistance = workoutCount * 30000.0,
            sportStats = emptyMap(),
            workoutIdToPolylineMap = polylinesMap,
            polylines = polylinesMap.values.toList(),
            minLat = 48.60,
            maxLat = 48.75,
            minLng = 8.95,
            maxLng = 9.15,
            sortKey = "2026"
        )
    }

    @Test
    fun testBudgetDefaultConstants() {
        assertEquals(2_000, MAX_CARD_MINI_MAP_VERTICES)
        assertEquals(8_000, MAX_DETAILED_MAP_VERTICES)
    }

    /**
     * TST-PER-034.1: Card Mini-Map Hard Vertex Budget Enforcement with 300 Workouts.
     */
    @Test
    fun testTstPer034_1_CardMiniMapHardVertexBudget_300Workouts() {
        val syntheticYear = createSyntheticYearSummary(workoutCount = 300, pointsPerWorkout = 200)
        assertEquals(300, syntheticYear.polylines.size)

        val state = prepareCardMapPreviewSync(syntheticYear)

        assertTrue("State must be ready", state.isReady)
        assertFalse("Paths must not be empty", state.primaryPaths.isEmpty())

        val totalVertices = state.primaryPaths.sumOf { it.size }
        assertTrue(
            "Card mini-map aggregate vertices ($totalVertices) MUST NOT exceed MAX_CARD_MINI_MAP_VERTICES ($MAX_CARD_MINI_MAP_VERTICES)",
            totalVertices <= MAX_CARD_MINI_MAP_VERTICES
        )
    }

    /**
     * TST-PER-034.2: Detailed Map Hard Vertex Budget Enforcement with 300 Workouts.
     */
    @Test
    fun testTstPer034_2_DetailedMapHardVertexBudget_300Workouts() {
        val rawPathMap = (1L..300L).associateWith { id ->
            generateSyntheticWorkoutPath(id.toInt(), 200)
        }
        val totalRawPoints = rawPathMap.values.sumOf { it.size }
        assertEquals(60_000, totalRawPoints)

        val budgetMap = simplifyPathMapToBudget(
            pathMap = rawPathMap,
            maxVertices = MAX_DETAILED_MAP_VERTICES,
            baseTolerance = 8.0
        )

        assertEquals("All 300 workouts must be preserved in map", 300, budgetMap.size)
        val totalSimplifiedVertices = budgetMap.values.sumOf { it.size }
        assertTrue(
            "Detailed map aggregate vertices ($totalSimplifiedVertices) MUST NOT exceed MAX_DETAILED_MAP_VERTICES ($MAX_DETAILED_MAP_VERTICES)",
            totalSimplifiedVertices <= MAX_DETAILED_MAP_VERTICES
        )
    }

    /**
     * TST-PER-034.3: Base Tolerance Preservation Under Moderate Volume (Shared Corridor Alignment).
     */
    @Test
    fun testTstPer034_3_BaseTolerancePreservationUnderModerateVolume() {
        // 5 workouts with moderate curvature; total points well below budget
        val rawPaths = (1..5).map { id ->
            (0..50).map { i -> LatLng(48.68 + i * 0.0001, 9.00 + i * 0.0001) }
        }

        val baseTolerance = getPeriodSimplificationToleranceMeters(PeriodType.MONTH) // 6.0m
        val directlySimplified = rawPaths.map { PolyUtil.simplify(it, baseTolerance) }
        val directlySimplifiedTotal = directlySimplified.sumOf { it.size }
        assertTrue("Baseline points must be below card budget", directlySimplifiedTotal < MAX_CARD_MINI_MAP_VERTICES)

        val budgetResult = simplifyPathsToBudget(
            paths = rawPaths,
            maxVertices = MAX_CARD_MINI_MAP_VERTICES,
            baseTolerance = baseTolerance
        )

        assertEquals("Path count must match", directlySimplified.size, budgetResult.size)
        assertEquals("Total vertices must match direct simplification exactly", directlySimplifiedTotal, budgetResult.sumOf { it.size })
        for (i in directlySimplified.indices) {
            assertEquals("Path $i must match direct baseline simplification", directlySimplified[i], budgetResult[i])
        }
    }

    /**
     * TST-PER-034.4: Adaptive Decimation Convergence and Extreme Density Stability.
     */
    @Test
    fun testTstPer034_4_AdaptiveDecimationConvergenceAndStability() {
        // High density repetitive loops with tight budget
        val tightBudget = 500
        val densePaths = (1..50).map { id ->
            (0..300).map { i ->
                val angle = i * 0.2
                LatLng(48.5 + 0.001 * Math.sin(angle), 9.0 + 0.001 * Math.cos(angle))
            }
        }

        val result = simplifyPathsToBudget(
            paths = densePaths,
            maxVertices = tightBudget,
            baseTolerance = 5.0
        )

        val totalVertices = result.sumOf { it.size }
        assertTrue(
            "Must converge to <= $tightBudget points (was $totalVertices)",
            totalVertices <= tightBudget
        )
        assertFalse("Paths must not be empty", result.isEmpty())
        for (path in result) {
            assertTrue("Each path must have at least 2 points", path.size >= 2)
        }
    }
}
