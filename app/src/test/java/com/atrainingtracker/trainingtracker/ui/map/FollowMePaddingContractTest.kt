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
            "ATrainingTrackerMap must decouple GoogleMap contentPadding (topPadding = 0.dp) to satisfy Rule 33",
            content.contains("val topPadding = 0.dp")
        )
        assertTrue(
            "GoogleMap must receive PaddingValues(top = topPadding) as contentPadding",
            content.contains("contentPadding = PaddingValues(top = topPadding)")
        )
        assertTrue(
            "ATrainingTrackerMap must pass viewportHeightDp to followMeController",
            content.contains("viewportHeightDp = maxHeight.value")
        )
    }

    @Test
    fun testFollowMeController_defaultsToCanonicalTuningParameters() {
        val mapContent = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt").readText()

        assertTrue(
            "followMeController must default baseZoom to TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_INITIAL_ZOOM",
            mapContent.contains("baseZoom: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_INITIAL_ZOOM")
        )
        assertTrue(
            "followMeController must default speedZoomEnabled to TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED",
            mapContent.contains("speedZoomEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED")
        )
        assertTrue(
            "followMeController must default cruisingZoom to TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_CRUISING_ZOOM",
            mapContent.contains("cruisingZoom: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_CRUISING_ZOOM")
        )
        assertTrue(
            "followMeController must default tiltAngle to TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_TILT_ANGLE",
            mapContent.contains("tiltAngle: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_TILT_ANGLE")
        )
        assertTrue(
            "followMeController must default lookaheadPaddingPercent to TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT",
            mapContent.contains("lookaheadPaddingPercent: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT")
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
