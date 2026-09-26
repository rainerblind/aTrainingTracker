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
}
