package com.atrainingtracker.trainingtracker.ui.tracking.configfilter

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.filters.FilterType

@Composable
fun ConfigureFilterDialog(
    viewModel: ConfigureFilterViewModel,
    onDismissRequest: () -> Unit,
    onSave: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val filterTypes = remember {
        listOf(
            FilterType.INSTANTANEOUS,
            FilterType.AVERAGE,
            FilterType.MOVING_AVERAGE_TIME, // Represents both time and number initially
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

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 8.dp) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.filter_configure_smoothing),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    textAlign = TextAlign.Center
                )

                // Filter Type Spinner
                FilterTypeSpinner(
                    items = filterTypes.map { it.getDisplayName(context) },
                    selectedItem = uiState.selectedFilterType.getDisplayName(context),
                    onItemSelected = { index ->
                        viewModel.onFilterTypeChanged(filterTypes[index])
                    }
                )

                Spacer(Modifier.height(8.dp))

                // Constant and Unit inputs (conditionally visible)
                when (finalFilterType) {
                    FilterType.MOVING_AVERAGE_TIME, FilterType.MOVING_AVERAGE_NUMBER -> {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = uiState.filterConstant.toInt().toString(),
                                onValueChange = { textValue ->
                                    // Parse the text to an Int, then convert to Double for the ViewModel
                                    val intValue = textValue.filter { it.isDigit() }.toIntOrNull() ?: 1
                                    viewModel.onFilterConstantChanged(intValue.toDouble())},
                                label = { Text(stringResource(R.string.filter_value)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            UnitSpinner(
                                selectedUnit = uiState.movingAverageUnit,
                                onUnitSelected = { viewModel.onUnitChanged(it) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    FilterType.EXPONENTIAL_SMOOTHING -> {
                        OutlinedTextField(
                            value = uiState.filterConstant.toString(),
                            onValueChange = { viewModel.onFilterConstantChanged(it.toDoubleOrNull() ?: 0.1) },
                            label = { Text(stringResource(R.string.filter_value)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    // For INSTANTANEOUS, AVERAGE, MAX_VALUE, nothing is shown
                    else -> {}
                }

                Spacer(Modifier.height(16.dp))

                // This text now gets its values from the ViewModel's state
                val finalConstant = if (uiState.selectedFilterType == FilterType.MOVING_AVERAGE_TIME && uiState.movingAverageUnit == "min") {
                    uiState.filterConstant * 60
                } else {
                    uiState.filterConstant
                }
                Text(
                    text = finalFilterType.getDetails(context, finalConstant),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(24.dp))

                // Action Buttons
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.Cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        // 3. CORRECTED: Simply call the onSave lambda passed from the Fragment.
                        // The Fragment will then call viewModel.saveFilterChanges().
                        onSave()
                    }) {
                        Text(stringResource(R.string.OK))
                    }
                }
            }
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
            modifier = Modifier.fillMaxWidth().menuAnchor()
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
            modifier = Modifier.fillMaxWidth().menuAnchor()
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

// Helper extension functions to get display names and details from resources
private fun FilterType.getDisplayName(context: Context): String {
    val id = when (this) {
        FilterType.INSTANTANEOUS -> R.string.filter_instantaneous
        FilterType.AVERAGE -> R.string.filter_average
        FilterType.MOVING_AVERAGE_TIME, FilterType.MOVING_AVERAGE_NUMBER -> R.string.filter_moving_average
        FilterType.EXPONENTIAL_SMOOTHING -> R.string.filter_exponential_smoothing
        FilterType.MAX_VALUE -> R.string.filter_max
    }
    return context.getString(id)
}

private fun FilterType.getDetails(context: Context, constant: Double): String {
    return when (this) {
        FilterType.INSTANTANEOUS -> context.getString(R.string.filter_details__instantaneous)
        FilterType.AVERAGE -> context.getString(R.string.filter_details__average)
        FilterType.MAX_VALUE -> context.getString(R.string.filter_details__max)
        FilterType.EXPONENTIAL_SMOOTHING -> context.getString(R.string.filter_details__exponential_smoothing)
        FilterType.MOVING_AVERAGE_NUMBER -> context.getString(R.string.filter_details__moving_average_number, constant.toInt())
        FilterType.MOVING_AVERAGE_TIME -> {
            if (constant < 60) {
                context.getString(R.string.filter_details__moving_average_time, constant.toInt(), context.getString(R.string.units_seconds_long))
            } else {
                context.getString(R.string.filter_details__moving_average_time, (constant / 60).toInt(), context.getString(R.string.units_minutes_long))
            }
        }
    }
}