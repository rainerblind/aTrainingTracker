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
import androidx.compose.ui.unit.dp
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
            zoneColor = Color(0xFF8B5A2B),
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

    @Test
    fun testZoneGaugeBar_usesProportionalWeightsUnderSegmentBadgeAndPrimaryBlueNeedle() {
        val barFile = java.io.File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/ZoneGaugeBar.kt").let {
            if (it.exists()) it else java.io.File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/ZoneGaugeBar.kt")
        }
        assertTrue("ZoneGaugeBar.kt must exist", barFile.exists())
        val source = barFile.readText()

        // 1. Outer vertical Column layout so track spans full width and badge sits underneath
        assertTrue("ZoneGaugeBar must use an outer Column layout", source.contains("Column("))
        assertTrue("ZoneGaugeBar must weight segments by state.segmentWeights", source.contains("state.segmentWeights.getOrElse(index)"))

        // 2. Primary Blue needle (MaterialTheme.colorScheme.primary) with 4.dp width instead of 3.dp Color.White
        assertTrue(
            "ZoneGaugeBar needle must resolve MaterialTheme.colorScheme.primary",
            source.contains("val primaryNeedleColor = MaterialTheme.colorScheme.primary")
        )
        assertTrue(
            "ZoneGaugeBar needle must draw with primaryNeedleColor",
            source.contains("color = primaryNeedleColor")
        )
        assertTrue(
            "ZoneGaugeBar needle must use 4.dp width",
            source.contains("4.dp.value * (totalHeightPx / totalBarHeight.value)")
        )
        assertFalse(
            "ZoneGaugeBar needle must not draw with Color.White fill",
            source.contains("color = Color.White,")
        )

        // 3. Active zone badge centered beneath active segment using MaterialTheme.colorScheme.onSurface
        assertTrue(
            "Active zone badge must render only under the active segment index (index == state.activeZoneIndex - 1)",
            source.contains("if (index == state.activeZoneIndex - 1)")
        )
        assertTrue(
            "Active zone badge must use MaterialTheme.colorScheme.onSurface for high-contrast legibility across all themes",
            source.contains("color = MaterialTheme.colorScheme.onSurface")
        )
    }

    /**
     * TST-TRK-046.4: SensorFieldState supports showValue and showBar properties (REQ-TRK-046).
     */
    @Test
    fun sensorFieldState_showValueAndShowBar_defaultsAndCopy() {
        val defaultState = SensorFieldState(
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
        assertTrue("showValue must default to true", defaultState.showValue)
        assertFalse("showBar must default to false", defaultState.showBar)

        val barOnly = defaultState.copy(showValue = false, showBar = true)
        assertFalse("showValue must be false in bar-only configuration", barOnly.showValue)
        assertTrue("showBar must be true in bar-only configuration", barOnly.showBar)

        val valueOnly = defaultState.copy(showValue = true, showBar = false)
        assertTrue("showValue must be true in value-only configuration", valueOnly.showValue)
        assertFalse("showBar must be false in value-only configuration", valueOnly.showBar)
    }

    /**
     * TST-UI-436.1: Active segment height elevation (1.6x base height), reserved container height,
     * and layout height invariance across live and stale states (REQ-UI-436, ATT-4928).
     */
    @Test
    fun testCalculateActiveTrackHeight_elevatesActiveZone_andKeepsInactiveUniform() {
        val baseHeight = 6.dp
        assertEquals(9.6.dp, calculateReservedActiveTrackHeight(baseHeight))

        val staleHeight = calculateActiveTrackHeight(trackHeight = baseHeight, activeZoneIndex = null, isStale = true)
        assertEquals(6.dp, staleHeight)

        val activeHeight = calculateActiveTrackHeight(trackHeight = baseHeight, activeZoneIndex = 3, isStale = false)
        assertEquals(9.6.dp, activeHeight)

        // ATT-4928: Container height must unconditionally reserve elevated track height + needle overhang (4.dp)
        val trackHeights = listOf(6.dp, 10.dp, 14.dp, 18.dp, 22.dp)
        for (th in trackHeights) {
            val reservedActive = calculateReservedActiveTrackHeight(th)
            val containerHeight = reservedActive + 4.dp
            assertEquals(th * 1.6f, reservedActive)
            assertEquals(th * 1.6f + 4.dp, containerHeight)
        }
    }

    /**
     * TST-UI-436.2: Dynamic bar height scaling across ViewSizes in bar-only mode (REQ-UI-436).
     */
    @Test
    fun testResolveBarOnlyTrackHeights_scalesWithViewSize() {
        // ZoneGaugeBar: SMALL = 10.dp, NORMAL = 14.dp, LARGE = 18.dp, EXTRA_LARGE = 22.dp (standard: 6.dp)
        assertEquals(10.dp, resolveZoneGaugeTrackHeight(ViewSize.SMALL, isBarOnly = true))
        assertEquals(14.dp, resolveZoneGaugeTrackHeight(ViewSize.NORMAL, isBarOnly = true))
        assertEquals(18.dp, resolveZoneGaugeTrackHeight(ViewSize.LARGE, isBarOnly = true))
        assertEquals(22.dp, resolveZoneGaugeTrackHeight(ViewSize.XLARGE, isBarOnly = true))
        assertEquals(6.dp, resolveZoneGaugeTrackHeight(ViewSize.NORMAL, isBarOnly = false))

        // PedalPowerBalanceBar: SMALL = 12.dp, NORMAL = 16.dp, LARGE = 20.dp, EXTRA_LARGE = 24.dp (standard: 8.dp)
        assertEquals(12.dp, resolvePedalBalanceTrackHeight(ViewSize.SMALL, isBarOnly = true))
        assertEquals(16.dp, resolvePedalBalanceTrackHeight(ViewSize.NORMAL, isBarOnly = true))
        assertEquals(20.dp, resolvePedalBalanceTrackHeight(ViewSize.LARGE, isBarOnly = true))
        assertEquals(24.dp, resolvePedalBalanceTrackHeight(ViewSize.XLARGE, isBarOnly = true))
        assertEquals(8.dp, resolvePedalBalanceTrackHeight(ViewSize.NORMAL, isBarOnly = false))

        // WPrimeBatteryGaugeBar: SMALL = 14.dp, NORMAL = 20.dp, LARGE = 26.dp, EXTRA_LARGE = 32.dp (standard: 14.dp)
        assertEquals(14.dp, resolveWPrimeBatteryHeight(ViewSize.SMALL, isBarOnly = true))
        assertEquals(20.dp, resolveWPrimeBatteryHeight(ViewSize.NORMAL, isBarOnly = true))
        assertEquals(26.dp, resolveWPrimeBatteryHeight(ViewSize.LARGE, isBarOnly = true))
        assertEquals(32.dp, resolveWPrimeBatteryHeight(ViewSize.XLARGE, isBarOnly = true))
        assertEquals(14.dp, resolveWPrimeBatteryHeight(ViewSize.NORMAL, isBarOnly = false))
    }

    /**
     * TST-UI-436.3: Contract verification of reservedActiveTrackHeight, container height, and active segment pill in ZoneGaugeBar.
     */
    @Test
    fun testZoneGaugeBar_sourceContainsActiveZoneElevationAndContainerHeight() {
        val barFile = java.io.File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/ZoneGaugeBar.kt").let {
            if (it.exists()) it else java.io.File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/ZoneGaugeBar.kt")
        }
        assertTrue("ZoneGaugeBar.kt must exist", barFile.exists())
        val source = barFile.readText()

        assertTrue(
            "ZoneGaugeBar must calculate reserved track height using 1.6f multiplier",
            source.contains("trackHeight * 1.6f")
        )
        assertTrue(
            "ZoneGaugeBar totalBarHeight must reserve reservedActiveTrackHeight + 4.dp",
            source.contains("reservedActiveTrackHeight + 4.dp")
        )
        assertTrue(
            "ZoneGaugeBar must render active segment elevation with RoundedCornerShape",
            source.contains("RoundedCornerShape(reservedActiveTrackHeight / 4)")
        )
        assertFalse(
            "ZoneGaugeBar must not contain obsolete 2-parameter calculateActiveTrackHeight overload",
            source.contains("fun calculateActiveTrackHeight(baseTrackHeight: Dp, isActive: Boolean)")
        )
    }
}
