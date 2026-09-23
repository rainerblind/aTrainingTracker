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

package com.atrainingtracker.trainingtracker.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.trainingtracker.ui.routes.RouteOnMapScreen
import com.atrainingtracker.trainingtracker.ui.segments.SegmentOnMapScreen
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Top-level Composable for Map with current track, live segments, and routes (REQ-UI-159).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreenWithTrack(
    viewModel: MapFragmentWithTrackViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val liveSegments by viewModel.liveSegments.collectAsStateWithLifecycle()
    val allRoutes by viewModel.allRoutes.collectAsStateWithLifecycle()

    // The ID of the segment/route currently being "peeked"
    var selectedSegmentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedRouteId by rememberSaveable { mutableStateOf<Long?>(null) }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(skipHiddenState = false)
    )

    // Effect: When a user clicks a new segment, ensure the sheet is at least "Partially Expanded" (Peeked)
    LaunchedEffect(selectedSegmentId, selectedRouteId) {
        if (selectedSegmentId != null || selectedRouteId != null) {
            scaffoldState.bottomSheetState.partialExpand()
        } else {
            scaffoldState.bottomSheetState.hide()
        }
    }

    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val maxSheetHeight = maxHeight - statusBarHeight

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = if (selectedSegmentId != null) 185.dp + navBarHeight
            else if (selectedRouteId != null) 100.dp + navBarHeight
            else 0.dp,
            sheetDragHandle = null,
            sheetContent = {
                if (selectedSegmentId != null || selectedRouteId != null) {
                    Box(modifier = Modifier.fillMaxWidth().height(maxSheetHeight)) {
                        when {
                            selectedSegmentId != null -> {
                                val selectedSegment =
                                    uiState.segments.find { it.stravaId == selectedSegmentId }

                                SegmentOnMapScreen(
                                    segmentSummary = liveSegments.find { it.summary.stravaId == selectedSegmentId }?.summary,
                                    segment = selectedSegment,
                                    modifier = Modifier.fillMaxSize(),
                                    useStatusBarsPadding = false
                                )
                            }
                            selectedRouteId != null -> {
                                val selectedRoute =
                                    uiState.routes.find { it.id == selectedRouteId }

                                RouteOnMapScreen(
                                    route = selectedRoute,
                                    routeSummary = allRoutes.find { it.summary.id == selectedRouteId }?.summary,
                                    onToggleSelection = { viewModel.onToggleRoute(
                                        id = selectedRouteId!!,
                                        selected = it
                                    ) },
                                    modifier = Modifier.fillMaxSize(),
                                    useStatusBarsPadding = false
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(1.dp))
                }
            }
        ) { innerPadding ->
            ATrainingTrackerMap(
                zoomFocus = MapZoomFocus.LOCAL_SEGMENTS,
                bSportType = uiState.bSportType,
                currentLocationFlow = MutableStateFlow(currentLocation),
                modifier = Modifier.fillMaxSize()
            ) {
                segments(uiState.segments, onSegmentClick = { id ->
                    selectedRouteId = null
                    selectedSegmentId = id
                })
                routes(uiState.routes, onRouteClick = { id ->
                    selectedSegmentId = null
                    selectedRouteId = id
                })
                markers(uiState.markers)
                liveTrack(uiState.currentTrack)
            }
        }
    }

    // Handle system back button to close the peek
    BackHandler(enabled = selectedSegmentId != null || selectedRouteId != null) {
        selectedSegmentId = null
        selectedRouteId = null
    }
}
