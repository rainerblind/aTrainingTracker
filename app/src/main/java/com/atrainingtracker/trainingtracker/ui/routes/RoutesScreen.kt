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

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.activities.GpxImportActivity
import com.atrainingtracker.trainingtracker.ui.map.toMapRoute
import com.atrainingtracker.trainingtracker.ui.map.toMapSegment
import kotlinx.coroutines.launch

/**
 * Top-level Composable for Routes tabbed list, detail map view, and editing (REQ-UI-159).
 */
@Composable
fun RoutesScreen(
    viewModel: RoutesViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val gpxPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val intent = Intent(context, GpxImportActivity::class.java).apply {
                data = it
            }
            context.startActivity(intent)
        }
    }

    val routes by viewModel.routes.collectAsStateWithLifecycle()
    val allSegments by viewModel.segments.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val filterCriteria by viewModel.filterCriteria.collectAsStateWithLifecycle()
    val isLocationAvailable by viewModel.isLocationAvailable.collectAsStateWithLifecycle()
    val isSyncingStrava by viewModel.isSyncingStrava.collectAsStateWithLifecycle()
    val syncStravaStatus by viewModel.syncStravaStatus.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val successMsg = stringResource(R.string.strava_sync_success)
    val errorMsg = stringResource(R.string.strava_sync_failed)
    val routeSavedAsLocalMsg = stringResource(R.string.route_saved_as_local)

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

    var selectedRouteIdForDetails by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedRouteIdForEdit by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier,
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
                val selectedRoute = routes.find { it.summary.id == selectedRouteIdForDetails }

                if (selectedRoute != null) {
                    val backgroundPaths = remember(selectedRoute, allSegments) {
                        allSegments
                            .filter { it.summary.bSportType == selectedRoute.summary.bSportType }
                            .map { it.toMapSegment(showStartAndFinishText = false) }
                    }

                    RouteOnMapScreen(
                        route = selectedRoute.toMapRoute(),
                        routeSummary = selectedRoute.summary,
                        backgroundPaths = backgroundPaths,
                        modifier = Modifier,
                        onToggleSelection = { isSelected ->
                            viewModel.toggleRouteSelection(
                                selectedRoute.summary.id,
                                isSelected
                            )
                        }
                    )

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

                    BackHandler {
                        selectedRouteIdForEdit = null
                    }
                }
            } else {
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
                    onDuplicateAsLocal = { id ->
                        viewModel.duplicateRouteAsLocal(id) { success ->
                            if (success) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(routeSavedAsLocalMsg)
                                }
                            }
                        }
                    },
                    onImportClick = { gpxPickerLauncher.launch("*/*") },
                    onSyncStravaClick = { viewModel.syncStravaRoutes() },
                    isSyncing = isSyncingStrava,
                    sortOrder = sortOrder,
                    onSortOrderChange = { viewModel.setSortOrder(it) },
                    scrollToTop = viewModel.shouldScrollToTop(sortOrder),
                    isLocationAvailable = isLocationAvailable,
                    filterCriteria = filterCriteria,
                    onApplyFilterCriteria = { viewModel.setFilterCriteria(it) },
                    onClearAllFilters = { viewModel.clearFilterCriteria() },
                    onUpdateFilterCriteria = { viewModel.updateFilterCriteria(it) }
                )
            }
        }
    }
}
