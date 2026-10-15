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

package com.atrainingtracker.trainingtracker.zones

import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.navigation.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for parameterized zone navigation route building conforming to [TST-UI-337.3] (REQ-UI-374.3).
 */
class NavRoutesZoneTest {

    @Test
    fun testTrainingZonesRoute_defaultReturnsBaseRoute() {
        assertEquals("training_zones", NavRoutes.trainingZones())
        assertEquals("training_zones", NavRoutes.trainingZones(initialTab = 0, promptReason = null))
    }

    @Test
    fun testTrainingZonesRoute_withInitialTabAndPromptReason() {
        assertEquals(
            "training_zones?initialTab=0&promptReason=hr_paired",
            NavRoutes.trainingZones(initialTab = 0, promptReason = "hr_paired")
        )
        assertEquals(
            "training_zones?initialTab=2&promptReason=pwr_paired",
            NavRoutes.trainingZones(initialTab = 2, promptReason = "pwr_paired")
        )
    }

    @Test
    fun testTrainingZonesRoute_withInitialTabOnly() {
        assertEquals(
            "training_zones?initialTab=2",
            NavRoutes.trainingZones(initialTab = 2, promptReason = null)
        )
    }

    @Test
    fun testToDrawerItemId_resolvesParameterizedRouteCorrectly() {
        assertEquals(
            R.id.drawer_training_zones,
            NavRoutes.toDrawerItemId("training_zones?initialTab=2&promptReason=pwr_paired")
        )
        assertEquals(
            R.id.drawer_training_zones,
            NavRoutes.toDrawerItemId("training_zones")
        )
    }
}
