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

package com.atrainingtracker.trainingtracker.ui.routes

import com.atrainingtracker.trainingtracker.routes.ReturnNavigationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UI contract & state verification test for ReturnNavigationHud and metrics display (REQ-MAP-029 / TST-MAP-031.4).
 */
class ReturnNavigationHudContractTest {

    @Test
    fun hudState_formatting_displaysCorrectTokens() {
        val state = ReturnNavigationState(
            isActive = true,
            destinationName = "Home Base",
            isHomeDestination = true,
            remainingDistanceMeters = 12400.0,
            remainingElevationGainMeters = 180.0,
            durationSeconds = 2040.0,
            formattedDuration = "~34 min",
            formattedClockTime = "18:42",
            isDismissed = false
        )

        assertTrue(state.hasRemainingMetrics)
        assertEquals("12.4 km", state.formattedRemainingDistance)
        assertEquals("+180 m", state.formattedRemainingClimb)
        assertEquals("18:42", state.formattedClockTime)
        assertEquals("~34 min", state.formattedDuration)
    }

    @Test
    fun hudState_whenDismissed_hasRemainingMetricsIsFalse() {
        val state = ReturnNavigationState(
            isActive = true,
            destinationName = "Home Base",
            remainingDistanceMeters = 5000.0,
            isDismissed = true
        )

        assertFalse(state.hasRemainingMetrics)
    }

    @Test
    fun hudState_whenInactive_hasRemainingMetricsIsFalse() {
        val state = ReturnNavigationState(
            isActive = false,
            remainingDistanceMeters = 5000.0,
            isDismissed = false
        )

        assertFalse(state.hasRemainingMetrics)
    }

    @Test
    fun hudState_withZeroRemainingDistance_hasRemainingMetricsIsFalse() {
        val state = ReturnNavigationState(
            isActive = true,
            remainingDistanceMeters = 0.0,
            isDismissed = false
        )

        assertFalse(state.hasRemainingMetrics)
    }

    @Test
    fun hudState_whenHomeDestination_formatsProperly() {
        val state = ReturnNavigationState(
            isActive = true,
            destinationName = "Zu Hause",
            isHomeDestination = true,
            remainingDistanceMeters = 3500.0,
            remainingElevationGainMeters = 45.0
        )

        assertTrue(state.isHomeDestination)
        assertEquals("3.5 km", state.formattedRemainingDistance)
        assertEquals("+45 m", state.formattedRemainingClimb)
    }
}
