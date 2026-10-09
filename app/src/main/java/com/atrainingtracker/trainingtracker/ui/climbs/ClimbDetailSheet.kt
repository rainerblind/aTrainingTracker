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

package com.atrainingtracker.trainingtracker.ui.climbs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.climbs.Climb
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
 * Computes tight LatLngBounds enclosing the climb path, with zero-area expansion fallback.
 */
fun calculateClimbBounds(climb: Climb): LatLngBounds? {
    val points = if (climb.pathPoints.isNotEmpty()) {
        climb.pathPoints.map { it.latLng }
    } else {
        listOf(climb.startLatLng, climb.endLatLng)
    }
    if (points.isEmpty()) return null
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
 * Dedicated Climb Detail Bottom Sheet (REQ-UI-300 / ATT-2511).
 *
 * Displays:
 * 1. Header with climb name, route counter badge, and [ClimbCategoryChip].
 * 2. Key Metrics HUD (Distance, Elevation Gain, Avg Grade, Max Grade, Start Offset).
 * 3. Focused Map viewport tightly zoomed onto the climb geometry via [MapZoomFocus.EXPLICIT_BOUNDS].
 * 4. Isolated Zoomed Elevation Profile with slope grade coloring and elevation spans.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClimbDetailSheet(
    climb: Climb,
    modifier: Modifier = Modifier,
    routeIndex: Int? = null,
    totalRouteClimbs: Int? = null,
    bSportType: BSportType = BSportType.BIKE,
    onDismiss: () -> Unit
) {
    val formatters = LocalMetricFormatter.current

    AppModalBottomSheet(
        title = climb.name.ifBlank { stringResource(R.string.climb_title) },
        onDismissRequest = onDismiss,
        showCloseButton = true,
        headerActions = {
            if (routeIndex != null && totalRouteClimbs != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.climb_route_counter, routeIndex, totalRouteClimbs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            ClimbCategoryChip(category = climb.category)
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
            ClimbDetailMetricsCard(climb = climb, formatters = formatters)

            // 2. Focused Map Viewport
            ClimbDetailMapCard(climb = climb, bSportType = bSportType)

            // 3. Isolated Zoomed Elevation Profile
            ClimbDetailElevationProfileCard(climb = climb, formatters = formatters)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ClimbDetailMetricsCard(
    climb: Climb,
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
                val startDistMeters = climb.pathPoints.firstOrNull()?.distance ?: 0.0
                Text(
                    text = stringResource(
                        R.string.routes_climb_start_at,
                        formatters.distance.format_with_units(startDistMeters) ?: "${(startDistMeters / 1000.0).roundToInt()} km"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Distance
                ClimbMetricItem(
                    iconRes = R.drawable.ic_distance,
                    label = stringResource(R.string.climb_remaining_dist),
                    value = formatters.distance.format_with_units(climb.distanceMeters) ?: "${(climb.distanceMeters / 1000.0).roundToInt()} km"
                )

                // Elevation Gain
                ClimbMetricItem(
                    iconRes = R.drawable.ic_ascent,
                    label = stringResource(R.string.climb_remaining_elevation),
                    value = "+${formatters.altitude.format_with_units(climb.elevationGainMeters) ?: "${climb.elevationGainMeters.roundToInt()} m"}"
                )

                // Average Grade
                ClimbMetricItem(
                    iconRes = R.drawable.ic_grade,
                    label = stringResource(R.string.climb_grade),
                    value = stringResource(R.string.routes_climb_avg_grade, climb.avgGradePercent)
                )

                // Maximum Grade
                ClimbMetricItem(
                    iconRes = R.drawable.ic_grade,
                    label = stringResource(R.string.climb_max_grade_label),
                    value = stringResource(R.string.routes_climb_max_grade, climb.maxGradePercent)
                )
            }
        }
    }
}

@Composable
private fun ClimbMetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    @androidx.annotation.DrawableRes iconRes: Int? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (iconRes != null) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ClimbDetailMapCard(
    climb: Climb,
    bSportType: BSportType,
    modifier: Modifier = Modifier
) {
    val climbBounds = remember(climb) { calculateClimbBounds(climb) }
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
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ATrainingTrackerMap(
                zoomFocus = if (climbBounds != null) MapZoomFocus.EXPLICIT_BOUNDS else MapZoomFocus.FIT_PRIMARY,
                initialBounds = climbBounds,
                currentLocationFlow = noLocation,
                modifier = Modifier.fillMaxSize(),
                content = {
                    climbs(listOf(climb))
                    val markersList = mutableListOf<LocationMarker>()
                    if (startMarker != null) {
                        markersList.add(
                            LocationMarker(
                                position = climb.startLatLng,
                                iconResId = R.drawable.control_start,
                                title = "Start",
                                iconDescriptor = startMarker
                            )
                        )
                    }
                    if (endMarker != null) {
                        markersList.add(
                            LocationMarker(
                                position = climb.endLatLng,
                                iconResId = R.drawable.control_stop,
                                title = "Summit",
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
private fun ClimbDetailElevationProfileCard(
    climb: Climb,
    formatters: MetricFormatterContext,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                val minAlt = climb.pathPoints.minOfOrNull { it.altitude }
                val maxAlt = climb.pathPoints.maxOfOrNull { it.altitude }
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

            ClimbDetailElevationProfile(
                climb = climb,
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
                Text(
                    text = formatters.distance.format_with_units(climb.distanceMeters) ?: "${(climb.distanceMeters / 1000.0).roundToInt()} km",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Isolated zoomed elevation profile canvas for a specific climb.
 */
@Composable
fun ClimbDetailElevationProfile(
    climb: Climb,
    modifier: Modifier = Modifier
) {
    val pathPoints = climb.pathPoints

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        if (pathPoints.size < 2) {
            val rampPath = Path().apply {
                moveTo(0f, height)
                lineTo(width, 0f)
                lineTo(width, height)
                close()
            }
            drawPath(
                path = rampPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFEF6C00).copy(alpha = 0.5f), Color.Transparent),
                    startY = 0f,
                    endY = height
                )
            )
            drawLine(
                color = Color(0xFFEF6C00),
                start = Offset(0f, height),
                end = Offset(width, 0f),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            return@Canvas
        }

        val startDist = pathPoints.first().distance
        val totalDist = (pathPoints.last().distance - startDist).coerceAtLeast(1.0)
        val minAlt = pathPoints.minOf { it.altitude }
        val maxAlt = pathPoints.maxOf { it.altitude }
        val altSpan = (maxAlt - minAlt).coerceAtLeast(10.0)

        val topPadding = 8f
        val bottomPadding = 4f
        val usableHeight = height - topPadding - bottomPadding

        var prevX = 0f
        var prevY = (height - bottomPadding - (((pathPoints[0].altitude - minAlt) / altSpan) * usableHeight)).toFloat()

        for (i in 1 until pathPoints.size) {
            val p = pathPoints[i]
            val x = (((p.distance - startDist) / totalDist) * width).toFloat().coerceIn(0f, width)
            val y = (height - bottomPadding - (((p.altitude - minAlt) / altSpan) * usableHeight)).toFloat().coerceIn(0f, height)

            val dDist = p.distance - pathPoints[i - 1].distance
            val dAlt = p.altitude - pathPoints[i - 1].altitude
            val grade = if (dDist > 0) (dAlt / dDist) * 100.0 else 0.0

            val segmentColor = when {
                grade < 3.0 -> Color(0xFF4CAF50)
                grade < 6.0 -> Color(0xFF8BC34A)
                grade < 9.0 -> Color(0xFFFFC107)
                grade < 12.0 -> Color(0xFFFF9800)
                grade < 20.0 -> Color(0xFFE53935)
                else -> Color(0xFF212121)
            }

            val fillPath = Path().apply {
                moveTo(prevX, height)
                lineTo(prevX, prevY)
                lineTo(x, y)
                lineTo(x, height)
                close()
            }
            drawPath(
                path = fillPath,
                color = segmentColor.copy(alpha = 0.35f)
            )

            drawLine(
                color = segmentColor,
                start = Offset(prevX, prevY),
                end = Offset(x, y),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            prevX = x
            prevY = y
        }
    }
}
