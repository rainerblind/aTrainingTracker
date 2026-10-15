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

package com.atrainingtracker.trainingtracker.zones

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract test for ZonesSettingsActivity header row.
 *
 * Enforces [REQ-UI-378] and [TST-UI-341.1]:
 * 1. Compact icon buttons (Icons.Default.Calculate and Icons.Default.RestartAlt).
 * 2. Zero TextButton controls in the header row to prevent multi-line character wrapping and vertical screen blowout.
 * 3. Bounded title container with maxLines = 1 and TextOverflow.Ellipsis.
 */
class ZonesSettingsHeaderContractTest {

    private fun resolveActivityFile(): File {
        var current: File? = File(".").canonicalFile
        while (current != null && !File(current, "settings.gradle").exists() && !File(current, "settings.gradle.kts").exists()) {
            current = current.parentFile
        }
        val root = current ?: File(".")
        return File(root, "app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt")
    }

    @Test
    fun testZonesSettingsActivityFileExists() {
        val activityFile = resolveActivityFile()
        assertTrue("ZonesSettingsActivity.kt must exist", activityFile.exists())
    }

    @Test
    fun testHeaderUsesCompactIconButtonsInsteadOfTextButtons() {
        val activityFile = resolveActivityFile()
        val content = activityFile.readText()

        // Extract the header block: between "THE HEADER" and the tab bar / content Surface
        val headerStartIndex = content.indexOf("// 1. THE HEADER")
        assertTrue("Header comment must exist in ZonesSettingsActivity.kt", headerStartIndex != -1)
        val headerEndIndex = content.indexOf("if (currentType == ZoneType.PWR_BIKE", headerStartIndex)
        assertTrue("Header end marker must exist in ZonesSettingsActivity.kt", headerEndIndex != -1)

        val headerBlock = content.substring(headerStartIndex, headerEndIndex)

        // Must NOT use TextButton in the header block
        assertFalse(
            "Header block must not contain TextButton calls (causes vertical blowout in German per REQ-UI-378.1)",
            headerBlock.contains("TextButton")
        )

        // Must use IconButton
        assertTrue(
            "Header block must contain IconButton calls per REQ-UI-378.1",
            headerBlock.contains("IconButton")
        )

        // Must use Calculate and RestartAlt icons
        assertTrue(
            "Header block must use Icons.Default.Calculate per REQ-UI-378.1",
            headerBlock.contains("Icons.Default.Calculate")
        )
        assertTrue(
            "Header block must use Icons.Default.RestartAlt per REQ-UI-378.1",
            headerBlock.contains("Icons.Default.RestartAlt")
        )
    }

    @Test
    fun testHeaderTitleIsBoundedWithMaxLinesAndEllipsis() {
        val activityFile = resolveActivityFile()
        val content = activityFile.readText()
        val headerStartIndex = content.indexOf("// 1. THE HEADER")
        val headerEndIndex = content.indexOf("if (currentType == ZoneType.PWR_BIKE", headerStartIndex)
        val headerBlock = content.substring(headerStartIndex, headerEndIndex)

        assertTrue(
            "Title Text in header must declare maxLines = 1 per REQ-UI-378.2",
            headerBlock.contains("maxLines = 1")
        )
        assertTrue(
            "Title Text in header must declare overflow = TextOverflow.Ellipsis per REQ-UI-378.2",
            headerBlock.contains("overflow = TextOverflow.Ellipsis")
        )
        assertTrue(
            "Left title container in header must declare weight(1f, fill = false) per REQ-UI-378.2",
            headerBlock.contains("weight(1f, fill = false)")
        )
    }
}
