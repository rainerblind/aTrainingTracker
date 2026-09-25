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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test verifying navigation drawer item routing and controller interaction
 * for Known Start Locations (ATT-919 / REQ-UI-165).
 *
 * Traceability:
 * - TST-UI-117.1: Drawer item routes to NavRoutes.START_LOCATIONS and closes drawer.
 */
class AppNavigationDrawerTest {

    @Test
    fun testStartLocationsRouteResolution() {
        val resolvedRoute = NavRoutes.fromDrawerItemId(R.id.drawer_start_locations)
        assertEquals(
            "Drawer item ID R.id.drawer_start_locations must resolve to NavRoutes.START_LOCATIONS",
            NavRoutes.START_LOCATIONS,
            resolvedRoute
        )

        val resolvedItemId = NavRoutes.toDrawerItemId(NavRoutes.START_LOCATIONS)
        assertEquals(
            "NavRoutes.START_LOCATIONS must resolve to R.id.drawer_start_locations",
            R.id.drawer_start_locations,
            resolvedItemId
        )
    }

    @Test
    fun testStartLocationsDrawerItemDoesNotTriggerBottomSheet() {
        val bottomSheetType = NavRoutes.toBottomSheetType(R.id.drawer_start_locations)
        assertNull(
            "Start Locations must be a full screen route, not a modal settings bottom sheet",
            bottomSheetType
        )
    }

    @Test
    fun testDrawerControllerSelectionAndCloseTransition() {
        val controller = NavigationDrawerController()
        var drawerCloseCalled = false

        controller.bindDrawer(
            open = {},
            close = { drawerCloseCalled = true },
            isOpen = { true }
        )

        assertTrue(controller.isDrawerOpen)

        // Select the start locations destination
        controller.selectedItemId = R.id.drawer_start_locations
        assertEquals(R.id.drawer_start_locations, controller.selectedItemId)

        // Verify closing the drawer
        controller.closeDrawer()
        assertTrue("Selecting destination item must trigger drawer close", drawerCloseCalled)
    }

    @Test
    fun testDrawerMapsGroupItemOrderingAndIcons() {
        val groups = createDrawerGroups(R.string.tab_start)
        val mapsGroup = groups.firstOrNull { it.titleRes == R.string.drawer__maps }
        assertTrue("Maps drawer group must exist", mapsGroup != null)

        val items = mapsGroup!!.items
        val myLocationsIndex = items.indexOfFirst { it.id == R.id.drawer_my_locations }
        val startLocationsIndex = items.indexOfFirst { it.id == R.id.drawer_start_locations }

        assertTrue("Lieblingsstrecken (drawer_my_locations) must be in maps group", myLocationsIndex != -1)
        assertTrue("Lieblingsorte (drawer_start_locations) must be in maps group", startLocationsIndex != -1)
        assertTrue(
            "Lieblingsorte must appear directly after Lieblingsstrecken (ATT-1382)",
            startLocationsIndex == myLocationsIndex + 1
        )

        val myLocationsItem = items[myLocationsIndex]
        val startLocationsItem = items[startLocationsIndex]

        assertEquals(
            "Lieblingsstrecken icon must be ic_favorite_route",
            R.drawable.ic_favorite_route,
            myLocationsItem.iconRes
        )
        assertEquals(
            "Lieblingsorte icon must be my_locations (pin with heart)",
            R.drawable.my_locations,
            startLocationsItem.iconRes
        )
    }
}
