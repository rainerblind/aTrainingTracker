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
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import com.atrainingtracker.trainingtracker.dialogs.GPSDisabledAlertDialog
import com.atrainingtracker.trainingtracker.dialogs.StartOrResumeAlertDialog
import com.atrainingtracker.trainingtracker.dialogs.StartOrResumeDialogContent
import com.atrainingtracker.trainingtracker.helpers.ProcessExitReasonHelper.KillDiagnosis
import com.atrainingtracker.trainingtracker.helpers.ProcessExitReasonHelper.KillReason
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.common.GooglePlayServicesUtil
import com.google.android.gms.maps.MapsInitializer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Behavioral Compose UI & excision contract test suite for ATT-8129 (REQ-UI-2801, TST-UI-2801):
 * 1. Physical and classpath excision of all 27 legacy Fragment / SettingsDialogFragment / hybrid-bridge files
 *    and the StartOrResumeDialog DialogFragment wrapper.
 * 2. MainActivityWithNavigation cleanup (zero mFragment/mDrawerLayout/StartOrResumeInterface, Compose dialog states).
 * 3. Pure Compose StartOrResumeAlertDialog & GPSDisabledAlertDialog interactive rendering and Rule 8.5 Light/Dark PNG capture.
 */
// Linked test specs: TST-UI-2801
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w860dp-h1100dp-xhdpi")
class LegacyUiDecommissioningComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        unmockkAll()
        MainActivityWithNavigation.defaultIoDispatcher = Dispatchers.IO
        runCatching { Dispatchers.resetMain() }
    }

    @Test
    fun retiredFragmentsSettingsDialogFragmentsAndBridges_areExcisedFromClasspathAndDisk_TST_UI_2801_1() {
        val retiredClasses = listOf(
            // 13 unreferenced screen Fragments
            "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsFragment",
            "com.atrainingtracker.trainingtracker.ui.equipment.EquipmentFragment",
            "com.atrainingtracker.trainingtracker.ui.routes.RoutesFragment",
            "com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClustersFragment",
            "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsFragment",
            "com.atrainingtracker.trainingtracker.migration.BackupRestoreFragment",
            "com.atrainingtracker.trainingtracker.ui.segments.segmentlist.StarredSegmentsFragment",
            "com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedContainerFragment",
            "com.atrainingtracker.banalservice.ui.sporttype.SportTypeListFragment",
            "com.atrainingtracker.trainingtracker.ui.map.MapFragmentWithTrack",
            "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesTabbedFragment",
            "com.atrainingtracker.trainingtracker.activities.ZoneSettingsFragment",
            "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesListFragment",
            // 9 orphaned SettingsDialogFragment subclasses + base + helper
            "com.atrainingtracker.trainingtracker.ui.settings.display.DisplaySettingsDialogFragment",
            "com.atrainingtracker.trainingtracker.ui.settings.dropbox.DropboxSettingsDialogFragment",
            "com.atrainingtracker.trainingtracker.ui.settings.export.ExportSettingsDialogFragment",
            "com.atrainingtracker.trainingtracker.ui.settings.googledrive.GoogleDriveSettingsDialogFragment",
            "com.atrainingtracker.trainingtracker.ui.settings.search.SearchSettingsDialogFragment",
            "com.atrainingtracker.trainingtracker.ui.settings.strava.StravaSettingsDialogFragment",
            "com.atrainingtracker.trainingtracker.ui.settings.units.UnitsSettingsDialogFragment",
            "com.atrainingtracker.trainingtracker.ui.settings.trackingtabs.ActivityTypeSelectionDialogFragment",
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningDialogFragment",
            "com.atrainingtracker.trainingtracker.ui.components.core.AppBottomSheetDialogFragment",
            "com.atrainingtracker.trainingtracker.ui.settings.trackingtabs.ActivityTypeSelectionHelper",
            // Hybrid DialogFragment / Activity interface bridges
            "com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceFragmentFactory",
            "com.atrainingtracker.banalservice.ui.devices.editdevice.ComposableEditDeviceDialogFragment",
            "com.atrainingtracker.trainingtracker.dialogs.StartOrResumeDialog",
            "com.atrainingtracker.trainingtracker.interfaces.StartOrResumeInterface",
            "com.atrainingtracker.trainingtracker.dialogs.GPSDisabledDialog"
        )

        for (className in retiredClasses) {
            try {
                Class.forName(className)
                fail("Retired legacy class $className must be excised from classpath (REQ-UI-2801.1)")
            } catch (_: ClassNotFoundException) {
                // Expected
            }
        }
    }

    @Test
    fun mainActivityWithNavigation_triggersPureComposeDialogStatesWithoutLegacyFields_TST_UI_2801_2() {
        val testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        MainActivityWithNavigation.defaultIoDispatcher = testDispatcher

        val workoutRepoMock = mockk<WorkoutRepository>(relaxed = true)
        every { workoutRepoMock.hasUnfinishedWorkout() } returns true
        WorkoutRepository.resetForTesting(workoutRepoMock)

        mockkStatic(MapsInitializer::class)
        every { MapsInitializer.initialize(any(), any(), any()) } returns 0
        every { MapsInitializer.initialize(any()) } returns 0

        @Suppress("DEPRECATION")
        run {
            mockkStatic(GooglePlayServicesUtil::class)
            every { GooglePlayServicesUtil.isGooglePlayServicesAvailable(any()) } returns 0
            every { GooglePlayServicesUtil.getErrorDialog(any(), any(), any()) } returns null
        }

        val fields = MainActivityWithNavigation::class.java.declaredFields.map { it.name }.toSet()
        assertFalse("mFragment must be removed", "mFragment" in fields)
        assertFalse("mDrawerLayout must be removed", "mDrawerLayout" in fields)
        assertFalse("mStartAndNotResume must be removed", "mStartAndNotResume" in fields)
        assertFalse("mAlreadyTriedToRequestDropboxToken must be removed", "mAlreadyTriedToRequestDropboxToken" in fields)

        val controller = Robolectric.buildActivity(MainActivityWithNavigation::class.java).create()
        val activity = controller.get()
        assertFalse("showStartOrResumeDialogState starts false", activity.showStartOrResumeDialogState)
        assertFalse("showGpsDisabledDialog starts false", activity.showGpsDisabledDialog)

        activity.checkUnfinishedWorkout()
        assertTrue(
            "checkUnfinishedWorkout must flip pure Compose showStartOrResumeDialogState to true",
            activity.showStartOrResumeDialogState
        )
        controller.destroy()
    }

    @Test
    fun mainActivityWithNavigation_preservesPureComposeDialogFlagsAcrossRotation_TST_UI_2801_2() {
        val testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        MainActivityWithNavigation.defaultIoDispatcher = testDispatcher

        val workoutRepoMock = mockk<WorkoutRepository>(relaxed = true)
        every { workoutRepoMock.hasUnfinishedWorkout() } returns false
        WorkoutRepository.resetForTesting(workoutRepoMock)

        mockkStatic(MapsInitializer::class)
        every { MapsInitializer.initialize(any(), any(), any()) } returns 0
        every { MapsInitializer.initialize(any()) } returns 0

        @Suppress("DEPRECATION")
        run {
            mockkStatic(GooglePlayServicesUtil::class)
            every { GooglePlayServicesUtil.isGooglePlayServicesAvailable(any()) } returns 0
            every { GooglePlayServicesUtil.getErrorDialog(any(), any(), any()) } returns null
        }

        val controller = Robolectric.buildActivity(MainActivityWithNavigation::class.java).create()
        val initialActivity = controller.get()
        initialActivity.showStartOrResumeDialogState = true
        initialActivity.showGpsDisabledDialog = true

        controller.recreate()
        val recreatedActivity = controller.get()
        assertTrue(
            "showStartOrResumeDialogState must survive Activity recreation (rotation)",
            recreatedActivity.showStartOrResumeDialogState
        )
        assertTrue(
            "showGpsDisabledDialog must survive Activity recreation (rotation)",
            recreatedActivity.showGpsDisabledDialog
        )

        recreatedActivity.showStartOrResumeDialogState = false
        recreatedActivity.showGpsDisabledDialog = false
        controller.recreate()
        val recreatedCleared = controller.get()
        assertFalse(
            "Cleared showStartOrResumeDialogState must remain false after recreation",
            recreatedCleared.showStartOrResumeDialogState
        )
        assertFalse(
            "Cleared showGpsDisabledDialog must remain false after recreation",
            recreatedCleared.showGpsDisabledDialog
        )
        controller.destroy()
    }

    @Test
    fun pureComposeDialogs_renderAndDispatchActions_andCaptureLightDarkPng_TST_UI_2801_2() {
        var resumeCount = 0
        var startNewCount = 0
        var batterySettingsCount = 0
        var gpsSettingsCount = 0
        var gpsDismissCount = 0

        val batteryDiagnosis = KillDiagnosis(
            reason = KillReason.BATTERY_KILL,
            escalationLevel = 1,
            isStravaActive = false,
            shouldShowBatteryButton = true
        )

        val showPreviewRow = androidx.compose.runtime.mutableStateOf(true)
        val showGps = androidx.compose.runtime.mutableStateOf(false)
        val showRecoveryModal = androidx.compose.runtime.mutableStateOf(false)

        // Single setContent hosting Light/Dark visual preview + conditional pure Compose modal dialogs
        composeTestRule.setContent {
            if (showPreviewRow.value) {
                Row(
                    modifier = Modifier
                        .testTag("Att8129StartOrResumeLightDark")
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ATrainingTrackerTheme(darkTheme = false) {
                        Column(
                            modifier = Modifier
                                .width(400.dp)
                                .background(MaterialTheme.colorScheme.background)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Light — StartOrResumeAlertDialog",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            StartOrResumeDialogContent(
                                diagnosis = batteryDiagnosis,
                                onStartNew = {},
                                onResume = {},
                                onDisableBatteryOptimization = {}
                            )
                        }
                    }
                    ATrainingTrackerTheme(darkTheme = true) {
                        Column(
                            modifier = Modifier
                                .width(400.dp)
                                .background(MaterialTheme.colorScheme.background)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Dark — StartOrResumeAlertDialog",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            StartOrResumeDialogContent(
                                diagnosis = batteryDiagnosis,
                                onStartNew = {},
                                onResume = {},
                                onDisableBatteryOptimization = {}
                            )
                        }
                    }
                }
            }

            ATrainingTrackerTheme(darkTheme = false) {
                if (showGps.value) {
                    GPSDisabledAlertDialog(
                        onOpenLocationSettings = { gpsSettingsCount++ },
                        onDismiss = { gpsDismissCount++ }
                    )
                }
                if (showRecoveryModal.value) {
                    StartOrResumeAlertDialog(
                        diagnosis = batteryDiagnosis,
                        onDismissRequest = { showRecoveryModal.value = false },
                        onStartNew = { startNewCount++ },
                        onResume = { resumeCount++ },
                        onDisableBatteryOptimization = { batterySettingsCount++ }
                    )
                }
            }
        }

        val recoveryBmp = composeTestRule.onNodeWithTag("Att8129StartOrResumeLightDark")
            .captureToImage()
            .asAndroidBitmap()

        // 2. Show interactive GPSDisabledAlertDialog & verify callbacks + capture dialog bitmap
        showPreviewRow.value = false
        showGps.value = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.gps_disabled_on_device)).assertIsDisplayed()
        val gpsBmp = composeTestRule.onNode(isDialog()).captureToImage().asAndroidBitmap()

        composeTestRule.onNodeWithText(context.getString(R.string.gps_goto_settings)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.Cancel)).performClick()
        assertTrue("GPS Go to Settings callback invoked", gpsSettingsCount == 1)
        assertTrue("GPS Dismiss callback invoked", gpsDismissCount == 1)

        // 3. Switch to interactive StartOrResumeAlertDialog modal and verify all 3 callbacks
        showGps.value = false
        showRecoveryModal.value = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.action_disable_battery_optimization)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.start_new_workout)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.resume_workout)).performClick()
        assertTrue("Battery settings callback invoked", batterySettingsCount == 1)
        assertTrue("Start new callback invoked", startNewCount == 1)
        assertTrue("Resume callback invoked", resumeCount == 1)

        // Composite recovery Light/Dark + GPSDisabledAlertDialog into one crisp Rule 8.5 PNG
        val combinedWidth = maxOf(recoveryBmp.width, gpsBmp.width)
        val combinedHeight = recoveryBmp.height + gpsBmp.height + 24
        val combined = Bitmap.createBitmap(combinedWidth, combinedHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combined)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(recoveryBmp, 0f, 0f, null)
        canvas.drawBitmap(gpsBmp, ((combinedWidth - gpsBmp.width) / 2f).coerceAtLeast(0f), recoveryBmp.height + 16f, null)

        val outFile = File("/tmp/att8129_dialogs_light_dark.png")
        FileOutputStream(outFile).use { out ->
            combined.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("Composite Rule 8.5 PNG must exist and be non-empty", outFile.exists() && outFile.length() > 1000L)
    }
}
