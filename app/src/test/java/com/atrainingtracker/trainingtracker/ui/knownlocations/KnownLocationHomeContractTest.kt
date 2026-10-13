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
 * Structural contract test verifying UI, Dialog, and ViewModel integration for Home-Base selection.
 *
 * Traceability:
 * - REQ-MAP-034: Designated Home-Base Selection for Return Navigation & Substring Disambiguation.
 * - REQ-MAP-036: Known Locations: Heim-Basis Redundant Badge Excision & Start-Count Home Resolution Alignment.
 * - TST-MAP-036.4, TST-MAP-038.3: UI & Dialog Contract Tests.
 */
class KnownLocationHomeContractTest {

    private fun loadFileSource(relativeSubpath: String): String {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/$relativeSubpath"),
            File("src/main/java/com/atrainingtracker/trainingtracker/$relativeSubpath"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/$relativeSubpath")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Source file not found for: $relativeSubpath")
        return file.readText()
    }

    @Test
    fun testKnownLocationsScreen_containsHomeTitleIconAndNoRedundantBadge() {
        val content = loadFileSource("ui/knownlocations/KnownLocationsScreen.kt")

        // Verifies Home icon is rendered for home location (REQ-MAP-034, REQ-MAP-036)
        assertTrue(
            "KnownLocationsScreen must render Icons.Default.Home next to title",
            content.contains("Icons.Default.Home")
        )
        assertTrue(
            "KnownLocationsScreen must render title Home icon with test tag",
            content.contains("location_home_icon_\${item.id}")
        )

        // Verifies redundant home badge is cleanly excised (REQ-MAP-036, ATT-2629)
        assertFalse(
            "KnownLocationsScreen must NOT render redundant home badge with test tag",
            content.contains("location_home_badge_\${item.id}")
        )

        // Verifies context menu contains set and remove home actions
        assertTrue(
            "KnownLocationsScreen must offer known_locations_set_home in dropdown",
            content.contains("R.string.known_locations_set_home")
        )
        assertTrue(
            "KnownLocationsScreen must offer known_locations_remove_home in dropdown",
            content.contains("R.string.known_locations_remove_home")
        )
    }

    @Test
    fun testEditKnownLocationDialog_containsHomeBaseToggle() {
        val content = loadFileSource("ui/knownlocations/EditKnownLocationDialog.kt")

        // Verifies toggle switch row exists for home base
        assertTrue(
            "EditKnownLocationDialog must reference known_locations_home_base",
            content.contains("R.string.known_locations_home_base")
        )
        assertTrue(
            "EditKnownLocationDialog must contain Switch for home base",
            content.contains("isHome") && content.contains("Switch(")
        )
    }

    @Test
    fun testKnownLocationsViewModel_exposesHomeMethods() {
        val content = loadFileSource("ui/knownlocations/KnownLocationsViewModel.kt")

        // Verifies ViewModel exposes setHomeLocation and clearHomeLocation
        assertTrue(
            "KnownLocationsViewModel must declare setHomeLocation",
            content.contains("fun setHomeLocation(id: Long)")
        )
        assertTrue(
            "KnownLocationsViewModel must declare clearHomeLocation",
            content.contains("fun clearHomeLocation()")
        )
    }
}
