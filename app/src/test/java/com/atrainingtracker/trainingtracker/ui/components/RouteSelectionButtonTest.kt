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

package com.atrainingtracker.trainingtracker.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural and contract tests for RouteSelectionButton (REQ-UI-279, REQ-UI-281 / TST-UI-241.4).
 */
class RouteSelectionButtonTest {

    private fun resolveSourceFile(path: String): File {
        val candidates = listOf(
            File(path),
            File(path.removePrefix("app/")),
            File("../$path"),
            File("../../$path")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Source file not found in candidates: $candidates")
    }

    @Test
    fun testRouteSelectionButton_declaresIsDimmedParameterAndAlphaModifier() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/RouteSelectionButton.kt")
        val content = file.readText()

        assertTrue(
            "RouteSelectionButton must declare isDimmed parameter with default false (REQ-UI-281)",
            content.contains("isDimmed: Boolean = false")
        )
        assertTrue(
            "RouteSelectionButton must apply reduced alpha (0.45f) modifier when isDimmed is true (REQ-UI-281)",
            content.contains(".alpha(if (isDimmed) 0.45f else 1.0f)")
        )
    }

    @Test
    fun testTrackingTabsScreen_passesIsDimmedToRouteSelectionButton() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt")
        val content = file.readText()

        assertTrue(
            "TrackingTabsScreen must pass isDimmed evaluating empty candidate condition (REQ-UI-281)",
            content.contains("isDimmed = routeSelectorUiState.activeRoute == null && routeSelectorUiState.routes.isEmpty()")
        )
    }
}
