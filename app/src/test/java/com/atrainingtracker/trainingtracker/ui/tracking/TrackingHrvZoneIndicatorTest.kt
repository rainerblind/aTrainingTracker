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
}
