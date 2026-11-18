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

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.settings.trackingtabs.ActivityTypeSelectionDialog
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.reflect.Field

/**
 * Behavioral Robolectric & Compose UI tests for Navigation Drawer structure, EN/DE localization,
 * and direct Tracking Tabs ActivityType selection dialog routing.
 *
 * Linked test specs: TST-NAV-001, TST-NAV-002, TST-NAV-003
 */
// Linked test specs: TST-NAV-001, TST-NAV-002, TST-NAV-003
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h2200dp-xhdpi")
class NavigationDrawerAndDialogContractTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    private fun setTrainingAppStaticField(fieldName: String, value: Any?) {
        val field: Field = TrainingApplication::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(null, value)
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        setTrainingAppStaticField("cAppContext", context)
        setTrainingAppStaticField("cSharedPreferences", prefs)
    }

    @After
    fun tearDown() {
        setTrainingAppStaticField("cSharedPreferences", null)
        setTrainingAppStaticField("cAppContext", null)
    }

    /**
     * TST-NAV-001.1: Verify createDrawerGroups() returns all 6 canonical sections and expected placements.
     */
    @Test
    fun testDrawerStructureAndGroupPlacement() {
        val groups = createDrawerGroups(R.string.tab_start, isDebug = false)
        assertEquals(
            listOf(
                R.string.drawer__training,
                R.string.drawer__maps,
                R.string.drawer__my_stuff,
                R.string.prefsOnlineCommunities,
                R.string.drawer__settings,
                R.string.drawer__info_support
            ),
            groups.map { it.titleRes }
        )

        val myStuff = groups.first { it.titleRes == R.string.drawer__my_stuff }
        assertTrue(myStuff.items.any { it.id == R.id.drawer_my_sensors })
        assertTrue(myStuff.items.any { it.id == R.id.drawer_equipment })

        val sync = groups.first { it.titleRes == R.string.prefsOnlineCommunities }
        assertTrue(sync.items.any { it.id == R.id.drawer_strava })
        assertTrue(sync.items.any { it.id == R.id.drawer_dropbox })
        assertTrue(sync.items.any { it.id == R.id.drawer_export })

        val settings = groups.first { it.titleRes == R.string.drawer__settings }
        assertTrue(settings.items.any { it.id == R.id.drawer_sport_types })
        assertTrue(settings.items.any { it.id == R.id.drawer_training_zones })
        assertTrue(settings.items.any { it.id == R.id.drawer_tracking_layouts })
        assertTrue(settings.items.any { it.id == R.id.drawer_units })
    }

    /**
     * TST-NAV-001.2: Render AppNavigationDrawer, expand collapsible groups, and verify click dispatch.
     */
    @Test
    fun testDrawerExpansionAndSelectionDispatch() {
        val selectedIds = mutableListOf<Int>()

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                AppNavigationDrawer(
                    selectedItemId = R.id.drawer_start_tracking,
                    startTrackingTitleRes = R.string.tab_start,
                    onItemSelected = { selectedIds.add(it) }
                )
            }
        }

        // Primary sections are expanded by default
        composeTestRule.onNodeWithText(context.getString(R.string.drawer__training)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.drawer__maps)).assertIsDisplayed()

        // Expand Synchronization and tap Strava + Export
        composeTestRule.onNodeWithText(context.getString(R.string.prefsOnlineCommunities))
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.Strava))
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.prefsExportTitle))
            .performScrollTo()
            .performClick()

        // Expand Settings and tap Sport Types
        composeTestRule.onNodeWithText(context.getString(R.string.drawer__settings))
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.sport_types))
            .performScrollTo()
            .performClick()

        assertEquals(
            listOf(R.id.drawer_strava, R.id.drawer_export, R.id.drawer_sport_types),
            selectedIds
        )
    }

    /**
     * TST-NAV-002.1 (EN): Verify rendered drawer terminology in English ("Equipment", "Synchronization", "Export").
     */
    @Test
    @Config(sdk = [34], qualifiers = "en-w412dp-h2200dp-xhdpi")
    fun testDrawerTerminologyLocalization_en() {
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                AppNavigationDrawer(
                    selectedItemId = R.id.drawer_start_tracking,
                    startTrackingTitleRes = R.string.tab_start,
                    onItemSelected = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Equipment").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Synchronization")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithText("Export").performScrollTo().assertIsDisplayed()
    }

    /**
     * TST-NAV-002.1 (DE): Verify rendered drawer terminology in German ("Ausrüstung", "Synchronisation", "Export").
     */
    @Test
    @Config(sdk = [34], qualifiers = "de-w412dp-h2200dp-xhdpi")
    fun testDrawerTerminologyLocalization_de() {
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                AppNavigationDrawer(
                    selectedItemId = R.id.drawer_start_tracking,
                    startTrackingTitleRes = R.string.tab_start,
                    onItemSelected = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Ausrüstung").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Synchronisation")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithText("Export").performScrollTo().assertIsDisplayed()
    }

    /**
     * TST-NAV-003.1: Verify drawer_tracking_layouts maps to ACTIVITY_TYPE sheet and ActivityTypeSelectionDialog dispatches selection.
     */
    @Test
    fun testTrackingTabsRouteAndActivityTypeSelectionDialog() {
        assertNull(NavRoutes.fromDrawerItemId(R.id.drawer_tracking_layouts))
        assertEquals(
            SettingsBottomSheetType.ACTIVITY_TYPE,
            NavRoutes.toBottomSheetType(R.id.drawer_tracking_layouts)
        )

        var chosenType: ActivityType? = null
        var dismissed = false

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    ActivityTypeSelectionDialog(
                        onTypeSelected = { chosenType = it },
                        onDismiss = { dismissed = true }
                    )
                }
            }
        }

        val expectedType = ActivityType.RUN_SPEED_AND_CADENCE
        val label = context.getString(expectedType.titleId)
        composeTestRule.onNodeWithText(label)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertNotNull(chosenType)
        assertEquals(expectedType, chosenType)
        assertTrue("ActivityTypeSelectionDialog must invoke onDismiss after selection", dismissed)
    }
}
