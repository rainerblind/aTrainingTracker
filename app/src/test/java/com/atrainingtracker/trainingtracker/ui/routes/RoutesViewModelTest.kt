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

package com.atrainingtracker.trainingtracker.ui.routes

import android.app.Application
import android.net.Uri
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.routes.GpxRouteImporter
import com.atrainingtracker.trainingtracker.routes.RouteImportResult
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying batch GPX route importing contracts in [RoutesViewModel]
 * (REQ-MAP-053, TST-MAP-055).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoutesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockRoutesRepo = mockk<RoutesRepository>(relaxed = true)
    private val mockBanalRepo = mockk<BANALServiceRepository>(relaxed = true)
    private val mockSegmentsRepo = mockk<SegmentsRepository>(relaxed = true)
    private val mockImporter = mockk<GpxRouteImporter>(relaxed = true)

    private var activeViewModel: RoutesViewModel? = null

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
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkObject(RoutesRepository.Companion)
        mockkObject(BANALServiceRepository.Companion)
        mockkObject(SegmentsRepository.Companion)
        mockkConstructor(MyPreferenceManager::class)

        every { RoutesRepository.getInstance(any()) } returns mockRoutesRepo
        every { BANALServiceRepository.getInstance(any()) } returns mockBanalRepo
        every { SegmentsRepository.getInstance(any()) } returns mockSegmentsRepo

        every { mockRoutesRepo.allRoutes } returns MutableStateFlow(emptyList())
        every { mockRoutesRepo.activeNavigatedRouteId } returns MutableStateFlow(null)
        every { mockBanalRepo.currentLocation } returns MutableStateFlow(null)
        every { mockSegmentsRepo.allSegmentsWithPath } returns MutableStateFlow(emptyList())
        every { anyConstructed<MyPreferenceManager>().routeFilterCriteriaFlow } returns MutableStateFlow(RouteFilterCriteria())
    }

    private fun createViewModel(): RoutesViewModel {
        return RoutesViewModel(
            application = mockApplication,
            routesRepository = mockRoutesRepo,
            banalServiceRepository = mockBanalRepo,
            segmentsRepository = mockSegmentsRepo,
            gpxRouteImporter = mockImporter
        ).also { activeViewModel = it }
    }

    @After
    fun tearDown() {
        activeViewModel?.viewModelScope?.cancel()
        activeViewModel = null
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
        unmockkAll()
    }

    private fun createRouteData(idStr: String): RouteImportResult {
        val summary = RouteSummary(
            id = 0,
            externalId = "$idStr.gpx",
            name = "Route $idStr",
            description = "Imported route $idStr",
            isSelected = false,
            distance = 5000.0,
            elevationGain = 100.0,
            bSportType = BSportType.BIKE,
            source = RouteSource.LOCAL_GPX
        )
        val points = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 500.0)
        )
        return RouteImportResult(summary = summary, pathPoints = points, waypoints = emptyList())
    }

    @Test
    fun testBatchImport_allValidFiles_TST_MAP_055_1() = runTest(testDispatcher) {
        val uri1 = mockk<Uri>()
        val uri2 = mockk<Uri>()
        val uri3 = mockk<Uri>()

        val route1 = createRouteData("1")
        val route2 = createRouteData("2")
        val route3 = createRouteData("3")

        coEvery { mockImporter.importRouteFromGpx(uri1) } returns Result.success(route1)
        coEvery { mockImporter.importRouteFromGpx(uri2) } returns Result.success(route2)
        coEvery { mockImporter.importRouteFromGpx(uri3) } returns Result.success(route3)
        coEvery { mockRoutesRepo.insertRoute(any(), any(), any()) } returns 1L

        val viewModel = createViewModel()
        viewModel.importRoutesBatch(listOf(uri1, uri2, uri3))
        advanceUntilIdle()

        coVerify(exactly = 1) { mockRoutesRepo.insertRoute(route1.summary, route1.pathPoints, route1.waypoints) }
        coVerify(exactly = 1) { mockRoutesRepo.insertRoute(route2.summary, route2.pathPoints, route2.waypoints) }
        coVerify(exactly = 1) { mockRoutesRepo.insertRoute(route3.summary, route3.pathPoints, route3.waypoints) }

        assertNull(viewModel.batchImportProgress.value)
        assertEquals(
            BatchImportResult(importedCount = 3, failedCount = 0, totalCount = 3),
            viewModel.batchImportResult.value
        )
    }

    @Test
    fun testBatchImport_partialFailure_TST_MAP_055_2() = runTest(testDispatcher) {
        val uri1 = mockk<Uri>()
        val uri2 = mockk<Uri>()
        val uri3 = mockk<Uri>()

        val route1 = createRouteData("1")
        val route3 = createRouteData("3")

        coEvery { mockImporter.importRouteFromGpx(uri1) } returns Result.success(route1)
        coEvery { mockImporter.importRouteFromGpx(uri2) } returns Result.failure(Exception("Corrupt XML"))
        coEvery { mockImporter.importRouteFromGpx(uri3) } returns Result.success(route3)
        coEvery { mockRoutesRepo.insertRoute(any(), any(), any()) } returns 1L

        val viewModel = createViewModel()
        viewModel.importRoutesBatch(listOf(uri1, uri2, uri3))
        advanceUntilIdle()

        coVerify(exactly = 1) { mockRoutesRepo.insertRoute(route1.summary, route1.pathPoints, route1.waypoints) }
        coVerify(exactly = 0) { mockRoutesRepo.insertRoute(match { it.externalId == "2.gpx" }, any(), any()) }
        coVerify(exactly = 1) { mockRoutesRepo.insertRoute(route3.summary, route3.pathPoints, route3.waypoints) }

        assertNull(viewModel.batchImportProgress.value)
        assertEquals(
            BatchImportResult(importedCount = 2, failedCount = 1, totalCount = 3),
            viewModel.batchImportResult.value
        )
    }

    @Test
    fun testBatchImport_allFilesFailed_TST_MAP_055_3() = runTest(testDispatcher) {
        val uri1 = mockk<Uri>()
        val uri2 = mockk<Uri>()
        val uri3 = mockk<Uri>()

        coEvery { mockImporter.importRouteFromGpx(uri1) } returns Result.failure(Exception("Error 1"))
        coEvery { mockImporter.importRouteFromGpx(uri2) } returns Result.failure(Exception("Error 2"))
        coEvery { mockImporter.importRouteFromGpx(uri3) } returns Result.failure(Exception("Error 3"))

        val viewModel = createViewModel()
        viewModel.importRoutesBatch(listOf(uri1, uri2, uri3))
        advanceUntilIdle()

        coVerify(exactly = 0) { mockRoutesRepo.insertRoute(any(), any(), any()) }

        assertNull(viewModel.batchImportProgress.value)
        assertEquals(
            BatchImportResult(importedCount = 0, failedCount = 3, totalCount = 3),
            viewModel.batchImportResult.value
        )
    }

    @Test
    fun testBatchImport_emptyUriList_TST_MAP_055_4() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.importRoutesBatch(emptyList())
        advanceUntilIdle()

        coVerify(exactly = 0) { mockImporter.importRouteFromGpx(any()) }
        coVerify(exactly = 0) { mockRoutesRepo.insertRoute(any(), any(), any()) }
        assertNull(viewModel.batchImportProgress.value)
        assertNull(viewModel.batchImportResult.value)
    }

    @Test
    fun testBatchImport_clearBatchImportResult_TST_MAP_055_5() = runTest(testDispatcher) {
        val uri1 = mockk<Uri>()
        val route1 = createRouteData("1")
        coEvery { mockImporter.importRouteFromGpx(uri1) } returns Result.success(route1)
        coEvery { mockRoutesRepo.insertRoute(any(), any(), any()) } returns 1L

        val viewModel = createViewModel()
        viewModel.importRoutesBatch(listOf(uri1))
        advanceUntilIdle()

        assertEquals(
            BatchImportResult(importedCount = 1, failedCount = 0, totalCount = 1),
            viewModel.batchImportResult.value
        )

        viewModel.clearBatchImportResult()
        assertNull(viewModel.batchImportResult.value)
    }
}
