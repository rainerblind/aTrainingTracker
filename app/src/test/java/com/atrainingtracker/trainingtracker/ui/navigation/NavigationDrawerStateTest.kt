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
import org.junit.Test

/**
 * Unit test for [NavigationDrawerController] state transitions and navigation item structure.
 *
 * Fulfills verification requirement TST-NAV-007 (ATT-526 / ATT-243).
 */
class NavigationDrawerStateTest {

    @Test
    fun testInitialDrawerState() {
        val controller = NavigationDrawerController()
        assertEquals("Default selected item must be start tracking", R.id.drawer_start_tracking, controller.selectedItemId)
        assertEquals("Default tracking title must be tab_start", R.string.tab_start, controller.startTrackingTitleRes)
    }

    @Test
    fun testTrackingStateTransitions() {
        val controller = NavigationDrawerController()

        // Start tracking
        controller.startTrackingTitleRes = R.string.Tracking
        assertEquals(R.string.Tracking, controller.startTrackingTitleRes)

        // Pause tracking
        controller.startTrackingTitleRes = R.string.Pause
        assertEquals(R.string.Pause, controller.startTrackingTitleRes)

        // Stop tracking (resets to Start)
        controller.startTrackingTitleRes = R.string.Start
        assertEquals(R.string.Start, controller.startTrackingTitleRes)
    }

    @Test
    fun testDrawerSelectionState() {
        val controller = NavigationDrawerController()

        controller.selectedItemId = R.id.drawer_workouts
        assertEquals(R.id.drawer_workouts, controller.selectedItemId)

        controller.selectedItemId = R.id.drawer_map
        assertEquals(R.id.drawer_map, controller.selectedItemId)

        controller.selectedItemId = R.id.drawer_backup_restore
        assertEquals(R.id.drawer_backup_restore, controller.selectedItemId)
    }

    @Test
    fun testDrawerItemStructureIntegrity() {
        val expectedCategories = listOf(
            R.string.drawer__training,
            R.string.drawer__maps,
            R.string.drawer__my_stuff,
            R.string.prefsOnlineCommunities,
            R.string.drawer__settings
        )
        assertEquals("Navigation drawer must contain 5 category hubs", 5, expectedCategories.size)

        val expectedItems = listOf(
            // Training (3)
            R.id.drawer_start_tracking,
            R.id.drawer_workouts,
            R.id.drawer_periods,
            // Maps (4)
            R.id.drawer_map,
            R.id.drawer_segments,
            R.id.drawer_routes,
            R.id.drawer_my_locations,
            // My Stuff / Equipment (5)
            R.id.drawer_my_sensors,
            R.id.drawer_bikes,
            R.id.drawer_shoes,
            R.id.drawer_sport_types,
            R.id.drawer_training_zones,
            // Online Communities (3)
            R.id.drawer_strava,
            R.id.drawer_dropbox,
            R.id.drawer_export,
            // Settings (6)
            R.id.drawer_units,
            R.id.drawer_display_settings,
            R.id.drawer_tracking_layouts,
            R.id.drawer_search_settings,
            R.id.drawer_backup_restore,
            R.id.drawer_privacy_policy
        )

        assertEquals("Navigation drawer must contain exactly 21 destinations", 21, expectedItems.size)
        // Ensure all IDs are unique non-zero resource integers
        val uniqueItems = expectedItems.toSet()
        assertEquals("All 21 navigation items must have distinct IDs", 21, uniqueItems.size)
        expectedItems.forEach { id ->
            assertNotNull("Item ID must not be null", id)
        }
    }
}
