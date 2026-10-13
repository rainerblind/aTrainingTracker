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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract tests verifying unified viewport anchor authority and
 * decoupled GoogleMap content padding under Rule 33 (REQ-MAP-046 / TST-MAP-048.2).
 */
class FollowMeUnifiedAnchorContractTest {

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
    fun testATrainingTrackerMap_decouplesGoogleMapPadding_andPassesLookaheadParameters() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt").readText()

        assertTrue(
            "ATrainingTrackerMap must set topPadding to 0.dp to eliminate competing layout loops (Rule 33)",
            content.contains("val topPadding = 0.dp")
        )
        assertTrue(
            "GoogleMap must receive PaddingValues(top = topPadding) where topPadding is 0.dp",
            content.contains("contentPadding = PaddingValues(top = topPadding)")
        )
        assertTrue(
            "ATrainingTrackerMap must pass lookaheadPaddingPercent to followMeController",
            content.contains("lookaheadPaddingPercent = tuningConfig.mapFollowMeLookaheadPaddingPercent")
        )
        assertTrue(
            "ATrainingTrackerMap must pass viewportHeightDp = maxHeight.value to followMeController",
            content.contains("viewportHeightDp = maxHeight.value")
        )
    }

    @Test
    fun testMapBehaviors_followMeController_usesCalculateFollowMeCameraTarget() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt").readText()

        assertTrue(
            "MapBehaviors must define calculateFollowMeCameraTarget",
            content.contains("fun calculateFollowMeCameraTarget(")
        )
        assertTrue(
            "followMeController must invoke calculateFollowMeCameraTarget",
            content.contains("val cameraTarget = calculateFollowMeCameraTarget(")
        )
        assertTrue(
            "followMeController must pass tiltAngle = effectiveTilt to calculateFollowMeCameraTarget (REQ-MAP-048)",
            content.contains("tiltAngle = effectiveTilt")
        )
        assertTrue(
            "followMeController must pass cameraTarget to CameraPosition.builder().target(cameraTarget)",
            content.contains(".target(cameraTarget)")
        )
    }
}

