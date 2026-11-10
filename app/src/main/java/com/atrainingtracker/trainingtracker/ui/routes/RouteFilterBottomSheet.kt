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
import com.atrainingtracker.trainingtracker.ui.common.filters.FilterSearchField
import com.atrainingtracker.trainingtracker.ui.common.filters.NumericIntervalFilterSection

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
    var draft by remember(criteria) { mutableStateOf(criteria) }

    val isImperial = FilterFormattingUtils.rememberIsImperialUnits()
    val distanceMultiplier = remember(isImperial) { FilterFormattingUtils.getDistanceMultiplier(isImperial) }
    val elevationMultiplier = remember(isImperial) { FilterFormattingUtils.getElevationMultiplier(isImperial) }
    val distanceUnit = FilterFormattingUtils.getDistanceUnit(isImperial)
    val elevationUnit = FilterFormattingUtils.getElevationUnit(isImperial)

    var minDistanceText by remember(criteria.minDistanceMeters, distanceMultiplier) {
        mutableStateOf(FilterFormattingUtils.formatDistanceInputValue(criteria.minDistanceMeters, distanceMultiplier))
    }
    var maxDistanceText by remember(criteria.maxDistanceMeters, distanceMultiplier) {
        mutableStateOf(FilterFormattingUtils.formatDistanceInputValue(criteria.maxDistanceMeters, distanceMultiplier))
    }
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
            draft = RouteFilterCriteria()
            minDistanceText = ""
            maxDistanceText = ""
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
            label = stringResource(R.string.filter_search_routes_hint)
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
                        selected = (draft.source == src),
                        onClick = { draft = draft.copy(source = if (draft.source == src) null else src) },
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
                    selected = (draft.isSelected == true),
                    onClick = { draft = draft.copy(isSelected = if (draft.isSelected == true) null else true) },
                    label = { Text(stringResource(R.string.filter_route_selected)) }
                )
            }
        }

        // 4. Distance Interval (REQ-UI-417.2, REQ-UI-508.3, REQ-UI-509.2)
        NumericIntervalFilterSection(
            title = stringResource(R.string.filter_section_distance),
            unit = distanceUnit,
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

        // 5. Elevation Gain Thresholds (REQ-UI-417.2, REQ-UI-425, REQ-UI-508.3, REQ-UI-509.2)
        NumericIntervalFilterSection(
            title = stringResource(R.string.sort_elevation_gain),
            unit = elevationUnit,
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
    }
}
