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

package com.atrainingtracker.trainingtracker.integration

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessRepository
import com.atrainingtracker.trainingtracker.monetization.SensorQuotaReconciliationManager
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingButton
import com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingViewModel
import com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsViewModel
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Software Integration Verification Test: Presentation Layer UI to ViewModel (ASPICE SWE.5,
 * INT-SWE-01, IF-SWE-01, REQ-PRO-110.1, TST-INT-009, ATT-8155).
 *
 * Verifies:
 * 1. Unidirectional MVI action dispatch from ControlTrackingButton to real ControlTrackingViewModel
 *    (onStartTracking, onPauseTracking, onResumeTracking, onStopTracking) and Compose recomposition.
 * 2. TrackingTabsViewModel screenMode StateFlow updates driving Compose node recomposition.
 * 3. Session coachmark state hoisting and Compose UI node updates verified via waitForIdle().
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UiPresentationIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var application: Application
    private lateinit var mockTrackingViewsRepo: TrackingViewsRepository
    private lateinit var mockBanalRepo: BANALServiceRepository
    private lateinit var mockDevicesRepo: DeviceDataRepository
    private lateinit var mockReconciliationManager: SensorQuotaReconciliationManager
    private lateinit var mockTuningDataStore: TuningPreferencesDataStore
    private lateinit var mockHrvReadinessRepo: HrvReadinessRepository

    private val trackingModeLiveData = MutableLiveData(TrackingMode.READY)
    private val defaultSport = ActivityType.getDefaultActivityType()
    private val banalActivityTypeFlow = MutableStateFlow(defaultSport)

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        application = ApplicationProvider.getApplicationContext()
        application.deleteDatabase("WorkoutSummaries.db")

        val appField = TrainingApplication::class.java.getDeclaredField("cAppContext")
        appField.isAccessible = true
        appField.set(null, application)

        val prefField = TrainingApplication::class.java.getDeclaredField("cSharedPreferences")
        prefField.isAccessible = true
        prefField.set(null, PreferenceManager.getDefaultSharedPreferences(application))

        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        WorkoutRepository.resetForTesting(null)

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockTrackingViewsRepo = mockk(relaxed = true)
        mockBanalRepo = mockk(relaxed = true)
        mockDevicesRepo = mockk(relaxed = true)
        mockReconciliationManager = mockk(relaxed = true)
        mockTuningDataStore = mockk(relaxed = true)
        mockHrvReadinessRepo = mockk(relaxed = true)

        every { mockBanalRepo.trackingMode } returns trackingModeLiveData
        every { mockBanalRepo.activityType } returns banalActivityTypeFlow
        every { mockBanalRepo.wasAutoPaused } returns MutableStateFlow(false)
        every { mockBanalRepo.currentLocation } returns MutableStateFlow(null)
        every { mockBanalRepo.currentDistance } returns MutableStateFlow(0.0)
        every { mockBanalRepo.currentBearing } returns MutableStateFlow(0.0)
        every { mockBanalRepo.allFilteredSensorData } returns MutableStateFlow(emptyList())
        every { mockBanalRepo.bindToBANALService() } just Runs
        every { mockTrackingViewsRepo.getTrackingViewsFlow(any()) } returns flowOf(emptyList())
        every { mockDevicesRepo.allDevices } returns MutableStateFlow(emptyList())
        every { mockReconciliationManager.checkDowngradeReconciliation() } returns false
        every { mockTuningDataStore.tuningConfigFlow } returns flowOf(TuningConfig())
        coEvery { mockHrvReadinessRepo.hasCompletedMorningCheck(any()) } returns true

        mockkObject(BANALServiceRepository.Companion)
        every { BANALServiceRepository.getInstance(any<Application>()) } returns mockBanalRepo
        every { BANALServiceRepository.getInstance(any<Context>()) } returns mockBanalRepo

        mockkObject(DeviceDataRepository.Companion)
        every { DeviceDataRepository.getInstance(any<Application>()) } returns mockDevicesRepo
        every { DeviceDataRepository.getInstance(any<Context>()) } returns mockDevicesRepo

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        val mockSummaries = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummaries
        every { mockSummaries.hasUnfinishedWorkout() } returns false
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        application.deleteDatabase("WorkoutSummaries.db")
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
        unmockkAll()
    }

    private fun createTabsViewModel(): TrackingTabsViewModel {
        return TrackingTabsViewModel(
            application = application,
            trackingViewsRepository = mockTrackingViewsRepo,
            banalServiceRepository = mockBanalRepo,
            devicesRepository = mockDevicesRepo
        )
    }

    private fun createControlViewModel(): ControlTrackingViewModel {
        return ControlTrackingViewModel(
            application = application,
            reconciliationManager = mockReconciliationManager,
            tuningPreferencesDataStore = mockTuningDataStore,
            hrvReadinessRepository = mockHrvReadinessRepo
        )
    }

    /**
     * TST-INT-009.1: Verifies real ControlTrackingViewModel MVI action dispatch and Compose recomposition
     * across READY -> TRACKING -> PAUSED -> RESUME & STOP.
     */
    @Test
    fun testControlTrackingButton_realViewModelMviDispatch_andRecomposition() {
        val viewModel = createControlViewModel()

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                val mode by viewModel.trackingMode.observeAsState(TrackingMode.READY)
                ControlTrackingButton(
                    modifier = Modifier.testTag("control_button"),
                    mode = mode,
                    onStart = viewModel::onStartTracking,
                    onPause = viewModel::onPauseTracking,
                    onResume = viewModel::onResumeTracking,
                    onStop = viewModel::onStopTracking
                )
            }
        }

        val startLabel = application.getString(R.string.start_tracking)
        val pauseLabel = application.getString(R.string.pause_tracking)
        val resumeLabel = application.getString(R.string.resume_tracking)
        val stopLabel = application.getString(R.string.stop_tracking)

        // 1. READY -> click Start dispatches REQUEST_START_TRACKING
        composeTestRule.onNodeWithText(startLabel).assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        val broadcastedActions = shadowOf(application).broadcastIntents.map { it.action }
        assertTrue(
            "Start click must dispatch REQUEST_START_TRACKING via ControlTrackingViewModel",
            broadcastedActions.contains(TrainingApplication.REQUEST_START_TRACKING)
        )

        // 2. Transition mode to TRACKING -> Compose recomposes to Pause button -> click Pause
        trackingModeLiveData.postValue(TrackingMode.TRACKING)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(pauseLabel).assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertTrue(
            "Pause click must dispatch REQUEST_PAUSE_TRACKING",
            shadowOf(application).broadcastIntents.any { it.action == TrainingApplication.REQUEST_PAUSE_TRACKING }
        )

        // 3. Transition mode to PAUSED -> Compose recomposes to Resume + Stop buttons -> click both
        trackingModeLiveData.postValue(TrackingMode.PAUSED)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(resumeLabel).assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText(stopLabel).assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        val allActions = shadowOf(application).broadcastIntents.map { it.action }
        assertTrue(
            "Resume click must dispatch REQUEST_RESUME_FROM_PAUSED",
            allActions.contains(TrainingApplication.REQUEST_RESUME_FROM_PAUSED)
        )
        assertTrue(
            "Stop click must dispatch REQUEST_STOP_TRACKING",
            allActions.contains(TrainingApplication.REQUEST_STOP_TRACKING)
        )
    }

    /**
     * TST-INT-009.2: Verifies TrackingTabsViewModel screenMode StateFlow reactive emission
     * recomposes the Compose UI node text across TRACKING -> CONFIGURATION -> PREVIEW.
     */
    @Test
    fun testTrackingTabsViewModel_screenModeStateFlow_recomposition() {
        val viewModel = createTabsViewModel()
        assertEquals(ScreenMode.TRACKING, viewModel.screenMode.value)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                val mode by viewModel.screenMode.collectAsState()
                Text(
                    text = "Mode: ${mode.name}",
                    modifier = Modifier.testTag("screen_mode_text")
                )
            }
        }

        composeTestRule.onNodeWithTag("screen_mode_text")
            .assertIsDisplayed()
            .assertTextEquals("Mode: TRACKING")

        viewModel.setScreenMode(ScreenMode.CONFIGURATION)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("screen_mode_text")
            .assertTextEquals("Mode: CONFIGURATION")

        viewModel.setScreenMode(ScreenMode.PREVIEW)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("screen_mode_text")
            .assertTextEquals("Mode: PREVIEW")
    }

    /**
     * TST-INT-009.3: Verifies session coachmark dismissal state hoisting and Compose UI node
     * recomposition via waitForIdle() and node text assertions.
     */
    @Test
    fun testTrackingTabsViewModel_coachmarkStateHoisting() {
        val viewModel = createTabsViewModel()

        assertFalse(viewModel.hasRenderedCoachmarkInSession.value)
        assertFalse(viewModel.isCoachmarkDismissPending.value)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                val rendered by viewModel.hasRenderedCoachmarkInSession.collectAsState()
                val pending by viewModel.isCoachmarkDismissPending.collectAsState()
                Text(
                    text = "Rendered: $rendered, Pending: $pending",
                    modifier = Modifier.testTag("coachmark_state_text")
                )
            }
        }

        composeTestRule.onNodeWithTag("coachmark_state_text")
            .assertIsDisplayed()
            .assertTextEquals("Rendered: false, Pending: false")

        viewModel.markCoachmarkRenderedInSession()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("coachmark_state_text")
            .assertTextEquals("Rendered: true, Pending: false")

        viewModel.markCoachmarkDismissPending()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("coachmark_state_text")
            .assertTextEquals("Rendered: true, Pending: true")
    }
}
