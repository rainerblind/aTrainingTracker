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

package com.atrainingtracker.trainingtracker.ui.components.workoutheader

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawTelemetryPruner
import com.atrainingtracker.trainingtracker.database.WorkoutSource
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.ui.dialogs.SensorDiagnosticShareSheet
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.trainingtracker.ui.theme.TTColor

/**
 * Test tags for [WorkoutHeader] action buttons, special distinction badges, and global-vs-unit visual grouping (`REQ-UI-2786`, `REQ-REV-018`).
 */
object WorkoutHeaderTestTags {
    const val GLOBAL_TABS_PILL = "workout_header_global_tabs_pill"
    const val TABS_CONFIG_BUTTON = "workout_header_tabs_config_button"
    const val ACTIONS_DIVIDER = "workout_header_actions_divider"
    const val CHANGE_CLUSTER_BUTTON = "workout_header_change_cluster_button"
    const val EDIT_BUTTON = "workout_header_edit_button"
    const val SHARE_BUTTON = "workout_header_share_button"
    const val RACE_BADGE = "workout_header_race_badge"
    const val EPIC_BADGE = "workout_header_epic_badge"
}

/**
 * Reusable header component for workout summaries and map detail views (ATT-503).
 *
 * Displays the sport icon, workout title, date/time, and an interactive cluster navigation
 * button when linked to a [com.atrainingtracker.trainingtracker.database.WorkoutCluster].
 *
 * @param data Header metadata including cluster identification and formatting.
 * @param onClicked Callback invoked when the user clicks the general header area (opens editor).
 * @param onExport Callback for file export action.
 * @param onSaveAsRoute Callback for saving the workout session as an authoritative route.
 * @param onDeleteRequest Callback for requesting workout deletion.
 * @param modifier Optional [Modifier] for layout adjustments.
 * @param menuEnabled Whether the action menu and export behaviors are enabled.
 * @param canDelete Whether the workout deletion context menu is enabled (ATT-917).
 * @param onClusterClick Optional callback invoked when the user clicks the cluster navigation button.
 * @param onEditWorkout Optional callback invoked when the user clicks the dedicated edit workout action.
 * @param onShareWorkout Optional callback invoked when the user clicks the dedicated share workout action (ATT-5809).
 * @param onOpenTabsConfig Optional callback invoked when the user clicks the global display/tabs configuration button (`REQ-UI-2786`).
 * @param onChangeCluster Optional callback invoked when the user clicks the per-workout cluster swap button (`REQ-UI-2786`).
 * @param actions Optional slot for trailing action buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHeader(
    data: WorkoutHeaderData,
    onClicked: (() -> Unit)? = null,
    onExport: (FileFormat) -> Unit,
    onSaveAsRoute: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
    menuEnabled: Boolean = true,
    canDelete: Boolean = menuEnabled,
    onMarkFinished: (() -> Unit)? = null,
    onClusterClick: ((Long) -> Unit)? = null,
    onEditWorkout: (() -> Unit)? = null,
    onShareWorkout: (() -> Unit)? = null,
    onClearRecordingErrors: (() -> Unit)? = null,
    onSyncHealthConnect: (() -> Unit)? = null,
    onOpenTabsConfig: (() -> Unit)? = null,
    onChangeCluster: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    // State to control menu visibility
    var showMenu by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var showDiagnosticSheet by remember { mutableStateOf(false) }
    var showSensorDiagnosticSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val hasLongClick = canDelete || (!data.finished && onMarkFinished != null)

    Surface(
        modifier = if (hasLongClick || onClicked != null) {
            modifier.fillMaxWidth()
                .combinedClickable(
                    onClick = { onClicked?.invoke() },
                    onLongClick = if (hasLongClick) { { showContextMenu = true } } else null
                )
        } else {
            modifier.fillMaxWidth()
        },
        color = Color.Transparent
    ) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        var measuredActionsWidthPx by remember { mutableIntStateOf(0) }

        val actionButtonsContent: @Composable () -> Unit = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasPerWorkoutActions = onChangeCluster != null ||
                    !data.recordingErrors.isNullOrBlank() ||
                    onEditWorkout != null ||
                    onShareWorkout != null ||
                    menuEnabled

                // Global/Persistent Display Configuration (distinct from per-workout actions)
                if (onOpenTabsConfig != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = TTAlpha.Faint),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag(WorkoutHeaderTestTags.GLOBAL_TABS_PILL)
                    ) {
                        Box(modifier = Modifier.padding(2.dp)) {
                            HeaderActionTooltipButton(
                                onClick = onOpenTabsConfig,
                                icon = Icons.Default.Tune,
                                contentDescription = stringResource(R.string.workout_header_tabs_config_tooltip),
                                tooltipText = stringResource(R.string.workout_header_tabs_config_tooltip),
                                buttonTag = WorkoutHeaderTestTags.TABS_CONFIG_BUTTON
                            )
                        }
                    }
                    if (hasPerWorkoutActions) {
                        Spacer(modifier = Modifier.width(6.dp))
                        VerticalDivider(
                            modifier = Modifier
                                .height(16.dp)
                                .testTag(WorkoutHeaderTestTags.ACTIONS_DIVIDER),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }

                // Per-Workout Actions: [⇄ Cluster Swap] [Diagnostic] [Edit] [Share] [Menu]
                if (onChangeCluster != null) {
                    HeaderActionTooltipButton(
                        onClick = onChangeCluster,
                        icon = Icons.Default.SwapHoriz,
                        contentDescription = stringResource(R.string.workout_header_change_cluster_tooltip),
                        tooltipText = stringResource(R.string.workout_header_change_cluster_tooltip),
                        buttonTag = WorkoutHeaderTestTags.CHANGE_CLUSTER_BUTTON
                    )
                }

                if (!data.recordingErrors.isNullOrBlank()) {
                    IconButton(
                        onClick = { showDiagnosticSheet = true },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag(com.atrainingtracker.trainingtracker.ui.dialogs.WorkoutDiagnosticTestTags.BADGE)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = stringResource(R.string.workout_diagnostic_banner_title),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (onEditWorkout != null) {
                    HeaderActionTooltipButton(
                        onClick = onEditWorkout,
                        icon = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.workout_header_edit_tooltip),
                        tooltipText = stringResource(R.string.workout_header_edit_tooltip),
                        buttonTag = WorkoutHeaderTestTags.EDIT_BUTTON
                    )
                }

                if (onShareWorkout != null) {
                    HeaderActionTooltipButton(
                        onClick = onShareWorkout,
                        icon = Icons.Default.Share,
                        contentDescription = stringResource(R.string.workout_header_share_tooltip),
                        tooltipText = stringResource(R.string.workout_header_share_tooltip),
                        buttonTag = WorkoutHeaderTestTags.SHARE_BUTTON
                    )
                }

                // Custom actions provided by caller
                actions()

                if (menuEnabled) {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_more_vert_24),
                                contentDescription = stringResource(R.string.ExportFiles),
                            )
                        }

                        // Material 3 Dropdown Menu for exporting to various file formats.
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            // 1. Define standard formats
                            val standardFormats = listOf(
                                FileFormat.TCX to R.string.tcxWrite,
                                FileFormat.GPX to R.string.gpxWrite,
                                FileFormat.FIT to R.string.fitWrite,
                                FileFormat.CSV to R.string.csvWrite,
                                FileFormat.GC to R.string.jsonWrite
                            )

                            // 2. Render standard formats
                            standardFormats.forEach { (format, labelRes) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(labelRes)) },
                                    onClick = {
                                        showMenu = false
                                        onExport(format)
                                    }
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.save_as_route)) },
                                onClick = {
                                    showMenu = false
                                    onSaveAsRoute()
                                },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_route), contentDescription = null) }
                            )

                            // Check if Strava is globally enabled AND if this workout specifically allows it
                            val stravaGloballyEnabled = TrainingApplication.uploadToCommunity(FileFormat.STRAVA)
                            val stravaMappingAvailable = data.stravaSportName != null
                            val stravaIndividuallyEnabled = data.uploadToStrava != 0

                            if (stravaGloballyEnabled && stravaIndividuallyEnabled && stravaMappingAvailable) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                painter = painterResource(R.drawable.logo_square_strava),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = Color.Unspecified
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.stravaUpload))
                                        }
                                    },
                                    onClick = {
                                        showMenu = false
                                        onExport(FileFormat.STRAVA)
                                    }
                                )
                            }

                            if (onSyncHealthConnect != null) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.health_connect_sync_manual))
                                        }
                                    },
                                    onClick = {
                                        showMenu = false
                                        onSyncHealthConnect()
                                    }
                                )
                            }

                            val hasSidecar = remember(data.fileBaseName) {
                                PolarRawTelemetryPruner.hasSidecar(context, data.fileBaseName)
                            }
                            if (hasSidecar) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.BugReport,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.report_raw_sensor_data))
                                        }
                                    },
                                    onClick = {
                                        showMenu = false
                                        showSensorDiagnosticSheet = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Box allows us to place the Menu Button at the absolute top-right
        Box(modifier = Modifier.fillMaxWidth()) {

            if (isLandscape) {
                // Landscape: Compact single horizontal row utilizing wide viewport (REQ-UI-433.2 / ATT-4774)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 44.dp)
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Image(
                        painter = painterResource(id = data.bSportType.iconResId),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        colorFilter = null
                    )

                    Text(
                        text = data.workoutName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (data.clusterId > 0 && !data.clusterName.isNullOrBlank()) {
                        Button(
                            onClick = { onClusterClick?.invoke(data.clusterId) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = TTAlpha.Faint),
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            elevation = null
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_favorite_route),
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = data.clusterName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)
                    )

                    Text(
                        text = data.sportName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)
                    )

                    Text(
                        text = "${data.formattedDate} ${data.formattedTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (data.race) {
                        WorkoutRaceBadge()
                    }
                    if (data.epic) {
                        WorkoutEpicBadge()
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    actionButtonsContent()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 8.dp, bottom = 4.dp, end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 1. TOP ROW: Sport Icon and Workout Name
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = data.bSportType.iconResId),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            colorFilter = null // Original color
                        )
                        Text(
                            text = data.workoutName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Dynamic Spacer for the Action / Menu Button area to prevent title overlap (ATT-5809 / ATT-8097)
                        val hasPerWorkoutSpacer = onChangeCluster != null ||
                            !data.recordingErrors.isNullOrBlank() ||
                            onEditWorkout != null ||
                            onShareWorkout != null ||
                            menuEnabled
                        val actionSpacerWidth = (if (onOpenTabsConfig != null) {
                            36.dp + if (hasPerWorkoutSpacer) 17.dp else 4.dp
                        } else 0.dp) +
                            (if (onChangeCluster != null) 32.dp else 0.dp) +
                            (if (!data.recordingErrors.isNullOrBlank()) 32.dp else 0.dp) +
                            (if (menuEnabled) 32.dp else 0.dp) +
                            (if (onEditWorkout != null) 32.dp else 0.dp) +
                            (if (onShareWorkout != null) 32.dp else 0.dp)
                        val measuredActionsDp = with(LocalDensity.current) { measuredActionsWidthPx.toDp() }
                        val effectiveActionSpacerWidth = maxOf(actionSpacerWidth, measuredActionsDp)
                        if (effectiveActionSpacerWidth > 0.dp) {
                            Spacer(modifier = Modifier.width(effectiveActionSpacerWidth.coerceAtLeast(40.dp)))
                        }
                    }

                    // --- Workout Cluster Info (ATT-388 / ATT-503): Positioned directly below the name ---
                    if (data.clusterId > 0 && !data.clusterName.isNullOrBlank()) {
                        Button(
                            onClick = { onClusterClick?.invoke(data.clusterId) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = TTAlpha.Faint),
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            elevation = null
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_favorite_route),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = data.clusterName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        val clusterLabel = data.clusterName ?: stringResource(R.string.unclustered)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_favorite_route),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)
                            )
                            Text(
                                text = clusterLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium),
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 2. BOTTOM CONTENT: Organized in horizontal rows
                    // Row A: Sport Specific Info (Sport Name, Equipment, Routine Commute/Indoor, Special Race/Epic Badges, Source)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = data.sportName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        data.equipmentName?.let { equipmentName ->
                            Text(
                                text = equipmentName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Routine Context: Trainer / Commute remain subtle muted secondary text
                        if (data.trainer || data.commute) {
                            val label = when {
                                data.commute -> stringResource(R.string.commute)
                                else -> stringResource(data.bSportType.indoorEquipmentResId)
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Special Distinction: Prominent Race Badge (ATT-2005)
                        if (data.race) {
                            WorkoutRaceBadge()
                        }

                        // Special Distinction: Prominent Epic Badge (ATT-9022 / REQ-REV-018.2)
                        if (data.epic) {
                            WorkoutEpicBadge()
                        }

                        // Origin Source Badge (ATT-2186)
                        if (data.source != WorkoutSource.TRACKED) {
                            val sourceLabel = when (data.source) {
                                WorkoutSource.TCX -> stringResource(R.string.workout_source_tcx)
                                WorkoutSource.GPX -> stringResource(R.string.workout_source_gpx)
                                WorkoutSource.FIT -> stringResource(R.string.workout_source_fit)
                                WorkoutSource.TRACKED -> ""
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Text(
                                    text = sourceLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Row B: Date and Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconTextRow(
                            iconRes = R.drawable.ic_date_start,
                            text = data.formattedDate,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconTextRow(
                            iconRes = R.drawable.ic_time_start,
                            text = data.formattedTime,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Row C: Start & Destination Favorite Locations (ATT-1400 / REQ-UI-184)
                    val hasStartLocation = !data.startLocationName.isNullOrBlank()
                    val hasEndLocation = !data.endLocationName.isNullOrBlank()

                    if (hasStartLocation || hasEndLocation) {
                        val startLoc = data.startLocationName
                        val endLoc = data.endLocationName
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (startLoc != null && endLoc != null && startLoc == endLoc) {
                                // Round-Trip / Loop
                                IconTextRow(
                                    iconRes = R.drawable.my_locations,
                                    text = stringResource(R.string.workout_start_and_destination, startLoc),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            } else {
                                // Point-to-Point or Single Endpoint
                                if (!startLoc.isNullOrBlank()) {
                                    IconTextRow(
                                        iconRes = R.drawable.my_locations,
                                        text = stringResource(R.string.workout_start_location, startLoc),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                                if (!endLoc.isNullOrBlank()) {
                                    IconTextRow(
                                        iconRes = R.drawable.my_locations,
                                        text = stringResource(R.string.workout_destination_location, endLoc),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Action / Menu Button Area (Pinned to Top-End)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 4.dp)
                        .onSizeChanged { measuredActionsWidthPx = it.width },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    actionButtonsContent()
                }
            }

            // Context Menu for deletion (Pinned to Top-Start to cover the header area)
            // Move it here so it's anchored to the main Box's Top-Start (SCRUM-218)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 8.dp)
            ) {
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    if (!data.finished && onMarkFinished != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mark_as_finished)) },
                            onClick = { showContextMenu = false; onMarkFinished() },
                            leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                        )
                    }
                    if (canDelete) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = { showContextMenu = false; onDeleteRequest() },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                        )
                    }
                }
            }
        }
    }

    if (showDiagnosticSheet && !data.recordingErrors.isNullOrBlank()) {
        com.atrainingtracker.trainingtracker.ui.dialogs.WorkoutDiagnosticBottomSheet(
            recordingErrorsJson = data.recordingErrors!!,
            workoutName = data.workoutName,
            sportName = data.sportName,
            onClearWarning = {
                onClearRecordingErrors?.invoke()
                showDiagnosticSheet = false
            },
            onDismiss = { showDiagnosticSheet = false }
        )
    }

    if (showSensorDiagnosticSheet && !data.fileBaseName.isNullOrBlank()) {
        SensorDiagnosticShareSheet(
            fileBaseName = data.fileBaseName!!,
            onDismiss = { showSensorDiagnosticSheet = false }
        )
    }
}

/**
 * Helper to mimic the drawableStart + drawablePadding behavior from your XML
 */
@Composable
private fun IconTextRow(
    iconRes: Int,
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = color
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            maxLines = maxLines,
            overflow = overflow
        )
    }
}


// --- PREVIEWS ---

/**
 * Parameter Provider allows us to test multiple data states in the same Preview block
 */
class WorkoutHeaderPreviewProvider : PreviewParameterProvider<WorkoutHeaderData> {
    override val values = sequenceOf(
        // Case 1: Standard Finished Ride
        WorkoutHeaderData(
            workoutName = "Afternoon Ride",
            formattedDate = "Tuesday, Apr 21",
            formattedTime = "14:30",
            sportName = "Cycling",
            bSportType = BSportType.BIKE,
            finished = true,
            equipmentName = "Specialized Tarmac",
            startTimeS = 0,
            commute = false,
            trainer = false,
            uploadToStrava = 0,
            stravaSportName = "Ride",
            startLocationName = "Home",
            endLocationName = "Office"
        ),

        // Case 2: Commute / Trainer Run (Testing Chips)
        WorkoutHeaderData(
            workoutName = "Morning Run",
            formattedDate = "Wednesday, Apr 22",
            formattedTime = "08:00",
            sportName = "Running",
            bSportType = BSportType.RUN,
            finished = true,
            commute = false,
            trainer = true,
            startTimeS = 0,
            equipmentName = null,
            uploadToStrava = 1,
            stravaSportName = "Run"
        ),
        // Case 3: No Strava Mapping
        WorkoutHeaderData(
            workoutName = "Private Walk",
            formattedDate = "Thursday, Apr 23",
            formattedTime = "10:00",
            sportName = "Walk",
            bSportType = BSportType.RUN,
            finished = true,
            commute = false,
            trainer = false,
            startTimeS = 0,
            equipmentName = null,
            uploadToStrava = 1,
            stravaSportName = "NONE"
        )
    )
}

@Preview(name = "Light Mode", showBackground = true)
@Preview(name = "Dark Mode", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewWorkoutHeader(
    @PreviewParameter(WorkoutHeaderPreviewProvider::class) data: WorkoutHeaderData
) {
    ATrainingTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            WorkoutHeader(
                data = data,
                onClicked = {},
                onExport = {},
                onSaveAsRoute = {},
                onDeleteRequest = {}
            )
        }
    }
}

@Preview(name = "Commute Tag", showBackground = true)
@Composable
private fun PreviewCommuteHeader() {
    ATrainingTrackerTheme {
        WorkoutHeader(
            data = WorkoutHeaderData(
                workoutName = "Ride to Work",
                formattedDate = "Today",
                formattedTime = "09:00",
                sportName = "Cycling",
                bSportType = BSportType.BIKE,
                commute = true,
                finished = true,
                startTimeS = 0,
                equipmentName = null,
                trainer = false,
                uploadToStrava = -1,
                stravaSportName = "Ride"
            ),
            onClicked = {},
            onExport = {},
            onSaveAsRoute = {},
            onDeleteRequest = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeaderActionTooltipButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    tooltipText: String,
    buttonTag: String,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(tooltipText)
            }
        },
        state = rememberTooltipState()
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(32.dp)
                .testTag(buttonTag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint
            )
        }
    }
}

@Preview(name = "Detail Actions Unified - Light", showBackground = true, widthDp = 411)
@Preview(name = "Detail Actions Unified - Dark", showBackground = true, widthDp = 411, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Detail Actions Narrow 360dp - Light", showBackground = true, widthDp = 360)
@Composable
private fun PreviewWorkoutHeaderDetailActionsUnified() {
    ATrainingTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            WorkoutHeader(
                data = WorkoutHeaderData(
                    workoutName = "Morning Tempo Ride Along Isar Valley",
                    formattedDate = "Today",
                    formattedTime = "07:30",
                    sportName = "Cycling",
                    bSportType = BSportType.BIKE,
                    clusterId = 1L,
                    clusterName = "Isar Valley Loop",
                    finished = true,
                    commute = false,
                    trainer = false,
                    startTimeS = 0,
                    equipmentName = "Gravel Bike",
                    uploadToStrava = 1,
                    stravaSportName = "Ride"
                ),
                menuEnabled = false,
                canDelete = false,
                onClicked = null,
                onExport = {},
                onSaveAsRoute = {},
                onDeleteRequest = {},
                onOpenTabsConfig = {},
                onChangeCluster = {},
                onEditWorkout = {},
                onShareWorkout = {}
            )
        }
    }
}

@Preview(name = "Edit and Share Buttons Active - Light", showBackground = true)
@Preview(name = "Edit and Share Buttons Active - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewWorkoutHeaderWithEditAndShare() {
    ATrainingTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            WorkoutHeader(
                data = WorkoutHeaderData(
                    workoutName = "Morning Tempo Ride with Clustered Intervals",
                    formattedDate = "Today",
                    formattedTime = "07:30",
                    sportName = "Cycling",
                    bSportType = BSportType.BIKE,
                    finished = true,
                    commute = false,
                    trainer = false,
                    startTimeS = 0,
                    equipmentName = null,
                    uploadToStrava = 1,
                    stravaSportName = "Ride"
                ),
                onClicked = {},
                onExport = {},
                onSaveAsRoute = {},
                onDeleteRequest = {},
                onEditWorkout = {},
                onShareWorkout = {}
            )
        }
    }
}

@Composable
private fun WorkoutRaceBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier.testTag(WorkoutHeaderTestTags.RACE_BADGE)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = stringResource(R.string.race_badge),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stringResource(R.string.race_badge),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun WorkoutEpicBadge(modifier: Modifier = Modifier) {
    val epicColors = TTColor.epicBadgeColors()
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = epicColors.container,
        contentColor = epicColors.onContainer,
        border = BorderStroke(0.75.dp, epicColors.accent.copy(alpha = 0.55f)),
        modifier = modifier.testTag(WorkoutHeaderTestTags.EPIC_BADGE)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = stringResource(R.string.epic_badge),
                tint = epicColors.accent,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stringResource(R.string.epic_badge),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Preview(name = "Epic & Race Badges - Light", showBackground = true)
@Preview(name = "Epic & Race Badges - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewWorkoutHeaderEpicAndRaceBadges() {
    ATrainingTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            WorkoutHeader(
                data = WorkoutHeaderData(
                    workoutName = "Ötztaler Radmarathon",
                    formattedDate = "Aug 30, 2026",
                    formattedTime = "06:30",
                    sportName = "Road Cycling",
                    bSportType = BSportType.BIKE,
                    finished = true,
                    commute = false,
                    trainer = false,
                    race = true,
                    epic = true,
                    startTimeS = 0,
                    equipmentName = "Canyon Ultimate",
                    uploadToStrava = 1,
                    stravaSportName = "Ride"
                ),
                onClicked = {},
                onExport = {},
                onSaveAsRoute = {},
                onDeleteRequest = {},
                onEditWorkout = {},
                onShareWorkout = {}
            )
        }
    }
}