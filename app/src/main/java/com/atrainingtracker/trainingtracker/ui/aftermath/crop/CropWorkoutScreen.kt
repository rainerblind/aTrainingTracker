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

package com.atrainingtracker.trainingtracker.ui.aftermath.crop

import android.app.Application
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.map.MapTrack
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.PathPreviewMap
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropWorkoutScreen(
    workoutId: Long,
    onDismiss: () -> Unit,
    viewModel: CropWorkoutViewModel = viewModel(
        key = "crop_$workoutId",
        factory = CropWorkoutViewModel.Factory(
            application = LocalContext.current.applicationContext as Application,
            workoutId = workoutId
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    var showConfirmDialog by remember { mutableStateOf(false) }

    // Intercept back button during active cropping operation
    BackHandler(enabled = uiState.isCropping) {
        // Ignored while cropping to prevent transaction corruption
    }

    AppModalBottomSheet(
        title = stringResource(R.string.crop_workout_title),
        icon = Icons.Default.ContentCut,
        onDismissRequest = {
            if (!uiState.isCropping) onDismiss()
        },
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !uiState.isCropping
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { showConfirmDialog = true },
                    enabled = uiState.isValid && !uiState.isCropping && !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.crop_workout_action))
                }
            }
        }
    ) {
        if (uiState.isLoading) {
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

        if (uiState.points.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                val errorMessage = if (uiState.errorMessageResId == R.string.crop_insufficient_storage) {
                    stringResource(R.string.crop_insufficient_storage, WorkoutRepository.MIN_FREE_STORAGE_MB)
                } else {
                    stringResource(uiState.errorMessageResId ?: R.string.crop_export_failed_retry)
                }
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error
                )
            }
            return@AppModalBottomSheet
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Map preview of cropped segment
            val retainedPoints = remember(uiState.startIndex, uiState.endIndex, uiState.points) {
                if (uiState.points.isNotEmpty() && uiState.startIndex <= uiState.endIndex) {
                    uiState.points.subList(uiState.startIndex, uiState.endIndex + 1)
                } else emptyList()
            }

            val pathPoints = remember(retainedPoints) {
                retainedPoints.mapNotNull { pt ->
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

            if (pathPoints.isNotEmpty()) {
                val mapTrack = remember(pathPoints) {
                    MapTrack(
                        id = workoutId,
                        type = TrackType.GPS,
                        bSportType = BSportType.BIKE,
                        path = pathPoints
                    )
                }

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
                        start = pathPoints.firstOrNull()?.latLng,
                        end = pathPoints.lastOrNull()?.latLng
                    )
                }
            }

            // 2. Range Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                val startTrimDuration = remember(uiState.startIndex, uiState.points) {
                    if (uiState.startIndex > 0 && uiState.startIndex < uiState.points.size) {
                        (uiState.points[uiState.startIndex].timeTotalS - uiState.points.first().timeTotalS).coerceAtLeast(0L)
                    } else 0L
                }
                val endTrimDuration = remember(uiState.endIndex, uiState.points) {
                    if (uiState.endIndex < uiState.points.size - 1 && uiState.endIndex >= 0) {
                        (uiState.points.last().timeTotalS - uiState.points[uiState.endIndex].timeTotalS).coerceAtLeast(0L)
                    } else 0L
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Start: +${DateUtils.formatElapsedTime(startTrimDuration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "End: -${DateUtils.formatElapsedTime(endTrimDuration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val maxSliderValue = (uiState.totalPoints - 1).coerceAtLeast(1).toFloat()
                RangeSlider(
                    value = uiState.startIndex.toFloat()..uiState.endIndex.toFloat(),
                    onValueChange = { range ->
                        viewModel.updateRange(range.start.roundToInt(), range.endInclusive.roundToInt())
                    },
                    valueRange = 0f..maxSliderValue,
                    enabled = !uiState.isCropping,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 3. Metric Summary Cards
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Distanz:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = String.format("%.2f km", uiState.croppedDistanceM / 1000.0),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Dauer:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = DateUtils.formatElapsedTime(uiState.croppedDurationS),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Ø Geschwindigkeit:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = String.format("%.1f km/h", uiState.croppedAvgSpeedMps * 3.6),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 4. In-progress indicator
            if (uiState.isCropping) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.crop_workout_progress),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // Confirmation Alert Dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isCropping) showConfirmDialog = false
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(stringResource(R.string.crop_workout_confirm_title))
            },
            text = {
                Text(stringResource(R.string.crop_workout_confirm_message))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        viewModel.executeCrop(
                            onSuccess = { onDismiss() },
                            onError = { /* errorMessage handled in state */ }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    enabled = !uiState.isCropping
                ) {
                    Text(stringResource(R.string.crop_workout_confirm_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false },
                    enabled = !uiState.isCropping
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
