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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying SensorFieldState integration with PedalBalanceState (REQ-UI-377, TST-UI-340.3).
 */
class SensorFieldPedalBalanceTest {

    @Test
    fun sensorFieldState_defaultsToNoPedalBalanceState() {
        val state = SensorFieldState(
            configHash = 1,
            sensorFieldId = 10,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "Balance",
            filterDescription = "",
            value = "--",
            units = "%",
            zoneColor = Color.Transparent
        )

        assertNull("pedalBalanceState must default to null", state.pedalBalanceState)
    }

    @Test
    fun sensorFieldState_withOptimalPedalBalanceState_isPreserved() {
        val balanceState = PedalPowerBalanceMath.calculateBalanceState(51)
        val state = SensorFieldState(
            configHash = 1,
            sensorFieldId = 10,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "Balance",
            filterDescription = "",
            value = PedalPowerBalanceMath.formatBalanceString(balanceState),
            units = "%",
            zoneColor = Color.Transparent,
            pedalBalanceState = balanceState
        )

        assertNotNull(state.pedalBalanceState)
        val nonNullBalance = state.pedalBalanceState!!
        assertEquals(49, nonNullBalance.leftPercent)
        assertEquals(51, nonNullBalance.rightPercent)
        assertEquals(1, nonNullBalance.deviationPercent)
        assertEquals(DominantSide.RIGHT, nonNullBalance.dominantSide)
        assertEquals(PedalBalanceTier.OPTIMAL, nonNullBalance.tier)
        assertEquals(0.51f, nonNullBalance.indicatorFraction ?: 0f, 0.001f)
        assertFalse(nonNullBalance.isStale)
        assertEquals("49 | 51", state.value)
    }

    @Test
    fun sensorFieldState_withMildAsymmetryPedalBalanceState_isPreserved() {
        val balanceState = PedalPowerBalanceMath.calculateBalanceState(54)
        val state = SensorFieldState(
            configHash = 2,
            sensorFieldId = 20,
            rowNr = 0,
            colNr = 1,
            viewSize = ViewSize.NORMAL,
            label = "Balance",
            filterDescription = "",
            value = PedalPowerBalanceMath.formatBalanceString(balanceState),
            units = "%",
            zoneColor = Color.Transparent,
            pedalBalanceState = balanceState
        )

        assertNotNull(state.pedalBalanceState)
        val nonNullBalance = state.pedalBalanceState!!
        assertEquals(46, nonNullBalance.leftPercent)
        assertEquals(54, nonNullBalance.rightPercent)
        assertEquals(4, nonNullBalance.deviationPercent)
        assertEquals(DominantSide.RIGHT, nonNullBalance.dominantSide)
        assertEquals(PedalBalanceTier.MILD, nonNullBalance.tier)
        assertEquals(PedalPowerBalanceMath.MILD_COLOR, nonNullBalance.tierColor)
        assertEquals(0.54f, nonNullBalance.indicatorFraction ?: 0f, 0.001f)
        assertFalse(nonNullBalance.isStale)
        assertEquals("46 | 54", state.value)
    }

    @Test
    fun sensorFieldState_withSevereAsymmetryPedalBalanceState_isPreserved() {
        val balanceState = PedalPowerBalanceMath.calculateBalanceState(40)
        val state = SensorFieldState(
            configHash = 3,
            sensorFieldId = 30,
            rowNr = 1,
            colNr = 0,
            viewSize = ViewSize.LARGE,
            label = "Balance",
            filterDescription = "",
            value = PedalPowerBalanceMath.formatBalanceString(balanceState),
            units = "%",
            zoneColor = Color.Transparent,
            pedalBalanceState = balanceState
        )

        assertNotNull(state.pedalBalanceState)
        val nonNullBalance = state.pedalBalanceState!!
        assertEquals(60, nonNullBalance.leftPercent)
        assertEquals(40, nonNullBalance.rightPercent)
        assertEquals(10, nonNullBalance.deviationPercent)
        assertEquals(DominantSide.LEFT, nonNullBalance.dominantSide)
        assertEquals(PedalBalanceTier.SEVERE, nonNullBalance.tier)
        assertEquals(PedalPowerBalanceMath.SEVERE_COLOR, nonNullBalance.tierColor)
        assertEquals(0.40f, nonNullBalance.indicatorFraction ?: 0f, 0.001f)
        assertFalse(nonNullBalance.isStale)
        assertEquals("60 | 40", state.value)
    }

    @Test
    fun sensorFieldState_stalePedalBalanceState_hasNullIndicatorFractionAndIsStale() {
        val staleBalance = PedalPowerBalanceMath.calculateBalanceState(null)
        val state = SensorFieldState(
            configHash = 4,
            sensorFieldId = 40,
            rowNr = 1,
            colNr = 1,
            viewSize = ViewSize.NORMAL,
            label = "Balance",
            filterDescription = "",
            value = PedalPowerBalanceMath.formatBalanceString(staleBalance),
            units = "%",
            zoneColor = Color.Transparent,
            pedalBalanceState = staleBalance
        )

        assertNotNull(state.pedalBalanceState)
        val nonNullStale = state.pedalBalanceState!!
        assertTrue(nonNullStale.isStale)
        assertNull(nonNullStale.indicatorFraction)
        assertEquals("-- | --", state.value)
    }

    @Test
    fun sensorFieldState_copyMutation_updatesPedalBalanceStateIndependently() {
        val initialBalance = PedalPowerBalanceMath.calculateBalanceState(50)
        val initial = SensorFieldState(
            configHash = 5,
            sensorFieldId = 50,
            rowNr = 2,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "Balance",
            filterDescription = "",
            value = PedalPowerBalanceMath.formatBalanceString(initialBalance),
            units = "%",
            zoneColor = Color.Transparent,
            pedalBalanceState = initialBalance
        )

        val updatedBalance = PedalPowerBalanceMath.calculateBalanceState(53)
        val modified = initial.copy(
            value = PedalPowerBalanceMath.formatBalanceString(updatedBalance),
            pedalBalanceState = updatedBalance
        )

        assertEquals("47 | 53", modified.value)
        assertEquals(PedalBalanceTier.MILD, modified.pedalBalanceState?.tier)
        assertEquals(0.53f, modified.pedalBalanceState?.indicatorFraction ?: 0f, 0.001f)

        // Initial remains untouched
        assertEquals("50 | 50", initial.value)
        assertEquals(PedalBalanceTier.OPTIMAL, initial.pedalBalanceState?.tier)
        assertEquals(0.50f, initial.pedalBalanceState?.indicatorFraction ?: 0f, 0.001f)
    }
}
