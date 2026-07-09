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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.trainingtracker.database.RouteCluster
import com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreen
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.map.*
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrequentPathHeatmapScreen(
    cluster: RouteCluster,
    viewModel: FrequentPathsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val workouts by viewModel.clusterWorkouts.collectAsState()
    val peekedWorkoutDataWithTrack by viewModel.peekedWorkoutDataWithTrack.collectAsState()
    
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

    val clusterPaths = remember(workouts) {
        workouts.mapNotNull { if (it.mapPolyline.isNotEmpty()) com.google.maps.android.PolyUtil.decode(it.mapPolyline) else null }
    }

    // Adaptive heatmap parameters based on workout count
    val heatmapOpacity = when {
        cluster.hitCount <= 5 -> 0.6
        cluster.hitCount <= 20 -> 0.8
        else -> 1.0
    }

    var showRenameDialog by remember { mutableStateOf(false) }
    var workoutToMove by remember { mutableStateOf<WorkoutData?>(null) }

    if (showRenameDialog) {
        RenameClusterDialog(
            currentName = cluster.name,
            onConfirm = { newName ->
                viewModel.renameCluster(cluster, newName)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (workoutToMove != null) {
        val candidates = remember(workoutToMove) { viewModel.getCandidateClustersForWorkout(workoutToMove!!) }
        MoveWorkoutClusterDialog(
            workout = workoutToMove!!,
            currentCluster = cluster,
            candidates = candidates,
            onMove = { targetId ->
                viewModel.moveWorkout(workoutToMove!!, targetId)
                workoutToMove = null
                viewModel.clearPeekSelection()
            },
            onDismiss = { workoutToMove = null }
        )
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(skipHiddenState = false)
    )

    LaunchedEffect(peekedWorkoutDataWithTrack) {
        if (peekedWorkoutDataWithTrack != null) {
            scaffoldState.bottomSheetState.partialExpand()
        } else {
            scaffoldState.bottomSheetState.hide()
        }
    }

    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = if (peekedWorkoutDataWithTrack != null) 120.dp + navBarHeight else 0.dp,
        sheetDragHandle = null,
        sheetContent = {
            peekedWorkoutDataWithTrack?.workoutData?.let { workoutData ->
                TrackOnMapScreen(
                    workoutData = workoutData,
                    tracks = listOf(MapTrack(workoutData.id, TrackType.BEST, workoutData.bSportType, peekedWorkoutDataWithTrack!!.trackPoints)),
                    modifier = Modifier,
                    useStatusBarsPadding = false,
                    headerActions = {
                        IconButton(
                            onClick = { workoutToMove = workoutData },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Move Cluster",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        }
    ) {
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
                    actions = {
                        IconButton(onClick = { showRenameDialog = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_table_edit),
                                contentDescription = stringResource(R.string.edit_workout_name)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            mapContent = {
                heatmap(clusterPaths, opacity = heatmapOpacity)

                mapTracks.forEach { track ->
                    path(track, alpha = 0.2f, onPathClick = { id ->
                        viewModel.selectWorkoutForPeek(id)
                    })
                }
                markers(fingerprintMarkers)
            },
            overlay = {
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

    BackHandler {
        if (peekedWorkoutDataWithTrack != null) {
            viewModel.clearPeekSelection()
        } else {
            onBack()
        }
    }
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

@Composable
fun RenameClusterDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_workout_name)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun MoveWorkoutClusterDialog(
    workout: WorkoutData,
    currentCluster: RouteCluster,
    candidates: List<Pair<RouteCluster, Double>>,
    onMove: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedId by remember { mutableStateOf(currentCluster.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Column {
                Text(stringResource(R.string.cluster_move_workout_title), style = MaterialTheme.typography.titleLarge)
                Text(workout.workoutName, style = MaterialTheme.typography.bodySmall)
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                Text(stringResource(R.string.cluster_move_workout_hint), 
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                LazyColumn {
                    items(candidates) { (cluster, score) ->
                        val isSelected = selectedId == cluster.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = { selectedId = cluster.id }
                                )
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedId = cluster.id }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = cluster.name,
                                    style = if (isSelected) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) 
                                            else MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.cluster_score_format, score, cluster.hitCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onMove(selectedId) },
                enabled = selectedId != currentCluster.id
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
