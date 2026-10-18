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
        val groups = createDrawerGroups(R.string.tab_start)
        val expectedCategories = listOf(
            R.string.drawer__training,
            R.string.drawer__maps,
            R.string.drawer__my_stuff,
            R.string.prefsOnlineCommunities,
            R.string.drawer__settings
        )
        assertEquals("Navigation drawer must contain 5 category hubs (Advanced group removed)", 5, groups.size)
        assertEquals(expectedCategories, groups.map { it.titleRes })

        val trainingGroup = groups.first { it.titleRes == R.string.drawer__training }
        val mapsGroup = groups.first { it.titleRes == R.string.drawer__maps }
        val myStuffGroup = groups.first { it.titleRes == R.string.drawer__my_stuff }
        val onlineGroup = groups.first { it.titleRes == R.string.prefsOnlineCommunities }
        val settingsGroup = groups.first { it.titleRes == R.string.drawer__settings }

        // Verify collapsible flags: Training and Maps are always expanded; My Stuff, Online Communities, and Settings are collapsible (REQ-UI-405)
        org.junit.Assert.assertFalse(trainingGroup.collapsible)
        org.junit.Assert.assertFalse(mapsGroup.collapsible)
        org.junit.Assert.assertTrue(myStuffGroup.collapsible)
        org.junit.Assert.assertTrue(onlineGroup.collapsible)
        org.junit.Assert.assertTrue(settingsGroup.collapsible)

        // Verify Training ordering: Start Tracking, Workouts, Stats & Periods, Profile & Zones (REQ-UI-405.1)
        val trainingIds = trainingGroup.items.map { it.id }
        assertEquals(
            listOf(
                R.id.drawer_start_tracking,
                R.id.drawer_workouts,
                R.id.drawer_periods,
                R.id.drawer_athlete_profile
            ),
            trainingIds
        )
        val profileItem = trainingGroup.items.first { it.id == R.id.drawer_athlete_profile }
        assertEquals(R.drawable.ic_athlete_profile, profileItem.iconRes)
        assertEquals(R.string.drawer_athlete_profile, profileItem.titleRes)

        // Verify My Stuff ordering: My Sensors, Equipment, Sport Types (REQ-UI-405.2)
        val myStuffIds = myStuffGroup.items.map { it.id }
        assertEquals(
            listOf(
                R.id.drawer_my_sensors,
                R.id.drawer_equipment,
                R.id.drawer_sport_types
            ),
            myStuffIds
        )
        val equipmentItem = myStuffGroup.items.first { it.id == R.id.drawer_equipment }
        assertEquals(R.drawable.ic_equipment_combined, equipmentItem.iconRes)
        assertEquals(R.string.equipment_management_title, equipmentItem.titleRes)

        // Verify Settings includes Advanced Tuning at the bottom
        val settingsIds = settingsGroup.items.map { it.id }
        org.junit.Assert.assertTrue(settingsIds.contains(R.id.drawer_advanced_tuning))
        assertEquals(R.id.drawer_advanced_tuning, settingsIds.last())

        // Verify default visible destination items across non-collapsible groups = 9 (4 + 5) (REQ-UI-405.3)
        val defaultVisibleCount = groups.filter { !it.collapsible }.sumOf { it.items.size }
        assertEquals("Default visible destination items when secondary groups are collapsed must be 9", 9, defaultVisibleCount)

        val allItems = groups.flatMap { it.items.map { item -> item.id } }
        assertEquals("Navigation drawer must contain 22 total items across 5 groups", 22, allItems.size)
        assertEquals("All 22 navigation items must have distinct IDs", 22, allItems.toSet().size)
        org.junit.Assert.assertFalse(
            "drawer_search_settings must be removed from drawer destinations",
            allItems.contains(R.id.drawer_search_settings)
        )
        allItems.forEach { id ->
            assertNotNull("Item ID must not be null", id)
        }
    }

    @Test
    fun testCollapsibleDrawerInitialExpansionAndConsolidatedSelection() {
        val groups = createDrawerGroups(R.string.tab_start)
        val trainingGroup = groups.first { it.titleRes == R.string.drawer__training }
        val myStuffGroup = groups.first { it.titleRes == R.string.drawer__my_stuff }
        val onlineGroup = groups.first { it.titleRes == R.string.prefsOnlineCommunities }
        val settingsGroup = groups.first { it.titleRes == R.string.drawer__settings }

        // Default selection (Start Tracking): primary expanded, secondary collapsed
        org.junit.Assert.assertTrue(isDrawerGroupInitiallyExpanded(trainingGroup, R.id.drawer_start_tracking))
        org.junit.Assert.assertFalse(isDrawerGroupInitiallyExpanded(myStuffGroup, R.id.drawer_start_tracking))
        org.junit.Assert.assertFalse(isDrawerGroupInitiallyExpanded(onlineGroup, R.id.drawer_start_tracking))
        org.junit.Assert.assertFalse(isDrawerGroupInitiallyExpanded(settingsGroup, R.id.drawer_start_tracking))

        // Exhaustively verify every item in My Stuff (plus consolidated aliases) auto-expands My Stuff
        myStuffGroup.items.forEach { item ->
            org.junit.Assert.assertTrue(
                "Selecting ${item.id} must auto-expand My Stuff",
                isDrawerGroupInitiallyExpanded(myStuffGroup, item.id)
            )
        }
        org.junit.Assert.assertTrue(isDrawerGroupInitiallyExpanded(myStuffGroup, R.id.drawer_bikes))
        org.junit.Assert.assertTrue(isDrawerGroupInitiallyExpanded(myStuffGroup, R.id.drawer_shoes))

        // Active item inside Online Communities or Settings auto-expands that group
        org.junit.Assert.assertTrue(isDrawerGroupInitiallyExpanded(onlineGroup, R.id.drawer_strava))
        org.junit.Assert.assertTrue(isDrawerGroupInitiallyExpanded(settingsGroup, R.id.drawer_advanced_tuning))
        org.junit.Assert.assertTrue(isDrawerGroupInitiallyExpanded(settingsGroup, R.id.drawer_backup_restore))

        // Consolidated item selection matching for Equipment and Profile & Zones
        org.junit.Assert.assertTrue(isDrawerItemSelected(R.id.drawer_equipment, R.id.drawer_equipment))
        org.junit.Assert.assertTrue(isDrawerItemSelected(R.id.drawer_equipment, R.id.drawer_bikes))
        org.junit.Assert.assertTrue(isDrawerItemSelected(R.id.drawer_equipment, R.id.drawer_shoes))
        org.junit.Assert.assertFalse(isDrawerItemSelected(R.id.drawer_equipment, R.id.drawer_workouts))

        org.junit.Assert.assertTrue(isDrawerItemSelected(R.id.drawer_athlete_profile, R.id.drawer_athlete_profile))
        org.junit.Assert.assertTrue(isDrawerItemSelected(R.id.drawer_athlete_profile, R.id.drawer_training_zones))
        org.junit.Assert.assertFalse(isDrawerItemSelected(R.id.drawer_athlete_profile, R.id.drawer_workouts))
    }
}
