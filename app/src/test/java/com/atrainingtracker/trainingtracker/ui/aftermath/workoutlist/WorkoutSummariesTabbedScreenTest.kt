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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural and contract tests verifying edge-to-edge system navigation bar
 * insets padding for post-workout snackbars and action controls (REQ-UI-350 / TST-UI-310 / ATT-3167).
 */
class WorkoutSummariesTabbedScreenTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val workoutSummariesTabbedScreenFile: File by lazy {
        File(
            projectRoot,
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedScreen.kt"
        )
    }

    private val workoutSummariesListFragmentFile: File by lazy {
        File(
            projectRoot,
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesListFragment.kt"
        )
    }

    private val routesScreenFile: File by lazy {
        File(
            projectRoot,
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreen.kt"
        )
    }

    @Test
    fun testWorkoutSummariesTabbedScreen_snackbarHost_appliesNavigationBarsPadding() {
        assertTrue("WorkoutSummariesTabbedScreen.kt must exist", workoutSummariesTabbedScreenFile.exists())
        val content = workoutSummariesTabbedScreenFile.readText()

        // 1. Must import navigationBarsPadding
        assertTrue(
            "WorkoutSummariesTabbedScreen.kt must import navigationBarsPadding",
            content.contains("import androidx.compose.foundation.layout.navigationBarsPadding")
        )

        // 2. SnackbarHost must configure modifier = Modifier.navigationBarsPadding()
        assertTrue(
            "WorkoutSummariesTabbedScreen.kt must configure Modifier.navigationBarsPadding() on SnackbarHost (REQ-UI-350)",
            content.contains("SnackbarHost(\n                hostState = snackbarHostState,\n                modifier = Modifier.navigationBarsPadding()") ||
            content.contains("SnackbarHost(") && content.contains("modifier = Modifier.navigationBarsPadding()")
        )

        // 3. Must preserve contentWindowInsets = WindowInsets(0.dp) for edge-to-edge drawing
        assertTrue(
            "WorkoutSummariesTabbedScreen.kt must preserve contentWindowInsets = WindowInsets(0.dp)",
            content.contains("contentWindowInsets = WindowInsets(0.dp)")
        )
    }

    @Test
    fun testWorkoutSummariesListFragment_snackbarHost_appliesNavigationBarsPadding() {
        assertTrue("WorkoutSummariesListFragment.kt must exist", workoutSummariesListFragmentFile.exists())
        val content = workoutSummariesListFragmentFile.readText()

        // 1. Must import navigationBarsPadding
        assertTrue(
            "WorkoutSummariesListFragment.kt must import navigationBarsPadding",
            content.contains("import androidx.compose.foundation.layout.navigationBarsPadding")
        )

        // 2. SnackbarHost must configure modifier = Modifier.navigationBarsPadding()
        assertTrue(
            "WorkoutSummariesListFragment.kt must configure Modifier.navigationBarsPadding() on SnackbarHost (REQ-UI-350)",
            content.contains("modifier = Modifier.navigationBarsPadding()")
        )

        // 3. Must preserve contentWindowInsets = WindowInsets(0.dp)
        assertTrue(
            "WorkoutSummariesListFragment.kt must preserve contentWindowInsets = WindowInsets(0.dp)",
            content.contains("contentWindowInsets = WindowInsets(0.dp)")
        )
    }

    @Test
    fun testRoutesScreen_snackbarHost_appliesNavigationBarsPadding() {
        assertTrue("RoutesScreen.kt must exist", routesScreenFile.exists())
        val content = routesScreenFile.readText()

        // 1. Must import navigationBarsPadding
        assertTrue(
            "RoutesScreen.kt must import navigationBarsPadding",
            content.contains("import androidx.compose.foundation.layout.navigationBarsPadding")
        )

        // 2. SnackbarHost must configure modifier = Modifier.navigationBarsPadding()
        assertTrue(
            "RoutesScreen.kt must configure Modifier.navigationBarsPadding() on SnackbarHost (REQ-UI-350)",
            content.contains("modifier = Modifier.navigationBarsPadding()")
        )

        // 3. Must preserve contentWindowInsets = WindowInsets(0.dp)
        assertTrue(
            "RoutesScreen.kt must preserve contentWindowInsets = WindowInsets(0.dp)",
            content.contains("contentWindowInsets = WindowInsets(0.dp)")
        )
    }
}
