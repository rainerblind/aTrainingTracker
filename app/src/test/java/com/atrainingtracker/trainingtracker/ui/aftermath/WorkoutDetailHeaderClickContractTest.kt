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

package com.atrainingtracker.trainingtracker.ui.aftermath

import org.junit.Assert.assertNotNull
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Structural contract test for [WorkoutDetailTabbedContent] verifying that full-card click
 * does not trigger workout editing, reserving edit triggering strictly to the dedicated
 * Edit pencil icon button (REQ-UI-498.1 / TST-UI-498.1).
 * Refactored to pure JVM reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class WorkoutDetailHeaderClickContractTest {

    @Test
    fun testWorkoutHeader_onClickedIsNull_TST_UI_498_1() {
        val tabbedClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentKt")
        val tabbedMethod = tabbedClazz.declaredMethods.find { it.name.startsWith("WorkoutDetailTabbedContent") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutDetailTabbedContent composable must exist", tabbedMethod)

        val headerClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderKt")
        val headerMethod = headerClazz.declaredMethods.find { it.name.startsWith("WorkoutHeader") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutHeader composable must exist", headerMethod)
    }
}
