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

import android.net.Uri
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.routes.GpxRouteImporter
import com.atrainingtracker.trainingtracker.routes.RouteImportResult
import com.atrainingtracker.trainingtracker.routes.RouteWaypoint
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying GpxImportViewModel intent handling, state emissions,
 * and route saving contracts (REQ-MAP-025 / TST-MAP-027).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GpxImportViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockImporter: GpxRouteImporter
    private lateinit var mockRepository: RoutesRepository
    private lateinit var viewModel: GpxImportViewModel
    private val testUri = mockk<Uri>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockImporter = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)
        viewModel = GpxImportViewModel(mockImporter, mockRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState_isLoading() {
        assertTrue(viewModel.uiState is GpxImportViewModel.ImportState.Loading)
    }

    @Test
    fun testHandleIntent_nullUri_emitsError() {
        viewModel.handleIntent(null)
        assertTrue(viewModel.uiState is GpxImportViewModel.ImportState.Error)
        val error = viewModel.uiState as GpxImportViewModel.ImportState.Error
        assertEquals("No file provided", error.message)
    }

    @Test
    fun testHandleIntent_validGpx_emitsEditingState() = runTest {
        val summary = RouteSummary(
            id = 0,
            externalId = "test.gpx",
            name = "Test Route",
            description = "Enriched elevation route",
            isSelected = false,
            distance = 5000.0,
            elevationGain = 120.0,
            bSportType = BSportType.BIKE,
            source = RouteSource.LOCAL_GPX
        )
        val points = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 500.0),
            PathPoint(distance = 5000.0, latLng = LatLng(48.05, 11.05), altitude = 620.0)
        )

        coEvery { mockImporter.importRouteFromGpx(testUri) } returns Result.success(RouteImportResult(summary, points))

        viewModel.handleIntent(testUri)
        advanceUntilIdle()

        assertTrue(viewModel.uiState is GpxImportViewModel.ImportState.Editing)
        val editing = viewModel.uiState as GpxImportViewModel.ImportState.Editing
        assertEquals("Test Route", editing.summary.name)
        assertEquals(120.0, editing.summary.elevationGain, 0.001)
        assertEquals(2, editing.points.size)
        assertEquals(500.0, editing.points[0].altitude, 0.001)
        assertEquals(620.0, editing.points[1].altitude, 0.001)
    }

    @Test
    fun testHandleIntent_parseFailure_emitsError() = runTest {
        coEvery { mockImporter.importRouteFromGpx(testUri) } returns
                Result.failure(Exception("Corrupted GPX XML"))

        viewModel.handleIntent(testUri)
        advanceUntilIdle()

        assertTrue(viewModel.uiState is GpxImportViewModel.ImportState.Error)
        val error = viewModel.uiState as GpxImportViewModel.ImportState.Error
        assertEquals("Corrupted GPX XML", error.message)
    }

    @Test
    fun testSaveRoute_delegatesToRepository_andEmitsSuccess() = runTest {
        val summary = RouteSummary(
            id = 0,
            externalId = "test.gpx",
            name = "Test Route",
            description = "Enriched elevation route",
            isSelected = false,
            distance = 5000.0,
            elevationGain = 120.0,
            bSportType = BSportType.BIKE,
            source = RouteSource.LOCAL_GPX
        )
        val points = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 500.0)
        )

        coEvery { mockRepository.insertRoute(summary, points, emptyList()) } returns 42L

        viewModel.saveRoute(summary, points)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockRepository.insertRoute(summary, points, emptyList()) }
        assertTrue(viewModel.uiState is GpxImportViewModel.ImportState.Success)
    }

    @Test
    fun testSaveRoute_withWaypoints_delegatesToRepository_andEmitsSuccess() = runTest {
        val summary = RouteSummary(
            id = 0,
            externalId = "jusi.gpx",
            name = "Gipfelkreuz auf dem Jusi",
            description = "Schwäbische Alb",
            isSelected = false,
            distance = 8500.0,
            elevationGain = 320.0,
            bSportType = BSportType.BIKE,
            source = RouteSource.LOCAL_GPX
        )
        val points = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.553, 9.330), altitude = 450.0)
        )
        val waypoints = listOf(
            RouteWaypoint(
                id = 1L,
                routeId = 0L,
                latLng = LatLng(48.5527, 9.3347),
                altitude = 673.0,
                name = "Gipfelkreuz auf dem Jusi",
                type = com.atrainingtracker.trainingtracker.routes.WaypointType.POI_SUMMIT
            )
        )

        coEvery { mockRepository.insertRoute(summary, points, waypoints) } returns 100L

        viewModel.saveRoute(summary, points, waypoints)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockRepository.insertRoute(summary, points, waypoints) }
        assertTrue(viewModel.uiState is GpxImportViewModel.ImportState.Success)
    }

    @Test
    fun testHandleBatchUris_emptyList_emitsError() {
        viewModel.handleBatchUris(emptyList())
        assertTrue(viewModel.uiState is GpxImportViewModel.ImportState.Error)
        val error = viewModel.uiState as GpxImportViewModel.ImportState.Error
        assertEquals("No files provided", error.message)
    }

    @Test
    fun testHandleBatchUris_singleUri_delegatesToHandleIntent() = runTest {
        val summary = RouteSummary(
            id = 0,
            externalId = "single.gpx",
            name = "Single Route",
            description = "Single route description",
            isSelected = false,
            distance = 3000.0,
            elevationGain = 50.0,
            bSportType = BSportType.BIKE,
            source = RouteSource.LOCAL_GPX
        )
        val points = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 500.0)
        )
        coEvery { mockImporter.importRouteFromGpx(testUri) } returns Result.success(RouteImportResult(summary, points))

        viewModel.handleBatchUris(listOf(testUri))
        advanceUntilIdle()

        assertTrue(viewModel.uiState is GpxImportViewModel.ImportState.Editing)
    }

    @Test
    fun testHandleBatchUris_multipleValidUris_insertsAllAndEmitsBatchSuccess() = runTest {
        val uri1 = mockk<Uri>()
        val uri2 = mockk<Uri>()
        val summary1 = RouteSummary(id = 0, externalId = "1.gpx", name = "Route 1", description = "Desc 1", isSelected = false, distance = 1000.0, elevationGain = 10.0, bSportType = BSportType.BIKE, source = RouteSource.LOCAL_GPX)
        val summary2 = RouteSummary(id = 0, externalId = "2.gpx", name = "Route 2", description = "Desc 2", isSelected = false, distance = 2000.0, elevationGain = 20.0, bSportType = BSportType.BIKE, source = RouteSource.LOCAL_GPX)
        val points1 = listOf(PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 500.0))
        val points2 = listOf(PathPoint(distance = 0.0, latLng = LatLng(48.1, 11.1), altitude = 510.0))

        coEvery { mockImporter.importRouteFromGpx(uri1) } returns Result.success(RouteImportResult(summary1, points1))
        coEvery { mockImporter.importRouteFromGpx(uri2) } returns Result.success(RouteImportResult(summary2, points2))
        coEvery { mockRepository.insertRoute(any(), any(), any()) } returns 1L

        viewModel.handleBatchUris(listOf(uri1, uri2))
        advanceUntilIdle()

        coVerify(exactly = 1) { mockRepository.insertRoute(summary1, points1, emptyList()) }
        coVerify(exactly = 1) { mockRepository.insertRoute(summary2, points2, emptyList()) }
        assertTrue(viewModel.uiState is GpxImportViewModel.ImportState.BatchSuccess)
        val state = viewModel.uiState as GpxImportViewModel.ImportState.BatchSuccess
        assertEquals(2, state.importedCount)
        assertEquals(0, state.failedCount)
    }

    @Test
    fun testHandleBatchUris_partialFailure_insertsSuccessfulAndReportsFailed() = runTest {
        val uriValid = mockk<Uri>()
        val uriCorrupted = mockk<Uri>()
        val summary = RouteSummary(id = 0, externalId = "valid.gpx", name = "Valid Route", description = "Desc valid", isSelected = false, distance = 1000.0, elevationGain = 10.0, bSportType = BSportType.BIKE, source = RouteSource.LOCAL_GPX)
        val points = listOf(PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 500.0))

        coEvery { mockImporter.importRouteFromGpx(uriValid) } returns Result.success(RouteImportResult(summary, points))
        coEvery { mockImporter.importRouteFromGpx(uriCorrupted) } returns Result.failure(Exception("Bad XML"))
        coEvery { mockRepository.insertRoute(any(), any(), any()) } returns 1L

        viewModel.handleBatchUris(listOf(uriValid, uriCorrupted))
        advanceUntilIdle()

        coVerify(exactly = 1) { mockRepository.insertRoute(summary, points, emptyList()) }
        assertTrue(viewModel.uiState is GpxImportViewModel.ImportState.BatchSuccess)
        val state = viewModel.uiState as GpxImportViewModel.ImportState.BatchSuccess
        assertEquals(1, state.importedCount)
        assertEquals(1, state.failedCount)
    }
}

