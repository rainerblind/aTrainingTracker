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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.LayoutConstants
import com.google.android.gms.maps.model.LatLng

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
            onConfirm = { id, name, altitude, radius, source ->
                viewModel.updateLocation(id, name, altitude, radius, source)
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
                    onDelete = { onDelete(item) }
                )
            }
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
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

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
                    // Number of Starts (Interactive Drill-Down Touch Target)
                    Surface(
                        onClick = onShowWorkouts,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.primary,
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
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = pluralStringResource(R.plurals.known_locations_starts, startsCount, startsCount),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Number of Routes (Interactive Drill-Down Touch Target per REQ-UI-188, REQ-UI-195)
                    if (linkedClusters.isNotEmpty()) {
                        Surface(
                            onClick = onShowRoutes,
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.primary,
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
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = pluralStringResource(R.plurals.known_locations_routes, linkedClusters.size, linkedClusters.size),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
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

@Preview(name = "List Item - Light", showBackground = true)
@Composable
fun PreviewKnownLocationCardLight() {
    ATrainingTrackerTheme(darkTheme = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            KnownLocationCard(
                item = previewMockLocation,
                isMetric = true,
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
