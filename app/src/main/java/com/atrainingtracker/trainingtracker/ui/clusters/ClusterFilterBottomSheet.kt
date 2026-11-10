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
import com.atrainingtracker.trainingtracker.ui.common.filters.FilterSearchField
import com.atrainingtracker.trainingtracker.ui.common.filters.NumericIntervalFilterSection
import com.atrainingtracker.trainingtracker.ui.common.filters.StartLocationFilterSection

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
        title = stringResource(R.string.filter_clusters_title),
        onDismissRequest = onDismissRequest,
        onClearAll = {
            draft = ClusterFilterCriteria()
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
            label = stringResource(R.string.filter_search_clusters_hint)
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
                            selected = (draft.equipmentName == equipName),
                            onClick = {
                                draft = draft.copy(
                                    equipmentName = if (draft.equipmentName == equipName) null else equipName
                                )
                            },
                            label = { Text(equipName) }
                        )
                    }
                }
            }
        }

        // 3. Distance Interval (REQ-UI-417.2, REQ-UI-508.3, REQ-UI-509.2)
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

        // 4. Elevation Gain Interval (REQ-UI-425, REQ-UI-508.3, REQ-UI-509.2)
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
                        selected = (draft.minHitCount == count),
                        onClick = {
                            draft = draft.copy(
                                minHitCount = if (draft.minHitCount == count) null else count
                            )
                        },
                        label = { Text(stringResource(R.string.filter_min_recordings_chip_format, count)) }
                    )
                }
            }
        }

        // 6. Favorite Locations Selection (Start at / Lieblingsorte, REQ-UI-187, REQ-UI-194, REQ-UI-208, REQ-UI-467, REQ-UI-509.3)
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
