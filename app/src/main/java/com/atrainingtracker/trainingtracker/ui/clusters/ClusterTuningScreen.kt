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

package com.atrainingtracker.trainingtracker.ui.clusters

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClusterTuningScreen(
    viewModel: FrequentPathsViewModel,
    onBack: () -> Unit
) {
    val isRecalculating by viewModel.isRecalculating.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cluster_tuning_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isRecalculating) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
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
                TuningSlider(
                    label = stringResource(R.string.cluster_tuning_endpoint_label),
                    value = viewModel.endpointTolerance,
                    onValueChange = { viewModel.endpointTolerance = it },
                    valueRange = 10f..500f,
                    unit = "m"
                )

                TuningSlider(
                    label = stringResource(R.string.cluster_tuning_apex_label),
                    value = viewModel.apexTolerance,
                    onValueChange = { viewModel.apexTolerance = it },
                    valueRange = 10f..500f,
                    unit = "m"
                )

                TuningSlider(
                    label = stringResource(R.string.cluster_tuning_distance_label),
                    value = viewModel.distanceTolerance,
                    onValueChange = { viewModel.distanceTolerance = it },
                    valueRange = 0.01f..0.2f,
                    unit = "%",
                    displayMultiplier = 100f
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

@Composable
private fun TuningSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    unit: String,
    displayMultiplier: Float = 1f
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${(value * displayMultiplier).toInt()} $unit",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
