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

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Structural contract test verifying that Aftermath post-workout customization toggles
 * (WorkoutCardSectionPreferences and EditWorkoutFieldPreferences) have been cleanly removed
 * from DisplaySettingsDialog.kt in accordance with REQ-UI-216 and TST-UI-170.1.
 * Refactored to pure JVM reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class DisplaySettingsCleanupTest {

    @Test
    fun testDisplaySettingsDialog_doesNotContainAftermathTogglesOrPreferenceModels() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.display.DisplaySettingsDialogKt")
        val methods = clazz.declaredMethods
        val dialogMethod = methods.find { it.name == "DisplaySettingsDialog" && Modifier.isPublic(it.modifiers) }
        assertNotNull("DisplaySettingsDialog composable must exist and be public", dialogMethod)

        // Verify Aftermath preferences models are NOT passed into DisplaySettingsDialog
        val paramTypeNames = dialogMethod!!.parameterTypes.map { it.name }
        assertTrue(
            "DisplaySettingsDialog must not accept WorkoutCardSectionPreferences",
            paramTypeNames.none { it.contains("WorkoutCardSectionPreferences") }
        )
        assertTrue(
            "DisplaySettingsDialog must not accept EditWorkoutFieldPreferences",
            paramTypeNames.none { it.contains("EditWorkoutFieldPreferences") }
        )
    }
}
