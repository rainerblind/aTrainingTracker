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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.helpers.combineWorkoutAndShare
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMap
import com.atrainingtracker.trainingtracker.ui.map.ElevationProfile
import com.atrainingtracker.trainingtracker.ui.map.MapSegment
import com.atrainingtracker.trainingtracker.ui.map.MapRoute
import com.atrainingtracker.trainingtracker.ui.map.MapZoomFocus
import com.atrainingtracker.trainingtracker.ui.map.MapDetailLayout
import com.atrainingtracker.trainingtracker.ui.map.MappablePath
import com.atrainingtracker.trainingtracker.ui.map.createSensorMarker
import com.atrainingtracker.trainingtracker.ui.map.LocationMarker
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.ui.climbs.ClimbCategoryChip
import com.atrainingtracker.trainingtracker.ui.climbs.getClimbCategoryColors
import kotlin.math.roundToInt

@Composable
fun RouteOnMapScreen(
    route: MapRoute?,
    routeSummary: RouteSummary?,
    backgroundPaths: List<MappablePath> = emptyList(),
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

    val climbMarkers = remember(climbs) {
        climbs.map { climb ->
            val (bgColor, _, _) = getClimbCategoryColors(climb.category)
            createSensorMarker(context, R.drawable.ic_ascent, bgColor)
        }
    }

    val routeBounds = remember(routeSummary) {
        if (routeSummary?.minLat != null && routeSummary.maxLat != null && routeSummary.minLng != null && routeSummary.maxLng != null) {
            com.google.android.gms.maps.model.LatLngBounds(
                LatLng(routeSummary.minLat, routeSummary.minLng),
                LatLng(routeSummary.maxLat, routeSummary.maxLng)
            )
        } else null
    }

    MapDetailLayout(
        bSportType = bSportType,
        zoomFocus = if (routeBounds != null) MapZoomFocus.EXPLICIT_BOUNDS else MapZoomFocus.FIT_PRIMARY,
        initialBounds = routeBounds,
        activeScrubPath = route?.path,
        useStatusBarsPadding = useStatusBarsPadding,
        showMap = showMap,
        climbs = climbs,
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
        analyticsContent = if (climbs.isNotEmpty()) {
            {
                RouteClimbsBreakdownSection(climbs = climbs)
            }
        } else null,
        mapContent = {
            if (route != null) {
                routes(listOf(route))
                
                // Add unified Start and End markers (SCRUM-185)
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

                // Add climb start markers (REQ-UI-274)
                climbs.forEachIndexed { idx, climb ->
                    val descriptor = climbMarkers.getOrNull(idx)
                    if (descriptor != null) {
                        allMarkers.add(
                            LocationMarker(
                                position = climb.startLatLng,
                                iconResId = R.drawable.ic_ascent,
                                title = climb.name,
                                iconDescriptor = descriptor
                            )
                        )
                    }
                }

                if (allMarkers.isNotEmpty()) {
                    markers(allMarkers)
                }
            }
            contextualPaths(backgroundPaths, sameSportAlpha = TTAlpha.Medium)
        },
        modifier = modifier
    )
}

@Composable
fun RouteClimbsBreakdownSection(
    climbs: List<Climb>,
    modifier: Modifier = Modifier
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ClimbCategoryChip(category = climb.category)
                            Text(
                                text = climb.name.ifBlank { stringResource(R.string.climb_route_counter, index + 1, climbs.size) },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
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
