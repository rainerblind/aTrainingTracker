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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural and contract tests verifying NonCancellable protection
 * during bulk workout deletion downstream cache resynchronization
 * (REQ-UI-427.4, TST-UI-392.5, ATT-4018).
 */
class WorkoutRepositoryDeleteTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val repoFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt")
    }

    private val viewModelFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesViewModel.kt")
    }

    @Test
    fun testWorkoutRepository_deleteOldWorkouts_wrapsResyncInNonCancellable() {
        assertTrue("WorkoutRepository.kt must exist", repoFile.exists())
        val content = repoFile.readText()

        assertTrue(
            "deleteOldWorkouts must wrap downstream cache resynchronization in NonCancellable (REQ-UI-427.4, TST-UI-392.5)",
            content.contains("withContext(NonCancellable) {") &&
            content.contains("PeriodsRepository.getInstance(application).resyncAllPeriods()") &&
            content.contains("WorkoutClusterRepository.getInstance(application).refreshClusters(")
        )
    }

    @Test
    fun testWorkoutSummariesViewModel_executeDeleteOldWorkouts_wrapsCallInNonCancellable() {
        assertTrue("WorkoutSummariesViewModel.kt must exist", viewModelFile.exists())
        val content = viewModelFile.readText()

        assertTrue(
            "WorkoutSummariesViewModel must wrap deleteOldWorkouts call in NonCancellable (REQ-UI-427.4, TST-UI-392.5)",
            content.contains("withContext(NonCancellable) {") &&
            content.contains("workoutRepo.deleteOldWorkouts(daysToKeep)")
        )
    }
}
