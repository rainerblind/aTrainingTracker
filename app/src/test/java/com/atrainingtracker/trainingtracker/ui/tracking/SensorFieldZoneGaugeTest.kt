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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying SensorFieldState integration with ZoneGaugeState and ZoneDisplayOptions.showGauge
 * (REQ-UI-376, TST-UI-339.3).
 */
class SensorFieldZoneGaugeTest {

    @Test
    fun sensorFieldState_defaultsToNoZoneGauge() {
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

        assertFalse("showGauge must default to false", state.zoneDisplayOptions.showGauge)
        assertNull("zoneGaugeState must default to null", state.zoneGaugeState)
    }

    @Test
    fun sensorFieldState_withHrZoneGaugeState_isPreserved() {
        val gaugeState = ZoneGaugeState(
            segmentColors = ZoneGaugeMath.HR_SEGMENT_COLORS,
            needleFraction = 0.55f,
            activeZoneIndex = 3
        )
        val state = SensorFieldState(
            configHash = 1,
            sensorFieldId = 10,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "Heart Rate",
            filterDescription = "",
            value = "155",
            units = "bpm",
            zoneColor = Color(0xFFFFA500),
            zoneDisplayOptions = ZoneDisplayOptions(showGauge = true),
            zoneGaugeState = gaugeState
        )

        assertTrue(state.zoneDisplayOptions.showGauge)
        assertNotNull(state.zoneGaugeState)
        val nonNullState = state.zoneGaugeState!!
        assertEquals(5, nonNullState.segmentColors.size)
        assertEquals(0.55f, nonNullState.needleFraction ?: -1f, 0.001f)
        assertEquals(3, nonNullState.activeZoneIndex)
    }

    @Test
    fun sensorFieldState_withPowerZoneGaugeState_isPreserved() {
        val gaugeState = ZoneGaugeState(
            segmentColors = ZoneGaugeMath.POWER_SEGMENT_COLORS,
            needleFraction = 0.85f,
            activeZoneIndex = 6
        )
        val state = SensorFieldState(
            configHash = 2,
            sensorFieldId = 20,
            rowNr = 0,
            colNr = 1,
            viewSize = ViewSize.LARGE,
            label = "Power",
            filterDescription = "3s avg",
            value = "410",
            units = "W",
            zoneColor = Color(0xFF4E342E),
            zoneDisplayOptions = ZoneDisplayOptions(showGauge = true),
            zoneGaugeState = gaugeState
        )

        assertTrue(state.zoneDisplayOptions.showGauge)
        assertNotNull(state.zoneGaugeState)
        val nonNullPowerState = state.zoneGaugeState!!
        assertEquals(7, nonNullPowerState.segmentColors.size)
        assertEquals(0.85f, nonNullPowerState.needleFraction ?: -1f, 0.001f)
        assertEquals(6, nonNullPowerState.activeZoneIndex)
    }

    @Test
    fun sensorFieldState_staleGaugeState_hasNullNeedleAndNullActiveZone() {
        val staleGauge = ZoneGaugeState(
            segmentColors = ZoneGaugeMath.POWER_SEGMENT_COLORS,
            needleFraction = null,
            activeZoneIndex = null
        )
        val state = SensorFieldState(
            configHash = 3,
            sensorFieldId = 30,
            rowNr = 1,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "Power",
            filterDescription = "",
            value = "--",
            units = "W",
            zoneColor = Color.Transparent,
            zoneDisplayOptions = ZoneDisplayOptions(showGauge = true),
            zoneGaugeState = staleGauge
        )

        assertTrue(state.zoneDisplayOptions.showGauge)
        assertNotNull(state.zoneGaugeState)
        val nonNullStale = state.zoneGaugeState!!
        assertEquals(7, nonNullStale.segmentColors.size)
        assertNull(nonNullStale.needleFraction)
        assertNull(nonNullStale.activeZoneIndex)
    }

    @Test
    fun sensorFieldState_copyMutation_updatesZoneGaugeStateIndependently() {
        val initialGauge = ZoneGaugeState(
            segmentColors = ZoneGaugeMath.HR_SEGMENT_COLORS,
            needleFraction = 0.20f,
            activeZoneIndex = 1
        )
        val initial = SensorFieldState(
            configHash = 1,
            sensorFieldId = 10,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "Heart Rate",
            filterDescription = "",
            value = "110",
            units = "bpm",
            zoneColor = Color(0xFF7FFF00),
            zoneDisplayOptions = ZoneDisplayOptions(showGauge = true),
            zoneGaugeState = initialGauge
        )

        val updatedGauge = ZoneGaugeState(
            segmentColors = ZoneGaugeMath.HR_SEGMENT_COLORS,
            needleFraction = 0.40f,
            activeZoneIndex = 2
        )
        val modified = initial.copy(
            value = "135",
            zoneColor = Color(0xFF008000),
            zoneGaugeState = updatedGauge
        )

        assertEquals("135", modified.value)
        assertEquals(0.40f, modified.zoneGaugeState?.needleFraction ?: -1f, 0.001f)
        assertEquals(2, modified.zoneGaugeState?.activeZoneIndex)

        // Initial remains untouched
        assertEquals("110", initial.value)
        assertEquals(0.20f, initial.zoneGaugeState?.needleFraction ?: -1f, 0.001f)
        assertEquals(1, initial.zoneGaugeState?.activeZoneIndex)
    }
}
