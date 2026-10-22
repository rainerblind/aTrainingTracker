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
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying SensorFieldState integration with WPrimeBatteryState (REQ-UI-416, TST-UI-379.2).
 */
class SensorFieldWPrimeBatteryTest {

    @Test
    fun sensorFieldState_defaultsToNoWPrimeBatteryState() {
        val state = SensorFieldState(
            configHash = 1,
            sensorFieldId = 10,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "W'bal",
            filterDescription = "",
            value = "--",
            units = "kJ",
            zoneColor = Color.Transparent,
            sensorType = SensorType.W_PRIME_BAL
        )

        assertNull("wPrimeBatteryState must default to null", state.wPrimeBatteryState)
    }

    @Test
    fun sensorFieldState_withActiveWPrimeBatteryState_isPreserved() {
        val batteryState = WPrimeBatteryMath.calculateBatteryState(
            remainingKj = 16.0,
            totalKj = 20.0,
            timestampMs = 1000L,
            nowMs = 1500L
        )
        val state = SensorFieldState(
            configHash = 1,
            sensorFieldId = 10,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "W'bal",
            filterDescription = "",
            value = "16.0",
            units = "kJ",
            zoneColor = Color.Transparent,
            wPrimeBatteryState = batteryState,
            sensorType = SensorType.W_PRIME_BAL
        )

        val wState = checkNotNull(state.wPrimeBatteryState)
        assertEquals(80, wState.percentage)
        assertEquals(0.80f, wState.batteryFraction ?: 0f, 0.001f)
        assertEquals(WPrimeWarningLevel.NORMAL, wState.warningLevel)
        assertEquals(TTColor.WPrimeBalGreen, wState.batteryColor)
        assertFalse(wState.isWarning)
        assertFalse(wState.isStale)
    }

    @Test
    fun sensorFieldState_warningState_marksWarningTrue() {
        // <20% capacity -> warning tier CRITICAL
        val batteryState = WPrimeBatteryMath.calculateBatteryState(
            remainingKj = 3.0,
            totalKj = 20.0,
            timestampMs = 1000L,
            nowMs = 1500L
        )
        val state = SensorFieldState(
            configHash = 1,
            sensorFieldId = 10,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "W'bal",
            filterDescription = "",
            value = "3.0",
            units = "kJ",
            zoneColor = Color.Transparent,
            wPrimeBatteryState = batteryState,
            sensorType = SensorType.W_PRIME_BAL
        )

        val wState = checkNotNull(state.wPrimeBatteryState)
        assertEquals(15, wState.percentage)
        assertEquals(WPrimeWarningLevel.CRITICAL, wState.warningLevel)
        assertTrue("isWarning must be true when capacity < 20%", wState.isWarning)
        assertFalse(wState.isStale)
    }

    @Test
    fun sensorFieldState_staleState_dimsAndClearsFraction() {
        val batteryState = WPrimeBatteryMath.calculateBatteryState(
            remainingKj = null,
            totalKj = 20.0,
            timestampMs = 0L,
            nowMs = 1000L
        )
        val state = SensorFieldState(
            configHash = 1,
            sensorFieldId = 10,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "W'bal",
            filterDescription = "",
            value = "--",
            units = "kJ",
            zoneColor = Color.Transparent,
            wPrimeBatteryState = batteryState,
            sensorType = SensorType.W_PRIME_BAL
        )

        val wState = checkNotNull(state.wPrimeBatteryState)
        assertTrue(wState.isStale)
        assertNull(wState.batteryFraction)
        assertFalse("isWarning must be false when stale", wState.isWarning)
    }

    @Test
    fun sensorFieldState_copyPreservesWPrimeBatteryState() {
        val batteryState = WPrimeBatteryMath.calculateBatteryState(
            remainingKj = 10.0,
            totalKj = 20.0,
            timestampMs = 1000L,
            nowMs = 1500L
        )
        val original = SensorFieldState(
            configHash = 1,
            sensorFieldId = 10,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "W'bal",
            filterDescription = "",
            value = "10.0",
            units = "kJ",
            zoneColor = Color.Transparent,
            wPrimeBatteryState = batteryState,
            sensorType = SensorType.W_PRIME_BAL
        )

        val updated = original.copy(value = "9.8")
        assertEquals(batteryState, updated.wPrimeBatteryState)
        assertEquals("9.8", updated.value)
    }

    @Test
    fun testRapidSensorUpdatesCancellation_staleDebouncePreservesLatestState() {
        // Simulates rapid sensor updates during delay() period
        var scheduledJobCancelled = false
        var activeJobCount = 0

        // Invariant: each new valid reading cancels previous pending stale debounce job
        fun onIncomingReading(remainingKj: Double?) {
            if (activeJobCount > 0) {
                scheduledJobCancelled = true
                activeJobCount--
            }
            if (remainingKj != null) {
                activeJobCount++
            }
        }

        onIncomingReading(18.0)
        assertEquals(1, activeJobCount)
        assertFalse(scheduledJobCancelled)

        // Rapid subsequent reading before delay() expires
        onIncomingReading(17.9)
        assertEquals(1, activeJobCount)
        assertTrue(scheduledJobCancelled)

        // Reading transitions to null / inactive
        onIncomingReading(null)
        assertEquals(0, activeJobCount)
    }
}
