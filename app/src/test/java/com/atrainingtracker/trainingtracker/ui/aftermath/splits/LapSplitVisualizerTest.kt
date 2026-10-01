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
    fun testLapSplitVisualizerSourceContainsNoEmojis() {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitVisualizer.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitVisualizer.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitVisualizer.kt")
        )
        val file = candidates.firstOrNull { it.exists() }
        assertNotNull("LapSplitVisualizer.kt file must exist", file)

        val content = file!!.readText()
        assertFalse("LapSplitVisualizer.kt must NOT contain rabbit emoji (\uD83D\uDC07)", content.contains("\uD83D\uDC07"))
        assertFalse("LapSplitVisualizer.kt must NOT contain hedgehog emoji (\uD83E\uDD94)", content.contains("\uD83E\uDD94"))
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
}
