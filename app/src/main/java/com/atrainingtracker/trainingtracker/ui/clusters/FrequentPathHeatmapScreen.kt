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

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.trainingtracker.database.RouteCluster
import com.atrainingtracker.trainingtracker.ui.map.*
import com.google.android.gms.maps.model.LatLng

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrequentPathHeatmapScreen(
    cluster: RouteCluster,
    viewModel: FrequentPathsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val workouts by viewModel.clusterWorkouts.collectAsState()
    
    val sportType = remember(cluster.probableSportId) {
        BSportType.entries.find { it.ordinal.toLong() == cluster.probableSportId } ?: BSportType.UNKNOWN
    }

    val mapTracks = remember(workouts) {
        workouts.map { it.toMapTrack().copy(isVisible = true) }
    }

    val startLabel = stringResource(R.string.start)
    val endLabel = stringResource(R.string.end)
    val apexLabel = stringResource(R.string.max_line_distance)

    val fingerprintMarkers = remember(cluster, startLabel, endLabel, apexLabel) {
        listOf(
            LocationMarker(
                position = LatLng(cluster.startLat, cluster.startLng),
                iconResId = R.drawable.ic_location,
                title = startLabel,
                iconDescriptor = createSensorMarker(context, R.drawable.ic_location, Color(0xFF2E7D32)) // Green
            ),
            LocationMarker(
                position = LatLng(cluster.endLat, cluster.endLng),
                iconResId = R.drawable.ic_location,
                title = endLabel,
                iconDescriptor = createSensorMarker(context, R.drawable.ic_location, Color(0xFFC62828) ) // Red
            ),
            LocationMarker(
                position = LatLng(cluster.maxDispLat, cluster.maxDispLng),
                iconResId = R.drawable.ic_distance,
                title = apexLabel,
                iconDescriptor = createSensorMarker(context, R.drawable.ic_distance, Color(0xFF1565C0)) // Blue
            )
        )
    }

    MapDetailLayout(
        bSportType = sportType,
        zoomFocus = MapZoomFocus.FIT_PRIMARY,
        activeScrubPath = null,
        showElevationProfile = false,
        header = {
            TopAppBar(
                title = { 
                    Column {
                        Text(cluster.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.my_locations), 
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        mapContent = {
            // Apply a heatmap-like transparency to all tracks
            mapTracks.forEach { track ->
                path(track, alpha = 0.2f)
            }
            markers(fingerprintMarkers)
        },
        overlay = {
            // Overlay for aggregated stats
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 4.dp
            ) {
                ClusterStatsContent(cluster = cluster, workoutCount = workouts.size)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun ClusterStatsContent(cluster: RouteCluster, workoutCount: Int) {
    val distanceFormatter = remember { DistanceFormatter() }
    
    Row(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(R.string.stats_workouts), style = MaterialTheme.typography.labelSmall)
            Text(text = workoutCount.toString(), style = MaterialTheme.typography.titleMedium)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(R.string.cluster_average_distance), style = MaterialTheme.typography.labelSmall)
            Text(text = distanceFormatter.format_with_units(cluster.refDistance), style = MaterialTheme.typography.titleMedium)
        }
    }
}
