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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract test verifying that [WorkoutFilterBottomSheet] and [ClusterFilterBottomSheet]
 * reference [R.string.filter_section_start_at] for the location filter section heading instead of
 * [R.string.known_locations_title], while [KnownLocationsScreen] preserves [R.string.known_locations_title]
 * for its top app bar (REQ-UI-208, TST-UI-162).
 */
class FilterSectionHeadingLayoutTest {

    private fun loadSourceFile(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Source file not found: $relativePath across candidates $candidates")
        return file.readText()
    }

    @Test
    fun testWorkoutFilterBottomSheetUsesStartAtHeading() {
        val content = loadSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheet.kt")

        // Must reference filter_section_start_at
        assertTrue(
            "WorkoutFilterBottomSheet must reference R.string.filter_section_start_at for location filter heading",
            content.contains("R.string.filter_section_start_at")
        )

        // Section 8 must not reference R.string.known_locations_title
        val section8Index = content.indexOf("// 8. Favorite Locations")
        assertTrue("Section 8 comment must be present", section8Index >= 0)

        val nextSectionIndex = content.indexOf("// 9. Favorite Tracks")
        val section8Snippet = if (nextSectionIndex > section8Index) {
            content.substring(section8Index, nextSectionIndex)
        } else {
            content.substring(section8Index)
        }

        assertFalse(
            "WorkoutFilterBottomSheet section 8 must not reference R.string.known_locations_title",
            section8Snippet.contains("R.string.known_locations_title")
        )
    }

    @Test
    fun testClusterFilterBottomSheetUsesStartAtHeading() {
        val content = loadSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterBottomSheet.kt")

        // Must reference filter_section_start_at
        assertTrue(
            "ClusterFilterBottomSheet must reference R.string.filter_section_start_at for location filter heading",
            content.contains("R.string.filter_section_start_at")
        )

        // Must not reference R.string.known_locations_title anywhere in ClusterFilterBottomSheet
        assertFalse(
            "ClusterFilterBottomSheet must not reference R.string.known_locations_title",
            content.contains("R.string.known_locations_title")
        )
    }

    @Test
    fun testKnownLocationsScreenPreservesKnownLocationsTitle() {
        val content = loadSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt")

        // Must strictly preserve R.string.known_locations_title for top app bar
        assertTrue(
            "KnownLocationsScreen must preserve R.string.known_locations_title for top app bar",
            content.contains("stringResource(R.string.known_locations_title)")
        )
    }
}
