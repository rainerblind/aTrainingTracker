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

package com.atrainingtracker.trainingtracker.ui.settings.tuning.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSectionType
import com.atrainingtracker.trainingtracker.ui.components.workoutlaps.LapDisplayMode

private data class MatrixFeatureRow(
    val sectionType: WorkoutSectionType,
    val titleRes: Int,
    val listChecked: Boolean,
    val onListChange: (Boolean) -> Unit,
    val detailChecked: Boolean,
    val onDetailChange: (Boolean) -> Unit
)

/**
 * Workout cards, detail masks, and section reordering tuning category composable (REQ-UI-262).
 * Encapsulates the 8-feature matrix, section reordering list controls, and lap display mode segmented buttons.
 */
@Composable
fun WorkoutMasksAndCardsSection(
    workoutCardPrefs: WorkoutCardSectionPreferences,
    onWorkoutCardPrefsChange: (WorkoutCardSectionPreferences) -> Unit,
    workoutDetailPrefs: WorkoutDetailPreferences = WorkoutDetailPreferences(),
    onWorkoutDetailPrefsChange: (WorkoutDetailPreferences) -> Unit = {},
    workoutSectionsOrder: List<WorkoutSectionType> = WorkoutSectionType.DEFAULT_ORDER,
    onWorkoutSectionsOrderChange: (List<WorkoutSectionType>) -> Unit = {}
) {
    val resolvedOrder = remember(workoutSectionsOrder) {
        val present = workoutSectionsOrder.distinct()
        val missing = WorkoutSectionType.values().filter { !present.contains(it) }
        present + missing
    }

    fun moveSection(index: Int, targetIndex: Int) {
        if (index !in resolvedOrder.indices || targetIndex !in resolvedOrder.indices) return
        val mutable = resolvedOrder.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(targetIndex, item)
        onWorkoutSectionsOrderChange(mutable)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Section Header Subtitle
        Text(
            text = stringResource(R.string.settings_workout_card_title),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Matrix Table Column Headers (REQ-UI-240-E / REQ-UI-255)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(32.dp))
            Text(
                text = stringResource(R.string.tuning_matrix_feature),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            )
            Box(
                modifier = Modifier.width(50.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.tuning_matrix_col_list),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Box(
                modifier = Modifier.width(50.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.tuning_matrix_col_details),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 8 Feature Matrix Rows with Alternating Backgrounds & 48dp Touch Targets
        val features = resolvedOrder.map { type ->
            when (type) {
                WorkoutSectionType.DESCRIPTION -> MatrixFeatureRow(
                    sectionType = type,
                    titleRes = R.string.settings_workout_card_description,
                    listChecked = workoutCardPrefs.showDescription,
                    onListChange = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(showDescription = it)) },
                    detailChecked = workoutDetailPrefs.showDescription,
                    onDetailChange = { onWorkoutDetailPrefsChange(workoutDetailPrefs.copy(showDescription = it)) }
                )
                WorkoutSectionType.EXTREMA -> MatrixFeatureRow(
                    sectionType = type,
                    titleRes = R.string.settings_workout_card_extrema,
                    listChecked = workoutCardPrefs.showExtrema,
                    onListChange = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(showExtrema = it)) },
                    detailChecked = workoutDetailPrefs.showExtrema,
                    onDetailChange = { onWorkoutDetailPrefsChange(workoutDetailPrefs.copy(showExtrema = it)) }
                )
                WorkoutSectionType.LAPS -> MatrixFeatureRow(
                    sectionType = type,
                    titleRes = R.string.settings_workout_card_laps,
                    listChecked = workoutCardPrefs.showLaps,
                    onListChange = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(showLaps = it)) },
                    detailChecked = workoutDetailPrefs.showLaps,
                    onDetailChange = { onWorkoutDetailPrefsChange(workoutDetailPrefs.copy(showLaps = it)) }
                )
                WorkoutSectionType.STRAVA -> MatrixFeatureRow(
                    sectionType = type,
                    titleRes = R.string.settings_workout_card_strava,
                    listChecked = workoutCardPrefs.showStrava,
                    onListChange = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(showStrava = it)) },
                    detailChecked = workoutDetailPrefs.showStrava,
                    onDetailChange = { onWorkoutDetailPrefsChange(workoutDetailPrefs.copy(showStrava = it)) }
                )
                WorkoutSectionType.MAP -> MatrixFeatureRow(
                    sectionType = type,
                    titleRes = R.string.settings_workout_card_map,
                    listChecked = workoutCardPrefs.showMapPreview,
                    onListChange = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(showMapPreview = it)) },
                    detailChecked = workoutDetailPrefs.showMap,
                    onDetailChange = { onWorkoutDetailPrefsChange(workoutDetailPrefs.copy(showMap = it)) }
                )
                WorkoutSectionType.ELEVATION -> MatrixFeatureRow(
                    sectionType = type,
                    titleRes = R.string.settings_workout_card_elevation,
                    listChecked = workoutCardPrefs.showElevationProfile,
                    onListChange = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(showElevationProfile = it)) },
                    detailChecked = workoutDetailPrefs.showElevationProfile,
                    onDetailChange = { onWorkoutDetailPrefsChange(workoutDetailPrefs.copy(showElevationProfile = it)) }
                )
                WorkoutSectionType.CHARTS -> MatrixFeatureRow(
                    sectionType = type,
                    titleRes = R.string.settings_workout_card_charts,
                    listChecked = workoutCardPrefs.showTelemetryCharts,
                    onListChange = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(showTelemetryCharts = it)) },
                    detailChecked = workoutDetailPrefs.showTelemetryCharts,
                    onDetailChange = { onWorkoutDetailPrefsChange(workoutDetailPrefs.copy(showTelemetryCharts = it)) }
                )
                WorkoutSectionType.ZONES -> MatrixFeatureRow(
                    sectionType = type,
                    titleRes = R.string.settings_workout_card_zones,
                    listChecked = workoutCardPrefs.showZoneAnalysis,
                    onListChange = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(showZoneAnalysis = it)) },
                    detailChecked = workoutDetailPrefs.showZoneAnalysis,
                    onDetailChange = { onWorkoutDetailPrefsChange(workoutDetailPrefs.copy(showZoneAnalysis = it)) }
                )
            }
        }

        features.forEachIndexed { index, feature ->
            val rowBg = if (index % 2 == 0) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            } else {
                Color.Transparent
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(rowBg)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.width(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = { moveSection(index, index - 1) },
                            enabled = index > 0,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.action_move_up),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { moveSection(index, index + 1) },
                            enabled = index < features.size - 1,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.action_move_down),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Text(
                        text = stringResource(feature.titleRes),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    )
                    Box(
                        modifier = Modifier.size(50.dp, 44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Checkbox(
                            checked = feature.listChecked,
                            onCheckedChange = feature.onListChange
                        )
                    }
                    Box(
                        modifier = Modifier.size(50.dp, 44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Checkbox(
                            checked = feature.detailChecked,
                            onCheckedChange = feature.onDetailChange
                        )
                    }
                }
            }
        }

        // Dedicated Lap Display Mode Sub-Setting Section (Decoupled from reorderable rows)
        if (workoutCardPrefs.showLaps || workoutDetailPrefs.showLaps) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_lap_display_mode_title),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = workoutCardPrefs.lapDisplayMode == LapDisplayMode.TABLE_ONLY,
                        onClick = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(lapDisplayMode = LapDisplayMode.TABLE_ONLY)) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(stringResource(R.string.settings_lap_display_mode_table))
                    }
                    SegmentedButton(
                        selected = workoutCardPrefs.lapDisplayMode == LapDisplayMode.VISUALIZER_ONLY,
                        onClick = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(lapDisplayMode = LapDisplayMode.VISUALIZER_ONLY)) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(stringResource(R.string.settings_lap_display_mode_visualizer))
                    }
                }
            }
        }
    }
}

/**
 * Convenience alias for WorkoutMasksAndCardsSection (REQ-UI-262).
 */
@Composable
fun WorkoutAftermathMatrixSection(
    workoutCardPrefs: WorkoutCardSectionPreferences,
    onWorkoutCardPrefsChange: (WorkoutCardSectionPreferences) -> Unit,
    workoutDetailPrefs: WorkoutDetailPreferences = WorkoutDetailPreferences(),
    onWorkoutDetailPrefsChange: (WorkoutDetailPreferences) -> Unit = {}
) {
    WorkoutMasksAndCardsSection(
        workoutCardPrefs = workoutCardPrefs,
        onWorkoutCardPrefsChange = onWorkoutCardPrefsChange,
        workoutDetailPrefs = workoutDetailPrefs,
        onWorkoutDetailPrefsChange = onWorkoutDetailPrefsChange
    )
}
