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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.ui.components.DeleteConfirmationDialog
import com.atrainingtracker.trainingtracker.ui.components.MappableListItem
import com.atrainingtracker.trainingtracker.ui.map.DarkMapAntiFlashOverlay
import com.atrainingtracker.trainingtracker.ui.map.DarkMapStyle
import com.atrainingtracker.trainingtracker.ui.map.createHeartPinMarker
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.LayoutConstants
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
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
                        overflow = TextOverflow.Ellipsis
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
            onDelete = { locationPendingDeletion = it },
            modifier = Modifier.fillMaxSize()
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
                items = uiState.locations,
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
                    onDelete = { onDelete(item) }
                )
            }
        }
    }
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
            .size(100.dp)
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
                        color = Color(0x332196F3),
                        radius = circleRadius,
                        center = center
                    )
                    drawCircle(
                        color = Color(0x882196F3),
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
                pinColor = Color(0xFF2196F3),
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
                .background(if (isDark) Color(0xFF121212) else Color.White)
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
                    fillColor = Color(0x332196F3),
                    strokeColor = Color(0x882196F3),
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Prominent Altitude Metric
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_ascent),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = KnownLocationsUnitConversions.formatAltitude(item.altitude, isMetric),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Dedicated Badges Row (Starts and Routes, REQ-UI-195)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Dedicated Home Base Badge (REQ-MAP-034)
                            if (item.isHome) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                    modifier = Modifier.testTag("location_home_badge_${item.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Home,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = stringResource(R.string.known_locations_home_badge),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            // Number of Starts (Interactive Drill-Down Touch Target)
                            Surface(
                                onClick = onShowWorkouts,
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                modifier = Modifier.testTag("location_starts_badge_${item.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = stringResource(R.string.known_locations_view_workouts),
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = pluralStringResource(R.plurals.known_locations_starts, startsCount, startsCount),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)
                                    )
                                }
                            }

                            // Number of Routes (Interactive Drill-Down Touch Target per REQ-UI-188, REQ-UI-195, REQ-UI-207)
                            if (linkedClusters.isNotEmpty()) {
                                Surface(
                                    onClick = onShowRoutes,
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                    modifier = Modifier.testTag("location_routes_badge_${item.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_favorite_route),
                                            contentDescription = stringResource(R.string.known_locations_view_routes),
                                            modifier = Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = pluralStringResource(R.plurals.known_locations_routes, linkedClusters.size, linkedClusters.size),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            modifier = Modifier.size(11.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Map Preview Thumbnail (right side, REQ-UI-217, REQ-UI-244)
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
            Color(0xFFE8F5E9),
            Color(0xFF2E7D32)
        )
        ElevationSource.AUTO_LEARNED -> Triple(
            R.string.source_auto_learned,
            Color(0xFFFFF8E1),
            Color(0xFFF57F17)
        )
        ElevationSource.GPS_FALLBACK -> Triple(
            R.string.source_gps_fallback,
            Color(0xFFFFE0B2),
            Color(0xFFE65100)
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
fun PreviewKnownLocationCardLight() {
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
fun PreviewKnownLocationCardDark() {
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
fun PreviewKnownLocationCardContextMenu() {
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
