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

package com.atrainingtracker.trainingtracker.ui.routes

import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.routes.ForkBranchOption
import com.atrainingtracker.trainingtracker.routes.ForkDecisionState
import com.atrainingtracker.trainingtracker.routes.ForkDirection
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract and state verification for ForkDecisionCard (REQ-MAP-031 / TST-MAP-033.4).
 */
class ForkDecisionCardTest {

    @Test
    fun forkDirection_hasValidStringResources() {
        assertEquals(R.string.fork_direction_left, ForkDirection.LEFT.labelRes)
        assertEquals(R.string.fork_direction_straight, ForkDirection.STRAIGHT.labelRes)
        assertEquals(R.string.fork_direction_right, ForkDirection.RIGHT.labelRes)
    }

    @Test
    fun forkDecisionState_containsAllBranchDetails() {
        val branchA = ForkBranchOption(
            routeId = 101L,
            routeName = "Waldenbuch Scenic Loop",
            totalDistanceMeters = 38400.0,
            totalElevationMeters = 420.0,
            direction = ForkDirection.LEFT,
            bearingDiffDegrees = -35.0
        )
        val branchB = ForkBranchOption(
            routeId = 102L,
            routeName = "Dettenhausen Fast Ridge",
            totalDistanceMeters = 54200.0,
            totalElevationMeters = 680.0,
            direction = ForkDirection.RIGHT,
            bearingDiffDegrees = 40.0
        )

        val state = ForkDecisionState(
            divergenceCoordinate = LatLng(48.5100, 9.0000),
            distanceToForkMeters = 245.0,
            branches = listOf(branchA, branchB),
            isApproaching = true
        )

        assertNotNull(state)
        assertEquals(2, state.branches.size)
        assertEquals(245.0, state.distanceToForkMeters, 0.1)
        assertTrue(state.isApproaching)

        val firstBranch = state.branches[0]
        assertEquals(101L, firstBranch.routeId)
        assertEquals("Waldenbuch Scenic Loop", firstBranch.routeName)
        assertEquals(38400.0, firstBranch.totalDistanceMeters, 0.1)
        assertEquals(420.0, firstBranch.totalElevationMeters, 0.1)
        assertEquals(ForkDirection.LEFT, firstBranch.direction)

        val secondBranch = state.branches[1]
        assertEquals(102L, secondBranch.routeId)
        assertEquals("Dettenhausen Fast Ridge", secondBranch.routeName)
        assertEquals(ForkDirection.RIGHT, secondBranch.direction)
    }
}
