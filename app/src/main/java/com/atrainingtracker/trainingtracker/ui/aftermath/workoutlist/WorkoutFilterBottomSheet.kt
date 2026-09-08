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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData

/**
 * Material 3 modal bottom sheet for multi-dimensional workout filtering.
 *
 * Allows users to search by keyword, select specific years, sports, equipment,
 * workout flags (commute, trainer, GPS presence), and minimum distance/duration thresholds.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkoutFilterBottomSheet(
    criteria: WorkoutFilterCriteria,
    allWorkouts: List<WorkoutData>,
    onApplyCriteria: (WorkoutFilterCriteria) -> Unit,
    onClearAll: () -> Unit,
    onDismissRequest: () -> Unit
) {
    var localQuery by remember(criteria.query) { mutableStateOf(criteria.query) }
    var localYear by remember(criteria.year) { mutableStateOf(criteria.year) }
    var localSportId by remember(criteria.sportTypeId) { mutableStateOf(criteria.sportTypeId) }
    var localEquipId by remember(criteria.equipmentId) { mutableStateOf(criteria.equipmentId) }
    var localCommute by remember(criteria.isCommute) { mutableStateOf(criteria.isCommute) }
    var localTrainer by remember(criteria.isTrainer) { mutableStateOf(criteria.isTrainer) }
    var localHasGps by remember(criteria.hasGpsTrack) { mutableStateOf(criteria.hasGpsTrack) }
    var localMinDistanceMeters by remember(criteria.minDistanceMeters) { mutableStateOf(criteria.minDistanceMeters) }
    var localMinDurationSec by remember(criteria.minDurationSec) { mutableStateOf(criteria.minDurationSec) }

    val availableYears = remember(allWorkouts) {
        allWorkouts.map { it.localDateTime.year }.distinct().sortedDescending()
    }

    val availableSports = remember(allWorkouts) {
        allWorkouts.map { it.sportId to it.sportName }.distinctBy { it.first }.sortedBy { it.second }
    }

    val availableEquipment = remember(allWorkouts) {
        allWorkouts
            .filter { it.equipmentId > 0 && !it.equipmentName.isNullOrBlank() }
            .map { it.equipmentId to it.equipmentName!! }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header Bar: Title and Close dismiss button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.filter_workouts_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.Cancel)
                    )
                }
            }

            HorizontalDivider()

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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

                // 2. Year Selection
                if (availableYears.isNotEmpty()) {
                    Column {
                        Text(
                            text = stringResource(R.string.filter_section_time),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
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
                    }
                }

                // 3. Sport Sub-Type Selection
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

                // 6. Minimum Distance & Duration Thresholds
                Column {
                    Text(
                        text = stringResource(R.string.filter_section_thresholds),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Distance Thresholds: 10km, 25km, 50km, 100km
                        listOf(10.0, 25.0, 50.0, 100.0).forEach { km ->
                            val meters = km * 1000.0
                            FilterChip(
                                selected = (localMinDistanceMeters == meters),
                                onClick = {
                                    localMinDistanceMeters = if (localMinDistanceMeters == meters) null else meters
                                },
                                label = { Text(stringResource(R.string.filter_min_distance_format, km.toInt().toString())) }
                            )
                        }

                        // Duration Thresholds: 30min, 60min, 90min, 120min
                        listOf(30L, 60L, 90L, 120L).forEach { minutes ->
                            val sec = minutes * 60L
                            FilterChip(
                                selected = (localMinDurationSec == sec),
                                onClick = {
                                    localMinDurationSec = if (localMinDurationSec == sec) null else sec
                                },
                                label = { Text(stringResource(R.string.filter_min_duration_format, minutes.toString())) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            HorizontalDivider()

            // Dedicated Bottom Action Bar: Reset all & Apply
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        localQuery = ""
                        localYear = null
                        localSportId = null
                        localEquipId = null
                        localCommute = null
                        localTrainer = null
                        localHasGps = null
                        localMinDistanceMeters = null
                        localMinDurationSec = null
                        onClearAll()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.filter_clear_all))
                }

                Button(
                    onClick = {
                        val updated = criteria.copy(
                            query = localQuery.trim(),
                            year = localYear,
                            sportTypeId = localSportId,
                            equipmentId = localEquipId,
                            isCommute = localCommute,
                            isTrainer = localTrainer,
                            hasGpsTrack = localHasGps,
                            minDistanceMeters = localMinDistanceMeters,
                            minDurationSec = localMinDurationSec
                        )
                        onApplyCriteria(updated)
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.OK))
                }
            }
        }
    }
}
