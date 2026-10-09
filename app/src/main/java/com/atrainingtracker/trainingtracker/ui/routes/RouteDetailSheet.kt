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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.map.*
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter
import com.atrainingtracker.trainingtracker.ui.util.MetricFormatterContext
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt

/**
 * Computes tight LatLngBounds enclosing the route path, with zero-area expansion fallback.
 */
fun calculateRouteBounds(route: RouteWithPath): LatLngBounds? {
    val points = route.path.map { it.latLng }
    if (points.isEmpty()) {
        val summary = route.summary
        if (summary.minLat != null && summary.maxLat != null && summary.minLng != null && summary.maxLng != null) {
            return LatLngBounds(
                LatLng(summary.minLat, summary.minLng),
                LatLng(summary.maxLat, summary.maxLng)
            )
        }
        return null
    }
    val builder = LatLngBounds.builder()
    points.forEach { builder.include(it) }
    val bounds = builder.build()
    return if (bounds.northeast.latitude == bounds.southwest.latitude &&
        bounds.northeast.longitude == bounds.southwest.longitude
    ) {
        LatLngBounds(
            LatLng(bounds.southwest.latitude - 0.001, bounds.southwest.longitude - 0.001),
            LatLng(bounds.northeast.latitude + 0.001, bounds.northeast.longitude + 0.001)
        )
    } else {
        bounds
    }
}

/**
 * Dedicated Route Detail Bottom Sheet (REQ-UI-316 / ATT-2861).
 *
 * Provides a lightweight modal preview of a containing route from segment inspection:
 * 1. Header with route name, sport type icon, and close button.
 * 2. Key Metrics HUD (Distance, Elevation Gain, Min/Max Altitude).
 * 3. Focused Map viewport tightly zoomed onto the route geometry via [MapZoomFocus.EXPLICIT_BOUNDS].
 * 4. Route Elevation Profile displaying the complete elevation curve.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailSheet(
    routeWithPath: RouteWithPath,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    val formatters = LocalMetricFormatter.current

    AppModalBottomSheet(
        title = routeWithPath.summary.name.ifBlank { stringResource(R.string.routes) },
        onDismissRequest = onDismiss,
        showCloseButton = true,
        headerActions = {
            Icon(
                painter = painterResource(id = routeWithPath.summary.bSportType.iconResId),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Key Metrics HUD
            RouteDetailMetricsCard(route = routeWithPath, formatters = formatters)

            // 2. Focused Map Viewport
            RouteDetailMapCard(route = routeWithPath)

            // 3. Route Elevation Profile
            RouteDetailElevationProfileCard(route = routeWithPath)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RouteDetailMetricsCard(
    route: RouteWithPath,
    formatters: MetricFormatterContext,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Distance
                RouteDetailMetricItem(
                    iconRes = R.drawable.ic_distance,
                    label = stringResource(R.string.climb_remaining_dist),
                    value = formatters.distance.format_with_units(route.summary.distance)
                        ?: "${(route.summary.distance / 1000.0).roundToInt()} km"
                )

                // Elevation Gain
                RouteDetailMetricItem(
                    iconRes = R.drawable.ic_ascent,
                    label = stringResource(R.string.climb_remaining_elevation),
                    value = "+${formatters.altitude.format_with_units(route.summary.elevationGain) ?: "${route.summary.elevationGain.roundToInt()} m"}"
                )

                // Altitude Range / Extrema
                if (route.path.isNotEmpty()) {
                    val minAlt = route.path.minOf { it.altitude }
                    val maxAlt = route.path.maxOf { it.altitude }
                    val minFormatted = formatters.altitude.format_with_units(minAlt) ?: "${minAlt.roundToInt()} m"
                    val maxFormatted = formatters.altitude.format_with_units(maxAlt) ?: "${maxAlt.roundToInt()} m"

                    RouteDetailMetricItem(
                        iconRes = R.drawable.ic_ascent,
                        label = stringResource(R.string.graph_heading_elevation),
                        value = "$minFormatted - $maxFormatted"
                    )
                } else if (route.climbs.isNotEmpty()) {
                    RouteDetailMetricItem(
                        iconRes = R.drawable.ic_ascent,
                        label = stringResource(R.string.graph_heading_elevation),
                        value = stringResource(R.string.routes_climb_count, route.climbs.size)
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteDetailMetricItem(
    iconRes: Int,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun RouteDetailMapCard(
    route: RouteWithPath,
    modifier: Modifier = Modifier
) {
    val routeBounds = remember(route) { calculateRouteBounds(route) }
    val context = LocalContext.current
    val noLocation = remember { MutableStateFlow<LatLng?>(null) }
    val startMarker = remember(context) {
        createSensorMarker(context, R.drawable.control_start, TTColor.StartPoint)
    }
    val endMarker = remember(context) {
        createSensorMarker(context, R.drawable.control_stop, TTColor.EndPoint)
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ATrainingTrackerMap(
                zoomFocus = if (routeBounds != null) MapZoomFocus.EXPLICIT_BOUNDS else MapZoomFocus.FIT_PRIMARY,
                initialBounds = routeBounds,
                currentLocationFlow = noLocation,
                modifier = Modifier.fillMaxSize(),
                content = {
                    routes(listOf(route.toMapRoute()))
                    val markersList = mutableListOf<LocationMarker>()
                    if (startMarker != null && route.path.isNotEmpty()) {
                        markersList.add(
                            LocationMarker(
                                position = route.path.first().latLng,
                                iconResId = R.drawable.control_start,
                                title = "Start",
                                iconDescriptor = startMarker
                            )
                        )
                    }
                    if (endMarker != null && route.path.isNotEmpty()) {
                        markersList.add(
                            LocationMarker(
                                position = route.path.last().latLng,
                                iconResId = R.drawable.control_stop,
                                title = "Finish",
                                iconDescriptor = endMarker
                            )
                        )
                    }
                    if (markersList.isNotEmpty()) {
                        markers(markersList)
                    }
                }
            )
        }
    }
}

@Composable
private fun RouteDetailElevationProfileCard(
    route: RouteWithPath,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.graph_heading_elevation),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ElevationProfile(
                pathPoints = route.path,
                currentDistance = null,
                bSportType = route.summary.bSportType,
                climbs = route.climbs,
                showZoomControls = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}
