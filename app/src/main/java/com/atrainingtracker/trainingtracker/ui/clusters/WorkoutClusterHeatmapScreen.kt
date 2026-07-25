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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
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
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreen
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.components.DropdownSelector
import com.atrainingtracker.trainingtracker.ui.map.*
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutClusterHeatmapScreen(
    cluster: WorkoutCluster,
    viewModel: WorkoutClustersViewModel,
    onBack: () -> Unit,
    onHitCountClick: (WorkoutCluster) -> Unit
) {
    val context = LocalContext.current
    val workouts by viewModel.clusterWorkouts.collectAsState()
    val linkedRoute by viewModel.linkedRoute.collectAsState()
    val peekedWorkoutDataWithTrack by viewModel.peekedWorkoutDataWithTrack.collectAsState()
    
    val sportType = remember(cluster.probableSportId) { viewModel.getBSportType(cluster.probableSportId) }

    val mapTracks = remember(workouts) {
        workouts.map { it.toMapTrack().copy(isVisible = true) }
    }

    // --- FINGERPRINT EDIT STATE (SCRUM-197 Refined) ---
    var isEditingFingerprint by remember { mutableStateOf(false) }
    var editStart by remember(cluster) { mutableStateOf(LatLng(cluster.startLat, cluster.startLng)) }
    var editEnd by remember(cluster) { mutableStateOf(LatLng(cluster.endLat, cluster.endLng)) }
    var editApex by remember(cluster) { mutableStateOf(LatLng(cluster.maxDispLat, cluster.maxDispLng)) }

    val hasChanges = remember(cluster, editStart, editEnd, editApex) {
        LatLng(cluster.startLat, cluster.startLng) != editStart ||
        LatLng(cluster.endLat, cluster.endLng) != editEnd ||
        LatLng(cluster.maxDispLat, cluster.maxDispLng) != editApex
    }

    val startLabel = stringResource(R.string.start)
    val endLabel = stringResource(R.string.end)
    val apexLabel = stringResource(R.string.max_line_distance)

    val fingerprintMarkers = remember(editStart, editEnd, editApex, isEditingFingerprint, startLabel, endLabel, apexLabel) {
        listOf(
            LocationMarker(
                position = editStart,
                iconResId = R.drawable.control_start,
                title = startLabel,
                iconDescriptor = createSensorMarker(context, R.drawable.control_start, TTColor.StartPoint), // Green
                draggable = isEditingFingerprint,
                onDragEnd = { editStart = it }
            ),
            LocationMarker(
                position = editEnd,
                iconResId = R.drawable.control_stop,
                title = endLabel,
                iconDescriptor = createSensorMarker(context, R.drawable.control_stop, TTColor.EndPoint), // Red
                draggable = isEditingFingerprint,
                onDragEnd = { editEnd = it }
            ),
            LocationMarker(
                position = editApex,
                iconResId = R.drawable.ic_distance,
                title = apexLabel,
                iconDescriptor = createSensorMarker(context, R.drawable.ic_distance, TTColor.ApexPoint), // Blue
                draggable = isEditingFingerprint,
                onDragEnd = { editApex = it }
            )
        )
    }

    // --- ALL WORKOUT MARKERS (SCRUM-199) ---
    val memberAlpha = 0.3f
    val memberMarkers = remember(workouts, context) {
        // Shared descriptors (Solid colors, alpha handled by Marker property)
        val startIcon = createSensorMarker(context, R.drawable.control_start, TTColor.StartPoint)
        val endIcon = createSensorMarker(context, R.drawable.control_stop, TTColor.EndPoint)
        val apexIcon = createSensorMarker(context, R.drawable.ic_distance, TTColor.ApexPoint)

        workouts.flatMap { w ->
            val list = mutableListOf<LocationMarker>()
            val onMarkerClick: () -> Boolean = {
                if (!isEditingFingerprint) {
                    viewModel.selectWorkoutForPeek(w.id)
                    true
                } else false
            }

            w.startLatLng?.let { list.add(LocationMarker(it, R.drawable.control_start, iconDescriptor = startIcon, alpha = memberAlpha, onClick = onMarkerClick)) }
            w.endLatLng?.let { list.add(LocationMarker(it, R.drawable.control_stop, iconDescriptor = endIcon, alpha = memberAlpha, onClick = onMarkerClick)) }
            w.maxDisplacementLatLng?.let { list.add(LocationMarker(it, R.drawable.ic_distance, iconDescriptor = apexIcon, alpha = memberAlpha, onClick = onMarkerClick)) }
            list
        }
    }

    val clusterPaths = remember(workouts) {
        workouts.mapNotNull { if (it.mapPolyline.isNotEmpty()) PolyUtil.decode(it.mapPolyline) else null }
    }

    // Adaptive heatmap parameters based on workout count
    val heatmapOpacity = when {
        cluster.hitCount <= 5 -> 0.6
        cluster.hitCount <= 20 -> 0.8
        else -> 1.0
    }

    var showEditDialog by remember { mutableStateOf(false) }
    var workoutToMove by remember { mutableStateOf<WorkoutData?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showEditDialog) {
        EditWorkoutClusterIdentityDialog(
            cluster = cluster,
            onConfirm = { newName, newSportId ->
                viewModel.updateClusterIdentity(cluster, newName, newSportId)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.cluster_delete_title)) },
            text = { Text(stringResource(R.string.cluster_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCluster(cluster)
                        showDeleteConfirmation = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (workoutToMove != null) {
        val candidates = remember(workoutToMove) { viewModel.getCandidateClustersForWorkout(workoutToMove!!) }
        WorkoutClusterSelectionDialog(
            title = stringResource(R.string.cluster_move_workout_title),
            candidates = candidates,
            onSelect = { target ->
                viewModel.moveWorkout(workoutToMove!!, target.id)
                workoutToMove = null
                viewModel.clearPeekSelection()
            },
            onDismiss = { workoutToMove = null },
            sportNameResolver = { viewModel.getSportName(it) },
            bSportTypeResolver = { viewModel.getBSportType(it) }
        )
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(skipHiddenState = false)
    )

    LaunchedEffect(peekedWorkoutDataWithTrack) {
        if (peekedWorkoutDataWithTrack != null && !isEditingFingerprint) {
            scaffoldState.bottomSheetState.partialExpand()
        } else {
            scaffoldState.bottomSheetState.hide()
        }
    }

    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val clusterBounds = remember(cluster) {
        if (cluster.minLat != null && cluster.maxLat != null && cluster.minLng != null && cluster.maxLng != null) {
            com.google.android.gms.maps.model.LatLngBounds(
                LatLng(cluster.minLat, cluster.minLng),
                LatLng(cluster.maxLat, cluster.maxLng)
            )
        } else null
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = if (peekedWorkoutDataWithTrack != null && !isEditingFingerprint) 120.dp + navBarHeight else 0.dp,
        sheetDragHandle = null,
        sheetContent = {
            peekedWorkoutDataWithTrack?.workoutData?.let { workoutData ->
                TrackOnMapScreen(
                    workoutData = workoutData,
                    tracks = listOf(MapTrack(workoutData.id, TrackType.BEST, workoutData.bSportType, peekedWorkoutDataWithTrack!!.trackPoints)),
                    markers = peekedWorkoutDataWithTrack!!.markers,
                    modifier = Modifier,
                    useStatusBarsPadding = false,
                    headerActions = {
                        IconButton(
                            onClick = { workoutToMove = workoutData },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = stringResource(R.string.cluster_move_content_desc),
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
            zoomFocus = if (clusterBounds != null) MapZoomFocus.EXPLICIT_BOUNDS else MapZoomFocus.FIT_PRIMARY,
            initialBounds = clusterBounds,
            activeScrubPath = null,
            showElevationProfile = false,
            header = {
                WorkoutClusterSummaryHeader(
                    cluster = cluster,
                    viewModel = viewModel,
                    isEditing = isEditingFingerprint,
                    onBack = {
                        if (isEditingFingerprint) {
                            isEditingFingerprint = false
                            // Revert changes
                            editStart = LatLng(cluster.startLat, cluster.startLng)
                            editEnd = LatLng(cluster.endLat, cluster.endLng)
                            editApex = LatLng(cluster.maxDispLat, cluster.maxDispLng)
                        } else {
                            onBack()
                        }
                    },
                    onRename = { showEditDialog = true },
                    onEditFingerprint = { isEditingFingerprint = true },
                    onSaveFingerprint = {
                        viewModel.updateClusterFingerprint(cluster, editStart, editEnd, editApex)
                        isEditingFingerprint = false
                    },
                    onDeleteRequest = { showDeleteConfirmation = true },
                    onHitCountClick = { onHitCountClick(cluster) },
                    hasChanges = hasChanges
                )
            },
            mapContent = {
                heatmap(clusterPaths, opacity = heatmapOpacity)

                // 1. Authoritative Route (If linked - SCRUM-216)
                linkedRoute?.let { route ->
                    path(route.toMapRoute().copy(isSelected = true), alpha = 1.0f)
                }

                // 2. Member traces
                // Only allow path clicks if not editing fingerprint
                mapTracks.forEach { track ->
                    path(track, alpha = memberAlpha, onPathClick = { id ->
                        if (!isEditingFingerprint) {
                            viewModel.selectWorkoutForPeek(id)
                        }
                    })
                }
                
                // 3. Show distribution of markers for all cluster members (SCRUM-199)
                if (!isEditingFingerprint) {
                    markers(memberMarkers)
                }
                
                // Primary cluster signature
                markers(fingerprintMarkers)
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    BackHandler {
        if (peekedWorkoutDataWithTrack != null) {
            viewModel.clearPeekSelection()
        } else if (isEditingFingerprint) {
            isEditingFingerprint = false
            editStart = LatLng(cluster.startLat, cluster.startLng)
            editEnd = LatLng(cluster.endLat, cluster.endLng)
            editApex = LatLng(cluster.maxDispLat, cluster.maxDispLng)
        } else {
            onBack()
        }
    }
}

@Composable
fun WorkoutClusterSummaryHeader(
    cluster: WorkoutCluster,
    viewModel: WorkoutClustersViewModel,
    isEditing: Boolean,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onEditFingerprint: () -> Unit,
    onSaveFingerprint: () -> Unit,
    onDeleteRequest: () -> Unit,
    onHitCountClick: () -> Unit,
    hasChanges: Boolean,
    modifier: Modifier = Modifier
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    if (!isEditing) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showContextMenu = true
                    }
                }
            ),
        color = if (isEditing) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isEditing) 2.dp else 0.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // --- TOP ROW: Navigation (Conditional) + Icon + Name ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isEditing) {
                        IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }

                    WorkoutClusterIdentityRow(
                        cluster = cluster,
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f)
                    )

                    // Spacer for actions area
                    Spacer(modifier = Modifier.width(72.dp))
                }

                if (!isEditing) {
                    WorkoutClusterMetadataBlock(
                        cluster = cluster,
                        viewModel = viewModel,
                        onHitCountClick = onHitCountClick
                    )
                } else {
                    // Editing Mode Hint
                    Text(
                        text = stringResource(R.string.cluster_edit_fingerprint_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // --- ACTIONS AREA (Pinned to Top-End) ---
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isEditing) {
                    IconButton(onClick = onSaveFingerprint, enabled = hasChanges) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = stringResource(R.string.cluster_save_fingerprint_content_desc),
                            tint = if (hasChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    IconButton(onClick = onRename) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_table_edit),
                            contentDescription = stringResource(R.string.edit_workout_name)
                        )
                    }
                    IconButton(onClick = onEditFingerprint) {
                        Icon(Icons.Default.EditLocationAlt, contentDescription = stringResource(R.string.cluster_edit_fingerprint_content_desc))
                    }
                }
            }

            // Context Menu for deletion (Long-click target)
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = { showContextMenu = false; onDeleteRequest() },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
fun EditWorkoutClusterIdentityDialog(
    cluster: WorkoutCluster,
    onConfirm: (String, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(cluster.name) }
    
    val context = LocalContext.current
    val sportTypesList = remember { SportTypesRepository.getInstance(context.applicationContext as android.app.Application).sportTypesList }
    val sportNames = remember { sportTypesList.map { it.name } }
    
    var selectedSportName by remember { 
        mutableStateOf(sportTypesList.find { it.id == cluster.probableSportId }?.name ?: sportNames.firstOrNull() ?: "") 
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_workout_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                DropdownSelector(
                    label = stringResource(R.string.Sport),
                    options = sportNames,
                    selectedOption = selectedSportName,
                    onOptionSelected = { selectedSportName = it },
                    modifier = Modifier.fillMaxWidth(),
                    stayOpenOn = emptySet()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    val sportId = sportTypesList.find { it.name == selectedSportName }?.id ?: cluster.probableSportId
                    onConfirm(name, sportId) 
                },
                enabled = name.isNotBlank()
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
