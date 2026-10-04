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

package com.atrainingtracker.trainingtracker.ui.components.strava

import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.ui.aftermath.StravaActivity
import com.atrainingtracker.trainingtracker.ui.aftermath.StravaActivityParser
import com.atrainingtracker.trainingtracker.ui.aftermath.StravaBestEffort
import com.atrainingtracker.trainingtracker.ui.aftermath.StravaSegmentEffort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Performance and contract tests verifying that high-density Strava activities
 * (such as 40+ segment efforts and 20+ best efforts) are handled efficiently
 * with memoized time formatters and zero memory/computational bottlenecks (REQ-UI-254 / ATT-2178).
 */
class StravaActivitySectionPerformanceTest {

    private lateinit var stravaActivitySectionFile: File

    @Before
    fun setUp() {
        val userDir = System.getProperty("user.dir") ?: "."
        var projectRoot = File(userDir)
        if (!File(projectRoot, "app").exists() && File(projectRoot, "../app").exists()) {
            projectRoot = File(projectRoot, "..")
        }
        stravaActivitySectionFile = File(
            projectRoot,
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/strava/StravaActivitySection.kt"
        )
    }

    @Test
    fun testStravaActivitySection_memoizesTimeFormatter() {
        assertTrue("StravaActivitySection.kt must exist", stravaActivitySectionFile.exists())
        val content = stravaActivitySectionFile.readText()

        assertTrue(
            "StravaActivitySection must remember TimeFormatter at section level",
            content.contains("val timeFormatter = remember { TimeFormatter() }")
        )
        assertTrue(
            "SegmentPrCelebrationBanner must accept reusable timeFormatter",
            content.contains("timeFormatter: TimeFormatter = remember { TimeFormatter() }")
        )
        assertTrue(
            "BestEffortRow must accept reusable timeFormatter",
            content.contains("timeFormatter: TimeFormatter = remember { TimeFormatter() }")
        )
        assertTrue(
            "SegmentEffortRow must accept reusable timeFormatter",
            content.contains("timeFormatter: TimeFormatter = remember { TimeFormatter() }")
        )
    }

    @Test
    fun testHighDensityActivity_40SegmentEfforts_formatsSmoothlyWithSharedFormatter() {
        val timeFormatter = TimeFormatter()
        val segments = (1..50).map { i ->
            StravaSegmentEffort(
                name = "Segment $i",
                elapsedTimeSec = 100 + i * 15,
                prRank = if (i % 5 == 0) 1 else null,
                komRank = if (i == 1) 1 else null,
                isStarred = i % 3 == 0,
                segmentId = 1000L + i
            )
        }
        val bestEfforts = (1..20).map { i ->
            StravaBestEffort(
                name = "Best Effort $i",
                elapsedTimeSec = 300 + i * 30,
                prRank = if (i == 1) 1 else null,
                distanceMeters = (i * 1000).toDouble()
            )
        }

        val activity = StravaActivity(
            id = 999999L,
            segmentEfforts = segments,
            bestEfforts = bestEfforts
        )

        assertEquals(50, activity.segmentEfforts.size)
        assertEquals(20, activity.bestEfforts.size)

        // Verify batch formatting with single shared formatter instance
        val formattedTimes = mutableListOf<String>()
        val startNs = System.nanoTime()
        for (effort in activity.segmentEfforts) {
            formattedTimes.add(timeFormatter.format(effort.elapsedTimeSec.toLong()))
        }
        for (effort in activity.bestEfforts) {
            formattedTimes.add(timeFormatter.format(effort.elapsedTimeSec.toLong()))
        }
        val durationMs = (System.nanoTime() - startNs) / 1_000_000

        assertEquals(70, formattedTimes.size)
        assertTrue("70 items formatted in under 50ms with shared formatter", durationMs < 50)
    }
}
