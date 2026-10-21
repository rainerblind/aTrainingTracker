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

package com.atrainingtracker.trainingtracker.ui.aftermath.crop

import android.app.Application
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying [CropWorkoutViewModel] concurrency protection:
 * - Synchronous double-tap guard on executeCrop
 * - Rejection of duplicate concurrent crop invocations
 * (REQ-UI-427.4, TST-UI-392.4, ATT-4018).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CropWorkoutViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockRepository = mockk<WorkoutRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
        unmockkAll()
    }

    @Test
    fun executeCrop_setsIsCroppingSynchronouslyAndRejectsDuplicateTap() = runTest(testDispatcher) {
        every { mockRepository.allWorkouts } returns MutableStateFlow(emptyList())
        coEvery { mockRepository.getCropSamplePoints(any()) } returns emptyList()
        coEvery { mockRepository.cropWorkout(any(), any(), any()) } coAnswers {
            kotlinx.coroutines.delay(1000)
            Result.success(Unit)
        }

        val viewModel = CropWorkoutViewModel(mockApplication, 1L, mockRepository)
        val validState = viewModel.uiState.value.copy(isValid = true, totalPoints = 100, startIndex = 10, endIndex = 90)

        val uiStateField = CropWorkoutViewModel::class.java.getDeclaredField("_uiState").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val stateFlow = uiStateField.get(viewModel) as MutableStateFlow<CropWorkoutUiState>
        stateFlow.value = validState

        assertFalse("Initial isCropping must be false", viewModel.uiState.value.isCropping)

        var successCount = 0
        var errorCount = 0

        // First tap
        viewModel.executeCrop(onSuccess = { successCount++ }, onError = { errorCount++ })

        // MUST be true immediately and synchronously without advancing dispatcher (REQ-UI-427.4, TST-UI-392.4)
        assertTrue("isCropping must be set synchronously before coroutine dispatch", viewModel.uiState.value.isCropping)

        // Second tap immediately while first is in-flight
        viewModel.executeCrop(onSuccess = { successCount++ }, onError = { errorCount++ })

        // Complete the in-flight coroutine
        advanceUntilIdle()

        // Verify repository only invoked once
        coVerify(exactly = 1) { mockRepository.cropWorkout(1L, 10, 90) }
        assertEquals(1, successCount)
        assertEquals(0, errorCount)
        assertFalse("isCropping must be reset to false after completion", viewModel.uiState.value.isCropping)
    }
}
