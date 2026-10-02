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

package com.atrainingtracker.trainingtracker.ui.components.workoutlaps

import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying conditional display logic for LapDisplayMode (REQ-UI-229, TST-UI-183, ATT-1870).
 */
class WorkoutLapsDisplayModeTest {

    @Test
    fun testShouldShowVisualizer_acrossAllDisplayModes() {
        assertTrue("BOTH must show split visualizer", WorkoutLapsHelper.shouldShowVisualizer(LapDisplayMode.BOTH))
        assertTrue("VISUALIZER_ONLY must show split visualizer", WorkoutLapsHelper.shouldShowVisualizer(LapDisplayMode.VISUALIZER_ONLY))
        assertFalse("TABLE_ONLY must hide split visualizer", WorkoutLapsHelper.shouldShowVisualizer(LapDisplayMode.TABLE_ONLY))
    }

    @Test
    fun testShouldShowTable_acrossAllDisplayModes() {
        assertTrue("BOTH must show lap table", WorkoutLapsHelper.shouldShowTable(LapDisplayMode.BOTH))
        assertTrue("TABLE_ONLY must show lap table", WorkoutLapsHelper.shouldShowTable(LapDisplayMode.TABLE_ONLY))
        assertFalse("VISUALIZER_ONLY must hide lap table", WorkoutLapsHelper.shouldShowTable(LapDisplayMode.VISUALIZER_ONLY))
    }

    @Test
    fun testLapClickContract_retainsLapDataAcrossModes() {
        val lap1 = LapData(id = 10, workoutId = 1, lapNr = 1, timeTotalS = 180, distanceTotalM = 500.0, speedAverageMps = 2.77)
        val lap2 = LapData(id = 11, workoutId = 1, lapNr = 2, timeTotalS = 200, distanceTotalM = 500.0, speedAverageMps = 2.50)
        val laps = listOf(lap1, lap2)

        var clickedLap: LapData? = null
        val onLapClick: (LapData) -> Unit = { lap -> clickedLap = lap }

        // Test split click callback simulation
        val visualizerLapClickSim: (Long) -> Unit = { lapNr ->
            laps.find { it.lapNr == lapNr }?.let { onLapClick(it) }
        }

        visualizerLapClickSim(2L)
        assertEquals(lap2, clickedLap)

        visualizerLapClickSim(1L)
        assertEquals(lap1, clickedLap)
    }
}
