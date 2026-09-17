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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
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
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Horizontal scrolling row displaying removable chips for each active workout filter dimension.
 *
 * Provides immediate visual feedback of active filters directly above the workout list,
 * allowing single-tap dismissal of individual filter criteria without opening the filter sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveFilterChipsRow(
    criteria: WorkoutFilterCriteria,
    onRemoveQuery: () -> Unit,
    onRemoveYear: () -> Unit,
    onRemoveMonth: () -> Unit,
    onRemoveDateRange: () -> Unit,
    onRemoveSport: () -> Unit,
    onRemoveEquipment: () -> Unit,
    onRemoveCommute: () -> Unit,
    onRemoveTrainer: () -> Unit,
    onRemoveGpsTrack: () -> Unit,
    onRemoveDistanceRange: () -> Unit = {},
    onRemoveDurationRange: () -> Unit = {},
    onRemoveMinDistance: () -> Unit = onRemoveDistanceRange,
    onRemoveMinDuration: () -> Unit = onRemoveDurationRange,
    onClearAll: () -> Unit,
    sportName: String? = null,
    equipmentName: String? = null,
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
        // Text Query Chip
        if (criteria.query.isNotBlank()) {
            item("query") {
                RemovableFilterChip(
                    label = "\"${criteria.query}\"",
                    onRemove = onRemoveQuery
                )
            }
        }

        // Year Chip
        if (criteria.year != null) {
            item("year") {
                RemovableFilterChip(
                    label = criteria.year.toString(),
                    onRemove = onRemoveYear
                )
            }
        }

        // Month Chip
        if (criteria.month != null) {
            item("month") {
                val monthName = java.time.Month.of(criteria.month).getDisplayName(
                    java.time.format.TextStyle.SHORT,
                    Locale.getDefault()
                )
                RemovableFilterChip(
                    label = monthName,
                    onRemove = onRemoveMonth
                )
            }
        }

        // Date Range Chip
        if (criteria.startDateS != null || criteria.endDateS != null) {
            item("dateRange") {
                val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT)
                val startText = criteria.startDateS?.let { dateFormat.format(Date(it * 1000L)) } ?: "..."
                val endText = criteria.endDateS?.let { dateFormat.format(Date(it * 1000L)) } ?: "..."
                RemovableFilterChip(
                    label = "$startText - $endText",
                    onRemove = onRemoveDateRange
                )
            }
        }

        // Sport Sub-Type Chip
        if (criteria.sportTypeId != null) {
            item("sport") {
                val label = sportName ?: stringResource(R.string.filter_section_sport)
                RemovableFilterChip(
                    label = label,
                    onRemove = onRemoveSport
                )
            }
        }

        // Equipment Chip
        if (criteria.equipmentId != null) {
            item("equipment") {
                val label = equipmentName ?: stringResource(R.string.filter_section_equipment)
                RemovableFilterChip(
                    label = label,
                    onRemove = onRemoveEquipment
                )
            }
        }

        // Commute Chip
        if (criteria.isCommute != null) {
            item("commute") {
                RemovableFilterChip(
                    label = stringResource(R.string.filter_commute),
                    onRemove = onRemoveCommute
                )
            }
        }

        // Trainer / Indoor Chip
        if (criteria.isTrainer != null) {
            item("trainer") {
                RemovableFilterChip(
                    label = stringResource(R.string.filter_trainer),
                    onRemove = onRemoveTrainer
                )
            }
        }

        // Has GPS Track Chip
        if (criteria.hasGpsTrack == true) {
            item("hasGps") {
                RemovableFilterChip(
                    label = stringResource(R.string.filter_has_gps),
                    onRemove = onRemoveGpsTrack
                )
            }
        }

        // Distance Interval Chip
        if (criteria.minDistanceMeters != null || criteria.maxDistanceMeters != null) {
            item("distance") {
                val minKm = criteria.minDistanceMeters?.let { (it / 1000.0).let { v -> if (v % 1.0 == 0.0) v.toInt().toString() else v.toString() } }
                val maxKm = criteria.maxDistanceMeters?.let { (it / 1000.0).let { v -> if (v % 1.0 == 0.0) v.toInt().toString() else v.toString() } }
                val label = when {
                    minKm != null && maxKm != null -> stringResource(R.string.filter_distance_interval_format, minKm, maxKm)
                    minKm != null -> stringResource(R.string.filter_min_distance_format, minKm)
                    else -> stringResource(R.string.filter_max_distance_format, maxKm!!)
                }
                RemovableFilterChip(
                    label = label,
                    onRemove = onRemoveDistanceRange
                )
            }
        }

        // Duration Interval Chip
        if (criteria.minDurationSec != null || criteria.maxDurationSec != null) {
            item("duration") {
                val minM = criteria.minDurationSec?.let { (it / 60L).toString() }
                val maxM = criteria.maxDurationSec?.let { (it / 60L).toString() }
                val label = when {
                    minM != null && maxM != null -> stringResource(R.string.filter_duration_interval_format, minM, maxM)
                    minM != null -> stringResource(R.string.filter_min_duration_format, minM)
                    else -> stringResource(R.string.filter_max_duration_format, maxM!!)
                }
                RemovableFilterChip(
                    label = label,
                    onRemove = onRemoveDurationRange
                )
            }
        }

        // Clear All Action Button
        if (criteria.activeFilterCount > 1) {
            item("clearAll") {
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
}
