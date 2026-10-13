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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural and architectural contract test for RouteSelectorSheet mid-ride entry point
 * and design system alignment (REQ-UI-282 / TST-UI-242.1 / ATT-2462).
 */
class RouteSelectorMidRideContractTest {

    private val routeSelectorFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheet.kt")
    private val trackingTabsFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt")

    @Test
    fun routeSelectorSheet_declaresMidRideParameters() {
        val content = routeSelectorFile.readText()

        // Verify parameter exposure on RouteSelectorModalBottomSheet
        assertTrue("RouteSelectorModalBottomSheet must expose isMidRide parameter",
            content.contains("fun RouteSelectorModalBottomSheet(") && content.contains("isMidRide: Boolean = false"))
        assertTrue("RouteSelectorModalBottomSheet must expose onTakeMeHome parameter",
            content.contains("onTakeMeHome: (() -> Unit)? = null"))

        // Verify parameter exposure on RouteSelectorContent
        assertTrue("RouteSelectorContent must expose isMidRide parameter",
            content.contains("fun RouteSelectorContent(") && content.contains("isMidRide: Boolean = false"))
        assertTrue("RouteSelectorContent must expose onTakeMeHome parameter",
            content.contains("onTakeMeHome: (() -> Unit)? = null"))
    }

    @Test
    fun routeSelectorSheet_conditionallyRendersMidRideHeimwegCard() {
        val content = routeSelectorFile.readText()

        // Must guard MidRideHeimwegCard with isMidRide && onTakeMeHome != null
        assertTrue("MidRideHeimwegCard must be guarded by isMidRide check",
            content.contains("if (isMidRide && onTakeMeHome != null)"))
        assertTrue("MidRideHeimwegCard must be invoked when condition holds",
            content.contains("MidRideHeimwegCard("))
    }

    @Test
    fun midRideHeimwegCard_adheresToDesignSystemTokens() {
        val content = routeSelectorFile.readText()

        // Verify Rule 23 baseline tokens
        assertTrue("MidRideHeimwegCard must use RoundedCornerShape(12.dp)",
            content.contains("RoundedCornerShape(12.dp)"))
        assertTrue("MidRideHeimwegCard must use TTColor.RouteSelected domain accent",
            content.contains("TTColor.RouteSelected"))
        assertTrue("MidRideHeimwegCard must use Icons.Default.Home",
            content.contains("Icons.Default.Home"))
        assertTrue("MidRideHeimwegCard must use take_me_home_title",
            content.contains("R.string.take_me_home_title"))
        assertTrue("MidRideHeimwegCard must use take_me_home_desc",
            content.contains("R.string.take_me_home_desc"))
    }

    @Test
    fun trackingTabsScreen_wiresMidRideReturnNavigation() {
        val content = trackingTabsFile.readText()

        assertTrue("TrackingTabsScreen must pass isMidRide to RouteSelectorModalBottomSheet",
            content.contains("isMidRide = isMidRide"))
        assertTrue("TrackingTabsScreen must compute isMidRide from TrackingMode",
            content.contains("val isMidRide = trackingMode == TrackingMode.TRACKING || trackingMode == TrackingMode.PAUSED"))
        assertTrue("TrackingTabsScreen must trigger returnNavRepo.startTakeMeHome() onTakeMeHome",
            content.contains("returnNavRepo.startTakeMeHome()"))
    }
}
