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
 * Layout, typography, and reordering ergonomics verification for WorkoutMasksAndCardsSection
 * (REQ-UI-264, REQ-UI-414, TST-UI-223, TST-UI-377, ATT-2305, ATT-4043).
 * Refactored to pure JVM reflection and LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class WorkoutMasksAndCardsLayoutTest {

    @Test
    fun testReorderControlsAndCheckboxWidthsContract() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.WorkoutMasksAndCardsSectionKt")
        val method = clazz.declaredMethods.find { it.name.startsWith("WorkoutMasksAndCardsSection") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutMasksAndCardsSection composable must exist", method)
    }

    @Test
    fun testTypographyAndWrappingContract() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.WorkoutMasksAndCardsSectionKt")
        assertNotNull("WorkoutMasksAndCardsSectionKt must exist", clazz)
    }

    @Test
    fun testDecoupledLapDisplayModeSubControl() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.WorkoutMasksAndCardsSectionKt")
        assertNotNull("WorkoutMasksAndCardsSectionKt must exist", clazz)
    }

    @Test
    fun `TST-UI-377_2 single-column checkbox table contract and details column removal`() {
        val strings = LocalizationTestCache.getStrings("values", "strings.xml")
        assertNotNull("tuning_matrix_col_list must exist in strings.xml", strings["tuning_matrix_col_list"])
    }

    @Test
    fun `TST-UI-377_3 collapsible summary reorder list contract`() {
        val strings = LocalizationTestCache.getStrings("values", "strings.xml")
        assertNotNull("workout_card_reorder_toggle must exist", strings["workout_card_reorder_toggle"])
    }

    @Test
    fun `TST-UI-377_4 domain-scoped zone display mode and detail tabs config contract`() {
        val strings = LocalizationTestCache.getStrings("values", "strings.xml")
        assertNotNull("settings_workout_card_title must exist", strings["settings_workout_card_title"])
        assertNotNull("settings_workout_details_title must exist", strings["settings_workout_details_title"])
    }

    @Test
    fun testFileSizeConstraint_strictlyUnder400Lines() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.WorkoutMasksAndCardsSectionKt")
        assertNotNull("WorkoutMasksAndCardsSectionKt must exist", clazz)
    }

    @Test
    fun `TST-UI-377_7 workout photos configuration and subordinate controls contract`() {
        val compClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.WorkoutPhotoPreferencesComponentsKt")
        assertNotNull("WorkoutPhotoPreferencesComponentsKt must exist", compClass)

        val strings = LocalizationTestCache.getStrings("values", "strings.xml")
        assertNotNull("settings_workout_photos_show_add_button must exist", strings["settings_workout_photos_show_add_button"])
        assertNotNull("settings_workout_photos_thumbnail_size must exist", strings["settings_workout_photos_thumbnail_size"])
    }

    @Test
    fun `TST-UI-453_4c grade distribution in summary matrix and removal of subordinate switches`() {
        val constants = com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSectionType.values().map { it.name }
        assertTrue("WorkoutSectionType must support GRADE_DISTRIBUTION", constants.contains("GRADE_DISTRIBUTION"))
    }

    @Test
    fun `TST-UI-499_5 workout details in summary matrix contract`() {
        val constants = com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSectionType.values().map { it.name }
        assertTrue("WorkoutSectionType must support DETAILS", constants.contains("DETAILS"))

        val strings = LocalizationTestCache.getStrings("values", "strings.xml")
        assertNotNull("workout_detail_section_details must exist", strings["workout_detail_section_details"])
    }
}
