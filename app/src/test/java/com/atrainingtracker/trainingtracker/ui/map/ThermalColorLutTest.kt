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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ThermalColorLut] sequential palettes and contrast verification.
 *
 * Requirements: REQ-MAP-056.2, REQ-MAP-057.2, TST-MAP-059.2
 */
class ThermalColorLutTest {

    @Test
    fun testDefaultLutIsBlue() {
        val defaultLut = ThermalColorLut.DEFAULT
        val blueLut = ThermalColorLut.BLUE
        assertEquals("DEFAULT and BLUE must have identical tables", defaultLut.table.toList(), blueLut.table.toList())

        // Index 0 is transparent
        assertEquals(0, defaultLut.getColor(0))

        // Single pass low intensity should be cyan (dominant green and blue, zero red)
        val lowColor = defaultLut.getColor(1)
        val r = (lowColor shr 16) and 0xFF
        val g = (lowColor shr 8) and 0xFF
        val b = lowColor and 0xFF
        assertEquals(0, r)
        assertTrue("Green should be prominent in low-intensity cyan", g > 200)
        assertTrue("Blue should be prominent in low-intensity cyan", b > 200)
    }

    @Test
    fun testHotPaletteColorProgression_TST_MAP_059_2() {
        val hotLut = ThermalColorLut.HOT
        assertNotNull(hotLut)

        // Index 0 must be fully transparent
        assertEquals(0, hotLut.getColor(0))

        // Index 1 (low density / yellow): red and green high, blue low
        val yellowColor = hotLut.getColor(1)
        val yr = (yellowColor shr 16) and 0xFF
        val yg = (yellowColor shr 8) and 0xFF
        val yb = yellowColor and 0xFF
        val ya = (yellowColor shr 24) and 0xFF
        assertTrue("Yellow must have high red", yr >= 240)
        assertTrue("Yellow must have high green", yg >= 220)
        assertTrue("Yellow must have low blue", yb <= 130)
        assertTrue("Alpha should be non-zero", ya > 100)

        // Index 90 (~35% density / orange): red high, green medium, blue very low
        val orangeColor = hotLut.getColor(90)
        val or = (orangeColor shr 16) and 0xFF
        val og = (orangeColor shr 8) and 0xFF
        val ob = orangeColor and 0xFF
        assertTrue("Orange must have high red", or >= 240)
        assertTrue("Orange must have medium green", og in 130..180)
        assertTrue("Orange must have very low blue", ob <= 30)

        // Index 180 (~70% density / warm red): red high, green low, blue low
        val redColor = hotLut.getColor(180)
        val rr = (redColor shr 16) and 0xFF
        val rg = (redColor shr 8) and 0xFF
        val rb = redColor and 0xFF
        assertTrue("Red must have high red", rr >= 220)
        assertTrue("Red must have low green", rg <= 70)
        assertTrue("Red must have low blue", rb <= 70)

        // Index 255 (maximum density / dark red #880E4F): R=136, G=14, B=79, A=255
        val maxColor = hotLut.getColor(255)
        val mr = (maxColor shr 16) and 0xFF
        val mg = (maxColor shr 8) and 0xFF
        val mb = maxColor and 0xFF
        val ma = (maxColor shr 24) and 0xFF
        assertEquals("Dark Red red channel", 136, mr)
        assertEquals("Dark Red green channel", 14, mg)
        assertEquals("Dark Red blue channel", 79, mb)
        assertEquals("Dark Red alpha channel", 255, ma)
    }

    @Test
    fun testHotPaletteContrastsAgainstWaterAndTerrain() {
        val hotLut = ThermalColorLut.HOT
        // Across the hot palette, red component dominates blue component, contrasting against blue water features
        for (i in 1..255) {
            val color = hotLut.getColor(i)
            val r = (color shr 16) and 0xFF
            val b = color and 0xFF
            assertTrue("Hot palette red must strictly exceed blue at index $i to contrast with water", r > b)
        }
    }

    @Test
    fun testClassicBlueLutAlphaStopsAndOpacity_TST_MAP_060_1() {
        val classicLut = ThermalColorLut.CLASSIC_BLUE
        assertEquals("CLASSIC_BLUE default opacity must be 0.8", 0.8, classicLut.opacity, 0.001)

        // Index 0 must be fully transparent
        assertEquals(0, classicLut.getColor(0))

        // Index 1 (low density / cyan): base alpha 51 * 0.8 = 41
        val lowColor = classicLut.getColor(1)
        val lowAlpha = (lowColor shr 24) and 0xFF
        val lowGreen = (lowColor shr 8) and 0xFF
        val lowBlue = lowColor and 0xFF
        assertEquals(41, lowAlpha)
        assertTrue("Low intensity green should be prominent in cyan", lowGreen > 200)
        assertEquals("Low intensity blue should be 255", 255, lowBlue)

        // Index 128 (mid density / pure blue): base alpha 77 * 0.8 = 62
        val midColor = classicLut.getColor(128)
        val midAlpha = (midColor shr 24) and 0xFF
        val midBlue = midColor and 0xFF
        assertEquals(62, midAlpha)
        assertEquals("Mid intensity blue should be 255", 255, midBlue)

        // Index 255 (high density / deep indigo): base alpha 180 * 0.8 = 144
        val highColor = classicLut.getColor(255)
        val highAlpha = (highColor shr 24) and 0xFF
        val highRed = (highColor shr 16) and 0xFF
        val highBlue = highColor and 0xFF
        assertEquals(144, highAlpha)
        assertEquals(49, highRed)
        assertEquals(146, highBlue)

        // Verify that CLASSIC_BLUE has lower alpha than the boosted BLUE_STOPS (55%/80%/100%), proving pre-ATT-4833 look
        val periodBlueLut = ThermalColorLut.BLUE
        val periodHighAlpha = (periodBlueLut.getColor(255) shr 24) and 0xFF
        assertEquals("BLUE has 100% alpha (255) in Periods", 255, periodHighAlpha)
        assertTrue("CLASSIC_BLUE alpha (144) must be significantly lower than Periods BLUE (255)", highAlpha < periodHighAlpha)
    }

    @Test
    fun testCustomStopsConstructor() {
        val customStops = arrayOf(
            Pair(0.0f, intArrayOf(10, 20, 30, 100)),
            Pair(1.0f, intArrayOf(100, 200, 250, 255))
        )
        val customLut = ThermalColorLut(opacity = 0.5, stops = customStops)
        assertEquals(0, customLut.getColor(0))

        val endColor = customLut.getColor(255)
        val r = (endColor shr 16) and 0xFF
        val g = (endColor shr 8) and 0xFF
        val b = endColor and 0xFF
        val a = (endColor shr 24) and 0xFF
        assertEquals(100, r)
        assertEquals(200, g)
        assertEquals(250, b)
        assertEquals(128, a)
    }
}
