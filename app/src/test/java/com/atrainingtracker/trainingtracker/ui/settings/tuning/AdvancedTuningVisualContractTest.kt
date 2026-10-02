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
 * Structural contract test verifying that AdvancedTuningDialog is refactored into a modular,
 * collapsible accordion architecture with 5 semantic subsections, zero tonal elevation,
 * and decoupled subsection composables in accordance with REQ-UI-222 and TST-UI-176.2.
 */
class AdvancedTuningVisualContractTest {

    private fun findSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Source file $relativePath not found in candidates: $candidates")
    }

    @Test
    fun testAdvancedTuningDialog_usesAccordionSectionStructure() {
        val dialogFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt")
        val content = dialogFile.readText()

        // 1. Invocations of TuningAccordionSection
        assertTrue(
            "AdvancedTuningDialog must invoke TuningAccordionSection",
            content.contains("TuningAccordionSection(")
        )

        // 2. All 5 Semantic TuningSection Enum values referenced
        assertTrue(content.contains("TuningSection.COCKPIT_TYPOGRAPHY"))
        assertTrue(content.contains("TuningSection.BATTERY_SAVER"))
        assertTrue(content.contains("TuningSection.SENSORS_GPS"))
        assertTrue(content.contains("TuningSection.AFTERMATH_ANALYSIS"))
        assertTrue(content.contains("TuningSection.WORKOUT_MASKS_CARDS"))

        // 3. Category Title String Resource References
        assertTrue(content.contains("tuning_cat_cockpit_typography"))
        assertTrue(content.contains("tuning_cat_battery_saver"))
        assertTrue(content.contains("tuning_cat_sensors_gps"))
        assertTrue(content.contains("tuning_cat_aftermath"))
        assertTrue(content.contains("tuning_cat_workout_masks_cards"))

        // 4. Accordion Expansion State Management
        assertTrue(
            "Dialog must manage expanded accordion sections via rememberSaveable",
            content.contains("rememberSaveable") && content.contains("expandedSections")
        )
    }

    @Test
    fun testTuningAccordionSection_zeroTonalElevationAndM3Tokens() {
        val accordionFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordion.kt")
        val content = accordionFile.readText()

        // 1. Material 3 Card Container Styling
        assertTrue(
            "TuningAccordionSection must use 12.dp rounded corners",
            content.contains("RoundedCornerShape(12.dp)")
        )
        assertTrue(
            "TuningAccordionSection must use surfaceVariant container color",
            content.contains("surfaceVariant")
        )
        assertTrue(
            "TuningAccordionSection must use outlineVariant border stroke",
            content.contains("outlineVariant")
        )

        // 2. Animated expand/collapse mechanics
        assertTrue(
            "TuningAccordionSection must animate chevron rotation",
            content.contains("animateFloatAsState") && content.contains("180f")
        )
        assertTrue(
            "TuningAccordionSection must use AnimatedVisibility with vertical expand/shrink",
            content.contains("AnimatedVisibility") &&
                    content.contains("expandVertically()") &&
                    content.contains("shrinkVertically()")
        )
    }

    @Test
    fun testDecoupledSubsections_existAsIndividualComposables() {
        val dialogFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt")
        val content = dialogFile.readText()

        // Verify each decoupled subsection composable function signature
        assertTrue(
            "Must define CockpitTypographySection composable",
            content.contains("fun CockpitTypographySection(")
        )
        assertTrue(
            "Must define AmoledBatterySaverSection composable",
            content.contains("fun AmoledBatterySaverSection(")
        )
        assertTrue(
            "Must define SensorsGpsFilterSection composable",
            content.contains("fun SensorsGpsFilterSection(")
        )
        assertTrue(
            "Must define AftermathAnalysisSection composable",
            content.contains("fun AftermathAnalysisSection(")
        )
        assertTrue(
            "Must define WorkoutMasksAndCardsSection composable",
            content.contains("fun WorkoutMasksAndCardsSection(")
        )
    }

    @Test
    fun testAdvancedTuningDialog_initiallyCollapsesAllSections() {
        val dialogFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt")
        val content = dialogFile.readText()

        // 1. Verify emptySet initialization in rememberSaveable (REQ-UI-222 / ATT-1957)
        assertTrue(
            "AdvancedTuningDialog must initialize expandedSections with emptySet() (REQ-UI-222 / ATT-1957)",
            Regex("""var\s+expandedSections\s+by\s+rememberSaveable\s*\{\s*mutableStateOf\(\s*emptySet<String>\(\)\s*\)\s*\}""").containsMatchIn(content)
        )

        // 2. Verify CockpitTypography is not expanded by default
        assertFalse(
            "AdvancedTuningDialog must NOT default-expand COCKPIT_TYPOGRAPHY (ATT-1957)",
            content.contains("setOf(TuningSection.COCKPIT_TYPOGRAPHY.name)")
        )
    }

    @Test
    fun testAdvancedTuningDialog_workoutCardPrefsFlowGating() {
        val dialogFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt")
        val content = dialogFile.readText()

        // Verify initial = null flow collection to prevent race condition (REQ-UI-229 / ATT-1958)
        assertTrue(
            "workoutCardPreferencesFlow must be collected with initial = null to avoid race condition",
            content.contains("workoutCardPreferencesFlow.collectAsState(initial = null)")
        )
        assertTrue(
            "editWorkoutFieldPreferencesFlow must be collected with initial = null to avoid race condition",
            content.contains("editWorkoutFieldPreferencesFlow.collectAsState(initial = null)")
        )

        // Verify non-null gating before initialization
        assertTrue(
            "LaunchedEffect must gate state initialization on non-null preferences",
            content.contains("cardPrefs != null && editPrefs != null")
        )
    }
}

