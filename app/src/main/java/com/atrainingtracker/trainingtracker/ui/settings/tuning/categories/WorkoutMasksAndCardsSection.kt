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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabsConfigSheet
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSectionType
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode
import com.atrainingtracker.trainingtracker.ui.components.workoutlaps.LapDisplayMode
import kotlinx.coroutines.launch

private data class MatrixFeatureRow(
    val sectionType: WorkoutSectionType,
    val titleRes: Int,
    val listChecked: Boolean,
    val onListChange: (Boolean) -> Unit
)

private fun buildMatrixFeatureRow(
    type: WorkoutSectionType,
    prefs: WorkoutCardSectionPreferences,
    onPrefsChange: (WorkoutCardSectionPreferences) -> Unit
): MatrixFeatureRow = when (type) {
    WorkoutSectionType.DESCRIPTION -> MatrixFeatureRow(type, R.string.settings_workout_card_description, prefs.showDescription) { onPrefsChange(prefs.copy(showDescription = it)) }
    WorkoutSectionType.EXTREMA -> MatrixFeatureRow(type, R.string.settings_workout_card_extrema, prefs.showExtrema) { onPrefsChange(prefs.copy(showExtrema = it)) }
    WorkoutSectionType.LAPS -> MatrixFeatureRow(type, R.string.settings_workout_card_laps, prefs.showLaps) { onPrefsChange(prefs.copy(showLaps = it)) }
    WorkoutSectionType.STRAVA -> MatrixFeatureRow(type, R.string.settings_workout_card_strava, prefs.showStrava) { onPrefsChange(prefs.copy(showStrava = it)) }
    WorkoutSectionType.MAP -> MatrixFeatureRow(type, R.string.settings_workout_card_map, prefs.showMapPreview) { onPrefsChange(prefs.copy(showMapPreview = it)) }
    WorkoutSectionType.ELEVATION -> MatrixFeatureRow(type, R.string.settings_workout_card_elevation, prefs.showElevationProfile) { onPrefsChange(prefs.copy(showElevationProfile = it)) }
    WorkoutSectionType.CHARTS -> MatrixFeatureRow(type, R.string.settings_workout_card_charts, prefs.showTelemetryCharts) { onPrefsChange(prefs.copy(showTelemetryCharts = it)) }
    WorkoutSectionType.ZONES -> MatrixFeatureRow(type, R.string.settings_workout_card_zones, prefs.showZoneAnalysis) { onPrefsChange(prefs.copy(showZoneAnalysis = it)) }
    WorkoutSectionType.EXPORT_STATUS -> MatrixFeatureRow(type, R.string.export_status, prefs.showExportStatus) { onPrefsChange(prefs.copy(showExportStatus = it)) }
}

/**
 * Reusable 3-way [ZoneCardDisplayMode] segmented button selector (REQ-UI-386 / REQ-UI-388 / REQ-UI-414.4).
 */
@Composable
private fun ZoneDisplayModeSelector(
    label: String,
    selectedMode: ZoneCardDisplayMode,
    onModeSelected: (ZoneCardDisplayMode) -> Unit
) {
    val zoneModes = listOf(
        ZoneCardDisplayMode.STACKED_BAR to R.string.zone_mode_stacked_bar,
        ZoneCardDisplayMode.COLUMNS to R.string.zone_mode_columns,
        ZoneCardDisplayMode.HISTOGRAM to R.string.zone_mode_histogram
    )
    Text(text = label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        zoneModes.forEachIndexed { index, (mode, labelRes) ->
            val selected = selectedMode == mode
            SegmentedButton(
                selected = selected,
                onClick = { onModeSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = zoneModes.size),
                icon = {},
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Text(
                    stringResource(labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Workout cards and detail settings tuning category composable (REQ-UI-262 / REQ-UI-414).
 * Encapsulates the collapsible summary cards reorder list, domain-scoped zone display mode selectors,
 * and the workout detail tabs configuration launcher.
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

    var isSummaryListExpanded by remember { mutableStateOf(false) }

    val activeCardCount = remember(workoutCardPrefs) {
        listOf(
            workoutCardPrefs.showDescription, workoutCardPrefs.showExtrema, workoutCardPrefs.showLaps,
            workoutCardPrefs.showStrava, workoutCardPrefs.showMapPreview, workoutCardPrefs.showElevationProfile,
            workoutCardPrefs.showTelemetryCharts, workoutCardPrefs.showZoneAnalysis, workoutCardPrefs.showExportStatus
        ).count { it }
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ==========================================
        // PART 1: Trainingsliste (Summary-Karten)
        // ==========================================
        Text(
            text = stringResource(R.string.settings_workout_card_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        // Collapsible Summary Cards Header / Toggle Card (REQ-UI-414.3)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { isSummaryListExpanded = !isSummaryListExpanded },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.workout_card_reorder_toggle, activeCardCount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isSummaryListExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Expanded 9-Row Reorderable Section Table (Single List Checkbox Column, REQ-UI-414.1 / REQ-UI-414.3)
        if (isSummaryListExpanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(32.dp))
                    Text(
                        text = stringResource(R.string.tuning_matrix_feature),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                    )
                    Box(modifier = Modifier.width(50.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.tuning_matrix_col_list),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                val features = resolvedOrder.map { type ->
                    buildMatrixFeatureRow(type, workoutCardPrefs, onWorkoutCardPrefsChange)
                }

                features.forEachIndexed { index, feature ->
                    val rowBg = if (index % 2 == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f) else Color.Transparent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(rowBg)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.width(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            IconButton(onClick = { moveSection(index, index - 1) }, enabled = index > 0, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.action_move_up), Modifier.size(18.dp))
                            }
                            IconButton(onClick = { moveSection(index, index + 1) }, enabled = index < features.size - 1, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.action_move_down), Modifier.size(18.dp))
                            }
                        }

                        Text(
                            text = stringResource(feature.titleRes),
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                        )
                        Box(modifier = Modifier.size(50.dp, 44.dp), contentAlignment = Alignment.Center) {
                            Checkbox(checked = feature.listChecked, onCheckedChange = feature.onListChange)
                        }
                    }
                }
            }
        }

        // Summary Lap Display Mode Sub-Setting Section (REQ-UI-414.4)
        if (workoutCardPrefs.showLaps) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_lap_display_mode_title),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = workoutCardPrefs.lapDisplayMode == LapDisplayMode.TABLE_ONLY,
                        onClick = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(lapDisplayMode = LapDisplayMode.TABLE_ONLY)) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text(stringResource(R.string.settings_lap_display_mode_table)) }
                    SegmentedButton(
                        selected = workoutCardPrefs.lapDisplayMode == LapDisplayMode.VISUALIZER_ONLY,
                        onClick = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(lapDisplayMode = LapDisplayMode.VISUALIZER_ONLY)) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text(stringResource(R.string.settings_lap_display_mode_visualizer)) }
                }
            }
        }

        // Summary Zone Card Display Mode Sub-Setting Section (REQ-UI-414.4)
        if (workoutCardPrefs.showZoneAnalysis) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ZoneDisplayModeSelector(
                    label = stringResource(R.string.settings_zone_card_display_mode_title),
                    selectedMode = workoutCardPrefs.zoneCardDisplayMode,
                    onModeSelected = { mode -> onWorkoutCardPrefsChange(workoutCardPrefs.copy(zoneCardDisplayMode = mode)) }
                )
            }
        }

        // ==========================================
        // PART 2: Trainings-Details (Detail-Ansicht)
        // ==========================================
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        Text(
            text = stringResource(R.string.settings_workout_details_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        // Workout Detail Tabs Configuration Launcher (REQ-UI-408 / REQ-UI-414.2)
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val prefMgr = remember(context) { MyPreferenceManager(context) }
        val detailTabs by prefMgr.workoutDetailTabsOrderFlow.collectAsState(initial = remember(prefMgr) { prefMgr.getWorkoutDetailTabs() })
        var showTabsConfigSheet by remember { mutableStateOf(false) }

        OutlinedButton(
            onClick = { showTabsConfigSheet = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.workout_detail_tabs_config_title))
        }

        if (showTabsConfigSheet) {
            WorkoutDetailTabsConfigSheet(
                tabs = detailTabs,
                onTabsChange = { updated -> scope.launch { prefMgr.setWorkoutDetailTabs(updated) } },
                onResetDefaults = { scope.launch { prefMgr.resetWorkoutDetailTabs() } },
                onDismiss = { showTabsConfigSheet = false }
            )
        }

        // Details Zone Card Display Mode Sub-Setting Section (REQ-UI-414.4)
        if (workoutDetailPrefs.showZoneAnalysis) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ZoneDisplayModeSelector(
                    label = stringResource(R.string.settings_zone_card_display_mode_title),
                    selectedMode = workoutDetailPrefs.zoneCardDisplayMode,
                    onModeSelected = { mode -> onWorkoutDetailPrefsChange(workoutDetailPrefs.copy(zoneCardDisplayMode = mode)) }
                )
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
