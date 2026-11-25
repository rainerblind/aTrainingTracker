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

package com.atrainingtracker.banalservice.ui.sporttype

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.SportTypeDbHelper
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.SportTypeEquipmentLinkManager
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutStatsSummary
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behavioral Compose UI tests for [SportTypesTabsScreen] verifying live Swimming list updates
 * without an app restart and `ON_RESUME` refresh wiring (`REQ-SET-134.2`, `TST-SET-134.2`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class SportTypesTabsScreenComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var application: Application
    private lateinit var mockWorkoutRepository: WorkoutRepository

    private class FakeLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Before
    fun setUp() {
        val baseApp = ApplicationProvider.getApplicationContext<Application>()
        application = spyk(baseApp)
        every { application.applicationContext } returns application
        every { application.getString(R.string.sport_type_other) } returns "Other"
        every { application.getString(R.string.sport_type_run) } returns "Run"
        every { application.getString(R.string.sport_type_bike) } returns "Bike"
        every { application.getString(R.string.sport_type_walk) } returns "Walk"
        every { application.getString(R.string.sport_type_MTB) } returns "MTB"
        every { application.getString(R.string.sport_type_swim) } returns "Swimming"
        every { application.getString(R.string.stats_total) } returns "Total"

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getAppContext() } returns application
        every { TrainingApplication.getMinWalkSpeed_mps() } returns 0.5
        every { TrainingApplication.getMaxWalkSpeed_mps() } returns 2.0
        every { TrainingApplication.getMaxRunSpeed_mps() } returns 6.0
        every { TrainingApplication.getMaxMTBSpeed_mps() } returns 7.0
        every { TrainingApplication.getMaxBikeSpeed_mps() } returns 15.0

        mockWorkoutRepository = mockk(relaxed = true)
        every { mockWorkoutRepository.workoutMutationEvents } returns MutableSharedFlow()
        every { mockWorkoutRepository.getSportTypeStats(any()) } returns WorkoutStatsSummary()
        WorkoutRepository.resetForTesting(mockWorkoutRepository)

        val mockLinksHelper = mockk<SportTypeEquipmentLinkManager>(relaxed = true)
        every { mockLinksHelper.getEquipmentIdsForSport(any()) } returns emptyList()
        mockkObject(SportTypeEquipmentLinkManager.Companion)
        every { SportTypeEquipmentLinkManager.getInstance(any()) } returns mockLinksHelper

        val mockEquipmentHelper = mockk<EquipmentDbHelper>(relaxed = true)
        EquipmentDbHelper.resetForTesting(mockEquipmentHelper)

        SportTypesRepository.resetForTesting(null)
        SportTypeDatabaseManager.resetForTesting(null)
        application.deleteDatabase(SportTypeDbHelper.DB_NAME)
    }

    @After
    fun tearDown() {
        SportTypesRepository.resetForTesting(null)
        SportTypeDatabaseManager.resetForTesting(null)
        application.deleteDatabase(SportTypeDbHelper.DB_NAME)
        WorkoutRepository.resetForTesting(null)
        EquipmentDbHelper.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testSportTypesTabsScreen_showsSwimmingImmediatelyOnActivationAndTabSwitch_TST_SET_134_2() {
        val viewModel = SportTypeViewModel(application, mockWorkoutRepository)
        val fakeOwner = FakeLifecycleOwner().apply {
            registry.currentState = Lifecycle.State.RESUMED
        }

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                CompositionLocalProvider(LocalLifecycleOwner provides fakeOwner) {
                    SportTypesTabsScreen(
                        viewModel = viewModel,
                        onNavigateToWorkouts = {}
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 3000) {
            viewModel.sportTypes.value.size == 5
        }
        composeTestRule.onNodeWithText("Swimming").assertDoesNotExist()

        // Activate Swimming via Expert Settings pathway without recreating screen or ViewModel
        SportTypesRepository.getInstance(application).ensureSwimSportTypeExists()

        composeTestRule.waitUntil(timeoutMillis = 3000) {
            viewModel.sportTypes.value.any { it.name == "Swimming" }
        }
        composeTestRule.onNodeWithText("Swimming").assertIsDisplayed()

        // Switch to "Other" tab (BSportType.UNKNOWN) and verify Swimming appears there too
        val otherTabLabel = application.getString(R.string.sport_type_tab_unknown)
        composeTestRule.onNodeWithText(otherTabLabel).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Swimming").assertIsDisplayed()
    }

    @Test
    fun testSportTypesTabsScreen_onResumeTriggersReload_TST_SET_134_2() {
        val mockVm = mockk<SportTypeViewModel>(relaxed = true)
        every { mockVm.sportTypes } returns kotlinx.coroutines.flow.MutableStateFlow(emptyList())
        val fakeOwner = FakeLifecycleOwner().apply {
            registry.currentState = Lifecycle.State.STARTED
        }

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                CompositionLocalProvider(LocalLifecycleOwner provides fakeOwner) {
                    SportTypesTabsScreen(
                        viewModel = mockVm,
                        onNavigateToWorkouts = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        verify(exactly = 0) { mockVm.loadSportTypes() }

        composeTestRule.runOnUiThread {
            fakeOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        composeTestRule.waitForIdle()
        verify(atLeast = 1) { mockVm.loadSportTypes() }
    }
}
