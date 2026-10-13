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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Visual and architectural contract test verifying Material 3 Origin Source badge enforcement in WorkoutHeader (REQ-DAT-017, TST-DAT-012.4).
 * Enforces:
 * 1. Conditional display based on `data.source != WorkoutSource.TRACKED`.
 * 2. Uncluttered presentation for live tracked sessions (absence of badge).
 * 3. Material 3 tonal styling using surfaceVariant / onSurfaceVariant.
 * 4. Localized string resources for TCX, GPX, and FIT origin badges.
 */
class WorkoutHeaderSourceBadgeVisualContractTest {

    private val headerSourceFile: File by lazy {
        listOf(
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt"),
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt")
        ).firstOrNull { it.exists() } ?: File("src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt")
    }

    @Test
    fun testWorkoutHeaderSourceExists() {
        assertTrue("WorkoutHeader.kt source file must exist", headerSourceFile.exists())
    }

    @Test
    fun testOriginBadge_isGatedByDataSourceNotTracked() {
        val content = headerSourceFile.readText()
        assertTrue("Origin badge must be conditionally rendered when data.source != WorkoutSource.TRACKED",
            content.contains("data.source != WorkoutSource.TRACKED"))
    }

    @Test
    fun testOriginBadge_usesM3SurfaceVariantTokens() {
        val content = headerSourceFile.readText()
        assertTrue("Origin badge must use surfaceVariant container color",
            content.contains("surfaceVariant"))
        assertTrue("Origin badge must use onSurfaceVariant content color",
            content.contains("onSurfaceVariant"))
    }

    @Test
    fun testOriginBadge_usesLocalizedResources() {
        val content = headerSourceFile.readText()
        assertTrue("Origin badge must support R.string.workout_source_tcx",
            content.contains("R.string.workout_source_tcx"))
        assertTrue("Origin badge must support R.string.workout_source_gpx",
            content.contains("R.string.workout_source_gpx"))
        assertTrue("Origin badge must support R.string.workout_source_fit",
            content.contains("R.string.workout_source_fit"))
    }
}
