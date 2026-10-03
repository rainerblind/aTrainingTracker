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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreen
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.EditWorkoutScreen
import com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.EditWorkoutViewModel
import com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.EditWorkoutViewModelFactory
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutList
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutListActions
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesViewModel
import com.atrainingtracker.trainingtracker.ui.map.MapTrack
import com.atrainingtracker.trainingtracker.ui.map.TrackOnMapAftermathViewModel
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.atrainingtracker.trainingtracker.ui.map.toMapTrack
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.saveable.rememberSaveable

class WorkoutClustersFragment : Fragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        summariesViewModel.loadWorkoutsIfNeeded()
    }

    companion object {
        const val TAG = "WorkoutClustersFragment"
        const val ARG_CLUSTER_ID = "ARG_CLUSTER_ID"

        /**
         * Creates a new instance of [WorkoutClustersFragment], optionally targeting a specific [clusterId] (ATT-503).
         */
        fun newInstance(clusterId: Long? = null) = WorkoutClustersFragment().apply {
            if (clusterId != null && clusterId > 0) {
                arguments = Bundle().apply {
                    putLong(ARG_CLUSTER_ID, clusterId)
                }
            }
        }
    }

    private val viewModel: WorkoutClustersViewModel by viewModels()
    private val summariesViewModel: WorkoutSummariesViewModel by activityViewModels()
    private val trackOnMapViewModel: TrackOnMapAftermathViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val initialClusterId = arguments?.getLong(ARG_CLUSTER_ID, -1L)?.takeIf { it > 0 }
        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {
                    WorkoutClustersScreen(
                        viewModel = viewModel,
                        summariesViewModel = summariesViewModel,
                        trackOnMapViewModel = trackOnMapViewModel,
                        initialClusterId = initialClusterId,
                        onBackToNav = { parentFragmentManager.popBackStack() }
                    )
                }
            }
        }
    }
}
