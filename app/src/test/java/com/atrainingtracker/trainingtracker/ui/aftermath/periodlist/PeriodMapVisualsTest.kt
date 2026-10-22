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

import com.atrainingtracker.trainingtracker.ui.map.PolylineHeatmapTileProvider
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [getPeriodMapVisuals] (ATT-4480 / TST-PER-023).
 */
class PeriodMapVisualsTest {

    private val samplePath = listOf(
        LatLng(48.5, 9.0),
        LatLng(48.6, 9.1)
    )

    @Test
    fun testDayPeriodReturnsNullHeatmapProvider() {
        val visuals = getPeriodMapVisuals(PeriodType.DAY, listOf(samplePath))
        assertNull(visuals.heatmapProvider)
        assertEquals(1.0f, visuals.polylineAlpha, 0.001f)
        assertEquals(8f, visuals.polylineWidth, 0.001f)
    }

    @Test
    fun testEmptyPathsReturnNullHeatmapProvider() {
        val visuals = getPeriodMapVisuals(PeriodType.WEEK, emptyList())
        assertNull(visuals.heatmapProvider)
    }

    @Test
    fun testWeekPeriodReturnsPolylineHeatmapProvider() {
        val visuals = getPeriodMapVisuals(PeriodType.WEEK, listOf(samplePath))
        assertNotNull(visuals.heatmapProvider)
        assertTrue(visuals.heatmapProvider is PolylineHeatmapTileProvider)
        assertEquals(0.9f, visuals.polylineAlpha, 0.001f)
        assertEquals(8f, visuals.polylineWidth, 0.001f)
    }

    @Test
    fun testMonthPeriodReturnsPolylineHeatmapProvider() {
        val visuals = getPeriodMapVisuals(PeriodType.MONTH, listOf(samplePath))
        assertNotNull(visuals.heatmapProvider)
        assertTrue(visuals.heatmapProvider is PolylineHeatmapTileProvider)
        assertEquals(0.8f, visuals.polylineAlpha, 0.001f)
        assertEquals(7f, visuals.polylineWidth, 0.001f)
    }

    @Test
    fun testYearPeriodReturnsPolylineHeatmapProvider() {
        val visuals = getPeriodMapVisuals(PeriodType.YEAR, listOf(samplePath))
        assertNotNull(visuals.heatmapProvider)
        assertTrue(visuals.heatmapProvider is PolylineHeatmapTileProvider)
        assertEquals(0.6f, visuals.polylineAlpha, 0.001f)
        assertEquals(5f, visuals.polylineWidth, 0.001f)
    }
}
