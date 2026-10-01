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

package com.atrainingtracker.trainingtracker.ui.settings.display

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract test verifying that Aftermath post-workout customization toggles
 * (WorkoutCardSectionPreferences and EditWorkoutFieldPreferences) have been cleanly removed
 * from DisplaySettingsDialog.kt in accordance with REQ-UI-216 and TST-UI-170.1.
 */
class DisplaySettingsCleanupTest {

    private fun findDisplaySettingsDialogFile(): File {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialog.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialog.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialog.kt")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("DisplaySettingsDialog.kt not found in candidates: $candidates")
    }

    @Test
    fun testDisplaySettingsDialog_doesNotContainAftermathTogglesOrPreferenceModels() {
        val file = findDisplaySettingsDialogFile()
        val content = file.readText()

        // Verify Aftermath preferences models are NOT referenced or imported
        assertFalse(
            "DisplaySettingsDialog must not reference WorkoutCardSectionPreferences",
            content.contains("WorkoutCardSectionPreferences")
        )
        assertFalse(
            "DisplaySettingsDialog must not reference EditWorkoutFieldPreferences",
            content.contains("EditWorkoutFieldPreferences")
        )

        // Verify Aftermath section titles are NOT referenced
        assertFalse(
            "DisplaySettingsDialog must not reference settings_workout_card_title",
            content.contains("settings_workout_card_title")
        )
        assertFalse(
            "DisplaySettingsDialog must not reference settings_edit_workout_title",
            content.contains("settings_edit_workout_title")
        )

        // Verify individual toggle keys are NOT referenced
        assertFalse(content.contains("settings_workout_card_description"))
        assertFalse(content.contains("settings_workout_card_extrema"))
        assertFalse(content.contains("settings_workout_card_laps"))
        assertFalse(content.contains("settings_workout_card_strava"))
        assertFalse(content.contains("settings_workout_card_map"))
        assertFalse(content.contains("settings_workout_card_elevation"))
        assertFalse(content.contains("settings_workout_card_charts"))
        assertFalse(content.contains("settings_workout_card_zones"))

        assertFalse(content.contains("settings_edit_workout_description"))
        assertFalse(content.contains("settings_edit_workout_cluster"))
        assertFalse(content.contains("settings_edit_workout_commute_trainer"))
        assertFalse(content.contains("settings_edit_workout_strava"))
        assertFalse(content.contains("settings_edit_workout_goal"))
        assertFalse(content.contains("settings_edit_workout_method"))

        // Verify core display settings and tuning navigation remain intact
        assertTrue(
            "DisplaySettingsDialog must retain forcePortrait toggle",
            content.contains("forcePortrait")
        )
        assertTrue(
            "DisplaySettingsDialog must retain keepScreenOn toggle",
            content.contains("keepScreenOn")
        )
        assertTrue(
            "DisplaySettingsDialog must retain noUnlocking toggle",
            content.contains("noUnlocking")
        )
        assertTrue(
            "DisplaySettingsDialog must retain advanced_tuning_title navigation button",
            content.contains("advanced_tuning_title")
        )
    }
}
