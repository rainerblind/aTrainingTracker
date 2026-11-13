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
import io.mockk.mockk
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
        assertTrue(state.zoneDisplayOptions.showRightBar)
        assertFalse(state.zoneDisplayOptions.showTextColor)
        assertFalse("isArtifactGated must default to false", state.isArtifactGated)
        assertEquals(0.75f, state.dfaA1ThresholdT1)
        assertEquals(0.50f, state.dfaA1ThresholdT2)
    }

    @Test
    fun sensorFieldState_dfaA1Thresholds_defaultsAndPreservesCustomValues() {
        val defaultState = SensorFieldState(
            configHash = 1,
            sensorFieldId = 10,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "DFA a1",
            filterDescription = "",
            value = "--.--",
            units = "",
            zoneColor = Color.Transparent
        )
        assertEquals(0.75f, defaultState.dfaA1ThresholdT1)
        assertEquals(0.50f, defaultState.dfaA1ThresholdT2)

        val customState = defaultState.copy(dfaA1ThresholdT1 = 0.80f, dfaA1ThresholdT2 = 0.55f)
        assertEquals(0.80f, customState.dfaA1ThresholdT1)
        assertEquals(0.55f, customState.dfaA1ThresholdT2)
    }

    @Test
    fun sensorFieldState_isArtifactGated_defaultsToFalseAndPreservesExplicitTrue() {
        val defaultState = SensorFieldState(
            configHash = 1,
            sensorFieldId = 10,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "DFA a1",
            filterDescription = "",
            value = "--.--",
            units = "",
            zoneColor = Color.Transparent
        )
        assertFalse("isArtifactGated must default to false", defaultState.isArtifactGated)

        val gatedState = defaultState.copy(isArtifactGated = true)
        assertTrue("isArtifactGated = true must be preserved", gatedState.isArtifactGated)
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

        val contrastRatio = com.atrainingtracker.trainingtracker.ui.theme.TTColor.calculateContrastRatio(
            zone6Color,
            zone7Color
        )
        assertTrue(
            "Zone 6 (#8B5A2B) vs Zone 7 (#000000) contrast ratio ($contrastRatio) must be >= 3.0:1",
            contrastRatio >= 3.0
        )
    }

    /**
     * TST-TRK-046.4: Empty-Tile Fallback Logic in SensorFieldView:
     * When showValue == false, but no active bar can be rendered (!hasActiveBar),
     * value row MUST render as a fallback so the sensor tile is never empty.
     */
    @Test
    fun sensorFieldState_emptyTileFallbackLogic() {
        fun shouldRenderValue(state: SensorFieldState): Boolean {
            val hasActiveBar = state.showBar && (
                state.zoneGaugeState != null ||
                state.pedalBalanceState != null ||
                state.wPrimeBatteryState != null
            )
            return state.showValue || !hasActiveBar
        }

        val baseState = SensorFieldState(
            configHash = 1,
            sensorFieldId = 10,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "Power",
            filterDescription = "",
            value = "250",
            units = "W",
            zoneColor = Color.Green
        )

        // Case 1: Standard tile (showValue = true, showBar = false) -> renders value
        assertTrue(shouldRenderValue(baseState.copy(showValue = true, showBar = false)))

        // Case 2: Both active (showValue = true, showBar = true with active gauge) -> renders value
        val mockGauge = mockk<ZoneGaugeState>()
        assertTrue(shouldRenderValue(baseState.copy(showValue = true, showBar = true, zoneGaugeState = mockGauge)))

        // Case 3: Bar-only tile with active gauge (showValue = false, showBar = true, zoneGaugeState != null) -> bar only, no value
        assertFalse(shouldRenderValue(baseState.copy(showValue = false, showBar = true, zoneGaugeState = mockGauge)))

        // Case 4: Bar-only tile with NO active gauge (e.g. unconfigured zones or unsupported sport) -> falls back to value!
        assertTrue("Tile without active bar must fall back to rendering value",
            shouldRenderValue(baseState.copy(showValue = false, showBar = true, zoneGaugeState = null)))

        // Case 5: Both toggled false (defensive against corrupt db) -> falls back to value!
        assertTrue("Tile with both flags false must fall back to rendering value",
            shouldRenderValue(baseState.copy(showValue = false, showBar = false)))

        // Case 6: Bar-only with active pedal balance -> bar only, no value
        val mockBalance = mockk<PedalBalanceState>()
        assertFalse(shouldRenderValue(baseState.copy(showValue = false, showBar = true, pedalBalanceState = mockBalance)))

        // Case 7: Bar-only with active W' battery -> bar only, no value
        val mockWPrime = mockk<WPrimeBatteryState>()
        assertFalse(shouldRenderValue(baseState.copy(showValue = false, showBar = true, wPrimeBatteryState = mockWPrime)))
    }
}
