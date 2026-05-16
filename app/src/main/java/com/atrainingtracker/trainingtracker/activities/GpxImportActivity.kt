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

package com.atrainingtracker.trainingtracker.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import com.atrainingtracker.trainingtracker.ui.routes.EditRouteScreen
import com.atrainingtracker.trainingtracker.ui.routes.GpxImportViewModel
import com.atrainingtracker.trainingtracker.ui.routes.GpxImportViewModelFactory
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

class GpxImportActivity : ComponentActivity() {

    private val viewModel: GpxImportViewModel by viewModels {
        GpxImportViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle the incoming intent data
        val uri = intent.data ?: intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
        viewModel.handleIntent(uri)

        setContent {
            ATrainingTrackerTheme {
                when (val state = viewModel.uiState) {
                    is GpxImportViewModel.ImportState.Loading -> {
                        CircularProgressIndicator()
                    }
                    is GpxImportViewModel.ImportState.Editing -> {
                        EditRouteScreen(
                            routeSummary = state.summary,
                            onSave = { updatedSummary ->
                                viewModel.saveRoute(updatedSummary, state.points)
                            },
                            onCancel = { finish() }
                        )
                    }
                    is GpxImportViewModel.ImportState.Saving -> {
                        CircularProgressIndicator()
                    }
                    is GpxImportViewModel.ImportState.Success -> {
                        LaunchedEffect(Unit) {
                            Toast.makeText(this@GpxImportActivity, "Route Imported!", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                    is GpxImportViewModel.ImportState.Error -> {
                        // Show error and finish
                        Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        }
    }
}