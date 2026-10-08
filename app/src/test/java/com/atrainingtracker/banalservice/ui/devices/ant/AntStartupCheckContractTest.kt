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

package com.atrainingtracker.banalservice.ui.devices.ant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract test verifying cold-start elimination of ANT+ installation checks
 * and dialog popups (REQ-UI-296, TST-UI-256, ATT-2513).
 */
class AntStartupCheckContractTest {

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
    fun testColdStartDoesNotPerformAntInstallationCheck() {
        val activityFile = findFile("src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt")
        assertTrue("MainActivityWithNavigation.kt must exist", activityFile.exists())

        val content = activityFile.readText()

        // Extract onCreate method body roughly
        val onCreateStart = content.indexOf("override fun onCreate(savedInstanceState: Bundle?)")
        assertTrue("onCreate must exist in MainActivityWithNavigation.kt", onCreateStart != -1)
        val onCreateEnd = content.indexOf("private fun checkBatteryOptimizations()", onCreateStart)
        assertTrue("checkBatteryOptimizations must follow onCreate", onCreateEnd != -1)

        val onCreateBody = content.substring(onCreateStart, onCreateEnd)

        // Verify checkANTInstallation is not invoked during onCreate
        assertFalse(
            "onCreate must not call checkANTInstallation() during cold start",
            onCreateBody.contains("checkANTInstallation()")
        )

        // Verify showInstallANTShitDialog is not invoked during onCreate
        assertFalse(
            "onCreate must not call showInstallANTShitDialog() during cold start",
            onCreateBody.contains("showInstallANTShitDialog()")
        )
    }
}
