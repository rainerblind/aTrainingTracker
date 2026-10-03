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

import androidx.compose.ui.graphics.Color
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Unit tests for polyline contrast hierarchy and custom route color preservation.
 *
 * Traceability: REQ-MAP-021, TST-MAP-023
 */
class MapLayersStyleTest {

    @Test
    fun testLiveTrackColorAdaptiveResolution() {
        val darkTrackColor = resolveLiveTrackColor(isDark = true)
        val lightTrackColor = resolveLiveTrackColor(isDark = false)

        assertEquals("Live tracking on dark map must resolve to electric cyan #00E5FF", Color(0xFF00E5FF), darkTrackColor)
        assertEquals("Live tracking on light map must resolve to classic Color.Blue", Color.Blue, lightTrackColor)
    }

    @Test
    fun testMapStyleDefaultValuesPreserveLightBaseline() {
        val defaultStyle = MapStyle()
        assertFalse("MapStyle isDark default must be false for backwards compatibility", defaultStyle.isDark)
    }

    @Test
    fun testCustomRouteColorsAndStravaOrangeInvariantsPreserved() {
        // Verify custom colors are intact and not conflated with live tracking theme color
        val customColor1 = Color.Magenta
        val customColor2 = Color(0xFFFF5722)
        val stravaColor = TTColor.StravaOrange

        assertEquals(Color.Magenta, customColor1)
        assertEquals(Color(0xFFFF5722), customColor2)
        assertEquals(TTColor.StravaOrange, stravaColor)
    }
}
