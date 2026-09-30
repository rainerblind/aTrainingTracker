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
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [LapSplitCalculator].
 * (TST-UI-158 / REQ-UI-204 / ATT-1392)
 */
class LapSplitCalculatorTest {

    private fun createLap(
        lapNr: Long,
        speedMps: Double,
        distanceM: Double = 1000.0,
        timeS: Int = 300,
        name: String? = null
    ): LapData {
        return LapData(
            id = lapNr,
            workoutId = 1L,
            lapNr = lapNr,
            timeStart = "2026-09-30 10:00:00",
            timeTotalS = timeS,
            distanceTotalM = distanceM,
            speedAverageMps = speedMps,
            name = name,
            description = null
        )
    }

    @Test
    fun testCalculateSplitData_relativeRatioScaling() {
        val laps = listOf(
            createLap(1L, speedMps = 3.0),
            createLap(2L, speedMps = 4.0),
            createLap(3L, speedMps = 5.0),
            createLap(4L, speedMps = 6.0)
        )

        val result = LapSplitCalculator.calculateSplitData(laps, BSportType.RUN)
        assertNotNull(result)
        assertEquals(4, result!!.splits.size)

        // Slowest (3.0 m/s) -> ratio 0.25f
        assertEquals(0.25f, result.splits[0].relativeRatio, 0.001f)
        // Fastest (6.0 m/s) -> ratio 1.0f
        assertEquals(1.0f, result.splits[3].relativeRatio, 0.001f)

        // Monotonic progression
        assertTrue(result.splits[0].relativeRatio < result.splits[1].relativeRatio)
        assertTrue(result.splits[1].relativeRatio < result.splits[2].relativeRatio)
        assertTrue(result.splits[2].relativeRatio < result.splits[3].relativeRatio)
    }

    @Test
    fun testCalculateSplitData_fastestAndSlowestIdentification() {
        val laps = listOf(
            createLap(1L, speedMps = 3.0),
            createLap(2L, speedMps = 4.5),
            createLap(3L, speedMps = 6.0)
        )

        val result = LapSplitCalculator.calculateSplitData(laps, BSportType.RUN)
        assertNotNull(result)

        assertTrue(result!!.splits[0].isSlowest)
        assertFalse(result.splits[0].isFastest)

        assertFalse(result.splits[1].isSlowest)
        assertFalse(result.splits[1].isFastest)

        assertFalse(result.splits[2].isSlowest)
        assertTrue(result.splits[2].isFastest)

        assertEquals(3L, result.fastestLapNr)
        assertEquals(1L, result.slowestLapNr)
    }

    @Test
    fun testCalculateSplitData_uniformSpeeds() {
        val laps = listOf(
            createLap(1L, speedMps = 4.0),
            createLap(2L, speedMps = 4.0),
            createLap(3L, speedMps = 4.0)
        )

        val result = LapSplitCalculator.calculateSplitData(laps, BSportType.RUN)
        assertNotNull(result)

        // Uniform speeds: no fastest or slowest, all ratios 1.0f
        result!!.splits.forEach { split ->
            assertFalse(split.isFastest)
            assertFalse(split.isSlowest)
            assertEquals(1.0f, split.relativeRatio, 0.001f)
        }
        assertNull(result.fastestLapNr)
        assertNull(result.slowestLapNr)
    }

    @Test
    fun testCalculateSplitData_intensityColorMapping() {
        assertEquals(TTColor.Zone1, LapSplitCalculator.resolveIntensityColor(0.25f))
        assertEquals(TTColor.Zone1, LapSplitCalculator.resolveIntensityColor(0.39f))
        assertEquals(TTColor.Zone2, LapSplitCalculator.resolveIntensityColor(0.40f))
        assertEquals(TTColor.Zone2, LapSplitCalculator.resolveIntensityColor(0.54f))
        assertEquals(TTColor.Zone3, LapSplitCalculator.resolveIntensityColor(0.55f))
        assertEquals(TTColor.Zone3, LapSplitCalculator.resolveIntensityColor(0.69f))
        assertEquals(TTColor.Zone4, LapSplitCalculator.resolveIntensityColor(0.70f))
        assertEquals(TTColor.Zone4, LapSplitCalculator.resolveIntensityColor(0.84f))
        assertEquals(TTColor.Zone5, LapSplitCalculator.resolveIntensityColor(0.85f))
        assertEquals(TTColor.Zone5, LapSplitCalculator.resolveIntensityColor(1.0f))
    }

    @Test
    fun testCalculateSplitData_lessThanTwoLaps_returnsNull() {
        assertNull(LapSplitCalculator.calculateSplitData(emptyList(), BSportType.RUN))

        val singleLap = listOf(createLap(1L, speedMps = 5.0))
        assertNull(LapSplitCalculator.calculateSplitData(singleLap, BSportType.RUN))
    }

    @Test
    fun testCalculateSplitData_sportTypeFormatting() {
        val laps = listOf(
            createLap(1L, speedMps = 3.33333), // ~5:00 /km or 12.0 km/h
            createLap(2L, speedMps = 4.16667)  // ~4:00 /km or 15.0 km/h
        )

        val runResult = LapSplitCalculator.calculateSplitData(laps, BSportType.RUN)
        assertNotNull(runResult)
        assertTrue(runResult!!.splits[0].formattedPaceOrSpeed.contains("/km") || runResult.splits[0].formattedPaceOrSpeed.contains(":"))

        val bikeResult = LapSplitCalculator.calculateSplitData(laps, BSportType.BIKE)
        assertNotNull(bikeResult)
        assertTrue(bikeResult!!.splits[0].formattedPaceOrSpeed.contains("km/h"))
    }
}
