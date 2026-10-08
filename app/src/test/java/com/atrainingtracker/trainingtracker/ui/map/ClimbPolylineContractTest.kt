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

package com.atrainingtracker.trainingtracker.ui.map

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.ui.climbs.getClimbCategoryColors
import com.google.android.gms.maps.model.LatLng
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit and contract tests for climb polyline highlighting in [MapContentScope] (REQ-UI-298, TST-UI-258, ATT-2509).
 */
class ClimbPolylineContractTest {

    private lateinit var mapScope: MapContentScopeImpl

    @Before
    fun setUp() {
        val mockContext = mockk<Context>(relaxed = true)
        mapScope = MapContentScopeImpl(
            zoomFocus = MapZoomFocus.FIT_PRIMARY,
            primaryColor = Color.Green,
            context = mockContext,
            directionIcons = Triple(null, null, null),
            bSportType = BSportType.BIKE
        )
    }

    @Test
    fun testClimbsHighlightCreation_forAllStandardCategories() {
        val categories = listOf(
            ClimbCategory.CAT_4,
            ClimbCategory.CAT_3,
            ClimbCategory.CAT_2,
            ClimbCategory.CAT_1,
            ClimbCategory.HC
        )

        val climbs = categories.mapIndexed { idx, cat ->
            val p1 = PathPoint(latLng = LatLng(48.0 + idx * 0.01, 9.0), altitude = 400.0, distance = 0.0)
            val p2 = PathPoint(latLng = LatLng(48.01 + idx * 0.01, 9.01), altitude = 500.0, distance = 1000.0)
            val p3 = PathPoint(latLng = LatLng(48.02 + idx * 0.01, 9.02), altitude = 600.0, distance = 2000.0)
            Climb(
                id = idx.toLong(),
                name = "Climb $idx",
                startLat = p1.latLng.latitude,
                startLng = p1.latLng.longitude,
                endLat = p3.latLng.latitude,
                endLng = p3.latLng.longitude,
                distanceMeters = 2000.0,
                elevationGainMeters = 200.0,
                avgGradePercent = 10.0,
                maxGradePercent = 15.0,
                category = cat,
                pathPoints = listOf(p1, p2, p3)
            )
        }

        mapScope.collect {
            climbs(climbs)
        }

        assertEquals(5, mapScope.climbHighlights.size)

        categories.forEachIndexed { idx, cat ->
            val highlight = mapScope.climbHighlights[idx]
            val expectedColor = getClimbCategoryColors(cat).first

            assertEquals("Color mismatch for $cat", expectedColor, highlight.color)
            assertEquals("zIndex should be 25f", 25f, highlight.zIndex, 0.001f)
            assertEquals("width should be 10f", 10f, highlight.width, 0.001f)
            assertEquals(3, highlight.path.size)
            assertEquals(LatLng(48.0 + idx * 0.01, 9.0), highlight.path[0])
            assertEquals(LatLng(48.02 + idx * 0.01, 9.02), highlight.path[2])
        }
    }

    @Test
    fun testUncategorizedClimbs_areOmittedFromHighlightLayer() {
        val uncategorizedClimb = Climb(
            id = 99L,
            name = "Gentle Rise",
            startLat = 48.0,
            startLng = 9.0,
            endLat = 48.01,
            endLng = 9.01,
            distanceMeters = 500.0,
            elevationGainMeters = 10.0,
            avgGradePercent = 2.0,
            maxGradePercent = 3.0,
            category = ClimbCategory.UNCATEGORIZED,
            pathPoints = listOf(
                PathPoint(latLng = LatLng(48.0, 9.0), altitude = 400.0, distance = 0.0),
                PathPoint(latLng = LatLng(48.01, 9.01), altitude = 410.0, distance = 500.0)
            )
        )

        mapScope.collect {
            climbs(listOf(uncategorizedClimb))
        }

        assertTrue("Uncategorized climb should not generate a highlight polyline", mapScope.climbHighlights.isEmpty())
    }

    @Test
    fun testFallbackToEndpoints_whenPathPointsEmpty() {
        val climbWithoutPoints = Climb(
            id = 42L,
            name = "Sparse Climb",
            startLat = 48.5,
            startLng = 9.2,
            endLat = 48.55,
            endLng = 9.25,
            distanceMeters = 3000.0,
            elevationGainMeters = 250.0,
            avgGradePercent = 8.3,
            maxGradePercent = 12.0,
            category = ClimbCategory.CAT_2,
            pathPoints = emptyList()
        )

        mapScope.collect {
            climbs(listOf(climbWithoutPoints))
        }

        assertEquals(1, mapScope.climbHighlights.size)
        val highlight = mapScope.climbHighlights[0]
        assertEquals(2, highlight.path.size)
        assertEquals(LatLng(48.5, 9.2), highlight.path[0])
        assertEquals(LatLng(48.55, 9.25), highlight.path[1])
        assertEquals(getClimbCategoryColors(ClimbCategory.CAT_2).first, highlight.color)
    }

    @Test
    fun testDegenerateSinglePointClimb_isIgnored() {
        val degenerateClimb = Climb(
            id = 7L,
            name = "Point Climb",
            startLat = 48.0,
            startLng = 9.0,
            endLat = 48.0,
            endLng = 9.0,
            distanceMeters = 0.0,
            elevationGainMeters = 0.0,
            avgGradePercent = 0.0,
            maxGradePercent = 0.0,
            category = ClimbCategory.CAT_4,
            pathPoints = listOf(
                PathPoint(latLng = LatLng(48.0, 9.0), altitude = 400.0, distance = 0.0)
            )
        )

        mapScope.collect {
            climbs(listOf(degenerateClimb))
        }

        assertTrue("Climb with less than 2 distinct points should be omitted", mapScope.climbHighlights.isEmpty())
    }

    @Test
    fun testClear_resetsClimbHighlights() {
        val climb = Climb(
            id = 1L,
            name = "Climb",
            startLat = 48.0,
            startLng = 9.0,
            endLat = 48.01,
            endLng = 9.01,
            distanceMeters = 1000.0,
            elevationGainMeters = 80.0,
            avgGradePercent = 8.0,
            maxGradePercent = 10.0,
            category = ClimbCategory.CAT_3,
            pathPoints = listOf(
                PathPoint(latLng = LatLng(48.0, 9.0), altitude = 400.0, distance = 0.0),
                PathPoint(latLng = LatLng(48.01, 9.01), altitude = 480.0, distance = 1000.0)
            )
        )

        mapScope.collect {
            climbs(listOf(climb))
        }
        assertEquals(1, mapScope.climbHighlights.size)

        mapScope.collect {
            // empty block
        }
        assertTrue("Collecting empty block should clear climb highlights", mapScope.climbHighlights.isEmpty())
    }
}
