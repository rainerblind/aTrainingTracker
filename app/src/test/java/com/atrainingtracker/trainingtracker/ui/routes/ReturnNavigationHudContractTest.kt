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

import com.atrainingtracker.banalservice.sensor.formater.AltitudeFormatter
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
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
        assertEquals(DistanceFormatter().format_with_units(12400.0), state.formattedRemainingDistance)
        assertEquals("+${AltitudeFormatter().format_with_units(180.0)}", state.formattedRemainingClimb)
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
        assertEquals(DistanceFormatter().format_with_units(3500.0), state.formattedRemainingDistance)
        assertEquals("+${AltitudeFormatter().format_with_units(45.0)}", state.formattedRemainingClimb)
    }

    @Test
    fun hudState_defaultValues_areInactiveAndEmpty() {
        val defaultState = ReturnNavigationState()
        assertFalse(defaultState.isActive)
        assertEquals("", defaultState.destinationName)
        assertFalse(defaultState.isHomeDestination)
        assertEquals(0.0, defaultState.remainingDistanceMeters, 0.001)
        assertEquals(0.0, defaultState.remainingElevationGainMeters, 0.001)
        assertEquals(0.0, defaultState.durationSeconds, 0.001)
        assertEquals("", defaultState.formattedDuration)
        assertEquals("", defaultState.formattedClockTime)
        assertFalse(defaultState.isReverseReturn)
        assertFalse(defaultState.isTakeMeHomeMode)
        org.junit.Assert.assertNull(defaultState.snappedRouteId)
        assertFalse(defaultState.isDismissed)
        assertFalse(defaultState.hasRemainingMetrics)
    }

    @Test
    fun hudState_reverseReturnAndTakeMeHomeModes_retainProperties() {
        val state = ReturnNavigationState(
            isActive = true,
            destinationName = "Startpunkt",
            isReverseReturn = true,
            isTakeMeHomeMode = false,
            snappedRouteId = 42L,
            remainingDistanceMeters = 8200.0,
            remainingElevationGainMeters = 110.0
        )

        assertTrue(state.isActive)
        assertTrue(state.isReverseReturn)
        assertFalse(state.isTakeMeHomeMode)
        assertEquals(42L, state.snappedRouteId)
        assertTrue(state.hasRemainingMetrics)
        assertEquals(DistanceFormatter().format_with_units(8200.0), state.formattedRemainingDistance)
        assertEquals("+${AltitudeFormatter().format_with_units(110.0)}", state.formattedRemainingClimb)
    }

    @Test
    fun hudState_stopReturnActionResource_existsInResourceTable() {
        assertTrue(
            "R.string.nav_stop_return must be defined in resource table",
            com.atrainingtracker.R.string.nav_stop_return != 0
        )
    }
}

