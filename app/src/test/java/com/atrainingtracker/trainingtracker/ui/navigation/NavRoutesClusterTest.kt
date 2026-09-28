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

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.WorkoutNavigationEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test

/**
 * Unit tests verifying parameterized cluster routes, drawer item resolution,
 * and direct workout-to-cluster navigation events (ATT-1448, REQ-UI-178, TST-UI-130).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavRoutesClusterTest {

    companion object {
        private val testDispatcher = StandardTestDispatcher()

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            Dispatchers.setMain(testDispatcher)
            ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
                override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
                override fun postToMainThread(runnable: Runnable) = runnable.run()
                override fun isMainThread(): Boolean = true
            })
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            ArchTaskExecutor.getInstance().setDelegate(null)
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testLocationsRoute_withoutArguments_returnsBasePath() {
        assertEquals(NavRoutes.LOCATIONS, NavRoutes.locations(null))
        assertEquals(NavRoutes.LOCATIONS, NavRoutes.locations(-1L))
        assertEquals(NavRoutes.LOCATIONS, NavRoutes.locations(0L))
        assertEquals("locations", NavRoutes.LOCATIONS)
    }

    @Test
    fun testLocationsRoute_withValidClusterId_returnsParameterizedQuery() {
        assertEquals("locations?clusterId=42", NavRoutes.locations(42L))
        assertEquals("locations?clusterId=1", NavRoutes.locations(1L))
        assertEquals("locations?clusterId=999999", NavRoutes.locations(999999L))
        assertEquals("locations?clusterId={clusterId}", NavRoutes.LOCATIONS_PATTERN)
        assertEquals("clusterId", NavRoutes.ARG_CLUSTER_ID)
    }

    @Test
    fun testToDrawerItemId_withParameterizedRoutes_correctlyExtractsBaseRoute() {
        // Base route without query parameters
        assertEquals(R.id.drawer_my_locations, NavRoutes.toDrawerItemId("locations"))

        // Parameterized cluster routes
        assertEquals(R.id.drawer_my_locations, NavRoutes.toDrawerItemId("locations?clusterId=42"))
        assertEquals(R.id.drawer_my_locations, NavRoutes.toDrawerItemId("locations?clusterId=1"))
        assertEquals(R.id.drawer_my_locations, NavRoutes.toDrawerItemId("locations?clusterId=100&other=true"))

        // Standard non-parameterized routes
        assertEquals(R.id.drawer_workouts, NavRoutes.toDrawerItemId("workouts"))
        assertEquals(R.id.drawer_periods, NavRoutes.toDrawerItemId("periods"))
        assertEquals(R.id.drawer_start_tracking, NavRoutes.toDrawerItemId("start_tracking"))

        // Edge cases
        assertEquals(R.id.drawer_start_tracking, NavRoutes.toDrawerItemId(null))
        assertEquals(R.id.drawer_start_tracking, NavRoutes.toDrawerItemId("unknown_route"))
    }

    @Test
    fun testWorkoutNavigationEvents_clusterDispatchAndReset() {
        // Initial state or reset
        WorkoutNavigationEvents.resetCluster()
        assertNull(WorkoutNavigationEvents.navigateToCluster.replayCache.firstOrNull())

        // Trigger cluster event
        WorkoutNavigationEvents.triggerCluster(42L)
        assertEquals(42L, WorkoutNavigationEvents.navigateToCluster.replayCache.firstOrNull())

        // Reset cluster event
        WorkoutNavigationEvents.resetCluster()
        assertNull(WorkoutNavigationEvents.navigateToCluster.replayCache.firstOrNull())
    }

    @Test
    fun testMapRoute_parameterizedAndDrawerMapping() {
        assertEquals("map", NavRoutes.map(null))
        assertEquals("map", NavRoutes.map(-1L))
        assertEquals("map", NavRoutes.map(0L))
        assertEquals("map?locationId=42", NavRoutes.map(42L))
        assertEquals("map?locationId=1", NavRoutes.map(1L))
        assertEquals("map?locationId={locationId}", NavRoutes.MAP_PATTERN)
        assertEquals("locationId", NavRoutes.ARG_LOCATION_ID)

        assertEquals(R.id.drawer_map, NavRoutes.toDrawerItemId("map"))
        assertEquals(R.id.drawer_map, NavRoutes.toDrawerItemId("map?locationId=42"))
        assertEquals(R.id.drawer_map, NavRoutes.toDrawerItemId("map?locationId=1&extra=true"))
    }
}
