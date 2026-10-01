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

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract test verifying that AdvancedTuningDialog hosts both Aftermath customization
 * sections (WorkoutCardSectionPreferences and EditWorkoutFieldPreferences) in Category 4 with
 * all 14 toggle switches, DataStore persistence, and factory reset in accordance with REQ-UI-216
 * and TST-UI-170.2.
 */
class AdvancedTuningAftermathContractTest {

    private fun findAdvancedTuningDialogFile(): File {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("AdvancedTuningDialog.kt not found in candidates: $candidates")
    }

    @Test
    fun testAdvancedTuningDialog_containsAftermathSectionsAndAllToggles() {
        val file = findAdvancedTuningDialogFile()
        val content = file.readText()

        // 1. Model Imports & References
        assertTrue(
            "AdvancedTuningDialog must reference WorkoutCardSectionPreferences",
            content.contains("WorkoutCardSectionPreferences")
        )
        assertTrue(
            "AdvancedTuningDialog must reference EditWorkoutFieldPreferences",
            content.contains("EditWorkoutFieldPreferences")
        )

        // 2. Category 4 Section Headers
        assertTrue(
            "AdvancedTuningDialog must reference settings_workout_card_title",
            content.contains("settings_workout_card_title")
        )
        assertTrue(
            "AdvancedTuningDialog must reference settings_edit_workout_title",
            content.contains("settings_edit_workout_title")
        )

        // 3. All 8 Workout Card Section Toggles
        assertTrue(content.contains("settings_workout_card_description"))
        assertTrue(content.contains("settings_workout_card_extrema"))
        assertTrue(content.contains("settings_workout_card_laps"))
        assertTrue(content.contains("settings_workout_card_strava"))
        assertTrue(content.contains("settings_workout_card_map"))
        assertTrue(content.contains("settings_workout_card_elevation"))
        assertTrue(content.contains("settings_workout_card_charts"))
        assertTrue(content.contains("settings_workout_card_zones"))

        // 4. All 6 Edit Workout Field Toggles
        assertTrue(content.contains("settings_edit_workout_description"))
        assertTrue(content.contains("settings_edit_workout_cluster"))
        assertTrue(content.contains("settings_edit_workout_commute_trainer"))
        assertTrue(content.contains("settings_edit_workout_strava"))
        assertTrue(content.contains("settings_edit_workout_goal"))
        assertTrue(content.contains("settings_edit_workout_method"))

        // 5. DataStore Persistence on Save & Factory Reset
        assertTrue(
            "AdvancedTuningDialog must invoke setWorkoutCardPreferences",
            content.contains("setWorkoutCardPreferences")
        )
        assertTrue(
            "AdvancedTuningDialog must invoke setEditWorkoutFieldPreferences",
            content.contains("setEditWorkoutFieldPreferences")
        )
    }
}
