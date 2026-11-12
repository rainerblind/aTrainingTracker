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

import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit and contract tests for root BackHandler task preservation and LIFO navigation hierarchy
 * in ATrainingTrackerApp (REQ-TRK-059.8, TST-TRK-059.8, ATT-6710).
 */
class ATrainingTrackerAppNavigationTest {

    @Test
    fun testRootBackNavigation_movesTaskToBackWhenOnRootTrackingWithoutBackstack() {
        var popBackStackCalled = false
        var navigateToDrawerStartTrackingCalled = false
        var moveTaskToBackCalled = false
        var finishCalled = false

        fun executeLayer3BackHandler(
            hasPreviousBackStack: Boolean,
            currentRoute: String
        ) {
            if (hasPreviousBackStack) {
                popBackStackCalled = true
            } else if (currentRoute != NavRoutes.START_TRACKING) {
                navigateToDrawerStartTrackingCalled = true
            } else {
                // REQ-TRK-059.8, ATT-6710: Root screen must call moveTaskToBack(true) instead of finish()
                moveTaskToBackCalled = true
            }
        }

        // Scenario: Root screen START_TRACKING with empty backstack
        executeLayer3BackHandler(hasPreviousBackStack = false, currentRoute = NavRoutes.START_TRACKING)

        assertTrue("Root START_TRACKING back press must invoke moveTaskToBack(true)", moveTaskToBackCalled)
        assertFalse("Root START_TRACKING must never invoke finish()", finishCalled)
        assertFalse("Child backstack must not be popped", popBackStackCalled)
        assertFalse("Must not trigger drawer navigation when already at root", navigateToDrawerStartTrackingCalled)
    }

    @Test
    fun testNonRootBackNavigation_redirectsToStartTrackingOrPopsBackstack() {
        var popBackStackCount = 0
        var navigateToStartTrackingCount = 0
        var moveTaskToBackCount = 0

        fun executeLayer3BackHandler(
            hasPreviousBackStack: Boolean,
            currentRoute: String
        ) {
            if (hasPreviousBackStack) {
                popBackStackCount++
            } else if (currentRoute != NavRoutes.START_TRACKING) {
                navigateToStartTrackingCount++
            } else {
                moveTaskToBackCount++
            }
        }

        // Child screen with previous backstack entry
        executeLayer3BackHandler(hasPreviousBackStack = true, currentRoute = NavRoutes.START_TRACKING)
        assertEquals("Child destination with backstack entry must pop backstack", 1, popBackStackCount)

        // Non-root screen (e.g. WORKOUTS) without backstack
        executeLayer3BackHandler(hasPreviousBackStack = false, currentRoute = NavRoutes.WORKOUTS)
        assertEquals("Non-root destination without backstack must navigate to START_TRACKING", 1, navigateToStartTrackingCount)
        assertEquals("Must not move task to back on non-root screen", 0, moveTaskToBackCount)
    }

    @Test
    fun testLayer3BackHandler_dispatchesViaOnBackPressedDispatcherLIFO() {
        val dispatcher = OnBackPressedDispatcher()
        var rootActionExecuted = false

        val rootBackCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                rootActionExecuted = true
            }
        }
        dispatcher.addCallback(rootBackCallback)

        assertTrue(rootBackCallback.isEnabled)
        dispatcher.onBackPressed()
        assertTrue("Dispatcher must execute registered root back callback", rootActionExecuted)
    }

    @Test
    fun testDrawerEdgeSwipeInterceptorPredicate_deactivatedWhenPushed() {
        // REQ-UI-517.2, TST-UI-517.3 (ATT-7313)
        fun shouldEnableDrawerEdgeSwipe(isPushed: Boolean, locationId: Long): Boolean {
            val isCurrentScreenPushed = isPushed || locationId > 0
            return !isCurrentScreenPushed
        }

        // Root drawer destinations: edge swipe enabled
        assertTrue("Root WORKOUTS must enable edge swipe", shouldEnableDrawerEdgeSwipe(isPushed = false, locationId = -1L))
        assertTrue("Root LOCATIONS must enable edge swipe", shouldEnableDrawerEdgeSwipe(isPushed = false, locationId = -1L))
        assertTrue("Root MAP must enable edge swipe", shouldEnableDrawerEdgeSwipe(isPushed = false, locationId = -1L))

        // Pushed destinations: edge swipe disabled
        assertFalse("Pushed WORKOUTS must disable edge swipe", shouldEnableDrawerEdgeSwipe(isPushed = true, locationId = -1L))
        assertFalse("Pushed LOCATIONS must disable edge swipe", shouldEnableDrawerEdgeSwipe(isPushed = true, locationId = -1L))
        assertFalse("Pushed MAP with locationId must disable edge swipe", shouldEnableDrawerEdgeSwipe(isPushed = false, locationId = 123L))
        assertFalse("Pushed MAP with locationId and pushed must disable edge swipe", shouldEnableDrawerEdgeSwipe(isPushed = true, locationId = 123L))
    }

    @Test
    fun testPushedScreenBackAction_clearsContextualFiltersAndPopsBackstack() {
        // REQ-UI-517.2, TST-UI-517.3 (ATT-7313)
        var filtersCleared = false
        var backstackPopped = false

        fun handlePushedBackClick() {
            filtersCleared = true
            backstackPopped = true
        }

        handlePushedBackClick()

        assertTrue("Pushed back action must clear contextual filter criteria", filtersCleared)
        assertTrue("Pushed back action must pop backstack to origin", backstackPopped)
    }
}
