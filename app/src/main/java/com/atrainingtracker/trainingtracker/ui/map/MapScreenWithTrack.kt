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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Badge
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.ui.knownlocations.EditKnownLocationDialog
import com.atrainingtracker.trainingtracker.ui.knownlocations.ElevationSourceBadge
import com.atrainingtracker.trainingtracker.ui.knownlocations.KnownLocationsUnitConversions
import com.atrainingtracker.trainingtracker.ui.routes.RouteOnMapScreen
import com.atrainingtracker.trainingtracker.ui.segments.SegmentOnMapScreen
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Top-level Composable for Map with current track, live segments, routes, and favorite locations (REQ-UI-159, REQ-UI-180).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreenWithTrack(
    viewModel: MapFragmentWithTrackViewModel = viewModel(),
    targetLocationId: Long? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val liveSegments by viewModel.liveSegments.collectAsStateWithLifecycle()
    val allRoutes by viewModel.allRoutes.collectAsStateWithLifecycle()

    // The ID of the segment/route/location currently being "peeked"
    var selectedSegmentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedRouteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedLocationId by rememberSaveable { mutableStateOf<Long?>(targetLocationId) }
    var editingLocation by remember { mutableStateOf<KnownLocationItem?>(null) }

    LaunchedEffect(targetLocationId) {
        if (targetLocationId != null && targetLocationId > 0) {
            selectedSegmentId = null
            selectedRouteId = null
            selectedLocationId = targetLocationId
        }
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(skipHiddenState = false)
    )

    // Effect: When a user clicks a new segment/route/location, ensure the sheet is at least "Partially Expanded" (Peeked)
    LaunchedEffect(selectedSegmentId, selectedRouteId, selectedLocationId) {
        if (selectedSegmentId != null || selectedRouteId != null || selectedLocationId != null) {
            scaffoldState.bottomSheetState.partialExpand()
        } else {
            scaffoldState.bottomSheetState.hide()
        }
    }

    val initialBounds = remember(targetLocationId, uiState.knownLocations) {
        if (targetLocationId != null && targetLocationId > 0) {
            val loc = uiState.knownLocations.find { it.id == targetLocationId }
            if (loc != null) {
                val delta = 0.005 // ~500m window
                LatLngBounds(
                    LatLng(loc.latLng.latitude - delta, loc.latLng.longitude - delta),
                    LatLng(loc.latLng.latitude + delta, loc.latLng.longitude + delta)
                )
            } else null
        } else null
    }

    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val maxSheetHeight = maxHeight - statusBarHeight

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = when {
                selectedSegmentId != null -> 185.dp + navBarHeight
                selectedRouteId != null -> 100.dp + navBarHeight
                selectedLocationId != null -> 140.dp + navBarHeight
                else -> 0.dp
            },
            sheetDragHandle = null,
            sheetContent = {
                if (selectedSegmentId != null || selectedRouteId != null || selectedLocationId != null) {
                    Box(modifier = Modifier.fillMaxWidth().height(maxSheetHeight)) {
                        when {
                            selectedLocationId != null -> {
                                val selectedLocation =
                                    uiState.knownLocations.find { it.id == selectedLocationId }

                                if (selectedLocation != null) {
                                    val isMetric = remember { TrainingApplication.getUnit() == MyUnits.METRIC }
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(16.dp),
                                        tonalElevation = 3.dp
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.my_locations),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = selectedLocation.name,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                Badge(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                ) {
                                                    Text(
                                                        text = pluralStringResource(R.plurals.known_locations_starts, selectedLocation.hitCount, selectedLocation.hitCount),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_ascent),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = KnownLocationsUnitConversions.formatAltitude(selectedLocation.altitude, isMetric),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    ElevationSourceBadge(source = selectedLocation.source)
                                                }

                                                OutlinedButton(
                                                    onClick = { editingLocation = selectedLocation },
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = stringResource(R.string.Edit),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = stringResource(R.string.Edit),
                                                        style = MaterialTheme.typography.labelMedium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
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
                zoomFocus = if (initialBounds != null) MapZoomFocus.EXPLICIT_BOUNDS else MapZoomFocus.LOCAL_SEGMENTS,
                initialBounds = initialBounds,
                bSportType = uiState.bSportType,
                currentLocationFlow = MutableStateFlow(currentLocation),
                onMapClick = {
                    selectedSegmentId = null
                    selectedRouteId = null
                    selectedLocationId = null
                },
                modifier = Modifier.fillMaxSize()
            ) {
                knownLocations(uiState.knownLocations, onLocationClick = { id ->
                    selectedSegmentId = null
                    selectedRouteId = null
                    selectedLocationId = id
                })
                segments(uiState.segments, onSegmentClick = { id ->
                    selectedRouteId = null
                    selectedLocationId = null
                    selectedSegmentId = id
                })
                routes(uiState.routes, onRouteClick = { id ->
                    selectedSegmentId = null
                    selectedLocationId = null
                    selectedRouteId = id
                })
                markers(uiState.markers)
                liveTrack(uiState.currentTrack)
            }
        }
    }

    // Modal Edit Bottom Sheet
    editingLocation?.let { loc ->
        val isMetric = remember { TrainingApplication.getUnit() == MyUnits.METRIC }
        EditKnownLocationDialog(
            location = loc,
            isMetric = isMetric,
            showMap = false,
            onConfirm = { id, name, altitude, radius, source ->
                viewModel.updateKnownLocation(id, name, altitude, radius, source)
                editingLocation = null
            },
            onDismiss = { editingLocation = null }
        )
    }

    // Handle system back button to close the peek
    BackHandler(enabled = selectedSegmentId != null || selectedRouteId != null || selectedLocationId != null) {
        selectedSegmentId = null
        selectedRouteId = null
        selectedLocationId = null
    }
}
