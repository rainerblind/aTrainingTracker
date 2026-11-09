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

package com.atrainingtracker.trainingtracker.ui.segments.segmentlist

import android.app.Application
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Unit tests verifying exception containment and coroutine lifecycle contracts
 * in [SegmentListViewModel] (REQ-MAP-061.2, TST-MAP-063, ATT-6165).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SegmentListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockSegmentsRepo = mockk<SegmentsRepository>(relaxed = true)
    private val mockRoutesRepo = mockk<RoutesRepository>(relaxed = true)
    private val mockBanalRepo = mockk<BANALServiceRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkObject(SegmentsRepository.Companion)
        mockkObject(RoutesRepository.Companion)
        mockkObject(BANALServiceRepository.Companion)
        mockkConstructor(MyPreferenceManager::class)

        every { SegmentsRepository.getInstance(any()) } returns mockSegmentsRepo
        every { RoutesRepository.getInstance(any()) } returns mockRoutesRepo
        every { BANALServiceRepository.getInstance(any()) } returns mockBanalRepo

        every { mockSegmentsRepo.connectedToStrava } returns false
        every { mockSegmentsRepo.allSegmentsWithPath } returns MutableStateFlow(emptyList())
        every { mockSegmentsRepo.refreshingSports } returns MutableStateFlow(emptySet())
        every { mockRoutesRepo.allRoutes } returns MutableStateFlow(emptyList())
        every { mockBanalRepo.currentLocation } returns MutableStateFlow(null)
        every { anyConstructed<MyPreferenceManager>().segmentFilterCriteriaFlow } returns MutableStateFlow(SegmentFilterCriteria())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
        unmockkAll()
    }

    private fun createViewModel(): SegmentListViewModel {
        return SegmentListViewModel(
            application = mockApplication,
            segmentsRepository = mockSegmentsRepo,
            routesRepository = mockRoutesRepo,
            banalServiceRepository = mockBanalRepo
        )
    }

    @Test
    fun testInitializationWithoutCrash() {
        val viewModel = createViewModel()
        assertNotNull(viewModel)
    }

    @Test
    fun testOnRefreshCatchesIOExceptionAndLogsError() = runTest(testDispatcher) {
        coEvery { mockSegmentsRepo.syncStarredSegments(any()) } throws IOException("Strava API network timeout")

        val viewModel = createViewModel()
        viewModel.onRefresh(BSportType.BIKE)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockSegmentsRepo.syncStarredSegments(BSportType.BIKE) }
        verify(atLeast = 1) {
            Log.e(eq("SegmentListViewModel"), match { it.contains("Failed to refresh segments") }, any<IOException>())
        }
    }

    @Test
    fun testOnRefreshSuccess() = runTest(testDispatcher) {
        coEvery { mockSegmentsRepo.syncStarredSegments(any()) } returns Unit

        val viewModel = createViewModel()
        viewModel.onRefresh(BSportType.RUN)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockSegmentsRepo.syncStarredSegments(BSportType.RUN) }
        verify(exactly = 0) {
            Log.e(eq("SegmentListViewModel"), any<String>(), any())
        }
    }

    @Test
    fun testOnRefreshRethrowsCancellationException() = runTest(testDispatcher) {
        coEvery { mockSegmentsRepo.syncStarredSegments(any()) } throws CancellationException("Coroutine scope cancelled")

        val viewModel = createViewModel()
        viewModel.onRefresh(BSportType.BIKE)
        advanceUntilIdle()

        // CancellationException must NOT be caught and logged as an error
        verify(exactly = 0) {
            Log.e(eq("SegmentListViewModel"), any<String>(), any<CancellationException>())
        }
    }
}
