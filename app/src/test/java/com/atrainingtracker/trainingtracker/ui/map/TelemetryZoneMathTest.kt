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
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds
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
        z4Max = 300
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
    fun testCalculatePowerZoneBands_fullSpanAcrossAll5Zones() {
        val bands = TelemetryZoneMath.calculatePowerZoneBands(powerThresholds, 0.0, 350.0)

        assertEquals(5, bands.size)
        assertEquals(150.0, bands[0].maxVal, 1e-4)
        assertEquals(200.0, bands[1].maxVal, 1e-4)
        assertEquals(250.0, bands[2].maxVal, 1e-4)
        assertEquals(300.0, bands[3].maxVal, 1e-4)
        assertEquals(350.0, bands[4].maxVal, 1e-4)
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
    fun testDeterminePowerZone_mapsCorrectly() {
        assertEquals(1, TelemetryZoneMath.determinePowerZone(100.0, powerThresholds))
        assertEquals(2, TelemetryZoneMath.determinePowerZone(175.0, powerThresholds))
        assertEquals(3, TelemetryZoneMath.determinePowerZone(225.0, powerThresholds))
        assertEquals(4, TelemetryZoneMath.determinePowerZone(280.0, powerThresholds))
        assertEquals(5, TelemetryZoneMath.determinePowerZone(350.0, powerThresholds))
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

        val result = TelemetryZoneMath.loadPowerThresholds(context)
        assertEquals(PowerZoneThresholds(150, 200, 250, 300), result)
    }

    @Test
    fun testLoadPowerThresholds_invalidValues_returnsNull() {
        val context = mockk<Context>()
        mockkStatic(SettingsDataStoreJavaHelper::class)
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.PWR_BIKE, 1) } returns 150
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.PWR_BIKE, 2) } returns 0
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.PWR_BIKE, 3) } returns 250
        every { SettingsDataStoreJavaHelper.getZoneMax(context, SettingsDataStore.ZoneType.PWR_BIKE, 4) } returns 300

        assertNull(TelemetryZoneMath.loadPowerThresholds(context))
    }
}
