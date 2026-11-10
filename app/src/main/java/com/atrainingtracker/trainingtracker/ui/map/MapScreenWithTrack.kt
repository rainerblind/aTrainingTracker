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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.atrainingtracker.banalservice.sensor.formater.AltitudeFormatter
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterStats
import com.atrainingtracker.trainingtracker.ui.clusters.ClusterMarkerType
import com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClusterIdentityRow
import com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClusterMetadataBlock
import com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClustersViewModel
import com.atrainingtracker.trainingtracker.ui.climbs.ClimbDetailSheet
import com.atrainingtracker.trainingtracker.ui.climbs.getClimbCategoryColors
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.components.core.BottomSheetDesign
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.ui.knownlocations.EditKnownLocationDialog
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
    modifier: Modifier = Modifier,
    onMenuClick: (() -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val liveSegments by viewModel.liveSegments.collectAsStateWithLifecycle()
    val allRoutes by viewModel.allRoutes.collectAsStateWithLifecycle()
    val allClusters by viewModel.allClusters.collectAsStateWithLifecycle()
    val enabledLayers by viewModel.enabledLayers.collectAsStateWithLifecycle()

    // The ID of the segment/route/location/cluster/climb currently being "peeked"
    var selectedSegmentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedRouteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedClusterId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedClimbId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedLocationId by rememberSaveable { mutableStateOf<Long?>(targetLocationId) }
    var editingLocation by remember { mutableStateOf<KnownLocationItem?>(null) }
    var previewRadius by remember { mutableStateOf<Int?>(null) }

    var measuredSegmentHeaderHeight by remember(selectedSegmentId) { mutableStateOf<Dp?>(null) }
    var measuredRouteHeaderHeight by remember(selectedRouteId) { mutableStateOf<Dp?>(null) }
    var measuredClusterHeaderHeight by remember(selectedClusterId) { mutableStateOf<Dp?>(null) }

    LaunchedEffect(targetLocationId) {
        if (targetLocationId != null && targetLocationId > 0) {
            selectedSegmentId = null
            selectedRouteId = null
            selectedClusterId = null
            selectedClimbId = null
            selectedLocationId = targetLocationId
        }
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            skipHiddenState = false,
            confirmValueChange = { targetValue ->
                if (selectedLocationId != null && targetValue == SheetValue.Expanded) {
                    false
                } else {
                    true
                }
            }
        )
    )

    // Effect: When a user clicks a new segment/route/location/cluster, ensure the sheet is at least "Partially Expanded" (Peeked)
    LaunchedEffect(selectedSegmentId, selectedRouteId, selectedLocationId, selectedClusterId) {
        if (selectedSegmentId != null || selectedRouteId != null || selectedLocationId != null || selectedClusterId != null) {
            scaffoldState.bottomSheetState.partialExpand()
        } else {
            scaffoldState.bottomSheetState.hide()
        }
    }

    // Effect: When dynamic measurement completes for the active sheet, re-align the resting offset flush to the navigation bar (REQ-UI-221, ATT-1645)
    LaunchedEffect(measuredSegmentHeaderHeight, measuredRouteHeaderHeight, measuredClusterHeaderHeight) {
        if (selectedSegmentId != null || selectedRouteId != null || selectedClusterId != null) {
            if (scaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded ||
                scaffoldState.bottomSheetState.targetValue == SheetValue.PartiallyExpanded) {
                scaffoldState.bottomSheetState.partialExpand()
            }
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

    val displayLocations = remember(uiState.knownLocations, editingLocation, previewRadius) {
        val editing = editingLocation
        val radius = previewRadius
        if (editing != null && radius != null) {
            uiState.knownLocations.map { loc ->
                if (loc.id == editing.id) loc.copy(radius = radius) else loc
            }
        } else {
            uiState.knownLocations
        }
    }

    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val maxSheetHeight = maxHeight - statusBarHeight

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetSwipeEnabled = selectedLocationId == null,
            sheetShape = BottomSheetDesign.SheetShape,
            sheetContainerColor = MaterialTheme.colorScheme.surface,
            sheetShadowElevation = BottomSheetDesign.SheetShadowElevation,
            sheetTonalElevation = BottomSheetDesign.SheetTonalElevation,
            sheetPeekHeight = when {
                selectedSegmentId != null -> {
                    val base = measuredSegmentHeaderHeight ?: BottomSheetDesign.PeekHeightSegment
                    base + navBarHeight
                }
                selectedRouteId != null -> {
                    val routeSummary = allRoutes.find { it.summary.id == selectedRouteId }?.summary
                    val defaultPeek = if (routeSummary?.description.isNullOrEmpty()) {
                        BottomSheetDesign.PeekHeightRoute
                    } else {
                        BottomSheetDesign.PeekHeightRouteWithDescription
                    }
                    val base = measuredRouteHeaderHeight ?: defaultPeek
                    base + navBarHeight
                }
                selectedLocationId != null -> BottomSheetDesign.PeekHeightKnownLocation + navBarHeight
                selectedClusterId != null -> {
                    val base = measuredClusterHeaderHeight ?: BottomSheetDesign.PeekHeightWorkout
                    base + navBarHeight
                }
                else -> 0.dp
            },
            sheetDragHandle = null,
            sheetContent = {
                if (selectedSegmentId != null || selectedRouteId != null || selectedLocationId != null || selectedClusterId != null) {
                    Box(modifier = Modifier.fillMaxWidth().height(maxSheetHeight)) {
                        when {
                            selectedClusterId != null -> {
                                val selectedCluster =
                                    uiState.clusters.find { it.id == selectedClusterId }

                                if (selectedCluster != null) {
                                    val rawCluster = allClusters.find { it.id == selectedClusterId }
                                    ClusterOnMapSheet(
                                        cluster = selectedCluster,
                                        rawCluster = rawCluster,
                                        onHeaderHeightMeasured = { measuredClusterHeaderHeight = it },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            selectedLocationId != null -> {
                                val selectedLocation =
                                    uiState.knownLocations.find { it.id == selectedLocationId }

                                if (selectedLocation != null) {
                                    val isMetric = remember { TrainingApplication.getUnit() == MyUnits.METRIC }
                                    KnownLocationOnMapSheet(
                                        location = selectedLocation,
                                        isMetric = isMetric,
                                        onEdit = { editingLocation = selectedLocation },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            selectedSegmentId != null -> {
                                val selectedSegment =
                                    uiState.segments.find { it.stravaId == selectedSegmentId }

                                SegmentOnMapScreen(
                                    segmentSummary = liveSegments.find { it.summary.stravaId == selectedSegmentId }?.summary,
                                    segment = selectedSegment,
                                    modifier = Modifier.fillMaxSize(),
                                    useStatusBarsPadding = false,
                                    onHeaderHeightMeasured = { measuredSegmentHeaderHeight = it }
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
                                    useStatusBarsPadding = false,
                                    onHeaderHeightMeasured = { measuredRouteHeaderHeight = it }
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(1.dp))
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                ATrainingTrackerMap(
                    zoomFocus = if (initialBounds != null) MapZoomFocus.EXPLICIT_BOUNDS else MapZoomFocus.LOCAL_SEGMENTS,
                    initialBounds = initialBounds,
                    bSportType = uiState.bSportType,
                    currentLocationFlow = MutableStateFlow(currentLocation),
                    contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                    onMapClick = {
                        selectedSegmentId = null
                        selectedRouteId = null
                        selectedLocationId = null
                        selectedClusterId = null
                        selectedClimbId = null
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (GeneralMapLayer.KNOWN_LOCATIONS in enabledLayers) {
                        knownLocations(displayLocations, onLocationClick = { id ->
                            selectedSegmentId = null
                            selectedRouteId = null
                            selectedClusterId = null
                            selectedClimbId = null
                            selectedLocationId = id
                        })
                    }
                    if (GeneralMapLayer.SEGMENTS in enabledLayers) {
                        segments(uiState.segments, onSegmentClick = { id ->
                            selectedRouteId = null
                            selectedLocationId = null
                            selectedClusterId = null
                            selectedClimbId = null
                            selectedSegmentId = id
                        })
                    }
                    if (GeneralMapLayer.ROUTES in enabledLayers) {
                        routes(uiState.routes, onRouteClick = { id ->
                            selectedSegmentId = null
                            selectedLocationId = null
                            selectedClusterId = null
                            selectedClimbId = null
                            selectedRouteId = id
                        })
                    }
                    if (GeneralMapLayer.CLUSTERS in enabledLayers) {
                        clusters(uiState.clusters, onClusterClick = { id ->
                            selectedSegmentId = null
                            selectedRouteId = null
                            selectedLocationId = null
                            selectedClimbId = null
                            selectedClusterId = id
                        })
                    }
                    if (GeneralMapLayer.CLIMBS in enabledLayers) {
                        climbs(uiState.climbs, onClimbClick = { id ->
                            selectedSegmentId = null
                            selectedRouteId = null
                            selectedLocationId = null
                            selectedClusterId = null
                            selectedClimbId = id
                        })
                    }
                    if (GeneralMapLayer.TRACK in enabledLayers) {
                        markers(uiState.markers)
                        liveTrack(uiState.currentTrack)
                    }
                }

                // Top-Start Navigation Button (REQ-MAP-059.4)
                if (onMenuClick != null || onNavigateBack != null) {
                    val isBack = onNavigateBack != null && onMenuClick == null
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(top = 16.dp, start = 16.dp)
                    ) {
                        Surface(
                            onClick = {
                                if (isBack) onNavigateBack() else onMenuClick?.invoke()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("map_navigation_button"),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = TTAlpha.Overlay),
                            shadowElevation = 6.dp,
                            tonalElevation = 2.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isBack) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Menu,
                                    contentDescription = if (isBack) stringResource(R.string.back) else null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Floating Layers Button & DropdownMenu (REQ-UI-322)
                var showLayersMenu by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 16.dp, end = 16.dp)
                ) {
                    Surface(
                        onClick = { showLayersMenu = true },
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = TTAlpha.Overlay),
                        shadowElevation = 6.dp,
                        tonalElevation = 2.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val anyLayerDisabled = enabledLayers.size < GeneralMapLayer.entries.size
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = stringResource(R.string.map_layers),
                                modifier = Modifier.size(22.dp),
                                tint = if (anyLayerDisabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showLayersMenu,
                        onDismissRequest = { showLayersMenu = false }
                    ) {
                        GeneralMapLayer.entries.forEach { layer ->
                            val (layerName, layerColor) = when (layer) {
                                GeneralMapLayer.ROUTES -> Pair(
                                    stringResource(R.string.map_layer_routes),
                                    TTColor.RouteSelected
                                )
                                GeneralMapLayer.SEGMENTS -> Pair(
                                    stringResource(R.string.map_layer_segments),
                                    TTColor.StravaOrange
                                )
                                GeneralMapLayer.KNOWN_LOCATIONS -> Pair(
                                    stringResource(R.string.map_layer_locations),
                                    MaterialTheme.colorScheme.tertiary
                                )
                                GeneralMapLayer.TRACK -> Pair(
                                    stringResource(R.string.map_layer_track),
                                    TrackType.BEST.color
                                )
                                GeneralMapLayer.CLUSTERS -> Pair(
                                    stringResource(R.string.map_layer_clusters),
                                    TTColor.Cluster
                                )
                                GeneralMapLayer.CLIMBS -> Pair(
                                    stringResource(R.string.map_layer_climbs),
                                    getClimbCategoryColors(ClimbCategory.CAT_1).first
                                )
                            }

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Checkbox(
                                            checked = layer in enabledLayers,
                                            onCheckedChange = null
                                        )
                                        Surface(
                                            modifier = Modifier.size(12.dp),
                                            color = layerColor,
                                            shape = RoundedCornerShape(2.dp)
                                        ) {}
                                        Text(
                                            text = layerName,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.toggleLayer(layer)
                                }
                            )
                        }
                    }
                }
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
            onRadiusChange = { newRadius ->
                previewRadius = newRadius
            },
            onConfirm = { id, name, altitude, radius, source ->
                viewModel.updateKnownLocation(id, name, altitude, radius, source)
                previewRadius = null
                editingLocation = null
            },
            onDismiss = {
                previewRadius = null
                editingLocation = null
            }
        )
    }

    selectedClimbId?.let { id ->
        val climb = uiState.climbs.find { it.id == id }
        if (climb != null) {
            ClimbDetailSheet(
                climb = climb,
                bSportType = uiState.bSportType,
                onDismiss = { selectedClimbId = null }
            )
        }
    }

    // Handle system back button to close the peek
    BackHandler(enabled = selectedSegmentId != null || selectedRouteId != null || selectedLocationId != null || selectedClusterId != null || selectedClimbId != null) {
        selectedSegmentId = null
        selectedRouteId = null
        selectedLocationId = null
        selectedClusterId = null
        selectedClimbId = null
    }
}

/**
 * Bottom sheet peek layout for favorite locations (Lieblingsorte) on the central map.
 * Formatted identically to the list entry in [KnownLocationsScreen], with an Edit icon (REQ-UI-180).
 */
@Composable
fun KnownLocationOnMapSheet(
    location: KnownLocationItem,
    isMetric: Boolean,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = location.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.Edit),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Prominent Altitude Metric
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ascent),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = KnownLocationsUnitConversions.formatAltitude(location.altitude, isMetric),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Number of Starts
                Text(
                    text = pluralStringResource(R.plurals.known_locations_starts, location.hitCount, location.hitCount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Bottom sheet peek layout for favorite route clusters (Lieblingsstrecken) on the central map (REQ-UI-322).
 */
@Composable
fun ClusterOnMapSheet(
    cluster: MapCluster,
    rawCluster: WorkoutCluster? = null,
    clustersViewModel: WorkoutClustersViewModel = viewModel(),
    onHeaderHeightMeasured: ((Dp) -> Unit)? = null,
    onHitCountClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolvedCluster = remember(cluster, rawCluster) {
        rawCluster ?: WorkoutCluster(
            id = cluster.id,
            name = cluster.name,
            probableSportId = 0L,
            startLat = cluster.latLngs.firstOrNull()?.latitude ?: 0.0,
            startLng = cluster.latLngs.firstOrNull()?.longitude ?: 0.0,
            endLat = cluster.latLngs.lastOrNull()?.latitude ?: 0.0,
            endLng = cluster.latLngs.lastOrNull()?.longitude ?: 0.0,
            maxDispLat = cluster.latLngs.lastOrNull()?.latitude ?: 0.0,
            maxDispLng = cluster.latLngs.lastOrNull()?.longitude ?: 0.0,
            refDistance = cluster.refDistance,
            hitCount = cluster.hitCount,
            bSportType = cluster.bSportType
        )
    }

    LaunchedEffect(resolvedCluster.id) {
        clustersViewModel.selectCluster(resolvedCluster)
    }

    val linkedRoute by clustersViewModel.linkedRoute.collectAsStateWithLifecycle()
    val mapState by clustersViewModel.mapState.collectAsStateWithLifecycle()
    val enabledMarkerTypes by clustersViewModel.enabledMarkerTypes.collectAsStateWithLifecycle()

    val clusterBounds = remember(resolvedCluster, cluster.latLngs) {
        if (resolvedCluster.minLat != null && resolvedCluster.maxLat != null && resolvedCluster.minLng != null && resolvedCluster.maxLng != null) {
            LatLngBounds(
                LatLng(resolvedCluster.minLat, resolvedCluster.minLng),
                LatLng(resolvedCluster.maxLat, resolvedCluster.maxLng)
            )
        } else if (cluster.latLngs.isNotEmpty()) {
            val builder = LatLngBounds.builder()
            cluster.latLngs.forEach { builder.include(it) }
            try {
                builder.build()
            } catch (e: Exception) {
                null
            }
        } else null
    }

    val startLabel = stringResource(R.string.start)
    val endLabel = stringResource(R.string.end)
    val apexLabel = stringResource(R.string.max_line_distance)
    val minAltLabel = stringResource(R.string.marker_min_altitude)
    val maxAltLabel = stringResource(R.string.marker_max_altitude)

    val fingerprintMarkers = remember(
        resolvedCluster,
        enabledMarkerTypes,
        startLabel,
        endLabel,
        apexLabel,
        minAltLabel,
        maxAltLabel
    ) {
        val list = mutableListOf<LocationMarker>()

        if (enabledMarkerTypes.contains(ClusterMarkerType.START)) {
            list.add(
                LocationMarker(
                    position = LatLng(resolvedCluster.startLat, resolvedCluster.startLng),
                    iconResId = R.drawable.control_start,
                    title = startLabel,
                    iconDescriptor = createSensorMarker(context, R.drawable.control_start, TTColor.StartPoint),
                    draggable = false
                )
            )
        }

        if (enabledMarkerTypes.contains(ClusterMarkerType.END)) {
            list.add(
                LocationMarker(
                    position = LatLng(resolvedCluster.endLat, resolvedCluster.endLng),
                    iconResId = R.drawable.control_stop,
                    title = endLabel,
                    iconDescriptor = createSensorMarker(context, R.drawable.control_stop, TTColor.EndPoint),
                    draggable = false
                )
            )
        }

        if (enabledMarkerTypes.contains(ClusterMarkerType.DISTANCE)) {
            list.add(
                LocationMarker(
                    position = LatLng(resolvedCluster.maxDispLat, resolvedCluster.maxDispLng),
                    iconResId = R.drawable.ic_distance,
                    title = apexLabel,
                    iconDescriptor = createSensorMarker(context, R.drawable.ic_distance, TTColor.ApexPoint),
                    draggable = false
                )
            )
        }

        if (enabledMarkerTypes.contains(ClusterMarkerType.ALTITUDE_MIN)) {
            resolvedCluster.minAltLatLng?.let { minAltPos ->
                list.add(
                    LocationMarker(
                        position = minAltPos,
                        iconResId = R.drawable.ic_altitude_min,
                        title = minAltLabel,
                        iconDescriptor = createSensorMarker(context, R.drawable.ic_altitude_min, TTColor.MinAltitude),
                        draggable = false
                    )
                )
            }
        }

        if (enabledMarkerTypes.contains(ClusterMarkerType.ALTITUDE_MAX)) {
            resolvedCluster.maxAltLatLng?.let { maxAltPos ->
                list.add(
                    LocationMarker(
                        position = maxAltPos,
                        iconResId = R.drawable.ic_altitude_max,
                        title = maxAltLabel,
                        iconDescriptor = createSensorMarker(context, R.drawable.ic_altitude_max, TTColor.MaxAltitude),
                        draggable = false
                    )
                )
            }
        }

        list
    }

    val filteredMemberMarkers = remember(mapState.memberMarkers, enabledMarkerTypes) {
        mapState.memberMarkers
            .filter { enabledMarkerTypes.contains(it.markerType) }
            .map { marker ->
                val color = when (marker.markerType) {
                    ClusterMarkerType.START -> TTColor.StartPoint
                    ClusterMarkerType.END -> TTColor.EndPoint
                    ClusterMarkerType.DISTANCE -> TTColor.ApexPoint
                    ClusterMarkerType.ALTITUDE_MIN -> TTColor.MinAltitude
                    ClusterMarkerType.ALTITUDE_MAX -> TTColor.MaxAltitude
                }
                LocationMarker(
                    position = marker.pos,
                    iconResId = marker.iconResId,
                    title = marker.title,
                    iconDescriptor = createSensorMarker(context, marker.iconResId, color, Color.White),
                    alpha = 0.5f
                )
            }
    }

    val heatmapOpacity = when {
        resolvedCluster.hitCount <= 5 -> 0.6
        resolvedCluster.hitCount <= 20 -> 0.8
        else -> 1.0
    }

    MapDetailLayout(
        bSportType = resolvedCluster.bSportType,
        zoomFocus = if (clusterBounds != null) MapZoomFocus.EXPLICIT_BOUNDS else MapZoomFocus.FIT_PRIMARY,
        initialBounds = clusterBounds,
        activeScrubPath = null,
        showMap = true,
        useStatusBarsPadding = false,
        showElevationProfile = false,
        showZoomControls = false,
        showTelemetryCharts = false,
        onHeaderHeightMeasured = onHeaderHeightMeasured,
        header = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                WorkoutClusterIdentityRow(
                    cluster = resolvedCluster,
                    viewModel = clustersViewModel,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                WorkoutClusterMetadataBlock(
                    cluster = resolvedCluster,
                    viewModel = clustersViewModel,
                    modifier = Modifier.fillMaxWidth(),
                    includeSpacer = false,
                    onHitCountClick = onHitCountClick
                )
            }
        },
        overlay = {
            if (mapState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        mapContent = {
            heatmap(
                allPaths = mapState.heatmapPaths,
                opacity = heatmapOpacity,
                colorLut = ThermalColorLut.CLASSIC_BLUE,
                normalizeCorridors = false,
                useClassicStepIntensity = true
            )

            linkedRoute?.let { route ->
                path(route.toMapRoute().copy(isSelected = true), alpha = 1.0f)
            }

            mapState.tracks.forEach { track ->
                path(track, alpha = 0.3f)
            }

            markers(filteredMemberMarkers)
            markers(fingerprintMarkers)
        },
        modifier = modifier
    )
}
