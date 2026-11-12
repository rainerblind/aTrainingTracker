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

package com.atrainingtracker.trainingtracker.ui.sportingjourney

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit verification suite for [SportingJourneyViewModel] (REQ-SPT-002, TST-SPT-008).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SportingJourneyViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var repository: SportingJourneyRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        repository = mockk()
        coEvery { repository.loadJourneyData(any(), any()) } returns SportingJourneyData(
            horizon = SportingHorizon.AllTime,
            sportFilter = SportingSportFilter.ALL,
            isLoading = false
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialLoad_callsRepositoryWithDefaults() = runTest(testDispatcher) {
        val viewModel = SportingJourneyViewModel(application, repository)
        assertTrue(viewModel.uiState.value.isLoading)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(SportingHorizon.AllTime, viewModel.uiState.value.horizon)
        assertEquals(SportingSportFilter.ALL, viewModel.uiState.value.sportFilter)
        coVerify(exactly = 1) { repository.loadJourneyData(SportingHorizon.AllTime, SportingSportFilter.ALL) }
    }

    @Test
    fun testSetHorizon_immediatelyKeysStateAndRefreshes() = runTest(testDispatcher) {
        coEvery { repository.loadJourneyData(SportingHorizon.Year(2026), SportingSportFilter.ALL) } returns SportingJourneyData(
            horizon = SportingHorizon.Year(2026),
            sportFilter = SportingSportFilter.ALL,
            isLoading = false
        )

        val viewModel = SportingJourneyViewModel(application, repository)
        advanceUntilIdle()

        viewModel.setHorizon(SportingHorizon.Year(2026))

        // UI state must immediately key to Year(2026) with isLoading = true
        assertEquals(SportingHorizon.Year(2026), viewModel.uiState.value.horizon)
        assertTrue(viewModel.uiState.value.isLoading)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(SportingHorizon.Year(2026), viewModel.uiState.value.horizon)
        coVerify(exactly = 1) { repository.loadJourneyData(SportingHorizon.Year(2026), SportingSportFilter.ALL) }
    }

    @Test
    fun testSetSportFilter_immediatelyKeysStateAndRefreshes() = runTest(testDispatcher) {
        coEvery { repository.loadJourneyData(SportingHorizon.AllTime, SportingSportFilter.RUNNING) } returns SportingJourneyData(
            horizon = SportingHorizon.AllTime,
            sportFilter = SportingSportFilter.RUNNING,
            isLoading = false
        )

        val viewModel = SportingJourneyViewModel(application, repository)
        advanceUntilIdle()

        viewModel.setSportFilter(SportingSportFilter.RUNNING)

        // UI state must immediately key to RUNNING with isLoading = true
        assertEquals(SportingSportFilter.RUNNING, viewModel.uiState.value.sportFilter)
        assertTrue(viewModel.uiState.value.isLoading)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(SportingSportFilter.RUNNING, viewModel.uiState.value.sportFilter)
        coVerify(exactly = 1) { repository.loadJourneyData(SportingHorizon.AllTime, SportingSportFilter.RUNNING) }
    }
}
