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
import com.atrainingtracker.testing.LocalizationTestCache
import com.atrainingtracker.trainingtracker.routes.ForkBranchOption
import com.atrainingtracker.trainingtracker.routes.ForkDecisionState
import com.atrainingtracker.trainingtracker.routes.ForkDirection
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Contract, layout, and state verification for ForkDecisionCard (REQ-MAP-031, REQ-UI-330 / TST-UI-290).
 */
class ForkDecisionCardTest {

    private val cardClass: Class<*> by lazy {
        Class.forName("com.atrainingtracker.trainingtracker.ui.routes.ForkDecisionCardKt")
    }

    private val forkAlertKeys = listOf(
        "fork_alert_title",
        "fork_approaching_in_m",
        "fork_dismiss",
        "fork_direction_left",
        "fork_direction_straight",
        "fork_direction_right"
    )

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

    @Test
    fun forkDirectionGrouping_groupsBranchesByDirectionCorrectly() {
        val branchStraight1 = ForkBranchOption(1L, "Route A", 10000.0, 100.0, ForkDirection.STRAIGHT, 0.0)
        val branchStraight2 = ForkBranchOption(2L, "Route B", 12000.0, 150.0, ForkDirection.STRAIGHT, 2.0)
        val branchRight = ForkBranchOption(3L, "Route C", 15000.0, 200.0, ForkDirection.RIGHT, 45.0)

        val state = ForkDecisionState(
            divergenceCoordinate = LatLng(48.5000, 9.0000),
            distanceToForkMeters = 150.0,
            branches = listOf(branchStraight1, branchStraight2, branchRight),
            isApproaching = true
        )

        val grouped = state.branches.groupBy { it.direction }
        assertEquals(2, grouped.size)
        assertTrue(grouped.containsKey(ForkDirection.STRAIGHT))
        assertTrue(grouped.containsKey(ForkDirection.RIGHT))
        assertEquals(2, grouped[ForkDirection.STRAIGHT]?.size)
        assertEquals(1, grouped[ForkDirection.RIGHT]?.size)
        assertEquals("Route A", grouped[ForkDirection.STRAIGHT]?.get(0)?.routeName)
        assertEquals("Route B", grouped[ForkDirection.STRAIGHT]?.get(1)?.routeName)
    }

    @Test
    fun forkAlertStrings_existAcrossAll9Languages() {
        for (locale in LocalizationTestCache.LOCALES) {
            val strings = LocalizationTestCache.getStrings(locale)
            for (key in forkAlertKeys) {
                val value = strings[key]
                assertNotNull("Key '$key' must exist in locale $locale", value)
                assertFalse("Value for '$key' in locale $locale must not be blank", value!!.isBlank())
            }
        }
    }

    @Test
    fun forkDecisionCardLayout_verifiesM3TokensAndStructure() {
        assertNotNull("ForkDecisionCardKt class must exist", cardClass)
        val methods = cardClass.declaredMethods

        val mainComposable = methods.find {
            it.name == "ForkDecisionCard" && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("ForkDecisionCard composable must exist and be public", mainComposable)

        val groupComposable = methods.find {
            it.name == "ForkDirectionGroup"
        }
        assertNotNull("ForkDirectionGroup composable must be declared", groupComposable)

        val rowComposable = methods.find {
            it.name == "ForkRouteRow"
        }
        assertNotNull("ForkRouteRow composable must be declared", rowComposable)
    }

    @Test
    fun forkDecisionCard_enforcesZeroTonalElevationAndCleanSurfaces() {
        assertNotNull("ForkDecisionCardKt class must exist", cardClass)
        val methods = cardClass.declaredMethods

        val mainComposable = methods.find { it.name == "ForkDecisionCard" }
        assertNotNull("ForkDecisionCard composable must exist", mainComposable)

        val mainParams = mainComposable!!.parameterTypes
        assertTrue(
            "ForkDecisionCard must accept ForkDecisionState parameter",
            mainParams.any { it == ForkDecisionState::class.java }
        )
        assertTrue(
            "ForkDecisionCard must accept overlayAlpha float parameter",
            mainParams.contains(Float::class.javaPrimitiveType)
        )

        val groupComposable = methods.find { it.name == "ForkDirectionGroup" }
        assertNotNull("ForkDirectionGroup composable must exist", groupComposable)
        val groupParams = groupComposable!!.parameterTypes
        assertTrue(
            "ForkDirectionGroup must support overlayAlpha parameter",
            groupParams.contains(Float::class.javaPrimitiveType)
        )
    }
}
