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
import androidx.compose.ui.tooling.preview.Preview
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
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
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSource
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.common.filters.FilterBottomSheetScaffold
import com.atrainingtracker.trainingtracker.ui.common.filters.FilterFormattingUtils
import com.atrainingtracker.trainingtracker.ui.common.filters.FilterSearchField
import com.atrainingtracker.trainingtracker.ui.common.filters.NumericIntervalFilterSection
import com.atrainingtracker.trainingtracker.ui.common.filters.StartLocationFilterSection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import java.util.TimeZone

/**
 * Data class encapsulating partitioned equipment categories (REQ-UI-193).
 */
data class CategorizedEquipment(
    val bikes: List<Pair<Long, String>>,
    val shoes: List<Pair<Long, String>>,
    val other: List<Pair<Long, String>>
) {
    val all: List<Pair<Long, String>>
        get() = bikes + shoes + other
}

/**
 * Partitions available equipment items from workouts into sport-specific categories
 * respecting tab context and sub-sport filters (REQ-UI-193).
 */
fun partitionAvailableEquipment(
    allWorkouts: List<WorkoutData>,
    activeBSportType: BSportType?,
    localSportId: Long?
): CategorizedEquipment {
    val bikes = if (activeBSportType != null && activeBSportType != BSportType.BIKE) {
        emptyList()
    } else {
        allWorkouts
            .filter { it.equipmentId > 0 && !it.equipmentName.isNullOrBlank() && it.bSportType == BSportType.BIKE }
            .filter { localSportId == null || it.sportId == localSportId }
            .map { it.equipmentId to it.equipmentName!! }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }

    val shoes = if (activeBSportType != null && activeBSportType != BSportType.RUN) {
        emptyList()
    } else {
        allWorkouts
            .filter { it.equipmentId > 0 && !it.equipmentName.isNullOrBlank() && it.bSportType == BSportType.RUN }
            .filter { localSportId == null || it.sportId == localSportId }
            .map { it.equipmentId to it.equipmentName!! }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }

    val other = if (activeBSportType == BSportType.BIKE || activeBSportType == BSportType.RUN) {
        emptyList()
    } else {
        allWorkouts
            .filter { it.equipmentId > 0 && !it.equipmentName.isNullOrBlank() && it.bSportType != BSportType.BIKE && it.bSportType != BSportType.RUN }
            .filter { localSportId == null || it.sportId == localSportId }
            .map { it.equipmentId to it.equipmentName!! }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }

    return CategorizedEquipment(bikes = bikes, shoes = shoes, other = other)
}

/**
 * Material 3 modal bottom sheet for multi-dimensional workout filtering (REQ-UI-132, REQ-UI-157, REQ-UI-187, REQ-UI-193).
 *
 * Allows users to search by keyword, filter by year and date intervals via date pickers,
 * select tab-contextualized sport sub-types, categorized equipment (bikes/shoes), workout flags (commute, trainer, GPS presence),
 * custom distance/duration intervals (REQ-UI-157), and favorite locations / clusters (REQ-UI-187).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkoutFilterBottomSheet(
    criteria: WorkoutFilterCriteria,
    allWorkouts: List<WorkoutData>,
    onApplyCriteria: (WorkoutFilterCriteria) -> Unit,
    onClearAll: () -> Unit,
    onDismissRequest: () -> Unit,
    activeBSportType: BSportType? = null,
    knownLocations: List<KnownLocationItem> = emptyList()
) {
    var draft by remember(criteria) { mutableStateOf(criteria) }

    val isImperial = FilterFormattingUtils.rememberIsImperialUnits()
    val distanceMultiplier = remember(isImperial) { FilterFormattingUtils.getDistanceMultiplier(isImperial) }
    val elevationMultiplier = remember(isImperial) { FilterFormattingUtils.getElevationMultiplier(isImperial) }

    var minDistanceText by remember(criteria.minDistanceMeters, distanceMultiplier) {
        mutableStateOf(FilterFormattingUtils.formatDistanceInputValue(criteria.minDistanceMeters, distanceMultiplier))
    }
    var maxDistanceText by remember(criteria.maxDistanceMeters, distanceMultiplier) {
        mutableStateOf(FilterFormattingUtils.formatDistanceInputValue(criteria.maxDistanceMeters, distanceMultiplier))
    }

    var minDurationText by remember(criteria.minDurationSec) {
        mutableStateOf(criteria.minDurationSec?.let { (it / 60L).toString() } ?: "")
    }
    var maxDurationText by remember(criteria.maxDurationSec) {
        mutableStateOf(criteria.maxDurationSec?.let { (it / 60L).toString() } ?: "")
    }

    var minElevationText by remember(criteria.minElevationGainMeters, elevationMultiplier) {
        mutableStateOf(FilterFormattingUtils.formatElevationInputValue(criteria.minElevationGainMeters, elevationMultiplier))
    }
    var maxElevationText by remember(criteria.maxElevationGainMeters, elevationMultiplier) {
        mutableStateOf(FilterFormattingUtils.formatElevationInputValue(criteria.maxElevationGainMeters, elevationMultiplier))
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

    // Categorized Sport-Aware Equipment Selection (REQ-UI-193)
    val categorizedEquipment = remember(allWorkouts, activeBSportType, draft.sportTypeId) {
        partitionAvailableEquipment(allWorkouts, activeBSportType, draft.sportTypeId)
    }

    LaunchedEffect(categorizedEquipment.all) {
        if (draft.equipmentId != null && categorizedEquipment.all.none { it.first == draft.equipmentId }) {
            draft = draft.copy(equipmentId = null)
        }
    }

    // Date Picker Dialogs
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = (draft.startDateS ?: (System.currentTimeMillis() / 1000L)) * 1000L
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { ms ->
                        draft = draft.copy(startDateS = ms / 1000L)
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
            initialSelectedDateMillis = (draft.endDateS ?: (System.currentTimeMillis() / 1000L)) * 1000L
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { ms ->
                        draft = draft.copy(endDateS = (ms / 1000L) + 86399L)
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
            draft = WorkoutFilterCriteria(clusterId = criteria.clusterId, clusterName = criteria.clusterName)
            minDistanceText = ""
            maxDistanceText = ""
            minDurationText = ""
            maxDurationText = ""
            minElevationText = ""
            maxElevationText = ""
            onClearAll()
        },
        onApply = {
            onApplyCriteria(draft.copy(query = draft.query.trim()))
            onDismissRequest()
        }
    ) {
        // 1. Text Search Input (REQ-UI-509.1)
        FilterSearchField(
            query = draft.query,
            onQueryChange = { draft = draft.copy(query = it) },
            label = stringResource(R.string.filter_search_hint)
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
                            selected = (draft.year == yr),
                            onClick = { draft = draft.copy(year = if (draft.year == yr) null else yr) },
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
                val startLabel = draft.startDateS?.let { dateFormatter.format(Date(it * 1000L)) }
                    ?: stringResource(R.string.filter_date_from)
                InputChip(
                    selected = draft.startDateS != null,
                    onClick = { showStartDatePicker = true },
                    label = { Text(startLabel) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = if (draft.startDateS != null) {
                        {
                            IconButton(
                                onClick = { draft = draft.copy(startDateS = null) },
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

                val endLabel = draft.endDateS?.let { dateFormatter.format(Date(it * 1000L)) }
                    ?: stringResource(R.string.filter_date_to)
                InputChip(
                    selected = draft.endDateS != null,
                    onClick = { showEndDatePicker = true },
                    label = { Text(endLabel) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = if (draft.endDateS != null) {
                        {
                            IconButton(
                                onClick = { draft = draft.copy(endDateS = null) },
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
                            selected = (draft.sportTypeId == sId),
                            onClick = { draft = draft.copy(sportTypeId = if (draft.sportTypeId == sId) null else sId) },
                            label = { Text(sName) }
                        )
                    }
                }
            }
        }

        // 4. Categorized Equipment Selection (REQ-UI-193)
        if (categorizedEquipment.bikes.isNotEmpty()) {
            EquipmentSection(
                title = stringResource(R.string.equipment_type_bike),
                items = categorizedEquipment.bikes,
                selectedEquipId = draft.equipmentId,
                onSelectEquipId = { draft = draft.copy(equipmentId = it) }
            )
        }

        if (categorizedEquipment.shoes.isNotEmpty()) {
            EquipmentSection(
                title = stringResource(R.string.equipment_type_shoe),
                items = categorizedEquipment.shoes,
                selectedEquipId = draft.equipmentId,
                onSelectEquipId = { draft = draft.copy(equipmentId = it) }
            )
        }

        if (categorizedEquipment.other.isNotEmpty()) {
            EquipmentSection(
                title = stringResource(R.string.filter_section_equipment),
                items = categorizedEquipment.other,
                selectedEquipId = draft.equipmentId,
                onSelectEquipId = { draft = draft.copy(equipmentId = it) }
            )
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
                    selected = (draft.isCommute == true),
                    onClick = { draft = draft.copy(isCommute = if (draft.isCommute == true) null else true) },
                    label = { Text(stringResource(R.string.filter_commute)) }
                )

                FilterChip(
                    selected = (draft.isTrainer == true),
                    onClick = { draft = draft.copy(isTrainer = if (draft.isTrainer == true) null else true) },
                    label = { Text(stringResource(R.string.filter_trainer)) }
                )

                FilterChip(
                    selected = (draft.isRace == true),
                    onClick = { draft = draft.copy(isRace = if (draft.isRace == true) null else true) },
                    label = { Text(stringResource(R.string.filter_race_only)) }
                )

                FilterChip(
                    selected = (draft.hasGpsTrack == true),
                    onClick = { draft = draft.copy(hasGpsTrack = if (draft.hasGpsTrack == true) null else true) },
                    label = { Text(stringResource(R.string.filter_has_gps)) }
                )
            }
        }

        // 5b. Origin Source Selection (REQ-UI-265)
        Column {
            Text(
                text = stringResource(R.string.filter_section_origin_source),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val sources = listOf(
                    WorkoutSource.TRACKED to R.string.workout_source_tracked,
                    WorkoutSource.TCX to R.string.workout_source_tcx,
                    WorkoutSource.GPX to R.string.workout_source_gpx,
                    WorkoutSource.FIT to R.string.workout_source_fit
                )
                sources.forEach { (src, labelRes) ->
                    FilterChip(
                        selected = (draft.source == src),
                        onClick = { draft = draft.copy(source = if (draft.source == src) null else src) },
                        label = { Text(stringResource(labelRes)) }
                    )
                }
            }
        }

        // 6. Distance Interval (REQ-UI-157, REQ-UI-508.3, REQ-UI-509.2)
        NumericIntervalFilterSection(
            title = stringResource(R.string.filter_section_distance),
            unit = FilterFormattingUtils.getDistanceUnit(isImperial),
            presets = remember(isImperial) { FilterFormattingUtils.getDistancePresets(isImperial) },
            minMeters = draft.minDistanceMeters,
            maxMeters = draft.maxDistanceMeters,
            minText = minDistanceText,
            maxText = maxDistanceText,
            multiplier = distanceMultiplier,
            onBoundsChange = { minM, maxM, minT, maxT ->
                draft = draft.copy(minDistanceMeters = minM, maxDistanceMeters = maxM)
                minDistanceText = minT
                maxDistanceText = maxT
            },
            formatInputValue = { m, mult -> FilterFormattingUtils.formatDistanceInputValue(m, mult) }
        )

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
                    val isSelected = draft.minDurationSec == minS && draft.maxDurationSec == maxS
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) {
                                draft = draft.copy(minDurationSec = null, maxDurationSec = null)
                                minDurationText = ""
                                maxDurationText = ""
                            } else {
                                draft = draft.copy(minDurationSec = minS, maxDurationSec = maxS)
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
                        draft = draft.copy(minDurationSec = str.toLongOrNull()?.times(60L))
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
                        draft = draft.copy(maxDurationSec = str.toLongOrNull()?.times(60L))
                    },
                    label = { Text("${stringResource(R.string.filter_max_label)} (min)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 7b. Elevation Gain Interval (REQ-UI-425, REQ-UI-508.3, REQ-UI-509.2)
        NumericIntervalFilterSection(
            title = stringResource(R.string.sort_elevation_gain),
            unit = FilterFormattingUtils.getElevationUnit(isImperial),
            presets = remember(isImperial) { FilterFormattingUtils.getElevationPresets(isImperial) },
            minMeters = draft.minElevationGainMeters,
            maxMeters = draft.maxElevationGainMeters,
            minText = minElevationText,
            maxText = maxElevationText,
            multiplier = elevationMultiplier,
            onBoundsChange = { minM, maxM, minT, maxT ->
                draft = draft.copy(minElevationGainMeters = minM, maxElevationGainMeters = maxM)
                minElevationText = minT
                maxElevationText = maxT
            },
            formatInputValue = { m, mult -> FilterFormattingUtils.formatElevationInputValue(m, mult) },
            keyboardType = KeyboardType.Number
        )

        // 8. Favorite Locations (Start at / Lieblingsorte, REQ-UI-187, REQ-UI-194, REQ-UI-208, REQ-UI-467, REQ-UI-509.3)
        StartLocationFilterSection(
            title = stringResource(R.string.filter_section_start_at),
            knownLocations = knownLocations,
            selectedLat = draft.startLocationLat,
            selectedLng = draft.startLocationLng,
            onLocationSelected = { name, lat, lng, radiusM ->
                draft = draft.copy(
                    startLocationName = name,
                    startLocationLat = lat,
                    startLocationLng = lng,
                    startLocationRadiusM = radiusM
                )
            }
        )
    }
}

@Composable
private fun EquipmentSection(
    title: String,
    items: List<Pair<Long, String>>,
    selectedEquipId: Long?,
    onSelectEquipId: (Long?) -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { (eId, eName) ->
                FilterChip(
                    selected = (selectedEquipId == eId),
                    onClick = { onSelectEquipId(if (selectedEquipId == eId) null else eId) },
                    label = { Text(eName) }
                )
            }
        }
    }
}

@Preview(name = "Workout Filter Bottom Sheet - Light", showBackground = true)
@Composable
private fun WorkoutFilterBottomSheetLightPreview() {
    ATrainingTrackerTheme(darkTheme = false) {
        WorkoutFilterBottomSheet(
            criteria = WorkoutFilterCriteria(),
            allWorkouts = emptyList(),
            onApplyCriteria = {},
            onClearAll = {},
            onDismissRequest = {},
            knownLocations = listOf(
                KnownLocationItem(1L, "Home Base", 500.0, 100, LatLng(48.0, 11.5), 10, false, ElevationSource.GPS_FALLBACK),
                KnownLocationItem(2L, "Vacation Spot", 500.0, 100, LatLng(47.5, 12.0), 2, false, ElevationSource.GPS_FALLBACK)
            )
        )
    }
}
