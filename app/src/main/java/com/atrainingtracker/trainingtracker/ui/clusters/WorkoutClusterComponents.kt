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
 */

package com.atrainingtracker.trainingtracker.ui.clusters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.components.MappableListItem
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.map.createSensorMarker
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.*

/**
 * Standard identity row for a Workout Cluster (Icon + Name).
 */
@Composable
fun WorkoutClusterIdentityRow(
    cluster: WorkoutCluster,
    viewModel: WorkoutClustersViewModel,
    modifier: Modifier = Modifier
) {
    val bSportType = remember(cluster.probableSportId) { viewModel.getBSportType(cluster.probableSportId) }
    
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painter = painterResource(id = bSportType.iconResId),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = Color.Unspecified
        )
        Text(
            text = cluster.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Standard metadata block for a Workout Cluster (Distance, Sport, Equipment, HitCount).
 */
@Composable
fun WorkoutClusterMetadataBlock(
    cluster: WorkoutCluster,
    viewModel: WorkoutClustersViewModel,
    modifier: Modifier = Modifier,
    includeSpacer: Boolean = false,
    onHitCountClick: (() -> Unit)? = null
) {
    val distanceFormatter = remember { DistanceFormatter() }
    val sportName = remember(cluster.probableSportId) { viewModel.getSportName(cluster.probableSportId) }
    val linkedEquipment = remember(cluster.probableSportId) { viewModel.getLinkedEquipment(cluster.probableSportId) }

    Column(modifier = modifier) {
        // 1. Distance
        MetricItem(
            iconRes = R.drawable.ic_distance,
            value = distanceFormatter.format_with_units(cluster.refDistance),
            isPrimary = false,
            valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)
        )

        Spacer(modifier = Modifier.height(2.dp))

        // 2. Sport Type
        Text(
            text = sportName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 3. Resulting Equipment
        if (linkedEquipment.isNotEmpty()) {
            Text(
                text = stringResource(R.string.cluster_equipment_mapping_format, linkedEquipment.joinToString(", ")),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)
            )
        }

        if (includeSpacer) {
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }

        // 4. Hit Count
        Text(
            text = stringResource(R.string.cluster_recordings_format, cluster.hitCount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = if (onHitCountClick != null) {
                Modifier.clickable { onHitCountClick() }
            } else {
                Modifier
            }
        )
    }
}

@Composable
fun ClusterItem(
    cluster: WorkoutCluster,
    viewModel: WorkoutClustersViewModel,
    onClick: () -> Unit,
    onDeleteRequest: (WorkoutCluster) -> Unit,
    onHitCountClick: (WorkoutCluster) -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Box {
        MappableListItem(
            onClick = onClick,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showContextMenu = true
            }
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    // --- 1. TOP ROW: Standard Header (Icon + Name) ---
                    WorkoutClusterIdentityRow(cluster = cluster, viewModel = viewModel)

                    Spacer(modifier = Modifier.height(8.dp))

                    // --- 2. BOTTOM AREA: Details on left, Map on right ---
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // LEFT SIDE: Metadata & Metrics
                        WorkoutClusterMetadataBlock(
                            cluster = cluster,
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            includeSpacer = true,
                            onHitCountClick = { onHitCountClick(cluster) }
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // RIGHT SIDE: SMALL SQUARE MAP
                        Surface(
                            modifier = Modifier.size(100.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            val context = LocalContext.current
                            Box(modifier = Modifier.fillMaxSize()) {
                                val start = LatLng(cluster.startLat, cluster.startLng)
                                val end = LatLng(cluster.endLat, cluster.endLng)
                                val apex = LatLng(cluster.maxDispLat, cluster.maxDispLng)

                                val bounds = remember(cluster.minLat, cluster.maxLat, cluster.minLng, cluster.maxLng, start, end, apex) {
                                    if (cluster.minLat != null && cluster.maxLat != null && cluster.minLng != null && cluster.maxLng != null && cluster.minLat < 90.0) {
                                        LatLngBounds(LatLng(cluster.minLat, cluster.minLng), LatLng(cluster.maxLat, cluster.maxLng))
                                    } else {
                                        LatLngBounds.Builder()
                                            .include(start)
                                            .include(end)
                                            .include(apex)
                                            .build()
                                    }
                                }

                                val cameraPositionState = rememberCameraPositionState()
                                var isMapLoaded by remember { mutableStateOf(false) }

                                LaunchedEffect(bounds, isMapLoaded) {
                                    if (isMapLoaded) {
                                        cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 40))
                                    }
                                }

                                GoogleMap(
                                    modifier = Modifier.fillMaxSize(),
                                    cameraPositionState = cameraPositionState,
                                    properties = MapProperties(mapType = MapType.TERRAIN),
                                    onMapLoaded = { isMapLoaded = true },
                                    uiSettings = MapUiSettings(
                                        zoomControlsEnabled = false,
                                        scrollGesturesEnabled = false,
                                        zoomGesturesEnabled = false,
                                        tiltGesturesEnabled = false,
                                        rotationGesturesEnabled = false,
                                        myLocationButtonEnabled = false
                                    ),
                                    onMapClick = { onClick() }
                                ) {
                                    // --- RENDER PREVIEW PATHS (SCRUM-224) ---
                                    val pathColor = MaterialTheme.colorScheme.primary
                                    val isHeatmap = cluster.previewPaths.size > 1

                                    cluster.previewPaths.forEach { polyline ->
                                        val points = remember(polyline) { PolyUtil.decode(polyline) }
                                        if (points.isNotEmpty()) {
                                            Polyline(
                                                points = points,
                                                color = if (isHeatmap) pathColor.copy(alpha = 0.2f) else pathColor,
                                                width = if (isHeatmap) 6f else 4f,
                                                startCap = RoundCap(),
                                                endCap = RoundCap(),
                                                jointType = JointType.ROUND
                                            )
                                        }
                                    }

                                    // --- RENDER AUTHORITATIVE ROUTE (ATT-255) ---
                                    cluster.routePolyline?.let { polyline ->
                                        val points = remember(polyline) { PolyUtil.decode(polyline) }
                                        if (points.isNotEmpty()) {
                                            Polyline(
                                                points = points,
                                                color = TTColor.RouteSelected, // Green
                                                width = 6f,
                                                startCap = RoundCap(),
                                                endCap = RoundCap(),
                                                jointType = JointType.ROUND,
                                                zIndex = 10f // Ensure it's on top
                                            )
                                        }
                                    }

                                    Marker(
                                        state = remember(start) { MarkerState(position = start) },
                                        icon = remember { createSensorMarker(context, R.drawable.control_start, TTColor.StartPoint) }
                                    )
                                    Marker(
                                        state = remember(end) { MarkerState(position = end) },
                                        icon = remember { createSensorMarker(context, R.drawable.control_stop, TTColor.EndPoint) }
                                    )
                                    Marker(
                                        state = remember(apex) { MarkerState(position = apex) },
                                        icon = remember { createSensorMarker(context, R.drawable.ic_distance, TTColor.ApexPoint) }
                                    )
                                }
                                
                                // Transparent overlay to ensure reliable click handling in a scrollable list
                                Box(modifier = Modifier.fillMaxSize().combinedClickable(
                                    onClick = onClick,
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showContextMenu = true
                                    }
                                ))
                            }
                        }
                    }
                }
            }
        }

        // Context Menu for deletion (Pinned to Top-Start to cover the header area)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 8.dp)
        ) {
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = {
                        showContextMenu = false
                        onDeleteRequest(cluster)
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
fun UnclusteredWorkoutItem(
    workout: WorkoutData,
    viewModel: WorkoutClustersViewModel,
    onClick: () -> Unit
) {
    Box {
        MappableListItem(
            onClick = onClick
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    // --- 1. TOP ROW: Standard Header (Sport Icon + Workout Name) ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = workout.bSportType.iconResId),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color.Unspecified
                        )
                        Text(
                            text = workout.workoutName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // --- 2. BOTTOM AREA: Details on left, Map on right ---
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // LEFT SIDE: Metadata & Metrics
                        val distanceFormatter = remember { DistanceFormatter() }
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            MetricItem(
                                iconRes = R.drawable.ic_distance,
                                value = distanceFormatter.format_with_units(workout.totalDistance),
                                isPrimary = false,
                                valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                iconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = workout.sportName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!workout.equipmentName.isNullOrBlank()) {
                                Text(
                                    text = stringResource(R.string.cluster_equipment_mapping_format, workout.equipmentName),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = workout.formattedDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // RIGHT SIDE: SMALL SQUARE MAP
                        Surface(
                            modifier = Modifier.size(100.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            val context = LocalContext.current
                            Box(modifier = Modifier.fillMaxSize()) {
                                val start = workout.startLatLng
                                val end = workout.endLatLng
                                val apex = workout.maxDisplacementLatLng

                                val bounds = remember(workout.minLat, workout.maxLat, workout.minLng, workout.maxLng, start, end, apex) {
                                    if (workout.minLat != null && workout.maxLat != null && workout.minLng != null && workout.maxLng != null && workout.minLat < 90.0) {
                                        LatLngBounds(LatLng(workout.minLat, workout.minLng), LatLng(workout.maxLat, workout.maxLng))
                                    } else if (start != null && end != null && apex != null) {
                                        LatLngBounds.Builder().include(start).include(end).include(apex).build()
                                    } else null
                                }

                                val cameraPositionState = rememberCameraPositionState()
                                var isMapLoaded by remember { mutableStateOf(false) }

                                LaunchedEffect(bounds, isMapLoaded) {
                                    if (isMapLoaded && bounds != null) {
                                        cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 40))
                                    }
                                }

                                GoogleMap(
                                    modifier = Modifier.fillMaxSize(),
                                    cameraPositionState = cameraPositionState,
                                    properties = MapProperties(mapType = MapType.TERRAIN),
                                    onMapLoaded = { isMapLoaded = true },
                                    uiSettings = MapUiSettings(
                                        zoomControlsEnabled = false,
                                        scrollGesturesEnabled = false,
                                        zoomGesturesEnabled = false,
                                        tiltGesturesEnabled = false,
                                        rotationGesturesEnabled = false,
                                        myLocationButtonEnabled = false
                                    ),
                                    onMapClick = { onClick() }
                                ) {
                                    if (workout.mapPolyline.isNotEmpty()) {
                                        val points = remember(workout.mapPolyline) { PolyUtil.decode(workout.mapPolyline) }
                                        if (points.isNotEmpty()) {
                                            Polyline(
                                                points = points,
                                                color = MaterialTheme.colorScheme.primary,
                                                width = 4f,
                                                startCap = RoundCap(),
                                                endCap = RoundCap(),
                                                jointType = JointType.ROUND
                                            )
                                        }
                                    }

                                    if (start != null) {
                                        Marker(
                                            state = remember(start) { MarkerState(position = start) },
                                            icon = remember { createSensorMarker(context, R.drawable.control_start, TTColor.StartPoint) }
                                        )
                                    }
                                    if (end != null) {
                                        Marker(
                                            state = remember(end) { MarkerState(position = end) },
                                            icon = remember { createSensorMarker(context, R.drawable.control_stop, TTColor.EndPoint) }
                                        )
                                    }
                                    if (apex != null) {
                                        Marker(
                                            state = remember(apex) { MarkerState(position = apex) },
                                            icon = remember { createSensorMarker(context, R.drawable.ic_distance, TTColor.ApexPoint) }
                                        )
                                    }
                                }
                                
                                Box(modifier = Modifier.fillMaxSize().clickable { onClick() })
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Standard selection item for a Workout Cluster (Used in dropdowns and dialogs).
 */
@Composable
fun WorkoutClusterSelectionItem(
    cluster: WorkoutCluster,
    score: Double,
    sportName: String,
    bSportType: BSportType,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painter = painterResource(id = bSportType.iconResId),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = Color.Unspecified
        )

        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            // 1. Identity Row: Name (weighted) + Score (pinned right)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = cluster.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.cluster_score_brackets_format, score),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 2. Sport Row
            Text(
                text = sportName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 3. Hit Count Row (Blue/Primary, not bold)
            Text(
                text = stringResource(R.string.cluster_recordings_format, cluster.hitCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Standard dialog for selecting a Workout Cluster from a list of candidates.
 * Harmonized between Edit Workout and Manual Reassignment (SCRUM-214).
 */
@Composable
fun WorkoutClusterSelectionDialog(
    title: String,
    candidates: List<Pair<WorkoutCluster, Double>>,
    onSelect: (WorkoutCluster) -> Unit,
    onDismiss: () -> Unit,
    sportNameResolver: (Long) -> String,
    bSportTypeResolver: (Long) -> BSportType
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        modifier = Modifier.fillMaxWidth(0.95f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.cluster_suggestions_title),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    itemsIndexed(candidates) { index, pair ->
                        val cluster = pair.first
                        val score = pair.second
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    onSelect(cluster)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            WorkoutClusterSelectionItem(
                                cluster = cluster,
                                score = score,
                                sportName = sportNameResolver(cluster.probableSportId),
                                bSportType = bSportTypeResolver(cluster.probableSportId)
                            )
                        }
                        
                        if (index < candidates.size - 1) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}
