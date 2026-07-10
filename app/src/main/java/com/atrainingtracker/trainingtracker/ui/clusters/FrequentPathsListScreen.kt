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

package com.atrainingtracker.trainingtracker.ui.clusters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrequentPathsListScreen(
    viewModel: FrequentPathsViewModel,
    onClusterClick: (RouteCluster) -> Unit,
    onTuneClick: () -> Unit,
    onAddClick: () -> Unit
) {
    val clusters by viewModel.allClusters.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_locations)) },
                actions = {
                    IconButton(onClick = onTuneClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings_24),
                            contentDescription = "Tune Clustering"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Cluster")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(clusters) { cluster ->
                ClusterItem(
                    cluster = cluster,
                    viewModel = viewModel,
                    onClick = { onClusterClick(cluster) }
                )
            }
        }
    }
}

@Composable
fun ClusterItem(
    cluster: RouteCluster,
    viewModel: FrequentPathsViewModel,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val distanceFormatter = remember { DistanceFormatter() }
    val sportName = remember(cluster.probableSportId) { viewModel.getSportName(cluster.probableSportId) }
    val bSportType = remember(cluster.probableSportId) { viewModel.getBSportType(cluster.probableSportId) }

    MappableListItem(onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // --- 1. TOP ROW: Standard Header (Icon + Name) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
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

            Spacer(modifier = Modifier.height(8.dp))

            // --- 2. BOTTOM AREA: Details on left, Map on right ---
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.Bottom
            ) {
                // LEFT SIDE: Metadata & Metrics (SCRUM-188 Reordered)
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // 1. Distance
                    MetricItem(
                        iconRes = R.drawable.ic_distance,
                        value = distanceFormatter.format_with_units(cluster.refDistance),
                        isPrimary = false,
                        valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 2. Sport Name
                    Text(
                        text = sportName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 3. Predicted Equipment (Technical mapping via Sport Type)
                    val linkedEquipment = remember(cluster.probableSportId) { viewModel.getLinkedEquipment(cluster.probableSportId) }
                    if (linkedEquipment.isNotEmpty()) {
                        Text(
                            text = "→ ${linkedEquipment.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Variable space (SCRUM refinement)
                    Spacer(modifier = Modifier.weight(1f))

                    // 4. Hit Count (Recordings count, Consistent with Periods)
                    Text(
                        text = stringResource(R.string.cluster_recordings_format, cluster.hitCount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // RIGHT SIDE: SMALL SQUARE MAP
                Surface(
                    modifier = Modifier.size(100.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
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
                        Box(modifier = Modifier.fillMaxSize().clickable { onClick() })
                    }
                }
            }
        }
    }
}
