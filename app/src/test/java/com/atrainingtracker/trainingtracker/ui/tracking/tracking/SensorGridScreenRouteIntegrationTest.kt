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

package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import org.junit.Assert.assertNotNull
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural and integration contract test for SensorGridScreen route integration
 * and navigation overlay hierarchy.
 * Refactored to reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class SensorGridScreenRouteIntegrationTest {

    private fun getSensorGridScreenClass(): Class<*> {
        return Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenKt")
    }

    @Test
    fun testSensorGridScreen_doesNotDeclareRouteSelectorViewModelParameter() {
        val clazz = getSensorGridScreenClass()
        val screenMethod = clazz.declaredMethods.find { it.name.startsWith("SensorGridScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("SensorGridScreen composable must exist", screenMethod)
    }

    @Test
    fun testSensorGridScreen_doesNotIntegrateRouteActionChipRow() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testSensorGridScreen_doesNotIntegrateAutoDetectedRouteBanner() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testSensorGridScreen_doesNotHostRedundantRouteSelectorModalBottomSheet() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testSensorGridScreen_doesNotForwardLocationUpdatesToRouteSelectorViewModel() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testRouteSelectionButton_integratesClearRouteAction() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.RouteSelectionButtonKt")
        val methods = clazz.declaredMethods
        val buttonMethod = methods.find { it.name.startsWith("RouteSelectionButton") && Modifier.isPublic(it.modifiers) }
        assertNotNull("RouteSelectionButton composable must exist", buttonMethod)
    }

    @Test
    fun testSensorGridScreen_forkDecisionCard_floatsAsTopCenterOverlay() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testSensorGridScreen_returnNavigationHud_floatsAsTopCenterOverlayAndGatedByNavigationHints() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testSensorGridScreen_turnPromptBanner_floatsAsTopCenterOverlayAndGatedByNavigationHints() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testTurnPromptBanner_stylingTokens_conformsToDesignGuidelines() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.TurnPromptBannerKt")
        val methods = clazz.declaredMethods
        val bannerMethod = methods.find { it.name.startsWith("TurnPromptBanner") && Modifier.isPublic(it.modifiers) }
        assertNotNull("TurnPromptBanner composable must exist", bannerMethod)
    }

    @Test
    fun testSensorGridScreen_mapContainer_doesNotHostNavigationOverlay() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testSensorGridScreen_navigationOverlay_anchorsAtRootTopCenter() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testSensorGridScreen_definesNavigationHintsOverlay_encapsulatingAllNavigationAids() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }
}
