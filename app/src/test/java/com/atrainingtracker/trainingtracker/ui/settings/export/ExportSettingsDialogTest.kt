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

package com.atrainingtracker.trainingtracker.ui.settings.export

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.navigation.NavRoutes
import com.atrainingtracker.trainingtracker.ui.navigation.SettingsBottomSheetType
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Behavioral Robolectric Compose UI tests for [ExportSettingsDialog] and drawer routing.
 *
 * Linked test specs: TST-NAV-006
 */
// Linked test specs: TST-NAV-006
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h2200dp-xhdpi")
class ExportSettingsDialogTest {

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
        prefs.edit().clear().commit()
        setTrainingAppStaticField("cAppContext", context)
        setTrainingAppStaticField("cSharedPreferences", prefs)
        TrainingApplication.setExportToTCX(true)
        TrainingApplication.setExportToGPX(false)
        TrainingApplication.setExportToGCJson(false)
        TrainingApplication.setExportToCSV(false)
        TrainingApplication.setExportToFIT(false)
        TrainingApplication.setExportToRawSensor(false)
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
        setTrainingAppStaticField("cSharedPreferences", null)
        setTrainingAppStaticField("cAppContext", null)
    }

    /**
     * TST-NAV-006.1: Verify drawer_export routes to EXPORT sheet and Save persists toggled formats.
     */
    @Test
    fun testExportDialog_savePersistsToggles() {
        assertNull(NavRoutes.fromDrawerItemId(R.id.drawer_export))
        assertEquals(SettingsBottomSheetType.EXPORT, NavRoutes.toBottomSheetType(R.id.drawer_export))

        var dismissCount = 0

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    ExportSettingsDialog(onDismiss = { dismissCount++ })
                }
            }
        }

        val rawSensorLabel = context.getString(R.string.file_format_raw_sensor)
        val saveLabel = context.getString(R.string.save)

        listOf("TCX", "GPX", "Golden Cheetah JSON", "CSV", "FIT", rawSensorLabel).forEach { label ->
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }

        // Toggle GPX (index 1) ON and FIT (index 4) ON, then Save
        val switches = composeTestRule.onAllNodes(isToggleable())
        switches[1].performClick()
        switches[4].performClick()
        composeTestRule.onNodeWithText(saveLabel).performClick()

        assertEquals(1, dismissCount)
        assertTrue(TrainingApplication.exportToTCX())
        assertTrue(TrainingApplication.exportToGPX())
        assertTrue(TrainingApplication.exportToFIT())
        assertFalse(TrainingApplication.exportToCSV())
    }

    /**
     * TST-NAV-006.2: Verify Cancel dismisses without persisting unsaved export format changes.
     */
    @Test
    fun testExportDialog_cancelDiscardsChanges() {
        TrainingApplication.setExportToGPX(true)
        TrainingApplication.setExportToCSV(false)
        var dismissCount = 0

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    ExportSettingsDialog(onDismiss = { dismissCount++ })
                }
            }
        }

        val cancelLabel = context.getString(R.string.cancel)
        composeTestRule.onAllNodes(isToggleable())[3].performClick()
        composeTestRule.onNodeWithText(cancelLabel).performClick()

        assertEquals(1, dismissCount)
        assertFalse("Cancel must not persist CSV toggle", TrainingApplication.exportToCSV())
        assertTrue("Previously saved GPX flag must remain enabled", TrainingApplication.exportToGPX())
    }
}
