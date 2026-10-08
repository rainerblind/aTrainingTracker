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

package com.atrainingtracker.trainingtracker.ui.segments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.routes.MatchedRouteSegment
import com.atrainingtracker.trainingtracker.ui.climbs.ClimbCategoryChip
import com.atrainingtracker.trainingtracker.ui.components.MetricBadge
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
 * Computes tight LatLngBounds enclosing the segment path, with zero-area expansion fallback.
 */
fun calculateSegmentBounds(matchedSegment: MatchedRouteSegment): LatLngBounds? {
    val points = matchedSegment.segment.path.map { it.latLng }
    if (points.isEmpty()) {
        val summary = matchedSegment.segment.summary
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
 * Dedicated Segment Detail Bottom Sheet (REQ-UI-303 / ATT-2774).
 *
 * Provides visual, functional, and architectural parity with [com.atrainingtracker.trainingtracker.ui.climbs.ClimbDetailSheet]:
 * 1. Header with segment name, route counter badge, category chip, PR badge, and dismiss button.
 * 2. Key Metrics HUD displaying start offset along the route, city, and full [SegmentDetails].
 * 3. Focused Map viewport tightly zoomed onto the segment geometry via [MapZoomFocus.EXPLICIT_BOUNDS].
 * 4. Isolated Zoomed Elevation Profile showing the segment elevation curve with altitude extrema.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentDetailSheet(
    matchedSegment: MatchedRouteSegment,
    modifier: Modifier = Modifier,
    routeIndex: Int? = null,
    totalRouteSegments: Int? = null,
    bSportType: BSportType = BSportType.BIKE,
    onDismiss: () -> Unit
) {
    val formatters = LocalMetricFormatter.current
    val summary = matchedSegment.segment.summary

    AppModalBottomSheet(
        title = summary.name.ifBlank { stringResource(R.string.strava_segments_title) },
        onDismissRequest = onDismiss,
        showCloseButton = true,
        headerActions = {
            if (routeIndex != null && totalRouteSegments != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.routes_segment_counter, routeIndex, totalRouteSegments),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            val category = ClimbCategory.fromCode(summary.climbCategory)
            if (category != ClimbCategory.UNCATEGORIZED) {
                ClimbCategoryChip(category = category)
                Spacer(modifier = Modifier.width(6.dp))
            }

            if (summary.prTime.isNotBlank()) {
                MetricBadge(
                    text = stringResource(R.string.routes_segment_pr, summary.prTime)
                )
            }
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
            SegmentDetailMetricsCard(matchedSegment = matchedSegment, formatters = formatters)

            // 2. Focused Map Viewport
            SegmentDetailMapCard(matchedSegment = matchedSegment, bSportType = bSportType)

            // 3. Isolated Zoomed Elevation Profile
            SegmentDetailElevationProfileCard(matchedSegment = matchedSegment, formatters = formatters)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SegmentDetailMetricsCard(
    matchedSegment: MatchedRouteSegment,
    formatters: MetricFormatterContext,
    modifier: Modifier = Modifier
) {
    val summary = matchedSegment.segment.summary
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
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
                val startDistMeters = matchedSegment.startDistanceMeters
                Text(
                    text = stringResource(
                        R.string.routes_segment_start_at,
                        formatters.distance.format_with_units(startDistMeters) ?: "${(startDistMeters / 1000.0).roundToInt()} km"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (summary.city.isNotBlank()) {
                    Text(
                        text = summary.city,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            SegmentDetails(
                summary = summary,
                showStravaLogo = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SegmentDetailMapCard(
    matchedSegment: MatchedRouteSegment,
    bSportType: BSportType,
    modifier: Modifier = Modifier
) {
    val segmentBounds = remember(matchedSegment) { calculateSegmentBounds(matchedSegment) }
    val context = LocalContext.current
    val noLocation = remember { MutableStateFlow<LatLng?>(null) }
    val startMarker = remember(context) {
        createSensorMarker(context, R.drawable.control_start, TTColor.StartPoint)
    }
    val endMarker = remember(context) {
        createSensorMarker(context, R.drawable.control_stop, TTColor.EndPoint)
    }

    val pathPoints = matchedSegment.segment.path
    if (pathPoints.isEmpty()) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ATrainingTrackerMap(
                zoomFocus = if (segmentBounds != null) MapZoomFocus.EXPLICIT_BOUNDS else MapZoomFocus.FIT_PRIMARY,
                initialBounds = segmentBounds,
                currentLocationFlow = noLocation,
                modifier = Modifier.fillMaxSize(),
                content = {
                    path(
                        path = MapTrack(
                            id = matchedSegment.segment.summary.stravaId,
                            type = TrackType.BEST,
                            bSportType = bSportType,
                            path = pathPoints
                        )
                    )
                    val markersList = mutableListOf<LocationMarker>()
                    if (startMarker != null) {
                        markersList.add(
                            LocationMarker(
                                position = pathPoints.first().latLng,
                                iconResId = R.drawable.control_start,
                                title = "Start",
                                iconDescriptor = startMarker
                            )
                        )
                    }
                    if (endMarker != null) {
                        markersList.add(
                            LocationMarker(
                                position = pathPoints.last().latLng,
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
private fun SegmentDetailElevationProfileCard(
    matchedSegment: MatchedRouteSegment,
    formatters: MetricFormatterContext,
    modifier: Modifier = Modifier
) {
    val pathPoints = matchedSegment.segment.path
    if (pathPoints.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                val minAlt = pathPoints.minOfOrNull { it.altitude }
                val maxAlt = pathPoints.maxOfOrNull { it.altitude }
                Text(
                    text = stringResource(R.string.graph_heading_elevation),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (minAlt != null && maxAlt != null) {
                    Text(
                        text = "${minAlt.roundToInt()} m → ${maxAlt.roundToInt()} m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ElevationProfile(
                pathPoints = pathPoints,
                currentDistance = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "0 km",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val distMeters = matchedSegment.segment.summary.distance_raw.takeIf { it > 0.0 }
                    ?: (pathPoints.last().distance - pathPoints.first().distance).coerceAtLeast(0.0)
                Text(
                    text = formatters.distance.format_with_units(distMeters) ?: "${(distMeters / 1000.0).roundToInt()} km",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
