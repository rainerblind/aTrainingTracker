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
import com.atrainingtracker.trainingtracker.settings.ZoneDisplayOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test for [SensorFieldState] zone display configuration integration (TST-UI-124).
 */
class SensorFieldZoneDisplayTest {

    @Test
    fun sensorFieldState_defaultsToStandardZoneDisplayOptions() {
        val state = SensorFieldState(
            configHash = 1,
            sensorFieldId = 10,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "Heart Rate",
            filterDescription = "",
            value = "145",
            units = "bpm",
            zoneColor = Color(0xFF008000)
        )

        assertTrue(state.zoneDisplayOptions.showBackground)
        assertTrue(state.zoneDisplayOptions.showLeftBar)
        assertFalse(state.zoneDisplayOptions.showRightBar)
        assertFalse(state.zoneDisplayOptions.showTextColor)
    }

    @Test
    fun sensorFieldState_customZoneDisplayOptions_arePreserved() {
        val customOptions = ZoneDisplayOptions(
            showBackground = false,
            showLeftBar = false,
            showRightBar = true,
            showTextColor = true
        )

        val state = SensorFieldState(
            configHash = 1,
            sensorFieldId = 10,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "Power",
            filterDescription = "",
            value = "280",
            units = "W",
            zoneColor = Color(0xFFFFA500),
            zoneDisplayOptions = customOptions
        )

        assertFalse(state.zoneDisplayOptions.showBackground)
        assertFalse(state.zoneDisplayOptions.showLeftBar)
        assertTrue(state.zoneDisplayOptions.showRightBar)
        assertTrue(state.zoneDisplayOptions.showTextColor)
    }

    @Test
    fun sensorFieldState_noZoneColor_hasTransparentZoneColor() {
        val state = SensorFieldState(
            configHash = 1,
            sensorFieldId = 10,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "Speed",
            filterDescription = "",
            value = "25.4",
            units = "km/h",
            zoneColor = Color.Transparent
        )

        assertEquals(Color.Transparent, state.zoneColor)
    }

    @Test
    fun sensorFieldState_zone6AndZone7Colors_arePreserved() {
        val zone6Color = com.atrainingtracker.trainingtracker.ui.theme.TTColor.Zone6
        val zone7Color = com.atrainingtracker.trainingtracker.ui.theme.TTColor.Zone7

        val stateZ6 = SensorFieldState(
            configHash = 1,
            sensorFieldId = 11,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "Power",
            filterDescription = "",
            value = "400",
            units = "W",
            zoneColor = zone6Color
        )

        val stateZ7 = SensorFieldState(
            configHash = 1,
            sensorFieldId = 12,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "Power",
            filterDescription = "",
            value = "600",
            units = "W",
            zoneColor = zone7Color
        )

        assertEquals(Color(0xFF8B5A2B), stateZ6.zoneColor)
        assertEquals(Color(0xFF000000), stateZ7.zoneColor)

        // Verify XML parity with color.xml and WCAG 2.1 AA >= 3.0:1 non-text contrast against Zone 7 (TST-UI-355.1)
        val colorXmlFile = java.io.File("src/main/res/values/color.xml").let {
            if (it.exists()) it else java.io.File("app/src/main/res/values/color.xml")
        }
        assertTrue("color.xml must exist", colorXmlFile.exists())
        val xmlContent = colorXmlFile.readText()
        assertTrue(
            "color.xml must define zone_6 as #8B5A2B",
            xmlContent.contains("<color name=\"zone_6\">#8B5A2B</color>")
        )

        fun srgbChannelToLinear(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        fun relativeLuminance(color: Color): Double =
            0.2126 * srgbChannelToLinear(color.red) +
                0.7152 * srgbChannelToLinear(color.green) +
                0.0722 * srgbChannelToLinear(color.blue)

        val l6 = relativeLuminance(zone6Color)
        val l7 = relativeLuminance(zone7Color)
        val contrastRatio = (Math.max(l6, l7) + 0.05) / (Math.min(l6, l7) + 0.05)
        assertTrue(
            "Zone 6 (#8B5A2B) vs Zone 7 (#000000) contrast ratio ($contrastRatio) must be >= 3.0:1",
            contrastRatio >= 3.0
        )
    }
}
