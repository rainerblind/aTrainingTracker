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

import androidx.compose.ui.graphics.Color
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for DFA a1 aerobic threshold zone indicator colors conforming to REQ-TRK-061.3 and TST-TRK-061.3.
 */
class TrackingHrvZoneIndicatorTest {

    @Test
    fun testCalculateZoneColor_aerobicBase_returnsZone2Green() {
        // Default T1 = 0.75, T2 = 0.50
        val color = HrvZoneIndicator.calculateZoneColor(0.85)
        assertEquals(TTColor.Zone2, color)

        // Exactly above T1
        val colorJustAbove = HrvZoneIndicator.calculateZoneColor(0.751)
        assertEquals(TTColor.Zone2, colorJustAbove)
    }

    @Test
    fun testCalculateZoneColor_thresholdTransition_returnsZone3Orange() {
        // Between 0.50 and 0.75 inclusive
        val colorExactT1 = HrvZoneIndicator.calculateZoneColor(0.75)
        assertEquals(TTColor.Zone3, colorExactT1)

        val colorMid = HrvZoneIndicator.calculateZoneColor(0.65)
        assertEquals(TTColor.Zone3, colorMid)

        val colorExactT2 = HrvZoneIndicator.calculateZoneColor(0.50)
        assertEquals(TTColor.Zone3, colorExactT2)
    }

    @Test
    fun testCalculateZoneColor_highIntensity_returnsZone4Red() {
        // Below T2 = 0.50
        val colorBelow = HrvZoneIndicator.calculateZoneColor(0.49)
        assertEquals(TTColor.Zone4, colorBelow)

        val colorLow = HrvZoneIndicator.calculateZoneColor(0.25)
        assertEquals(TTColor.Zone4, colorLow)
    }

    @Test
    fun testCalculateZoneColor_customThresholds_respectsOverriddenBoundaries() {
        // Custom T1 = 0.80, T2 = 0.55
        val colorAboveCustomT1 = HrvZoneIndicator.calculateZoneColor(0.82, t1 = 0.80f, t2 = 0.55f)
        assertEquals(TTColor.Zone2, colorAboveCustomT1)

        val colorBetweenCustom = HrvZoneIndicator.calculateZoneColor(0.70, t1 = 0.80f, t2 = 0.55f)
        assertEquals(TTColor.Zone3, colorBetweenCustom)

        val colorBelowCustomT2 = HrvZoneIndicator.calculateZoneColor(0.52, t1 = 0.80f, t2 = 0.55f)
        assertEquals(TTColor.Zone4, colorBelowCustomT2)
    }

    @Test
    fun testCalculateZoneColor_nullNanNegativeGated_returnsDefaultTransparent() {
        val defaultColor = Color.Transparent
        assertEquals(defaultColor, HrvZoneIndicator.calculateZoneColor(null))
        assertEquals(defaultColor, HrvZoneIndicator.calculateZoneColor(Double.NaN))
        assertEquals(defaultColor, HrvZoneIndicator.calculateZoneColor(Double.POSITIVE_INFINITY))
        assertEquals(defaultColor, HrvZoneIndicator.calculateZoneColor(-0.5))
    }

    @Test
    fun testCalculateZoneColor_smoothedStream_suppressesZone2Color() {
        val defaultColor = Color.Transparent
        // High alpha1 would normally be Zone2 green, but isSmoothedStream suppresses it to defaultColor
        val colorSmoothed = HrvZoneIndicator.calculateZoneColor(0.85, isSmoothedStream = true)
        assertEquals(defaultColor, colorSmoothed)

        val colorTransitionSmoothed = HrvZoneIndicator.calculateZoneColor(0.65, isSmoothedStream = true)
        assertEquals(defaultColor, colorTransitionSmoothed)

        val colorAnaerobicSmoothed = HrvZoneIndicator.calculateZoneColor(0.40, isSmoothedStream = true)
        assertEquals(defaultColor, colorAnaerobicSmoothed)
    }

    @Test
    fun testCalculateZoneGaugeState_aerobicBase_mapsToZone2() {
        val state = HrvZoneIndicator.calculateZoneGaugeState(0.85)
        org.junit.Assert.assertNotNull(state)
        assertEquals(1, state?.activeZoneIndex)
        assertEquals(HrvZoneIndicator.HRV_SEGMENT_COLORS, state?.segmentColors)
        assertEquals(listOf("Z2", "Z3", "Z4"), state?.zoneLabels)
        assertEquals(3, state?.segmentColors?.size)
        org.junit.Assert.assertTrue((state?.needleFraction ?: 0f) in 0.05f..0.45f)
    }

    @Test
    fun testCalculateZoneGaugeState_thresholdTransition_mapsToZone3() {
        val state = HrvZoneIndicator.calculateZoneGaugeState(0.65)
        org.junit.Assert.assertNotNull(state)
        assertEquals(2, state?.activeZoneIndex)
        org.junit.Assert.assertTrue((state?.needleFraction ?: 0f) in 0.45f..0.70f)
    }

    @Test
    fun testCalculateZoneGaugeState_highIntensity_mapsToZone4() {
        val state = HrvZoneIndicator.calculateZoneGaugeState(0.40)
        org.junit.Assert.assertNotNull(state)
        assertEquals(3, state?.activeZoneIndex)
        org.junit.Assert.assertTrue((state?.needleFraction ?: 0f) in 0.70f..0.95f)
    }

    @Test
    fun testCalculateZoneGaugeState_proportionalSegmentWeights() {
        // Default T1 = 0.75, T2 = 0.50 -> spans: 0.45, 0.25, 0.30 (total 1.0)
        val defaultState = HrvZoneIndicator.calculateZoneGaugeState(0.70)
        org.junit.Assert.assertNotNull(defaultState)
        val weights = defaultState!!.segmentWeights
        assertEquals(3, weights.size)
        assertEquals(0.45f, weights[0], 0.001f)
        assertEquals(0.25f, weights[1], 0.001f)
        assertEquals(0.30f, weights[2], 0.001f)
        assertEquals(1.0f, weights.sum(), 0.001f)

        // Custom T1 = 0.80, T2 = 0.60 -> spans: 0.40, 0.20, 0.40 (total 1.0)
        val customState = HrvZoneIndicator.calculateZoneGaugeState(0.70, t1 = 0.80f, t2 = 0.60f)
        org.junit.Assert.assertNotNull(customState)
        val customWeights = customState!!.segmentWeights
        assertEquals(0.40f, customWeights[0], 0.001f)
        assertEquals(0.20f, customWeights[1], 0.001f)
        assertEquals(0.40f, customWeights[2], 0.001f)
        assertEquals(1.0f, customWeights.sum(), 0.001f)
    }

    @Test
    fun testCalculateZoneGaugeState_strictlyMonotonicNeedle() {
        val alphaValues = listOf(1.15, 1.0, 0.85, 0.75, 0.65, 0.50, 0.40, 0.25)
        val fractions = alphaValues.map {
            HrvZoneIndicator.calculateZoneGaugeState(it)!!.needleFraction!!
        }
        for (i in 0 until fractions.size - 1) {
            org.junit.Assert.assertTrue(
                "Needle fraction must strictly increase as alpha1 decreases: ${fractions[i]} < ${fractions[i + 1]}",
                fractions[i] < fractions[i + 1]
            )
        }
    }

    @Test
    fun testCalculateZoneGaugeState_nullOrInvalid_returnsNull() {
        org.junit.Assert.assertNull(HrvZoneIndicator.calculateZoneGaugeState(null))
        org.junit.Assert.assertNull(HrvZoneIndicator.calculateZoneGaugeState(Double.NaN))
        org.junit.Assert.assertNull(HrvZoneIndicator.calculateZoneGaugeState(-0.1))
    }
}
