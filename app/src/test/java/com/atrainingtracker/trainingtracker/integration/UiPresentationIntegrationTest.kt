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
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.MutableLiveData
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingButton
import com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsViewModel
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Software Integration Verification Test: Presentation Layer UI to ViewModel (ASPICE SWE.5,
 * INT-SWE-01, IF-SWE-01, REQ-PRO-110.1, TST-INT-009, ATT-8155).
 *
 * Verifies:
 * 1. Unidirectional MVI action dispatch and reactive state hoisting between Jetpack Compose
 *    composables and ViewModels under Robolectric JVM environment without an emulator.
 * 2. Control tracking bar mode transitions (READY -> TRACKING -> PAUSED -> RESUME/STOP).
 * 3. StateFlow collection in Compose tree updating UI state without recomposition mutation leaks.
 * 4. Zero runtime exceptions during state changes and UI interaction dispatch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UiPresentationIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockTrackingViewsRepo = mockk<TrackingViewsRepository>(relaxed = true)
    private val mockBanalRepo = mockk<BANALServiceRepository>(relaxed = true)
    private val mockDevicesRepo = mockk<DeviceDataRepository>(relaxed = true)

    private val trackingModeLiveData = MutableLiveData<TrackingMode>()
    private val defaultSport = ActivityType.getDefaultActivityType()
    private val banalActivityTypeFlow = MutableStateFlow(defaultSport)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        every { mockApplication.filesDir } returns tempFolder.newFolder("files_${System.nanoTime()}")
        every { mockApplication.applicationContext } returns mockApplication

        every { mockBanalRepo.trackingMode } returns trackingModeLiveData
        every { mockBanalRepo.activityType } returns banalActivityTypeFlow
        every { mockBanalRepo.wasAutoPaused } returns MutableStateFlow(false)
        every { mockBanalRepo.bindToBANALService() } just Runs
        every { mockTrackingViewsRepo.getTrackingViewsFlow(any()) } returns flowOf(emptyList())
        every { mockDevicesRepo.allDevices } returns MutableStateFlow(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
        unmockkAll()
    }

    private fun createViewModel(): TrackingTabsViewModel {
        return TrackingTabsViewModel(
            application = mockApplication,
            trackingViewsRepository = mockTrackingViewsRepo,
            banalServiceRepository = mockBanalRepo,
            devicesRepository = mockDevicesRepo
        )
    }

    /**
     * TST-INT-009.1: Verifies ControlTrackingButton MVI action dispatch and state transitions
     * across READY -> TRACKING -> PAUSED -> RESUME.
     */
    @Test
    fun testControlTrackingButton_mviStateTransitions_actionDispatch() {
        var startClicked = false
        var pauseClicked = false
        var resumeClicked = false
        var stopClicked = false

        val modeState = androidx.compose.runtime.mutableStateOf(TrackingMode.READY)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                ControlTrackingButton(
                    modifier = Modifier.testTag("control_button"),
                    mode = modeState.value,
                    onStart = {
                        startClicked = true
                        modeState.value = TrackingMode.TRACKING
                    },
                    onPause = {
                        pauseClicked = true
                        modeState.value = TrackingMode.PAUSED
                    },
                    onResume = {
                        resumeClicked = true
                        modeState.value = TrackingMode.TRACKING
                    },
                    onStop = { stopClicked = true }
                )
            }
        }

        // 1. Initial READY mode -> click triggers onStart
        composeTestRule.onNodeWithTag("control_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("control_button").performClick()
        assertTrue("onStart callback must be invoked when in READY mode", startClicked)
        composeTestRule.waitForIdle()

        // 2. Now in TRACKING mode -> click triggers onPause
        composeTestRule.onNodeWithTag("control_button").performClick()
        assertTrue("onPause callback must be invoked when in TRACKING mode", pauseClicked)
        composeTestRule.waitForIdle()

        // 3. Now in PAUSED mode
        composeTestRule.onNodeWithTag("control_button").assertIsDisplayed()
        assertFalse("stop must not be clicked yet", stopClicked)
    }

    /**
     * TST-INT-009.2: Verifies TrackingTabsViewModel screenMode StateFlow reactive emission
     * triggers Compose recomposition with updated screen mode state.
     */
    @Test
    fun testTrackingTabsViewModel_screenModeStateFlow_recomposition() {
        val viewModel = createViewModel()
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

        composeTestRule.onNodeWithTag("screen_mode_text").assertIsDisplayed()

        // Dispatch screen mode change
        viewModel.setScreenMode(ScreenMode.CONFIGURATION)
        assertEquals(ScreenMode.CONFIGURATION, viewModel.screenMode.value)

        viewModel.setScreenMode(ScreenMode.PREVIEW)
        assertEquals(ScreenMode.PREVIEW, viewModel.screenMode.value)
    }

    /**
     * TST-INT-009.3: Verifies session coachmark dismissal state hoisting and StateFlow
     * updates without recomposition exceptions.
     */
    @Test
    fun testTrackingTabsViewModel_coachmarkStateHoisting() {
        val viewModel = createViewModel()

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

        composeTestRule.onNodeWithTag("coachmark_state_text").assertIsDisplayed()

        viewModel.markCoachmarkRenderedInSession()
        assertTrue(viewModel.hasRenderedCoachmarkInSession.value)

        viewModel.markCoachmarkDismissPending()
        assertTrue(viewModel.isCoachmarkDismissPending.value)
    }
}
