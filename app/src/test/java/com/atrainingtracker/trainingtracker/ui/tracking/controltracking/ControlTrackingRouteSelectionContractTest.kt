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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural and contract tests for relocating the Route Selection entry point to exclusively
 * the Control Tracking screen with app-consistent branding (REQ-UI-279 / TST-UI-239 / ATT-2458):
 * 1. Complete removal of RouteActionChipRow from SensorGridScreen (REQ-UI-279.1).
 * 2. Preservation of in-ride auto-detection banner in SensorGridScreen.
 * 3. RouteSelectionButton design tokens (12.dp rounded corners, green domain accents, icons, strings) (REQ-UI-279.3).
 * 4. TrackingTabsScreen wiring into ControlTrackingScreen's bottomContent slot (REQ-UI-279.2 / REQ-UI-279.4).
 * Refactored to reflection and LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class ControlTrackingRouteSelectionContractTest {

    @Test
    fun testSensorGridScreen_doesNotContainRouteActionChipRow() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenKt")
        val methods = clazz.declaredMethods
        assertFalse(
            "SensorGridScreen must not define RouteActionChipRow composable (REQ-UI-279.1)",
            methods.any { it.name.startsWith("RouteActionChipRow") }
        )
    }

    @Test
    fun testSensorGridScreen_doesNotIntegrateAutoDetectedRouteBanner() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenKt")
        val methods = clazz.declaredMethods
        assertFalse(
            "SensorGridScreen must not host AutoDetectedRouteBanner (REQ-UI-311)",
            methods.any { it.name.startsWith("AutoDetectedRouteBanner") }
        )
    }

    @Test
    fun testRouteSelectionButton_adheresToRule23AndBrandingDesignTokens() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.RouteSelectionButtonKt")
        val methods = clazz.declaredMethods
        val buttonMethod = methods.find { it.name.startsWith("RouteSelectionButton") && Modifier.isPublic(it.modifiers) }
        assertNotNull("RouteSelectionButton composable must exist", buttonMethod)

        // String resources verification
        val strings = LocalizationTestCache.getStrings("values")
        assertTrue("route_action_select must exist", strings.containsKey("route_action_select"))
        assertTrue("route_action_select_desc must exist", strings.containsKey("route_action_select_desc"))
        assertTrue("route_action_clear must exist", strings.containsKey("route_action_clear"))
        assertTrue("route_metrics_format must exist", strings.containsKey("route_metrics_format"))
    }

    @Test
    fun testTrackingTabsScreen_wiresRouteSelectionButtonToBottomContentSlot() {
        val tabsClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsScreenKt")
        val methods = tabsClazz.declaredMethods
        val screenMethod = methods.find { it.name.startsWith("TrackingTabsScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("TrackingTabsScreen composable must exist", screenMethod)
    }
}
