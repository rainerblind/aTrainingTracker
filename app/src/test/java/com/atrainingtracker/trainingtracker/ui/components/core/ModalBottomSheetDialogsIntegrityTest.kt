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

package com.atrainingtracker.trainingtracker.ui.components.core

import android.app.Application
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.batterysaver.DisplayBrightnessMode
import com.atrainingtracker.trainingtracker.ui.clusters.ClusterInfoDialog
import com.atrainingtracker.trainingtracker.ui.components.DeleteConfirmationDialog
import com.atrainingtracker.trainingtracker.ui.settings.display.DisplaySettingsDialog
import com.atrainingtracker.trainingtracker.ui.settings.search.SearchSettingsDialog
import com.atrainingtracker.trainingtracker.ui.settings.trackingtabs.ActivityTypeSelectionDialog
import com.atrainingtracker.trainingtracker.ui.settings.units.UnitsSettingsDialog
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.CockpitThemeMode
import com.atrainingtracker.trainingtracker.ui.tracking.LapSummaryDialog
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behavioral Compose UI and excision contract tests verifying modernized modal bottom sheet dialogs,
 * selection flows, and centered dialog invariants according to REQ-UI-149 and TST-UI-102 (ATT-900).
 */
// Linked test specs: TST-UI-104, TST-UI-106, TST-UI-107, TST-UI-109
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class ModalBottomSheetDialogsIntegrityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        unmockkStatic(TrainingApplication::class)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun testAppModalBottomSheet_supportsPainterAndVectorIcons() {
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                AppModalBottomSheet(
                    title = "Vector Icon Sheet",
                    icon = Icons.Default.Settings,
                    onDismissRequest = {}
                ) {
                    Text("Body Vector")
                }
                AppModalBottomSheet(
                    title = "Painter Icon Sheet",
                    iconPainter = painterResource(id = R.drawable.ic_distance),
                    onDismissRequest = {}
                ) {
                    Text("Body Painter")
                }
            }
        }

        composeTestRule.onNodeWithText("Vector Icon Sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Body Vector").assertIsDisplayed()
        composeTestRule.onNodeWithText("Painter Icon Sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Body Painter").assertIsDisplayed()
    }

    @Test
    fun testLegacySettingsDialogFragmentsAndAppBottomSheetDialogFragment_areEliminated() {
        val retiredFragmentClasses = listOf(
            "com.atrainingtracker.trainingtracker.ui.components.core.AppBottomSheetDialogFragment",
            "com.atrainingtracker.trainingtracker.ui.settings.export.ExportSettingsDialogFragment",
            "com.atrainingtracker.trainingtracker.ui.settings.units.UnitsSettingsDialogFragment",
            "com.atrainingtracker.trainingtracker.ui.settings.display.DisplaySettingsDialogFragment",
            "com.atrainingtracker.trainingtracker.ui.settings.dropbox.DropboxSettingsDialogFragment",
            "com.atrainingtracker.trainingtracker.ui.settings.googledrive.GoogleDriveSettingsDialogFragment",
            "com.atrainingtracker.trainingtracker.ui.settings.strava.StravaSettingsDialogFragment",
            "com.atrainingtracker.trainingtracker.ui.settings.search.SearchSettingsDialogFragment",
            "com.atrainingtracker.trainingtracker.ui.settings.trackingtabs.ActivityTypeSelectionDialogFragment",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningDialogFragment"
        )
        for (className in retiredFragmentClasses) {
            try {
                Class.forName(className)
                fail("Retired legacy fragment $className must not exist in classpath (REQ-UI-2801.1)")
            } catch (_: ClassNotFoundException) {
                // Expected
            }
        }
        try {
            Class.forName("com.atrainingtracker.trainingtracker.ui.settings.export.ExportSettingsDialogKt")
            fail("Retired ExportSettingsDialogKt island must not exist in classpath (REQ-MIG-059.2)")
        } catch (_: ClassNotFoundException) {
            // Expected
        }
    }

    @Test
    fun testAppBottomSheetContent_rendersHeaderAndBody() {
        var dismissed = false
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                AppBottomSheetContent(
                    title = "Sheet Content Title",
                    onDismissRequest = { dismissed = true },
                    actions = {
                        AppDialogActions.CancelOnly(onCancel = { dismissed = true })
                    }
                ) {
                    Text("Inner Content")
                }
            }
        }

        composeTestRule.onNodeWithText("Sheet Content Title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inner Content").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.Cancel))
            .performSemanticsAction(SemanticsActions.OnClick)
        assertTrue("CancelOnly must invoke onDismiss", dismissed)
    }

    @Test
    fun testSettingsDialogs_renderAndHandleDismiss_behavioral() {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getDisplayOptions() } returns emptySet()
        every { TrainingApplication.getCockpitThemeMode() } returns CockpitThemeMode.SYSTEM
        every { TrainingApplication.getDisplayBrightnessMode() } returns DisplayBrightnessMode.SYSTEM
        every { TrainingApplication.getCustomDisplayBrightness() } returns 0.5f
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC
        every { TrainingApplication.getNumberOfSearchTries() } returns 3
        every { TrainingApplication.getBtleSearchPeriodSec() } returns 15
        every { TrainingApplication.startSearchWhenAppStarts() } returns false
        every { TrainingApplication.startSearchWhenResumeFromPaused() } returns false
        every { TrainingApplication.startSearchWhenUserChangesSport() } returns false
        every { TrainingApplication.startSearchWhenTrackingStarts() } returns false
        every { TrainingApplication.searchOnlyForSportSpecificDevices() } returns false
        every { TrainingApplication.changeSportWhenDeviceGetsLost() } returns false

        var activeSheet by mutableIntStateOf(0)
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                when (activeSheet) {
                    0 -> UnitsSettingsDialog(onDismiss = {})
                    1 -> DisplaySettingsDialog(onDismiss = {})
                    2 -> SearchSettingsDialog(onDismiss = {})
                    3 -> ActivityTypeSelectionDialog(onTypeSelected = {}, onDismiss = {})
                    4 -> ClusterInfoDialog(onDismissRequest = {}, onNavigateToAdvancedSettings = {})
                }
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.prefsUnitsTitle)).assertIsDisplayed()
        composeTestRule.runOnIdle { activeSheet = 1 }
        composeTestRule.onNodeWithText(context.getString(R.string.Display)).assertIsDisplayed()
        composeTestRule.runOnIdle { activeSheet = 2 }
        composeTestRule.onNodeWithText(context.getString(R.string.Search_Settings)).assertIsDisplayed()
        composeTestRule.runOnIdle { activeSheet = 3 }
        composeTestRule.onNodeWithText(context.getString(R.string.choose_activity_type)).assertIsDisplayed()
        composeTestRule.runOnIdle { activeSheet = 4 }
        composeTestRule.onNodeWithText(context.getString(R.string.cluster_info_title)).assertIsDisplayed()
    }

    @Test
    fun testCenteredDialogInvariants_retainedAsExpected() {
        var deleteConfirmed = false
        var showDelete by mutableStateOf(false)
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                if (!showDelete) {
                    LapSummaryDialog(
                        lapNr = 3,
                        lapTime = "04:12",
                        lapDistance = "1.00 km",
                        lapSpeed = "14.2 km/h",
                        onDismissRequest = {}
                    )
                } else {
                    DeleteConfirmationDialog(
                        title = "Delete Item",
                        message = "Confirm deletion?",
                        onConfirm = { deleteConfirmed = true },
                        onDismiss = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("04:12", substring = true).assertIsDisplayed()
        composeTestRule.runOnIdle { showDelete = true }
        composeTestRule.onNodeWithText("Delete Item").assertIsDisplayed()
        composeTestRule.onNodeWithText("Confirm deletion?").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.delete))
            .performSemanticsAction(SemanticsActions.OnClick)
        assertTrue("DeleteConfirmationDialog must invoke onConfirm", deleteConfirmed)
    }
}
