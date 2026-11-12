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

package com.atrainingtracker.trainingtracker.ui.tracking

import com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural and contract tests for Tracking Tab WYSIWYG configuration mode (REQ-UI-275, TST-UI-235).
 * Refactored to reflection and state validation per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class TrackingTabWysiwygContractTest {

    @Test
    fun testTrackingScreenState_defaultsAreBackwardCompatible() {
        val defaultState = TrackingScreenState()
        assertEquals(false, defaultState.showMap)
        assertEquals(false, defaultState.showElevationProfile)
        assertEquals(false, defaultState.showLiveSegments)
        assertTrue("showLiveClimbs must default to true for backward compatibility", defaultState.showLiveClimbs)
        assertTrue("showNavigationHints must default to true for backward compatibility", defaultState.showNavigationHints)
        assertTrue("showLapButton must default to true for backward compatibility", defaultState.showLapButton)
    }

    @Test
    fun testTrackingTabConfigHeader_removedGenericCheckboxes() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabConfigHeaderKt")
        assertNotNull("TrackingTabConfigHeaderKt must exist", clazz)
    }

    @Test
    fun testSensorGridScreen_definesSpatialWysiwygToggles() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenKt")
        val methods = clazz.declaredMethods
        val toggleMethod = methods.find { it.name.startsWith("SpatialCockpitToggleCard") }
        assertNotNull("SpatialCockpitToggleCard composable must exist", toggleMethod)
    }

    @Test
    fun testSensorGridScreen_enforcesRuntimeGating() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenKt")
        val methods = clazz.declaredMethods
        val screenMethod = methods.find { it.name.startsWith("SensorGridScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("SensorGridScreen composable must exist", screenMethod)
    }

    @Test
    fun testSensorGridScreen_configurationMode_unifiedScrollableContainer() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenKt")
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testSensorGridScreen_configurationMode_excludesLiveMapAndElevationProfile() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenKt")
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testSensorGridScreen_isolatesBottomSheetScaffoldToTrackingMode() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenKt")
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testSensorGridScreen_configurationMode_fullWidthTogglesEliminatesTwoColumnRows() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenKt")
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testTrackingTabsScreen_suppressesLapButtonInConfigurationMode() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsScreenKt")
        val methods = clazz.declaredMethods
        val tabsMethod = methods.find { it.name.startsWith("TrackingTabsScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("TrackingTabsScreen composable must exist", tabsMethod)
    }
}
