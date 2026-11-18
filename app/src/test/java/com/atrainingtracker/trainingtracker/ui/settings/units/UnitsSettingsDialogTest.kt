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

package com.atrainingtracker.trainingtracker.ui.settings.units

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.navigation.NavRoutes
import com.atrainingtracker.trainingtracker.ui.navigation.SettingsBottomSheetType
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Behavioral Robolectric Compose UI tests for [UnitsSettingsDialog] and drawer routing.
 *
 * Linked test specs: TST-NAV-005
 */
// Linked test specs: TST-NAV-005
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h2200dp-xhdpi")
class UnitsSettingsDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    private fun setTrainingAppStaticField(fieldName: String, value: Any?) {
        val field: Field = TrainingApplication::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(null, value)
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().putString(TrainingApplication.SP_UNITS, MyUnits.METRIC.name).commit()
        setTrainingAppStaticField("cAppContext", context)
        setTrainingAppStaticField("cSharedPreferences", prefs)
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
        setTrainingAppStaticField("cSharedPreferences", null)
        setTrainingAppStaticField("cAppContext", null)
    }

    /**
     * TST-NAV-005.1: Verify drawer_units routes to UNITS sheet and Save persists IMPERIAL selection.
     */
    @Test
    fun testUnitsDialog_savePersistsSelection() {
        assertNull(NavRoutes.fromDrawerItemId(R.id.drawer_units))
        assertEquals(SettingsBottomSheetType.UNITS, NavRoutes.toBottomSheetType(R.id.drawer_units))

        var dismissCount = 0

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    UnitsSettingsDialog(onDismiss = { dismissCount++ })
                }
            }
        }

        val imperialLabel = context.getString(MyUnits.IMPERIAL.nameId)
        val metricLabel = context.getString(MyUnits.METRIC.nameId)
        val saveLabel = context.getString(R.string.save)

        composeTestRule.onNodeWithText(metricLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(imperialLabel).assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText(saveLabel).performClick()

        assertEquals(1, dismissCount)
        assertEquals(MyUnits.IMPERIAL.name, prefs.getString(TrainingApplication.SP_UNITS, null))
    }

    /**
     * TST-NAV-005.2: Verify Cancel dismisses without overwriting the persisted unit preference.
     */
    @Test
    fun testUnitsDialog_cancelDiscardsChanges() {
        prefs.edit().putString(TrainingApplication.SP_UNITS, MyUnits.IMPERIAL.name).commit()
        var dismissCount = 0

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    UnitsSettingsDialog(onDismiss = { dismissCount++ })
                }
            }
        }

        val metricLabel = context.getString(MyUnits.METRIC.nameId)
        val cancelLabel = context.getString(R.string.cancel)

        composeTestRule.onNodeWithText(metricLabel).performClick()
        composeTestRule.onNodeWithText(cancelLabel).performClick()

        assertTrue("Cancel must invoke onDismiss", dismissCount == 1)
        assertEquals(
            "Cancel must not overwrite persisted IMPERIAL unit",
            MyUnits.IMPERIAL.name,
            prefs.getString(TrainingApplication.SP_UNITS, null)
        )
    }
}
