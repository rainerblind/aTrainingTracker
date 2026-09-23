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
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodSummary
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Unit test for Jetpack Compose Single-Activity Navigation architecture (ATT-1083).
 *
 * Verifies [NavRoutes] routing contracts, [SettingsBottomSheetType] sheet mappings,
 * [NavigationDrawerController] state transitions, and [MainActivityWithNavigation] public API compliance.
 *
 * Fulfills verification requirement TST-NAV-009 (REQ-UI-159).
 */
class SingleActivityNavigationTest {

    @Test
    fun testNavRoutesConstantsIntegrity() {
        assertEquals("start_tracking", NavRoutes.START_TRACKING)
        assertEquals("workouts", NavRoutes.WORKOUTS)
        assertEquals("periods", NavRoutes.PERIODS)
        assertEquals("map", NavRoutes.MAP)
        assertEquals("segments", NavRoutes.SEGMENTS)
        assertEquals("routes", NavRoutes.ROUTES)
        assertEquals("locations", NavRoutes.LOCATIONS)
        assertEquals("sensors", NavRoutes.SENSORS)
        assertEquals("bikes", NavRoutes.BIKES)
        assertEquals("shoes", NavRoutes.SHOES)
        assertEquals("sport_types", NavRoutes.SPORT_TYPES)
        assertEquals("training_zones", NavRoutes.TRAINING_ZONES)
        assertEquals("backup_restore", NavRoutes.BACKUP_RESTORE)
    }

    @Test
    fun testDrawerItemIdToRouteMapping() {
        assertEquals(NavRoutes.START_TRACKING, NavRoutes.fromDrawerItemId(R.id.drawer_start_tracking))
        assertEquals(NavRoutes.WORKOUTS, NavRoutes.fromDrawerItemId(R.id.drawer_workouts))
        assertEquals(NavRoutes.PERIODS, NavRoutes.fromDrawerItemId(R.id.drawer_periods))
        assertEquals(NavRoutes.MAP, NavRoutes.fromDrawerItemId(R.id.drawer_map))
        assertEquals(NavRoutes.SEGMENTS, NavRoutes.fromDrawerItemId(R.id.drawer_segments))
        assertEquals(NavRoutes.ROUTES, NavRoutes.fromDrawerItemId(R.id.drawer_routes))
        assertEquals(NavRoutes.LOCATIONS, NavRoutes.fromDrawerItemId(R.id.drawer_my_locations))
        assertEquals(NavRoutes.SENSORS, NavRoutes.fromDrawerItemId(R.id.drawer_my_sensors))
        assertEquals(NavRoutes.BIKES, NavRoutes.fromDrawerItemId(R.id.drawer_bikes))
        assertEquals(NavRoutes.SHOES, NavRoutes.fromDrawerItemId(R.id.drawer_shoes))
        assertEquals(NavRoutes.SPORT_TYPES, NavRoutes.fromDrawerItemId(R.id.drawer_sport_types))
        assertEquals(NavRoutes.TRAINING_ZONES, NavRoutes.fromDrawerItemId(R.id.drawer_training_zones))
        assertEquals(NavRoutes.BACKUP_RESTORE, NavRoutes.fromDrawerItemId(R.id.drawer_backup_restore))

        // Non-route items must return null
        assertNull(NavRoutes.fromDrawerItemId(R.id.drawer_strava))
        assertNull(NavRoutes.fromDrawerItemId(R.id.drawer_dropbox))
        assertNull(NavRoutes.fromDrawerItemId(R.id.drawer_export))
        assertNull(NavRoutes.fromDrawerItemId(R.id.drawer_tracking_layouts))
        assertNull(NavRoutes.fromDrawerItemId(R.id.drawer_units))
        assertNull(NavRoutes.fromDrawerItemId(R.id.drawer_display_settings))
        assertNull(NavRoutes.fromDrawerItemId(R.id.drawer_search_settings))
        assertNull(NavRoutes.fromDrawerItemId(R.id.drawer_privacy_policy))
    }

    @Test
    fun testRouteToDrawerItemIdMapping() {
        assertEquals(R.id.drawer_start_tracking, NavRoutes.toDrawerItemId(NavRoutes.START_TRACKING))
        assertEquals(R.id.drawer_workouts, NavRoutes.toDrawerItemId(NavRoutes.WORKOUTS))
        assertEquals(R.id.drawer_periods, NavRoutes.toDrawerItemId(NavRoutes.PERIODS))
        assertEquals(R.id.drawer_map, NavRoutes.toDrawerItemId(NavRoutes.MAP))
        assertEquals(R.id.drawer_segments, NavRoutes.toDrawerItemId(NavRoutes.SEGMENTS))
        assertEquals(R.id.drawer_routes, NavRoutes.toDrawerItemId(NavRoutes.ROUTES))
        assertEquals(R.id.drawer_my_locations, NavRoutes.toDrawerItemId(NavRoutes.LOCATIONS))
        assertEquals(R.id.drawer_my_sensors, NavRoutes.toDrawerItemId(NavRoutes.SENSORS))
        assertEquals(R.id.drawer_bikes, NavRoutes.toDrawerItemId(NavRoutes.BIKES))
        assertEquals(R.id.drawer_shoes, NavRoutes.toDrawerItemId(NavRoutes.SHOES))
        assertEquals(R.id.drawer_sport_types, NavRoutes.toDrawerItemId(NavRoutes.SPORT_TYPES))
        assertEquals(R.id.drawer_training_zones, NavRoutes.toDrawerItemId(NavRoutes.TRAINING_ZONES))
        assertEquals(R.id.drawer_backup_restore, NavRoutes.toDrawerItemId(NavRoutes.BACKUP_RESTORE))

        // Unknown route falls back to start tracking
        assertEquals(R.id.drawer_start_tracking, NavRoutes.toDrawerItemId("unknown_route"))
    }

    @Test
    fun testSettingsBottomSheetTypeMapping() {
        assertEquals(SettingsBottomSheetType.STRAVA, NavRoutes.toSettingsBottomSheetType(R.id.drawer_strava))
        assertEquals(SettingsBottomSheetType.DROPBOX, NavRoutes.toSettingsBottomSheetType(R.id.drawer_dropbox))
        assertEquals(SettingsBottomSheetType.EXPORT, NavRoutes.toSettingsBottomSheetType(R.id.drawer_export))
        assertEquals(SettingsBottomSheetType.ACTIVITY_TYPE, NavRoutes.toSettingsBottomSheetType(R.id.drawer_tracking_layouts))
        assertEquals(SettingsBottomSheetType.UNITS, NavRoutes.toSettingsBottomSheetType(R.id.drawer_units))
        assertEquals(SettingsBottomSheetType.DISPLAY, NavRoutes.toSettingsBottomSheetType(R.id.drawer_display_settings))
        assertEquals(SettingsBottomSheetType.SEARCH, NavRoutes.toSettingsBottomSheetType(R.id.drawer_search_settings))

        // Navigation destinations must return null for bottom sheet type
        assertNull(NavRoutes.toSettingsBottomSheetType(R.id.drawer_start_tracking))
        assertNull(NavRoutes.toSettingsBottomSheetType(R.id.drawer_workouts))
        assertNull(NavRoutes.toSettingsBottomSheetType(R.id.drawer_privacy_policy))
    }

    @Test
    fun testNavigationDrawerControllerSingleActivityState() {
        val controller = NavigationDrawerController()

        // Initial drawer open state is false
        assertFalse(controller.isDrawerOpen)
        assertNull(controller.activeBottomSheet)

        // Open drawer
        controller.openDrawer()
        assertTrue(controller.isDrawerOpen)

        // Close drawer
        controller.closeDrawer()
        assertFalse(controller.isDrawerOpen)

        // Active bottom sheet transitions
        controller.activeBottomSheet = SettingsBottomSheetType.STRAVA
        assertEquals(SettingsBottomSheetType.STRAVA, controller.activeBottomSheet)

        controller.activeBottomSheet = SettingsBottomSheetType.DISPLAY
        assertEquals(SettingsBottomSheetType.DISPLAY, controller.activeBottomSheet)

        controller.activeBottomSheet = null
        assertNull(controller.activeBottomSheet)
    }

    @Test
    fun testMainActivitySingleActivityPublicApiCompliance() {
        val clazz = MainActivityWithNavigation::class.java

        // openDrawer()
        val openDrawerMethod = clazz.getMethod("openDrawer")
        assertNotNull("openDrawer method must be present", openDrawerMethod)
        assertTrue(Modifier.isPublic(openDrawerMethod.modifiers))

        // closeDrawer()
        val closeDrawerMethod = clazz.getMethod("closeDrawer")
        assertNotNull("closeDrawer method must be present", closeDrawerMethod)
        assertTrue(Modifier.isPublic(closeDrawerMethod.modifiers))

        // onActivityTypeSelected(ActivityType)
        val onActivityTypeMethod = clazz.getMethod("onActivityTypeSelected", ActivityType::class.java)
        assertNotNull("onActivityTypeSelected method must be present", onActivityTypeMethod)
        assertTrue(Modifier.isPublic(onActivityTypeMethod.modifiers))

        // navigateToFilteredWorkouts(StatsData)
        val navigateToFilteredMethod = clazz.getMethod("navigateToFilteredWorkouts", StatsData::class.java)
        assertNotNull("navigateToFilteredWorkouts method must be present", navigateToFilteredMethod)
        assertTrue(Modifier.isPublic(navigateToFilteredMethod.modifiers))

        // startWorkoutSummaryListFromPeriod(PeriodSummary, BSportType, Long)
        val startFromPeriodMethod = clazz.getMethod(
            "startWorkoutSummaryListFromPeriod",
            PeriodSummary::class.java,
            BSportType::class.java,
            java.lang.Long::class.javaObjectType
        )
        assertNotNull("startWorkoutSummaryListFromPeriod method must be present", startFromPeriodMethod)
        assertTrue(Modifier.isPublic(startFromPeriodMethod.modifiers))

        // startPairing(Protocol, DeviceType)
        val startPairingMethod = clazz.getMethod(
            "startPairing",
            Protocol::class.java,
            DeviceType::class.java
        )
        assertNotNull("startPairing method must be present", startPairingMethod)
        assertTrue(Modifier.isPublic(startPairingMethod.modifiers))

        // navigateToDrawerItem(Int)
        val navigateMethod = clazz.getMethod("navigateToDrawerItem", Int::class.javaPrimitiveType)
        assertNotNull("navigateToDrawerItem method must be present", navigateMethod)
        assertTrue(Modifier.isPublic(navigateMethod.modifiers))
    }
}
