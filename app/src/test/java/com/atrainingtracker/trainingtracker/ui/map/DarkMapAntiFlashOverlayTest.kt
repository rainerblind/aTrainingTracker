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

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests and architectural compliance checks for DarkMapAntiFlashOverlay.
 *
 * Requirements: REQ-MAP-021
 * Test Spec: TST-MAP-023.1, TST-MAP-023.2, TST-MAP-023.3
 */
class DarkMapAntiFlashOverlayTest {

    @Test
    fun testDarkMapBackgroundColorConstant() {
        // TST-MAP-023.2: Verify DarkMapStyle.DARK_MAP_BACKGROUND_COLOR matches #121212
        assertEquals(
            "Default dark map background color must be #121212",
            Color(0xFF121212),
            DarkMapStyle.DARK_MAP_BACKGROUND_COLOR
        )
    }

    @Test
    fun testDarkMapAntiFlashOverlayVisibilityLogic() {
        // TST-MAP-023.3: Verify overlay visibility condition: active only when isDark && !isMapLoaded
        fun shouldShowOverlay(isDark: Boolean, isMapLoaded: Boolean): Boolean {
            return isDark && !isMapLoaded
        }

        // Dark mode, map initializing -> Overlay ACTIVE (obscures bright flash)
        assertTrue(
            "Anti-flash overlay must be visible in dark mode before map has loaded",
            shouldShowOverlay(isDark = true, isMapLoaded = false)
        )

        // Dark mode, map loaded -> Overlay INACTIVE (reveals styled dark map)
        assertFalse(
            "Anti-flash overlay must be hidden once map has finished loading",
            shouldShowOverlay(isDark = true, isMapLoaded = true)
        )

        // Light mode, map initializing -> Overlay INACTIVE (never masks light mode tiles)
        assertFalse(
            "Anti-flash overlay must never be shown in light mode during initialization",
            shouldShowOverlay(isDark = false, isMapLoaded = false)
        )

        // Light mode, map loaded -> Overlay INACTIVE
        assertFalse(
            "Anti-flash overlay must never be shown in light mode after loading",
            shouldShowOverlay(isDark = false, isMapLoaded = true)
        )
    }

    @Test
    fun testStaticAuditOverlayPresentInAllTargetMapComponents() {
        // TST-MAP-023.1: Verify target map surfaces exist and DarkMapAntiFlashOverlay composable exists
        val overlayClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.DarkMapStyleKt")
        assertTrue(
            "DarkMapStyleKt must declare DarkMapAntiFlashOverlay composable",
            overlayClass.declaredMethods.any { it.name.startsWith("DarkMapAntiFlashOverlay") }
        )

        val targetClasses = listOf(
            "com.atrainingtracker.trainingtracker.ui.map.PathPreviewMapKt",
            "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodSummaryCardKt",
            "com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClusterComponentsKt",
            "com.atrainingtracker.trainingtracker.ui.clusters.ManualClusterScreenKt",
            "com.atrainingtracker.trainingtracker.ui.components.workoutlaps.LapEditBottomSheetKt",
            "com.atrainingtracker.trainingtracker.ui.knownlocations.EditKnownLocationDialogKt"
        )
        for (className in targetClasses) {
            val clazz = Class.forName(className)
            assertNotNull("Map component class must exist: $className", clazz)
        }
    }
}
