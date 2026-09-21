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

package com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.tracking.getDisplayName

/**
 * Modernized bottom sheet dialog for configuring a tracking grid sensor field (REQ-UI-149, TST-UI-102).
 * Allows selecting the sensor type, source device, display text size, and smoothing filter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSensorFieldDialog(
    title: String,
    viewModel: EditSensorFieldViewModel,
    onDismissRequest: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    AppModalBottomSheet(
        onDismissRequest = onDismissRequest,
        title = title,
        icon = Icons.Default.Edit,
        actions = {
            AppDialogActions.SaveCancel(
                onSave = {
                    viewModel.saveChanges()
                    onDismissRequest()
                },
                onCancel = onDismissRequest,
                saveText = stringResource(R.string.save)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Sensor Type Spinner ---
            Spinner(
                label = stringResource(R.string.sensor),
                items = uiState.availableSensorTypesForCurrentActivityType.map { it.getFullName(context) },
                selectedItem = uiState.selectedSensorType?.getFullName(context) ?: "",
                onItemSelected = { index ->
                    viewModel.onSensorTypeChanged(uiState.availableSensorTypesForCurrentActivityType[index])
                }
            )

            Spacer(Modifier.height(12.dp))

            // --- Source Device Spinner ---
            val deviceList = uiState.availableDevices
            if (deviceList.size == 1 && deviceList[0].first == -1L) {
                // when there is only the 'best' sensor available, there is no choice.
            } else {
                Spinner(
                    label = stringResource(R.string.source),
                    items = deviceList.map { it.second },
                    selectedItem = deviceList.find { it.first == uiState.selectedDeviceId }?.second ?: stringResource(R.string.bestSensor),
                    onItemSelected = { index ->
                        viewModel.onDeviceChanged(deviceList[index].first, deviceList[index].second)
                    }
                )

                Spacer(Modifier.height(12.dp))
            }

            // --- View Size Spinner ---
            Spinner(
                label = stringResource(R.string.text_size),
                items = uiState.availableViewSizes.map { it.getDisplayName(context) },
                selectedItem = uiState.selectedViewSize.getDisplayName(context),
                onItemSelected = { index ->
                    viewModel.onViewSizeChanged(uiState.availableViewSizes[index])
                }
            )

            Spacer(Modifier.height(16.dp))

            // --- Optionally: Configure Filter Button ---
            if (uiState.selectedSensorType?.filteringPossible == true) {
                OutlinedButton(
                    onClick = { viewModel.onConfigureFilterClicked() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${stringResource(R.string.filter)}: ${uiState.filterSummary}")
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // when necessary, also show the ConfigFilterDialog.
    if (uiState.showFilterConfigDialog) {
        ConfigureFilterDialog(
            viewModel = viewModel,
            onDismissRequest = { viewModel.onFilterConfigDismissed() },
            onSave = {
                viewModel.onSaveFilterConfig()
            }
        )
    }
}

// Helper composable for a dropdown spinner
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Spinner(
    label: String,
    items: List<String>,
    selectedItem: String,
    onItemSelected: (index: Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            value = selectedItem,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEachIndexed { index, text ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onItemSelected(index)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}