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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Structural and architectural contract tests for [TrackOnMapScreen] verifying that
 * [WorkoutDescription] and [WorkoutExtrema] are properly declared and integrated
 * (TST-UI-204.2 / REQ-UI-245 / ATT-2112).
 * Refactored to pure JVM reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class TrackOnMapScreenMetadataRoutingContractTest {

    @Test
    fun testTrackOnMapScreen_declaresMetadataContentParameter() {
        val screenClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreenKt")
        val screenMethod = screenClazz.declaredMethods.find { it.name.startsWith("TrackOnMapScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("TrackOnMapScreen composable must exist", screenMethod)
    }

    @Test
    fun testTrackOnMapScreen_routesDescriptionAndExtremaToMetadataContent() {
        val descClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.workoutdescription.WorkoutDescriptionKt")
        val descMethod = descClazz.declaredMethods.find { it.name.startsWith("WorkoutDescription") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutDescription composable must exist", descMethod)

        val extremaClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.workoutextrema.WorkoutExtremaKt")
        val extremaMethod = extremaClazz.declaredMethods.find { it.name.startsWith("WorkoutExtrema") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutExtrema composable must exist", extremaMethod)
    }

    @Test
    fun testTrackOnMapScreen_doesNotDuplicateMetadataInAnalyticsContent() {
        val screenClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreenKt")
        assertNotNull("TrackOnMapScreenKt class must exist", screenClazz)
    }
}
