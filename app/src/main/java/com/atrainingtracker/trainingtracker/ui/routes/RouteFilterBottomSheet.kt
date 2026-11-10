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

package com.atrainingtracker.trainingtracker.ui.routes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.ui.common.filters.FilterBottomSheetScaffold
import com.atrainingtracker.trainingtracker.ui.common.filters.FilterFormattingUtils

/**
 * Material 3 modal bottom sheet for multi-dimensional route filtering.
 *
 * Employs [FilterBottomSheetScaffold] for consistent styling with workout filtering.
 * Allows users to search by keyword (name/description), select route origin (Strava, GPX, Workout),
 * toggle map visibility / selection, configure distance intervals (presets & custom min/max km)
 * and choose elevation gain thresholds (REQ-UI-417.2).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RouteFilterBottomSheet(
    criteria: RouteFilterCriteria,
    onApplyCriteria: (RouteFilterCriteria) -> Unit,
    onClearAll: () -> Unit,
    onDismissRequest: () -> Unit
) {
    var localQuery by remember(criteria.query) { mutableStateOf(criteria.query) }
    var localSource by remember(criteria.source) { mutableStateOf(criteria.source) }
    var localIsSelected by remember(criteria.isSelected) { mutableStateOf(criteria.isSelected) }

    val isImperial = FilterFormattingUtils.rememberIsImperialUnits()
    val distanceMultiplier = remember(isImperial) { FilterFormattingUtils.getDistanceMultiplier(isImperial) }
    val elevationMultiplier = remember(isImperial) { FilterFormattingUtils.getElevationMultiplier(isImperial) }
    val distanceUnit = FilterFormattingUtils.getDistanceUnit(isImperial)
    val elevationUnit = FilterFormattingUtils.getElevationUnit(isImperial)

    var localMinDistanceMeters by remember(criteria.minDistanceMeters) { mutableStateOf(criteria.minDistanceMeters) }
    var localMaxDistanceMeters by remember(criteria.maxDistanceMeters) { mutableStateOf(criteria.maxDistanceMeters) }
    var minDistanceText by remember(criteria.minDistanceMeters, distanceMultiplier) {
        mutableStateOf(FilterFormattingUtils.formatDistanceInputValue(criteria.minDistanceMeters, distanceMultiplier))
    }
    var maxDistanceText by remember(criteria.maxDistanceMeters, distanceMultiplier) {
        mutableStateOf(FilterFormattingUtils.formatDistanceInputValue(criteria.maxDistanceMeters, distanceMultiplier))
    }
    var localMinElevationGainMeters by remember(criteria.minElevationGainMeters) { mutableStateOf(criteria.minElevationGainMeters) }
    var localMaxElevationGainMeters by remember(criteria.maxElevationGainMeters) { mutableStateOf(criteria.maxElevationGainMeters) }
    var minElevationText by remember(criteria.minElevationGainMeters, elevationMultiplier) {
        mutableStateOf(FilterFormattingUtils.formatElevationInputValue(criteria.minElevationGainMeters, elevationMultiplier))
    }
    var maxElevationText by remember(criteria.maxElevationGainMeters, elevationMultiplier) {
        mutableStateOf(FilterFormattingUtils.formatElevationInputValue(criteria.maxElevationGainMeters, elevationMultiplier))
    }

    FilterBottomSheetScaffold(
        title = stringResource(R.string.filter_routes_title),
        onDismissRequest = onDismissRequest,
        onClearAll = {
            localQuery = ""
            localSource = null
            localIsSelected = null
            localMinDistanceMeters = null
            localMaxDistanceMeters = null
            minDistanceText = ""
            maxDistanceText = ""
            localMinElevationGainMeters = null
            localMaxElevationGainMeters = null
            minElevationText = ""
            maxElevationText = ""
            onClearAll()
        },
        onApply = {
            val updated = criteria.copy(
                query = localQuery.trim(),
                source = localSource,
                isSelected = localIsSelected,
                minDistanceMeters = localMinDistanceMeters,
                maxDistanceMeters = localMaxDistanceMeters,
                minElevationGainMeters = localMinElevationGainMeters,
                maxElevationGainMeters = localMaxElevationGainMeters
            )
            onApplyCriteria(updated)
            onDismissRequest()
        }
    ) {
        // 1. Text Search Input
        OutlinedTextField(
            value = localQuery,
            onValueChange = { localQuery = it },
            label = { Text(stringResource(R.string.filter_search_routes_hint)) },
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

        // 2. Route Source Selection (Strava, Local GPX, Workout)
        Column {
            Text(
                text = stringResource(R.string.filter_route_source),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RouteSource.entries.forEach { src ->
                    FilterChip(
                        selected = (localSource == src),
                        onClick = { localSource = if (localSource == src) null else src },
                        label = { Text(stringResource(src.displayNameResId)) }
                    )
                }
            }
        }

        // 3. Route Attributes / Selection
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
                    selected = (localIsSelected == true),
                    onClick = { localIsSelected = if (localIsSelected == true) null else true },
                    label = { Text(stringResource(R.string.filter_route_selected)) }
                )
            }
        }

        // 4. Distance Interval (REQ-UI-417.2, REQ-UI-508.3)
        Column {
            Text(
                text = stringResource(R.string.filter_section_distance),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            val distancePresets = remember(isImperial) { FilterFormattingUtils.getDistancePresets(isImperial) }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
                                minDistanceText = FilterFormattingUtils.formatDistanceInputValue(minM, distanceMultiplier)
                                maxDistanceText = FilterFormattingUtils.formatDistanceInputValue(maxM, distanceMultiplier)
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
                        localMinDistanceMeters = str.toDoubleOrNull()?.times(distanceMultiplier)
                    },
                    label = { Text("${stringResource(R.string.filter_min_label)} ($distanceUnit)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = maxDistanceText,
                    onValueChange = { str ->
                        maxDistanceText = str
                        localMaxDistanceMeters = str.toDoubleOrNull()?.times(distanceMultiplier)
                    },
                    label = { Text("${stringResource(R.string.filter_max_label)} ($distanceUnit)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 5. Elevation Gain Thresholds (REQ-UI-417.2, REQ-UI-425, REQ-UI-508.3)
        Column {
            Text(
                text = stringResource(R.string.sort_elevation_gain),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            val elevationPresets = remember(isImperial) { FilterFormattingUtils.getElevationPresets(isImperial) }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                elevationPresets.forEach { (label, minM, maxM) ->
                    val isSelected = localMinElevationGainMeters == minM && localMaxElevationGainMeters == maxM
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) {
                                localMinElevationGainMeters = null
                                localMaxElevationGainMeters = null
                                minElevationText = ""
                                maxElevationText = ""
                            } else {
                                localMinElevationGainMeters = minM
                                localMaxElevationGainMeters = maxM
                                minElevationText = FilterFormattingUtils.formatElevationInputValue(minM, elevationMultiplier)
                                maxElevationText = FilterFormattingUtils.formatElevationInputValue(maxM, elevationMultiplier)
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
                    value = minElevationText,
                    onValueChange = { str ->
                        minElevationText = str
                        localMinElevationGainMeters = str.toDoubleOrNull()?.times(elevationMultiplier)
                    },
                    label = { Text("${stringResource(R.string.filter_min_label)} ($elevationUnit)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = maxElevationText,
                    onValueChange = { str ->
                        maxElevationText = str
                        localMaxElevationGainMeters = str.toDoubleOrNull()?.times(elevationMultiplier)
                    },
                    label = { Text("${stringResource(R.string.filter_max_label)} ($elevationUnit)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
