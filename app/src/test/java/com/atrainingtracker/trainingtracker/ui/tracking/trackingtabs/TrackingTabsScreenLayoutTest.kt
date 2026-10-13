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

package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural and contract tests verifying low sensor battery alert SnackbarHost,
 * edge-to-edge system navigation bar insets compliance, and LapButton clearance
 * in TrackingTabsScreen (REQ-UI-356 / TST-UI-316 / ATT-3313).
 */
class TrackingTabsScreenLayoutTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val trackingTabsScreenFile: File by lazy {
        File(
            projectRoot,
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt"
        )
    }

    @Test
    fun testTrackingTabsScreen_snackbarHost_appliesNavigationBarsPaddingAndLapButtonClearance() {
        assertTrue("TrackingTabsScreen.kt must exist", trackingTabsScreenFile.exists())
        val content = trackingTabsScreenFile.readText()

        // 1. Must import SnackbarHost and related Material3 snackbar components
        assertTrue(
            "TrackingTabsScreen.kt must import SnackbarHost",
            content.contains("import androidx.compose.material3.SnackbarHost")
        )
        assertTrue(
            "TrackingTabsScreen.kt must import SnackbarHostState",
            content.contains("import androidx.compose.material3.SnackbarHostState")
        )
        assertTrue(
            "TrackingTabsScreen.kt must import WorkoutNavigationEvents",
            content.contains("import com.atrainingtracker.trainingtracker.ui.WorkoutNavigationEvents")
        )

        // 2. Must observe WorkoutNavigationEvents.lowBatteryAlert
        assertTrue(
            "TrackingTabsScreen.kt must collect WorkoutNavigationEvents.lowBatteryAlert",
            content.contains("WorkoutNavigationEvents.lowBatteryAlert.collect")
        )

        // 3. Must consume alert after handling
        assertTrue(
            "TrackingTabsScreen.kt must call WorkoutNavigationEvents.consumeLowBatteryAlert()",
            content.contains("WorkoutNavigationEvents.consumeLowBatteryAlert()")
        )

        // 4. SnackbarHost must configure Modifier.navigationBarsPadding() (REQ-UI-356 / Rule 23)
        assertTrue(
            "TrackingTabsScreen.kt must configure Modifier.navigationBarsPadding() on SnackbarHost",
            content.contains("SnackbarHost(") && content.contains(".navigationBarsPadding()")
        )

        // 5. Must provide clearance above LapButton when visible
        assertTrue(
            "TrackingTabsScreen.kt must apply bottom padding conditional on shouldShowLapButton",
            content.contains("padding(bottom = if (shouldShowLapButton) 64.dp else 8.dp)")
        )
    }
}
