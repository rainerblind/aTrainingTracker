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

package com.atrainingtracker.trainingtracker.climbs

import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for FIETS Index & Strava/Garmin climb categorization formulas,
 * dynamic resolution, and altitude bonus calculations (REQ-CLM-010, TST-CLM-010).
 */
class ClimbCategorizationFormulaTest {

    @Test
    fun testFietsIndex_calculationAndAltitudeBonus() {
        // TST-CLM-010.1: Base FIETS = H^2 / (10 * D)
        // 1000m distance, 150m gain = 150^2 / (10 * 1000) = 22500 / 10000 = 2.25
        val baseScore = ClimbCategory.calculateFietsIndex(
            distanceMeters = 1000.0,
            elevationGainMeters = 150.0,
            summitAltitudeMeters = 500.0 // <= 1000m, no bonus
        )
        assertEquals(2.25, baseScore, 0.001)
        assertEquals(ClimbCategory.CAT_3, ClimbCategory.fromFietsIndex(1000.0, 150.0, 500.0))

        // High-altitude pass: 15 km, 1000m gain, summit 2200m
        // Base = 1000^2 / (10 * 15000) = 1000000 / 150000 = 6.6667
        // Bonus = (2200 - 1000) / 1000 = 1.2
        // Total = 6.6667 + 1.2 = 7.8667 -> HC
        val passScore = ClimbCategory.calculateFietsIndex(
            distanceMeters = 15000.0,
            elevationGainMeters = 1000.0,
            summitAltitudeMeters = 2200.0
        )
        assertEquals(7.867, passScore, 0.01)
        assertEquals(ClimbCategory.HC, ClimbCategory.fromFietsIndex(15000.0, 1000.0, 2200.0))
    }

    @Test
    fun testFietsIndex_categoryThresholds() {
        // HC >= 6.5, CAT_1 >= 5.0, CAT_2 >= 3.5, CAT_3 >= 2.0, CAT_4 >= 0.5, UNCATEGORIZED < 0.5
        // D = 1000m, H^2 / 10000 = score -> H = sqrt(score * 10000)
        // score 6.5 -> H = sqrt(65000) = 254.95m
        assertEquals(ClimbCategory.HC, ClimbCategory.fromFietsIndex(1000.0, 255.0))
        // score 5.0 -> H = sqrt(50000) = 223.6m
        assertEquals(ClimbCategory.CAT_1, ClimbCategory.fromFietsIndex(1000.0, 224.0))
        // score 3.5 -> H = sqrt(35000) = 187.08m
        assertEquals(ClimbCategory.CAT_2, ClimbCategory.fromFietsIndex(1000.0, 188.0))
        // score 2.0 -> H = sqrt(20000) = 141.42m
        assertEquals(ClimbCategory.CAT_3, ClimbCategory.fromFietsIndex(1000.0, 142.0))
        // score 0.5 -> H = sqrt(5000) = 70.71m
        assertEquals(ClimbCategory.CAT_4, ClimbCategory.fromFietsIndex(1000.0, 71.0))
        // score < 0.5 -> H = 50m -> score = 2500 / 10000 = 0.25 -> UC
        assertEquals(ClimbCategory.UNCATEGORIZED, ClimbCategory.fromFietsIndex(1000.0, 50.0))
    }

    @Test
    fun testStravaGarmin_formulaBackwardCompatibility() {
        // TST-CLM-010.2: Score = dist * avgGrade = 100 * gain
        // HC >= 80000, CAT_1 >= 64000, CAT_2 >= 32000, CAT_3 >= 16000, CAT_4 >= 8000
        assertEquals(ClimbCategory.HC, ClimbCategory.fromScore(85000.0))
        assertEquals(ClimbCategory.CAT_1, ClimbCategory.fromScore(65000.0))
        assertEquals(ClimbCategory.CAT_2, ClimbCategory.fromScore(35000.0))
        assertEquals(ClimbCategory.CAT_3, ClimbCategory.fromScore(18000.0))
        assertEquals(ClimbCategory.CAT_4, ClimbCategory.fromScore(9000.0))
        assertEquals(ClimbCategory.UNCATEGORIZED, ClimbCategory.fromScore(5000.0))
    }

    @Test
    fun testClimb_dynamicCategoryResolutionWithoutDbRebuild() {
        // TST-CLM-010.3:
        // A steep punchy climb: 1000m @ 15% (gain = 150m, summit = 450m)
        // Under Strava/Garmin: score = 1000 * 15 = 15,000 -> CAT_4 (< 16,000 threshold for CAT_3)
        // Under FIETS: base = 150^2 / (10 * 1000) = 2.25 -> CAT_3 (>= 2.0 threshold)
        val punchyClimb = Climb(
            id = 42L,
            name = "Mur Ramp",
            distanceMeters = 1000.0,
            elevationGainMeters = 150.0,
            avgGradePercent = 15.0,
            maxGradePercent = 18.0,
            startLat = 48.0,
            startLng = 11.0,
            endLat = 48.01,
            endLng = 11.0,
            category = ClimbCategory.CAT_4, // Stored historically under Strava
            pathPoints = listOf(
                PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 300.0),
                PathPoint(distance = 1000.0, latLng = LatLng(48.01, 11.0), altitude = 450.0)
            )
        )

        // Dynamic resolution under FIETS
        assertEquals(ClimbCategory.CAT_3, punchyClimb.resolveCategory(ClimbScoringFormula.FIETS))
        // Dynamic resolution under STRAVA_GARMIN
        assertEquals(ClimbCategory.CAT_4, punchyClimb.resolveCategory(ClimbScoringFormula.STRAVA_GARMIN))
    }
}
