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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.helpers.combineWorkoutAndShare
import com.atrainingtracker.trainingtracker.routes.MatchedRouteSegment
import com.atrainingtracker.trainingtracker.routes.RouteSegmentMatcher
import com.atrainingtracker.trainingtracker.segments.SegmentWithPath
import com.atrainingtracker.trainingtracker.ui.climbs.ClimbCategoryChip
import com.atrainingtracker.trainingtracker.ui.climbs.ClimbDetailSheet
import com.atrainingtracker.trainingtracker.ui.climbs.getClimbCategoryColors
import com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMap
import com.atrainingtracker.trainingtracker.ui.map.ElevationProfile
import com.atrainingtracker.trainingtracker.ui.map.LocationMarker
import com.atrainingtracker.trainingtracker.ui.map.MapDetailLayout
import com.atrainingtracker.trainingtracker.ui.map.MapRoute
import com.atrainingtracker.trainingtracker.ui.map.MapSegment
import com.atrainingtracker.trainingtracker.ui.map.MapZoomFocus
import com.atrainingtracker.trainingtracker.ui.map.MappablePath
import com.atrainingtracker.trainingtracker.ui.map.createSensorMarker
import com.atrainingtracker.trainingtracker.ui.segments.SegmentDetailSheet
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class RouteBreakdownTab {
    CLIMBS,
    SEGMENTS
}

@Composable
fun RouteOnMapScreen(
    route: MapRoute?,
    routeSummary: RouteSummary?,
    backgroundPaths: List<MappablePath> = emptyList(),
    allSegments: List<SegmentWithPath> = emptyList(),
    onToggleSelection: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    useStatusBarsPadding: Boolean = true,
    showMap: Boolean = true,
    onHeaderHeightMeasured: ((Dp) -> Unit)? = null
) {
    val bSportType = route?.bSportType ?: routeSummary?.bSportType ?: BSportType.UNKNOWN

    val context = LocalContext.current
    val startMarker = remember(route?.path?.firstOrNull()) {
        route?.path?.firstOrNull()?.let {
            createSensorMarker(context, R.drawable.control_start, TTColor.StartPoint)
        }
    }
    val endMarker = remember(route?.path?.lastOrNull()) {
        route?.path?.lastOrNull()?.let {
            createSensorMarker(context, R.drawable.control_stop, TTColor.EndPoint)
        }
    }

    val climbs = route?.climbs ?: emptyList()

    val routeBounds = remember(routeSummary) {
        if (routeSummary?.minLat != null && routeSummary.maxLat != null && routeSummary.minLng != null && routeSummary.maxLng != null) {
            com.google.android.gms.maps.model.LatLngBounds(
                LatLng(routeSummary.minLat, routeSummary.minLng),
                LatLng(routeSummary.maxLat, routeSummary.maxLng)
            )
        } else null
    }

    var selectedBreakdownTab by rememberSaveable { mutableStateOf(RouteBreakdownTab.CLIMBS) }
    var selectedClimbForDetail by remember { mutableStateOf<Climb?>(null) }
    var selectedSegmentForDetail by remember { mutableStateOf<MatchedRouteSegment?>(null) }
    var highlightedSegmentId by remember { mutableStateOf<Long?>(null) }
    var externalScrubDistance by remember { mutableStateOf<Double?>(null) }

    // Layer visibility state (REQ-UI-308 / ATT-2763)
    var enabledOverlayLayers by rememberSaveable {
        mutableStateOf(RouteOverlayLayer.entries.toSet())
    }
    var hiddenClimbIds by rememberSaveable {
        mutableStateOf(emptySet<Long>())
    }
    var hiddenSegmentIds by rememberSaveable {
        mutableStateOf(emptySet<Long>())
    }
    var showLayersMenu by remember { mutableStateOf(false) }

    val matchedSegments by produceState<List<MatchedRouteSegment>>(
        initialValue = emptyList(),
        key1 = route?.path,
        key2 = allSegments,
        key3 = bSportType
    ) {
        val path = route?.path
        value = if (!path.isNullOrEmpty() && allSegments.isNotEmpty()) {
            RouteSegmentMatcher.matchSegments(path, allSegments, bSportType)
        } else {
            emptyList()
        }
    }

    MapDetailLayout(
        bSportType = bSportType,
        zoomFocus = if (routeBounds != null) MapZoomFocus.EXPLICIT_BOUNDS else MapZoomFocus.FIT_PRIMARY,
        initialBounds = routeBounds,
        activeScrubPath = route?.path,
        useStatusBarsPadding = useStatusBarsPadding,
        showMap = showMap,
        climbs = climbs,
        externalScrubDistance = externalScrubDistance,
        onHeaderHeightMeasured = onHeaderHeightMeasured,
        header = {
            routeSummary?.let {
                RouteSummaryHeader(
                    summary = it,
                    modifier = Modifier.fillMaxWidth(),
                    climbs = climbs,
                    onToggleSelection = onToggleSelection,
                    showSwitch = true // Snapshot handled by MapDetailLayout
                )
            }
        },
        analyticsContent = if (climbs.isNotEmpty() || matchedSegments.isNotEmpty()) {
            {
                if (climbs.isNotEmpty() && matchedSegments.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedBreakdownTab == RouteBreakdownTab.CLIMBS,
                            onClick = { selectedBreakdownTab = RouteBreakdownTab.CLIMBS },
                            label = {
                                Text(stringResource(R.string.routes_climbs_section_title, climbs.size))
                            }
                        )
                        FilterChip(
                            selected = selectedBreakdownTab == RouteBreakdownTab.SEGMENTS,
                            onClick = { selectedBreakdownTab = RouteBreakdownTab.SEGMENTS },
                            label = {
                                Text(stringResource(R.string.routes_segments_tab_title, matchedSegments.size))
                            }
                        )
                    }
                }

                val showClimbs = when {
                    climbs.isNotEmpty() && matchedSegments.isNotEmpty() -> selectedBreakdownTab == RouteBreakdownTab.CLIMBS
                    climbs.isNotEmpty() -> true
                    else -> false
                }

                if (showClimbs) {
                    RouteClimbsBreakdownSection(
                        climbs = climbs,
                        hiddenClimbIds = hiddenClimbIds,
                        onToggleClimbVisibility = { climbKey ->
                            hiddenClimbIds = if (climbKey in hiddenClimbIds) {
                                hiddenClimbIds - climbKey
                            } else {
                                hiddenClimbIds + climbKey
                            }
                        },
                        onClimbClick = { climb -> selectedClimbForDetail = climb }
                    )
                } else {
                    RouteSegmentsBreakdownSection(
                        segments = matchedSegments,
                        hiddenSegmentIds = hiddenSegmentIds,
                        onToggleSegmentVisibility = { segId ->
                            hiddenSegmentIds = if (segId in hiddenSegmentIds) {
                                hiddenSegmentIds - segId
                            } else {
                                hiddenSegmentIds + segId
                            }
                        },
                        onSegmentClick = { matched ->
                            selectedSegmentForDetail = matched
                            highlightedSegmentId = matched.segment.summary.stravaId
                            externalScrubDistance = matched.startDistanceMeters
                        }
                    )
                }
            }
        } else null,
        mapContent = {
            if (route != null) {
                // Waypoint POI filtering (REQ-UI-308): route polyline is permanent, waypoints toggleable
                val routeToRender = if (RouteOverlayLayer.WAYPOINTS in enabledOverlayLayers) {
                    route
                } else {
                    route.copy(waypoints = emptyList())
                }
                routes(listOf(routeToRender))

                // Climb filtering: check layer toggle and individual hidden climbs
                if (RouteOverlayLayer.CLIMBS in enabledOverlayLayers) {
                    val visibleClimbs = climbs.filterIndexed { index, climb ->
                        val climbKey = if (climb.id != 0L) climb.id else (index + 1).toLong()
                        climbKey !in hiddenClimbIds
                    }
                    if (visibleClimbs.isNotEmpty()) {
                        climbs(visibleClimbs)
                    }
                }

                // Segment filtering: check layer toggle and individual hidden segments
                if (RouteOverlayLayer.SEGMENTS in enabledOverlayLayers && matchedSegments.isNotEmpty()) {
                    val visibleSegments = matchedSegments.filter { matched ->
                        matched.segment.summary.stravaId !in hiddenSegmentIds
                    }
                    if (visibleSegments.isNotEmpty()) {
                        val segmentPaths = visibleSegments.map { matched ->
                            val isHighlighted = highlightedSegmentId == matched.segment.summary.stravaId
                            MapSegment(
                                stravaId = matched.segment.summary.stravaId,
                                name = matched.segment.summary.name,
                                bSportType = matched.segment.summary.bSportType,
                                path = matched.segment.path,
                                minLat = matched.segment.summary.minLat,
                                minLng = matched.segment.summary.minLng,
                                maxLat = matched.segment.summary.maxLat,
                                maxLng = matched.segment.summary.maxLng,
                                onClick = { id ->
                                    highlightedSegmentId = if (highlightedSegmentId == id) null else id
                                    if (highlightedSegmentId != null) {
                                        externalScrubDistance = matched.startDistanceMeters
                                    }
                                }
                            )
                        }
                        segments(
                            segments = segmentPaths,
                            activeLiveSegmentIds = highlightedSegmentId?.let { setOf(it) } ?: emptySet(),
                            onSegmentClick = { id ->
                                highlightedSegmentId = if (highlightedSegmentId == id) null else id
                                val clicked = matchedSegments.find { it.segment.summary.stravaId == id }
                                if (clicked != null && highlightedSegmentId != null) {
                                    externalScrubDistance = clicked.startDistanceMeters
                                }
                            }
                        )
                    }
                }
                
                // Add unified Start and End markers (SCRUM-185 / REQ-UI-308: permanently anchored)
                val allMarkers = mutableListOf<LocationMarker>()
                if (route.path.isNotEmpty() && startMarker != null && endMarker != null) {
                    allMarkers.add(
                        LocationMarker(
                            position = route.path.first().latLng,
                            iconResId = R.drawable.control_start,
                            title = "Start",
                            iconDescriptor = startMarker
                        )
                    )
                    allMarkers.add(
                        LocationMarker(
                            position = route.path.last().latLng,
                            iconResId = R.drawable.control_stop,
                            title = "End",
                            iconDescriptor = endMarker
                        )
                    )
                }

                if (allMarkers.isNotEmpty()) {
                    markers(allMarkers)
                }
            }
            contextualPaths(backgroundPaths, sameSportAlpha = TTAlpha.Medium)
        },
        overlay = {
            val hasClimbs = climbs.isNotEmpty()
            val hasSegments = matchedSegments.isNotEmpty()
            val hasWaypoints = route?.waypoints?.isNotEmpty() == true
            val hasAnyOverlays = hasClimbs || hasSegments || hasWaypoints

            if (hasAnyOverlays) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 76.dp, end = 16.dp)
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
                            val anyLayerDisabled = enabledOverlayLayers.size < RouteOverlayLayer.values().size
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = stringResource(R.string.route_layers),
                                modifier = Modifier.size(22.dp),
                                tint = if (anyLayerDisabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showLayersMenu,
                        onDismissRequest = { showLayersMenu = false }
                    ) {
                        RouteOverlayLayer.entries.forEach { layer ->
                            val isAvailable = when (layer) {
                                RouteOverlayLayer.CLIMBS -> hasClimbs
                                RouteOverlayLayer.SEGMENTS -> hasSegments
                                RouteOverlayLayer.WAYPOINTS -> hasWaypoints
                            }

                            val (layerName, layerColor) = when (layer) {
                                RouteOverlayLayer.CLIMBS -> Pair(
                                    stringResource(R.string.route_layer_climbs),
                                    getClimbCategoryColors(ClimbCategory.CAT_1).first
                                )
                                RouteOverlayLayer.SEGMENTS -> Pair(
                                    stringResource(R.string.route_layer_segments),
                                    TTColor.StravaOrange
                                )
                                RouteOverlayLayer.WAYPOINTS -> Pair(
                                    stringResource(R.string.route_layer_waypoints),
                                    MaterialTheme.colorScheme.tertiary
                                )
                            }

                            DropdownMenuItem(
                                enabled = isAvailable,
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.alpha(if (isAvailable) TTAlpha.High else TTAlpha.Disabled)
                                    ) {
                                        Checkbox(
                                            checked = layer in enabledOverlayLayers,
                                            onCheckedChange = null,
                                            enabled = isAvailable
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
                                    enabledOverlayLayers = if (layer in enabledOverlayLayers) {
                                        enabledOverlayLayers - layer
                                    } else {
                                        enabledOverlayLayers + layer
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier
    )

    selectedClimbForDetail?.let { climb ->
        val climbIndex = climbs.indexOf(climb).takeIf { it >= 0 }?.let { it + 1 }
        ClimbDetailSheet(
            climb = climb,
            routeIndex = climbIndex,
            totalRouteClimbs = climbs.size,
            bSportType = bSportType,
            onDismiss = { selectedClimbForDetail = null }
        )
    }

    selectedSegmentForDetail?.let { matched ->
        val segIndex = matchedSegments.indexOf(matched).takeIf { it >= 0 }?.let { it + 1 }
        SegmentDetailSheet(
            matchedSegment = matched,
            routeIndex = segIndex,
            totalRouteSegments = matchedSegments.size,
            bSportType = bSportType,
            onDismiss = { selectedSegmentForDetail = null }
        )
    }
}

@Composable
fun RouteClimbsBreakdownSection(
    climbs: List<Climb>,
    modifier: Modifier = Modifier,
    hiddenClimbIds: Set<Long> = emptySet(),
    onToggleClimbVisibility: ((Long) -> Unit)? = null,
    onClimbClick: ((Climb) -> Unit)? = null
) {
    val formatters = com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.routes_climbs_section_title, climbs.size),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        climbs.forEachIndexed { index, climb ->
            ElevatedCard(
                onClick = { onClimbClick?.invoke(climb) },
                enabled = onClimbClick != null,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ClimbCategoryChip(category = climb.category)
                            Text(
                                text = climb.name.ifBlank { stringResource(R.string.climb_route_counter, index + 1, climbs.size) },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (onToggleClimbVisibility != null) {
                            val climbKey = if (climb.id != 0L) climb.id else (index + 1).toLong()
                            val isHidden = climbKey in hiddenClimbIds
                            IconButton(
                                onClick = { onToggleClimbVisibility(climbKey) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = stringResource(if (isHidden) R.string.route_item_show else R.string.route_item_hide),
                                    tint = if (isHidden) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Metrics Row: Start at km, Length, Elevation gain, Avg grade
                    val startDistMeters = climb.pathPoints.firstOrNull()?.distance ?: 0.0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.routes_climb_start_at, formatters.distance.format_with_units(startDistMeters) ?: "${(startDistMeters / 1000.0).roundToInt()} km"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "▲ +${formatters.altitude.format_with_units(climb.elevationGainMeters) ?: "${climb.elevationGainMeters.roundToInt()} m"}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.routes_climb_avg_grade, climb.avgGradePercent),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
