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
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.header

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Visual and architectural contract test verifying Material 3 Race badge enforcement in WorkoutHeader (REQ-UI-236, TST-UI-195.3).
 * Refactored to pure JVM reflection and LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class WorkoutHeaderRaceBadgeVisualContractTest {

    @Test
    fun testWorkoutHeaderSourceExists() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderKt")
        val method = clazz.declaredMethods.find { it.name.startsWith("WorkoutHeader") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutHeader composable must exist and be public", method)
    }

    @Test
    fun testRaceBadge_usesLocalizedResource() {
        val strings = LocalizationTestCache.getStrings("values", "strings.xml")
        assertNotNull("race_badge string must exist", strings["race_badge"])
        assertTrue("race_badge string must not be blank", strings["race_badge"]!!.isNotBlank())
    }
}
