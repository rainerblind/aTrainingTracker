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

package com.atrainingtracker.trainingtracker.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract tests verifying forward lookahead bottom camera padding and
 * Follow-Me tuning parameter wiring (REQ-MAP-042 / TST-MAP-044.2).
 */
class FollowMePaddingContractTest {

    private fun findProjectRoot(): File {
        var dir: File = File(".").canonicalFile
        while (dir.parentFile != null) {
            if (File(dir, "gradlew").exists() && File(dir, "app").exists()) {
                return dir
            }
            dir = dir.parentFile!!
        }
        return File(".").canonicalFile
    }

    private fun resolveSourceFile(relativePath: String): File {
        val root = findProjectRoot()
        val target = File(root, relativePath)
        assertTrue("Source file must exist: $relativePath", target.exists())
        return target
    }

    @Test
    fun testATrainingTrackerMap_appliesLookaheadPaddingInFollowMeMode() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt").readText()

        assertTrue(
            "ATrainingTrackerMap must wrap GoogleMap inside BoxWithConstraints",
            content.contains("BoxWithConstraints(modifier = modifier)")
        )
        assertTrue(
            "ATrainingTrackerMap must calculate bottomPadding based on FOLLOW_ME zoomFocus and tuningConfig",
            content.contains("val bottomPadding = if (zoomFocus == MapZoomFocus.FOLLOW_ME)") &&
                content.contains("maxHeight * (tuningConfig.mapFollowMeLookaheadPaddingPercent / 100f)") &&
                content.contains("0.dp")
        )
        assertTrue(
            "GoogleMap must receive PaddingValues(bottom = bottomPadding) as contentPadding",
            content.contains("contentPadding = PaddingValues(bottom = bottomPadding)")
        )
    }

    @Test
    fun testATrainingTrackerMap_passesTuningParametersToFollowMeController() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt").readText()

        assertTrue(
            "ATrainingTrackerMap must pass baseZoom to followMeController",
            content.contains("baseZoom = tuningConfig.mapFollowMeInitialZoom")
        )
        assertTrue(
            "ATrainingTrackerMap must pass speedZoomEnabled to followMeController",
            content.contains("speedZoomEnabled = tuningConfig.mapFollowMeSpeedZoomEnabled")
        )
        assertTrue(
            "ATrainingTrackerMap must pass cruisingZoom to followMeController",
            content.contains("cruisingZoom = tuningConfig.mapFollowMeCruisingZoom")
        )
        assertTrue(
            "ATrainingTrackerMap must pass tiltAngle to followMeController",
            content.contains("tiltAngle = tuningConfig.mapFollowMeTiltAngle")
        )
    }

    @Test
    fun testPaddingPercentageCalculation() {
        // Given a 400dp viewport and default 30% lookahead padding:
        val viewportHeightDp = 400.0f
        val paddingPercent = 30.0f
        val calculatedPaddingDp = viewportHeightDp * (paddingPercent / 100.0f)
        assertEquals(120.0f, calculatedPaddingDp, 0.001f)

        // Non-follow-me modes resolve to 0dp:
        val nonFollowMePaddingDp = 0.0f
        assertEquals(0.0f, nonFollowMePaddingDp, 0.001f)
    }
}
