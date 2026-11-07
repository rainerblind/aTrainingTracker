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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.common.filters.RemovableFilterChip
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * Horizontal scrolling row displaying removable chips for active Known Locations filter dimensions (REQ-UI-466.3).
 *
 * Provides immediate visual feedback of active filters below the top header,
 * allowing single-tap dismissal of individual filter criteria without opening the filter sheet.
 */
@Composable
fun ActiveKnownLocationsFilterChipsRow(
    criteria: KnownLocationsFilterCriteria,
    onRemoveQuery: () -> Unit,
    onRemoveMinStarts: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (criteria.isEmpty) return

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (criteria.query.isNotBlank()) {
            item("query") {
                RemovableFilterChip(
                    label = "\"${criteria.query.trim()}\"",
                    onRemove = onRemoveQuery,
                    modifier = Modifier.testTag("known_locations_filter_chip_query")
                )
            }
        }

        if (criteria.minStarts != null) {
            item("min_starts") {
                RemovableFilterChip(
                    label = stringResource(R.string.filter_min_starts_chip_format, criteria.minStarts),
                    onRemove = onRemoveMinStarts,
                    modifier = Modifier.testTag("known_locations_filter_chip_starts")
                )
            }
        }

        item("clear_all") {
            TextButton(
                onClick = onClearAll,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.filter_clear_all),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Preview(name = "Active Filter Chips Row - Light", showBackground = true)
@Composable
private fun ActiveKnownLocationsFilterChipsRowLightPreview() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface {
            ActiveKnownLocationsFilterChipsRow(
                criteria = KnownLocationsFilterCriteria(query = "Marienplatz", minStarts = 5),
                onRemoveQuery = {},
                onRemoveMinStarts = {},
                onClearAll = {}
            )
        }
    }
}

@Preview(name = "Active Filter Chips Row - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ActiveKnownLocationsFilterChipsRowDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface {
            ActiveKnownLocationsFilterChipsRow(
                criteria = KnownLocationsFilterCriteria(query = "Marienplatz", minStarts = 5),
                onRemoveQuery = {},
                onRemoveMinStarts = {},
                onClearAll = {}
            )
        }
    }
}
