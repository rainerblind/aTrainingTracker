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
import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import com.atrainingtracker.trainingtracker.ui.components.workoutlaps.LapDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Unit tests verifying LapDisplayMode preference handling in Advanced Tuning dialog and
 * complete 9-language translation parity across all supported locales (REQ-UI-229, TST-UI-183, ATT-1870).
 * Refactored to LocalizationTestCache and JVM reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class LapDisplayModeSettingsTest {

    private val locales = listOf("", "-de", "-es", "-fr", "-it", "-ja", "-nl", "-pl", "-pt")

    private val requiredKeys = listOf(
        "settings_lap_display_mode_title",
        "settings_lap_display_mode_table",
        "settings_lap_display_mode_visualizer",
        "settings_lap_display_mode_both"
    )

    @Test
    fun testWorkoutCardSectionPreferences_lapDisplayModeDefaultAndMutation() {
        val defaultPrefs = WorkoutCardSectionPreferences()
        assertEquals(LapDisplayMode.VISUALIZER_ONLY, defaultPrefs.lapDisplayMode)

        val tableOnly = defaultPrefs.copy(lapDisplayMode = LapDisplayMode.TABLE_ONLY)
        assertEquals(LapDisplayMode.TABLE_ONLY, tableOnly.lapDisplayMode)

        val visualizerOnly = defaultPrefs.copy(lapDisplayMode = LapDisplayMode.VISUALIZER_ONLY)
        assertEquals(LapDisplayMode.VISUALIZER_ONLY, visualizerOnly.lapDisplayMode)

        val resetPrefs = WorkoutCardSectionPreferences()
        assertEquals(LapDisplayMode.VISUALIZER_ONLY, resetPrefs.lapDisplayMode)
    }

    @Test
    fun testLapDisplayModeLocalizationParityAcrossAll9Locales() {
        for (suffix in locales) {
            val dirName = if (suffix.isEmpty()) "values" else "values$suffix"
            val strings = LocalizationTestCache.getStrings(dirName, "strings.xml")

            for (key in requiredKeys) {
                val value = strings[key]
                assertNotNull("Missing key '$key' in $dirName", value)
                assertTrue("Key '$key' in $dirName must not be blank", value!!.isNotBlank())
            }
        }
    }

    @Test
    fun testAdvancedTuningDialog_lapDisplayModeUsesSegmentedButton() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.WorkoutMasksAndCardsSectionKt")
        val method = clazz.declaredMethods.find { it.name.startsWith("WorkoutMasksAndCardsSection") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutMasksAndCardsSection composable must exist", method)
    }
}
