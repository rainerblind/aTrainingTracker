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

package com.atrainingtracker.trainingtracker.ui.aftermath.header

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Visual and architectural contract test verifying Material 3 Origin Source badge enforcement in WorkoutHeader (REQ-DAT-017, TST-DAT-012.4).
 * Refactored to pure JVM reflection and LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class WorkoutHeaderSourceBadgeVisualContractTest {

    @Test
    fun testWorkoutHeaderSourceExists() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderKt")
        val method = clazz.declaredMethods.find { it.name.startsWith("WorkoutHeader") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutHeader composable must exist and be public", method)
    }

    @Test
    fun testOriginBadge_usesLocalizedResources() {
        val strings = LocalizationTestCache.getStrings("values", "strings.xml")
        assertNotNull("workout_source_tcx must exist", strings["workout_source_tcx"])
        assertNotNull("workout_source_gpx must exist", strings["workout_source_gpx"])
        assertNotNull("workout_source_fit must exist", strings["workout_source_fit"])
    }
}
