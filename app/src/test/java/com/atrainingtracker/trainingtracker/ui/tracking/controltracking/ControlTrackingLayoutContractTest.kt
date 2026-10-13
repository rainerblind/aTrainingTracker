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
 * Structural and contract test verifying layout optimization on ControlTrackingScreen (REQ-UI-278, TST-UI-238, ATT-2189):
 * 1. Absence of PairingButtons in the primary screen body.
 * 2. Exposure of flexible bottomContent slot.
 * 3. Preservation of centered ControlTrackingButton via balanced weight(1f) spacers.
 * 4. Location permission gating (REQ-PRI-004) remaining intact.
 */
class ControlTrackingLayoutContractTest {

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
    fun testControlTrackingScreenHasNoPairingButtonsInBody() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt")
        assertTrue("ControlTrackingScreen.kt must exist", file.exists())

        val content = file.readText()

        // Extract the main ControlTrackingScreen composable function body
        val functionRegex = Regex("""fun ControlTrackingScreen\([\s\S]*?\)\s*\{([\s\S]*?)\n\}\n\n/\*\*""", RegexOption.MULTILINE)
        val match = functionRegex.find(content)
        assertTrue("ControlTrackingScreen function body must be matched", match != null)

        val functionBody = match!!.groupValues[1]

        // Verify PairingButtons composable invocation is absent in function body
        assertFalse(
            "PairingButtons must not be invoked within ControlTrackingScreen body",
            functionBody.contains("PairingButtons(")
        )
    }

    @Test
    fun testControlTrackingScreenExposesBottomContentSlot() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt")
        val content = file.readText()

        assertTrue(
            "ControlTrackingScreen must expose bottomContent slot parameter",
            content.contains("bottomContent: @Composable ColumnScope.() -> Unit = {}")
        )

        assertTrue(
            "ControlTrackingScreen must invoke bottomContent() within its layout",
            content.contains("bottomContent()")
        )
    }

    @Test
    fun testControlTrackingScreenPreservesCenteringSpacers() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt")
        val content = file.readText()

        val weightMatches = Regex("""Spacer\(modifier = Modifier\.weight\(1f\)\)""").findAll(content).toList()
        assertTrue(
            "ControlTrackingScreen must contain at least 2 weight(1f) spacers to center the control button",
            weightMatches.size >= 2
        )
    }

    @Test
    fun testControlTrackingScreenPreservesLocationPermissionGating() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt")
        val content = file.readText()

        assertTrue(
            "ControlTrackingScreen must verify ACCESS_FINE_LOCATION",
            content.contains("android.Manifest.permission.ACCESS_FINE_LOCATION")
        )

        assertTrue(
            "ControlTrackingScreen must gate Start button with handleStartClick",
            content.contains("onStart = handleStartClick")
        )

        assertTrue(
            "ControlTrackingScreen must display permission warning badge on ControlTrackingButton",
            content.contains("hasPermissionWarning = !hasLocationPermission")
        )
    }
}
