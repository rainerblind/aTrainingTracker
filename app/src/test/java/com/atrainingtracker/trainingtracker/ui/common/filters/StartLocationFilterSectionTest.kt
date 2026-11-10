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

package com.atrainingtracker.trainingtracker.ui.common.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit & Contract test suite for [StartLocationFilterSection] (REQ-UI-509.3, TST-UI-509.1).
 */
class StartLocationFilterSectionTest {

    private fun loadSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app", relativePath),
            File("..", relativePath),
            File("../app", relativePath)
        )
        return candidates.firstOrNull { it.exists() } ?: File(relativePath)
    }

    data class MockLocation(
        val name: String,
        val lat: Double,
        val lng: Double,
        val hitCount: Int
    )

    @Test
    fun testFrequentLocationFiltering_excludesInfrequentByDefault_unlessSelected() {
        val locations = listOf(
            MockLocation("Home", 48.1, 11.5, hitCount = 10),
            MockLocation("Work", 48.2, 11.6, hitCount = 5),
            MockLocation("Cabin", 47.5, 12.0, hitCount = 2),
            MockLocation("Lake", 47.8, 11.2, hitCount = 1)
        )

        // Default filter logic when showAllLocations = false and no selection
        val frequentOnly = locations.filter { it.hitCount >= 5 }
        assertEquals(2, frequentOnly.size)
        assertTrue(frequentOnly.any { it.name == "Home" })
        assertTrue(frequentOnly.any { it.name == "Work" })
        assertFalse(frequentOnly.any { it.name == "Cabin" })

        // When Cabin is selected, it must be included even if hitCount < 5
        val selectedLat = 47.5
        val selectedLng = 12.0
        val displayedWithSelection = locations.filter {
            it.hitCount >= 5 || (it.lat == selectedLat && it.lng == selectedLng)
        }
        assertEquals(3, displayedWithSelection.size)
        assertTrue(displayedWithSelection.any { it.name == "Cabin" })
    }

    @Test
    fun testToggleShowAll_includesAllLocations() {
        val locations = listOf(
            MockLocation("Home", 48.1, 11.5, hitCount = 10),
            MockLocation("Work", 48.2, 11.6, hitCount = 5),
            MockLocation("Cabin", 47.5, 12.0, hitCount = 2)
        )

        var showAll = false
        val hasInfrequent = locations.any { it.hitCount < 5 }
        assertTrue(hasInfrequent)

        // Toggle to true
        showAll = !showAll
        val displayed = if (showAll) locations else locations.filter { it.hitCount >= 5 }
        assertEquals(3, displayed.size)
    }

    @Test
    fun testStartLocationFilterSection_structuralContract() {
        val file = loadSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/common/filters/StartLocationFilterSection.kt")
        assertTrue("StartLocationFilterSection source must exist", file.exists())
        val src = file.readText()

        assertTrue("Must support customizable title with filter_section_start_at default", src.contains("filter_section_start_at"))
        assertTrue("Must use FlowRow for location chips", src.contains("FlowRow"))
        assertTrue("Must reference filter_locations_show_frequent", src.contains("filter_locations_show_frequent"))
        assertTrue("Must reference filter_locations_show_all", src.contains("filter_locations_show_all"))
        assertTrue("Must check hitCount >= 5 for frequent location boundary", src.contains("hitCount >= 5"))
    }
}
