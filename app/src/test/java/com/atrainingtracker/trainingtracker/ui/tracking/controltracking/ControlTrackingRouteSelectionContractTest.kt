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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural and contract tests for relocating the Route Selection entry point to exclusively
 * the Control Tracking screen with app-consistent branding (REQ-UI-279 / TST-UI-239 / ATT-2458):
 * 1. Complete removal of RouteActionChipRow from SensorGridScreen (REQ-UI-279.1).
 * 2. Preservation of in-ride auto-detection banner in SensorGridScreen.
 * 3. RouteSelectionButton design tokens (12.dp rounded corners, green domain accents, icons, strings) (REQ-UI-279.3).
 * 4. TrackingTabsScreen wiring into ControlTrackingScreen's bottomContent slot (REQ-UI-279.2 / REQ-UI-279.4).
 */
class ControlTrackingRouteSelectionContractTest {

    private fun findFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("File not found in candidates: $relativePath")
    }

    @Test
    fun testSensorGridScreen_doesNotContainRouteActionChipRow() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        assertTrue("SensorGridScreen.kt must exist", file.exists())

        val content = file.readText()

        assertFalse(
            "SensorGridScreen must not invoke RouteActionChipRow (REQ-UI-279.1)",
            content.contains("RouteActionChipRow(")
        )
        assertFalse(
            "SensorGridScreen must not define RouteActionChipRow composable",
            content.contains("fun RouteActionChipRow(")
        )
    }

    @Test
    fun testSensorGridScreen_doesNotIntegrateAutoDetectedRouteBanner() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        org.junit.Assert.assertFalse(
            "SensorGridScreen must not host AutoDetectedRouteBanner (REQ-UI-311)",
            content.contains("AutoDetectedRouteBanner(")
        )
        org.junit.Assert.assertFalse(
            "SensorGridScreen must not handle activateCandidate",
            content.contains("activateCandidate")
        )
        org.junit.Assert.assertFalse(
            "SensorGridScreen must not handle dismissCandidate",
            content.contains("dismissCandidate")
        )
    }

    @Test
    fun testRouteSelectionButton_adheresToRule23AndBrandingDesignTokens() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/components/RouteSelectionButton.kt")
        assertTrue("RouteSelectionButton.kt must exist", file.exists())

        val content = file.readText()

        assertTrue(
            "RouteSelectionButton must declare fun RouteSelectionButton composable",
            content.contains("fun RouteSelectionButton(")
        )
        assertTrue(
            "RouteSelectionButton must use 12.dp rounded corners (Rule 23 / Section 5.3)",
            content.contains("RoundedCornerShape(12.dp)")
        )
        assertTrue(
            "RouteSelectionButton must incorporate TTColor.RouteSelected for subtle green border/accent (Section 5.4)",
            content.contains("TTColor.RouteSelected")
        )
        assertTrue(
            "RouteSelectionButton must display ic_route icon",
            content.contains("R.drawable.ic_route")
        )
        assertTrue(
            "RouteSelectionButton must reference route_action_select string",
            content.contains("R.string.route_action_select")
        )
        assertTrue(
            "RouteSelectionButton must reference route_action_select_desc string",
            content.contains("R.string.route_action_select_desc")
        )
        assertTrue(
            "RouteSelectionButton must reference route_action_clear string",
            content.contains("R.string.route_action_clear")
        )
        assertTrue(
            "RouteSelectionButton must reference route_metrics_format string",
            content.contains("R.string.route_metrics_format")
        )
        assertTrue(
            "RouteSelectionButton must use AutoMirrored ArrowForwardIos for navigation chevron",
            content.contains("Icons.AutoMirrored.Default.ArrowForwardIos")
        )
        assertTrue(
            "RouteSelectionButton must use Icons.Default.Close for 1-tap route cancellation",
            content.contains("Icons.Default.Close")
        )
    }

    @Test
    fun testTrackingTabsScreen_wiresRouteSelectionButtonToBottomContentSlot() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt")
        assertTrue("TrackingTabsScreen.kt must exist", file.exists())

        val content = file.readText()

        assertTrue(
            "TrackingTabsScreen must import or reference RouteSelectionButton",
            content.contains("RouteSelectionButton(")
        )
        assertTrue(
            "TrackingTabsScreen must pass RouteSelectionButton inside bottomContent slot (REQ-UI-279.2)",
            content.contains("bottomContent = {") && content.contains("RouteSelectionButton(")
        )
        assertTrue(
            "TrackingTabsScreen must host RouteSelectorModalBottomSheet",
            content.contains("RouteSelectorModalBottomSheet(")
        )
        assertTrue(
            "TrackingTabsScreen must declare RouteSelectorViewModel",
            content.contains("RouteSelectorViewModel")
        )
    }
}
