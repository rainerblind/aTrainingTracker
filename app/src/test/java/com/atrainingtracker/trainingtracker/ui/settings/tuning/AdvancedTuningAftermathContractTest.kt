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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract test verifying that AdvancedTuningDialog hosts WorkoutCardSectionPreferences
 * with all 8 toggle switches, while excising EditWorkoutFieldPreferences, edit dialog toggles,
 * and setEditWorkoutFieldPreferences pursuant to REQ-UI-239 and TST-UI-198.1.
 */
class AdvancedTuningAftermathContractTest {

    private fun findTuningSourceContent(): String {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning")
        )
        val dir = candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: return ""
        val dialogFile = File(dir, "AdvancedTuningDialog.kt")
        val matrixFile = File(dir, "categories/WorkoutMasksAndCardsSection.kt")
        return (if (dialogFile.exists()) dialogFile.readText() else "") + "\n" +
                (if (matrixFile.exists()) matrixFile.readText() else "")
    }

    @Test
    fun testAdvancedTuningDialog_containsWorkoutCardTogglesAndOmitsEditDialogToggles() {
        val content = findTuningSourceContent()

        // 1. Model Imports & References
        assertTrue(
            "AdvancedTuningDialog must reference WorkoutCardSectionPreferences",
            content.contains("WorkoutCardSectionPreferences")
        )
        assertFalse(
            "AdvancedTuningDialog must NOT reference EditWorkoutFieldPreferences",
            content.contains("EditWorkoutFieldPreferences")
        )

        // 2. Section Headers
        assertTrue(
            "AdvancedTuningDialog must reference settings_workout_card_title",
            content.contains("settings_workout_card_title")
        )
        assertFalse(
            "AdvancedTuningDialog must NOT reference settings_edit_workout_title",
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

        // 4. All 7 Edit Workout Field Toggles excised
        assertFalse(content.contains("settings_edit_workout_description"))
        assertFalse(content.contains("settings_edit_workout_cluster"))
        assertFalse(content.contains("settings_edit_workout_commute_trainer"))
        assertFalse(content.contains("settings_edit_workout_race"))
        assertFalse(content.contains("settings_edit_workout_strava"))
        assertFalse(content.contains("settings_edit_workout_goal"))
        assertFalse(content.contains("settings_edit_workout_method"))

        // 5. DataStore Persistence on Save & Factory Reset
        assertTrue(
            "AdvancedTuningDialog must invoke setWorkoutCardPreferences",
            content.contains("setWorkoutCardPreferences")
        )
        assertFalse(
            "AdvancedTuningDialog must NOT invoke setEditWorkoutFieldPreferences",
            content.contains("setEditWorkoutFieldPreferences")
        )
    }

    @Test
    fun testAdvancedTuningDialog_hostsMatrixTableWithWorkoutDetailPreferences() {
        val content = findTuningSourceContent()

        // 1. Model Imports & References
        assertTrue(
            "AdvancedTuningDialog must reference WorkoutDetailPreferences (REQ-UI-240)",
            content.contains("WorkoutDetailPreferences")
        )
        assertTrue(
            "AdvancedTuningDialog must collect workoutDetailPreferencesFlow (REQ-UI-240)",
            content.contains("workoutDetailPreferencesFlow.collectAsState(initial = null)")
        )

        // 2. Matrix Table Columns
        assertTrue(
            "AdvancedTuningDialog must reference tuning_matrix_col_list",
            content.contains("tuning_matrix_col_list")
        )
        assertTrue(
            "AdvancedTuningDialog must reference tuning_matrix_col_details",
            content.contains("tuning_matrix_col_details")
        )
        assertTrue(
            "AdvancedTuningDialog must reference tuning_matrix_feature",
            content.contains("tuning_matrix_feature")
        )

        // 3. Persistence on Save
        assertTrue(
            "AdvancedTuningDialog must invoke setWorkoutDetailPreferences on save (REQ-UI-240)",
            content.contains("setWorkoutDetailPreferences")
        )

        // 4. Subtitle Formatter Integration
        assertTrue(
            "AdvancedTuningDialog must invoke formatWorkoutMatrixSubtitle (REQ-UI-240)",
            content.contains("formatWorkoutMatrixSubtitle")
        )
    }
}
