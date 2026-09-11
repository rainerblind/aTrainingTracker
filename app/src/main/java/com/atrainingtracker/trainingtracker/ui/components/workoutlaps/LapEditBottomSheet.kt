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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Modal bottom sheet for inspecting and editing individual lap details (ATT-511).
 *
 * Behavior:
 * 1. Initial popup height matches the edit controls exactly: the bottom of the comments/description
 *    text field aligns with the top of the navigation bar, and the map is not shown initially.
 * 2. Swiping the popup upwards smoothly expands it up to the status bar, revealing the map segment.
 * 3. Swiping downwards collapses it back to the initial height, or dismisses when swiped down further.
 * 4. Cancel & Store action buttons are positioned above the sequential (< Previous / Next >) navigation row.
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

    val coroutineScope = rememberCoroutineScope()
    var isClosing by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            if (!isClosing) {
                isClosing = true
                onDismissRequest()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val density = LocalDensity.current
        val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()
        val navBarBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            val screenHeightPx = constraints.maxHeight.toFloat()
            val offsetY = remember { Animatable(screenHeightPx) }
            var editControlsHeightPx by remember { mutableFloatStateOf(0f) }
            var dragHandleHeightPx by remember { mutableFloatStateOf(0f) }
            val topPaddingPx = with(density) { 4.dp.toPx() }

            // Partial offset: height from top such that the bottom of the comments text field
            // aligns exactly with the top of the navigation bar.
            val totalEditControlsHeightPx = dragHandleHeightPx + topPaddingPx + editControlsHeightPx
            val partialOffset = if (editControlsHeightPx > 0f && dragHandleHeightPx > 0f) {
                (screenHeightPx - navBarBottomPx - totalEditControlsHeightPx).coerceAtLeast(statusBarTopPx)
            } else {
                screenHeightPx
            }
            val expandedOffset = statusBarTopPx
            val hiddenOffset = screenHeightPx

            var isExpanded by remember { mutableStateOf(false) }

            // Animate sheet into view and keep synced with measured partialOffset
            LaunchedEffect(partialOffset, isExpanded) {
                if (!isClosing && partialOffset < screenHeightPx) {
                    if (isExpanded) {
                        offsetY.animateTo(
                            targetValue = expandedOffset,
                            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                        )
                    } else {
                        offsetY.animateTo(
                            targetValue = partialOffset,
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                        )
                    }
                }
            }

            fun animateDismiss() {
                if (!isClosing) {
                    isClosing = true
                    coroutineScope.launch {
                        offsetY.animateTo(
                            targetValue = hiddenOffset,
                            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                        )
                        onDismissRequest()
                    }
                }
            }

            BackHandler(enabled = true) {
                if (isExpanded) {
                    coroutineScope.launch {
                        isExpanded = false
                        offsetY.animateTo(partialOffset, animationSpec = tween(250))
                    }
                } else {
                    animateDismiss()
                }
            }

            // Scrim: darkens as sheet rises
            val scrimAlpha = if (hiddenOffset > partialOffset) {
                (0.4f * ((hiddenOffset - offsetY.value) / (hiddenOffset - partialOffset)).coerceIn(0f, 1f))
            } else 0f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .pointerInput(Unit) {
                        detectTapGestures {
                            animateDismiss()
                        }
                    }
            )

            // Sheet Draggable state
            val draggableState = rememberDraggableState { delta ->
                coroutineScope.launch {
                    val newOffset = (offsetY.value + delta).coerceIn(expandedOffset, hiddenOffset)
                    offsetY.snapTo(newOffset)
                }
            }

            fun handleDragEnd(velocity: Float = 0f) {
                coroutineScope.launch {
                    val currentVal = offsetY.value
                    val midPoint = (partialOffset + expandedOffset) / 2f
                    if (velocity < -800f) {
                        // Strong swipe upwards -> expand
                        isExpanded = true
                        offsetY.animateTo(expandedOffset, animationSpec = tween(250, easing = FastOutSlowInEasing))
                    } else if (velocity > 800f) {
                        // Strong swipe downwards
                        if (isExpanded && currentVal < partialOffset) {
                            isExpanded = false
                            offsetY.animateTo(partialOffset, animationSpec = tween(250, easing = FastOutSlowInEasing))
                        } else {
                            animateDismiss()
                        }
                    } else {
                        // Position-based snap
                        if (currentVal < midPoint) {
                            isExpanded = true
                            offsetY.animateTo(expandedOffset, animationSpec = tween(250, easing = FastOutSlowInEasing))
                        } else if (currentVal < partialOffset + with(density) { 60.dp.toPx() }) {
                            isExpanded = false
                            offsetY.animateTo(partialOffset, animationSpec = tween(250, easing = FastOutSlowInEasing))
                        } else {
                            animateDismiss()
                        }
                    }
                }
            }

            // Bottom sheet Surface
            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    .heightIn(max = with(density) { (screenHeightPx - statusBarTopPx).toDp() })
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .draggable(
                            state = draggableState,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity -> handleDragEnd(velocity) }
                        )
                ) {
                    // Top Drag Handle (tappable to toggle, draggable)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { size ->
                                dragHandleHeightPx = size.height.toFloat()
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                coroutineScope.launch {
                                    if (isExpanded) {
                                        isExpanded = false
                                        offsetY.animateTo(partialOffset, animationSpec = tween(250))
                                    } else {
                                        isExpanded = true
                                        offsetY.animateTo(expandedOffset, animationSpec = tween(250))
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        BottomSheetDefaults.DragHandle()
                    }

                    // Main Sheet Content
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState, enabled = isExpanded)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Section 1 to 6: Edit Controls (Measured for initial partial popup height)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { size ->
                                    editControlsHeightPx = size.height.toFloat()
                                },
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 1. Top Header: Title
                            Text(
                                text = stringResource(R.string.edit_lap_title, lapDisplayIndex),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // 2. Action Bar: Cancel & Store (Toggled above sequential navigation row)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { animateDismiss() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.cancel))
                                }
                                Button(
                                    onClick = {
                                        saveCurrentLap()
                                        animateDismiss()
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

                            // 6. Text Fields: Name & Description (Comments)
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
                        }

                        // Navigation bar height spacer ensures the map begins below the bottom of the screen
                        // at the initial partial height, so it is revealed smoothly when swiped up.
                        Spacer(modifier = Modifier.height(with(density) { navBarBottomPx.toDp() }))

                        // Section 7: Map Segment (Always included in popup, revealed when swiped up)
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
                                        // Full workout polyline in subtle color
                                        Polyline(
                                            points = allPoints,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                            width = 4f
                                        )

                                        // Highlighted active lap segment in vibrant primary color
                                        if (lapSegment.isNotEmpty()) {
                                            Polyline(
                                                points = lapSegment,
                                                color = MaterialTheme.colorScheme.primary,
                                                width = 10f
                                            )

                                            lapSegment.firstOrNull()?.let { startPoint ->
                                                Marker(
                                                    state = remember(startPoint) { MarkerState(position = startPoint) },
                                                    icon = remember { createSensorMarker(context, R.drawable.control_start, TTColor.StartPoint) }
                                                )
                                            }

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

                        // Navigation bar padding and bottom margin
                        Spacer(modifier = Modifier.navigationBarsPadding())
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
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
