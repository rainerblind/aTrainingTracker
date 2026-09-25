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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * Modernized bottom sheet dialog for configuring sensor filter smoothing (REQ-UI-149, REQ-UI-167, TST-UI-119).
 * Features 1-tap quick presets, live explanatory guidance, and expandable custom expert controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigureFilterDialog(
    viewModel: EditSensorFieldViewModel,
    onDismissRequest: () -> Unit,
    onSave: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    ConfigureFilterDialogContent(
        uiState = uiState,
        onPresetSelected = { viewModel.onPresetSelected(it) },
        onFilterTypeChanged = { viewModel.onFilterTypeChanged(it) },
        onFilterConstantChanged = { viewModel.onFilterConstantChanged(it) },
        onUnitChanged = { viewModel.onUnitChanged(it) },
        onDismissRequest = onDismissRequest,
        onSave = onSave
    )
}

/**
 * Stateless content composable for ConfigureFilterDialog to support Compose Previews and isolated unit testing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigureFilterDialogContent(
    uiState: EditDialogUiState,
    onPresetSelected: (FilterPreset) -> Unit,
    onFilterTypeChanged: (FilterType) -> Unit,
    onFilterConstantChanged: (Double) -> Unit,
    onUnitChanged: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current

    val filterTypes = remember {
        listOf(
            FilterType.INSTANTANEOUS,
            FilterType.AVERAGE,
            FilterType.MOVING_AVERAGE_TIME,
            FilterType.EXPONENTIAL_SMOOTHING,
            FilterType.MAX_VALUE
        )
    }

    // Determine the actual filter type based on the UI selection
    val finalFilterType = if (uiState.selectedFilterType == FilterType.MOVING_AVERAGE_TIME && uiState.movingAverageUnit == "samples") {
        FilterType.MOVING_AVERAGE_NUMBER
    } else {
        uiState.selectedFilterType
    }

    val finalConstant = when (uiState.selectedFilterType) {
        FilterType.MOVING_AVERAGE_TIME -> {
            if (uiState.movingAverageUnit == "min") uiState.filterConstant * 60 else uiState.filterConstant
        }
        FilterType.EXPONENTIAL_SMOOTHING -> {
            uiState.filterConstant.coerceIn(0.01, 1.0)
        }
        else -> uiState.filterConstant
    }

    AppModalBottomSheet(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.filter_configure_smoothing),
        icon = Icons.Default.FilterAlt,
        actions = {
            AppDialogActions.SaveCancel(
                onSave = onSave,
                onCancel = onDismissRequest,
                saveText = stringResource(R.string.save)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            // 1. Quick Presets Header
            Text(
                text = stringResource(R.string.filter_presets_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(8.dp))

            // Quick Preset Chips (FlowRow with all 7 presets)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterPreset.entries.forEach { preset ->
                    val isSelected = uiState.activePreset == preset
                    FilterChip(
                        selected = isSelected,
                        onClick = { onPresetSelected(preset) },
                        label = { Text(stringResource(preset.labelResId)) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 2. Explanatory Guidance Surface Card
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = finalFilterType.getSummary(context, finalConstant),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = finalFilterType.getDetails(context, finalConstant),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 3. Expandable Custom / Expert Configuration Section
            AnimatedVisibility(
                visible = uiState.activePreset == FilterPreset.CUSTOM || uiState.isCustomFilterExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.filter_custom_header),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(12.dp))

                    // Filter Type Spinner
                    FilterTypeSpinner(
                        items = filterTypes.map { it.getDisplayName(context) },
                        selectedItem = uiState.selectedFilterType.getDisplayName(context),
                        onItemSelected = { index ->
                            onFilterTypeChanged(filterTypes[index])
                        }
                    )

                    Spacer(Modifier.height(12.dp))

                    // Constant and Unit inputs (conditionally visible)
                    when (finalFilterType) {
                        FilterType.MOVING_AVERAGE_TIME, FilterType.MOVING_AVERAGE_NUMBER -> {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = uiState.filterConstant.toInt().toString(),
                                    onValueChange = { textValue ->
                                        val intValue = textValue.filter { it.isDigit() }.toIntOrNull() ?: 1
                                        onFilterConstantChanged(intValue.toDouble())
                                    },
                                    label = { Text(stringResource(R.string.filter_value)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                UnitSpinner(
                                    selectedUnit = uiState.movingAverageUnit,
                                    onUnitSelected = { onUnitChanged(it) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        FilterType.EXPONENTIAL_SMOOTHING -> {
                            var textValue by remember(uiState.filterConstant) {
                                mutableStateOf(uiState.filterConstant.toString())
                            }
                            OutlinedTextField(
                                value = textValue,
                                onValueChange = { newText ->
                                    textValue = newText
                                    val parsedValue = newText.toDoubleOrNull()
                                    if (parsedValue != null && parsedValue > 0.0 && parsedValue <= 1.0) {
                                        onFilterConstantChanged(parsedValue)
                                    }
                                },
                                label = { Text(stringResource(R.string.filter_value)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        else -> {}
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterTypeSpinner(items: List<String>, selectedItem: String, onItemSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedItem,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.filter_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEachIndexed { index, text ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onItemSelected(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitSpinner(selectedUnit: String, onUnitSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val units = listOf("sec", "min", "samples")

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier) {
        OutlinedTextField(
            value = selectedUnit,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.filter_unit)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            units.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit) },
                    onClick = {
                        onUnitSelected(unit)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "ConfigureFilterDialog Light")
@Composable
private fun ConfigureFilterDialogPreviewLight() {
    ATrainingTrackerTheme(darkTheme = false) {
        ConfigureFilterDialogContent(
            uiState = EditDialogUiState(
                selectedFilterType = FilterType.MOVING_AVERAGE_TIME,
                filterConstant = 3.0,
                movingAverageUnit = "sec",
                filterSummary = "3 s moving average"
            ),
            onPresetSelected = {},
            onFilterTypeChanged = {},
            onFilterConstantChanged = {},
            onUnitChanged = {},
            onDismissRequest = {},
            onSave = {}
        )
    }
}

@Preview(showBackground = true, name = "ConfigureFilterDialog Dark")
@Composable
private fun ConfigureFilterDialogPreviewDark() {
    ATrainingTrackerTheme(darkTheme = true) {
        ConfigureFilterDialogContent(
            uiState = EditDialogUiState(
                selectedFilterType = FilterType.MOVING_AVERAGE_TIME,
                filterConstant = 10.0,
                movingAverageUnit = "sec",
                filterSummary = "10 s moving average"
            ),
            onPresetSelected = {},
            onFilterTypeChanged = {},
            onFilterConstantChanged = {},
            onUnitChanged = {},
            onDismissRequest = {},
            onSave = {}
        )
    }
}
