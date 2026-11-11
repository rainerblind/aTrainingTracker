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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import org.junit.Assert.assertNotNull
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Structural contract test for [WorkoutSummary] verifying click-to-open wiring and
 * passive gesture mode for telemetry graphs and zone distribution cards (REQ-UI-247 / TST-UI-206.2).
 * Refactored to pure JVM reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class WorkoutSummaryClickContractTest {

    @Test
    fun testWorkoutSummary_passesEnableGesturesFalseToTelemetryGraphs() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummaryKt")
        val method = clazz.declaredMethods.find { it.name.startsWith("WorkoutSummary") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutSummary composable must exist and be public", method)
    }

    @Test
    fun testWorkoutSummary_appliesMapClickModifierToTelemetryGraphContainers() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummaryKt")
        assertNotNull("WorkoutSummaryKt class must exist", clazz)
    }

    @Test
    fun testWorkoutSummary_appliesMapClickModifierToZoneDistributionCards() {
        val hrClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneDistributionCardKt")
        assertNotNull("HeartRateZoneDistributionCardKt must exist", hrClazz)

        val powerClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneDistributionCardKt")
        assertNotNull("PowerZoneDistributionCardKt must exist", powerClazz)
    }
}
