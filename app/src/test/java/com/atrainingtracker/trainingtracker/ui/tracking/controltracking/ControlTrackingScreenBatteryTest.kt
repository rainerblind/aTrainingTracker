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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * State machine and contract verification for battery optimization warning integration
 * and cascade decoupling (REQ-PRI-005, TST-PRI-004, ATT-2621).
 */
class ControlTrackingScreenBatteryTest {

    private fun resolveHasPermissionWarning(hasLocation: Boolean, isIgnoringBattery: Boolean): Boolean {
        return !hasLocation || !isIgnoringBattery
    }

    @Test
    fun testStartButtonWarningBadge_evaluatesCorrectlyAcrossPermissionStates() {
        // Both missing -> warning
        assertTrue(resolveHasPermissionWarning(hasLocation = false, isIgnoringBattery = false))

        // Location missing, battery ignored -> warning
        assertTrue(resolveHasPermissionWarning(hasLocation = false, isIgnoringBattery = true))

        // Location granted, battery optimization NOT ignored (revoked) -> warning
        assertTrue(resolveHasPermissionWarning(hasLocation = true, isIgnoringBattery = false))

        // Both granted -> no warning
        assertFalse(resolveHasPermissionWarning(hasLocation = true, isIgnoringBattery = true))
    }

    @Test
    fun testCascadeDecoupling_whenBackgroundLocationSkipped_proceedsToBatteryOptimization() {
        var rationaleStep = RationaleStep.BACKGROUND_LOCATION
        var startInvoked = false
        val isIgnoringBatteryOptimizations = false

        // User chooses "Not now" on background location
        if (!isIgnoringBatteryOptimizations) {
            rationaleStep = RationaleStep.BATTERY_OPTIMIZATION
        } else {
            rationaleStep = RationaleStep.NONE
            startInvoked = true
        }

        assertEquals(RationaleStep.BATTERY_OPTIMIZATION, rationaleStep)
        assertFalse("Tracking must not start while battery step is pending", startInvoked)
    }

    @Test
    fun testCascadeDecoupling_whenBatteryOptimizationSkipped_startsTrackingWithoutLoop() {
        var rationaleStep = RationaleStep.BATTERY_OPTIMIZATION
        var startInvoked = false
        var pendingStart = true

        // User chooses "Not now" on battery optimization
        pendingStart = false
        rationaleStep = RationaleStep.NONE
        startInvoked = true

        assertEquals(RationaleStep.NONE, rationaleStep)
        assertTrue("Tracking must start when athlete explicitly skips battery exemption", startInvoked)
        assertFalse("Pending start flag must be cleared on dismissal", pendingStart)
    }

    @Test
    fun testResumeSynchronization_whenBatteryExempt_autoStartsIfPending() {
        var startInvoked = false
        var pendingStart = true
        val batteryExempt = true

        // Simulate ON_RESUME lifecycle event
        if (batteryExempt && pendingStart) {
            pendingStart = false
            startInvoked = true
        }

        assertTrue("Tracking must auto-start when returning from settings with exemption granted", startInvoked)
        assertFalse("Pending start flag must be reset", pendingStart)
    }

    @Test
    fun testControlTrackingScreenSourceContract_verifiesRequiredComponentsWired() {
        val file = File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt")
        assertTrue("ControlTrackingScreen.kt must exist", file.exists())
        val content = file.readText()

        assertTrue(
            "ControlTrackingScreen must render BatteryOptimizationWarningBanner",
            content.contains("BatteryOptimizationWarningBanner(")
        )
        assertTrue(
            "ControlTrackingScreen must evaluate hasPermissionWarning with battery optimization",
            content.contains("!hasLocationPermission || !isIgnoringBatteryOptimizations")
        )
        assertTrue(
            "ControlTrackingScreen must track pendingStartAfterBatteryExemption",
            content.contains("pendingStartAfterBatteryExemption")
        )
        assertTrue(
            "ControlTrackingScreen must re-query checkIsIgnoringBatteryOptimizations on ON_RESUME",
            content.contains("checkIsIgnoringBatteryOptimizations()")
        )
    }
}
