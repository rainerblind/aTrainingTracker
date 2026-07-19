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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreen
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.map.MapTrack
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.atrainingtracker.trainingtracker.ui.map.toMapTrack
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import kotlinx.coroutines.flow.collectLatest

class WorkoutClustersFragment : Fragment() {

    companion object {
        const val TAG = "WorkoutClustersFragment"
        fun newInstance() = WorkoutClustersFragment()
    }

    private val viewModel: WorkoutClustersViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {
                    val selectedCluster by viewModel.selectedCluster.collectAsState()
                    var selectedUnclusteredWorkout by remember { mutableStateOf<WorkoutData?>(null) }
                    val peekedWithTrack by viewModel.peekedWorkoutDataWithTrack.collectAsState()

                    var isTuning by remember { mutableStateOf(false) }
                    var isAdding by remember { mutableStateOf(false) }

                    // SCRUM-184: Persist scroll states when navigating to/from details
                    val pagerState = rememberPagerState { 5 }
                    val allListState = rememberLazyListState()
                    val bikeListState = rememberLazyListState()
                    val runListState = rememberLazyListState()
                    val otherListState = rememberLazyListState()
                    val unclusteredListState = rememberLazyListState()
                    
                    var clusterToDelete by remember { mutableStateOf<WorkoutCluster?>(null) }

                    LaunchedEffect(Unit) {
                        viewModel.recalculationFinished.collectLatest {
                            isTuning = false
                        }
                    }

                    when {
                        isTuning -> {
                            BackHandler { isTuning = false }
                            ClusterTuningScreen(
                                viewModel = viewModel,
                                onBack = { isTuning = false }
                            )
                        }
                        isAdding -> {
                            BackHandler { isAdding = false }
                            ManualClusterScreen(
                                viewModel = viewModel,
                                onBack = { isAdding = false }
                            )
                        }
                        selectedCluster != null -> {
                            BackHandler { viewModel.selectCluster(null) }
                            WorkoutClusterHeatmapScreen(
                                cluster = selectedCluster!!,
                                viewModel = viewModel,
                                onBack = { viewModel.selectCluster(null) }
                            )
                        }
                        selectedUnclusteredWorkout != null -> {
                            val workout = selectedUnclusteredWorkout!!
                            var workoutToCluster by remember { mutableStateOf<WorkoutData?>(null) }
                            
                            LaunchedEffect(workout.id) {
                                viewModel.selectWorkoutForPeek(workout.id)
                            }
                            
                            BackHandler { 
                                viewModel.clearPeekSelection()
                                selectedUnclusteredWorkout = null 
                            }

                            // PERFORMANCE: Immediate feedback using summarized data while high-fidelity samples load
                            val initialTrack = remember(workout) { workout.toMapTrack() }
                            val isDataLoaded = peekedWithTrack?.workoutData?.id == workout.id

                            TrackOnMapScreen(
                                workoutData = workout,
                                tracks = if (isDataLoaded) {
                                    peekedWithTrack?.trackPoints?.let { points ->
                                        listOf(MapTrack(
                                            id = workout.id,
                                            type = TrackType.BEST,
                                            bSportType = workout.bSportType,
                                            path = points
                                        ))
                                    } ?: listOf(initialTrack)
                                } else {
                                    listOf(initialTrack)
                                },
                                markers = if (isDataLoaded) peekedWithTrack!!.markers else emptyList(),
                                headerActions = {
                                    IconButton(onClick = { workoutToCluster = workout }) {
                                        Icon(
                                            imageVector = Icons.Default.SwapHoriz,
                                            contentDescription = stringResource(R.string.cluster_move_workout_title)
                                        )
                                    }
                                }
                            )

                            if (workoutToCluster != null) {
                                val candidates = remember(workoutToCluster) { viewModel.getCandidateClustersForWorkout(workoutToCluster!!) }
                                WorkoutClusterSelectionDialog(
                                    title = stringResource(R.string.cluster_move_workout_title),
                                    candidates = candidates,
                                    onSelect = { target ->
                                        viewModel.moveWorkout(workoutToCluster!!, target.id)
                                        workoutToCluster = null
                                        selectedUnclusteredWorkout = null
                                        viewModel.clearPeekSelection()
                                    },
                                    onDismiss = { workoutToCluster = null },
                                    sportNameResolver = { viewModel.getSportName(it) },
                                    bSportTypeResolver = { viewModel.getBSportType(it) }
                                )
                            }
                        }
                        else -> {
                            var workoutToCluster by remember { mutableStateOf<WorkoutData?>(null) }

                            WorkoutClustersTabsScreen(
                                viewModel = viewModel,
                                pagerState = pagerState,
                                allListState = allListState,
                                bikeListState = bikeListState,
                                runListState = runListState,
                                otherListState = otherListState,
                                unclusteredListState = unclusteredListState,
                                onClusterClick = { viewModel.selectCluster(it) },
                                onWorkoutClick = { selectedUnclusteredWorkout = it },
                                onTuneClick = { isTuning = true },
                                onAddClick = { isAdding = true },
                                onDeleteRequest = { clusterToDelete = it }
                            )

                            if (workoutToCluster != null) {
                                val candidates = remember(workoutToCluster) { viewModel.getCandidateClustersForWorkout(workoutToCluster!!) }
                                WorkoutClusterSelectionDialog(
                                    title = stringResource(R.string.cluster_move_workout_title),
                                    candidates = candidates,
                                    onSelect = { target ->
                                        viewModel.moveWorkout(workoutToCluster!!, target.id)
                                        workoutToCluster = null
                                    },
                                    onDismiss = { workoutToCluster = null },
                                    sportNameResolver = { viewModel.getSportName(it) },
                                    bSportTypeResolver = { viewModel.getBSportType(it) }
                                )
                            }
                        }
                    }

                    if (clusterToDelete != null) {
                        AlertDialog(
                            onDismissRequest = { clusterToDelete = null },
                            title = { Text(stringResource(R.string.cluster_delete_title)) },
                            text = { Text(stringResource(R.string.cluster_delete_message)) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.deleteCluster(clusterToDelete!!)
                                        clusterToDelete = null
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text(stringResource(R.string.delete))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { clusterToDelete = null }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
