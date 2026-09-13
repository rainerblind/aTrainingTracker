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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.common.filters.RemovableFilterChip

/**
 * Horizontal scrolling row displaying removable chips for each active favorite tracks filter dimension.
 *
 * Provides immediate visual feedback of active filters directly below the sport tabs,
 * allowing single-tap dismissal of individual filter criteria without opening the filter sheet.
 */
@Composable
fun ActiveClusterFilterChipsRow(
    criteria: ClusterFilterCriteria,
    onRemoveQuery: () -> Unit,
    onRemoveEquipment: () -> Unit,
    onRemoveMinDistance: () -> Unit,
    onRemoveMinHitCount: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (criteria.isEmpty) return

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Text Query Chip
        if (criteria.query.isNotBlank()) {
            item(key = "query") {
                RemovableFilterChip(
                    label = "\"${criteria.query}\"",
                    onRemove = onRemoveQuery
                )
            }
        }

        // 2. Equipment Chip
        criteria.equipmentName?.let { equip ->
            item(key = "equipment") {
                RemovableFilterChip(
                    label = equip,
                    onRemove = onRemoveEquipment
                )
            }
        }

        // 3. Minimum Distance Chip
        criteria.minDistanceMeters?.let { meters ->
            val km = (meters / 1000.0).toInt()
            item(key = "min_distance") {
                RemovableFilterChip(
                    label = stringResource(R.string.filter_min_distance_format, km.toString()),
                    onRemove = onRemoveMinDistance
                )
            }
        }

        // 4. Minimum Recordings / Hit Count Chip
        criteria.minHitCount?.let { count ->
            item(key = "min_hit_count") {
                RemovableFilterChip(
                    label = stringResource(R.string.filter_min_recordings_chip_format, count),
                    onRemove = onRemoveMinHitCount
                )
            }
        }

        // 5. "Clear all" button (when ≥ 2 filters active)
        if (criteria.activeFilterCount >= 2) {
            item(key = "clear_all") {
                TextButton(onClick = onClearAll) {
                    Text(
                        text = stringResource(R.string.filter_clear_all),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
