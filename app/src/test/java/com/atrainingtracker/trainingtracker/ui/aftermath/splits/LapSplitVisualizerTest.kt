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

package com.atrainingtracker.trainingtracker.ui.aftermath.splits

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Unit tests verifying [LapSplitVisualizer] data model compliance and aesthetic constraints.
 * (REQ-UI-204, TST-UI-158 / ATT-1742)
 */
class LapSplitVisualizerTest {

    private fun createLap(
        lapNr: Long,
        speedMps: Double,
        distanceM: Double = 1000.0,
        timeS: Int = 300
    ): LapData {
        return LapData(
            id = lapNr,
            workoutId = 1L,
            lapNr = lapNr,
            timeStart = "2026-10-01 10:00:00",
            timeTotalS = timeS,
            distanceTotalM = distanceM,
            speedAverageMps = speedMps,
            name = null,
            description = null
        )
    }

    @Test
    fun testLapSplitVisualizerSource_containsRabbitAndNoHedgehog() {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitVisualizer.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitVisualizer.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitVisualizer.kt")
        )
        val file = candidates.firstOrNull { it.exists() }
        assertNotNull("LapSplitVisualizer.kt file must exist", file)

        val content = file!!.readText()
        // REQ-UI-228 / ATT-1869: Rabbit icon left of pace on fastest split is required
        assertTrue("LapSplitVisualizer.kt must contain rabbit emoji (\uD83D\uDC07)", content.contains("\uD83D\uDC07"))
        assertFalse("LapSplitVisualizer.kt must NOT contain hedgehog emoji (\uD83E\uDD94)", content.contains("\uD83E\uDD94"))
        assertFalse("LapSplitVisualizer.kt must NOT contain split_badge_best text badge", content.contains("split_badge_best"))

        // Truncation & custom name contract checks
        assertTrue("LapSplitVisualizer.kt must reference show_all_laps", content.contains("show_all_laps"))
        assertTrue("LapSplitVisualizer.kt must reference show_fewer_laps", content.contains("show_fewer_laps"))
        assertTrue("LapSplitVisualizer.kt must slice take(3)", content.contains("take(3)"))
        assertTrue("LapSplitVisualizer.kt must render split.displayName", content.contains("split.displayName"))
        assertFalse("LapSplitVisualizer.kt must NOT hardcode synthetic L\${split.lapNr}", content.contains("\"L\${split.lapNr}\""))
    }

    @Test
    fun testVisualizerDataModel_identifiesBestSplitForBadge() {
        val laps = listOf(
            createLap(1L, speedMps = 3.5),
            createLap(2L, speedMps = 4.2), // fastest
            createLap(3L, speedMps = 3.8)
        )

        val data = LapSplitCalculator.calculateSplitData(laps, BSportType.RUN)
        assertNotNull(data)
        assertEquals(3, data!!.splits.size)

        val bestSplit = data.splits.find { it.isFastest }
        assertNotNull("Fastest split must be tagged for 'Best' badge", bestSplit)
        assertEquals(2L, bestSplit!!.lapNr)
        assertEquals(2L, data.fastestLapNr)

        // Verify other splits are not fastest
        assertFalse(data.splits[0].isFastest)
        assertFalse(data.splits[2].isFastest)
    }

    @Test
    fun testVisualizerDataModel_columnarPropertiesPresent() {
        val laps = listOf(
            createLap(1L, speedMps = 3.0, distanceM = 1000.0, timeS = 333),
            createLap(2L, speedMps = 4.0, distanceM = 1000.0, timeS = 250)
        )

        val data = LapSplitCalculator.calculateSplitData(laps, BSportType.RUN)
        assertNotNull(data)

        data!!.splits.forEach { split ->
            assertTrue("Lap number must be positive", split.lapNr > 0)
            assertTrue("Distance must be > 0", split.distanceMeters > 0.0)
            assertTrue("Duration must be > 0", split.durationSec > 0)
            assertTrue("Formatted pace or speed must not be blank", split.formattedPaceOrSpeed.isNotBlank())
            assertTrue("Relative ratio must be between 0.25 and 1.0", split.relativeRatio in 0.25f..1.0f)
        }
    }

    @Test
    fun testVisualizerDataModel_customLapNamesPreserved() {
        val laps = listOf(
            LapData(id = 1L, workoutId = 1L, lapNr = 1L, timeStart = "2026-10-01 10:00:00",
                timeTotalS = 300, distanceTotalM = 1000.0, speedAverageMps = 3.33, name = "Warm-up", description = null),
            LapData(id = 2L, workoutId = 1L, lapNr = 2L, timeStart = "2026-10-01 10:05:00",
                timeTotalS = 240, distanceTotalM = 1000.0, speedAverageMps = 4.16, name = "Intervall 1", description = null),
            LapData(id = 3L, workoutId = 1L, lapNr = 3L, timeStart = "2026-10-01 10:09:00",
                timeTotalS = 360, distanceTotalM = 1000.0, speedAverageMps = 2.77, name = null, description = null)
        )

        val data = LapSplitCalculator.calculateSplitData(laps, BSportType.RUN)
        assertNotNull(data)
        assertEquals(3, data!!.splits.size)

        assertEquals("Warm-up", data.splits[0].displayName)
        assertEquals("Intervall 1", data.splits[1].displayName)
        assertEquals("Lap 3", data.splits[2].displayName)
    }
}

