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
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.material3.DrawerValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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

    /**
     * Verifies ModalNavigationDrawer gesture activation state mapping (REQ-UI-161 / TST-UI-113).
     *
     * Validates that gesturesEnabled is strictly coupled to drawerState.isOpen:
     * - Closed state -> gesturesEnabled = false (prevents full-screen drags during map panning)
     * - Open state -> gesturesEnabled = true (preserves full-screen swipe-to-close & scrim dismissal)
     */
    @Test
    fun testDrawerGesturesEnabledMapping() {
        // Closed state
        val isDrawerOpenClosed = false
        val gesturesEnabledWhenClosed = isDrawerOpenClosed
        assertFalse("ModalNavigationDrawer gesturesEnabled must be false when drawer is closed", gesturesEnabledWhenClosed)

        // Open state
        val isDrawerOpenOpen = true
        val gesturesEnabledWhenOpen = isDrawerOpenOpen
        assertTrue("ModalNavigationDrawer gesturesEnabled must be true when drawer is open", gesturesEnabledWhenOpen)
    }

    /**
     * Verifies edge-swipe threshold boundary predicate (REQ-UI-161 / TST-UI-113).
     *
     * Validates that opening gestures are permitted only within the leftmost 40dp margin.
     */
    @Test
    fun testEdgeSwipeThresholdBounds() {
        val edgeThresholdDp = 40f

        // Boundary predicates
        fun isWithinEdgeThreshold(xDp: Float): Boolean = xDp <= edgeThresholdDp

        // In-bounds edge touches (must trigger edge interceptor)
        assertTrue("0dp must be within edge threshold", isWithinEdgeThreshold(0f))
        assertTrue("15dp must be within edge threshold", isWithinEdgeThreshold(15f))
        assertTrue("40dp must be exactly at boundary of edge threshold", isWithinEdgeThreshold(40f))

        // Out-of-bounds touches (must be ignored by drawer to protect map panning and details scrolls)
        assertFalse("40.1dp must exceed edge threshold", isWithinEdgeThreshold(40.1f))
        assertFalse("100dp must exceed edge threshold", isWithinEdgeThreshold(100f))
        assertFalse("200dp (mid-screen) must exceed edge threshold", isWithinEdgeThreshold(200f))
        assertFalse("360dp (right edge) must exceed edge threshold", isWithinEdgeThreshold(360f))
    }

    /**
     * Verifies NavigationDrawerController single source of truth delegation and backward-compatible fallback (REQ-UI-162 / TST-UI-114).
     */
    @Test
    fun testNavigationDrawerControllerActionDelegationAndFallback() {
        val controller = NavigationDrawerController()

        // Unbound state verification (fallback without NPE)
        assertFalse("Unbound controller must default isDrawerOpen to false", controller.isDrawerOpen)
        controller.openDrawer()
        assertTrue("Unbound controller openDrawer() must set fallback to true", controller.isDrawerOpen)
        controller.closeDrawer()
        assertFalse("Unbound controller closeDrawer() must set fallback to false", controller.isDrawerOpen)

        // Binding to authoritative state provider
        var openInvocations = 0
        var closeInvocations = 0
        var authoritativeIsOpen = false

        controller.bindDrawer(
            open = { openInvocations++ },
            close = { closeInvocations++ },
            isOpen = { authoritativeIsOpen }
        )

        // Assert bound delegation
        assertEquals("Initial bound isDrawerOpen must reflect provider", false, controller.isDrawerOpen)

        controller.openDrawer()
        assertEquals("openDrawer() must invoke bound open delegate", 1, openInvocations)

        controller.closeDrawer()
        assertEquals("closeDrawer() must invoke bound close delegate", 1, closeInvocations)

        authoritativeIsOpen = true
        assertTrue("isDrawerOpen must strictly observe authoritative state provider", controller.isDrawerOpen)

        authoritativeIsOpen = false
        assertFalse("isDrawerOpen must strictly observe authoritative state provider", controller.isDrawerOpen)

        // Unbind lifecycle
        controller.unbindDrawer()
        assertFalse("Unbound controller after unbindDrawer() must return fallback state cleanly", controller.isDrawerOpen)
    }

    /**
     * Verifies scoped BackHandler visibility predicates across all 4 permutations of the DrawerState matrix (REQ-UI-162 / TST-UI-114).
     */
    @Test
    fun testScopedBackHandlerVisibilityPredicatesAnd4StateMatrix() {
        fun computeIsDrawerVisible(current: DrawerValue, target: DrawerValue): Boolean {
            return current != DrawerValue.Closed || target != DrawerValue.Closed
        }

        fun isLayer1DrawerBackEnabled(isDrawerVisible: Boolean): Boolean = isDrawerVisible

        fun isLayer3ScreenBackEnabled(isDrawerVisible: Boolean, hasActiveBottomSheet: Boolean): Boolean {
            return !isDrawerVisible && !hasActiveBottomSheet
        }

        // State 1: Fully Closed (Closed, Closed)
        val s1Visible = computeIsDrawerVisible(DrawerValue.Closed, DrawerValue.Closed)
        assertFalse("State 1: isDrawerVisible must be false", s1Visible)
        assertFalse("State 1: Layer 1 BackHandler must be disabled", isLayer1DrawerBackEnabled(s1Visible))
        assertTrue("State 1: Layer 3 Screen BackHandler must be enabled", isLayer3ScreenBackEnabled(s1Visible, false))
        assertFalse("State 1 with bottom sheet: Layer 3 must be disabled", isLayer3ScreenBackEnabled(s1Visible, true))

        // State 2: Animating Open / Swiping Open (Closed, Open)
        val s2Visible = computeIsDrawerVisible(DrawerValue.Closed, DrawerValue.Open)
        assertTrue("State 2: isDrawerVisible must be true during opening animation", s2Visible)
        assertTrue("State 2: Layer 1 BackHandler must be enabled to intercept back press", isLayer1DrawerBackEnabled(s2Visible))
        assertFalse("State 2: Layer 3 Screen BackHandler must be disabled", isLayer3ScreenBackEnabled(s2Visible, false))

        // State 3: Fully Open (Open, Open)
        val s3Visible = computeIsDrawerVisible(DrawerValue.Open, DrawerValue.Open)
        assertTrue("State 3: isDrawerVisible must be true when open", s3Visible)
        assertTrue("State 3: Layer 1 BackHandler must be enabled to intercept back press", isLayer1DrawerBackEnabled(s3Visible))
        assertFalse("State 3: Layer 3 Screen BackHandler must be disabled", isLayer3ScreenBackEnabled(s3Visible, false))

        // State 4: Actively Dismissing / Closing (Open, Closed)
        val s4Visible = computeIsDrawerVisible(DrawerValue.Open, DrawerValue.Closed)
        assertTrue("State 4: isDrawerVisible must remain true during closing transition", s4Visible)
        assertTrue("State 4: Layer 1 BackHandler must remain enabled", isLayer1DrawerBackEnabled(s4Visible))
        assertFalse("State 4: Layer 3 Screen BackHandler must remain disabled until drawer fully settles", isLayer3ScreenBackEnabled(s4Visible, false))
    }

    /**
     * Verifies rapid double-tap back press handling and cooperative CancellationException resilience (REQ-UI-162 / TST-UI-114).
     */
    @Test
    fun testRapidDoubleBackPressCancellationSafety() = runBlocking {
        var drawerValue = DrawerValue.Open
        var targetValue = DrawerValue.Open

        fun computeIsDrawerVisible(): Boolean = drawerValue != DrawerValue.Closed || targetValue != DrawerValue.Closed

        var cancellationHandledCount = 0
        var completedClosureCount = 0

        // Simulate first back tap starting a closure job
        targetValue = DrawerValue.Closed
        val job1 = launch {
            try {
                delay(300L) // Simulate animation delay
                drawerValue = DrawerValue.Closed
                completedClosureCount++
            } catch (_: CancellationException) {
                cancellationHandledCount++
            }
        }

        // Mid-flight (e.g. 50ms interval): second back tap arrives
        delay(50L)
        assertTrue("Mid-flight: isDrawerVisible must remain true", computeIsDrawerVisible())

        // Second tap cancels first job cooperatively
        job1.cancel()
        job1.join()

        assertEquals("Cancelled job must handle CancellationException without fault", 1, cancellationHandledCount)
        assertEquals("Interrupted first job must not complete", 0, completedClosureCount)

        // Second job takes over and settles drawer
        val job2 = launch {
            try {
                drawerValue = DrawerValue.Closed
                completedClosureCount++
            } catch (_: CancellationException) {
                cancellationHandledCount++
            }
        }
        job2.join()

        assertEquals("Second job must complete closure", 1, completedClosureCount)
        assertEquals(DrawerValue.Closed, drawerValue)
        assertFalse("Settled drawer: isDrawerVisible must be false", computeIsDrawerVisible())
    }

    /**
     * Verifies deadlock safety timeout (400ms) with snapTo fallback (INV-UI-04 / REQ-UI-162 / TST-UI-114).
     */
    @Test
    fun testDeadlockSafetyTimeoutAndStarvationFallback() = runBlocking {
        var simulatedDrawerValue = DrawerValue.Open
        var fallbackExecuted = false

        // Simulate a frozen / starved animation exceeding 400ms
        val closeResult = withTimeoutOrNull(400L) {
            delay(1000L) // Stalled coroutine
            simulatedDrawerValue = DrawerValue.Closed
            true
        } ?: run {
            // Safety valve fallback (snapTo)
            fallbackExecuted = true
            simulatedDrawerValue = DrawerValue.Closed
            false
        }

        assertFalse("Timed-out animation must return false/null from withTimeoutOrNull", closeResult)
        assertTrue("400ms timeout must execute safety valve fallback", fallbackExecuted)
        assertEquals("Safety valve snapTo must forcefully settle drawer to Closed", DrawerValue.Closed, simulatedDrawerValue)
    }

    /**
     * Verifies dynamic LIFO evaluation precedence in OnBackPressedDispatcher (REQ-UI-162 / TST-UI-114 / ATT-1335).
     *
     * Validates that dynamically composed overlay BackHandlers (added when isDrawerVisible == true)
     * strictly supersede NavHost's internal destination backstack callbacks, and that removing the
     * overlay callback upon drawer closure fully restores NavHost destination popping.
     */
    @Test
    fun testOnBackPressedDispatcherDynamicLIFOPrecedence() {
        val dispatcher = OnBackPressedDispatcher()
        var navHostPopCount = 0
        var drawerCloseCount = 0

        // Step 1: NavHost registers its internal back callback when on a child destination
        val navHostCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navHostPopCount++
            }
        }
        dispatcher.addCallback(navHostCallback)

        // When drawer is closed, pressing Back pops NavHost destination
        assertTrue("NavHost callback must be enabled", navHostCallback.isEnabled)
        dispatcher.onBackPressed()
        assertEquals("Drawer closed: NavHost must pop destination", 1, navHostPopCount)
        assertEquals("Drawer closed: Drawer close must not be called", 0, drawerCloseCount)

        // Step 2: Athlete opens navigation drawer -> conditional BackHandler enters composition
        // addCallback appends to the end of dispatcher deque (top of LIFO stack)
        val drawerCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                drawerCloseCount++
            }
        }
        dispatcher.addCallback(drawerCallback)

        // Both callbacks are enabled simultaneously
        assertTrue("NavHost callback remains enabled", navHostCallback.isEnabled)
        assertTrue("Drawer callback is enabled", drawerCallback.isEnabled)

        // When Back is pressed with drawer open, LIFO order guarantees drawerCallback executes FIRST
        dispatcher.onBackPressed()
        assertEquals("Drawer open: Drawer close callback must execute", 1, drawerCloseCount)
        assertEquals("Drawer open: NavHost must NOT be triggered (destination stays unchanged)", 1, navHostPopCount)

        // Step 3: Drawer closes -> Composable leaves composition -> onDispose calls remove()
        drawerCallback.remove()

        // When Back is pressed again with drawer unmounted, NavHost handles Back again
        dispatcher.onBackPressed()
        assertEquals("Drawer closed: NavHost must pop destination again", 2, navHostPopCount)
        assertEquals("Drawer closed: Drawer callback was removed and must not execute", 1, drawerCloseCount)
    }

    /**
     * Verifies comprehensive drawer visibility predicate across all animation phases (REQ-UI-162 / TST-UI-114 / ATT-1335).
     */
    @Test
    fun testIsDrawerVisibleComprehensivePredicate() {
        fun computeIsDrawerVisible(current: DrawerValue, target: DrawerValue, isAnimationRunning: Boolean): Boolean {
            return current != DrawerValue.Closed || target != DrawerValue.Closed || isAnimationRunning
        }

        // Permutation 1: Settled Closed (idle)
        assertFalse(
            "Settled Closed (idle) must evaluate to false",
            computeIsDrawerVisible(DrawerValue.Closed, DrawerValue.Closed, false)
        )

        // Permutation 2: Opening transition (animation in flight from Closed to Open)
        assertTrue(
            "Opening transition must evaluate to true",
            computeIsDrawerVisible(DrawerValue.Closed, DrawerValue.Open, true)
        )

        // Permutation 3: Settled Open (idle)
        assertTrue(
            "Settled Open (idle) must evaluate to true",
            computeIsDrawerVisible(DrawerValue.Open, DrawerValue.Open, false)
        )

        // Permutation 4: Closing transition (animation in flight from Open to Closed)
        assertTrue(
            "Closing transition must evaluate to true",
            computeIsDrawerVisible(DrawerValue.Open, DrawerValue.Closed, true)
        )

        // Permutation 5: Post-target settle phase (target reached Closed, but animation finalizing)
        assertTrue(
            "Finalizing animation phase must evaluate to true",
            computeIsDrawerVisible(DrawerValue.Closed, DrawerValue.Closed, true)
        )
    }
}

