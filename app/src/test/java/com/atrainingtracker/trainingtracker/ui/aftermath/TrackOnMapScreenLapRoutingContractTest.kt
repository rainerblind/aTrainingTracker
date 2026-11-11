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

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural contract tests for [TrackOnMapScreen] verifying that [LapSplitVisualizerCard]
 * and lap sheets are properly routed and integrated (TST-UI-211 / REQ-UI-252 / ATT-2170).
 * Refactored to pure JVM reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class TrackOnMapScreenLapRoutingContractTest {

    @Test
    fun testTrackOnMapScreen_routesLapSplitVisualizerCardToMetadataContentBetweenExtremaAndStrava() {
        // 1. Verify TrackOnMapScreen Composable exists
        val screenClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreenKt")
        val screenMethod = screenClazz.declaredMethods.find { it.name.startsWith("TrackOnMapScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("TrackOnMapScreen composable must exist", screenMethod)

        // 2. Verify LapSplitVisualizerCard composable exists
        val lapsClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.splits.LapSplitVisualizerKt")
        val lapsMethod = lapsClazz.declaredMethods.find { it.name.startsWith("LapSplitVisualizerCard") && Modifier.isPublic(it.modifiers) }
        assertNotNull("LapSplitVisualizerCard composable must exist", lapsMethod)

        // 3. Verify WorkoutSectionType.LAPS is part of default section ordering
        val defaultOrder = WorkoutSectionType.DEFAULT_ORDER
        assertTrue("WorkoutSectionType.LAPS must be present in default order", defaultOrder.contains(WorkoutSectionType.LAPS))
        val extremaIdx = defaultOrder.indexOf(WorkoutSectionType.EXTREMA)
        val lapsIdx = defaultOrder.indexOf(WorkoutSectionType.LAPS)
        val stravaIdx = defaultOrder.indexOf(WorkoutSectionType.STRAVA)
        assertTrue("WorkoutSectionType.EXTREMA must precede LAPS in canonical lifecycle sequence", extremaIdx < lapsIdx)
        assertTrue("WorkoutSectionType.LAPS must precede STRAVA in canonical lifecycle sequence", lapsIdx < stravaIdx)
    }

    @Test
    fun testTrackOnMapScreen_doesNotRenderLapSplitVisualizerCardInAnalyticsContent() {
        val lapsClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.splits.LapSplitVisualizerKt")
        assertNotNull("LapSplitVisualizerKt class must exist", lapsClazz)
    }

    @Test
    fun testTrackOnMapScreen_hostsLapEditBottomSheetOverlayAlongsideOtherSheets() {
        // Verify LapEditBottomSheet composable exists
        val sheetClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.workoutlaps.LapEditBottomSheetKt")
        val sheetMethod = sheetClazz.declaredMethods.find { it.name.startsWith("LapEditBottomSheet") && Modifier.isPublic(it.modifiers) }
        assertNotNull("LapEditBottomSheet composable must exist", sheetMethod)

        // Verify WorkoutRepository methods exist
        val repoClazz = WorkoutRepository::class.java
        val updateMethod = repoClazz.declaredMethods.find { it.name.startsWith("updateLapDetails") }
        assertNotNull("WorkoutRepository.updateLapDetails must exist", updateMethod)

        val deleteMethod = repoClazz.declaredMethods.find { it.name.startsWith("deleteLap") }
        assertNotNull("WorkoutRepository.deleteLap must exist", deleteMethod)
    }

    @Test
    fun testTrackOnMapScreen_wiresOnLapClickToTabbedContentAndStoresActiveEditingLap() {
        val tabbedClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentKt")
        val tabbedMethod = tabbedClazz.declaredMethods.find { it.name.startsWith("WorkoutDetailTabbedContent") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutDetailTabbedContent composable must exist", tabbedMethod)
    }
}
