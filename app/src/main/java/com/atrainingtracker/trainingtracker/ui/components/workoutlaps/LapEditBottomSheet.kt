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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.components.MetricLayout
import com.atrainingtracker.trainingtracker.ui.map.LapSegmentUtils
import com.atrainingtracker.trainingtracker.ui.map.createSensorMarker
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter
import com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.*

/**
 * Material 3 modal bottom sheet for inspecting and editing individual lap details (ATT-511).
 *
 * Features:
 * 1. Split summary header with active time, distance, and speed/pace.
 * 2. Quick-tag preset chips for one-tap activity tagging (Warm-up, Interval, Recovery, etc.).
 * 3. Text fields for custom lap name and multi-line notes.
 * 4. Sequential lap navigation (< Previous / Next >) with auto-save across laps.
 * 5. Interactive map segment showing the entire workout path and the active lap highlighted in foreground.
 * 6. Graceful zero-GPS fallback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LapEditBottomSheet(
    workoutData: WorkoutData,
    initialLapNr: Long,
    onDismissRequest: () -> Unit,
    onSaveLap: (lapNr: Long, name: String?, description: String?) -> Unit,
    modifier: Modifier = Modifier,
    isPlayServiceAvailable: Boolean = true
) {
    val laps = workoutData.laps
    if (laps.isEmpty()) return

    var currentLapNr by rememberSaveable { mutableLongStateOf(initialLapNr) }
    val currentLap = laps.find { it.lapNr == currentLapNr } ?: laps.first()
    val currentIndex = laps.indexOf(currentLap).coerceAtLeast(0)
    val totalLaps = laps.size
    val lapDisplayIndex = currentIndex + 1

    var nameText by remember(currentLapNr) { mutableStateOf(currentLap.name ?: "") }
    var descriptionText by remember(currentLapNr) { mutableStateOf(currentLap.description ?: "") }

    val formatters = LocalMetricFormatter.current
    val isRunningSport = (workoutData.bSportType == BSportType.RUN)

    fun saveCurrentLap() {
        val cleanName = nameText.trim().ifEmpty { null }
        val cleanDesc = descriptionText.trim().ifEmpty { null }
        onSaveLap(currentLap.lapNr, cleanName, cleanDesc)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier.statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Top Header: Title
            Text(
                text = stringResource(R.string.edit_lap_title, lapDisplayIndex),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 2. Action Bar: Cancel & Store
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        saveCurrentLap()
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.save))
                }
            }

            // 3. Sequential Lap Navigation Bar (< Previous | X / Y | Next >)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (currentIndex > 0) {
                            saveCurrentLap()
                            currentLapNr = laps[currentIndex - 1].lapNr
                        }
                    },
                    enabled = currentIndex > 0
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.previous_lap))
                }

                Text(
                    text = "$lapDisplayIndex / $totalLaps",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedButton(
                    onClick = {
                        if (currentIndex < totalLaps - 1) {
                            saveCurrentLap()
                            currentLapNr = laps[currentIndex + 1].lapNr
                        }
                    },
                    enabled = currentIndex < totalLaps - 1
                ) {
                    Text(stringResource(R.string.next_lap))
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            // 4. Split Metrics Card
            val speedPaceFormatted = if (isRunningSport) {
                if (currentLap.speedAverageMps > 0.001) {
                    formatters.pace.format_with_units(1.0 / currentLap.speedAverageMps)
                } else {
                    "--"
                }
            } else {
                formatters.speed.format_with_units(currentLap.speedAverageMps)
            }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    MetricItem(
                        iconRes = R.drawable.ic_time_active,
                        label = stringResource(R.string.time_active),
                        value = formatters.time.format(currentLap.timeTotalS.toLong()),
                        layout = MetricLayout.VERTICAL,
                        iconSize = 22.dp,
                        modifier = Modifier.weight(1f)
                    )
                    MetricItem(
                        iconRes = R.drawable.ic_distance,
                        label = stringResource(R.string.distance),
                        value = formatters.distance.format_with_units(currentLap.distanceTotalM),
                        layout = MetricLayout.VERTICAL,
                        iconSize = 22.dp,
                        modifier = Modifier.weight(1f)
                    )
                    MetricItem(
                        iconRes = R.drawable.ic_speed,
                        label = if (isRunningSport) stringResource(R.string.pace) else stringResource(R.string.speed),
                        value = speedPaceFormatted,
                        layout = MetricLayout.VERTICAL,
                        iconSize = 22.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 5. Quick-Tag Preset Chips
            val quickTags = listOf(
                stringResource(R.string.quick_tag_warmup),
                stringResource(R.string.quick_tag_interval),
                stringResource(R.string.quick_tag_recovery),
                stringResource(R.string.quick_tag_hill_climb),
                stringResource(R.string.quick_tag_tempo),
                stringResource(R.string.quick_tag_sprint),
                stringResource(R.string.quick_tag_cooldown)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickTags.forEach { tag ->
                    val isSelected = (nameText.trim() == tag)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            nameText = tag
                        },
                        label = { Text(text = tag) }
                    )
                }
            }

            // 6. Text Fields: Name & Description
            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it },
                label = { Text(stringResource(R.string.lap_name_label)) },
                placeholder = { Text("Lap $lapDisplayIndex") },
                singleLine = true,
                trailingIcon = {
                    if (nameText.isNotEmpty()) {
                        IconButton(onClick = { nameText = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = null
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = descriptionText,
                onValueChange = { descriptionText = it },
                label = { Text(stringResource(R.string.lap_description_label)) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            // 7. Segment Map Preview / Fallback
            if (isPlayServiceAvailable && workoutData.mapPolyline.isNotEmpty()) {
                val context = LocalContext.current
                val allPoints = remember(workoutData.mapPolyline) {
                    try {
                        PolyUtil.decode(workoutData.mapPolyline)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                val allDistances = remember(workoutData.encodedDistances) {
                    try {
                        NumericalEncodingUtils.decodeDoubles(workoutData.encodedDistances)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }

                val (startDistM, endDistM) = remember(workoutData.laps, currentLap.lapNr) {
                    LapSegmentUtils.calculateLapDistanceRange(workoutData.laps, currentLap.lapNr)
                }
                val lapSegment = remember(allPoints, allDistances, startDistM, endDistM) {
                    LapSegmentUtils.sliceLapSegment(allPoints, allDistances, startDistM, endDistM)
                }
                val lapBounds = remember(lapSegment) {
                    LapSegmentUtils.calculateLapBounds(lapSegment)
                }

                if (allPoints.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        val cameraPositionState = rememberCameraPositionState()
                        var isMapLoaded by remember { mutableStateOf(false) }

                        LaunchedEffect(lapBounds, isMapLoaded) {
                            if (isMapLoaded && lapBounds != null) {
                                try {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngBounds(lapBounds, 70),
                                        durationMs = 500
                                    )
                                } catch (e: Exception) {
                                    try {
                                        cameraPositionState.move(
                                            CameraUpdateFactory.newLatLngBounds(lapBounds, 70)
                                        )
                                    } catch (ignored: Exception) {}
                                }
                            }
                        }

                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = false,
                                compassEnabled = false,
                                mapToolbarEnabled = false,
                                myLocationButtonEnabled = false,
                                scrollGesturesEnabled = true,
                                zoomGesturesEnabled = true,
                                rotationGesturesEnabled = false,
                                tiltGesturesEnabled = false
                            ),
                            properties = MapProperties(mapType = MapType.TERRAIN),
                            onMapLoaded = { isMapLoaded = true }
                        ) {
                            // Subtle background polyline representing full workout
                            Polyline(
                                points = allPoints,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                width = 4f
                            )

                            // Vibrant foreground polyline for active lap segment
                            if (lapSegment.isNotEmpty()) {
                                Polyline(
                                    points = lapSegment,
                                    color = MaterialTheme.colorScheme.primary,
                                    width = 10f
                                )

                                // Lap start marker
                                lapSegment.firstOrNull()?.let { startPoint ->
                                    Marker(
                                        state = remember(startPoint) { MarkerState(position = startPoint) },
                                        icon = remember { createSensorMarker(context, R.drawable.control_start, TTColor.StartPoint) }
                                    )
                                }

                                // Lap stop marker
                                lapSegment.lastOrNull()?.let { stopPoint ->
                                    Marker(
                                        state = remember(stopPoint) { MarkerState(position = stopPoint) },
                                        icon = remember { createSensorMarker(context, R.drawable.control_stop, TTColor.EndPoint) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    NoGpsTrackCard()
                }
            } else {
                NoGpsTrackCard()
            }

            // Bottom Spacer for generous scrolling breathing room above navigation bar
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NoGpsTrackCard() {
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.no_gps_track_available),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
