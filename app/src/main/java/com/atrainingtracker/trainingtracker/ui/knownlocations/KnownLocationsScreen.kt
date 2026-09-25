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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.ui.res.painterResource
import com.atrainingtracker.trainingtracker.ui.components.MappableListItem
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.map.createHeartPinMarker
import com.atrainingtracker.trainingtracker.ui.theme.LayoutConstants
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.tooling.preview.Preview
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.ui.components.DeleteConfirmationDialog
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Dual-perspective Jetpack Compose screen for managing known geographical workout start locations.
 *
 * Perspectives:
 * - List Tab: Filterable high-density cards displaying location name, altitude (m/ft),
 *   provenance source badge, hit count badge, geodetic coordinates, and actions.
 * - Map Tab: Interactive Google Map rendering markers and 200m circular geofence overlays
 *   with viewport-based marker culling for 60 FPS performance.
 *
 * Traceability: REQ-UI-165, REQ-DAT-007, REQ-DAT-014, TST-UI-117.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnownLocationsScreen(
    viewModel: KnownLocationsViewModel,
    onMenuClick: () -> Unit = { },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var locationPendingDeletion by remember { mutableStateOf<KnownLocationItem?>(null) }

    // Map camera state centered on fallback location (location with highest hitCount)
    val fallbackTarget = remember(uiState.locations) {
        viewModel.getFallbackMapLocation()?.latLng ?: LatLng(48.13715, 11.57612)
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(fallbackTarget, 14f)
    }

    var hasCenteredInitially by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.locations) {
        if (!hasCenteredInitially && uiState.locations.isNotEmpty()) {
            val primary = viewModel.getFallbackMapLocation()
            if (primary != null) {
                cameraPositionState.position = CameraPosition.fromLatLngZoom(primary.latLng, 14f)
                hasCenteredInitially = true
            }
        }
    }

    // Viewport bounds culling listener
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            try {
                val bounds = cameraPositionState.projection?.visibleRegion?.latLngBounds
                viewModel.onViewportBoundsChanged(bounds)
            } catch (_: Exception) {
                // Ignore projection query errors during early initialization
            }
        }
    }

    val pagerState = rememberPagerState(initialPage = uiState.selectedTab.ordinal) { 2 }

    LaunchedEffect(pagerState.currentPage) {
        val targetTab = if (pagerState.currentPage == 0) KnownLocationsTab.LIST else KnownLocationsTab.MAP
        if (uiState.selectedTab != targetTab) {
            viewModel.selectTab(targetTab)
        }
    }

    LaunchedEffect(uiState.selectedTab) {
        if (pagerState.currentPage != uiState.selectedTab.ordinal) {
            pagerState.animateScrollToPage(uiState.selectedTab.ordinal)
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // --- HEADER SURFACE (Standard Dark Blue primaryContainer) ---
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(LayoutConstants.HEADER_TITLE_ROW_HEIGHT)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = {
                                    if (onMenuClick != {}) {
                                        onMenuClick()
                                    } else {
                                        (context as? MainActivityWithNavigation)?.openDrawer()
                                    }
                                },
                                modifier = Modifier.testTag("known_locations_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.known_locations_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (uiState.filteredLocations.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Text(
                                        text = "${uiState.filteredLocations.size}",
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Standard PrimaryTabRow (surfaceContainerHighest, divider = {}) without icons
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    divider = {}
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(0) }
                            viewModel.selectTab(KnownLocationsTab.LIST)
                        },
                        text = { Text(stringResource(R.string.known_locations_tab_list)) },
                        modifier = Modifier.testTag("known_locations_tab_list")
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(1) }
                            viewModel.selectTab(KnownLocationsTab.MAP)
                        },
                        text = { Text(stringResource(R.string.known_locations_tab_map)) },
                        modifier = Modifier.testTag("known_locations_tab_map")
                    )
                }
            }
        }

        // Pager Content with swipe transitions
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> {
                    KnownLocationsListContent(
                        uiState = uiState,
                        onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                        onEdit = { viewModel.openEditDialog(it) },
                        onShowOnMap = { item ->
                            scope.launch {
                                pagerState.animateScrollToPage(1)
                                viewModel.selectTab(KnownLocationsTab.MAP)
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(item.latLng, 15f)
                                )
                                viewModel.openMapPeek(item)
                            }
                        },
                        onDelete = { locationPendingDeletion = it }
                    )
                }

                1 -> {
                    KnownLocationsMapContent(
                        uiState = uiState,
                        cameraPositionState = cameraPositionState,
                        onMarkerClick = { viewModel.openMapPeek(it) },
                        onDismissPeek = { viewModel.dismissMapPeek() },
                        onEditFromPeek = { item ->
                            viewModel.dismissMapPeek()
                            viewModel.openEditDialog(item)
                        }
                    )
                }
            }
        }
    }

    // Modal Edit Bottom Sheet
    uiState.selectedLocationForEdit?.let { itemToEdit ->
        EditKnownLocationDialog(
            location = itemToEdit,
            isMetric = uiState.isMetric,
            onConfirm = { id, name, altitude, source ->
                viewModel.updateLocation(id, name, altitude, source)
            },
            onFetchDem = { id, latLng ->
                viewModel.refreshDem(id, latLng)
            },
            onDismiss = { viewModel.dismissEditDialog() }
        )
    }

    // Delete Confirmation Dialog
    locationPendingDeletion?.let { itemToDelete ->
        DeleteConfirmationDialog(
            title = stringResource(R.string.delete),
            message = "Delete \"${itemToDelete.name}\"?",
            onConfirm = {
                viewModel.deleteLocation(itemToDelete.id)
                locationPendingDeletion = null
            },
            onDismiss = { locationPendingDeletion = null }
        )
    }
}

/**
 * List Perspective: Search/Filter bar and high-density location cards.
 */
@Composable
private fun KnownLocationsListContent(
    uiState: KnownLocationsUiState,
    onSearchQueryChanged: (String) -> Unit,
    onEdit: (KnownLocationItem) -> Unit,
    onShowOnMap: (KnownLocationItem) -> Unit,
    onDelete: (KnownLocationItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search Filter Bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = { Text(stringResource(R.string.known_locations_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("known_locations_search_input")
        )

        if (uiState.filteredLocations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
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
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("known_locations_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = uiState.filteredLocations,
                    key = { it.id }
                ) { item ->
                    KnownLocationCard(
                        item = item,
                        isMetric = uiState.isMetric,
                        onEdit = { onEdit(item) },
                        onShowOnMap = { onShowOnMap(item) },
                        onDelete = { onDelete(item) }
                    )
                }
            }
        }
    }
}

/**
 * Modern location card with icon badge, inline metrics, trailing edit button, and long-press context menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KnownLocationCard(
    item: KnownLocationItem,
    isMetric: Boolean,
    onEdit: () -> Unit,
    onShowOnMap: () -> Unit,
    onDelete: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Box {
        MappableListItem(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("location_card_${item.id}"),
            onClick = onEdit
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onEdit,
                        onLongClick = { showContextMenu = true }
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Leading Icon Container Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.my_locations),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Center Column: Title & Inline Metrics
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_ascent),
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = KnownLocationsUnitConversions.formatAltitude(item.altitude, isMetric),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.control_start),
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.known_locations_starts_count, item.hitCount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Trailing Edit Icon Button
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("location_card_edit_${item.id}")
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_table_edit),
                        contentDescription = stringResource(R.string.edit_my_location),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Context Menu on Long Click
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 8.dp)
        ) {
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit_my_location)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_table_edit),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        onEdit()
                    },
                    modifier = Modifier.testTag("location_edit_action_${item.id}")
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tab_map)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        onShowOnMap()
                    },
                    modifier = Modifier.testTag("location_show_map_action_${item.id}")
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
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
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Map Perspective: Interactive Google Map with 200m circular geofence overlays and bottom peek card.
 */
@Composable
private fun KnownLocationsMapContent(
    uiState: KnownLocationsUiState,
    cameraPositionState: com.google.maps.android.compose.CameraPositionState,
    onMarkerClick: (KnownLocationItem) -> Unit,
    onDismissPeek: () -> Unit,
    onEditFromPeek: (KnownLocationItem) -> Unit
) {
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val heartMarkerIcon = remember(primaryColor) {
        createHeartPinMarker(
            context = context,
            pinColor = primaryColor,
            heartColor = Color.White
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapType = MapType.TERRAIN),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, tiltGesturesEnabled = true)
        ) {
            // Render viewport-culled markers & 200m circular geofence overlays
            for (location in uiState.visibleMapLocations) {
                Marker(
                    state = MarkerState(position = location.latLng),
                    title = location.name,
                    snippet = KnownLocationsUnitConversions.formatAltitude(location.altitude, uiState.isMetric),
                    icon = heartMarkerIcon,
                    onClick = {
                        onMarkerClick(location)
                        true
                    }
                )
                Circle(
                    center = location.latLng,
                    radius = location.radius.toDouble().takeIf { it > 0 } ?: 200.0,
                    fillColor = Color(0x332196F3),
                    strokeColor = Color(0x882196F3),
                    strokeWidth = 2f
                )
            }
        }

        // Map Peek Bottom Card
        uiState.selectedLocationForMapPeek?.let { peekItem ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("map_peek_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = peekItem.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismissPeek,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = KnownLocationsUnitConversions.formatAltitude(peekItem.altitude, uiState.isMetric),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        ElevationSourceBadge(source = peekItem.source)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.known_locations_starts_count, peekItem.hitCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { onEditFromPeek(peekItem) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("map_peek_edit_button")
                    ) {
                        Icon(painter = painterResource(R.drawable.ic_table_edit), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.edit_my_location))
                    }
                }
            }
        }
    }
}

// =============================================================================
// PREVIEW DESIGN VARIANTS FOR ITERATION
// =============================================================================

/**
 * Variant 2: Modern Card with circular/squircle icon badge, inline metrics, and trailing edit action.
 */
@Composable
fun KnownLocationCardVariant2(
    item: KnownLocationItem,
    isMetric: Boolean = true,
    onEdit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    MappableListItem(
        modifier = modifier.fillMaxWidth(),
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.my_locations),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ascent),
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = KnownLocationsUnitConversions.formatAltitude(item.altitude, isMetric),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.control_start),
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.known_locations_starts_count, item.hitCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_table_edit),
                    contentDescription = stringResource(R.string.edit_my_location),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Variant 3: Material 3 Pill-Chip Badges for metrics.
 */
@Composable
fun KnownLocationCardVariant3(
    item: KnownLocationItem,
    isMetric: Boolean = true,
    onEdit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    MappableListItem(
        modifier = modifier.fillMaxWidth(),
        onClick = onEdit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.my_locations),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_table_edit),
                        contentDescription = stringResource(R.string.edit_my_location),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ascent),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = KnownLocationsUnitConversions.formatAltitude(item.altitude, isMetric),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.control_start),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.known_locations_starts_count, item.hitCount),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Variant 4: Compact Two-Tone Tile with left accent indicator.
 */
@Composable
fun KnownLocationCardVariant4(
    item: KnownLocationItem,
    isMetric: Boolean = true,
    onEdit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    MappableListItem(
        modifier = modifier.fillMaxWidth(),
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.my_locations),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${KnownLocationsUnitConversions.formatAltitude(item.altitude, isMetric)}  •  ${stringResource(R.string.known_locations_starts_count, item.hitCount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_table_edit),
                        contentDescription = stringResource(R.string.edit_my_location),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// =============================================================================
// COMPOSE PREVIEWS (Directly visible in Android Studio Split / Design View)
// =============================================================================

private val previewMockLocation1 = KnownLocationItem(
    id = 1L,
    name = "Zuhause",
    altitude = 520.0,
    radius = 50,
    latLng = LatLng(48.137154, 11.576124),
    hitCount = 42,
    isLocked = true,
    source = ElevationSource.MANUAL_USER
)

private val previewMockLocation2 = KnownLocationItem(
    id = 2L,
    name = "Olympiapark",
    altitude = 512.0,
    radius = 100,
    latLng = LatLng(48.1751, 11.5518),
    hitCount = 14,
    isLocked = false,
    source = ElevationSource.INTERNET_DEM
)

private val previewMockLocation3 = KnownLocationItem(
    id = 3L,
    name = "Starnberger See",
    altitude = 584.0,
    radius = 120,
    latLng = LatLng(47.9984, 11.3435),
    hitCount = 7,
    isLocked = false,
    source = ElevationSource.AUTO_LEARNED
)

@Preview(name = "Comparison - All 4 Variants", showBackground = true)
@Composable
fun PreviewAllLocationItemVariants() {
    ATrainingTrackerTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Variante 1: Aktuell (Route-Style)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            KnownLocationCard(
                item = previewMockLocation1,
                isMetric = true,
                onEdit = {},
                onShowOnMap = {},
                onDelete = {}
            )

            Text("Variante 2: Modern Icon-Badge & Inline-Metriken", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            KnownLocationCardVariant2(
                item = previewMockLocation1,
                isMetric = true,
                onEdit = {}
            )

            Text("Variante 3: Material 3 Chip-Badges", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            KnownLocationCardVariant3(
                item = previewMockLocation2,
                isMetric = true,
                onEdit = {}
            )

            Text("Variante 4: Compact Two-Tone Tile", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            KnownLocationCardVariant4(
                item = previewMockLocation3,
                isMetric = true,
                onEdit = {}
            )
        }
    }
}

@Preview(name = "Variant 1 - Current (Route-Style)", showBackground = true)
@Composable
fun PreviewVariant1Current() {
    ATrainingTrackerTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            KnownLocationCard(
                item = previewMockLocation1,
                isMetric = true,
                onEdit = {},
                onShowOnMap = {},
                onDelete = {}
            )
        }
    }
}

@Preview(name = "Variant 2 - Modern Icon-Badge", showBackground = true)
@Composable
fun PreviewVariant2Badge() {
    ATrainingTrackerTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            KnownLocationCardVariant2(
                item = previewMockLocation1,
                isMetric = true,
                onEdit = {}
            )
        }
    }
}

@Preview(name = "Variant 3 - Chip Badges", showBackground = true)
@Composable
fun PreviewVariant3Chips() {
    ATrainingTrackerTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            KnownLocationCardVariant3(
                item = previewMockLocation2,
                isMetric = true,
                onEdit = {}
            )
        }
    }
}

@Preview(name = "Variant 4 - Compact Tile", showBackground = true)
@Composable
fun PreviewVariant4Tile() {
    ATrainingTrackerTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            KnownLocationCardVariant4(
                item = previewMockLocation3,
                isMetric = true,
                onEdit = {}
            )
        }
    }
}

