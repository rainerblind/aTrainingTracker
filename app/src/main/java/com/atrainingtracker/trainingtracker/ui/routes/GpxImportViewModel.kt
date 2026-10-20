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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.routes.GpxRouteImporter
import com.atrainingtracker.trainingtracker.routes.RouteWaypoint
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import kotlinx.coroutines.launch

/**
 * ViewModel orchestrating single and batch GPX/TCX route import flows (REQ-MAP-053).
 *
 * Coordinates file parsing, sport type prompt state, headless batch importing,
 * and sequential interactive review across both in-app and external share entry points.
 *
 * @param importer Route parser and DEM elevation enrichment engine.
 * @param repository Route persistence repository.
 */
class GpxImportViewModel(
    private val importer: GpxRouteImporter,
    private val repository: RoutesRepository
) : ViewModel() {

    var uiState by mutableStateOf<ImportState>(ImportState.Loading)
        private set

    private var individualQueue: List<Uri> = emptyList()
    private var individualCurrentIndex: Int = 0
    private var individualImportedCount: Int = 0
    private var individualFailedCount: Int = 0
    private var currentIndividualPoints: List<PathPoint> = emptyList()
    private var currentIndividualWaypoints: List<RouteWaypoint> = emptyList()

    /**
     * UI states representing the various stages of the route import workflow.
     */
    sealed class ImportState {
        /** Initial loading or file parsing state. */
        object Loading : ImportState()

        /** Prompting athlete for batch sport type mode (REQ-MAP-053.6). */
        data class PromptSportType(val uris: List<Uri>) : ImportState()

        /** Single route interactive editing state. */
        data class Editing(
            val summary: RouteSummary,
            val points: List<PathPoint>,
            val waypoints: List<RouteWaypoint> = emptyList()
        ) : ImportState()

        /** Sequential individual review state during batch import (REQ-MAP-053.5). */
        data class EditingIndividual(
            val summary: RouteSummary,
            val points: List<PathPoint>,
            val waypoints: List<RouteWaypoint> = emptyList(),
            val current: Int,
            val total: Int
        ) : ImportState()

        /** Persistence save in progress. */
        object Saving : ImportState()

        /** Single route successfully saved. */
        object Success : ImportState()

        /** Headless batch import in progress with live file counter. */
        data class BatchProgress(val current: Int, val total: Int) : ImportState()

        /** Batch import completed with total imported and failed counts. */
        data class BatchSuccess(val importedCount: Int, val failedCount: Int) : ImportState()

        /** Terminal error state displaying localized error message. */
        data class Error(val message: String) : ImportState()
    }

    /**
     * Transitions state to display [BatchImportSportTypeDialog] for the given [uris].
     */
    fun promptSportType(uris: List<Uri>) {
        uiState = ImportState.PromptSportType(uris)
    }

    /**
     * Executes headless batch import for multiple [uris], optionally applying [sportType] (REQ-MAP-053.3, REQ-MAP-053.5).
     *
     * @param uris List of route [Uri]s to parse and save.
     * @param sportType Target sport type to assign to all imported routes, defaulting to [BSportType.UNKNOWN].
     */
    fun handleBatchUris(uris: List<Uri>, sportType: BSportType = BSportType.UNKNOWN) {
        if (uris.isEmpty()) {
            uiState = ImportState.Error("No files provided")
            return
        }
        if (uris.size == 1) {
            handleIntent(uris.first())
            return
        }
        viewModelScope.launch {
            var imported = 0
            var failed = 0
            val total = uris.size
            for ((index, uri) in uris.withIndex()) {
                uiState = ImportState.BatchProgress(index + 1, total)
                try {
                    val result = importer.importRouteFromGpx(uri)
                    if (result.isSuccess) {
                        val data = result.getOrNull()
                        if (data != null) {
                            val targetSummary = if (sportType != BSportType.UNKNOWN) {
                                data.summary.copy(bSportType = sportType)
                            } else {
                                data.summary
                            }
                            repository.insertRoute(targetSummary, data.pathPoints, data.waypoints)
                            imported++
                        } else {
                            failed++
                        }
                    } else {
                        failed++
                    }
                } catch (e: Exception) {
                    failed++
                }
            }
            uiState = ImportState.BatchSuccess(imported, failed)
        }
    }

    /**
     * Begins sequential interactive review of routes in [uris] (REQ-MAP-053.5).
     */
    fun startIndividualImport(uris: List<Uri>) {
        if (uris.isEmpty()) {
            uiState = ImportState.Error("No files provided")
            return
        }
        individualQueue = uris
        individualCurrentIndex = 0
        individualImportedCount = 0
        individualFailedCount = 0
        loadNextIndividualRoute()
    }

    private fun loadNextIndividualRoute() {
        if (individualCurrentIndex >= individualQueue.size) {
            uiState = ImportState.BatchSuccess(individualImportedCount, individualFailedCount)
            return
        }
        val uri = individualQueue[individualCurrentIndex]
        uiState = ImportState.Loading
        viewModelScope.launch {
            try {
                val result = importer.importRouteFromGpx(uri)
                if (result.isSuccess) {
                    val data = result.getOrNull()
                    if (data != null) {
                        currentIndividualPoints = data.pathPoints
                        currentIndividualWaypoints = data.waypoints
                        uiState = ImportState.EditingIndividual(
                            summary = data.summary,
                            points = data.pathPoints,
                            waypoints = data.waypoints,
                            current = individualCurrentIndex + 1,
                            total = individualQueue.size
                        )
                    } else {
                        individualFailedCount++
                        individualCurrentIndex++
                        loadNextIndividualRoute()
                    }
                } else {
                    individualFailedCount++
                    individualCurrentIndex++
                    loadNextIndividualRoute()
                }
            } catch (e: Exception) {
                individualFailedCount++
                individualCurrentIndex++
                loadNextIndividualRoute()
            }
        }
    }

    /**
     * Persists the currently reviewed individual route and advances to the next route in the queue.
     */
    fun saveCurrentIndividualRoute(updatedSummary: RouteSummary) {
        viewModelScope.launch {
            try {
                repository.insertRoute(updatedSummary, currentIndividualPoints, currentIndividualWaypoints)
                individualImportedCount++
            } catch (e: Exception) {
                individualFailedCount++
            } finally {
                individualCurrentIndex++
                loadNextIndividualRoute()
            }
        }
    }

    /**
     * Skips persisting the currently reviewed individual route and advances to the next route in the queue.
     */
    fun skipCurrentIndividualRoute() {
        individualFailedCount++
        individualCurrentIndex++
        loadNextIndividualRoute()
    }

    /**
     * Loads and parses a single GPX [uri] for interactive editing (REQ-MAP-053.2).
     */
    fun handleIntent(uri: Uri?) {
        if (uri == null) {
            uiState = ImportState.Error("No file provided")
            return
        }

        viewModelScope.launch {
            importer.importRouteFromGpx(uri).onSuccess { data ->
                uiState = ImportState.Editing(data.summary, data.pathPoints, data.waypoints)
            }.onFailure {
                uiState = ImportState.Error(it.message ?: "Failed to parse GPX")
            }
        }
    }

    /**
     * Saves a single edited route to repository.
     */
    fun saveRoute(
        summary: RouteSummary,
        points: List<PathPoint>,
        waypoints: List<RouteWaypoint> = emptyList()
    ) {
        uiState = ImportState.Saving
        viewModelScope.launch {
            repository.insertRoute(summary, points, waypoints)
            uiState = ImportState.Success
        }
    }
}

/**
 * Factory for creating [GpxImportViewModel] instances with application context dependencies.
 */
class GpxImportViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GpxImportViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GpxImportViewModel(
                importer = GpxRouteImporter(application),
                repository = RoutesRepository.getInstance(application)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}