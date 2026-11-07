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

package com.atrainingtracker.trainingtracker.ui.knownlocations

import android.content.res.Configuration
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.common.filters.FilterBottomSheetScaffold
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * Material 3 modal bottom sheet for multi-dimensional Known Locations (Lieblingsorte) filtering (REQ-UI-466.4).
 *
 * Employs [FilterBottomSheetScaffold] for design consistency across the application.
 * Allows users to search by keyword (location name or coordinates) and choose minimum starts
 * threshold presets (≥ 3, 5, 10, 25) or reset to show all locations.
 *
 * @param criteria Active filter criteria to initialize local UI state.
 * @param onApplyCriteria Callback invoked when the user applies updated criteria.
 * @param onClearAll Callback invoked when the user resets all criteria.
 * @param onDismissRequest Callback invoked when the sheet is closed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KnownLocationsFilterBottomSheet(
    criteria: KnownLocationsFilterCriteria,
    onApplyCriteria: (KnownLocationsFilterCriteria) -> Unit,
    onClearAll: () -> Unit,
    onDismissRequest: () -> Unit
) {
    var localQuery by remember(criteria.query) { mutableStateOf(criteria.query) }
    var localMinStarts by remember(criteria.minStarts) { mutableStateOf(criteria.minStarts) }

    FilterBottomSheetScaffold(
        title = stringResource(R.string.filter_known_locations_title),
        onDismissRequest = onDismissRequest,
        onClearAll = {
            localQuery = ""
            localMinStarts = null
            onClearAll()
        },
        onApply = {
            onApplyCriteria(
                KnownLocationsFilterCriteria(
                    query = localQuery,
                    minStarts = localMinStarts
                )
            )
            onDismissRequest()
        }
    ) {
        // 1. Keyword / Location Search
        OutlinedTextField(
            value = localQuery,
            onValueChange = { localQuery = it },
            label = { Text(stringResource(R.string.filter_search_known_locations_hint)) },
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

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Minimum Starts Thresholds
        Column {
            Text(
                text = stringResource(R.string.filter_section_starts),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(3, 5, 10, 25).forEach { count ->
                    FilterChip(
                        selected = (localMinStarts == count),
                        onClick = {
                            localMinStarts = if (localMinStarts == count) null else count
                        },
                        label = {
                            Text(stringResource(R.string.filter_min_starts_chip_format, count))
                        }
                    )
                }
            }
        }
    }
}

@Preview(name = "Known Locations Filter Bottom Sheet - Light", showBackground = true)
@Composable
private fun KnownLocationsFilterBottomSheetLightPreview() {
    ATrainingTrackerTheme(darkTheme = false) {
        KnownLocationsFilterBottomSheet(
            criteria = KnownLocationsFilterCriteria(query = "Olymp", minStarts = 5),
            onApplyCriteria = {},
            onClearAll = {},
            onDismissRequest = {}
        )
    }
}

@Preview(name = "Known Locations Filter Bottom Sheet - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun KnownLocationsFilterBottomSheetDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true) {
        KnownLocationsFilterBottomSheet(
            criteria = KnownLocationsFilterCriteria(query = "Olymp", minStarts = 5),
            onApplyCriteria = {},
            onClearAll = {},
            onDismissRequest = {}
        )
    }
}
