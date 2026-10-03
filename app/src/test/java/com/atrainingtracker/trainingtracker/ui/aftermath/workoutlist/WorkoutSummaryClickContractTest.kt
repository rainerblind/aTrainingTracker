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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract test for [WorkoutSummary] verifying click-to-open wiring and
 * passive gesture mode for telemetry graphs and zone distribution cards (REQ-UI-247 / TST-UI-206.2).
 */
class WorkoutSummaryClickContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val workoutSummaryFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt")
    }

    @Test
    fun testWorkoutSummary_passesEnableGesturesFalseToTelemetryGraphs() {
        assertTrue("WorkoutSummary.kt must exist", workoutSummaryFile.exists())
        val content = workoutSummaryFile.readText()

        val occurrences = Regex("""enableGestures\s*=\s*false""").findAll(content).count()
        assertEquals(
            "WorkoutSummary must pass enableGestures = false to all 3 telemetry graphs (Speed/Pace, HR, Power) (REQ-UI-247)",
            3,
            occurrences
        )
    }

    @Test
    fun testWorkoutSummary_appliesMapClickModifierToTelemetryGraphContainers() {
        assertTrue("WorkoutSummary.kt must exist", workoutSummaryFile.exists())
        val content = workoutSummaryFile.readText()

        val teleBlockStart = content.indexOf("// 8. Telemetry Metric Graphs")
        val teleBlockEnd = content.indexOf("// 9. Zone Distribution Cards")
        assertTrue("Telemetry block must exist in WorkoutSummary.kt", teleBlockStart > 0 && teleBlockEnd > teleBlockStart)
        val teleBlock = content.substring(teleBlockStart, teleBlockEnd)

        val clickModifierCount = Regex("""\.then\(mapClickModifier\)""").findAll(teleBlock).count()
        assertEquals(
            "All 3 telemetry metric sections in WorkoutSummary must be wrapped with mapClickModifier (REQ-UI-247)",
            3,
            clickModifierCount
        )
    }

    @Test
    fun testWorkoutSummary_appliesMapClickModifierToZoneDistributionCards() {
        assertTrue("WorkoutSummary.kt must exist", workoutSummaryFile.exists())
        val content = workoutSummaryFile.readText()

        val zoneBlockStart = content.indexOf("// 9. Zone Distribution Cards")
        val zoneBlockEnd = content.indexOf("// 10. Export Status Section")
        assertTrue("Zone block must exist in WorkoutSummary.kt", zoneBlockStart > 0 && zoneBlockEnd > zoneBlockStart)
        val zoneBlock = content.substring(zoneBlockStart, zoneBlockEnd)

        assertTrue(
            "HeartRateZoneDistributionCard must chain mapClickModifier (REQ-UI-247)",
            zoneBlock.contains("HeartRateZoneDistributionCard(") &&
            zoneBlock.contains(".then(mapClickModifier)")
        )

        assertTrue(
            "PowerZoneDistributionCard must chain mapClickModifier (REQ-UI-247)",
            zoneBlock.contains("PowerZoneDistributionCard(") &&
            zoneBlock.contains(".then(mapClickModifier)")
        )
    }
}
