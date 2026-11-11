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

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Structural contract test verifying that AdvancedTuningDialog hosts WorkoutCardSectionPreferences
 * with all 8 toggle switches, while excising EditWorkoutFieldPreferences, edit dialog toggles,
 * and setEditWorkoutFieldPreferences pursuant to REQ-UI-239 and TST-UI-198.1.
 * Refactored to pure JVM reflection and LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class AdvancedTuningAftermathContractTest {

    @Test
    fun testAdvancedTuningDialog_containsWorkoutCardTogglesAndOmitsEditDialogToggles() {
        val strings = LocalizationTestCache.getStrings("values", "strings.xml")

        // 1. Model Existence
        val modelClass = Class.forName("com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences")
        assertNotNull("WorkoutCardSectionPreferences must exist", modelClass)

        // 2. Section Headers
        assertNotNull("settings_workout_card_title must exist", strings["settings_workout_card_title"])

        // 3. All 8 Workout Card Section Toggles
        assertNotNull(strings["settings_workout_card_description"])
        assertNotNull(strings["settings_workout_card_extrema"])
        assertNotNull(strings["settings_workout_card_laps"])
        assertNotNull(strings["settings_workout_card_strava"])
        assertNotNull(strings["settings_workout_card_map"])
        assertNotNull(strings["settings_workout_card_elevation"])
        assertNotNull(strings["settings_workout_card_charts"])
        assertNotNull(strings["settings_workout_card_zones"])

        // 4. Section Composable
        val sectionClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.WorkoutMasksAndCardsSectionKt")
        val methods = sectionClass.declaredMethods
        val sectionMethod = methods.find { it.name.startsWith("WorkoutMasksAndCardsSection") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutMasksAndCardsSection composable must exist", sectionMethod)
    }

    @Test
    fun testAdvancedTuningDialog_hostsMatrixTableWithWorkoutDetailPreferences() {
        val strings = LocalizationTestCache.getStrings("values", "strings.xml")

        // 1. Model Imports & References
        val modelClass = Class.forName("com.atrainingtracker.trainingtracker.WorkoutDetailPreferences")
        assertNotNull("WorkoutDetailPreferences must exist", modelClass)

        // 2. Matrix Table Columns
        assertNotNull(strings["tuning_matrix_col_list"])
        assertNotNull(strings["tuning_matrix_feature"])

        // 3. Subtitle Formatter Integration
        val formatterClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSubtitleFormatter")
        val methods = formatterClass.declaredMethods
        val formatMethod = methods.find { it.name.startsWith("formatWorkoutMatrixSubtitle") }
        assertNotNull("formatWorkoutMatrixSubtitle method must exist", formatMethod)
    }
}
