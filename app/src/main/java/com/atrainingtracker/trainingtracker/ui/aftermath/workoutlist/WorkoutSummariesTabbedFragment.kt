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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.exporter.ExportStatusChangedBroadcaster
import com.atrainingtracker.trainingtracker.ui.WorkoutNavigationEvents
import com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreen
import com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.EditWorkoutScreen
import com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.EditWorkoutViewModel
import com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.EditWorkoutViewModelFactory
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.map.TrackOnMapAftermathViewModel
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.launch
import kotlin.getValue

class WorkoutSummariesTabbedFragment : Fragment() {

    // Initialize the existing ViewModel
    private val viewModel: WorkoutSummariesViewModel by viewModels()
    private val trackOnMapViewModel: TrackOnMapAftermathViewModel by viewModels()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val isPlayAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(requireActivity()) == ConnectionResult.SUCCESS

        // Ensure data is loaded, but avoid redundant full DB scans
        viewModel.loadWorkoutsIfNeeded()

        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {
                    // 1. HOIST SCROLL STATES
                    // These will live as long as the Fragment's View is alive
                    val pagerState = rememberPagerState(pageCount = { 4 })
                    val allListState = rememberLazyListState()
                    val bikeListState = rememberLazyListState()
                    val runListState = rememberLazyListState()
                    val otherListState = rememberLazyListState()
                    val scope = rememberCoroutineScope()

                    // Handle navigation to a specific workout (e.g. from notification)
                    var targetFileNameProcessed by rememberSaveable { mutableStateOf<String?>(null) }
                    
                    // 1. Observe the workouts list from ViewModel
                    val workouts by viewModel.workouts.collectAsStateWithLifecycle()

                    LaunchedEffect(workouts) {
                        val targetFileName = requireActivity().intent.getStringExtra(ExportStatusChangedBroadcaster.EXTRA_FILE_BASE_NAME)
                        if (targetFileName != null && targetFileName != targetFileNameProcessed && workouts.isNotEmpty()) {
                            val workout = workouts.find { it.fileBaseName == targetFileName }
                            if (workout != null) {
                                targetFileNameProcessed = targetFileName

                                val pageIndex = when (workout.bSportType) {
                                    BSportType.BIKE -> 1
                                    BSportType.RUN -> 2
                                    BSportType.UNKNOWN -> 3
                                    else -> 0
                                }

                                val listState = when (pageIndex) {
                                    1 -> bikeListState
                                    2 -> runListState
                                    3 -> otherListState
                                    else -> allListState
                                }

                                // Find the index in the filtered list
                                val filteredWorkouts = when (pageIndex) {
                                    1 -> workouts.filter { it.bSportType == BSportType.BIKE }
                                    2 -> workouts.filter { it.bSportType == BSportType.RUN }
                                    3 -> workouts.filter { it.bSportType == BSportType.UNKNOWN }
                                    else -> workouts
                                }
                                val itemIndex = filteredWorkouts.indexOfFirst { it.id == workout.id }

                                if (itemIndex != -1) {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pageIndex)
                                        listState.animateScrollToItem(itemIndex)
                                    }
                                }
                            }
                        }
                    }
                    val sortOrder by viewModel.sortOrder.collectAsState()
                    val isCompactView by viewModel.isCompactView.collectAsState()

                    // --- SNACKBAR FEEDBACK ---
                    val snackbarHostState = remember { SnackbarHostState() }
                    val saveRouteStatus by viewModel.saveRouteStatus.collectAsStateWithLifecycle()
                    val successMsg = stringResource(R.string.route_saved_success)
                    val errorMsg = stringResource(R.string.route_saved_failed)

                    LaunchedEffect(saveRouteStatus) {
                        saveRouteStatus?.let { success ->
                            val message = if (success) successMsg else errorMsg
                            snackbarHostState.showSnackbar(message)
                            viewModel.resetSaveRouteStatus()
                        }
                    }

                    var selectedWorkoutIdForDetails by rememberSaveable { mutableStateOf<Long?>(null) }
                    var selectedWorkoutIdForEdit by rememberSaveable { mutableStateOf<Long?>(null) }

                    // Observe the event stream
                    LaunchedEffect(Unit) {
                        WorkoutNavigationEvents.navigateToEdit.collect { workoutId ->
                            if (workoutId != -1L) {
                                selectedWorkoutIdForEdit = workoutId
                            }
                        }
                    }

                    val selectedWorkoutForDetails = selectedWorkoutIdForDetails?.let { id ->
                        workouts.find { it.id == id }
                    }

                    Scaffold(
                        snackbarHost = {
                            SnackbarHost(snackbarHostState) { data ->
                                Snackbar(
                                    snackbarData = data,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    actionColor = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        contentWindowInsets = WindowInsets(0.dp)
                    ) { paddingValues ->
                        Box(modifier = Modifier.padding(paddingValues)) {
                            if (selectedWorkoutForDetails != null) {
                                val aftermathUIState by trackOnMapViewModel.uiState.collectAsStateWithLifecycle()
                                TrackOnMapScreen(
                                    workoutData = selectedWorkoutForDetails,
                                    tracks = aftermathUIState.tracks,
                                    segments = aftermathUIState.segments,
                                    routes = aftermathUIState.routes,
                                    markers = aftermathUIState.markers,
                                    modifier = Modifier
                                )

                                // 4. Handle System Back Button
                                BackHandler {
                                    selectedWorkoutIdForDetails = null
                                }
                            } else if (selectedWorkoutIdForEdit != null) {
                                val editViewModel: EditWorkoutViewModel = viewModel(
                                    key = "edit_workout_$selectedWorkoutIdForEdit",
                                    factory = EditWorkoutViewModelFactory(
                                        requireActivity().application,
                                        selectedWorkoutIdForEdit!!
                                    )
                                )

                                EditWorkoutScreen(
                                    viewModel = editViewModel,
                                    onBack = {
                                        selectedWorkoutIdForEdit = null
                                        WorkoutNavigationEvents.reset()
                                    }
                                )

                                // 4. Handle System Back Button
                                BackHandler {
                                    selectedWorkoutIdForEdit = null
                                    WorkoutNavigationEvents.reset()
                                }

                            } else {
                                // 3. Render the Tabbed UI
                                WorkoutTabsScreen(
                                    workouts = workouts,
                                    pagerState = pagerState,
                                    allListState = allListState,
                                    bikeListState = bikeListState,
                                    runListState = runListState,
                                    otherListState = otherListState,
                                    onExportWorkoutTo = { workoutId, fileFormat ->
                                        viewModel.onExportWorkoutTo(workoutId, fileFormat)
                                    },
                                    onSaveAsRoute = { workoutData ->
                                        viewModel.saveAsRoute(workoutData)
                                    },
                                    onDeleteConfirmed = { workoutId ->
                                        viewModel.deleteWorkout(workoutId)
                                    },
                                    onEditWorkout = { workoutId ->
                                        selectedWorkoutIdForEdit = workoutId
                                    },
                                    onMapClick = { workoutData ->
                                        selectedWorkoutIdForDetails = workoutData.id
                                        trackOnMapViewModel.loadAftermathData(workoutData)
                                    },
                                    isPlayServiceAvailable = isPlayAvailable,
                                    sortOrder = sortOrder,
                                    onSortOrderChange = { viewModel.setSortOrder(it) },
                                    scrollToTop = viewModel.shouldScrollToTop(sortOrder),
                                    isCompactView = isCompactView,
                                    onToggleCompactView = { viewModel.toggleCompactView() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Handle deletion events or other one-time events from the ViewModel
        viewModel.confirmDeleteWorkoutEvent.observe(viewLifecycleOwner) { workoutId ->
            // Trigger your existing Delete Dialog logic here if needed
        }
    }


    // Companion object for Java compatibility
    companion object {
        @JvmField
        val TAG: String = "WorkoutSummariesTabbedFragment"

        @JvmStatic
        fun newInstance(): WorkoutSummariesTabbedFragment {
            return WorkoutSummariesTabbedFragment()
        }
    }
}