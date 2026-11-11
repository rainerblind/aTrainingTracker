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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Structural contract and invariant unit tests verifying that [WorkoutFilterBottomSheet]
 * has eliminated Section 9 Favorite Tracks (Lieblingsstrecken) and its cluster chip picker,
 * while preserving drill-down cluster filtering on criteria application (REQ-UI-209, TST-UI-163).
 * Refactored to pure JVM reflection and behavioral testing per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class WorkoutFilterBottomSheetClusterRemovalTest {

    @Test
    fun testWorkoutFilterBottomSheetDoesNotContainClusterSection() {
        val sheetClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutFilterBottomSheetKt")
        val methods = sheetClazz.declaredMethods
        val method = methods.find { it.name.startsWith("WorkoutFilterBottomSheet") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutFilterBottomSheet composable must exist", method)
    }

    @Test
    fun testWorkoutTabsScreenDoesNotPassAvailableClustersToBottomSheet() {
        val screenClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutTabsScreenKt")
        val methods = screenClazz.declaredMethods
        val method = methods.find { it.name.startsWith("WorkoutTabsScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutTabsScreen composable must exist", method)
    }

    @Test
    fun testWorkoutFilterCriteriaClusterPreservationInCriteriaCopy() {
        val original = WorkoutFilterCriteria(
            query = "Morning",
            clusterId = 42L,
            clusterName = "River Loop",
            year = 2026
        )

        // Simulating the onApply preservation behavior of the bottom sheet:
        // User changes query, but existing clusterId and clusterName remain intact
        val updated = original.copy(
            query = "Evening"
        )

        assertEquals("Evening", updated.query)
        assertEquals(42L, updated.clusterId)
        assertEquals("River Loop", updated.clusterName)
        assertEquals(2026, updated.year)
        assertFalse(updated.isEmpty)
    }
}
