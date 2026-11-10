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

package com.atrainingtracker.trainingtracker.ui.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit and contract tests for [LiveMmpCurveChart] and [MmpBenchmark] logarithmic scaling
 * and Rule 66 Compose state hygiene conforming to REQ-TRK-058.4 and TST-TRK-058.4.
 */
class LiveMmpCurveChartTest {

    @Test
    fun logScaleDurationMappingIsMonotonicAndNormalized() {
        val f1 = MmpBenchmark.durationToLogFraction(1)
        val f5 = MmpBenchmark.durationToLogFraction(5)
        val f60 = MmpBenchmark.durationToLogFraction(60)
        val f300 = MmpBenchmark.durationToLogFraction(300)
        val f1200 = MmpBenchmark.durationToLogFraction(1200)
        val f7200 = MmpBenchmark.durationToLogFraction(7200)

        assertEquals(0.0f, f1, 0.001f)
        assertEquals(1.0f, f7200, 0.001f)

        assertTrue("5s > 1s", f5 > f1)
        assertTrue("60s > 5s", f60 > f5)
        assertTrue("300s > 60s", f300 > f60)
        assertTrue("1200s > 300s", f1200 > f300)
        assertTrue("7200s > 1200s", f7200 > f1200)
    }

    @Test
    fun benchmarkDurationsParity() {
        assertEquals(4, MmpBenchmark.ALL.size)
        assertEquals(5, MmpBenchmark.SPRINT_5S.durationSeconds)
        assertEquals(60, MmpBenchmark.ANAEROBIC_1M.durationSeconds)
        assertEquals(300, MmpBenchmark.VO2MAX_5M.durationSeconds)
        assertEquals(1200, MmpBenchmark.FTP_20M.durationSeconds)
    }

    @Test
    fun verifyZeroSnapshotStateWritesDuringComposition_Rule66() {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        val chartFile = File(dir, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/LiveMmpCurveChart.kt")
        assertTrue("LiveMmpCurveChart.kt must exist", chartFile.exists())

        val lines = chartFile.readLines()
        lines.forEachIndexed { index, line ->
            assertFalse(
                "Line ${index + 1} contains mutableStateOf write during composition (Rule 66)",
                line.contains("by remember { mutableStateOf") && line.contains("=") && !line.trimStart().startsWith("//")
            )
            assertFalse(
                "Line ${index + 1} contains hardcoded Color(0x outside Color.kt",
                line.contains("Color(0x") && !line.trimStart().startsWith("//") && !line.trimStart().startsWith("*")
            )
        }
    }
}
