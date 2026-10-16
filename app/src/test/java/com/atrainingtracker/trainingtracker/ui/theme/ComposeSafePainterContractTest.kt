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

package com.atrainingtracker.trainingtracker.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract test enforcing REQ-UI-380 / TST-UI-339.2:
 * Defensive Jetpack Compose Resource Resolution Contract Verification.
 *
 * Asserts that all critical Compose UI components handling dynamic sensor, protocol,
 * device, and cloud integration icons invoke [safePainterResource] rather than raw [painterResource],
 * guaranteeing total crash immunity on density-split APKs and custom DPI scaling.
 */
class ComposeSafePainterContractTest {

    private val targetFiles = listOf(
        "com/atrainingtracker/banalservice/ui/devices/PairingProtocolBottomSheet.kt",
        "com/atrainingtracker/banalservice/ui/devices/DeviceTypeSelectionDialog.kt",
        "com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceItem.kt",
        "com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt",
        "com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorStatus.kt",
        "com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SearchArea.kt",
        "com/atrainingtracker/trainingtracker/ui/settings/dropbox/DropboxConnectionHeader.kt",
        "com/atrainingtracker/trainingtracker/ui/settings/dropbox/DropboxSettingsDialog.kt",
        "com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/ActivityTypeSelectionDialog.kt",
        "com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingButton.kt"
    )

    private fun resolveSourceFile(relativePath: String): File {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            File(projectDir, "src/main/java/$relativePath"),
            File(projectDir, "app/src/main/java/$relativePath"),
            File(projectDir, "../app/src/main/java/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: File(projectDir, "app/src/main/java/$relativePath")
    }

    @Test
    fun testAllTargetComposeComponents_existAndUseSafePainterResource() {
        for (relPath in targetFiles) {
            val file = resolveSourceFile(relPath)
            assertTrue("Target source file must exist: ${file.absolutePath}", file.exists())

            val content = file.readText()
            assertTrue(
                "Source file ${file.name} must invoke safePainterResource for dynamic icons (REQ-UI-380.2)",
                content.contains("safePainterResource(")
            )
            assertFalse(
                "Source file ${file.name} must NOT import raw painterResource to prevent unhandled Resources\$NotFoundException",
                content.contains("import androidx.compose.ui.res.painterResource")
            )
            assertFalse(
                "Source file ${file.name} must NOT invoke raw painterResource(",
                content.contains("painter = painterResource(") || content.contains("iconPainter = painterResource(")
            )
        }
    }

    @Test
    fun testAllTargetComposeComponents_supplySemanticFallbackVector() {
        for (relPath in targetFiles) {
            val file = resolveSourceFile(relPath)
            val content = file.readText()
            // Verify that safePainterResource calls supply a fallback ImageVector
            assertTrue(
                "Source file ${file.name} must supply a fallback ImageVector parameter to safePainterResource",
                content.contains("fallback = Icons.Default.") || content.contains("fallback = ")
            )
        }
    }
}
