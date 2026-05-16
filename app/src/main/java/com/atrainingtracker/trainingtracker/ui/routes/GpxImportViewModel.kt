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
import androidx.activity.result.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.routes.GpxRouteImporter
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import kotlinx.coroutines.launch

class GpxImportViewModel(
    private val importer: GpxRouteImporter, private val repository: RoutesRepository
) : ViewModel() {

    var uiState by mutableStateOf<ImportState>(ImportState.Loading)
        private set

    sealed class ImportState {
        object Loading : ImportState()
        data class Editing(val summary: RouteSummary, val points: List<PathPoint>) : ImportState()
        object Saving : ImportState()
        object Success : ImportState()
        data class Error(val message: String) : ImportState()
    }

    fun handleIntent(uri: Uri?) {
        if (uri == null) {
            uiState = ImportState.Error("No file provided")
            return
        }

        viewModelScope.launch {
            // Using the new ticofab-based parser logic we added to the importer
            importer.importRouteFromGpx(uri).onSuccess { data ->
                // 'data' is a Pair/Triple containing (RouteSummary, List<PathPoint>)
                uiState = ImportState.Editing(data.first, data.second)
            }.onFailure {
                uiState = ImportState.Error(it.message ?: "Failed to parse GPX")
            }
        }
    }

    fun saveRoute(summary: RouteSummary, points: List<PathPoint>) {
        uiState = ImportState.Saving
        viewModelScope.launch {
            repository.insertRoute(summary, points)
            uiState = ImportState.Success
        }
    }
}

class GpxImportViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GpxImportViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GpxImportViewModel(
                // Initialize the importer with application context
                importer = GpxRouteImporter(application),
                // Get the singleton instance of the repository
                repository = RoutesRepository.getInstance(application)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}