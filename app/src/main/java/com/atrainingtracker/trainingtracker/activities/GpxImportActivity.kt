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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
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
        val uris: List<android.net.Uri> = when (intent.action) {
            android.content.Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)?.filterNotNull() ?: emptyList()
            }
            else -> {
                val single = intent.data ?: intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
                listOfNotNull(single)
            }
        }

        if (uris.size > 1) {
            viewModel.handleBatchUris(uris)
        } else {
            viewModel.handleIntent(uris.firstOrNull())
        }

        setContent {
            ATrainingTrackerTheme {
                when (val state = viewModel.uiState) {
                    is GpxImportViewModel.ImportState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.gpx_enriching_elevation),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    is GpxImportViewModel.ImportState.Editing -> {
                        EditRouteScreen(
                            routeSummary = state.summary,
                            onSave = { updatedSummary ->
                                viewModel.saveRoute(updatedSummary, state.points, state.waypoints)
                            },
                            onCancel = { finish() }
                        )
                    }
                    is GpxImportViewModel.ImportState.Saving -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is GpxImportViewModel.ImportState.Success -> {
                        LaunchedEffect(Unit) {
                            Toast.makeText(this@GpxImportActivity, getString(R.string.route_saved_success), Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                    is GpxImportViewModel.ImportState.BatchProgress -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(
                                        R.string.routes_import_progress,
                                        state.current,
                                        state.total
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    is GpxImportViewModel.ImportState.BatchSuccess -> {
                        LaunchedEffect(Unit) {
                            val message = when {
                                state.failedCount == 0 -> getString(R.string.routes_import_success, state.importedCount)
                                state.importedCount > 0 -> getString(R.string.routes_import_partial, state.importedCount, state.failedCount)
                                else -> getString(R.string.routes_import_all_failed)
                            }
                            Toast.makeText(this@GpxImportActivity, message, Toast.LENGTH_SHORT).show()
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