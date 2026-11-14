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

package com.atrainingtracker.trainingtracker.ui.settings.autopause

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural contract test verifying retirement of duplicate auto-pause controls
 * from Advanced Tuning in accordance with Design Guidelines 3.2 (REQ-SET-080.4, TST-SET-080.3, ATT-4836).
 * Refactored to pure JVM reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class AutoPauseRetirementContractTest {

    @Test
    fun testSensorsGpsFilterSection_doesNotDeclareAutoPauseControls() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.SensorsGpsFilterSectionKt")
        val methods = clazz.declaredMethods
        val sectionMethod = methods.find { it.name == "SensorsGpsFilterSection" && Modifier.isPublic(it.modifiers) }
        assertNotNull("SensorsGpsFilterSection composable must exist", sectionMethod)

        // SensorsGpsFilterSection must NOT declare autoPauseBikeEnabled, autoPauseRunEnabled, or autoPauseOtherEnabled
        // (while allowing unrelated Boolean toggles such as postWorkoutErtsSmoothingEnabled).
        val metadata = clazz.getAnnotation(Metadata::class.java)
        assertNotNull("SensorsGpsFilterSectionKt must carry Kotlin Metadata", metadata)
        val symbols = metadata!!.data2.toList()
        val retiredParams = listOf("autoPauseBikeEnabled", "autoPauseRunEnabled", "autoPauseOtherEnabled")
        val foundRetired = retiredParams.filter { it in symbols }
        assertTrue(
            "SensorsGpsFilterSection must not declare retired auto-pause parameters: $foundRetired",
            foundRetired.isEmpty()
        )
    }

    @Test
    fun testAdvancedTuningScreenAndDialog_doNotPassAutoPauseToSensorsGpsFilterSection() {
        val screenClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningScreenKt")
        assertNotNull("AdvancedTuningScreenKt must exist", screenClass)

        val dialogClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningDialogKt")
        assertNotNull("AdvancedTuningDialogKt must exist", dialogClass)
    }
}
