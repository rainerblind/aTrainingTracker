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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreen
import com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.EditWorkoutScreen
import com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.EditWorkoutViewModel
import com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.EditWorkoutViewModelFactory
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.map.TrackOnMapAftermathViewModel
import com.atrainingtracker.trainingtracker.ui.utils.CollapsingAppBarNestedScrollConnection
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

class WorkoutSummariesListFragment : Fragment() {

    // 1. Share the ViewModel with the Activity/Parent as in the Classic version
    private val viewModel: WorkoutSummariesViewModel by activityViewModels()
    private val trackOnMapViewModel: TrackOnMapAftermathViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (DEBUG) Log.i(TAG, "onCreateView()")

        return ComposeView(requireContext()).apply {
            // Dispose the Composition when the view's LifecycleOwner is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                ATrainingTrackerTheme {
                    // 2. Retrieve Arguments (same logic as Classic)
                    val primaryTitle = arguments?.getString(ARG_PRIMARY_TITLE)
                    val secondaryTitle = arguments?.getString(ARG_SECONDARY_TITLE)
                    val bSportType = arguments?.getSerializable(ARG_BSPORT_TYPE) as? BSportType
                    val sportId = arguments?.getLong(ARG_SPORT_ID, -1)?.takeIf { it != -1L }
                    val equipId = arguments?.getLong(ARG_EQUIP_ID, -1)?.takeIf { it != -1L }
                    val startS = arguments?.getLong(ARG_START_S, -1L)?.takeIf { it != -1L }
                    val endS = arguments?.getLong(ARG_END_S, -1L)?.takeIf { it != -1L }

                    // 3. Observe the filtered Flow reactively
                    // We 'remember' the flow so we don't recreate the observer on every recomposition
                    val filteredWorkoutsFlow = remember(bSportType, sportId, equipId, startS, endS) {
                        viewModel.getFilteredWorkouts(
                            bSportType = bSportType,
                            sportTypeId = sportId,
                            equipmentId = equipId,
                            startTimeS = startS,
                            endTimeS = endS
                        )
                    }

                    val workouts by filteredWorkoutsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

                    val isPlayAvailable = remember {
                        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(requireActivity()) == ConnectionResult.SUCCESS
                    }

                    // 4. Implement Collapsing Header Logic for the Titles
                    val density = LocalDensity.current

                    val headerHeightDp = 110.dp
                    val headerHeightPx = with(density) { headerHeightDp.roundToPx() }

                    val connection = remember(headerHeightPx) {
                        CollapsingAppBarNestedScrollConnection(headerHeightPx)
                    }

                    var selectedWorkoutForDetails by rememberSaveable { mutableStateOf<Long?>(null) }
                    var selectedWorkoutIdForEdit by rememberSaveable { mutableStateOf<Long?>(null) }

                    val scrollState = rememberLazyListState()

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

                    val selectedWorkoutForDetailsData = selectedWorkoutForDetails?.let { id ->
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
                        containerColor = MaterialTheme.colorScheme.background,
                        contentWindowInsets = WindowInsets(0.dp)
                    ) { paddingValues ->
                        Box(modifier = Modifier.padding(paddingValues)) {
                            if (selectedWorkoutForDetailsData != null) {
                                // 3. Render the Detail Map Screen
                                val aftermathUIState by trackOnMapViewModel.uiState.collectAsStateWithLifecycle()
                                TrackOnMapScreen(
                                    workoutData = selectedWorkoutForDetailsData,
                                    tracks = aftermathUIState.tracks,
                                    segments = aftermathUIState.segments,
                                    routes = aftermathUIState.routes,
                                    markers = aftermathUIState.markers,
                                    zoomFocus = aftermathUIState.zoomFocus,
                                    bSportType = aftermathUIState.bSportType,
                                    modifier = Modifier
                                )

                                // 4. Handle System Back Button
                                BackHandler {
                                    selectedWorkoutForDetails = null
                                }
                            } else if (selectedWorkoutIdForEdit != null) {
                                val editViewModel: EditWorkoutViewModel = viewModel(
                                    factory = EditWorkoutViewModelFactory(
                                        requireActivity().application,
                                        selectedWorkoutIdForEdit!!
                                    )
                                )

                                ATrainingTrackerTheme {
                                    EditWorkoutScreen(
                                        viewModel = editViewModel,
                                        onBack = { selectedWorkoutIdForEdit = null }
                                    )
                                }

                                // 4. Handle System Back Button
                                BackHandler {
                                    selectedWorkoutIdForEdit = null
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .nestedScroll(connection)
                                ) {
                                    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()

                                    // 5. Scroll to Top when Sort Order changes
                                    LaunchedEffect(sortOrder) {
                                        if (viewModel.shouldScrollToTop(sortOrder)) {
                                            scrollState.scrollToItem(0)
                                        }
                                    }

                                    var workoutIdToDelete by remember { mutableLongStateOf(-1L) }
                                    val workoutToDelete = remember(workoutIdToDelete, workouts) {
                                        workouts.find { it.id == workoutIdToDelete }
                                    }

                                    if (workoutToDelete != null) {
                                        WorkoutDeleteDialog(
                                            workout = workoutToDelete,
                                            onConfirm = { id -> viewModel.deleteWorkout(id) },
                                            onDismiss = { workoutIdToDelete = -1L }
                                        )
                                    }

                                    // THE LIST (Content)
                                    WorkoutList(
                                        scrollState = scrollState,
                                        workouts = workouts,
                                        isPlayServiceAvailable = isPlayAvailable,
                                        onExportWorkout = { id, fileFormat ->
                                            viewModel.onExportWorkoutTo(id, fileFormat)
                                        },
                                        onSaveAsRoute = { workoutData ->
                                            viewModel.saveAsRoute(workoutData)
                                        },
                                        onDeleteRequest = { id -> workoutIdToDelete = id },
                                        onEditWorkout = { id ->
                                            selectedWorkoutIdForEdit = id
                                        },
                                        onMapClick = { workoutData ->
                                            selectedWorkoutForDetails = workoutData.id
                                            trackOnMapViewModel.loadAftermathData(workoutData)
                                        },
                                        isCompactView = viewModel.isCompactView.collectAsStateWithLifecycle().value,
                                        appBarOffsetPx = connection.appBarOffset,
                                        headerHeightPx = headerHeightPx.toFloat()
                                    )

                                    // THE HEADER (Titles)
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .offset { IntOffset(0, connection.appBarOffset) },
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .statusBarsPadding()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = primaryTitle ?: "",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = secondaryTitle ?: "",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            WorkoutListActions(
                                                isCompactView = viewModel.isCompactView.collectAsStateWithLifecycle().value,
                                                onToggleCompactView = { viewModel.toggleCompactView() },
                                                sortOrder = sortOrder,
                                                onSortOrderChange = { viewModel.setSortOrder(it) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Ensure data loading starts
        viewModel.loadWorkoutsIfNeeded()
    }

    companion object {
        const val ARG_PRIMARY_TITLE = "ARG_PRIMARY_TITLE"
        const val ARG_SECONDARY_TITLE = "ARG_SECONDARY_TITLE"
        const val ARG_BSPORT_TYPE = "ARG_BSPORT_TYPE"
        const val ARG_SPORT_ID = "ARG_SPORT_ID"
        const val ARG_EQUIP_ID = "ARG_EQUIP_ID"
        const val ARG_START_S = "ARG_START_S"
        const val ARG_END_S = "ARG_END_S"
        const val TAG = "WorkoutSummariesListFragment"
        val DEBUG = TrainingApplication.getDebug(true)

        // Keep the exact same newInstance signature for compatibility
        fun newInstance(
            primaryTitle: String,
            secondaryTitle: String,
            bSportType: BSportType? = null,
            sportTypeId: Long? = null,
            equipmentId: Long? = null,
            startS: Long? = null,
            endS: Long? = null
        ) = WorkoutSummariesListFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_PRIMARY_TITLE, primaryTitle)
                putString(ARG_SECONDARY_TITLE, secondaryTitle)
                putSerializable(ARG_BSPORT_TYPE, bSportType)
                sportTypeId?.let { putLong(ARG_SPORT_ID, it) }
                equipmentId?.let { putLong(ARG_EQUIP_ID, it) }
                startS?.let { putLong(ARG_START_S, it) }
                endS?.let { putLong(ARG_END_S, it) }
            }
        }
    }
}