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

package com.atrainingtracker.trainingtracker.ui.common.filters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem

/**
 * Standardized favorite start location filter section across filter bottom sheets (REQ-UI-509.3).
 *
 * Renders chips for known/favorite locations (Lieblingsorte). By default filters to frequent
 * locations ([KnownLocationItem.hitCount] >= 5) with an interactive toggle chip to expand all locations.
 *
 * @param knownLocations List of known locations to display.
 * @param selectedLat Currently selected start location latitude.
 * @param selectedLng Currently selected start location longitude.
 * @param onLocationSelected Callback invoked with selected location properties (or nulls to deselect).
 * @param modifier Optional modifier applied to the root column.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StartLocationFilterSection(
    knownLocations: List<KnownLocationItem>,
    selectedLat: Double?,
    selectedLng: Double?,
    onLocationSelected: (name: String?, lat: Double?, lng: Double?, radiusM: Double?) -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.filter_section_start_at)
) {
    if (knownLocations.isEmpty()) return

    var showAllLocations by remember { mutableStateOf(false) }
    val hasInfrequentLocations = remember(knownLocations) {
        knownLocations.any { it.hitCount < 5 }
    }
    val displayedLocations = remember(knownLocations, selectedLat, selectedLng, showAllLocations) {
        if (showAllLocations || !hasInfrequentLocations) {
            knownLocations
        } else {
            knownLocations.filter { loc ->
                loc.hitCount >= 5 || (selectedLat == loc.latLng.latitude && selectedLng == loc.latLng.longitude)
            }
        }
    }

    Column(modifier = modifier) {
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
            displayedLocations.forEach { loc ->
                val isSelected = selectedLat == loc.latLng.latitude &&
                        selectedLng == loc.latLng.longitude
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) {
                            onLocationSelected(null, null, null, null)
                        } else {
                            onLocationSelected(
                                loc.name,
                                loc.latLng.latitude,
                                loc.latLng.longitude,
                                loc.radius.toDouble()
                            )
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
