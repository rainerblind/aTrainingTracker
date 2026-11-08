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

package com.atrainingtracker.trainingtracker.ui.knownlocations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.trainingtracker.database.KnownLocationsGeometryMath
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.ui.common.filters.FilterActionButton
import com.atrainingtracker.trainingtracker.ui.components.DeleteConfirmationDialog
import com.atrainingtracker.trainingtracker.ui.components.MappableListItem
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.map.DarkMapAntiFlashOverlay
import com.atrainingtracker.trainingtracker.ui.map.DarkMapStyle
import com.atrainingtracker.trainingtracker.ui.map.createHeartPinMarker
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.LayoutConstants
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Streamlined single-perspective Jetpack Compose screen for managing known geographical workout start locations.
 *
 * Traceability: REQ-UI-165, REQ-UI-180, TST-UI-132.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnownLocationsScreen(
    viewModel: KnownLocationsViewModel,
    onMenuClick: () -> Unit = { },
    onShowOnMap: (Long) -> Unit = { },
    onShowWorkouts: (KnownLocationItem) -> Unit = { },
    onSelectCluster: (Long) -> Unit = { },
    onShowRoutes: (KnownLocationItem) -> Unit = { },
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var locationPendingDeletion by remember { mutableStateOf<KnownLocationItem?>(null) }
    var locationToMerge by remember { mutableStateOf<KnownLocationItem?>(null) }
    var pendingMergePair by remember { mutableStateOf<Pair<KnownLocationItem, KnownLocationItem>?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // --- HEADER SURFACE (Standard Dark Blue primaryContainer) ---
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(LayoutConstants.HEADER_TITLE_ROW_HEIGHT)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.known_locations_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterActionButton(
                            onClick = { showFilterSheet = true },
                            isFilterActive = uiState.filterCriteria.isNotEmpty,
                            activeFilterCount = uiState.filterCriteria.activeFilterCount,
                            modifier = Modifier.testTag("known_locations_filter_button")
                        )

                        var showSortMenu by remember { mutableStateOf(false) }

                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.testTag("known_locations_sort_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = stringResource(R.string.sort),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                        DropdownMenu(
                            containerColor = MaterialTheme.colorScheme.surface,
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            val isLocAvailable = uiState.isLocationAvailable
                            KnownLocationSortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(order.labelResId),
                                            color = if (order == KnownLocationSortOrder.DISTANCE_TO_USER && !isLocAvailable) {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    },
                                    onClick = {
                                        viewModel.setSortOrder(order)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (uiState.sortOrder == order) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = if (order == KnownLocationSortOrder.DISTANCE_TO_USER && !isLocAvailable) {
                                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        }
                                    },
                                    enabled = !(order == KnownLocationSortOrder.DISTANCE_TO_USER && !isLocAvailable),
                                    modifier = Modifier.testTag("known_locations_sort_${order.name.lowercase()}")
                                )
                            }
                        }
                    }
                    }
                }
            }
        }

        // Active Filter Chips Strip (REQ-UI-466.3)
        if (uiState.filterCriteria.isNotEmpty) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                ActiveKnownLocationsFilterChipsRow(
                    criteria = uiState.filterCriteria,
                    onRemoveQuery = { viewModel.setSearchQuery("") },
                    onRemoveMinStarts = { viewModel.setMinStarts(null) },
                    onClearAll = { viewModel.clearFilters() }
                )
            }
        }

        // Streamlined Single-Perspective List Content
        KnownLocationsListContent(
            uiState = uiState,
            onEdit = { viewModel.openEditDialog(it, showMap = true) },
            onShowOnMap = { item -> onShowOnMap(item.id) },
            onShowWorkouts = onShowWorkouts,
            onSelectCluster = onSelectCluster,
            onShowRoutes = onShowRoutes,
            onSetHome = { item -> viewModel.setHomeLocation(item.id) },
            onClearHome = { viewModel.clearHomeLocation() },
            onMerge = { locationToMerge = it },
            onDelete = { locationPendingDeletion = it },
            modifier = Modifier.fillMaxSize()
        )
    }

    // Modal Filter Bottom Sheet (REQ-UI-466.4)
    if (showFilterSheet) {
        KnownLocationsFilterBottomSheet(
            criteria = uiState.filterCriteria,
            onApplyCriteria = { viewModel.setFilterCriteria(it) },
            onClearAll = { viewModel.clearFilters() },
            onDismissRequest = { showFilterSheet = false }
        )
    }

    // Modal Edit Bottom Sheet
    uiState.selectedLocationForEdit?.let { itemToEdit ->
        EditKnownLocationDialog(
            location = itemToEdit,
            isMetric = uiState.isMetric,
            showMap = uiState.showMapInEditDialog,
            onConfirm = { id, name, altitude, radius, source, isHome ->
                viewModel.updateLocation(id, name, altitude, radius, source, isHome)
            },
            onDismiss = { viewModel.dismissEditDialog() }
        )
    }

    // Candidate Selection Bottom Sheet (REQ-SET-095.2)
    locationToMerge?.let { sourceItem ->
        val candidates = remember(sourceItem, uiState.locations) {
            viewModel.getCandidateLocationsForMerge(sourceItem)
        }
        KnownLocationMergeSelectionDialog(
            source = sourceItem,
            candidates = candidates,
            onSelectTarget = { targetItem ->
                pendingMergePair = Pair(sourceItem, targetItem)
            },
            onDismiss = { locationToMerge = null }
        )
    }

    // Merge Confirmation Dialog (REQ-SET-095.3)
    pendingMergePair?.let { (source, target) ->
        KnownLocationMergeConfirmationDialog(
            source = source,
            target = target,
            onConfirm = {
                viewModel.mergeLocations(source, target)
                pendingMergePair = null
                locationToMerge = null
            },
            onDismiss = {
                pendingMergePair = null
            }
        )
    }

    // Delete Confirmation Dialog
    locationPendingDeletion?.let { itemToDelete ->
        DeleteConfirmationDialog(
            title = stringResource(R.string.delete),
            message = stringResource(R.string.really_delete_format, itemToDelete.name),
            onConfirm = {
                viewModel.deleteLocation(itemToDelete.id)
                locationPendingDeletion = null
            },
            onDismiss = { locationPendingDeletion = null }
        )
    }
}

/**
 * List Perspective: High-density location cards or empty state.
 */
@Composable
private fun KnownLocationsListContent(
    uiState: KnownLocationsUiState,
    onEdit: (KnownLocationItem) -> Unit,
    onShowOnMap: (KnownLocationItem) -> Unit,
    onShowWorkouts: (KnownLocationItem) -> Unit = { },
    onSelectCluster: (Long) -> Unit = { },
    onShowRoutes: (KnownLocationItem) -> Unit = { },
    onSetHome: (KnownLocationItem) -> Unit = { },
    onClearHome: () -> Unit = { },
    onMerge: (KnownLocationItem) -> Unit = { },
    onDelete: (KnownLocationItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val bottomNavPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    if (uiState.locations.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp)
                .padding(bottom = bottomNavPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.known_locations_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.known_locations_empty_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else if (uiState.filteredLocations.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp)
                .padding(bottom = bottomNavPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.filter_no_matching_locations),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("known_locations_list"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = bottomNavPadding + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = uiState.filteredLocations,
                key = { it.id }
            ) { item ->
                KnownLocationCard(
                    item = item,
                    isMetric = uiState.isMetric,
                    linkedClusters = uiState.clustersByLocationId[item.id] ?: emptyList(),
                    startsCount = uiState.startsByLocationId[item.id] ?: item.hitCount,
                    onEdit = { onEdit(item) },
                    onShowOnMap = { onShowOnMap(item) },
                    onShowWorkouts = { onShowWorkouts(item) },
                    onSelectCluster = onSelectCluster,
                    onShowRoutes = { onShowRoutes(item) },
                    onSetHome = { onSetHome(item) },
                    onClearHome = onClearHome,
                    onMerge = { onMerge(item) },
                    onDelete = { onDelete(item) }
                )
            }
        }
    }
}

/**
 * Bottom sheet dialog to select a target location for merge (REQ-SET-095.2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnownLocationMergeSelectionDialog(
    source: KnownLocationItem,
    candidates: List<Pair<KnownLocationItem, Double>>,
    onSelectTarget: (KnownLocationItem) -> Unit,
    onDismiss: () -> Unit
) {
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.known_locations_merge_select_target_title),
        icon = Icons.AutoMirrored.Filled.CallMerge,
        actions = {
            AppDialogActions.CancelOnly(onCancel = onDismiss)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            if (candidates.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.known_locations_merge_no_candidates),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val distanceFormatter = remember { DistanceFormatter() }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .testTag("location_merge_candidates_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = candidates,
                        key = { it.first.id }
                    ) { (candidate, distanceMeters) ->
                        Surface(
                            onClick = {
                                onSelectTarget(candidate)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("location_merge_candidate_${candidate.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = candidate.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (candidate.isHome) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.Home,
                                                contentDescription = stringResource(R.string.known_locations_home_base),
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(
                                            R.string.known_locations_merge_distance_away,
                                            distanceFormatter.format_with_units(distanceMeters)
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Confirmation dialog for merging two known locations (REQ-SET-095.3).
 */
@Composable
fun KnownLocationMergeConfirmationDialog(
    source: KnownLocationItem,
    target: KnownLocationItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val geofence = remember(source, target) {
        KnownLocationsGeometryMath.calculateMergedGeofence(source, target)
    }
    val distanceFormatter = remember { DistanceFormatter() }
    val formattedRadius = distanceFormatter.format_with_units(geofence.radius.toDouble())

    val message = stringResource(
        R.string.known_locations_merge_dialog_message,
        source.name,
        target.name,
        formattedRadius
    )
    val fullMessage = if (geofence.isPinned) {
        message + "\n\n" + stringResource(R.string.known_locations_merge_pinned_notice, target.name)
    } else {
        message
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Text(stringResource(R.string.known_locations_merge_dialog_title))
        },
        text = {
            Text(fullMessage)
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                modifier = Modifier.testTag("location_merge_confirm_button")
            ) {
                Text(stringResource(R.string.known_locations_merge_action))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("location_merge_cancel_button")
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Compact map preview thumbnail for [KnownLocationCard] (REQ-UI-217, REQ-UI-244).
 * Displays a 100dp square map in lite mode, centered at the location coordinates with a heart pin marker
 * and geofence circle, styled for dark/light themes with an anti-flash overlay and offline preview fallback.
 */
@Composable
private fun KnownLocationThumbnailMap(
    item: KnownLocationItem,
    onShowOnMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(width = 116.dp, height = 80.dp)
            .testTag("location_map_preview_${item.id}"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        if (LocalInspectionMode.current) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(onClick = onShowOnMap),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val circleRadius = size.minDimension * 0.35f
                    drawCircle(
                        color = TTColor.GeofenceFill,
                        radius = circleRadius,
                        center = center
                    )
                    drawCircle(
                        color = TTColor.GeofenceStroke,
                        radius = circleRadius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            return@Surface
        }

        val context = LocalContext.current
        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val mapProperties = remember(isDark, context) {
            DarkMapStyle.resolveMapProperties(isDark, context)
        }
        val heartMarkerIcon = remember(context) {
            createHeartPinMarker(
                context = context,
                pinColor = TTColor.GeofencePin,
                heartColor = Color.White
            )
        }
        val targetZoom = remember(item.radius, item.latLng.latitude) {
            KnownLocationZoomMath.calculateThumbnailZoom(item.radius, item.latLng.latitude)
        }
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(item.latLng, targetZoom)
        }
        var isMapLoaded by remember { mutableStateOf(false) }

        LaunchedEffect(item.latLng, targetZoom, isMapLoaded) {
            if (isMapLoaded) {
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(item.latLng, targetZoom))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDark) TTColor.DarkMapCanvas else Color.White)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                googleMapOptionsFactory = {
                    GoogleMapOptions().liteMode(true)
                },
                properties = mapProperties,
                onMapLoaded = { isMapLoaded = true },
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    scrollGesturesEnabled = false,
                    zoomGesturesEnabled = false,
                    tiltGesturesEnabled = false,
                    rotationGesturesEnabled = false,
                    myLocationButtonEnabled = false,
                    compassEnabled = false,
                    mapToolbarEnabled = false
                ),
                onMapClick = { onShowOnMap() }
            ) {
                Marker(
                    state = remember(item.latLng) { MarkerState(position = item.latLng) },
                    icon = heartMarkerIcon,
                    onClick = {
                        onShowOnMap()
                        true
                    }
                )
                Circle(
                    center = item.latLng,
                    radius = item.radius.toDouble(),
                    fillColor = TTColor.GeofenceFill,
                    strokeColor = TTColor.GeofenceStroke,
                    strokeWidth = 2f
                )
            }
            DarkMapAntiFlashOverlay(isMapLoaded = isMapLoaded, isDark = isDark)
        }
    }
}

/**
 * Modern location card with icon badge, inline metrics, and universal delete-only long-press context menu.
 */
@Composable
private fun KnownLocationCard(
    item: KnownLocationItem,
    isMetric: Boolean,
    linkedClusters: List<WorkoutCluster> = emptyList(),
    startsCount: Int = item.hitCount,
    onEdit: () -> Unit,
    onShowOnMap: () -> Unit = {},
    onShowWorkouts: () -> Unit = {},
    onSelectCluster: (Long) -> Unit = {},
    onShowRoutes: () -> Unit = {},
    onSetHome: () -> Unit = {},
    onClearHome: () -> Unit = {},
    onMerge: () -> Unit = {},
    onDelete: () -> Unit,
    initialShowContextMenu: Boolean = false
) {
    var showContextMenu by remember { mutableStateOf(initialShowContextMenu) }

    Box {
        MappableListItem(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("location_card_${item.id}"),
            onClick = onEdit,
            onLongClick = { showContextMenu = true }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isHome) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = stringResource(R.string.known_locations_home_base),
                            modifier = Modifier
                                .size(20.dp)
                                .testTag("location_home_icon_${item.id}"),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // --- 2. BOTTOM AREA: Details on left, Map on right (REQ-UI-469) ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // LEFT SIDE: Metadata & Metrics
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Altitude Metric (REQ-UI-465.2)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terrain,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = KnownLocationsUnitConversions.formatAltitude(item.altitude, isMetric),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Routes Cross-Link (Unboxed, REQ-UI-188, REQ-UI-469.3)
                        if (linkedClusters.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .clickable(onClick = onShowRoutes)
                                    .testTag("location_routes_badge_${item.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_favorite_route),
                                    contentDescription = stringResource(R.string.known_locations_view_routes),
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = pluralStringResource(R.plurals.known_locations_routes, linkedClusters.size, linkedClusters.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Number of Starts (Unboxed Bottom-Anchored Primary Count, REQ-UI-195, REQ-UI-469.2)
                        Row(
                            modifier = Modifier
                                .clickable(onClick = onShowWorkouts)
                                .testTag("location_starts_badge_${item.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                                contentDescription = stringResource(R.string.known_locations_view_workouts),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = pluralStringResource(R.plurals.known_locations_starts, startsCount, startsCount),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Map Preview Thumbnail (right side, REQ-UI-217, REQ-UI-244, REQ-UI-465.3)
                    KnownLocationThumbnailMap(
                        item = item,
                        onShowOnMap = onShowOnMap
                    )
                }
            }
        }

        // Universal Long-Press Context Menu (Delete-only, Top-Left aligned per REQ-UI-061)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 8.dp)
        ) {
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false }
            ) {
                if (item.isHome) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.known_locations_remove_home)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showContextMenu = false
                            onClearHome()
                        },
                        modifier = Modifier.testTag("location_clear_home_action_${item.id}")
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.known_locations_set_home)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showContextMenu = false
                            onSetHome()
                        },
                        modifier = Modifier.testTag("location_set_home_action_${item.id}")
                    )
                }

                DropdownMenuItem(
                    text = { Text(stringResource(R.string.known_locations_menu_merge)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CallMerge,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        onMerge()
                    },
                    modifier = Modifier.testTag("location_merge_action_${item.id}")
                )

                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("location_delete_action_${item.id}")
                )
            }
        }
    }
}

/**
 * Color-coded semantic tag representing reference altitude provenance.
 */
@Composable
fun ElevationSourceBadge(source: ElevationSource) {
    val (labelRes, containerColor, contentColor) = when (source) {
        ElevationSource.INTERNET_DEM -> Triple(
            R.string.source_internet_dem,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        ElevationSource.MANUAL_USER -> Triple(
            R.string.source_manual_user,
            TTColor.LocationBadgeHomeContainer,
            TTColor.LocationBadgeHomeContent
        )
        ElevationSource.AUTO_LEARNED -> Triple(
            R.string.source_auto_learned,
            TTColor.LocationBadgeWorkContainer,
            TTColor.LocationBadgeWorkContent
        )
        ElevationSource.GPS_FALLBACK -> Triple(
            R.string.source_gps_fallback,
            TTColor.LocationBadgeOtherContainer,
            TTColor.LocationBadgeOtherContent
        )
        ElevationSource.LEGACY_RAW -> Triple(
            R.string.source_legacy_raw,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// =============================================================================
// COMPOSE PREVIEWS
// =============================================================================

private val previewMockLocation = KnownLocationItem(
    id = 1L,
    name = "Zuhause",
    altitude = 520.0,
    radius = 50,
    latLng = LatLng(48.137154, 11.576124),
    hitCount = 42,
    isLocked = true,
    source = ElevationSource.MANUAL_USER
)

private val previewMockCluster = WorkoutCluster(
    id = 101L,
    name = "Hausrunde",
    probableSportId = 1L,
    startLat = 48.137,
    startLng = 11.576,
    endLat = 48.137,
    endLng = 11.576,
    maxDispLat = 48.140,
    maxDispLng = 11.580,
    refDistance = 10200.0,
    hitCount = 5
)

@Preview(name = "List Item - Light", showBackground = true)
@Composable
private fun PreviewKnownLocationCardLight() {
    ATrainingTrackerTheme(darkTheme = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            KnownLocationCard(
                item = previewMockLocation,
                isMetric = true,
                linkedClusters = listOf(previewMockCluster),
                onEdit = {},
                onShowOnMap = {},
                onShowWorkouts = {},
                onDelete = {}
            )
        }
    }
}

@Preview(name = "List Item - Dark", showBackground = true)
@Composable
private fun PreviewKnownLocationCardDark() {
    ATrainingTrackerTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            KnownLocationCard(
                item = previewMockLocation,
                isMetric = true,
                linkedClusters = listOf(previewMockCluster),
                onEdit = {},
                onShowOnMap = {},
                onShowWorkouts = {},
                onDelete = {}
            )
        }
    }
}

@Preview(name = "List Item - Context Menu (Long Click)", showBackground = true)
@Composable
private fun PreviewKnownLocationCardContextMenu() {
    ATrainingTrackerTheme(darkTheme = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            KnownLocationCard(
                item = previewMockLocation,
                isMetric = true,
                onEdit = {},
                onShowOnMap = {},
                onShowWorkouts = {},
                onDelete = {},
                initialShowContextMenu = true
            )
        }
    }
}
