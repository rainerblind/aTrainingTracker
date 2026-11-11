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

import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Structural modularity and architecture contract tests for AdvancedTuningDialog (REQ-UI-262, TST-UI-221.1).
 * Verifies that the modular category and screen composables are decoupled into individual classes.
 * Refactored to pure JVM reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class AdvancedTuningModularityTest {

    @Test
    fun testModularClassesExist() {
        val expectedClasses = listOf(
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningScreenKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningDialogKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningAccordionKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningDialogFragment",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningFormControlsKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningPaceCeilingFormatter",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.CockpitTypographySectionKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.AmoledBatterySaverSectionKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.SensorsGpsFilterSectionKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.AftermathAnalysisSectionKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.WorkoutMasksAndCardsSectionKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.NavigationSectionKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.SensorSearchTuningSectionKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.ClusterTuningSectionKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.PhysiologyModelsSectionKt"
        )

        for (className in expectedClasses) {
            val clazz = Class.forName(className)
            assertNotNull("Expected modular class $className must exist", clazz)
        }
    }

    @Test
    fun testCategoryComposables_areDecoupledAndExposed() {
        val categoryClasses = listOf(
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.CockpitTypographySectionKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.AmoledBatterySaverSectionKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.SensorsGpsFilterSectionKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.AftermathAnalysisSectionKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.WorkoutMasksAndCardsSectionKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.NavigationSectionKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.SensorSearchTuningSectionKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.ClusterTuningSectionKt",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.PhysiologyModelsSectionKt"
        )

        for (className in categoryClasses) {
            val clazz = Class.forName(className)
            assertNotNull("Category class $className must exist", clazz)
            assert(clazz.declaredMethods.isNotEmpty()) { "Class $className must expose methods" }
        }
    }
}
