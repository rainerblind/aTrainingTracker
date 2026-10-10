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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.map.*
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter
import kotlin.math.roundToInt

/**
 * Dedicated Climb Inspection Screen backed by [MapDetailLayout] (REQ-UI-333, ATT-3053).
 *
 * Provides visual, architectural, and interaction parity with [SegmentOnMapScreen]:
 * 1. Harmonized header featuring [ClimbHeader] and [ClimbDetails].
 * 2. Interactive [MapDetailLayout] with elevation profile scrubbing and collapsible map viewport.
 * 3. Focused climb polyline with start/summit markers and zero-area expansion fallback bounds.
 */
@Composable
fun ClimbOnMapScreen(
    climb: Climb,
    modifier: Modifier = Modifier,
    routeIndex: Int? = null,
    totalRouteClimbs: Int? = null,
    bSportType: BSportType = BSportType.BIKE,
    useStatusBarsPadding: Boolean = false,
    showMap: Boolean = true,
    onHeaderHeightMeasured: ((Dp) -> Unit)? = null
) {
    val climbBounds = remember(climb) { calculateClimbBounds(climb) }
    val context = LocalContext.current
    val startMarker = remember(context) {
        createSensorMarker(context, R.drawable.control_start, TTColor.StartPoint)
    }
    val endMarker = remember(context) {
        createSensorMarker(context, R.drawable.control_stop, TTColor.EndPoint)
    }

    MapDetailLayout(
        bSportType = bSportType,
        zoomFocus = if (climbBounds != null) MapZoomFocus.EXPLICIT_BOUNDS else MapZoomFocus.FIT_PRIMARY,
        initialBounds = climbBounds,
        activeScrubPath = climb.pathPoints,
        useStatusBarsPadding = useStatusBarsPadding,
        showMap = showMap,
        onHeaderHeightMeasured = onHeaderHeightMeasured,
        header = {
            Column {
                ClimbHeader(
                    climb = climb,
                    routeIndex = routeIndex,
                    totalRouteClimbs = totalRouteClimbs,
                    bSportType = bSportType,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                ClimbDetails(
                    climb = climb,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)
                )
            }
        },
        mapContent = {
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
        },
        modifier = modifier
    )
}

/**
 * Harmonized Climb Header component mirroring [SegmentHeader] design tokens.
 */
@Composable
fun ClimbHeader(
    climb: Climb,
    modifier: Modifier = Modifier,
    routeIndex: Int? = null,
    totalRouteClimbs: Int? = null,
    bSportType: BSportType = BSportType.BIKE
) {
    val formatters = LocalMetricFormatter.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // --- TOP ROW: Sport Icon, Name, and Category Chip ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Sport Icon
                Icon(
                    painter = painterResource(id = bSportType.iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = Color.Unspecified
                )

                // 2. Name
                Text(
                    text = climb.name.ifBlank { stringResource(R.string.climb_title) },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // 3. Category Chip
                ClimbCategoryChip(category = climb.category)
            }

            // --- SECOND ROW: Route Counter Badge and Start Offset ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                val startDistMeters = climb.pathPoints.firstOrNull()?.distance ?: 0.0
                Text(
                    text = stringResource(
                        R.string.routes_climb_start_at,
                        formatters.distance.format_with_units(startDistMeters) ?: "${(startDistMeters / 1000.0).roundToInt()} km"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Harmonized Climb Details component mirroring [SegmentDetails] design tokens and 3-row layout.
 */
@Composable
fun ClimbDetails(
    climb: Climb,
    modifier: Modifier = Modifier
) {
    val formatters = LocalMetricFormatter.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // --- ROW 1: Distance ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MetricItem(
                iconRes = R.drawable.ic_distance,
                value = formatters.distance.format_with_units(climb.distanceMeters) ?: "${(climb.distanceMeters / 1000.0).roundToInt()} km",
                isPrimary = true
            )
        }

        // --- ROW 2: Grades (Average and Maximum) ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetricItem(
                iconRes = R.drawable.ic_grade,
                value = stringResource(R.string.routes_climb_avg_grade, climb.avgGradePercent),
                isPrimary = true
            )
            VerticalClimbDivider()
            Text(
                text = stringResource(R.string.routes_climb_max_grade, climb.maxGradePercent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // --- ROW 3: Elevations (Ascent Gain and Min/Max Altitude) ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_altitude),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))

            MetricItem(
                iconRes = R.drawable.ic_ascent,
                value = "+${formatters.altitude.format_with_units(climb.elevationGainMeters) ?: "${climb.elevationGainMeters.roundToInt()} m"}",
                isPrimary = true
            )

            val minAlt = climb.pathPoints.minOfOrNull { it.altitude }
            val maxAlt = climb.pathPoints.maxOfOrNull { it.altitude }
            if (minAlt != null && maxAlt != null) {
                VerticalClimbDivider()
                MetricItem(
                    iconRes = R.drawable.ic_altitude_min,
                    value = formatters.altitude.format_with_units(minAlt)
                )
                VerticalClimbDivider()
                MetricItem(
                    iconRes = R.drawable.ic_altitude_max,
                    value = formatters.altitude.format_with_units(maxAlt)
                )
            }
        }
    }
}

@Composable
private fun VerticalClimbDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .width(1.dp)
            .height(16.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
