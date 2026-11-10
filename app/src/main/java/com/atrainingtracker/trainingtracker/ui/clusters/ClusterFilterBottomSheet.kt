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

package com.atrainingtracker.trainingtracker.ui.clusters

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
import androidx.compose.ui.tooling.preview.Preview
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
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
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.ui.common.filters.FilterBottomSheetScaffold
import com.atrainingtracker.trainingtracker.ui.common.filters.FilterFormattingUtils

/**
 * Material 3 modal bottom sheet for multi-dimensional Favorite Tracks / Workout Clusters filtering (REQ-UI-187, REQ-UI-417.2).
 *
 * Employs [FilterBottomSheetScaffold] for strict consistency with workout, route, and segment filtering.
 * Allows users to search by keyword (track name), filter by favorite starting locations (Lieblingsorte),
 * filter by linked equipment, configure distance intervals (presets & custom min/max km),
 * and choose minimum recordings count thresholds (≥ 3, 5, 10, 25).
 *
 * @param criteria Active filter criteria to initialize local UI state.
 * @param availableEquipment List of distinct equipment names linked across clusters.
 * @param onApplyCriteria Callback invoked when the user applies updated criteria.
 * @param onClearAll Callback invoked when the user resets all criteria.
 * @param onDismissRequest Callback invoked when the sheet is closed.
 * @param knownLocations List of available favorite locations for spatial filtering.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClusterFilterBottomSheet(
    criteria: ClusterFilterCriteria,
    availableEquipment: List<String>,
    onApplyCriteria: (ClusterFilterCriteria) -> Unit,
    onClearAll: () -> Unit,
    onDismissRequest: () -> Unit,
    knownLocations: List<KnownLocationItem> = emptyList()
) {
    var localQuery by remember(criteria.query) { mutableStateOf(criteria.query) }
    var localEquipmentName by remember(criteria.equipmentName) { mutableStateOf(criteria.equipmentName) }

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
    var localMinHitCount by remember(criteria.minHitCount) { mutableStateOf(criteria.minHitCount) }
    var localStartLocationName by remember(criteria.startLocationName) { mutableStateOf(criteria.startLocationName) }
    var localStartLocationLat by remember(criteria.startLocationLat) { mutableStateOf(criteria.startLocationLat) }
    var localStartLocationLng by remember(criteria.startLocationLng) { mutableStateOf(criteria.startLocationLng) }
    var localStartLocationRadiusM by remember(criteria.startLocationRadiusM) { mutableStateOf(criteria.startLocationRadiusM) }

    FilterBottomSheetScaffold(
        title = stringResource(R.string.filter_clusters_title),
        onDismissRequest = onDismissRequest,
        onClearAll = {
            localQuery = ""
            localEquipmentName = null
            localMinDistanceMeters = null
            localMaxDistanceMeters = null
            minDistanceText = ""
            maxDistanceText = ""
            localMinElevationGainMeters = null
            localMaxElevationGainMeters = null
            minElevationText = ""
            maxElevationText = ""
            localMinHitCount = null
            localStartLocationName = null
            localStartLocationLat = null
            localStartLocationLng = null
            localStartLocationRadiusM = null
            onClearAll()
        },
        onApply = {
            val updated = criteria.copy(
                query = localQuery.trim(),
                equipmentName = localEquipmentName,
                minDistanceMeters = localMinDistanceMeters,
                maxDistanceMeters = localMaxDistanceMeters,
                minElevationGainMeters = localMinElevationGainMeters,
                maxElevationGainMeters = localMaxElevationGainMeters,
                minHitCount = localMinHitCount,
                startLocationName = localStartLocationName,
                startLocationLat = localStartLocationLat,
                startLocationLng = localStartLocationLng,
                startLocationRadiusM = localStartLocationRadiusM
            )
            onApplyCriteria(updated)
            onDismissRequest()
        }
    ) {
        // 1. Text Search Input
        OutlinedTextField(
            value = localQuery,
            onValueChange = { localQuery = it },
            label = { Text(stringResource(R.string.filter_search_clusters_hint)) },
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

        // 2. Equipment Selection
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
                    availableEquipment.forEach { equipName ->
                        FilterChip(
                            selected = (localEquipmentName == equipName),
                            onClick = {
                                localEquipmentName = if (localEquipmentName == equipName) null else equipName
                            },
                            label = { Text(equipName) }
                        )
                    }
                }
            }
        }

        // 3. Distance Interval (REQ-UI-417.2, REQ-UI-508.3)
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

        // 4. Elevation Gain Interval (REQ-UI-425, REQ-UI-508.3)
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

        // 5. Minimum Recordings / Hit Count Thresholds
        Column {
            Text(
                text = stringResource(R.string.filter_section_recordings),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Recordings Count Thresholds: 3, 5, 10, 25
                listOf(3, 5, 10, 25).forEach { count ->
                    FilterChip(
                        selected = (localMinHitCount == count),
                        onClick = {
                            localMinHitCount = if (localMinHitCount == count) null else count
                        },
                        label = { Text(stringResource(R.string.filter_min_recordings_chip_format, count)) }
                    )
                }
            }
        }

        // 5. Favorite Locations Selection (Start at / Lieblingsorte, REQ-UI-187, REQ-UI-194, REQ-UI-208, REQ-UI-467)
        if (knownLocations.isNotEmpty()) {
            var showAllLocations by remember { mutableStateOf(false) }
            val hasInfrequentLocations = remember(knownLocations) {
                knownLocations.any { it.hitCount < 5 }
            }
            val displayedLocations = remember(knownLocations, localStartLocationLat, localStartLocationLng, showAllLocations) {
                if (showAllLocations || !hasInfrequentLocations) {
                    knownLocations
                } else {
                    knownLocations.filter { loc ->
                        loc.hitCount >= 5 || (localStartLocationLat == loc.latLng.latitude && localStartLocationLng == loc.latLng.longitude)
                    }
                }
            }

            Column {
                Text(
                    text = stringResource(R.string.filter_section_start_at),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    displayedLocations.forEach { loc ->
                        val isSelected = localStartLocationLat == loc.latLng.latitude &&
                                localStartLocationLng == loc.latLng.longitude
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    localStartLocationName = null
                                    localStartLocationLat = null
                                    localStartLocationLng = null
                                    localStartLocationRadiusM = null
                                } else {
                                    localStartLocationName = loc.name
                                    localStartLocationLat = loc.latLng.latitude
                                    localStartLocationLng = loc.latLng.longitude
                                    localStartLocationRadiusM = loc.radius.toDouble()
                                }
                            },
                            label = { Text(loc.name) }
                        )
                    }

                    if (hasInfrequentLocations) {
                        FilterChip(
                            selected = showAllLocations,
                            onClick = { showAllLocations = !showAllLocations },
                            label = {
                                Text(
                                    stringResource(
                                        if (showAllLocations) R.string.filter_locations_show_frequent
                                        else R.string.filter_locations_show_all
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Cluster Filter Bottom Sheet - Light", showBackground = true)
@Composable
private fun ClusterFilterBottomSheetLightPreview() {
    ATrainingTrackerTheme(darkTheme = false) {
        ClusterFilterBottomSheet(
            criteria = ClusterFilterCriteria(minHitCount = 5),
            availableEquipment = listOf("Bike", "Running Shoes"),
            onApplyCriteria = {},
            onClearAll = {},
            onDismissRequest = {},
            knownLocations = listOf(
                KnownLocationItem(1L, "Home Base", 500.0, 100, LatLng(48.0, 11.5), 12, false, ElevationSource.GPS_FALLBACK),
                KnownLocationItem(2L, "Vacation Spot", 500.0, 100, LatLng(47.5, 12.0), 2, false, ElevationSource.GPS_FALLBACK)
            )
        )
    }
}
