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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.MyUnits
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.components.DropdownSelector
import com.atrainingtracker.trainingtracker.ui.map.createSensorMarker
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

private enum class SelectionMode { NONE, START, END, APEX }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualClusterScreen(
    viewModel: WorkoutClustersViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sportTypesList = remember { SportTypesRepository.getInstance(context.applicationContext as android.app.Application).sportTypesList }
    val sportNames = remember { sportTypesList.map { it.name } }

    var name by remember { mutableStateOf("") }
    var selectedSportName by remember { mutableStateOf(sportNames.firstOrNull() ?: "") }
    var distanceStr by remember { mutableStateOf("") }

    // Locale-aware double parsing (SCRUM-230)
    val parsedDistance = remember(distanceStr) {
        distanceStr.replace(',', '.').toDoubleOrNull()
    }
    
    var startPos by remember { mutableStateOf<LatLng?>(null) }
    var endPos by remember { mutableStateOf<LatLng?>(null) }
    var apexPos by remember { mutableStateOf<LatLng?>(null) }
    
    var selectionMode by remember { mutableStateOf(SelectionMode.NONE) }
    
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val cameraPositionState = rememberCameraPositionState()
    
    // Zoom to current location on first load if available
    LaunchedEffect(currentLocation) {
        currentLocation?.let {
            if (cameraPositionState.position.target.latitude == 0.0) {
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(it, 15f))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cluster_manual_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    val isValid = name.isNotBlank() && startPos != null && endPos != null && apexPos != null && parsedDistance != null
                    Button(
                        onClick = {
                            val sportId = sportTypesList.find { it.name == selectedSportName }?.id ?: -1L
                            var distance = parsedDistance ?: 0.0
                            if (TrainingApplication.getUnit() == MyUnits.IMPERIAL) {
                                distance *= BANALService.METER_PER_MILE
                            } else {
                                distance *= 1000.0 // km to m (SCRUM-201)
                            }
                            
                            viewModel.addManualCluster(
                                name = name,
                                sportId = sportId,
                                start = startPos!!,
                                end = endPos!!,
                                apex = apexPos!!,
                                distance = distance
                            )
                            onBack()
                        },
                        enabled = isValid,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownSelector(
                        label = stringResource(R.string.Sport),
                        options = sportNames,
                        selectedOption = selectedSportName,
                        onOptionSelected = { selectedSportName = it },
                        modifier = Modifier.weight(1.2f),
                        stayOpenOn = emptySet()
                    )
                    OutlinedTextField(
                        value = distanceStr,
                        onValueChange = { distanceStr = it },
                        label = {
                            val isImperial = TrainingApplication.getUnit() == MyUnits.IMPERIAL
                            val unitStr = stringResource(if (isImperial) R.string.units_distance_imperial else R.string.units_distance_metric)
                            Text(stringResource(R.string.cluster_manual_distance_hint, unitStr))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Text(
                    text = stringResource(R.string.cluster_manual_pick_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PointToggleButton(
                        label = stringResource(R.string.start),
                        isSelected = selectionMode == SelectionMode.START,
                        hasValue = startPos != null,
                        color = TTColor.StartPoint,
                        onClick = { selectionMode = if (selectionMode == SelectionMode.START) SelectionMode.NONE else SelectionMode.START },
                        onClear = { startPos = null },
                        modifier = Modifier.weight(1f)
                    )
                    PointToggleButton(
                        label = stringResource(R.string.end),
                        isSelected = selectionMode == SelectionMode.END,
                        hasValue = endPos != null,
                        color = TTColor.EndPoint,
                        onClick = { selectionMode = if (selectionMode == SelectionMode.END) SelectionMode.NONE else SelectionMode.END },
                        onClear = { endPos = null },
                        modifier = Modifier.weight(1f)
                    )
                    PointToggleButton(
                        label = stringResource(R.string.max_line_distance),
                        isSelected = selectionMode == SelectionMode.APEX,
                        hasValue = apexPos != null,
                        color = TTColor.ApexPoint,
                        onClick = { selectionMode = if (selectionMode == SelectionMode.APEX) SelectionMode.NONE else SelectionMode.APEX },
                        onClear = { apexPos = null },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Box(modifier = Modifier.weight(0.55f).fillMaxWidth()) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    onMapClick = { latLng ->
                        when (selectionMode) {
                            SelectionMode.START -> {
                                startPos = latLng
                                selectionMode = SelectionMode.NONE // Auto-disable to allow panning
                            }
                            SelectionMode.END -> {
                                endPos = latLng
                                selectionMode = SelectionMode.NONE
                            }
                            SelectionMode.APEX -> {
                                apexPos = latLng
                                selectionMode = SelectionMode.NONE
                            }
                            SelectionMode.NONE -> {}
                        }
                    },
                    uiSettings = MapUiSettings(
                        myLocationButtonEnabled = true,
                        zoomControlsEnabled = false
                    ),
                    properties = MapProperties(
                        isMyLocationEnabled = currentLocation != null
                    )
                ) {
                    startPos?.let {
                        Marker(
                            state = rememberMarkerState(position = it).apply { position = it },
                            icon = remember { createSensorMarker(context, R.drawable.control_start, TTColor.StartPoint) },
                            title = stringResource(R.string.start)
                        )
                    }
                    endPos?.let {
                        Marker(
                            state = rememberMarkerState(position = it).apply { position = it },
                            icon = remember { createSensorMarker(context, R.drawable.control_stop, TTColor.EndPoint) },
                            title = stringResource(R.string.end)
                        )
                    }
                    apexPos?.let {
                        Marker(
                            state = rememberMarkerState(position = it).apply { position = it },
                            icon = remember { createSensorMarker(context, R.drawable.ic_distance, TTColor.ApexPoint) },
                            title = stringResource(R.string.max_line_distance)
                        )
                    }
                }
                
                if (selectionMode != SelectionMode.NONE) {
                    val modeLabel = when (selectionMode) {
                        SelectionMode.START -> stringResource(R.string.start)
                        SelectionMode.END -> stringResource(R.string.end)
                        SelectionMode.APEX -> stringResource(R.string.max_line_distance)
                        else -> ""
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = stringResource(R.string.cluster_manual_tap_map_format, modeLabel),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PointToggleButton(
    label: String,
    isSelected: Boolean,
    hasValue: Boolean,
    color: Color,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) color else if (hasValue) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
    val contentColor = if (isSelected) Color.White else color

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        contentPadding = PaddingValues(4.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, color),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            if (hasValue && !isSelected) {
                IconButton(
                    onClick = { onClear() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.cluster_clear_content_desc),
                        modifier = Modifier.size(16.dp),
                        tint = color
                    )
                }
            } else if (hasValue) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
            }
        }
    }
}
