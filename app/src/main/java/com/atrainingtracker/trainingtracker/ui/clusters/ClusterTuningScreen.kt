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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.banalservice.BANALService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClusterTuningScreen(
    viewModel: WorkoutClustersViewModel,
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
                ClusterTuningContent(
                    endpointTolerance = viewModel.endpointTolerance,
                    onEndpointToleranceChange = { viewModel.endpointTolerance = it },
                    apexTolerance = viewModel.apexTolerance,
                    onApexToleranceChange = { viewModel.apexTolerance = it },
                    distanceTolerance = viewModel.distanceTolerance,
                    onDistanceToleranceChange = { viewModel.distanceTolerance = it }
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
fun ClusterTuningContent(
    endpointTolerance: Float,
    onEndpointToleranceChange: (Float) -> Unit,
    apexTolerance: Float,
    onApexToleranceChange: (Float) -> Unit,
    distanceTolerance: Float,
    onDistanceToleranceChange: (Float) -> Unit
) {
    var showDetails by remember { mutableStateOf(false) }

    // Constants for mapping
    val endRange = 10f..500f
    val apexRange = 10f..500f
    val distRange = 0.01f..0.2f

    // Derived master sensitivity (average of normalized values)
    val currentMasterValue = remember(endpointTolerance, apexTolerance, distanceTolerance) {
        val nEnd = (endpointTolerance - endRange.start) / (endRange.endInclusive - endRange.start)
        val nApex = (apexTolerance - apexRange.start) / (apexRange.endInclusive - apexRange.start)
        val nDist = (distanceTolerance - distRange.start) / (distRange.endInclusive - distRange.start)
        (nEnd + nApex + nDist) / 3f
    }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // 1. Master Slider
        Column {
            Text(
                text = stringResource(R.string.cluster_tuning_master_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = currentMasterValue,
                onValueChange = { sensitivity ->
                    onEndpointToleranceChange(endRange.start + (endRange.endInclusive - endRange.start) * sensitivity)
                    onApexToleranceChange(apexRange.start + (apexRange.endInclusive - apexRange.start) * sensitivity)
                    onDistanceToleranceChange(distRange.start + (distRange.endInclusive - distRange.start) * sensitivity)
                },
                valueRange = 0f..1f
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.cluster_tuning_strict), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.cluster_tuning_relaxed), style = MaterialTheme.typography.labelSmall)
            }
        }

        // 2. Details Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.cluster_tuning_show_details), style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = showDetails,
                onCheckedChange = { showDetails = it }
            )
        }

        // 3. Individual Sliders (Optional)
        if (showDetails) {
            val isImperial = TrainingApplication.getUnit() == MyUnits.IMPERIAL
            val lengthUnit = stringResource(if (isImperial) R.string.units_distance_imperial else R.string.units_distance_metric)
            val lengthMultiplier = if (isImperial) (1.0 / BANALService.METER_PER_MILE).toFloat() else 0.001f

            TuningSlider(
                label = stringResource(R.string.cluster_tuning_endpoint_label),
                value = endpointTolerance,
                onValueChange = onEndpointToleranceChange,
                valueRange = endRange,
                unit = lengthUnit,
                displayMultiplier = lengthMultiplier,
                decimalPlaces = 3
            )

            TuningSlider(
                label = stringResource(R.string.cluster_tuning_apex_label),
                value = apexTolerance,
                onValueChange = onApexToleranceChange,
                valueRange = apexRange,
                unit = lengthUnit,
                displayMultiplier = lengthMultiplier,
                decimalPlaces = 3
            )

            TuningSlider(
                label = stringResource(R.string.cluster_tuning_distance_label),
                value = distanceTolerance,
                onValueChange = onDistanceToleranceChange,
                valueRange = distRange,
                unit = stringResource(R.string.units_percent),
                displayMultiplier = 100f,
                decimalPlaces = 0
            )
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
    displayMultiplier: Float = 1f,
    decimalPlaces: Int = 0
) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = java.lang.String.format(locale, "%.${decimalPlaces}f %s", value * displayMultiplier, unit),
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
