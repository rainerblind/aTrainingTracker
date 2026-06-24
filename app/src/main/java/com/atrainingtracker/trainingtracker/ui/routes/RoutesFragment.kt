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

package com.atrainingtracker.trainingtracker.ui.routes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.activities.GpxImportActivity
import com.atrainingtracker.trainingtracker.ui.map.MapSegment
import com.atrainingtracker.trainingtracker.ui.map.toMapRoute
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * Fragment that hosts the tabbed Route list.
 * Integrates with the existing Navigation Drawer via MainActivityWithNavigation.
 */
class RoutesFragment : Fragment() {

    private lateinit var viewModel: RoutesViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Initialize the ViewModel
        viewModel = ViewModelProvider(this).get(RoutesViewModel::class.java)

        // 1. Register the picker launcher
        val gpxPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val intent = android.content.Intent(requireContext(), GpxImportActivity::class.java).apply {
                    data = it
                }
                startActivity(intent)
            }
        }

        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {

                    val routes by viewModel.routes.collectAsStateWithLifecycle()
                    val allSegments by viewModel.segments.collectAsStateWithLifecycle()
                    val sortOrder by viewModel.sortOrder.collectAsState()
                    val isLocationAvailable by viewModel.isLocationAvailable.collectAsStateWithLifecycle()
                    val isSyncingStrava by viewModel.isSyncingStrava.collectAsStateWithLifecycle()
                    val syncStravaStatus by viewModel.syncStravaStatus.collectAsStateWithLifecycle()

                    // --- SNACKBAR FEEDBACK ---
                    val snackbarHostState = remember { SnackbarHostState() }
                    val successMsg = stringResource(R.string.strava_sync_success)
                    val errorMsg = stringResource(R.string.strava_sync_failed)

                    LaunchedEffect(syncStravaStatus) {
                        syncStravaStatus?.let { success ->
                            val message = if (success) successMsg else errorMsg
                            snackbarHostState.showSnackbar(message)
                            viewModel.resetSyncStravaStatus()
                        }
                    }

                    val pagerState = rememberPagerState(pageCount = { 4 })
                    val allSportsListState = rememberLazyListState()
                    val bikeListState = rememberLazyListState()
                    val runListState = rememberLazyListState()
                    val otherListState = rememberLazyListState()

                    // 1. Manage local navigation state
                    var selectedRouteIdForDetails by rememberSaveable { mutableStateOf<Long?>(null) }
                    var selectedRouteIdForEdit by rememberSaveable { mutableStateOf<Long?>(null) }


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
                            if (selectedRouteIdForDetails != null) {
                                // SHOW DETAIL
                                // Deriving the specific route from the list we already have
                                val selectedRoute = routes.find { it.summary.id == selectedRouteIdForDetails }

                                if (selectedRoute != null) {

                                    // Create Map data on the fly
                                    val sportSegments = remember(selectedRoute, allSegments) {
                                        allSegments
                                            .filter { it.summary.bSportType == selectedRoute.summary.bSportType }
                                            .map { segment ->
                                                MapSegment(
                                                    stravaId = segment.summary.stravaId,
                                                    name = segment.summary.name,
                                                    path = segment.path,
                                                    bSportType = segment.summary.bSportType,
                                                    showStartAndFinishText = false
                                                )
                                            }
                                    }

                                    RouteOnMapScreen(
                                        route = selectedRoute.toMapRoute(),
                                        routeSummary = selectedRoute.summary,
                                        segments = sportSegments,
                                        modifier = Modifier.statusBarsPadding(),
                                        onToggleSelection = { isSelected ->
                                            viewModel.toggleRouteSelection(
                                                selectedRoute.summary.id,
                                                isSelected
                                            )
                                        }
                                    )

                                    // Handle Back Press to return to list
                                    BackHandler {
                                        selectedRouteIdForDetails = null
                                    }
                                }
                            } else if (selectedRouteIdForEdit != null) {
                                val routeToEdit = routes.find { it.summary.id == selectedRouteIdForEdit }
                                if (routeToEdit != null) {
                                    EditRouteScreen(
                                        routeSummary = routeToEdit.summary,
                                        onSave = {
                                            viewModel.updateRoute(it)
                                            selectedRouteIdForEdit = null
                                        },
                                        onCancel = {
                                            selectedRouteIdForEdit = null
                                        }
                                    )

                                    // Handle Back Press to return to list
                                    BackHandler {
                                        selectedRouteIdForEdit = null
                                    }
                                }
                            } else {
                                // SHOW LIST
                                RouteTabbedScreen(
                                    routesWithPath = routes,
                                    pagerState = pagerState,
                                    allSportsListState = allSportsListState,
                                    bikeListState = bikeListState,
                                    runListState = runListState,
                                    otherListState = otherListState,
                                    onMapClick = { id ->
                                        selectedRouteIdForDetails = id
                                    },
                                    onHeaderClick = { id ->
                                        selectedRouteIdForEdit = id
                                    },
                                    onToggleSelection = { id, isSelected ->
                                        viewModel.toggleRouteSelection(id, isSelected)
                                    },
                                    onDeleteConfirmed = { id ->
                                        viewModel.deleteRoute(id)
                                    },
                                    onImportClick = { gpxPickerLauncher.launch("*/*") },
                                    onSyncStravaClick = { viewModel.syncStravaRoutes() },
                                    isSyncing = isSyncingStrava,
                                    sortOrder = sortOrder,
                                    onSortOrderChange = { viewModel.setSortOrder(it) },
                                    scrollToTop = viewModel.shouldScrollToTop(sortOrder),
                                    isLocationAvailable = isLocationAvailable
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure data is fresh when returning to this screen
        viewModel.refresh()
    }

    companion object {
        const val TAG = "RoutesFragment"

        @JvmStatic
        fun newInstance(): RoutesFragment {
            return RoutesFragment()
        }
    }
}