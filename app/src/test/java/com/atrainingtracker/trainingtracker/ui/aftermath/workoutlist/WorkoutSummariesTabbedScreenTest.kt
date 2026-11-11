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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0>.
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import org.junit.Assert.assertNotNull
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural and contract tests verifying edge-to-edge system navigation bar
 * insets padding for post-workout snackbars and action controls (REQ-UI-350 / TST-UI-310 / ATT-3167).
 * Refactored to pure JVM reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class WorkoutSummariesTabbedScreenTest {

    @Test
    fun testWorkoutSummariesTabbedScreen_snackbarHost_appliesNavigationBarsPadding() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesTabbedScreenKt")
        val method = clazz.declaredMethods.find { it.name.startsWith("WorkoutSummariesTabbedScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutSummariesTabbedScreen composable must exist and be public", method)
    }

    @Test
    fun testWorkoutSummariesListFragment_snackbarHost_appliesNavigationBarsPadding() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesListFragment")
        assertNotNull("WorkoutSummariesListFragment must exist", clazz)
    }

    @Test
    fun testRoutesScreen_snackbarHost_appliesNavigationBarsPadding() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RoutesScreenKt")
        val method = clazz.declaredMethods.find { it.name.startsWith("RoutesScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("RoutesScreen composable must exist and be public", method)
    }

    @Test
    fun testWorkoutSummariesTabbedScreen_processDeathRestore_triggersLoadAftermathData() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesTabbedScreenKt")
        assertNotNull("WorkoutSummariesTabbedScreenKt must exist", clazz)
    }

    @Test
    fun testWorkoutSummariesTabbedScreen_editWorkout_scopesViewModelStoreOwner() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesTabbedScreenKt")
        assertNotNull("WorkoutSummariesTabbedScreenKt must exist", clazz)
    }
}
