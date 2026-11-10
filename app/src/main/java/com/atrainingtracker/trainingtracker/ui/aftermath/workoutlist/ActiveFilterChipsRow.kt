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
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutSource
import com.atrainingtracker.trainingtracker.ui.common.filters.FilterFormattingUtils
import com.atrainingtracker.trainingtracker.ui.common.filters.RemovableFilterChip
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveFilterChipsRow(
    criteria: WorkoutFilterCriteria,
    onUpdateCriteria: ((WorkoutFilterCriteria) -> WorkoutFilterCriteria) -> Unit,
    onClearAll: () -> Unit,
    sportName: String? = null,
    equipmentName: String? = null,
    modifier: Modifier = Modifier
) {
    if (criteria.isEmpty) return

    ActiveFilterChipsRow(
        criteria = criteria,
        onRemoveQuery = { onUpdateCriteria { it.copy(query = "") } },
        onRemoveYear = { onUpdateCriteria { it.copy(year = null) } },
        onRemoveMonth = { onUpdateCriteria { it.copy(month = null) } },
        onRemoveDateRange = { onUpdateCriteria { it.copy(startDateS = null, endDateS = null) } },
        onRemoveBSportType = { onUpdateCriteria { it.copy(bSportType = null) } },
        onRemoveSport = { onUpdateCriteria { it.copy(sportTypeId = null) } },
        onRemoveEquipment = { onUpdateCriteria { it.copy(equipmentId = null) } },
        onRemoveCommute = { onUpdateCriteria { it.copy(isCommute = null) } },
        onRemoveTrainer = { onUpdateCriteria { it.copy(isTrainer = null) } },
        onRemoveRace = { onUpdateCriteria { it.copy(isRace = null) } },
        onRemoveSource = { onUpdateCriteria { it.copy(source = null) } },
        onRemoveGpsTrack = { onUpdateCriteria { it.copy(hasGpsTrack = null) } },
        onRemoveDistanceRange = { onUpdateCriteria { it.copy(minDistanceMeters = null, maxDistanceMeters = null) } },
        onRemoveDurationRange = { onUpdateCriteria { it.copy(minDurationSec = null, maxDurationSec = null) } },
        onRemoveElevationRange = { onUpdateCriteria { it.copy(minElevationGainMeters = null, maxElevationGainMeters = null) } },
        onRemoveStartLocation = {
            onUpdateCriteria {
                it.copy(
                    startLocationName = null,
                    startLocationLat = null,
                    startLocationLng = null,
                    startLocationRadiusM = null
                )
            }
        },
        onRemoveCluster = {
            onUpdateCriteria {
                it.copy(
                    clusterId = null,
                    clusterName = null
                )
            }
        },
        onClearAll = onClearAll,
        sportName = sportName,
        equipmentName = equipmentName,
        modifier = modifier
    )
}

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
    onRemoveBSportType: () -> Unit = {},
    onRemoveSport: () -> Unit,
    onRemoveEquipment: () -> Unit,
    onRemoveCommute: () -> Unit,
    onRemoveTrainer: () -> Unit,
    onRemoveRace: () -> Unit = {},
    onRemoveSource: () -> Unit = {},
    onRemoveGpsTrack: () -> Unit,
    onRemoveDistanceRange: () -> Unit = {},
    onRemoveDurationRange: () -> Unit = {},
    onRemoveElevationRange: () -> Unit = {},
    onRemoveStartLocation: () -> Unit = {},
    onRemoveCluster: () -> Unit = {},
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

        // Start Location Chip
        if ((criteria.startLocationLat != null && criteria.startLocationLng != null) || !criteria.startLocationName.isNullOrBlank()) {
            item("startLocation") {
                val label = if (!criteria.startLocationName.isNullOrBlank()) {
                    criteria.startLocationName
                } else {
                    stringResource(R.string.filter_start_location)
                }
                RemovableFilterChip(
                    label = label,
                    onRemove = onRemoveStartLocation
                )
            }
        }

        // Route Cluster Chip
        if (criteria.clusterId != null || !criteria.clusterName.isNullOrBlank()) {
            item("cluster") {
                val label = if (!criteria.clusterName.isNullOrBlank()) {
                    criteria.clusterName
                } else {
                    stringResource(R.string.my_locations)
                }
                RemovableFilterChip(
                    label = label,
                    onRemove = onRemoveCluster
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

        // Base Sport Type Chip (ATT-6321)
        if (criteria.bSportType != null) {
            item("bSportType") {
                val label = when (criteria.bSportType) {
                    BSportType.BIKE -> stringResource(R.string.workout_summaries_tab_bike)
                    BSportType.RUN -> stringResource(R.string.workout_summaries_tab_run)
                    BSportType.UNKNOWN, BSportType.CONFLICT, null -> stringResource(R.string.workout_summaries_tab_other)
                }
                RemovableFilterChip(
                    label = label,
                    onRemove = onRemoveBSportType
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

        // Race Chip (ATT-2005)
        if (criteria.isRace != null) {
            item("race") {
                RemovableFilterChip(
                    label = stringResource(R.string.filter_race_only),
                    onRemove = onRemoveRace
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

        // Origin Source Chip (REQ-UI-265)
        if (criteria.source != null) {
            item("source") {
                val labelRes = when (criteria.source) {
                    WorkoutSource.TRACKED -> R.string.workout_source_tracked
                    WorkoutSource.TCX -> R.string.workout_source_tcx
                    WorkoutSource.GPX -> R.string.workout_source_gpx
                    WorkoutSource.FIT -> R.string.workout_source_fit
                }
                RemovableFilterChip(
                    label = stringResource(labelRes),
                    onRemove = onRemoveSource
                )
            }
        }

        // Distance Interval Chip
        if (criteria.minDistanceMeters != null || criteria.maxDistanceMeters != null) {
            item("distance") {
                val isImperial = FilterFormattingUtils.rememberIsImperialUnits()
                val label = FilterFormattingUtils.formatFilterDistanceChip(
                    criteria.minDistanceMeters,
                    criteria.maxDistanceMeters,
                    isImperial
                )
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

        // Elevation Gain Interval Chip (REQ-UI-425)
        if (criteria.minElevationGainMeters != null || criteria.maxElevationGainMeters != null) {
            item("elevation") {
                val isImperial = FilterFormattingUtils.rememberIsImperialUnits()
                val label = FilterFormattingUtils.formatFilterElevationChip(
                    criteria.minElevationGainMeters,
                    criteria.maxElevationGainMeters,
                    isImperial
                )
                RemovableFilterChip(
                    label = label,
                    onRemove = onRemoveElevationRange
                )
            }
        }

        // Clear All Action Button
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
