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

package com.atrainingtracker.trainingtracker.ui.knownlocations

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract test verifying that [KnownLocationCard] isolates the altitude metric
 * and places Starts and Strecken badges on their own dedicated row with compact sizing (REQ-UI-195, TST-UI-149.1).
 */
class KnownLocationCardLayoutTest {

    private fun loadScreenSource(): String {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("KnownLocationsScreen.kt not found in candidates: $candidates")
        return file.readText()
    }

    @Test
    fun testAltitudeDecoupledFromBadgesRow() {
        val content = loadScreenSource()

        // Verify altitude metric comment / structure exists
        assertTrue(
            "KnownLocationCard must contain dedicated altitude metric section",
            content.contains("// Prominent Altitude Metric")
        )

        // Verify dedicated badges row comment exists
        assertTrue(
            "KnownLocationCard must contain dedicated badges row",
            content.contains("// Dedicated Badges Row (Starts and Routes, REQ-UI-195)")
        )

        // Verify altitude metric appears BEFORE the dedicated badges row
        val altitudeIndex = content.indexOf("// Prominent Altitude Metric")
        val badgesIndex = content.indexOf("// Dedicated Badges Row (Starts and Routes, REQ-UI-195)")
        assertTrue(
            "Altitude metric must appear before dedicated badges row",
            altitudeIndex in 0 until badgesIndex
        )
    }

    @Test
    fun testCompactBadgeDimensionsAndStyling() {
        val content = loadScreenSource()

        // Badges must use RoundedCornerShape(8.dp) rather than 12.dp
        assertTrue(
            "Badges must use compact RoundedCornerShape(8.dp)",
            content.contains("shape = RoundedCornerShape(8.dp)")
        )

        // Badges must use compact internal padding (8.dp horizontal, 4.dp vertical)
        assertTrue(
            "Badges must use compact padding (horizontal = 8.dp, vertical = 4.dp)",
            content.contains("padding(horizontal = 8.dp, vertical = 4.dp)")
        )

        // Badges must NOT enforce defaultMinSize(minHeight = 48.dp) on their visual surface container
        assertFalse(
            "Badges must not force 48dp minimum container height",
            content.contains("defaultMinSize(minHeight = 48.dp)")
        )

        // Both test tags must be preserved
        assertTrue(
            "Starts badge test tag must be preserved",
            content.contains("location_starts_badge_\${item.id}")
        )
        assertTrue(
            "Routes badge test tag must be preserved",
            content.contains("location_routes_badge_\${item.id}")
        )
    }
}
