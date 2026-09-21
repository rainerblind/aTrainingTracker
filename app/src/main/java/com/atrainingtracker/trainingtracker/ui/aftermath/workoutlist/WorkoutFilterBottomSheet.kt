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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.common.filters.FilterBottomSheetScaffold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Material 3 modal bottom sheet for multi-dimensional workout filtering (REQ-UI-132, REQ-UI-157).
 *
 * Allows users to search by keyword, filter by year and date intervals via date pickers,
 * select tab-contextualized sport sub-types, equipment, workout flags (commute, trainer, GPS presence),
 * and custom distance/duration intervals (REQ-UI-157).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkoutFilterBottomSheet(
    criteria: WorkoutFilterCriteria,
    allWorkouts: List<WorkoutData>,
    onApplyCriteria: (WorkoutFilterCriteria) -> Unit,
    onClearAll: () -> Unit,
    onDismissRequest: () -> Unit,
    activeBSportType: BSportType? = null
) {
    var localQuery by remember(criteria.query) { mutableStateOf(criteria.query) }
    var localYear by remember(criteria.year) { mutableStateOf(criteria.year) }
    var localStartDateS by remember(criteria.startDateS) { mutableStateOf(criteria.startDateS) }
    var localEndDateS by remember(criteria.endDateS) { mutableStateOf(criteria.endDateS) }
    var localSportId by remember(criteria.sportTypeId) { mutableStateOf(criteria.sportTypeId) }
    var localEquipId by remember(criteria.equipmentId) { mutableStateOf(criteria.equipmentId) }
    var localCommute by remember(criteria.isCommute) { mutableStateOf(criteria.isCommute) }
    var localTrainer by remember(criteria.isTrainer) { mutableStateOf(criteria.isTrainer) }
    var localHasGps by remember(criteria.hasGpsTrack) { mutableStateOf(criteria.hasGpsTrack) }

    var localMinDistanceMeters by remember(criteria.minDistanceMeters) { mutableStateOf(criteria.minDistanceMeters) }
    var localMaxDistanceMeters by remember(criteria.maxDistanceMeters) { mutableStateOf(criteria.maxDistanceMeters) }
    var minDistanceText by remember(criteria.minDistanceMeters) {
        mutableStateOf(criteria.minDistanceMeters?.let { (it / 1000.0).let { v -> if (v % 1.0 == 0.0) v.toInt().toString() else v.toString() } } ?: "")
    }
    var maxDistanceText by remember(criteria.maxDistanceMeters) {
        mutableStateOf(criteria.maxDistanceMeters?.let { (it / 1000.0).let { v -> if (v % 1.0 == 0.0) v.toInt().toString() else v.toString() } } ?: "")
    }

    var localMinDurationSec by remember(criteria.minDurationSec) { mutableStateOf(criteria.minDurationSec) }
    var localMaxDurationSec by remember(criteria.maxDurationSec) { mutableStateOf(criteria.maxDurationSec) }
    var minDurationText by remember(criteria.minDurationSec) {
        mutableStateOf(criteria.minDurationSec?.let { (it / 60L).toString() } ?: "")
    }
    var maxDurationText by remember(criteria.maxDurationSec) {
        mutableStateOf(criteria.maxDurationSec?.let { (it / 60L).toString() } ?: "")
    }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val dateFormatter = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    val availableYears = remember(allWorkouts) {
        allWorkouts.map { it.localDateTime.year }.distinct().sortedDescending()
    }

    // Tab-Aware Sport Sub-Type Filtering (REQ-UI-157)
    val availableSports = remember(allWorkouts, activeBSportType) {
        allWorkouts
            .filter { activeBSportType == null || it.bSportType == activeBSportType }
            .map { it.sportId to it.sportName }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }

    // Sport-Aware Equipment Selection (REQ-UI-157)
    val availableEquipment = remember(allWorkouts, activeBSportType, localSportId) {
        allWorkouts
            .filter { it.equipmentId > 0 && !it.equipmentName.isNullOrBlank() }
            .filter { workout ->
                if (localSportId != null) {
                    workout.sportId == localSportId
                } else if (activeBSportType != null) {
                    workout.bSportType == activeBSportType
                } else {
                    true
                }
            }
            .map { it.equipmentId to it.equipmentName!! }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }

    LaunchedEffect(availableEquipment) {
        if (localEquipId != null && availableEquipment.none { it.first == localEquipId }) {
            localEquipId = null
        }
    }

    // Date Picker Dialogs
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = (localStartDateS ?: (System.currentTimeMillis() / 1000L)) * 1000L
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { ms ->
                        localStartDateS = ms / 1000L
                    }
                    showStartDatePicker = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text(stringResource(R.string.Cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = (localEndDateS ?: (System.currentTimeMillis() / 1000L)) * 1000L
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { ms ->
                        localEndDateS = (ms / 1000L) + 86399L
                    }
                    showEndDatePicker = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text(stringResource(R.string.Cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    FilterBottomSheetScaffold(
        title = stringResource(R.string.filter_workouts_title),
        onDismissRequest = onDismissRequest,
        onClearAll = {
            localQuery = ""
            localYear = null
            localStartDateS = null
            localEndDateS = null
            localSportId = null
            localEquipId = null
            localCommute = null
            localTrainer = null
            localHasGps = null
            localMinDistanceMeters = null
            localMaxDistanceMeters = null
            minDistanceText = ""
            maxDistanceText = ""
            localMinDurationSec = null
            localMaxDurationSec = null
            minDurationText = ""
            maxDurationText = ""
            onClearAll()
        },
        onApply = {
            val updated = criteria.copy(
                query = localQuery.trim(),
                year = localYear,
                startDateS = localStartDateS,
                endDateS = localEndDateS,
                sportTypeId = localSportId,
                equipmentId = localEquipId,
                isCommute = localCommute,
                isTrainer = localTrainer,
                hasGpsTrack = localHasGps,
                minDistanceMeters = localMinDistanceMeters,
                maxDistanceMeters = localMaxDistanceMeters,
                minDurationSec = localMinDurationSec,
                maxDurationSec = localMaxDurationSec
            )
            onApplyCriteria(updated)
            onDismissRequest()
        }
    ) {
        // 1. Text Search Input
        OutlinedTextField(
            value = localQuery,
            onValueChange = { localQuery = it },
            label = { Text(stringResource(R.string.filter_search_hint)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
            },
            trailingIcon = {
                if (localQuery.isNotBlank()) {
                    IconButton(onClick = { localQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.Cancel)
                        )
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // 2. Time & Period (Year & Date Interval)
        Column {
            Text(
                text = stringResource(R.string.filter_section_time),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (availableYears.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    availableYears.forEach { yr ->
                        FilterChip(
                            selected = (localYear == yr),
                            onClick = { localYear = if (localYear == yr) null else yr },
                            label = { Text(yr.toString()) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Custom Date Interval (REQ-UI-157)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val startLabel = localStartDateS?.let { dateFormatter.format(Date(it * 1000L)) }
                    ?: stringResource(R.string.filter_date_from)
                InputChip(
                    selected = localStartDateS != null,
                    onClick = { showStartDatePicker = true },
                    label = { Text(startLabel) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = if (localStartDateS != null) {
                        {
                            IconButton(
                                onClick = { localStartDateS = null },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.Cancel)
                                )
                            }
                        }
                    } else null,
                    modifier = Modifier.weight(1f)
                )

                val endLabel = localEndDateS?.let { dateFormatter.format(Date(it * 1000L)) }
                    ?: stringResource(R.string.filter_date_to)
                InputChip(
                    selected = localEndDateS != null,
                    onClick = { showEndDatePicker = true },
                    label = { Text(endLabel) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = if (localEndDateS != null) {
                        {
                            IconButton(
                                onClick = { localEndDateS = null },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.Cancel)
                                )
                            }
                        }
                    } else null,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 3. Sport Sub-Type Selection (Tab-Aware, REQ-UI-157)
        if (availableSports.isNotEmpty()) {
            Column {
                Text(
                    text = stringResource(R.string.filter_section_sport),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    availableSports.forEach { (sId, sName) ->
                        FilterChip(
                            selected = (localSportId == sId),
                            onClick = { localSportId = if (localSportId == sId) null else sId },
                            label = { Text(sName) }
                        )
                    }
                }
            }
        }

        // 4. Equipment Selection
        if (availableEquipment.isNotEmpty()) {
            Column {
                Text(
                    text = stringResource(R.string.filter_section_equipment),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    availableEquipment.forEach { (eId, eName) ->
                        FilterChip(
                            selected = (localEquipId == eId),
                            onClick = { localEquipId = if (localEquipId == eId) null else eId },
                            label = { Text(eName) }
                        )
                    }
                }
            }
        }

        // 5. Workout Attributes
        Column {
            Text(
                text = stringResource(R.string.filter_section_attributes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = (localCommute == true),
                    onClick = { localCommute = if (localCommute == true) null else true },
                    label = { Text(stringResource(R.string.filter_commute)) }
                )

                FilterChip(
                    selected = (localTrainer == true),
                    onClick = { localTrainer = if (localTrainer == true) null else true },
                    label = { Text(stringResource(R.string.filter_trainer)) }
                )

                FilterChip(
                    selected = (localHasGps == true),
                    onClick = { localHasGps = if (localHasGps == true) null else true },
                    label = { Text(stringResource(R.string.filter_has_gps)) }
                )
            }
        }

        // 6. Distance Interval (REQ-UI-157)
        Column {
            Text(
                text = stringResource(R.string.filter_section_distance),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val distancePresets = listOf(
                    Triple("< 10 km", null, 10000.0),
                    Triple("10 - 25 km", 10000.0, 25000.0),
                    Triple("25 - 50 km", 25000.0, 50000.0),
                    Triple("50 - 100 km", 50000.0, 100000.0),
                    Triple("> 100 km", 100000.0, null)
                )
                distancePresets.forEach { (label, minM, maxM) ->
                    val isSelected = localMinDistanceMeters == minM && localMaxDistanceMeters == maxM
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) {
                                localMinDistanceMeters = null
                                localMaxDistanceMeters = null
                                minDistanceText = ""
                                maxDistanceText = ""
                            } else {
                                localMinDistanceMeters = minM
                                localMaxDistanceMeters = maxM
                                minDistanceText = minM?.let { (it / 1000.0).toInt().toString() } ?: ""
                                maxDistanceText = maxM?.let { (it / 1000.0).toInt().toString() } ?: ""
                            }
                        },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = minDistanceText,
                    onValueChange = { str ->
                        minDistanceText = str
                        localMinDistanceMeters = str.toDoubleOrNull()?.times(1000.0)
                    },
                    label = { Text("${stringResource(R.string.filter_min_label)} (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = maxDistanceText,
                    onValueChange = { str ->
                        maxDistanceText = str
                        localMaxDistanceMeters = str.toDoubleOrNull()?.times(1000.0)
                    },
                    label = { Text("${stringResource(R.string.filter_max_label)} (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 7. Duration Interval (REQ-UI-157)
        Column {
            Text(
                text = stringResource(R.string.filter_section_duration),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val durationPresets = listOf(
                    Triple("< 30 min", null, 1800L),
                    Triple("30 - 60 min", 1800L, 3600L),
                    Triple("60 - 90 min", 3600L, 5400L),
                    Triple("90 - 120 min", 5400L, 7200L),
                    Triple("> 120 min", 7200L, null)
                )
                durationPresets.forEach { (label, minS, maxS) ->
                    val isSelected = localMinDurationSec == minS && localMaxDurationSec == maxS
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) {
                                localMinDurationSec = null
                                localMaxDurationSec = null
                                minDurationText = ""
                                maxDurationText = ""
                            } else {
                                localMinDurationSec = minS
                                localMaxDurationSec = maxS
                                minDurationText = minS?.let { (it / 60L).toString() } ?: ""
                                maxDurationText = maxS?.let { (it / 60L).toString() } ?: ""
                            }
                        },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = minDurationText,
                    onValueChange = { str ->
                        minDurationText = str
                        localMinDurationSec = str.toLongOrNull()?.times(60L)
                    },
                    label = { Text("${stringResource(R.string.filter_min_label)} (min)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = maxDurationText,
                    onValueChange = { str ->
                        maxDurationText = str
                        localMaxDurationSec = str.toLongOrNull()?.times(60L)
                    },
                    label = { Text("${stringResource(R.string.filter_max_label)} (min)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
