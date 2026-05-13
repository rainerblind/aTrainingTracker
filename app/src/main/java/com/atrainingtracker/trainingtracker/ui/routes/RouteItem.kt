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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.ui.map.ElevationProfile
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.TrackOrSegmentOnMap
import com.atrainingtracker.trainingtracker.ui.theme.RouteColorSelected
import com.atrainingtracker.trainingtracker.ui.theme.RouteColorUnselected

@Composable
fun RouteItem(
    summary: RouteSummary,
    pathPoints: List<PathPoint>,
    // TODO: onHeaderClick: (Long) -> Unit,
    onRouteClick: (Long) -> Unit,
    onToggleSelection: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        onClick = { onRouteClick(summary.id) }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. TOP: Route Summary Header (Title, Source, Metrics, Sport Icon, Switch)
            RouteSummaryHeader(
                summary = summary,
                onToggleSelection = onToggleSelection,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )

            // 2. MIDDLE: Map Preview
            // We use height(200.dp) to give the route map more prominence than the small segment square
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                TrackOrSegmentOnMap(
                    latLngs = pathPoints.map { it.latLng },
                    color = if (summary.isSelected) RouteColorSelected else RouteColorUnselected,
                    modifier = Modifier.fillMaxSize(),
                    onMapClick = { onRouteClick(summary.id) }
                )
            }

            // 3. BOTTOM: Elevation Profile
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                ElevationProfile(
                    pathPoints = pathPoints,
                    currentDistance = null, // No seeker in list view
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Selected Route")
@Composable
fun PreviewSelectedRoute() {
    val mockSummary = RouteSummary(
            id = 1,
            externalId = "sample_1",
            name = "Sunday Morning Forest Ride",
            distance = 45200.0, // 45.2 km
            elevationGain = 850.0,
            bSportType = BSportType.BIKE,
            source = RouteSource.STRAVA,
            isSelected = true
        )

    Box(modifier = Modifier.padding(8.dp)) {
        RouteItem(
            summary = mockSummary,
            pathPoints = emptyList(),
            onRouteClick = {},
            onToggleSelection = {},
        )
    }
}

@Preview(showBackground = true, name = "Unselected Route")
@Composable
fun PreviewUnselectedRoute() {
    val mockSummary = RouteSummary(
            id = 2,
            externalId = "sample_2",
            name = "City Park Loop",
            distance = 5400.0, // 5.4 km
            elevationGain = 25.0,
            bSportType = BSportType.RUN,
            source = RouteSource.LOCAL_GPX,
            isSelected = false
        )

    Box(modifier = Modifier.padding(8.dp)) {
        RouteItem(
            summary = mockSummary,
            pathPoints = emptyList(),
            onRouteClick = {},
            onToggleSelection = {},
        )
    }
}