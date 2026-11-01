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
import com.atrainingtracker.trainingtracker.ui.map.ThermalColorLut
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

    /**
     * TST-MAP-058.12: Unified Corridor Pass Count Calibration (ATT-4929 / REQ-PER-024.4 / REQ-MAP-056.6).
     */
    @Test
    fun testUnifiedCorridorPassCountCalibration_TST_MAP_058_12() {
        // When maxCorridorPassCount is explicitly passed, it is retained and propagated
        val visualsWithExplicit = getPeriodMapVisuals(PeriodType.MONTH, listOf(samplePath), maxCorridorPassCount = 42)
        assertEquals(42, visualsWithExplicit.maxCorridorPassCount)
        val provider = visualsWithExplicit.heatmapProvider as PolylineHeatmapTileProvider
        assertEquals(42, provider.maxCorridorPassCount)

        // When omitted, estimateMaxCorridorPassCount is automatically calibrated
        val visualsAuto = getPeriodMapVisuals(PeriodType.MONTH, listOf(samplePath, samplePath, samplePath))
        assertEquals(3, visualsAuto.maxCorridorPassCount)
    }

    /**
     * TST-MAP-059.4: Parameterized Warm Hot Palette and Container Dampening Propagation (ATT-4933 / REQ-MAP-057.4).
     */
    @Test
    fun testWarmHotPaletteAndMiniMapPropagation_TST_MAP_059_4() {
        // Default call for non-interactive card map should use isMiniMap = true and colorLut = ThermalColorLut.HOT
        val miniMapVisuals = getPeriodMapVisuals(PeriodType.MONTH, listOf(samplePath), isInteractive = false)
        val miniProvider = miniMapVisuals.heatmapProvider as PolylineHeatmapTileProvider
        assertTrue("Card mini-map must set isMiniMap = true", miniProvider.isMiniMap)
        assertSame("Card mini-map must default to ThermalColorLut.HOT", ThermalColorLut.HOT, miniProvider.colorLut)

        // Interactive detail map should set isMiniMap = false
        val detailVisuals = getPeriodMapVisuals(PeriodType.MONTH, listOf(samplePath), isInteractive = true)
        val detailProvider = detailVisuals.heatmapProvider as PolylineHeatmapTileProvider
        assertFalse("Interactive detail map must set isMiniMap = false", detailProvider.isMiniMap)
        assertSame("Interactive detail map must default to ThermalColorLut.HOT", ThermalColorLut.HOT, detailProvider.colorLut)

        // Custom palette can still be passed for decoupling
        val customVisuals = getPeriodMapVisuals(
            PeriodType.MONTH,
            listOf(samplePath),
            colorLut = ThermalColorLut.BLUE
        )
        val customProvider = customVisuals.heatmapProvider as PolylineHeatmapTileProvider
        assertSame("Custom LUT must be retained", ThermalColorLut.BLUE, customProvider.colorLut)
    }
}
