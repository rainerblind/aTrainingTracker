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
 */

package com.atrainingtracker.trainingtracker.ui.clusters

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClusterTuningScreen(
    viewModel: WorkoutClustersViewModel,
    onBack: () -> Unit
) {
    val isRecalculating by viewModel.isRecalculating.collectAsState()

    val handleBack = {
        viewModel.saveTuningParameters()
        onBack()
    }

    BackHandler(enabled = !isRecalculating) {
        handleBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveTuningParameters()
        }
    }

    var showInfoDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cluster_tuning_title)) },
                navigationIcon = {
                    IconButton(onClick = handleBack, enabled = !isRecalculating) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }, enabled = !isRecalculating) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.cluster_info_title)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (showInfoDialog) {
            ClusterInfoDialog(onDismissRequest = { showInfoDialog = false })
        }
        if (isRecalculating) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.cluster_tuning_recalculating), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.cluster_tuning_take_while), style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                ClusterTuningContent(
                    endpointTolerance = viewModel.endpointTolerance,
                    onEndpointToleranceChange = { viewModel.endpointTolerance = it },
                    apexTolerance = viewModel.apexTolerance,
                    onApexToleranceChange = { viewModel.apexTolerance = it },
                    distanceTolerance = viewModel.distanceTolerance,
                    onDistanceToleranceChange = { viewModel.distanceTolerance = it },
                    altitudePositionTolerance = viewModel.altitudePositionTolerance,
                    onAltitudePositionToleranceChange = { viewModel.altitudePositionTolerance = it },
                    useSportTypeForClustering = viewModel.useSportTypeForClustering,
                    onUseSportTypeChange = { 
                        viewModel.useSportTypeForClustering = it
                        viewModel.saveTuningParameters()
                    },
                    useAltitudePosForClustering = viewModel.useAltitudePosForClustering,
                    onUseAltitudePosChange = { 
                        viewModel.useAltitudePosForClustering = it
                        viewModel.saveTuningParameters()
                    },
                    onValueChangeFinished = { viewModel.saveTuningParameters() }
                )

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = { viewModel.recalculateClusters() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.cluster_tuning_recalculate_button), color = MaterialTheme.colorScheme.onError)
                }
                
                Text(
                    text = stringResource(R.string.cluster_tuning_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

