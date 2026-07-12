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
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.trainingtracker.database.RouteCluster
import com.atrainingtracker.trainingtracker.ui.components.MappableListItem
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.map.createSensorMarker
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*

/**
 * Standard identity row for a Route Cluster (Icon + Name).
 */
@Composable
fun RouteClusterIdentityRow(
    cluster: RouteCluster,
    viewModel: FrequentPathsViewModel,
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
 * Standard metadata block for a Route Cluster (Distance, Sport, Equipment, HitCount).
 */
@Composable
fun RouteClusterMetadataBlock(
    cluster: RouteCluster,
    viewModel: FrequentPathsViewModel,
    modifier: Modifier = Modifier,
    includeSpacer: Boolean = false
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
        )
    }
}

@Composable
fun ClusterItem(
    cluster: RouteCluster,
    viewModel: FrequentPathsViewModel,
    onClick: () -> Unit,
    onDeleteRequest: (RouteCluster) -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

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
                RouteClusterIdentityRow(cluster = cluster, viewModel = viewModel)

                Spacer(modifier = Modifier.height(8.dp))

                // --- 2. BOTTOM AREA: Details on left, Map on right ---
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // LEFT SIDE: Metadata & Metrics
                    RouteClusterMetadataBlock(
                        cluster = cluster,
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        includeSpacer = true
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

                            val bounds = remember(start, end, apex) {
                                LatLngBounds.Builder()
                                    .include(start)
                                    .include(end)
                                    .include(apex)
                                    .build()
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
                                Marker(
                                    state = remember(start) { MarkerState(position = start) },
                                    icon = remember { createSensorMarker(context, R.drawable.control_start, Color(0xFF2E7D32)) }
                                )
                                Marker(
                                    state = remember(end) { MarkerState(position = end) },
                                    icon = remember { createSensorMarker(context, R.drawable.control_stop, Color(0xFFC62828)) }
                                )
                                Marker(
                                    state = remember(apex) { MarkerState(position = apex) },
                                    icon = remember { createSensorMarker(context, R.drawable.ic_distance, Color(0xFF1565C0)) }
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

            // Context Menu for deletion (Long-click target)
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = { showContextMenu = false; onDeleteRequest(cluster) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                )
            }
        }
    }
}

/**
 * Standard selection item for a Route Cluster (Used in dropdowns and dialogs).
 */
@Composable
fun RouteClusterSelectionItem(
    cluster: RouteCluster,
    score: Double,
    sportName: String,
    bSportType: com.atrainingtracker.banalservice.BSportType,
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
 * Standard dialog for selecting a Route Cluster from a list of candidates.
 * Harmonized between Edit Workout and Manual Reassignment (SCRUM-214).
 */
@Composable
fun RouteClusterSelectionDialog(
    title: String,
    candidates: List<Pair<RouteCluster, Double>>,
    onSelect: (RouteCluster) -> Unit,
    onDismiss: () -> Unit,
    sportNameResolver: (Long) -> String,
    bSportTypeResolver: (Long) -> com.atrainingtracker.banalservice.BSportType
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
                            RouteClusterSelectionItem(
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
