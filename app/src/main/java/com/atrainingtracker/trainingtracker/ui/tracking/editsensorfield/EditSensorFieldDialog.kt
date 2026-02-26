package com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.atrainingtracker.R


@Composable
fun EditSensorFieldDialog(
    viewModel: EditSensorFieldViewModel,
    onDismissRequest: () -> Unit,
    onConfigureFilter: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.edit_field),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // --- Sensor Type Spinner ---
                Spinner(
                    label = stringResource(R.string.sensor),
                    items = uiState.availableSensorTypes.map { it.getShortName(context) },
                    selectedItem = uiState.selectedSensorType?.getShortName(context) ?: "",
                    onItemSelected = { index ->
                        viewModel.onSensorTypeChanged(uiState.availableSensorTypes[index])
                    }
                )

                Spacer(Modifier.height(8.dp))

                // --- Device Source Spinner ---
                val deviceList = uiState.availableDevices
                if (deviceList.isNotEmpty()) {
                    Spinner(
                        label = stringResource(R.string.source),
                        items = deviceList.map { it.second }, // Names
                        selectedItem = deviceList.find { it.first == uiState.selectedDeviceId }?.second ?: "",
                        onItemSelected = { index ->
                            viewModel.onDeviceChanged(deviceList[index].first)
                        }
                    )
                } else {
                    // Handle case with no external sensors (e.g., Time)
                    Text(stringResource(R.string.smartphone))
                }

                Spacer(Modifier.height(8.dp))

                // --- View Size Spinner ---
                Spinner(
                    label = stringResource(R.string.text_size),
                    items = uiState.availableViewSizes.map { it.name },
                    selectedItem = uiState.selectedViewSize.name,
                    onItemSelected = { index ->
                        viewModel.onViewSizeChanged(uiState.availableViewSizes[index])
                    }
                )

                Spacer(Modifier.height(16.dp))

                // --- Configure Filter Button ---
                if (uiState.selectedSensorType?.filteringPossible == true) {
                    OutlinedButton(
                        onClick = onConfigureFilter,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${stringResource(R.string.filter)}: ${uiState.filterSummary}")
                    }
                }

                Spacer(Modifier.height(16.dp))

                // --- Action Buttons (OK/Cancel) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.Cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        viewModel.saveChanges()
                        onDismissRequest() // Close dialog after saving
                    }) {
                        Text(stringResource(R.string.OK))
                    }
                }
            }
        }
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