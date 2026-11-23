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

package com.atrainingtracker.trainingtracker.migration

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.navigation.NavRoutes
import com.atrainingtracker.trainingtracker.ui.navigation.SettingsBottomSheetType
import com.atrainingtracker.trainingtracker.ui.navigation.createDrawerGroups
import com.atrainingtracker.trainingtracker.ui.navigation.isDrawerItemSelected
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
 * Behavioral Robolectric Compose UI tests for [ExportTabContent], Tab 0 mounting in
 * [ImportBackupTabsScreen], immediate persistence, and consolidated drawer/deep-link routing.
 *
 * Linked test specs: TST-MIG-059, TST-NAV-006
 */
// Linked test specs: TST-MIG-059, TST-NAV-006
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xhdpi")
class ExportTabContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    private fun setTrainingAppStaticField(fieldName: String, value: Any?) {
        val field: Field = TrainingApplication::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(null, value)
    }

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        context = app
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
     * TST-MIG-059.1: Verify ExportTabContent renders all 6 format toggles and persists changes immediately.
     */
    @Test
    fun testExportTabContent_rendersAllFormatsAndPersistsImmediately_TST_MIG_059_1() {
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                ExportTabContent()
            }
        }

        val rawSensorLabel = context.getString(R.string.file_format_raw_sensor)
        composeTestRule.onNodeWithText(context.getString(R.string.prefs_Export)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.prefsExportSummary)).assertIsDisplayed()

        listOf("TCX", "GPX", "Golden Cheetah JSON", "CSV", "FIT", rawSensorLabel).forEach { label ->
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }

        val switches = composeTestRule.onAllNodes(isToggleable())
        switches[0].assertIsOn()
        switches[1].assertIsOff()
        switches[4].assertIsOff()
        switches[5].assertIsOff()

        // Toggle GPX ON, FIT ON, TCX OFF, and Raw Sensor ON (via row tap) -> immediate persistence
        switches[1].performClick()
        switches[4].performClick()
        switches[0].performClick()
        composeTestRule.onNodeWithText(rawSensorLabel).performClick()
        composeTestRule.waitForIdle()

        assertFalse("TCX toggle must persist OFF immediately", TrainingApplication.exportToTCX())
        assertTrue("GPX toggle must persist ON immediately", TrainingApplication.exportToGPX())
        assertFalse("GC JSON must remain OFF", TrainingApplication.exportToGCJson())
        assertFalse("CSV must remain OFF", TrainingApplication.exportToCSV())
        assertTrue("FIT toggle must persist ON immediately", TrainingApplication.exportToFIT())
        assertTrue("Raw Sensor row click must persist ON immediately", TrainingApplication.exportToRawSensor())
    }

    /**
     * TST-MIG-059.1: Verify ImportBackupTabsScreen mounts Export at Tab 0 by default and respects initialTab.
     */
    @Test
    fun testImportBackupTabsScreen_mountsExportAtTabZeroByDefault_TST_MIG_059_1() {
        val vm = BackupRestoreViewModel(app)
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                ImportBackupTabsScreen(viewModel = vm)
            }
        }

        // Export content is immediately visible on initial Tab 0 without switching tabs
        composeTestRule.onNodeWithText("TCX").assertIsDisplayed()
        composeTestRule.onNodeWithText("GPX").assertIsDisplayed()
        composeTestRule.onNodeWithText("Golden Cheetah JSON").assertIsDisplayed()
        composeTestRule.onNodeWithText("FIT").assertIsDisplayed()

        // Toggle FIT directly from Tab 0 and confirm persistence
        composeTestRule.onNodeWithText("FIT").performClick()
        composeTestRule.waitForIdle()
        assertTrue("Toggling FIT on Tab 0 must persist immediately", TrainingApplication.exportToFIT())
    }

    /**
     * TST-MIG-059.1: Verify explicit initialTab parameter opens the requested tab (e.g., Backup = index 3).
     */
    @Test
    fun testImportBackupTabsScreen_respectsExplicitInitialTab_TST_MIG_059_1() {
        val vm = BackupRestoreViewModel(app)
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                ImportBackupTabsScreen(
                    viewModel = vm,
                    initialTab = ImportBackupTabIndex.BACKUP
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.backup_now)).assertIsDisplayed()
    }

    /**
     * TST-MIG-059.2: Verify drawer_export is removed from drawer, aliases to BACKUP_RESTORE, and has no sheet.
     */
    @Test
    fun testLegacyDrawerExportAlias_routesToBackupRestoreWithoutBottomSheet_TST_MIG_059_2() {
        assertEquals(NavRoutes.BACKUP_RESTORE, NavRoutes.fromDrawerItemId(R.id.drawer_export))
        assertNull(NavRoutes.toBottomSheetType(R.id.drawer_export))
        assertTrue(
            "SettingsBottomSheetType must not expose obsolete EXPORT entry",
            SettingsBottomSheetType.entries.none { it.name == "EXPORT" }
        )
        assertTrue(
            "drawer_backup_restore must highlight when legacy drawer_export is selected",
            isDrawerItemSelected(R.id.drawer_backup_restore, R.id.drawer_export)
        )
        val allDrawerIds = createDrawerGroups(R.string.tab_start, isDebug = false).flatMap { it.items }.map { it.id }
        assertFalse("Separate drawer_export entry must be removed", allDrawerIds.contains(R.id.drawer_export))
        assertTrue("Consolidated drawer_backup_restore entry must exist", allDrawerIds.contains(R.id.drawer_backup_restore))
    }
}
