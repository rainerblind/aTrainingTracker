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
 * Contract test for ControlTrackingScreen sensor header layout isolation (REQ-UI-301, TST-UI-261, ATT-2479):
 * 1. Verification of dedicated SearchArea slot above device rows.
 * 2. Elimination of legacy z-stacked Box that caused ResearchButton to collide with RemoteDevices.
 * 3. Side-by-side Row container allocating Modifier.weight(1f) to RemoteDevices when devices are present.
 * 4. Centered ResearchButton layout when no devices are present.
 * 5. Presence of multi-device preview covering 5+ active sensors.
 */
class ControlTrackingSensorHeaderContractTest {

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
    fun testSearchAreaIsPlacedInDedicatedSlotAboveDevices() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt")
        assertTrue("ControlTrackingScreen.kt must exist", file.exists())

        val content = file.readText()

        val searchAreaIndex = content.indexOf("SearchArea(")
        val devicesNotEmptyIndex = content.indexOf("if (devices.isNotEmpty())")

        assertTrue("SearchArea must be present in ControlTrackingScreen", searchAreaIndex != -1)
        assertTrue("devices.isNotEmpty() condition must be present", devicesNotEmptyIndex != -1)
        assertTrue(
            "SearchArea must precede the devices section in the vertical hierarchy",
            searchAreaIndex < devicesNotEmptyIndex
        )
    }

    @Test
    fun testLegacyCollidingBoxStructureIsEliminated() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt")
        val content = file.readText()

        assertFalse(
            "TopStart aligned Box over TopCenter RemoteDevices must be completely eliminated",
            content.contains("Modifier.align(Alignment.TopStart)") && content.contains("Modifier.align(Alignment.TopCenter)")
        )
    }

    @Test
    fun testSideBySideRowWithWeightForRemoteDevices() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt")
        val content = file.readText()

        assertTrue(
            "RemoteDevices must be assigned weight(1f) to prevent collision with ResearchButton",
            content.contains("RemoteDevices(") && content.contains("modifier = Modifier.weight(1f)")
        )

        assertTrue(
            "ResearchButton and RemoteDevices must be wrapped in a non-overlapping Row when devices are present",
            content.contains("if (devices.isNotEmpty())") && content.contains("verticalAlignment = Alignment.CenterVertically")
        )
    }

    @Test
    fun testCenteredResearchButtonWhenZeroDevices() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt")
        val content = file.readText()

        assertTrue(
            "ResearchButton must be centered when no devices are present",
            content.contains("horizontalArrangement = Arrangement.Center") &&
                    content.contains("else if (showResearchButton)")
        )
    }

    @Test
    fun testMultiDevicePreviewExists() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt")
        val content = file.readText()

        assertTrue(
            "PreviewControlTrackingScreenMultiDevices preview composable must exist",
            content.contains("fun PreviewControlTrackingScreenMultiDevices()")
        )
        assertTrue(
            "Multi-device preview must include at least 5 devices to verify non-collision",
            content.contains("RemoteDeviceUIData(5") && content.contains("RemoteDeviceUIData(6")
        )
    }
}
