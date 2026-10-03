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

package com.atrainingtracker.trainingtracker.ui.aftermath

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract tests for [TrackOnMapScreen] verifying that [LapSplitVisualizerCard]
 * is routed into the upper metadata slot between [WorkoutExtrema] and [StravaActivitySection] / map,
 * matching the canonical section order of WorkoutSummary.kt (TST-UI-211 / REQ-UI-252 / ATT-2170).
 */
class TrackOnMapScreenLapRoutingContractTest {

    private fun findTrackOnMapScreenFile(): File {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("TrackOnMapScreen.kt not found in candidates: $candidates")
    }

    @Test
    fun testTrackOnMapScreen_routesLapSplitVisualizerCardToMetadataContentBetweenExtremaAndStrava() {
        val file = findTrackOnMapScreenFile()
        val content = file.readText()

        val metadataSlotIdx = content.indexOf("metadataContent = {")
        val analyticsSlotIdx = content.indexOf("analyticsContent = {")

        assertTrue("metadataContent slot must be present in TrackOnMapScreen", metadataSlotIdx > 0)
        assertTrue("analyticsContent slot must follow metadataContent", analyticsSlotIdx > metadataSlotIdx)

        val metadataBlock = content.substring(metadataSlotIdx, analyticsSlotIdx)

        assertTrue(
            "metadataContent must render LapSplitVisualizerCard gated on activeDetailPrefs.showLaps",
            metadataBlock.contains("if (activeDetailPrefs.showLaps)") &&
                metadataBlock.contains("LapSplitVisualizerCard(")
        )

        val extremaIdx = metadataBlock.indexOf("WorkoutExtrema(")
        val lapsIdx = metadataBlock.indexOf("LapSplitVisualizerCard(")
        val stravaIdx = metadataBlock.indexOf("StravaActivitySection(")

        assertTrue("WorkoutExtrema must be rendered in metadataContent", extremaIdx > 0)
        assertTrue("LapSplitVisualizerCard must be rendered after WorkoutExtrema", lapsIdx > extremaIdx)
        assertTrue("StravaActivitySection must be rendered after LapSplitVisualizerCard", stravaIdx > lapsIdx)
    }

    @Test
    fun testTrackOnMapScreen_doesNotRenderLapSplitVisualizerCardInAnalyticsContent() {
        val file = findTrackOnMapScreenFile()
        val content = file.readText()

        val analyticsSlotIdx = content.indexOf("analyticsContent = {")
        assertTrue("analyticsContent slot must be present in TrackOnMapScreen", analyticsSlotIdx > 0)

        val afterAnalytics = content.substring(analyticsSlotIdx)
        val closingIdx = afterAnalytics.indexOf("    )")
        val analyticsBlock = if (closingIdx > 0) afterAnalytics.substring(0, closingIdx) else afterAnalytics

        assertFalse(
            "analyticsContent must not contain LapSplitVisualizerCard",
            analyticsBlock.contains("LapSplitVisualizerCard(")
        )
    }
}
