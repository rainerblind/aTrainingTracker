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

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.navigation.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests verifying navigation routes and drawer dispatch mapping for Advanced Settings (REQ-UI-357, TST-UI-317.3).
 */
class AdvancedTuningNavigationTest {

    @Test
    fun testAdvancedSettingsRouteConstant() {
        assertEquals("advanced_settings", NavRoutes.ADVANCED_SETTINGS)
    }

    @Test
    fun testDrawerItemIdMapsToAdvancedSettingsRoute() {
        assertEquals(NavRoutes.ADVANCED_SETTINGS, NavRoutes.fromDrawerItemId(R.id.drawer_advanced_tuning))
    }

    @Test
    fun testAdvancedSettingsRouteMapsToDrawerItemId() {
        assertEquals(R.id.drawer_advanced_tuning, NavRoutes.toDrawerItemId(NavRoutes.ADVANCED_SETTINGS))
    }

    @Test
    fun testAdvancedTuningDrawerItemReturnsNullForBottomSheet() {
        assertNull(
            "drawer_advanced_tuning must no longer be treated as a modal bottom sheet (REQ-UI-357)",
            NavRoutes.toBottomSheetType(R.id.drawer_advanced_tuning)
        )
        assertNull(
            "drawer_advanced_tuning must no longer be treated as a settings bottom sheet (REQ-UI-357)",
            NavRoutes.toSettingsBottomSheetType(R.id.drawer_advanced_tuning)
        )
    }
}
