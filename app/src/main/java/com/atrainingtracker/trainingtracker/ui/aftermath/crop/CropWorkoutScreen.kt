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
import androidx.compose.foundation.shape.RoundedCornerShape
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

/**
 * Interactive bottom sheet composable for trimming extraneous start and end samples of a recorded workout (REQ-UI-368).
 *
 * ### Layout Architecture & Constraints:
 * - **Sheet Container**: Hosted within [AppModalBottomSheet].
 * - **Header**: Fixed at the top with localized title and dismiss button.
 * - **Action Bar (`actions`)**: Fixed and pinned at the bottom above system navigation bars via [AppModalBottomSheet]'s
 *   action slot, ensuring "Cancel" and "Crop" buttons remain permanently visible regardless of device viewport size.
 * - **Content Body**: Constrained between header and action bar via [AppModalBottomSheet]'s native scrolling container
 *   (`Modifier.weight(1f, fill = false).verticalScroll(...)`).
 * - **Constraint Invariant**: The inner content [Column] must **never** declare `Modifier.verticalScroll` or nested
 *   unbounded height modifiers. Doing so would violate Compose scroll container constraints and trigger a fatal
 *   `IllegalStateException` ("Vertically scrollable component was measured with an infinity maximum height constraints").
 *
 * @param workoutId Unique database identifier of the completed workout to crop.
 * @param onDismiss Callback invoked when the athlete dismisses or closes the bottom sheet.
 * @param viewModel ViewModel managing sample point loading, range scrubbing state, and crop execution.
 */
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
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Map preview of cropped segment (REQ-MAP-051.4: Pre-extract once and downsample to <= 500 points)
            val preExtractedPathPoints = remember(uiState.points) {
                uiState.points.map { pt ->
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

            val pathPoints = remember(uiState.startIndex, uiState.endIndex, preExtractedPathPoints) {
                if (preExtractedPathPoints.isNotEmpty() &&
                    uiState.startIndex >= 0 &&
                    uiState.endIndex < preExtractedPathPoints.size &&
                    uiState.startIndex <= uiState.endIndex
                ) {
                    val sliced = preExtractedPathPoints
                        .subList(uiState.startIndex, uiState.endIndex + 1)
                        .filterNotNull()
                    CropWorkoutPreviewMath.downsamplePathPoints(sliced)
                } else emptyList()
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
                            text = String.format(java.util.Locale.getDefault(), "%.2f km", uiState.croppedDistanceM / 1000.0),
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
                            text = String.format(java.util.Locale.getDefault(), "%.1f km/h", uiState.croppedAvgSpeedMps * 3.6),
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

/**
 * Pure mathematical helper for downsampling interactive workout crop preview polylines (REQ-MAP-051.4).
 * Bounds the number of points sent to Google Maps Polyline.setPoints during 60–120 Hz RangeSlider drags
 * while strictly preserving the exact start and end coordinates of the selected window.
 */
object CropWorkoutPreviewMath {
    const val MAX_PREVIEW_POINTS = 500

    /**
     * Downsamples [points] to at most [maxPoints] evenly spaced samples across the list,
     * guaranteeing that `first()` and `last()` are always preserved.
     */
    fun downsamplePathPoints(
        points: List<PathPoint>,
        maxPoints: Int = MAX_PREVIEW_POINTS
    ): List<PathPoint> {
        if (points.size <= maxPoints || maxPoints < 2) {
            return points
        }
        val lastIdx = points.lastIndex
        val result = ArrayList<PathPoint>(maxPoints)
        for (i in 0 until maxPoints) {
            val idx = if (i == maxPoints - 1) {
                lastIdx
            } else {
                ((i.toLong() * lastIdx) / (maxPoints - 1)).toInt()
            }
            result.add(points[idx])
        }
        return result
    }
}

