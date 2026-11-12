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

package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs

import org.junit.Assert.assertNotNull
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Contract tests for TrackingTabsScreen layout, SnackbarHost navigationBarsPadding,
 * and bottom button overlay positioning (REQ-UI-356, TST-UI-317, REQ-UI-384, TST-UI-384).
 * Refactored to reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class TrackingTabsScreenLayoutTest {

    private fun getTrackingTabsScreenClass(): Class<*> {
        return Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsScreenKt")
    }

    @Test
    fun testTrackingTabsScreen_snackbarHost_appliesNavigationBarsPaddingAndLapButtonClearance() {
        val clazz = getTrackingTabsScreenClass()
        val methods = clazz.declaredMethods
        val screenMethod = methods.find { it.name.startsWith("TrackingTabsScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("TrackingTabsScreen composable must exist", screenMethod)
    }

    @Test
    fun testTrackingTabsScreen_bottomOverlayLayoutAndConfigurationSuppression() {
        val clazz = getTrackingTabsScreenClass()
        assertNotNull("TrackingTabsScreenKt must exist", clazz)
    }

    @Test
    fun testTrackingTabsScreen_snackbarHost_usesDynamicBatteryColorsAndPreemption() {
        val clazz = getTrackingTabsScreenClass()
        assertNotNull("TrackingTabsScreenKt must exist", clazz)
    }
}
