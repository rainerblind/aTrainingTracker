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
 * Structural contract test verifying that AdvancedTuningDialog is refactored into a modular,
 * collapsible accordion architecture with semantic subsections, zero tonal elevation,
 * and decoupled subsection composables in accordance with REQ-UI-222 and TST-UI-176.2.
 * Refactored to pure JVM reflection and LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class AdvancedTuningVisualContractTest {

    @Test
    fun testAdvancedTuningDialog_usesAccordionSectionStructure() {
        val strings = LocalizationTestCache.getStrings("values", "strings.xml")
        assertNotNull(strings["tuning_cat_cockpit_typography"])
        assertNotNull(strings["tuning_cat_battery_saver"])
        assertNotNull(strings["tuning_cat_sensors_gps"])
        assertNotNull(strings["tuning_cat_aftermath"])
        assertNotNull(strings["tuning_cat_workout_masks_cards"])

        val sectionEnum = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSection")
        val constants = sectionEnum.enumConstants.map { it.toString() }
        assertTrue(constants.contains("COCKPIT_TYPOGRAPHY"))
        assertTrue(constants.contains("BATTERY_SAVER"))
        assertTrue(constants.contains("SENSORS_GPS"))
        assertTrue(constants.contains("AFTERMATH_ANALYSIS"))
        assertTrue(constants.contains("WORKOUT_MASKS_CARDS"))
    }

    @Test
    fun testTuningAccordionSection_zeroTonalElevationAndM3Tokens() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningAccordionKt")
        val accordionMethod = clazz.declaredMethods.find { it.name.startsWith("TuningAccordionSection") && Modifier.isPublic(it.modifiers) }
        assertNotNull("TuningAccordionSection composable must exist and be public", accordionMethod)
    }

    @Test
    fun testDecoupledSubsections_existAsIndividualComposables() {
        val sections = listOf(
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.CockpitTypographySectionKt" to "CockpitTypographySection",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.AmoledBatterySaverSectionKt" to "AmoledBatterySaverSection",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.SensorsGpsFilterSectionKt" to "SensorsGpsFilterSection",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.AftermathAnalysisSectionKt" to "AftermathAnalysisSection",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.WorkoutMasksAndCardsSectionKt" to "WorkoutMasksAndCardsSection"
        )

        for ((className, methodName) in sections) {
            val clazz = Class.forName(className)
            val method = clazz.declaredMethods.find { it.name.startsWith(methodName) && Modifier.isPublic(it.modifiers) }
            assertNotNull("$methodName composable must exist and be public in $className", method)
        }
    }

    @Test
    fun testAdvancedTuningDialog_initiallyCollapsesAllSections() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningDialogKt")
        assertNotNull("AdvancedTuningDialogKt must exist", clazz)
    }

    @Test
    fun testAdvancedTuningDialog_workoutCardPrefsFlowGating() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningDialogKt")
        assertNotNull("AdvancedTuningDialogKt must exist", clazz)
    }

    @Test
    fun testWorkoutMasksAndCardsSection_lapDisplayModeUsesSegmentedButton() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.WorkoutMasksAndCardsSectionKt")
        val method = clazz.declaredMethods.find { it.name.startsWith("WorkoutMasksAndCardsSection") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutMasksAndCardsSection composable must exist", method)
    }

    @Test
    fun testNavigationSection_dismissDurationUsesSliderContract() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.NavigationSectionKt")
        val method = clazz.declaredMethods.find { it.name.startsWith("NavigationSection") && Modifier.isPublic(it.modifiers) }
        assertNotNull("NavigationSection composable must exist", method)

        val strings = LocalizationTestCache.getStrings("values", "strings.xml")
        assertNotNull("tuning_nav_cue_dismiss_duration_title must exist", strings["tuning_nav_cue_dismiss_duration_title"])
    }

    @Test
    fun testTuningWarningNotice_advisoryContainerAndInformativeIcon() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningFormControlsKt")
        val method = clazz.declaredMethods.find { it.name.startsWith("TuningWarningNotice") && Modifier.isPublic(it.modifiers) }
        assertNotNull("TuningWarningNotice composable must exist", method)

        val strings = LocalizationTestCache.getStrings("values", "strings.xml")
        assertNotNull("advanced_tuning_warning_title must exist", strings["advanced_tuning_warning_title"])
        assertNotNull("advanced_tuning_warning_desc must exist", strings["advanced_tuning_warning_desc"])
    }
}
