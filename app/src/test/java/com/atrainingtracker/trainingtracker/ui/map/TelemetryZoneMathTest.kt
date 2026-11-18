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
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying TelemetryZoneMath pure geometry, boundary clamping, threshold dashes,
 * label clearance gating, and zone index classification (REQ-UI-230, TST-UI-184.1, ATT-1839).
 */
class TelemetryZoneMathTest {

    private val hrThresholds = HeartRateZoneThresholds(
        z1Max = 120,
        z2Max = 140,
        z3Max = 160,
        z4Max = 180
    )

    private val powerThresholds = PowerZoneThresholds(
        z1Max = 150,
        z2Max = 200,
        z3Max = 250,
        z4Max = 300,
        z5Max = 350,
        z6Max = 450
    )

    @Test
    fun testCalculateHeartRateZoneBands_fullSpanAcrossAll5Zones() {
        // Data spans from 100 to 195 bpm (covering all 5 zones)
        val bands = TelemetryZoneMath.calculateHeartRateZoneBands(hrThresholds, 100.0, 195.0)

        assertEquals(5, bands.size)
        // Zone 1: 100..120
        assertEquals(1, bands[0].zoneIndex)
        assertEquals(100.0, bands[0].minVal, 1e-4)
        assertEquals(120.0, bands[0].maxVal, 1e-4)
        assertEquals("Z1", bands[0].label)

        // Zone 2: 120..140
        assertEquals(2, bands[1].zoneIndex)
        assertEquals(120.0, bands[1].minVal, 1e-4)
        assertEquals(140.0, bands[1].maxVal, 1e-4)
        assertEquals("Z2", bands[1].label)

        // Zone 3: 140..160
        assertEquals(3, bands[2].zoneIndex)
        assertEquals(140.0, bands[2].minVal, 1e-4)
        assertEquals(160.0, bands[2].maxVal, 1e-4)
        assertEquals("Z3", bands[2].label)

        // Zone 4: 160..180
        assertEquals(4, bands[3].zoneIndex)
        assertEquals(160.0, bands[3].minVal, 1e-4)
        assertEquals(180.0, bands[3].maxVal, 1e-4)
        assertEquals("Z4", bands[3].label)

        // Zone 5: 180..195
        assertEquals(5, bands[4].zoneIndex)
        assertEquals(180.0, bands[4].minVal, 1e-4)
        assertEquals(195.0, bands[4].maxVal, 1e-4)
        assertEquals("Z5", bands[4].label)
    }

    @Test
    fun testCalculateHeartRateZoneBands_partialSpanClamping() {
        // Narrow data range: 135 to 155 bpm (only intersects Zone 2 and Zone 3)
        val bands = TelemetryZoneMath.calculateHeartRateZoneBands(hrThresholds, 135.0, 155.0)

        assertEquals(2, bands.size)
        // Zone 2: 135..140
        assertEquals(2, bands[0].zoneIndex)
        assertEquals(135.0, bands[0].minVal, 1e-4)
        assertEquals(140.0, bands[0].maxVal, 1e-4)

        // Zone 3: 140..155
        assertEquals(3, bands[1].zoneIndex)
        assertEquals(140.0, bands[1].minVal, 1e-4)
        assertEquals(155.0, bands[1].maxVal, 1e-4)
    }

    @Test
    fun testCalculatePowerZoneBands_fullSpanAcrossAll7Zones() {
        val bands = TelemetryZoneMath.calculatePowerZoneBands(powerThresholds, 0.0, 550.0)

        assertEquals(7, bands.size)
        assertEquals(150.0, bands[0].maxVal, 1e-4)
        assertEquals(200.0, bands[1].maxVal, 1e-4)
        assertEquals(250.0, bands[2].maxVal, 1e-4)
        assertEquals(300.0, bands[3].maxVal, 1e-4)
        assertEquals(350.0, bands[4].maxVal, 1e-4)
        assertEquals(450.0, bands[5].maxVal, 1e-4)
        assertEquals(550.0, bands[6].maxVal, 1e-4)
        assertEquals("Z6", bands[5].label)
        assertEquals("Z7", bands[6].label)
    }

    @Test
    fun testCalculatePowerZoneBands_subThresholdRide_omitsZone7() {
        // DataMax <= z6Max (e.g. 400W), Zone 7 should not be rendered
        val bands = TelemetryZoneMath.calculatePowerZoneBands(powerThresholds, 0.0, 400.0)

        assertEquals(6, bands.size)
        assertEquals(6, bands.last().zoneIndex)
        assertEquals(400.0, bands.last().maxVal, 1e-4)
    }

    @Test
    fun testCalculateThresholdDashes_onlyIncludesVisibleRange() {
        // Range 130..170 intersects z2Max (140) and z3Max (160)
        val dashes = TelemetryZoneMath.calculateThresholdDashes(
            z1Max = 120,
            z2Max = 140,
            z3Max = 160,
            z4Max = 180,
            dataMin = 130.0,
            dataMax = 170.0
        )

        assertEquals(listOf(140.0, 160.0), dashes)
    }

    @Test
    fun testCalculatePowerThresholdDashes_returnsAll6Thresholds_tst_ui_383_2() {
        // powerThresholds: z1=150, z2=200, z3=250, z4=300, z5=350, z6=450
        // Full range covering all 6 thresholds: 50.0..600.0
        val allDashes = TelemetryZoneMath.calculatePowerThresholdDashes(
            thresholds = powerThresholds,
            dataMin = 50.0,
            dataMax = 600.0
        )
        assertEquals(listOf(150.0, 200.0, 250.0, 300.0, 350.0, 450.0), allDashes)

        // Bounded range: 220.0..380.0 (intersects z3Max=250, z4Max=300, z5Max=350)
        val boundedDashes = TelemetryZoneMath.calculatePowerThresholdDashes(
            thresholds = powerThresholds,
            dataMin = 220.0,
            dataMax = 380.0
        )
        assertEquals(listOf(250.0, 300.0, 350.0), boundedDashes)

        // HR threshold dashes invariance: strictly 4 thresholds
        val hrDashes = TelemetryZoneMath.calculateThresholdDashes(
            z1Max = 120,
            z2Max = 140,
            z3Max = 160,
            z4Max = 180,
            dataMin = 50.0,
            dataMax = 600.0
        )
        assertEquals(listOf(120.0, 140.0, 160.0, 180.0), hrDashes)
    }

    @Test
    fun testShouldRenderZoneLabel_clearanceThreshold() {
        assertTrue("Height >= 28f must render label", TelemetryZoneMath.shouldRenderZoneLabel(28f, 28f))
        assertTrue("Height > 28f must render label", TelemetryZoneMath.shouldRenderZoneLabel(45f, 28f))
        assertFalse("Height < 28f must suppress label", TelemetryZoneMath.shouldRenderZoneLabel(27.9f, 28f))
        assertFalse("Zero height must suppress label", TelemetryZoneMath.shouldRenderZoneLabel(0f, 28f))
    }

    @Test
    fun testDetermineHeartRateZone_mapsCorrectly() {
        assertEquals(1, TelemetryZoneMath.determineHeartRateZone(110.0, hrThresholds))
        assertEquals(1, TelemetryZoneMath.determineHeartRateZone(120.0, hrThresholds))
        assertEquals(2, TelemetryZoneMath.determineHeartRateZone(121.0, hrThresholds))
        assertEquals(2, TelemetryZoneMath.determineHeartRateZone(140.0, hrThresholds))
        assertEquals(3, TelemetryZoneMath.determineHeartRateZone(141.0, hrThresholds))
        assertEquals(3, TelemetryZoneMath.determineHeartRateZone(160.0, hrThresholds))
        assertEquals(4, TelemetryZoneMath.determineHeartRateZone(161.0, hrThresholds))
        assertEquals(4, TelemetryZoneMath.determineHeartRateZone(180.0, hrThresholds))
        assertEquals(5, TelemetryZoneMath.determineHeartRateZone(181.0, hrThresholds))
        assertEquals(5, TelemetryZoneMath.determineHeartRateZone(210.0, hrThresholds))
    }

    @Test
    fun testDeterminePowerZone_mapsCorrectlyAcrossAll7Zones() {
        assertEquals(1, TelemetryZoneMath.determinePowerZone(0.0, powerThresholds)) // Zero / negative safe
        assertEquals(1, TelemetryZoneMath.determinePowerZone(100.0, powerThresholds))
        assertEquals(2, TelemetryZoneMath.determinePowerZone(175.0, powerThresholds))
        assertEquals(3, TelemetryZoneMath.determinePowerZone(225.0, powerThresholds))
        assertEquals(4, TelemetryZoneMath.determinePowerZone(280.0, powerThresholds))
        assertEquals(5, TelemetryZoneMath.determinePowerZone(330.0, powerThresholds))
        assertEquals(6, TelemetryZoneMath.determinePowerZone(400.0, powerThresholds))
        assertEquals(7, TelemetryZoneMath.determinePowerZone(500.0, powerThresholds))
    }

    @Test
    fun testInvalidDataRanges_returnsEmptyList() {
        assertTrue(TelemetryZoneMath.calculateHeartRateZoneBands(hrThresholds, 150.0, 150.0).isEmpty())
        assertTrue(TelemetryZoneMath.calculateHeartRateZoneBands(hrThresholds, 160.0, 150.0).isEmpty())
        assertTrue(TelemetryZoneMath.calculatePowerZoneBands(powerThresholds, 200.0, 100.0).isEmpty())
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testLoadHeartRateThresholds_bike_loadsHrBike() {
        val context = mockk<Context>()
        mockkStatic(SettingsDataStoreJavaHelper::class)
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.HR_BIKE, 1) } returns 120
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.HR_BIKE, 2) } returns 140
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.HR_BIKE, 3) } returns 160
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.HR_BIKE, 4) } returns 180

        val result = TelemetryZoneMath.loadHeartRateThresholds(context, BSportType.BIKE)
        assertEquals(HeartRateZoneThresholds(120, 140, 160, 180), result)
    }

    @Test
    fun testLoadHeartRateThresholds_run_loadsHrRun() {
        val context = mockk<Context>()
        mockkStatic(SettingsDataStoreJavaHelper::class)
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.HR_RUN, 1) } returns 125
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.HR_RUN, 2) } returns 145
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.HR_RUN, 3) } returns 165
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.HR_RUN, 4) } returns 185

        val result = TelemetryZoneMath.loadHeartRateThresholds(context, BSportType.RUN)
        assertEquals(HeartRateZoneThresholds(125, 145, 165, 185), result)
    }

    @Test
    fun testLoadHeartRateThresholds_invalidValues_returnsNull() {
        val context = mockk<Context>()
        mockkStatic(SettingsDataStoreJavaHelper::class)
        // Zero threshold
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.HR_RUN, 1) } returns 0
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.HR_RUN, 2) } returns 145
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.HR_RUN, 3) } returns 165
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.HR_RUN, 4) } returns 185

        assertNull(TelemetryZoneMath.loadHeartRateThresholds(context, BSportType.RUN))

        // Non-ascending thresholds (z2 <= z1)
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.HR_RUN, 1) } returns 150
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.HR_RUN, 2) } returns 140
        assertNull(TelemetryZoneMath.loadHeartRateThresholds(context, BSportType.RUN))
    }

    @Test
    fun testLoadPowerThresholds_loadsPwrBike() {
        val context = mockk<Context>()
        mockkStatic(SettingsDataStoreJavaHelper::class)
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.PWR_BIKE, 1) } returns 150
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.PWR_BIKE, 2) } returns 200
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.PWR_BIKE, 3) } returns 250
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.PWR_BIKE, 4) } returns 300
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.PWR_BIKE, 5) } returns 350
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.PWR_BIKE, 6) } returns 450

        val result = TelemetryZoneMath.loadPowerThresholds(context)
        assertEquals(PowerZoneThresholds(150, 200, 250, 300, 350, 450), result)
    }

    @Test
    fun testLoadPowerThresholds_invalidValues_returnsNull() {
        val context = mockk<Context>()
        mockkStatic(SettingsDataStoreJavaHelper::class)
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.PWR_BIKE, 1) } returns 150
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.PWR_BIKE, 2) } returns 0
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.PWR_BIKE, 3) } returns 250
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.PWR_BIKE, 4) } returns 300
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.PWR_BIKE, 5) } returns 350
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.PWR_BIKE, 6) } returns 450

        assertNull(TelemetryZoneMath.loadPowerThresholds(context))
    }

    @Test
    fun testDetermineHrvDfaA1ZoneAndColor_preservesPolarityAndCustomThresholds() {
        // Default T1 = 0.75, T2 = 0.50 (REQ-HRV-044.1 / TrackingHrvZoneIndicatorTest parity)
        assertEquals(1, TelemetryZoneMath.determineHrvDfaA1Zone(0.751))
        assertEquals(TTColor.Zone2, TelemetryZoneMath.resolveHrvDfaA1ZoneColor(0.751))

        assertEquals(2, TelemetryZoneMath.determineHrvDfaA1Zone(0.75))
        assertEquals(TTColor.Zone3, TelemetryZoneMath.resolveHrvDfaA1ZoneColor(0.75))
        assertEquals(2, TelemetryZoneMath.determineHrvDfaA1Zone(0.50))
        assertEquals(TTColor.Zone3, TelemetryZoneMath.resolveHrvDfaA1ZoneColor(0.50))

        assertEquals(3, TelemetryZoneMath.determineHrvDfaA1Zone(0.499))
        assertEquals(TTColor.Zone4, TelemetryZoneMath.resolveHrvDfaA1ZoneColor(0.499))

        // Custom thresholds T1 = 0.90, T2 = 0.65
        assertEquals(1, TelemetryZoneMath.determineHrvDfaA1Zone(0.95, t1 = 0.90f, t2 = 0.65f))
        assertEquals(2, TelemetryZoneMath.determineHrvDfaA1Zone(0.80, t1 = 0.90f, t2 = 0.65f))
        assertEquals(3, TelemetryZoneMath.determineHrvDfaA1Zone(0.60, t1 = 0.90f, t2 = 0.65f))
    }

    @Test
    fun testCalculateHrvDfaA1ZoneBands_defaultAndOutOfRangeClamping() {
        val defaultBands = TelemetryZoneMath.calculateHrvDfaA1ZoneBands(
            t1 = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T1,
            t2 = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2,
            dataMin = 0.2,
            dataMax = 1.4
        )
        assertEquals(3, defaultBands.size)
        // Anaerob (Z3): 0.2..0.50 -> Zone4
        assertEquals(3, defaultBands[0].zoneIndex)
        assertEquals(0.2, defaultBands[0].minVal, 1e-4)
        assertEquals(0.50, defaultBands[0].maxVal, 1e-4)
        assertEquals(TTColor.Zone4, defaultBands[0].color)
        assertEquals("Z3", defaultBands[0].label)

        // Schwelle (Z2): 0.50..0.75 -> Zone3
        assertEquals(2, defaultBands[1].zoneIndex)
        assertEquals(0.50, defaultBands[1].minVal, 1e-4)
        assertEquals(0.75, defaultBands[1].maxVal, 1e-4)
        assertEquals(TTColor.Zone3, defaultBands[1].color)
        assertEquals("Z2", defaultBands[1].label)

        // Aerob (Z1): 0.75..1.4 -> Zone2
        assertEquals(1, defaultBands[2].zoneIndex)
        assertEquals(0.75, defaultBands[2].minVal, 1e-4)
        assertEquals(1.4, defaultBands[2].maxVal, 1e-4)
        assertEquals(TTColor.Zone2, defaultBands[2].color)
        assertEquals("Z1", defaultBands[2].label)

        // Out-of-range T1 = MAX_DFA_A1_THRESHOLD (1.50f > 1.4) and T2 = 0.15f (< 0.2)
        val clampedBands = TelemetryZoneMath.calculateHrvDfaA1ZoneBands(
            t1 = TuningPreferencesDefaults.MAX_DFA_A1_THRESHOLD,
            t2 = 0.15f,
            dataMin = 0.2,
            dataMax = 1.4
        )
        assertEquals(1, clampedBands.size)
        assertEquals(2, clampedBands[0].zoneIndex)
        assertEquals(0.2, clampedBands[0].minVal, 1e-4)
        assertEquals(1.4, clampedBands[0].maxVal, 1e-4)
        assertEquals(TTColor.Zone3, clampedBands[0].color)

        assertTrue(TelemetryZoneMath.calculateHrvDfaA1ZoneBands(0.75f, 0.50f, 1.4, 1.4).isEmpty())
    }

    @Test
    fun testCalculateHrvDfaA1ThresholdDashes_filtersOutOfRangeThresholds() {
        val defaultDashes = TelemetryZoneMath.calculateHrvDfaA1ThresholdDashes(0.75f, 0.50f, 0.2, 1.4)
        assertEquals(listOf(0.50, 0.75), defaultDashes)

        val maxT1Filtered = TelemetryZoneMath.calculateHrvDfaA1ThresholdDashes(
            t1 = TuningPreferencesDefaults.MAX_DFA_A1_THRESHOLD,
            t2 = 0.50f,
            dataMin = 0.2,
            dataMax = 1.4
        )
        assertEquals(listOf(0.50), maxT1Filtered)

        val allExcluded = TelemetryZoneMath.calculateHrvDfaA1ThresholdDashes(1.50f, 0.20f, 0.2, 1.4)
        assertTrue(allExcluded.isEmpty())

        val exactBoundsExcluded = TelemetryZoneMath.calculateHrvDfaA1ThresholdDashes(1.40f, 0.20f, 0.2, 1.4)
        assertTrue(exactBoundsExcluded.isEmpty())
    }

    @Test
    fun testBuildHrvDfaA1VerticalGradientStops_monotonicAndBoundarySafe() {
        val stops = TelemetryZoneMath.buildHrvDfaA1VerticalGradientStops(
            t1 = 0.75f,
            t2 = 0.50f,
            dataMin = 0.2,
            dataMax = 1.4
        )
        assertTrue(stops.size >= 4)
        assertEquals(0f, stops.first().first, 1e-5f)
        assertEquals(TTColor.Zone2, stops.first().second)
        assertEquals(1f, stops.last().first, 1e-5f)
        assertEquals(TTColor.Zone4, stops.last().second)
        for (i in 1 until stops.size) {
            assertTrue("Stops must be monotonically non-decreasing", stops[i].first >= stops[i - 1].first)
            assertTrue(stops[i].first in 0f..1f)
        }

        // Degenerate equal thresholds (t1 == t2) and out-of-range t1 = 1.50f
        val equalStops = TelemetryZoneMath.buildHrvDfaA1VerticalGradientStops(0.70f, 0.70f, 0.2, 1.4)
        assertEquals(TTColor.Zone2, equalStops.first().second)
        assertEquals(TTColor.Zone4, equalStops.last().second)
        for (i in 1 until equalStops.size) {
            assertTrue(equalStops[i].first >= equalStops[i - 1].first)
        }

        val outOfRangeStops = TelemetryZoneMath.buildHrvDfaA1VerticalGradientStops(1.50f, 0.50f, 0.2, 1.4)
        assertEquals(TTColor.Zone3, outOfRangeStops.first().second)
        assertEquals(TTColor.Zone4, outOfRangeStops.last().second)
    }
}
