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

package com.atrainingtracker.trainingtracker.ui.navigation

import com.atrainingtracker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying navigation drawer item routing and placement for Auto-Pause
 * (REQ-SET-080.1, TST-SET-080.1, ATT-4836).
 */
class AutoPauseNavigationTest {

    @Test
    fun testAutoPauseRouteBidirectionalResolution() {
        val resolvedRoute = NavRoutes.fromDrawerItemId(R.id.drawer_auto_pause)
        assertEquals(
            "Drawer item ID R.id.drawer_auto_pause must resolve to NavRoutes.AUTO_PAUSE",
            NavRoutes.AUTO_PAUSE,
            resolvedRoute
        )

        val resolvedItemId = NavRoutes.toDrawerItemId(NavRoutes.AUTO_PAUSE)
        assertEquals(
            "NavRoutes.AUTO_PAUSE must resolve to R.id.drawer_auto_pause",
            R.id.drawer_auto_pause,
            resolvedItemId
        )

        assertNull(
            "Auto Pause must be a full-screen route, not a modal settings bottom sheet",
            NavRoutes.toBottomSheetType(R.id.drawer_auto_pause)
        )
    }

    @Test
    fun testDrawerStructure_autoPauseDirectlyBelowSportTypes() {
        val groups = createDrawerGroups(R.string.tab_start)
        val settingsGroup = groups.firstOrNull { it.titleRes == R.string.drawer__settings }
        assertNotNull("Settings drawer group must exist", settingsGroup)

        val items = settingsGroup!!.items
        val trainingZonesIndex = items.indexOfFirst { it.id == R.id.drawer_training_zones }
        val sportTypesIndex = items.indexOfFirst { it.id == R.id.drawer_sport_types }
        val displaySettingsIndex = items.indexOfFirst { it.id == R.id.drawer_display_settings }
        val autoPauseIndex = items.indexOfFirst { it.id == R.id.drawer_auto_pause }

        assertTrue("drawer_training_zones must exist in settings group (REQ-UI-513.2)", trainingZonesIndex >= 0)
        assertTrue("drawer_sport_types must exist in settings group", sportTypesIndex >= 0)
        assertTrue("drawer_display_settings must exist in settings group", displaySettingsIndex >= 0)
        assertTrue("drawer_auto_pause must exist in settings group", autoPauseIndex >= 0)
        assertEquals(
            "drawer_sport_types must be placed directly below drawer_training_zones in the Settings group (REQ-UI-513.2)",
            trainingZonesIndex + 1,
            sportTypesIndex
        )
        assertEquals(
            "drawer_auto_pause must be placed directly below drawer_display_settings in the Settings group (REQ-UI-513.2)",
            displaySettingsIndex + 1,
            autoPauseIndex
        )

        val autoPauseConfig = items[autoPauseIndex]
        assertEquals(R.drawable.ic_pause_circle, autoPauseConfig.iconRes)
        assertEquals(R.string.auto_pause_title, autoPauseConfig.titleRes)
    }
}
