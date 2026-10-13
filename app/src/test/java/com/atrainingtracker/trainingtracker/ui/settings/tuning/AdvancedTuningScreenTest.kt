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
 * Structural, architectural and gesture contract tests for AdvancedTuningScreen (REQ-UI-357, TST-UI-317.1, TST-UI-317.2).
 */
class AdvancedTuningScreenTest {

    private fun findScreenSourceFile(): File {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreen.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreen.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreen.kt")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("AdvancedTuningScreen.kt not found in candidates: $candidates")
    }

    @Test
    fun testScreenArchitecture_scaffoldAndTopAppBar() {
        val file = findScreenSourceFile()
        val content = file.readText()

        // 1. Must use Material 3 Scaffold with TopAppBar and Sticky BottomBar
        assertTrue("Must use Scaffold", content.contains("Scaffold("))
        assertTrue("Must use TopAppBar", content.contains("TopAppBar("))
        assertTrue("Must display advanced_tuning_title in TopAppBar", content.contains("R.string.advanced_tuning_title"))
        assertTrue("Must contain navigation back icon", content.contains("Icons.AutoMirrored.Filled.ArrowBack"))
        assertTrue("Must contain reset action button in top bar", content.contains("Icons.Default.RestartAlt"))
        assertTrue("Must contain Save/Cancel bottom actions", content.contains("AppDialogActions.SaveCancel"))
    }

    @Test
    fun testZeroDismissalScroll_decoupledFromBottomSheet() {
        val file = findScreenSourceFile()
        val content = file.readText()

        // 1. Must use full-height verticalScroll
        assertTrue("Must use verticalScroll(rememberScrollState())", content.contains("verticalScroll(rememberScrollState())"))

        // 2. Must NOT be wrapped inside ModalBottomSheet or AppBottomSheetContent
        assertFalse("Must NOT use ModalBottomSheet", content.contains("ModalBottomSheet("))
        assertFalse("Must NOT use AppBottomSheetContent", content.contains("AppBottomSheetContent("))
    }

    @Test
    fun testAccordionSections_canonicalLifecycleSequence() {
        val file = findScreenSourceFile()
        val content = file.readText()

        // Verify index of each section to ensure correct 3-phase athletic lifecycle sequence
        val navIndex = content.indexOf("TuningSection.NAVIGATION")
        val cockpitIndex = content.indexOf("TuningSection.COCKPIT_TYPOGRAPHY")
        val batteryIndex = content.indexOf("TuningSection.BATTERY_SAVER")
        val sensorsIndex = content.indexOf("TuningSection.SENSORS_GPS")
        val searchIndex = content.indexOf("TuningSection.SENSOR_SEARCH")
        val workoutMasksIndex = content.indexOf("TuningSection.WORKOUT_MASKS_CARDS")
        val aftermathIndex = content.indexOf("TuningSection.AFTERMATH_ANALYSIS")

        assertTrue("Navigation must exist", navIndex >= 0)
        assertTrue("Cockpit Typography must exist", cockpitIndex >= 0)
        assertTrue("Battery Saver must exist", batteryIndex >= 0)
        assertTrue("Sensors GPS must exist", sensorsIndex >= 0)
        assertTrue("Sensor Search must exist", searchIndex >= 0)
        assertTrue("Workout Masks must exist", workoutMasksIndex >= 0)
        assertTrue("Aftermath Analysis must exist", aftermathIndex >= 0)

        assertTrue("Navigation must precede Cockpit", navIndex < cockpitIndex)
        assertTrue("Cockpit must precede Battery Saver", cockpitIndex < batteryIndex)
        assertTrue("Battery Saver must precede Sensors GPS", batteryIndex < sensorsIndex)
        assertTrue("Sensors GPS must precede Sensor Search", sensorsIndex < searchIndex)
        assertTrue("Sensor Search must precede Workout Masks", searchIndex < workoutMasksIndex)
        assertTrue("Workout Masks must precede Aftermath Analysis", workoutMasksIndex < aftermathIndex)
    }

    @Test
    fun testInitialCollapseState_allSectionsCollapsed() {
        val file = findScreenSourceFile()
        val content = file.readText()

        assertTrue(
            "Must initialize expandedSections with emptySet() to start fully collapsed",
            Regex("""var\s+expandedSections\s+by\s+rememberSaveable\s*\{\s*mutableStateOf\(\s*emptySet<String>\(\)\s*\)\s*\}""").containsMatchIn(content)
        )
    }
}
