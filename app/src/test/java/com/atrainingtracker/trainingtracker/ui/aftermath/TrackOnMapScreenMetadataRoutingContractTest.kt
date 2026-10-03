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
 * Structural and architectural contract tests for [TrackOnMapScreen] verifying that
 * [WorkoutDescription] and [WorkoutExtrema] are routed into the upper metadata slot
 * of [MapDetailLayout] above telemetry graphs rather than being buried at the bottom
 * of analytics (TST-UI-204.2 / REQ-UI-245 / ATT-2112).
 */
class TrackOnMapScreenMetadataRoutingContractTest {

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
    fun testTrackOnMapScreen_declaresMetadataContentParameter() {
        val file = findTrackOnMapScreenFile()
        val content = file.readText()

        assertTrue(
            "TrackOnMapScreen must declare metadataContent parameter with default null",
            Regex("""metadataContent:\s*\(@Composable\s+ColumnScope\.\(\)\s*->\s*Unit\)\?\s*=\s*null""").containsMatchIn(content)
        )
    }

    @Test
    fun testTrackOnMapScreen_routesDescriptionAndExtremaToMetadataContent() {
        val file = findTrackOnMapScreenFile()
        val content = file.readText()

        val metadataSlotIdx = content.indexOf("metadataContent = {")
        val analyticsSlotIdx = content.indexOf("analyticsContent = {")

        assertTrue("metadataContent slot must be passed to MapDetailLayout", metadataSlotIdx > 0)
        assertTrue("analyticsContent slot must be passed to MapDetailLayout", analyticsSlotIdx > metadataSlotIdx)

        val metadataBlock = content.substring(metadataSlotIdx, analyticsSlotIdx)

        assertTrue(
            "metadataContent must render WorkoutDescription gated on activeDetailPrefs.showDescription",
            metadataBlock.contains("if (activeDetailPrefs.showDescription)") &&
                metadataBlock.contains("WorkoutDescription(")
        )

        assertTrue(
            "metadataContent must render WorkoutExtrema gated on activeDetailPrefs.showExtrema",
            metadataBlock.contains("if (activeDetailPrefs.showExtrema && workoutData.extremaData.dataRows.isNotEmpty())") &&
                metadataBlock.contains("WorkoutExtrema(")
        )
    }

    @Test
    fun testTrackOnMapScreen_doesNotDuplicateMetadataInAnalyticsContent() {
        val file = findTrackOnMapScreenFile()
        val content = file.readText()

        val analyticsSlotIdx = content.indexOf("analyticsContent = {")
        assertTrue("analyticsContent slot must be passed to MapDetailLayout", analyticsSlotIdx > 0)

        val afterAnalytics = content.substring(analyticsSlotIdx)
        val closingIdx = afterAnalytics.indexOf("    )")
        val analyticsBlock = if (closingIdx > 0) afterAnalytics.substring(0, closingIdx) else afterAnalytics

        assertFalse(
            "analyticsContent must not contain WorkoutDescription",
            analyticsBlock.contains("WorkoutDescription(")
        )

        assertFalse(
            "analyticsContent must not contain WorkoutExtrema",
            analyticsBlock.contains("WorkoutExtrema(")
        )
    }
}
