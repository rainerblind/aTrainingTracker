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
            R.string.drawer__settings,
            R.string.drawer__info_support
        )
        assertEquals("Navigation drawer must contain 6 category hubs (ATT-5670 / REQ-UI-463)", 6, groups.size)
        assertEquals(expectedCategories, groups.map { it.titleRes })

        val trainingGroup = groups.first { it.titleRes == R.string.drawer__training }
        val mapsGroup = groups.first { it.titleRes == R.string.drawer__maps }
        val myStuffGroup = groups.first { it.titleRes == R.string.drawer__my_stuff }
        val onlineGroup = groups.first { it.titleRes == R.string.prefsOnlineCommunities }
        val settingsGroup = groups.first { it.titleRes == R.string.drawer__settings }
        val infoSupportGroup = groups.first { it.titleRes == R.string.drawer__info_support }

        // Verify collapsible flags: Training and Maps are always expanded; My Stuff, Online Communities, Settings, and Info & Support are collapsible (REQ-UI-405, REQ-UI-463)
        org.junit.Assert.assertFalse(trainingGroup.collapsible)
        org.junit.Assert.assertFalse(mapsGroup.collapsible)
        org.junit.Assert.assertTrue(myStuffGroup.collapsible)
        org.junit.Assert.assertTrue(onlineGroup.collapsible)
        org.junit.Assert.assertTrue(settingsGroup.collapsible)
        org.junit.Assert.assertTrue(infoSupportGroup.collapsible)

        // Verify Training ordering: Start Tracking, Workouts, Stats & Periods, Fitness & Physiology (REQ-UI-513.1)
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

        // Verify My Stuff ordering: My Sensors, Equipment (REQ-UI-422.1)
        val myStuffIds = myStuffGroup.items.map { it.id }
        assertEquals(
            listOf(
                R.id.drawer_my_sensors,
                R.id.drawer_equipment
            ),
            myStuffIds
        )
        val equipmentItem = myStuffGroup.items.first { it.id == R.id.drawer_equipment }
        assertEquals(R.drawable.ic_equipment_combined, equipmentItem.iconRes)
        assertEquals(R.string.equipment_management_title, equipmentItem.titleRes)

        // Verify Online Communities ordering: Strava, Dropbox, Google Drive, Export, Backup & Restore (REQ-UI-463.1)
        val onlineIds = onlineGroup.items.map { it.id }
        assertEquals(
            listOf(
                R.id.drawer_strava,
                R.id.drawer_dropbox,
                R.id.drawer_google_drive,
                R.id.drawer_export,
                R.id.drawer_backup_restore
            ),
            onlineIds
        )

        // Verify Settings ordering: Tracking Layouts, Sport Types, Training Zones, Auto-Pause, Display, Units, Advanced Tuning (REQ-UI-513.2)
        val settingsIds = settingsGroup.items.map { it.id }
        assertEquals(
            listOf(
                R.id.drawer_tracking_layouts,
                R.id.drawer_sport_types,
                R.id.drawer_training_zones,
                R.id.drawer_auto_pause,
                R.id.drawer_display_settings,
                R.id.drawer_units,
                R.id.drawer_advanced_tuning
            ),
            settingsIds
        )
        assertEquals(R.id.drawer_sport_types, settingsIds[1])
        assertEquals(R.id.drawer_training_zones, settingsIds[2])
        assertEquals(R.id.drawer_advanced_tuning, settingsIds.last())
        val sportTypesItem = settingsGroup.items.first { it.id == R.id.drawer_sport_types }
        assertEquals(R.drawable.ic_sports_combined, sportTypesItem.iconRes)
        assertEquals(R.string.sport_types, sportTypesItem.titleRes)
        val trainingZonesItem = settingsGroup.items.first { it.id == R.id.drawer_training_zones }
        assertEquals(R.drawable.ic_speed, trainingZonesItem.iconRes)
        assertEquals(R.string.prefs_training_zones, trainingZonesItem.titleRes)

        // Verify Info & Support ordering: Supporter, Tips Guide, Privacy Policy (REQ-UI-463.1)
        val infoSupportIds = infoSupportGroup.items.map { it.id }
        assertEquals(
            listOf(
                R.id.drawer_supporter,
                R.id.drawer_tips_guide,
                R.id.drawer_privacy_policy
            ),
            infoSupportIds
        )

        val tipsGuideItem = infoSupportGroup.items.first { it.id == R.id.drawer_tips_guide }
        assertEquals(R.drawable.ic_tips_and_updates, tipsGuideItem.iconRes)
        assertEquals(R.string.drawer_tips_guide, tipsGuideItem.titleRes)

        val supporterItem = infoSupportGroup.items.first { it.id == R.id.drawer_supporter }
        assertEquals(R.drawable.ic_crown, supporterItem.iconRes)
        assertEquals(R.string.drawer_supporter, supporterItem.titleRes)

        // Verify default visible destination items across non-collapsible groups = 9 (4 + 5) (REQ-UI-513.1)
        val defaultVisibleCount = groups.filter { !it.collapsible }.sumOf { it.items.size }
        assertEquals("Default visible destination items when secondary groups are collapsed must be 9", 9, defaultVisibleCount)

        val allItems = groups.flatMap { it.items.map { item -> item.id } }
        assertEquals("Navigation drawer must contain 26 total items across 6 groups", 26, allItems.size)
        assertEquals("All 26 navigation items must have distinct IDs", 26, allItems.toSet().size)
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
        val infoSupportGroup = groups.first { it.titleRes == R.string.drawer__info_support }

        // Default selection (Start Tracking): primary expanded, secondary collapsed
        org.junit.Assert.assertTrue(isDrawerGroupInitiallyExpanded(trainingGroup, R.id.drawer_start_tracking))
        org.junit.Assert.assertFalse(isDrawerGroupInitiallyExpanded(myStuffGroup, R.id.drawer_start_tracking))
        org.junit.Assert.assertFalse(isDrawerGroupInitiallyExpanded(onlineGroup, R.id.drawer_start_tracking))
        org.junit.Assert.assertFalse(isDrawerGroupInitiallyExpanded(settingsGroup, R.id.drawer_start_tracking))
        org.junit.Assert.assertFalse(isDrawerGroupInitiallyExpanded(infoSupportGroup, R.id.drawer_start_tracking))

        // Exhaustively verify every item in My Stuff (plus consolidated aliases) auto-expands My Stuff
        myStuffGroup.items.forEach { item ->
            org.junit.Assert.assertTrue(
                "Selecting ${item.id} must auto-expand My Stuff",
                isDrawerGroupInitiallyExpanded(myStuffGroup, item.id)
            )
        }
        org.junit.Assert.assertTrue(isDrawerGroupInitiallyExpanded(myStuffGroup, R.id.drawer_bikes))
        org.junit.Assert.assertTrue(isDrawerGroupInitiallyExpanded(myStuffGroup, R.id.drawer_shoes))

        // Active item inside Online Communities, Settings, or Info & Support auto-expands that group
        org.junit.Assert.assertTrue(isDrawerGroupInitiallyExpanded(onlineGroup, R.id.drawer_strava))
        org.junit.Assert.assertTrue(isDrawerGroupInitiallyExpanded(onlineGroup, R.id.drawer_backup_restore))
        org.junit.Assert.assertTrue(isDrawerGroupInitiallyExpanded(settingsGroup, R.id.drawer_training_zones))
        org.junit.Assert.assertTrue(isDrawerGroupInitiallyExpanded(settingsGroup, R.id.drawer_advanced_tuning))
        org.junit.Assert.assertTrue(isDrawerGroupInitiallyExpanded(infoSupportGroup, R.id.drawer_tips_guide))

        // Sport Types in Settings (REQ-UI-422.4)
        org.junit.Assert.assertTrue(isDrawerGroupInitiallyExpanded(settingsGroup, R.id.drawer_sport_types))
        org.junit.Assert.assertFalse(isDrawerGroupInitiallyExpanded(myStuffGroup, R.id.drawer_sport_types))

        // Consolidated item selection matching for Equipment and decoupled Profile & Zones (REQ-UI-513.2)
        org.junit.Assert.assertTrue(isDrawerItemSelected(R.id.drawer_equipment, R.id.drawer_equipment))
        org.junit.Assert.assertTrue(isDrawerItemSelected(R.id.drawer_equipment, R.id.drawer_bikes))
        org.junit.Assert.assertTrue(isDrawerItemSelected(R.id.drawer_equipment, R.id.drawer_shoes))
        org.junit.Assert.assertFalse(isDrawerItemSelected(R.id.drawer_equipment, R.id.drawer_workouts))

        org.junit.Assert.assertTrue(isDrawerItemSelected(R.id.drawer_athlete_profile, R.id.drawer_athlete_profile))
        org.junit.Assert.assertFalse(isDrawerItemSelected(R.id.drawer_athlete_profile, R.id.drawer_training_zones))
        org.junit.Assert.assertTrue(isDrawerItemSelected(R.id.drawer_training_zones, R.id.drawer_training_zones))
        org.junit.Assert.assertFalse(isDrawerItemSelected(R.id.drawer_training_zones, R.id.drawer_athlete_profile))
        org.junit.Assert.assertFalse(isDrawerItemSelected(R.id.drawer_athlete_profile, R.id.drawer_workouts))
    }
}
