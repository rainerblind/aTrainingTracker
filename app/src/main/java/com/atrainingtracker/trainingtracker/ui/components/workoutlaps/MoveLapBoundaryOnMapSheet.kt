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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Interactive bottom sheet allowing athletes to move an existing lap boundary between adjacent laps on the GPS track.
 * (REQ-UI-450 / ATT-908)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveLapBoundaryOnMapSheet(
    workoutId: Long,
    boundaryLapNr: Long = 1L,
    bSportType: BSportType = BSportType.UNKNOWN,
    onDismiss: () -> Unit,
    onBoundaryMoved: (boundaryLapNr: Long, newBoundarySampleIndex: Int) -> Unit,
    repository: WorkoutRepository? = null,
    providedSamples: List<CropSamplePoint>? = null,
    existingLaps: List<LapData> = emptyList(),
    modifier: Modifier = Modifier
) {
    var samples by remember { mutableStateOf<List<CropSamplePoint>>(providedSamples ?: emptyList()) }
    var isLoading by remember { mutableStateOf(providedSamples == null) }
    var isMoving by remember { mutableStateOf(false) }

    LaunchedEffect(workoutId, repository) {
        if (providedSamples == null && repository != null) {
            isLoading = true
            samples = repository.getCropSamplePoints(workoutId)
            isLoading = false
        }
    }

    val maxBoundaryLap = if (existingLaps.isNotEmpty()) (existingLaps.size - 1).coerceAtLeast(1).toLong() else 1L
    var currentBoundaryLapNr by rememberSaveable(boundaryLapNr, existingLaps.size) {
        mutableLongStateOf(boundaryLapNr.coerceIn(1L, maxBoundaryLap))
    }

    val lapKIndices = remember(samples, currentBoundaryLapNr) {
        samples.indices.filter { samples[it].lapNr == currentBoundaryLapNr }
    }
    val lapKPlus1Indices = remember(samples, currentBoundaryLapNr) {
        samples.indices.filter { samples[it].lapNr == currentBoundaryLapNr + 1L }
    }

    val hasAdjacentLaps = lapKIndices.isNotEmpty() && lapKPlus1Indices.isNotEmpty()
    val startK = if (hasAdjacentLaps) lapKIndices.first() else 0
    val endKPlus1 = if (hasAdjacentLaps) lapKPlus1Indices.last() else (samples.size - 1).coerceAtLeast(0)

    val minBoundaryIndex = (startK + 1).coerceAtMost(endKPlus1 - 1)
    val maxBoundaryIndex = (endKPlus1 - 1).coerceAtLeast(startK + 1)
    val defaultJunctionIndex = if (hasAdjacentLaps) {
        lapKPlus1Indices.first().coerceIn(minBoundaryIndex, maxBoundaryIndex)
    } else {
        minBoundaryIndex
    }

    var selectedIndex by rememberSaveable(currentBoundaryLapNr, samples.size) {
        mutableIntStateOf(defaultJunctionIndex)
    }

    // Keep selected index within valid range if boundary changes
    LaunchedEffect(currentBoundaryLapNr, minBoundaryIndex, maxBoundaryIndex) {
        if (selectedIndex !in minBoundaryIndex..maxBoundaryIndex) {
            selectedIndex = defaultJunctionIndex
        }
    }

    val validationResult = remember(samples, currentBoundaryLapNr, selectedIndex, existingLaps) {
        LapSplitAndMoveCalculator.validateBoundaryMove(
            samples = samples,
            boundaryLapNr = currentBoundaryLapNr,
            newBoundaryIndex = selectedIndex,
            existingLaps = existingLaps
        )
    }

    val distanceFormatter = remember { DistanceFormatter() }

    AppModalBottomSheet(
        title = stringResource(R.string.move_lap_boundary_title),
        iconPainter = painterResource(R.drawable.ic_lap_timer),
        scrollable = false,
        onDismissRequest = { if (!isMoving) onDismiss() },
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isMoving
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (validationResult.isValid && !isMoving) {
                            isMoving = true
                            onBoundaryMoved(currentBoundaryLapNr, validationResult.clampedSampleIndex)
                        }
                    },
                    enabled = validationResult.isValid && !isMoving && !isLoading
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lap_timer),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.move_lap_boundary_confirm))
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

        if (samples.size < 2 || existingLaps.size < 2) {
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Boundary selector when workout has > 2 laps
            if (existingLaps.size > 2) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items((1 until existingLaps.size).toList()) { bLapNr ->
                        val isSelected = (bLapNr.toLong() == currentBoundaryLapNr)
                        FilterChip(
                            selected = isSelected,
                            onClick = { currentBoundaryLapNr = bLapNr.toLong() },
                            label = {
                                Text(
                                    text = stringResource(
                                        R.string.move_lap_boundary_select_boundary,
                                        bLapNr,
                                        bLapNr + 1
                                    )
                                )
                            }
                        )
                    }
                }
            }

            // 1. Map preview of polyline with movable junction pin
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
                        type = TrackType.GPS,
                        bSportType = bSportType,
                        path = downsampledPath
                    )
                }

                val currentJunctionPoint = samples.getOrNull(selectedIndex)?.latLng

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
                        apex = currentJunctionPoint
                    )
                }
            }

            // 2. Clamped Precision Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                val currentDist = samples.getOrNull(selectedIndex)?.distanceTotalM ?: 0.0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.move_lap_boundary_slider_label),
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

                if (minBoundaryIndex < maxBoundaryIndex) {
                    Slider(
                        value = selectedIndex.toFloat(),
                        onValueChange = { selectedIndex = it.roundToInt().coerceIn(minBoundaryIndex, maxBoundaryIndex) },
                        valueRange = minBoundaryIndex.toFloat()..maxBoundaryIndex.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 3. Validation error banner
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

            // 4. Live 2-Lap Metric Preview Cards
            val lapK = validationResult.previewLapK
            val lapKPlus1 = validationResult.previewLapKPlus1
            if (lapK != null && lapKPlus1 != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LapBoundaryPreviewCard(
                        title = stringResource(R.string.move_lap_boundary_lap_k_preview, currentBoundaryLapNr),
                        lap = lapK,
                        bSportType = bSportType,
                        modifier = Modifier.weight(1f)
                    )
                    LapBoundaryPreviewCard(
                        title = stringResource(R.string.move_lap_boundary_lap_k_plus_1_preview, currentBoundaryLapNr + 1L),
                        lap = lapKPlus1,
                        bSportType = bSportType,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LapBoundaryPreviewCard(
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
