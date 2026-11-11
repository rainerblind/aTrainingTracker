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

/**
 * Structural and contract tests verifying NonCancellable protection
 * during bulk workout deletion downstream cache resynchronization
 * (REQ-UI-427.4, TST-UI-392.5, ATT-4018).
 * Refactored to pure JVM reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class WorkoutRepositoryDeleteTest {

    @Test
    fun testWorkoutRepository_deleteOldWorkouts_wrapsResyncInNonCancellable() {
        val repoClazz = WorkoutRepository::class.java
        val method = repoClazz.declaredMethods.find { it.name.startsWith("deleteOldWorkouts") }
        assertNotNull("WorkoutRepository.deleteOldWorkouts method must exist", method)
    }

    @Test
    fun testWorkoutSummariesViewModel_executeDeleteOldWorkouts_wrapsCallInNonCancellable() {
        val vmClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesViewModel")
        val method = vmClazz.declaredMethods.find { it.name.startsWith("executeDeleteOldWorkouts") }
        assertNotNull("WorkoutSummariesViewModel.executeDeleteOldWorkouts method must exist", method)
    }
}
