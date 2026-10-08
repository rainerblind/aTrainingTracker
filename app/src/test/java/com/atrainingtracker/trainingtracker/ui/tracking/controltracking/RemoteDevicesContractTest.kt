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
 * Contract test for RemoteDevices component parameterization and spacing (REQ-UI-301, TST-UI-261, ATT-2479):
 * 1. Verification of modifier parameter forwarding to root LazyRow.
 * 2. Proper spacing between tiles using spacedBy(8.dp, Alignment.CenterHorizontally).
 * 3. Horizontal edge padding (8.dp) to eliminate screen margin clipping.
 * 4. Stable item keying by device id.
 * 5. Early return on empty devices list.
 */
class RemoteDevicesContractTest {

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
    fun testRemoteDevicesAcceptsModifierParameter() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/RemoteDevices.kt")
        assertTrue("RemoteDevices.kt must exist", file.exists())

        val content = file.readText()

        assertTrue(
            "RemoteDevices signature must accept optional modifier parameter with default value",
            content.contains("modifier: Modifier = Modifier")
        )

        assertTrue(
            "LazyRow must apply the received modifier parameter",
            content.contains("LazyRow(\n        modifier = modifier.fillMaxWidth()")
        )
    }

    @Test
    fun testRemoteDevicesTileSpacingAndPadding() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/RemoteDevices.kt")
        val content = file.readText()

        assertTrue(
            "LazyRow must use spacedBy(8.dp, Alignment.CenterHorizontally) to prevent sensor tile clustering",
            content.contains("horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)")
        )

        assertTrue(
            "LazyRow must provide 8.dp horizontal content padding to clear outer screen boundaries",
            content.contains("contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)")
        )
    }

    @Test
    fun testRemoteDevicesStableKeying() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/RemoteDevices.kt")
        val content = file.readText()

        assertTrue(
            "LazyRow items must specify stable key based on device id",
            content.contains("key = { it.id }")
        )
    }

    @Test
    fun testRemoteDevicesEmptyGuard() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/RemoteDevices.kt")
        val content = file.readText()

        assertTrue(
            "RemoteDevices must return early when devices list is empty",
            content.contains("if (devices.isEmpty()) return")
        )
    }
}
