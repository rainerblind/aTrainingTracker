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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.common.filters.FilterBottomSheetScaffold

/**
 * Material 3 modal bottom sheet for multi-dimensional Favorite Tracks / Workout Clusters filtering.
 *
 * Employs [FilterBottomSheetScaffold] for strict consistency with workout, route, and segment filtering.
 * Allows users to search by keyword (track name), filter by linked equipment, choose minimum reference distance
 * thresholds (10km, 25km, 50km, 100km), and choose minimum recordings count thresholds (≥ 3, 5, 10, 25).
 *
 * @param criteria Active filter criteria to initialize local UI state.
 * @param availableEquipment List of distinct equipment names linked across clusters.
 * @param onApplyCriteria Callback invoked when the user applies updated criteria.
 * @param onClearAll Callback invoked when the user resets all criteria.
 * @param onDismissRequest Callback invoked when the sheet is closed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClusterFilterBottomSheet(
    criteria: ClusterFilterCriteria,
    availableEquipment: List<String>,
    onApplyCriteria: (ClusterFilterCriteria) -> Unit,
    onClearAll: () -> Unit,
    onDismissRequest: () -> Unit
) {
    var localQuery by remember(criteria.query) { mutableStateOf(criteria.query) }
    var localEquipmentName by remember(criteria.equipmentName) { mutableStateOf(criteria.equipmentName) }
    var localMinDistanceMeters by remember(criteria.minDistanceMeters) { mutableStateOf(criteria.minDistanceMeters) }
    var localMinHitCount by remember(criteria.minHitCount) { mutableStateOf(criteria.minHitCount) }

    FilterBottomSheetScaffold(
        title = stringResource(R.string.filter_clusters_title),
        onDismissRequest = onDismissRequest,
        onClearAll = {
            localQuery = ""
            localEquipmentName = null
            localMinDistanceMeters = null
            localMinHitCount = null
            onClearAll()
        },
        onApply = {
            val updated = criteria.copy(
                query = localQuery.trim(),
                equipmentName = localEquipmentName,
                minDistanceMeters = localMinDistanceMeters,
                minHitCount = localMinHitCount
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

        // 3. Minimum Reference Distance Thresholds
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
            }
        }

        // 4. Minimum Recordings / Hit Count Thresholds
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
    }
}
