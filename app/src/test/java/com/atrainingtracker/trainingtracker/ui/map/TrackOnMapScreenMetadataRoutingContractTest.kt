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
 * Architectural contract test for [com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreen]
 * verifying the upper metadata slotting of StravaActivitySection above the map viewport (REQ-UI-248 / TST-UI-207 / ATT-2137).
 */
class TrackOnMapScreenMetadataRoutingContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val trackOnMapScreenFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt")
    }

    @Test
    fun testTrackOnMapScreen_routesStravaActivitySectionInMetadataContentAfterExtrema() {
        assertTrue("TrackOnMapScreen.kt must exist", trackOnMapScreenFile.exists())
        val content = trackOnMapScreenFile.readText()

        val metadataSlotIdx = content.indexOf("metadataContent = {")
        val analyticsSlotIdx = content.indexOf("analyticsContent = {")
        assertTrue("metadataContent slot must be present", metadataSlotIdx > 0)
        assertTrue("analyticsContent slot must be present", analyticsSlotIdx > metadataSlotIdx)

        val metadataContentBlock = content.substring(metadataSlotIdx, analyticsSlotIdx)

        // 1. Verify StravaActivitySection is present in metadataContent
        assertTrue(
            "StravaActivitySection must be rendered within metadataContent",
            metadataContentBlock.contains("StravaActivitySection(")
        )

        // 2. Verify StravaActivitySection is rendered after WorkoutExtrema
        val extremaIdx = metadataContentBlock.indexOf("WorkoutExtrema(")
        val stravaIdx = metadataContentBlock.indexOf("StravaActivitySection(")
        assertTrue("WorkoutExtrema must be rendered in metadataContent", extremaIdx > 0)
        assertTrue("StravaActivitySection must be rendered after WorkoutExtrema in metadataContent", stravaIdx > extremaIdx)

        // 3. Verify guard condition
        assertTrue(
            "StravaActivitySection must be guarded by activeDetailPrefs.showStrava and !workoutData.stravaActivityData.isNullOrBlank()",
            metadataContentBlock.contains("activeDetailPrefs.showStrava && !workoutData.stravaActivityData.isNullOrBlank()")
        )
    }

    @Test
    fun testTrackOnMapScreen_omitsStravaActivitySectionFromAnalyticsContent() {
        assertTrue("TrackOnMapScreen.kt must exist", trackOnMapScreenFile.exists())
        val content = trackOnMapScreenFile.readText()

        val analyticsSlotIdx = content.indexOf("analyticsContent = {")
        assertTrue("analyticsContent slot must be present", analyticsSlotIdx > 0)

        val endAnalyticsSlotIdx = content.indexOf("@Composable\nprivate fun getTrackTypeName", analyticsSlotIdx)
        val analyticsContentBlock = content.substring(analyticsSlotIdx, if (endAnalyticsSlotIdx > 0) endAnalyticsSlotIdx else content.length)

        assertTrue(
            "StravaActivitySection must NOT be rendered within analyticsContent to prevent duplicate rendering below graphs",
            !analyticsContentBlock.contains("StravaActivitySection(")
        )
    }
}
