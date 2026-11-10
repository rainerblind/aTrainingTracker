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

package com.atrainingtracker.trainingtracker.ui.components.workoutlaps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import com.atrainingtracker.trainingtracker.ui.components.core.AppTableHeader
import com.atrainingtracker.trainingtracker.ui.components.core.AppTableHeaderCell
import com.atrainingtracker.trainingtracker.ui.components.core.BadgeBox
import com.atrainingtracker.trainingtracker.ui.components.core.BadgeSpacer
import com.atrainingtracker.trainingtracker.ui.aftermath.splits.LapSplitCalculator
import com.atrainingtracker.trainingtracker.ui.aftermath.splits.LapSplitChartData
import com.atrainingtracker.trainingtracker.ui.aftermath.splits.LapSplitVisualizer
import com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter
import com.atrainingtracker.trainingtracker.ui.util.MetricFormatterContext

/**
 * Pure UI state-driven composable rendering the workout laps section (REQ-UI-506.1).
 *
 * Decouples rendering from metric calculations and formatting during composition.
 */
@Composable
fun WorkoutLaps(
    lapRows: List<LapRowUiState>,
    isRunningSport: Boolean,
    lapDisplayMode: LapDisplayMode = LapDisplayMode.VISUALIZER_ONLY,
    modifier: Modifier = Modifier,
    splitChartData: LapSplitChartData? = null,
    onVisualizerLapClick: ((Long) -> Unit)? = null,
    onSplitLapClick: (() -> Unit)? = null,
    onMoveBoundaryClick: ((boundaryLapNr: Long) -> Unit)? = null
) {
    if (lapRows.isEmpty()) return

    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val displayedRows = WorkoutLapsHelper.getDisplayedRows(lapRows, isExpanded)

    val visualizerLapClick: ((Long) -> Unit)? = onVisualizerLapClick ?: run {
        if (lapRows.any { it.onClick != null }) {
            { lapNr: Long ->
                lapRows.find { it.lapNr == lapNr }?.onClick?.invoke()
                Unit
            }
        } else null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Section Header: "Laps (X)"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.laps_header, lapRows.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onMoveBoundaryClick != null && lapRows.size >= 2) {
                    IconButton(
                        onClick = { onMoveBoundaryClick(1L) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lap_move_boundary),
                            contentDescription = stringResource(R.string.move_lap_boundary_action),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (onSplitLapClick != null) {
                    IconButton(
                        onClick = onSplitLapClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lap_split_scissors),
                            contentDescription = stringResource(R.string.split_lap_action),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // High-Aesthetic Lap & Interval Split Visualizer (REQ-UI-204 / ATT-1742 / REQ-UI-229 / ATT-1870)
        if (splitChartData != null && WorkoutLapsHelper.shouldShowVisualizer(lapDisplayMode)) {
            LapSplitVisualizer(
                splitData = splitChartData,
                onLapClick = visualizerLapClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (WorkoutLapsHelper.shouldShowTable(lapDisplayMode)) {
            Spacer(modifier = Modifier.height(4.dp))

            // Table Header
            LapTableHeader(
                isRunningSport = isRunningSport,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )

            // Split Table Rows
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                displayedRows.forEachIndexed { index, row ->
                    LapRow(
                        uiState = row,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    if (index < displayedRows.size - 1) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            // Expandable toggle button if > 3 laps
            if (lapRows.size > 3) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .align(Alignment.Start)
                ) {
                    Text(
                        text = if (!isExpanded) {
                            stringResource(R.string.show_all_laps, lapRows.size)
                        } else {
                            stringResource(R.string.show_fewer_laps)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Backward-compatible domain model overload of [WorkoutLaps] (REQ-UI-506.4).
 */
@Composable
fun WorkoutLaps(
    laps: List<LapData>,
    bSportType: BSportType = BSportType.UNKNOWN,
    lapDisplayMode: LapDisplayMode = LapDisplayMode.VISUALIZER_ONLY,
    modifier: Modifier = Modifier,
    onLapClick: ((LapData) -> Unit)? = null,
    onSplitLapClick: (() -> Unit)? = null,
    onMoveBoundaryClick: ((boundaryLapNr: Long) -> Unit)? = null
) {
    if (laps.isEmpty()) return

    val formatters = LocalMetricFormatter.current
    val fastestDesc = stringResource(R.string.fastest_lap)
    val slowestDesc = stringResource(R.string.slowest_lap)

    val lapRows = remember(laps, bSportType, formatters, fastestDesc, slowestDesc, onLapClick) {
        WorkoutLapsHelper.buildLapRowUiStates(
            laps = laps,
            bSportType = bSportType,
            formatters = formatters,
            fastestBadgeDescription = fastestDesc,
            slowestBadgeDescription = slowestDesc,
            onLapClick = onLapClick
        )
    }

    val splitChartData = remember(laps, bSportType, formatters, lapDisplayMode) {
        if (WorkoutLapsHelper.shouldShowVisualizer(lapDisplayMode)) {
            LapSplitCalculator.calculateSplitData(
                laps = laps,
                bSportType = bSportType,
                paceFormatter = { formatters.pace.format_with_units(it) },
                speedFormatter = { formatters.speed.format_with_units(it) }
            )
        } else null
    }

    WorkoutLaps(
        lapRows = lapRows,
        isRunningSport = (bSportType == BSportType.RUN),
        lapDisplayMode = lapDisplayMode,
        modifier = modifier,
        splitChartData = splitChartData,
        onVisualizerLapClick = onLapClick?.let { callback ->
            { lapNr -> laps.find { it.lapNr == lapNr }?.let { callback(it) } }
        },
        onSplitLapClick = onSplitLapClick,
        onMoveBoundaryClick = onMoveBoundaryClick
    )
}

@Composable
private fun LapTableHeader(
    isRunningSport: Boolean,
    modifier: Modifier = Modifier
) {
    val speedPaceUnit = stringResource(
        MyHelper.getUnitsId(if (isRunningSport) SensorType.PACE_spm else SensorType.SPEED_mps)
    )

    AppTableHeader(modifier = modifier) {
        // Col 1: Lap Name (weight 1.6f)
        AppTableHeaderCell(
            text = stringResource(R.string.lap_table_header_lap),
            modifier = Modifier.weight(WorkoutLapsHelper.WEIGHT_LAP_NAME)
        )

        // Col 2: Duration (weight 0.85f)
        AppTableHeaderCell(
            text = stringResource(R.string.lap_table_header_time),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(WorkoutLapsHelper.WEIGHT_TIME)
        )

        // Col 3: Distance (weight 0.85f)
        AppTableHeaderCell(
            text = stringResource(R.string.lap_table_header_distance),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(WorkoutLapsHelper.WEIGHT_DISTANCE)
        )

        // Col 4: Pace / Speed unit (weight 1.1f)
        AppTableHeaderCell(
            text = speedPaceUnit,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(WorkoutLapsHelper.WEIGHT_PACE_SPEED)
        )

        // Col 5: Badge Spacer matching 26.dp Box in data rows
        BadgeSpacer()
    }
}

@Composable
fun LapRow(
    uiState: LapRowUiState,
    modifier: Modifier = Modifier
) {
    LapRow(
        displayName = uiState.displayName,
        description = uiState.description,
        timeFormatted = uiState.timeFormatted,
        distanceFormatted = uiState.distanceFormatted,
        speedPaceFormatted = uiState.speedPaceFormatted,
        badgeEmoji = uiState.badgeEmoji,
        badgeDescription = uiState.badgeDescription,
        onClick = uiState.onClick,
        modifier = modifier
    )
}

@Composable
fun LapRow(
    displayName: String,
    description: String?,
    timeFormatted: String,
    distanceFormatted: String,
    speedPaceFormatted: String,
    badgeEmoji: String?,
    badgeDescription: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Col 1: Lap Name & optional description (weight 1.6f)
        Column(
            modifier = Modifier.weight(WorkoutLapsHelper.WEIGHT_LAP_NAME)
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Col 2: Duration (weight 0.85f)
        Text(
            text = timeFormatted,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(WorkoutLapsHelper.WEIGHT_TIME)
        )

        // Col 3: Distance (weight 0.85f)
        Text(
            text = distanceFormatted,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(WorkoutLapsHelper.WEIGHT_DISTANCE)
        )

        // Col 4: Pace or Speed (weight 1.1f)
        Text(
            text = speedPaceFormatted,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(WorkoutLapsHelper.WEIGHT_PACE_SPEED)
        )

        // Col 5: Badge (Rabbit / Hedgehog) (fixed 26.dp)
        BadgeBox(
            modifier = Modifier.padding(start = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (badgeEmoji != null) {
                Text(
                    text = badgeEmoji,
                    fontSize = 14.sp,
                    modifier = Modifier.semantics {
                        if (badgeDescription != null) {
                            this.contentDescription = badgeDescription
                        }
                    }
                )
            }
        }
    }
}

/**
 * Pure helper functions for lap badge computation and list display logic.
 */
object WorkoutLapsHelper {
    const val WEIGHT_LAP_NAME = 1.35f
    const val WEIGHT_TIME = 0.9f
    const val WEIGHT_DISTANCE = 1.2f
    const val WEIGHT_PACE_SPEED = 0.8f
    const val BADGE_WIDTH_DP = 26

    enum class PerformanceBadge { FASTEST_RABBIT, SLOWEST_HEDGEHOG, NONE }

    fun determineBadges(laps: List<LapData>): Map<Long, PerformanceBadge> {
        val validSpeeds = laps.map { it.speedAverageMps }.filter { it > 0.001 }
        val distinctSpeeds = validSpeeds.distinct()
        if (distinctSpeeds.size <= 1) return emptyMap()

        val fastestSpeed = validSpeeds.maxOrNull() ?: return emptyMap()
        val slowestSpeed = validSpeeds.minOrNull() ?: return emptyMap()

        return laps.associate { lap ->
            val badge = when {
                kotlin.math.abs(lap.speedAverageMps - fastestSpeed) < 1e-5 -> PerformanceBadge.FASTEST_RABBIT
                kotlin.math.abs(lap.speedAverageMps - slowestSpeed) < 1e-5 -> PerformanceBadge.SLOWEST_HEDGEHOG
                else -> PerformanceBadge.NONE
            }
            lap.lapNr to badge
        }
    }

    fun getDisplayedLaps(laps: List<LapData>, isExpanded: Boolean): List<LapData> {
        return if (isExpanded || laps.size <= 3) laps else laps.take(3)
    }

    fun getDisplayedRows(rows: List<LapRowUiState>, isExpanded: Boolean): List<LapRowUiState> {
        return if (isExpanded || rows.size <= 3) rows else rows.take(3)
    }

    fun shouldShowVisualizer(mode: LapDisplayMode): Boolean =
        mode == LapDisplayMode.VISUALIZER_ONLY

    fun shouldShowTable(mode: LapDisplayMode): Boolean =
        mode == LapDisplayMode.TABLE_ONLY

    /**
     * Pure mapper function converting domain [LapData] models and formatters into immutable [LapRowUiState]s.
     */
    fun buildLapRowUiStates(
        laps: List<LapData>,
        bSportType: BSportType = BSportType.UNKNOWN,
        timeFormatter: (Long) -> String,
        distanceFormatter: (Double) -> String,
        paceFormatter: (Double) -> String,
        speedFormatter: (Double) -> String,
        fastestBadgeDescription: String? = null,
        slowestBadgeDescription: String? = null,
        onLapClick: ((LapData) -> Unit)? = null
    ): List<LapRowUiState> {
        if (laps.isEmpty()) return emptyList()
        val isRunningSport = (bSportType == BSportType.RUN)
        val badges = determineBadges(laps)

        return laps.mapIndexed { index, lap ->
            val lapIndex = index + 1
            val displayName = lap.getDisplayName(lapIndex)
            val badge = badges[lap.lapNr] ?: PerformanceBadge.NONE
            val badgeEmoji = when (badge) {
                PerformanceBadge.FASTEST_RABBIT -> "🐇"
                PerformanceBadge.SLOWEST_HEDGEHOG -> "🦔"
                PerformanceBadge.NONE -> null
            }
            val badgeDesc = when (badge) {
                PerformanceBadge.FASTEST_RABBIT -> fastestBadgeDescription
                PerformanceBadge.SLOWEST_HEDGEHOG -> slowestBadgeDescription
                PerformanceBadge.NONE -> null
            }

            val speedPaceFormatted = if (isRunningSport) {
                if (lap.speedAverageMps > 0.001) {
                    paceFormatter(1.0 / lap.speedAverageMps)
                } else {
                    "--"
                }
            } else {
                speedFormatter(lap.speedAverageMps)
            }

            LapRowUiState(
                lapNr = lap.lapNr,
                displayName = displayName,
                description = lap.description,
                timeFormatted = timeFormatter(lap.timeTotalS.toLong()),
                distanceFormatted = distanceFormatter(lap.distanceTotalM),
                speedPaceFormatted = speedPaceFormatted,
                badgeEmoji = badgeEmoji,
                badgeDescription = badgeDesc,
                onClick = if (onLapClick != null) { { onLapClick(lap) } } else null
            )
        }
    }

    /**
     * Overload using central [MetricFormatterContext].
     */
    fun buildLapRowUiStates(
        laps: List<LapData>,
        bSportType: BSportType = BSportType.UNKNOWN,
        formatters: MetricFormatterContext,
        fastestBadgeDescription: String? = null,
        slowestBadgeDescription: String? = null,
        onLapClick: ((LapData) -> Unit)? = null
    ): List<LapRowUiState> {
        return buildLapRowUiStates(
            laps = laps,
            bSportType = bSportType,
            timeFormatter = { formatters.time.format(it) },
            distanceFormatter = { formatters.distance.format_with_units(it) },
            paceFormatter = { formatters.pace.format(it) },
            speedFormatter = { formatters.speed.format(it) },
            fastestBadgeDescription = fastestBadgeDescription,
            slowestBadgeDescription = slowestBadgeDescription,
            onLapClick = onLapClick
        )
    }
}

// --- Previews ---

@Preview(showBackground = true, name = "Workout Laps Preview")
@Composable
private fun WorkoutLapsPreview() {
    val sampleRows = listOf(
        LapRowUiState(
            lapNr = 1L,
            displayName = "Lap 1",
            timeFormatted = "04:45",
            distanceFormatted = "1.00 km",
            speedPaceFormatted = "4:45",
            badgeEmoji = "🐇",
            badgeDescription = "Fastest lap"
        ),
        LapRowUiState(
            lapNr = 2L,
            displayName = "Lap 2",
            timeFormatted = "05:12",
            distanceFormatted = "1.00 km",
            speedPaceFormatted = "5:12"
        ),
        LapRowUiState(
            lapNr = 3L,
            displayName = "Lap 3",
            timeFormatted = "05:30",
            distanceFormatted = "1.00 km",
            speedPaceFormatted = "5:30",
            badgeEmoji = "🦔",
            badgeDescription = "Slowest lap"
        )
    )
    WorkoutLaps(
        lapRows = sampleRows,
        isRunningSport = true,
        lapDisplayMode = LapDisplayMode.TABLE_ONLY
    )
}
