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
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.SportTypeDbHelper
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.SportTypeEquipmentLinkManager
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutStatsSummary
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric integration tests verifying that [SportTypeViewModel] immediately refreshes its
 * [SportTypeViewModel.sportTypes] StateFlow when [SportTypesRepository.ensureSwimSportTypeExists]
 * mutates `SportType.db` without recreating the ViewModel (`REQ-SET-134.2`, `TST-SET-134.2`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SportTypeViewModelRefreshTest {

    private lateinit var application: Application
    private lateinit var mockWorkoutRepository: WorkoutRepository

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

    private fun awaitCondition(timeoutMs: Long = 3000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(20)
        }
        assertTrue("Condition was not satisfied within ${timeoutMs}ms", predicate())
    }

    @Test
    fun testEnsureSwimSportTypeExists_immediatelyRefreshesExistingViewModelWithoutRestart_TST_SET_134_2() {
        val viewModel = SportTypeViewModel(application, mockWorkoutRepository)

        awaitCondition { viewModel.sportTypes.value.size == 5 }
        assertFalse(
            "Initial fresh list must not include Swimming",
            viewModel.sportTypes.value.any { it.name == "Swimming" }
        )

        val swimId = SportTypesRepository.getInstance(application).ensureSwimSportTypeExists()
        assertTrue("Inserted Swim ID must be positive", swimId > 0L)

        awaitCondition { viewModel.sportTypes.value.any { it.id == swimId && it.name == "Swimming" } }
        val swimItem = viewModel.sportTypes.value.first { it.id == swimId }
        assertEquals(BSportType.UNKNOWN, swimItem.bSportType)
        assertEquals(6, viewModel.sportTypes.value.size)
    }

    @Test
    fun testSportTypeViewModel_preservesApplicationReflectionConstructor_TST_SET_134_2() {
        val constructor = SportTypeViewModel::class.java.getConstructor(Application::class.java)
        assertNotNull("SportTypeViewModel must preserve public constructor accepting Application", constructor)
    }
}
