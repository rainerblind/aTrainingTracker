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
 * Unit tests for [ZoneGaugeMath] verifying proportional segment weights, 10% boundary clipping,
 * strictly linear needle velocity, cumulative cutoff alignment, and null/stale handling across
 * 5-zone HR and 7-zone Power models (REQ-UI-376, REQ-UI-390, TST-UI-339, TST-UI-353).
 */
class ZoneGaugeMathTest {

    // 5-zone HR cutoffs: Z2=[120,140] (20), Z3=[140,160] (20), Z4=[160,180] (20) -> S_inner = 60 bpm
    private val hrCutoffs = listOf(120, 140, 160, 180)

    // Standard Coggan 7-zone Power cutoffs for FTP = 200 W:
    // [55%, 75%, 90%, 105%, 120%, 150%] = [110, 150, 180, 210, 240, 300] W
    // Spans: Z2=40 (20%), Z3=30 (15%), Z4=30 (15%), Z5=30 (15%), Z6=60 (30%) -> S_inner = 190 W (95% FTP)
    // w_cap = (10 / 95) * 190 = 20 W (10% FTP) -> X_min = 90 W (45% FTP), X_max = 320 W (160% FTP), S_total = 230 W (115% FTP)
    private val cogganPowerCutoffs = listOf(110, 150, 180, 210, 240, 300)

    @Test
    fun testCogganPower7Zones_proportionalSegmentWeights_match10_20_15_15_15_30_10() {
        val weights = ZoneGaugeMath.calculateSegmentWeights(cogganPowerCutoffs, isPower = true)
        assertEquals(7, weights.size)
        assertEquals(1.0f, weights.sum(), 1e-4f)

        val expectedSpans = listOf(10f, 20f, 15f, 15f, 15f, 30f, 10f)
        val totalSpan = 115f
        for (i in expectedSpans.indices) {
            assertEquals(
                "Segment Z${i + 1} weight must equal ${expectedSpans[i]}/115",
                expectedSpans[i] / totalSpan,
                weights[i],
                1e-4f
            )
        }
    }

    @Test
    fun testPower7Zones_strictlyLinearNeedleAndExactCutoffAlignment() {
        val weights = ZoneGaugeMath.calculateSegmentWeights(cogganPowerCutoffs, isPower = true)
        var cumulativeWeight = 0f

        for (i in cogganPowerCutoffs.indices) {
            cumulativeWeight += weights[i]
            val cutoffValue = cogganPowerCutoffs[i].toDouble()
            val needleAtCutoff = ZoneGaugeMath.calculateNeedleFraction(cutoffValue, cogganPowerCutoffs, isPower = true)
            assertNotNull(needleAtCutoff)
            assertEquals(
                "Needle at cutoff C_${i + 1} ($cutoffValue W) must match exact cumulative segment weight",
                cumulativeWeight,
                needleAtCutoff!!,
                1e-4f
            )
        }

        // Verify constant linear needle velocity (dx / dWatt) across Z2, Z4, and Z6:
        // +15 W in Z2 (120 -> 135 W), +15 W in Z4 (190 -> 205 W), and +15 W in Z6 (260 -> 275 W)
        val deltaZ2 = ZoneGaugeMath.calculateNeedleFraction(135.0, cogganPowerCutoffs, isPower = true)!! -
            ZoneGaugeMath.calculateNeedleFraction(120.0, cogganPowerCutoffs, isPower = true)!!
        val deltaZ4 = ZoneGaugeMath.calculateNeedleFraction(205.0, cogganPowerCutoffs, isPower = true)!! -
            ZoneGaugeMath.calculateNeedleFraction(190.0, cogganPowerCutoffs, isPower = true)!!
        val deltaZ6 = ZoneGaugeMath.calculateNeedleFraction(275.0, cogganPowerCutoffs, isPower = true)!! -
            ZoneGaugeMath.calculateNeedleFraction(260.0, cogganPowerCutoffs, isPower = true)!!

        assertEquals("Needle velocity must be identical in Z2 and Z4", deltaZ2, deltaZ4, 1e-5f)
        assertEquals("Needle velocity must be identical in Z4 and Z6", deltaZ4, deltaZ6, 1e-5f)
        assertEquals("15 W over 230 W total span must equal 15/230", 15f / 230f, deltaZ4, 1e-4f)
    }

    @Test
    fun testPower7Zones_10PercentBoundaryClippingAtXMinAndXMax() {
        // For cogganPowerCutoffs (FTP=200W): X_min = 110 - 20 = 90 W (45% FTP), X_max = 300 + 20 = 320 W (160% FTP)
        val atXMin = ZoneGaugeMath.calculateNeedleFraction(90.0, cogganPowerCutoffs, isPower = true)
        val belowXMin = ZoneGaugeMath.calculateNeedleFraction(40.0, cogganPowerCutoffs, isPower = true)
        val atZero = ZoneGaugeMath.calculateNeedleFraction(0.0, cogganPowerCutoffs, isPower = true)
        assertEquals(0.0f, atXMin!!, 1e-4f)
        assertEquals(0.0f, belowXMin!!, 1e-4f)
        assertEquals(0.0f, atZero!!, 1e-4f)

        // Midpoint of clipped Z1 (100 W -> 10 W above X_min=90 W): 10 / 230
        val midZ1 = ZoneGaugeMath.calculateNeedleFraction(100.0, cogganPowerCutoffs, isPower = true)
        assertEquals(10f / 230f, midZ1!!, 1e-4f)

        // Midpoint of clipped Z7 (310 W -> 220 W above X_min=90 W): 220 / 230
        val midZ7 = ZoneGaugeMath.calculateNeedleFraction(310.0, cogganPowerCutoffs, isPower = true)
        assertEquals(220f / 230f, midZ7!!, 1e-4f)

        // At and above X_max = 320 W
        val atXMax = ZoneGaugeMath.calculateNeedleFraction(320.0, cogganPowerCutoffs, isPower = true)
        val aboveXMax = ZoneGaugeMath.calculateNeedleFraction(800.0, cogganPowerCutoffs, isPower = true)
        assertEquals(1.0f, atXMax!!, 1e-4f)
        assertEquals(1.0f, aboveXMax!!, 1e-4f)
    }

    @Test
    fun testHeartRate5Zones_strictlyLinearNeedleAndExactCutoffAlignment() {
        val weights = ZoneGaugeMath.calculateSegmentWeights(hrCutoffs, isPower = false)
        assertEquals(5, weights.size)
        assertEquals(1.0f, weights.sum(), 1e-4f)

        var cumulativeWeight = 0f
        for (i in hrCutoffs.indices) {
            cumulativeWeight += weights[i]
            val cutoffBpm = hrCutoffs[i].toDouble()
            val needleAtCutoff = ZoneGaugeMath.calculateNeedleFraction(cutoffBpm, hrCutoffs, isPower = false)
            assertNotNull(needleAtCutoff)
            assertEquals(
                "HR needle at cutoff C_${i + 1} ($cutoffBpm bpm) must match cumulative segment weight",
                cumulativeWeight,
                needleAtCutoff!!,
                1e-4f
            )
        }

        // Midpoint of HR interior range (150 bpm is exact midpoint of [120, 180]) -> must be exactly 0.50f
        val midHr = ZoneGaugeMath.calculateNeedleFraction(150.0, hrCutoffs, isPower = false)
        assertEquals(0.50f, midHr!!, 1e-4f)

        // Clamping below X_min and above X_max
        assertEquals(0.0f, ZoneGaugeMath.calculateNeedleFraction(30.0, hrCutoffs, isPower = false)!!, 1e-4f)
        assertEquals(1.0f, ZoneGaugeMath.calculateNeedleFraction(220.0, hrCutoffs, isPower = false)!!, 1e-4f)
    }

    @Test
    fun testNullAndNonFiniteTelemetry_returnsNullNeedlePosition() {
        assertNull(ZoneGaugeMath.calculateNeedleFraction(null, hrCutoffs, isPower = false))
        assertNull(ZoneGaugeMath.calculateNeedleFraction(Double.NaN, hrCutoffs, isPower = false))
        assertNull(ZoneGaugeMath.calculateNeedleFraction(Double.POSITIVE_INFINITY, hrCutoffs, isPower = false))
        assertNull(ZoneGaugeMath.calculateNeedleFraction(Double.NEGATIVE_INFINITY, hrCutoffs, isPower = false))

        assertNull(ZoneGaugeMath.calculateNeedleFraction(null, cogganPowerCutoffs, isPower = true))
        assertNull(ZoneGaugeMath.calculateNeedleFraction(Double.NaN, cogganPowerCutoffs, isPower = true))
    }

    @Test
    fun testActiveZoneIndexCalculation() {
        assertEquals(1, ZoneGaugeMath.calculateActiveZoneIndex(80.0, hrCutoffs, isPower = false))
        assertEquals(2, ZoneGaugeMath.calculateActiveZoneIndex(130.0, hrCutoffs, isPower = false))
        assertEquals(3, ZoneGaugeMath.calculateActiveZoneIndex(150.0, hrCutoffs, isPower = false))
        assertEquals(4, ZoneGaugeMath.calculateActiveZoneIndex(175.0, hrCutoffs, isPower = false))
        assertEquals(5, ZoneGaugeMath.calculateActiveZoneIndex(195.0, hrCutoffs, isPower = false))

        assertEquals(1, ZoneGaugeMath.calculateActiveZoneIndex(100.0, cogganPowerCutoffs, isPower = true))
        assertEquals(2, ZoneGaugeMath.calculateActiveZoneIndex(130.0, cogganPowerCutoffs, isPower = true))
        assertEquals(3, ZoneGaugeMath.calculateActiveZoneIndex(165.0, cogganPowerCutoffs, isPower = true))
        assertEquals(4, ZoneGaugeMath.calculateActiveZoneIndex(195.0, cogganPowerCutoffs, isPower = true))
        assertEquals(5, ZoneGaugeMath.calculateActiveZoneIndex(225.0, cogganPowerCutoffs, isPower = true))
        assertEquals(6, ZoneGaugeMath.calculateActiveZoneIndex(270.0, cogganPowerCutoffs, isPower = true))
        assertEquals(7, ZoneGaugeMath.calculateActiveZoneIndex(350.0, cogganPowerCutoffs, isPower = true))

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
        assertEquals(5, hrState.segmentWeights.size)
        assertEquals(1.0f, hrState.segmentWeights.sum(), 1e-4f)
        assertEquals(3, hrState.activeZoneIndex)
        assertEquals(0.50f, hrState.needleFraction!!, 1e-4f)

        val powerState = ZoneGaugeMath.calculateZoneGaugeState(
            value = 225.0,
            zoneType = SettingsDataStore.ZoneType.PWR_BIKE,
            cutoffs = cogganPowerCutoffs
        )
        assertEquals(7, powerState.segmentColors.size)
        assertEquals(7, powerState.segmentWeights.size)
        assertEquals(1.0f, powerState.segmentWeights.sum(), 1e-4f)
        assertEquals(5, powerState.activeZoneIndex)
        assertNotNull(powerState.needleFraction)

        val staleState = ZoneGaugeMath.calculateZoneGaugeState(
            value = null,
            zoneType = SettingsDataStore.ZoneType.PWR_BIKE,
            cutoffs = cogganPowerCutoffs
        )
        assertEquals(7, staleState.segmentColors.size)
        assertEquals(7, staleState.segmentWeights.size)
        assertNull(staleState.activeZoneIndex)
        assertNull(staleState.needleFraction)
    }
}
