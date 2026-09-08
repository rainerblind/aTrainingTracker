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

package com.atrainingtracker.trainingtracker.ui.segments.segmentlist

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
 * Material 3 modal bottom sheet for multi-dimensional starred segment filtering.
 *
 * Employs [FilterBottomSheetScaffold] for consistent styling with workout and route filtering.
 * Allows users to search by keyword (name/city), select a minimum Strava climb category,
 * choose minimum distance and elevation gain thresholds, and toggle has-PR status.
 *
 * ### Climb Category Labels
 * Strava climb categories map as follows: 1 = Cat. 4, 2 = Cat. 3, 3 = Cat. 2, 4 = Cat. 1, 5 = HC.
 * Selecting a chip filters to segments at or above that minimum category; category-0 segments
 * (uncategorized) are always excluded when this filter is active.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SegmentFilterBottomSheet(
    criteria: SegmentFilterCriteria,
    onApplyCriteria: (SegmentFilterCriteria) -> Unit,
    onClearAll: () -> Unit,
    onDismissRequest: () -> Unit
) {
    var localQuery by remember(criteria.query) { mutableStateOf(criteria.query) }
    var localMinClimbCategory by remember(criteria.minClimbCategory) { mutableStateOf(criteria.minClimbCategory) }
    var localMinDistanceMeters by remember(criteria.minDistanceMeters) { mutableStateOf(criteria.minDistanceMeters) }
    var localMinElevationGainMeters by remember(criteria.minElevationGainMeters) { mutableStateOf(criteria.minElevationGainMeters) }
    var localHasPR by remember(criteria.hasPR) { mutableStateOf(criteria.hasPR) }

    FilterBottomSheetScaffold(
        title = stringResource(R.string.filter_segments_title),
        onDismissRequest = onDismissRequest,
        onClearAll = {
            localQuery = ""
            localMinClimbCategory = null
            localMinDistanceMeters = null
            localMinElevationGainMeters = null
            localHasPR = null
            onClearAll()
        },
        onApply = {
            val updated = criteria.copy(
                query = localQuery.trim(),
                minClimbCategory = localMinClimbCategory,
                minDistanceMeters = localMinDistanceMeters,
                minElevationGainMeters = localMinElevationGainMeters,
                hasPR = localHasPR
            )
            onApplyCriteria(updated)
            onDismissRequest()
        }
    ) {
        // 1. Text Search Input
        OutlinedTextField(
            value = localQuery,
            onValueChange = { localQuery = it },
            label = { Text(stringResource(R.string.filter_search_segments_hint)) },
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

        // 2. Climb Category Selection (1 = Cat.4 … 5 = HC)
        Column {
            Text(
                text = stringResource(R.string.filter_section_climb_category),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Pairs of (displayLabel, categoryValue)
                listOf(
                    stringResource(R.string.filter_climb_cat_4) to 1,
                    stringResource(R.string.filter_climb_cat_3) to 2,
                    stringResource(R.string.filter_climb_cat_2) to 3,
                    stringResource(R.string.filter_climb_cat_1) to 4,
                    stringResource(R.string.filter_climb_cat_hc) to 5
                ).forEach { (label, value) ->
                    FilterChip(
                        selected = (localMinClimbCategory == value),
                        onClick = {
                            localMinClimbCategory = if (localMinClimbCategory == value) null else value
                        },
                        label = { Text(label) }
                    )
                }
            }
        }

        // 3. Distance & Elevation Gain Thresholds
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
                // Distance Thresholds: 5km, 10km, 25km, 50km
                listOf(5.0, 10.0, 25.0, 50.0).forEach { km ->
                    val meters = km * 1000.0
                    FilterChip(
                        selected = (localMinDistanceMeters == meters),
                        onClick = {
                            localMinDistanceMeters = if (localMinDistanceMeters == meters) null else meters
                        },
                        label = { Text(stringResource(R.string.filter_min_distance_format, km.toInt().toString())) }
                    )
                }

                // Elevation Gain Thresholds: 100m, 250m, 500m, 1000m
                listOf(100.0, 250.0, 500.0, 1000.0).forEach { m ->
                    FilterChip(
                        selected = (localMinElevationGainMeters == m),
                        onClick = {
                            localMinElevationGainMeters = if (localMinElevationGainMeters == m) null else m
                        },
                        label = { Text(stringResource(R.string.filter_min_elevation_format, m.toInt().toString())) }
                    )
                }
            }
        }

        // 4. Attributes (Has PR)
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
                    selected = (localHasPR == true),
                    onClick = { localHasPR = if (localHasPR == true) null else true },
                    label = { Text(stringResource(R.string.filter_has_pr)) }
                )
            }
        }
    }
}
