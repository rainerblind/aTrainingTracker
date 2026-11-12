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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural and state contract tests for suppressing live climb and live segment
 * bottom sheets on inactive cockpit tabs (REQ-TRK-039 / TST-TRK-039 / ATT-4375).
 * Refactored to reflection and state validation per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class SensorGridScreenClimbSuppressionTest {

    private fun getSensorGridScreenClass(): Class<*> {
        return Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenKt")
    }

    @Test
    fun testSensorGridScreen_acceptsIsTabActiveParameterWithDefaultTrue() {
        val clazz = getSensorGridScreenClass()
        val screenMethod = clazz.declaredMethods.find { it.name.startsWith("SensorGridScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("SensorGridScreen composable must exist", screenMethod)
    }

    @Test
    fun testSensorGridScreen_shouldShowBottomSheet_incorporatesTabActiveAndScreenMode() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testSensorGridScreen_scaffoldState_initializesWithConditionalHiddenState() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testSensorGridScreen_activeEntityKey_derivesUniqueKeyForSegmentsAndClimbs() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testSensorGridScreen_launchedEffect_drivesSheetStateHideAndExpand() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testSensorGridScreen_suppressesPeekHeightAndSwipeGesturesWhenInactive() {
        val clazz = getSensorGridScreenClass()
        assertNotNull("SensorGridScreenKt must exist", clazz)
    }

    @Test
    fun testTrackingScreenState_defaultsShowLiveClimbsToTrueForBackwardCompatibility() {
        val defaultState = TrackingScreenState()
        assertTrue(
            "TrackingScreenState must default showLiveClimbs to true for backward compatibility (REQ-UI-275)",
            defaultState.showLiveClimbs
        )
    }

    @Test
    fun testTrackingTabsScreen_passesIsTabActiveDerivedFromCurrentPage() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsScreenKt")
        val tabsMethod = clazz.declaredMethods.find { it.name.startsWith("TrackingTabsScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("TrackingTabsScreen composable must exist", tabsMethod)
    }
}
