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

package com.atrainingtracker.trainingtracker.ui.tracking

import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ZoneGaugeMath] verifying intra-zone needle calculation, dynamic ceilings,
 * boundary clamping, and null/stale handling across 5-zone HR and 7-zone Power models (REQ-UI-376, TST-UI-339.1).
 */
class ZoneGaugeMathTest {

    private val hrCutoffs = listOf(120, 140, 160, 180)
    private val powerCutoffs = listOf(150, 200, 250, 300, 350, 450)

    @Test
    fun testHeartRate5Zones_intraZoneNeedlePositioning() {
        // Z1 baseline is 30.0 bpm, Z1 max is 120 bpm -> width 90 bpm
        // Midpoint of Z1: 30 + 45 = 75 bpm -> fraction 0.5 within Z1 -> 0.5 * 0.2 = 0.10
        val midZ1 = ZoneGaugeMath.calculateNeedleFraction(75.0, hrCutoffs, isPower = false)
        assertNotNull(midZ1)
        assertEquals(0.10f, midZ1!!, 0.001f)

        // Upper boundary of Z1: 120 bpm -> exactly 0.20
        val endZ1 = ZoneGaugeMath.calculateNeedleFraction(120.0, hrCutoffs, isPower = false)
        assertNotNull(endZ1)
        assertEquals(0.20f, endZ1!!, 0.001f)

        // Upper boundary of Z2: 140 bpm -> exactly 0.40
        val endZ2 = ZoneGaugeMath.calculateNeedleFraction(140.0, hrCutoffs, isPower = false)
        assertNotNull(endZ2)
        assertEquals(0.40f, endZ2!!, 0.001f)

        // Upper boundary of Z3: 160 bpm -> exactly 0.60
        val endZ3 = ZoneGaugeMath.calculateNeedleFraction(160.0, hrCutoffs, isPower = false)
        assertNotNull(endZ3)
        assertEquals(0.60f, endZ3!!, 0.001f)

        // Upper boundary of Z4: 180 bpm -> exactly 0.80
        val endZ4 = ZoneGaugeMath.calculateNeedleFraction(180.0, hrCutoffs, isPower = false)
        assertNotNull(endZ4)
        assertEquals(0.80f, endZ4!!, 0.001f)
    }

    @Test
    fun testHeartRate5Zones_openEndedZone5CeilingAndClamping() {
        // HR max = 195 bpm -> topLower = 180, topUpper = max(195, 180+25) = 205 bpm
        val hrMax = 195
        val expectedCeiling = 205.0

        // Entry of Zone 5: 180 bpm -> 0.80
        val entryZ5 = ZoneGaugeMath.calculateNeedleFraction(180.0, hrCutoffs, hrMax = hrMax, isPower = false)
        assertNotNull(entryZ5)
        assertEquals(0.80f, entryZ5!!, 0.001f)

        // At ceiling: 205 bpm -> exactly 1.00
        val atCeiling = ZoneGaugeMath.calculateNeedleFraction(expectedCeiling, hrCutoffs, hrMax = hrMax, isPower = false)
        assertNotNull(atCeiling)
        assertEquals(1.00f, atCeiling!!, 0.001f)

        // Beyond ceiling: 220 bpm -> clamped cleanly to 1.00
        val beyondCeiling = ZoneGaugeMath.calculateNeedleFraction(220.0, hrCutoffs, hrMax = hrMax, isPower = false)
        assertNotNull(beyondCeiling)
        assertEquals(1.00f, beyondCeiling!!, 0.001f)
    }

    @Test
    fun testHeartRate5Zones_belowBaselineClamping() {
        // Below 30 bpm baseline: clamps to 0.00f
        val below30 = ZoneGaugeMath.calculateNeedleFraction(20.0, hrCutoffs, isPower = false)
        assertNotNull(below30)
        assertEquals(0.00f, below30!!, 0.0001f)

        val atZero = ZoneGaugeMath.calculateNeedleFraction(0.0, hrCutoffs, isPower = false)
        assertNotNull(atZero)
        assertEquals(0.00f, atZero!!, 0.0001f)
    }

    @Test
    fun testPower7Zones_intraZoneNeedlePositioning() {
        val segmentWidth = 1.0f / 7.0f

        // Baseline is 0.0 W. Z1 upper boundary: 150 W -> 1/7
        val endZ1 = ZoneGaugeMath.calculateNeedleFraction(150.0, powerCutoffs, isPower = true)
        assertNotNull(endZ1)
        assertEquals(segmentWidth, endZ1!!, 0.001f)

        // Z2 upper boundary: 200 W -> 2/7
        val endZ2 = ZoneGaugeMath.calculateNeedleFraction(200.0, powerCutoffs, isPower = true)
        assertNotNull(endZ2)
        assertEquals(2 * segmentWidth, endZ2!!, 0.001f)

        // Z3 upper boundary: 250 W -> 3/7
        val endZ3 = ZoneGaugeMath.calculateNeedleFraction(250.0, powerCutoffs, isPower = true)
        assertNotNull(endZ3)
        assertEquals(3 * segmentWidth, endZ3!!, 0.001f)

        // Z4 upper boundary: 300 W -> 4/7
        val endZ4 = ZoneGaugeMath.calculateNeedleFraction(300.0, powerCutoffs, isPower = true)
        assertNotNull(endZ4)
        assertEquals(4 * segmentWidth, endZ4!!, 0.001f)

        // Z5 upper boundary: 350 W -> 5/7
        val endZ5 = ZoneGaugeMath.calculateNeedleFraction(350.0, powerCutoffs, isPower = true)
        assertNotNull(endZ5)
        assertEquals(5 * segmentWidth, endZ5!!, 0.001f)

        // Z6 upper boundary: 450 W -> 6/7
        val endZ6 = ZoneGaugeMath.calculateNeedleFraction(450.0, powerCutoffs, isPower = true)
        assertNotNull(endZ6)
        assertEquals(6 * segmentWidth, endZ6!!, 0.001f)
    }

    @Test
    fun testPower7Zones_openEndedZone7DynamicCeilingAndClamping() {
        // FTP = 300 W -> dynamic ceiling = min(2500, max(450 + 300, 300 * 2.0)) = max(750, 600) = 750 W
        val ftp = 300
        val expectedCeiling = 750.0

        // Z7 entry: 450 W -> 6/7
        val entryZ7 = ZoneGaugeMath.calculateNeedleFraction(450.0, powerCutoffs, ftp = ftp, isPower = true)
        assertNotNull(entryZ7)
        assertEquals(6f / 7f, entryZ7!!, 0.001f)

        // At dynamic ceiling: 750 W -> 1.00
        val atCeiling = ZoneGaugeMath.calculateNeedleFraction(expectedCeiling, powerCutoffs, ftp = ftp, isPower = true)
        assertNotNull(atCeiling)
        assertEquals(1.00f, atCeiling!!, 0.001f)

        // Sprint beyond ceiling: 1500 W -> clamped cleanly to 1.00
        val sprint = ZoneGaugeMath.calculateNeedleFraction(1500.0, powerCutoffs, ftp = ftp, isPower = true)
        assertNotNull(sprint)
        assertEquals(1.00f, sprint!!, 0.001f)

        // Physical cap enforcement: with FTP=1500 W, dynamic ceiling would be 3000 W, but capped at 2500 W
        val highFtp = 1500
        val extremeSprint = ZoneGaugeMath.calculateNeedleFraction(2600.0, powerCutoffs, ftp = highFtp, isPower = true)
        assertNotNull(extremeSprint)
        assertEquals(1.00f, extremeSprint!!, 0.001f)
    }

    @Test
    fun testNullAndNonFiniteTelemetry_returnsNullNeedlePosition() {
        assertNull(ZoneGaugeMath.calculateNeedleFraction(null, hrCutoffs, isPower = false))
        assertNull(ZoneGaugeMath.calculateNeedleFraction(Double.NaN, hrCutoffs, isPower = false))
        assertNull(ZoneGaugeMath.calculateNeedleFraction(Double.POSITIVE_INFINITY, hrCutoffs, isPower = false))
        assertNull(ZoneGaugeMath.calculateNeedleFraction(Double.NEGATIVE_INFINITY, hrCutoffs, isPower = false))

        assertNull(ZoneGaugeMath.calculateNeedleFraction(null, powerCutoffs, isPower = true))
        assertNull(ZoneGaugeMath.calculateNeedleFraction(Double.NaN, powerCutoffs, isPower = true))
    }

    @Test
    fun testActiveZoneIndexCalculation() {
        assertEquals(1, ZoneGaugeMath.calculateActiveZoneIndex(80.0, hrCutoffs, isPower = false))
        assertEquals(2, ZoneGaugeMath.calculateActiveZoneIndex(130.0, hrCutoffs, isPower = false))
        assertEquals(3, ZoneGaugeMath.calculateActiveZoneIndex(150.0, hrCutoffs, isPower = false))
        assertEquals(4, ZoneGaugeMath.calculateActiveZoneIndex(175.0, hrCutoffs, isPower = false))
        assertEquals(5, ZoneGaugeMath.calculateActiveZoneIndex(195.0, hrCutoffs, isPower = false))

        assertEquals(1, ZoneGaugeMath.calculateActiveZoneIndex(100.0, powerCutoffs, isPower = true))
        assertEquals(2, ZoneGaugeMath.calculateActiveZoneIndex(180.0, powerCutoffs, isPower = true))
        assertEquals(3, ZoneGaugeMath.calculateActiveZoneIndex(220.0, powerCutoffs, isPower = true))
        assertEquals(4, ZoneGaugeMath.calculateActiveZoneIndex(280.0, powerCutoffs, isPower = true))
        assertEquals(5, ZoneGaugeMath.calculateActiveZoneIndex(320.0, powerCutoffs, isPower = true))
        assertEquals(6, ZoneGaugeMath.calculateActiveZoneIndex(400.0, powerCutoffs, isPower = true))
        assertEquals(7, ZoneGaugeMath.calculateActiveZoneIndex(550.0, powerCutoffs, isPower = true))

        assertNull(ZoneGaugeMath.calculateActiveZoneIndex(null, hrCutoffs, isPower = false))
    }

    @Test
    fun testCalculateZoneGaugeState() {
        val hrState = ZoneGaugeMath.calculateZoneGaugeState(
            value = 150.0,
            zoneType = SettingsDataStore.ZoneType.HR_RUN,
            cutoffs = hrCutoffs
        )
        assertEquals(5, hrState.segmentColors.size)
        assertEquals(3, hrState.activeZoneIndex)
        assertNotNull(hrState.needleFraction)

        val powerState = ZoneGaugeMath.calculateZoneGaugeState(
            value = 320.0,
            zoneType = SettingsDataStore.ZoneType.PWR_BIKE,
            cutoffs = powerCutoffs
        )
        assertEquals(7, powerState.segmentColors.size)
        assertEquals(5, powerState.activeZoneIndex)
        assertNotNull(powerState.needleFraction)

        val staleState = ZoneGaugeMath.calculateZoneGaugeState(
            value = null,
            zoneType = SettingsDataStore.ZoneType.PWR_BIKE,
            cutoffs = powerCutoffs
        )
        assertEquals(7, staleState.segmentColors.size)
        assertNull(staleState.activeZoneIndex)
        assertNull(staleState.needleFraction)
    }
}
