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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.trainingtracker.domain.laps.LapSplitAndMoveCalculator
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.crop.CropSamplePoint
import com.atrainingtracker.trainingtracker.ui.aftermath.crop.CropWorkoutPreviewMath
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.map.MapTrack
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.PathPreviewMap
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter
import kotlin.math.roundToInt

/**
 * Interactive bottom sheet allowing athletes to create / split a lap directly along the recorded GPS polyline.
 * (REQ-UI-449 / ATT-907, with synergistic architecture for ATT-908)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateLapOnMapSheet(
    workoutId: Long,
    bSportType: BSportType = BSportType.UNKNOWN,
    initialSampleIndex: Int? = null,
    onDismiss: () -> Unit,
    onSplitConfirmed: (Int) -> Unit,
    repository: WorkoutRepository? = null,
    providedSamples: List<CropSamplePoint>? = null,
    existingLaps: List<LapData> = emptyList(),
    modifier: Modifier = Modifier
) {
    var samples by remember { mutableStateOf<List<CropSamplePoint>>(providedSamples ?: emptyList()) }
    var isLoading by remember { mutableStateOf(providedSamples == null) }
    var isSplitting by remember { mutableStateOf(false) }

    LaunchedEffect(workoutId, repository) {
        if (providedSamples == null && repository != null) {
            isLoading = true
            samples = repository.getCropSamplePoints(workoutId)
            isLoading = false
        }
    }

    var selectedIndex by rememberSaveable(samples.size, initialSampleIndex) {
        val defaultIdx = when {
            initialSampleIndex != null && initialSampleIndex in samples.indices -> initialSampleIndex
            samples.size >= 2 -> samples.size / 2
            else -> 0
        }
        mutableIntStateOf(defaultIdx)
    }

    val validationResult = remember(samples, selectedIndex, existingLaps) {
        LapSplitAndMoveCalculator.validateSplit(samples, selectedIndex, existingLaps)
    }

    val distanceFormatter = remember { DistanceFormatter() }

    AppModalBottomSheet(
        title = stringResource(R.string.create_lap_on_map_title),
        iconPainter = painterResource(R.drawable.ic_lap_split_scissors),
        onDismissRequest = { if (!isSplitting) onDismiss() },
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isSplitting
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (validationResult.isValid && !isSplitting) {
                            isSplitting = true
                            onSplitConfirmed(selectedIndex)
                        }
                    },
                    enabled = validationResult.isValid && !isSplitting && !isLoading
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lap_split_scissors),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.split_lap_confirm))
                }
            }
        }
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@AppModalBottomSheet
        }

        if (samples.size < 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.period_no_gps_data),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@AppModalBottomSheet
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Map preview of polyline with split pin
            val preExtractedPathPoints: List<PathPoint> = remember(samples) {
                samples.mapNotNull { pt ->
                    pt.latLng?.let { ll ->
                        PathPoint(
                            distance = pt.distanceTotalM,
                            latLng = ll,
                            altitude = pt.altitude ?: 0.0,
                            timeSec = pt.timeTotalS
                        )
                    }
                }
            }

            if (preExtractedPathPoints.isNotEmpty()) {
                val downsampledPath = remember(preExtractedPathPoints) {
                    CropWorkoutPreviewMath.downsamplePathPoints(preExtractedPathPoints)
                }

                val mapTrack = remember(downsampledPath) {
                    MapTrack(
                        id = workoutId,
                        type = TrackType.BEST,
                        bSportType = bSportType,
                        path = downsampledPath
                    )
                }

                val currentSplitPoint = samples.getOrNull(selectedIndex)?.latLng

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 2.dp
                ) {
                    PathPreviewMap(
                        path = mapTrack,
                        modifier = Modifier.fillMaxSize(),
                        start = downsampledPath.firstOrNull()?.latLng,
                        end = downsampledPath.lastOrNull()?.latLng,
                        apex = currentSplitPoint,
                        apexIconRes = R.drawable.ic_lap_split_scissors
                    )
                }
            }

            // 2. Precision Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                val currentDist = samples.getOrNull(selectedIndex)?.distanceTotalM ?: 0.0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.split_lap_slider_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = distanceFormatter.format_with_units(currentDist),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = selectedIndex.toFloat(),
                    onValueChange = { selectedIndex = it.roundToInt().coerceIn(0, samples.size - 1) },
                    valueRange = 0f..(samples.size - 1).toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 3. Validation error banner (if boundary violated)
            if (!validationResult.isValid && validationResult.errorMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = validationResult.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // 4. Live 2-Lap Metric Preview
            val before = validationResult.previewLapBefore
            val after = validationResult.previewLapAfter
            if (before != null && after != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LapPreviewCard(
                        title = stringResource(R.string.split_lap_preview_before),
                        lap = before,
                        bSportType = bSportType,
                        modifier = Modifier.weight(1f)
                    )
                    LapPreviewCard(
                        title = stringResource(R.string.split_lap_preview_after),
                        lap = after,
                        bSportType = bSportType,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LapPreviewCard(
    title: String,
    lap: LapData,
    bSportType: BSportType,
    modifier: Modifier = Modifier
) {
    val formatters = LocalMetricFormatter.current
    val isRunning = bSportType == BSportType.RUN

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            MetricItem(
                iconRes = R.drawable.ic_lap_timer,
                label = stringResource(R.string.lap_table_header_time),
                value = formatters.time.format(lap.timeTotalS.toLong()),
                modifier = Modifier.fillMaxWidth()
            )

            MetricItem(
                iconRes = R.drawable.ic_distance,
                label = stringResource(R.string.lap_table_header_distance),
                value = formatters.distance.format_with_units(lap.distanceTotalM),
                modifier = Modifier.fillMaxWidth()
            )

            val speedPaceFormatted = if (isRunning) {
                if (lap.speedAverageMps > 0.001) {
                    formatters.pace.format_with_units(1.0 / lap.speedAverageMps)
                } else {
                    "--"
                }
            } else {
                formatters.speed.format_with_units(lap.speedAverageMps)
            }

            MetricItem(
                iconRes = R.drawable.ic_speed,
                label = stringResource(if (isRunning) R.string.pace_short else R.string.speed_short),
                value = speedPaceFormatted,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
