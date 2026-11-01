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

package com.atrainingtracker.trainingtracker.ui.settings.autopause

import android.app.Application
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AutoPauseViewModel state management and DataStore dispatch
 * (REQ-SET-080.2, REQ-SET-080.3, TST-SET-080.2, ATT-4836).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoPauseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockApplication: Application
    private lateinit var mockDataStore: TuningPreferencesDataStore
    private val configFlow = MutableStateFlow(TuningConfig())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockApplication = mockk(relaxed = true)
        mockDataStore = mockk(relaxed = true)
        every { mockDataStore.tuningConfigStateFlow } returns configFlow
    }

    @After
    fun tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
    }

    @Test
    fun testTuningConfigExposesStateFlow() = runTest(testDispatcher) {
        val viewModel = AutoPauseViewModel(mockApplication, mockDataStore)
        testScheduler.advanceUntilIdle()

        assertEquals(configFlow.value, viewModel.tuningConfig.value)

        val updatedConfig = TuningConfig(autoPauseBikeEnabled = false, autoPauseBikeSpeedThreshold = 4)
        configFlow.value = updatedConfig
        testScheduler.advanceUntilIdle()

        assertEquals(updatedConfig, viewModel.tuningConfig.value)
    }

    @Test
    fun testUpdateAutoPauseBikeSettings() = runTest(testDispatcher) {
        val viewModel = AutoPauseViewModel(mockApplication, mockDataStore)

        viewModel.updateAutoPauseBikeEnabled(false)
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { mockDataStore.updateAutoPauseBikeEnabled(false) }

        viewModel.updateAutoPauseBikeSpeedThreshold(4)
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { mockDataStore.updateAutoPauseBikeSpeedThreshold(4) }

        viewModel.updateAutoPauseBikeDebounceSec(12)
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { mockDataStore.updateAutoPauseBikeDebounceSec(12) }

        viewModel.updateAutoPauseBikeResumeDebounceSec(6)
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { mockDataStore.updateAutoPauseBikeResumeDebounceSec(6) }
    }

    @Test
    fun testUpdateAutoPauseRunSettings() = runTest(testDispatcher) {
        val viewModel = AutoPauseViewModel(mockApplication, mockDataStore)

        viewModel.updateAutoPauseRunEnabled(true)
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { mockDataStore.updateAutoPauseRunEnabled(true) }

        viewModel.updateAutoPauseRunSpeedThreshold(3)
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { mockDataStore.updateAutoPauseRunSpeedThreshold(3) }

        viewModel.updateAutoPauseRunDebounceSec(8)
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { mockDataStore.updateAutoPauseRunDebounceSec(8) }

        viewModel.updateAutoPauseRunResumeDebounceSec(7)
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { mockDataStore.updateAutoPauseRunResumeDebounceSec(7) }
    }

    @Test
    fun testUpdateAutoPauseOtherSettings() = runTest(testDispatcher) {
        val viewModel = AutoPauseViewModel(mockApplication, mockDataStore)

        viewModel.updateAutoPauseOtherEnabled(true)
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { mockDataStore.updateAutoPauseOtherEnabled(true) }

        viewModel.updateAutoPauseOtherSpeedThreshold(2)
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { mockDataStore.updateAutoPauseOtherSpeedThreshold(2) }

        viewModel.updateAutoPauseOtherDebounceSec(25)
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { mockDataStore.updateAutoPauseOtherDebounceSec(25) }

        viewModel.updateAutoPauseOtherResumeDebounceSec(15)
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { mockDataStore.updateAutoPauseOtherResumeDebounceSec(15) }
    }

    @Test
    fun testResetToDefaults() = runTest(testDispatcher) {
        val viewModel = AutoPauseViewModel(mockApplication, mockDataStore)

        viewModel.resetToDefaults(BSportType.BIKE)
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { mockDataStore.resetAutoPausePreferences(BSportType.BIKE) }

        viewModel.resetToDefaults(null)
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { mockDataStore.resetAutoPausePreferences(null) }
    }
}
