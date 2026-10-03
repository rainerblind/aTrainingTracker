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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract and invariant unit tests verifying that [WorkoutFilterBottomSheet]
 * has eliminated Section 9 Favorite Tracks (Lieblingsstrecken) and its cluster chip picker,
 * while preserving drill-down cluster filtering on criteria application (REQ-UI-209, TST-UI-163).
 */
class WorkoutFilterBottomSheetClusterRemovalTest {

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
    fun testWorkoutFilterBottomSheetDoesNotContainClusterSection() {
        val content = loadSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheet.kt")

        // 1. Verify Section 9 (Favorite Tracks / Route Clusters) comment is eliminated
        assertFalse(
            "WorkoutFilterBottomSheet must not contain Section 9 Favorite Tracks",
            content.contains("// 9. Favorite Tracks")
        )

        // 2. Verify local cluster state variables are eliminated
        assertFalse(
            "WorkoutFilterBottomSheet must not declare localClusterId state",
            content.contains("localClusterId")
        )
        assertFalse(
            "WorkoutFilterBottomSheet must not declare localClusterName state",
            content.contains("localClusterName")
        )

        // 3. Verify availableClusters parameter is eliminated from WorkoutFilterBottomSheet signature
        assertFalse(
            "WorkoutFilterBottomSheet must not declare availableClusters parameter",
            content.contains("availableClusters:")
        )

        // 4. Verify WorkoutCluster database model is not imported
        assertFalse(
            "WorkoutFilterBottomSheet must not import WorkoutCluster",
            content.contains("import com.atrainingtracker.trainingtracker.database.WorkoutCluster")
        )

        // 5. Verify onApply preserves criteria.clusterId and criteria.clusterName
        assertTrue(
            "WorkoutFilterBottomSheet onApply must preserve criteria.clusterId",
            content.contains("clusterId = criteria.clusterId")
        )
        assertTrue(
            "WorkoutFilterBottomSheet onApply must preserve criteria.clusterName",
            content.contains("clusterName = criteria.clusterName")
        )

        // 6. Verify Section 8 (Start at / Favorite Locations) is present
        assertTrue(
            "WorkoutFilterBottomSheet must retain Section 8 Favorite Locations",
            content.contains("// 8. Favorite Locations")
        )
    }

    @Test
    fun testWorkoutTabsScreenDoesNotPassAvailableClustersToBottomSheet() {
        val content = loadSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutTabsScreen.kt")

        val bottomSheetIndex = content.indexOf("WorkoutFilterBottomSheet(")
        assertTrue("WorkoutTabsScreen must contain WorkoutFilterBottomSheet call", bottomSheetIndex >= 0)

        val callSnippet = content.substring(bottomSheetIndex, content.indexOf(")", bottomSheetIndex))
        assertFalse(
            "WorkoutTabsScreen must not pass availableClusters to WorkoutFilterBottomSheet",
            callSnippet.contains("availableClusters")
        )
    }

    @Test
    fun testWorkoutFilterCriteriaClusterPreservationInCriteriaCopy() {
        val original = WorkoutFilterCriteria(
            query = "Morning",
            clusterId = 42L,
            clusterName = "River Loop",
            year = 2026
        )

        // Simulating the onApply copy behavior: user modifies year, query, etc.
        val updated = original.copy(
            query = "Evening",
            year = 2025,
            clusterId = original.clusterId,
            clusterName = original.clusterName
        )

        assertEquals("Evening", updated.query)
        assertEquals(2025, updated.year)
        assertEquals(42L, updated.clusterId)
        assertEquals("River Loop", updated.clusterName)
    }
}
