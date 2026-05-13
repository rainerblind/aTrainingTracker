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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath

@Composable
fun RouteItem(
    route: RouteWithPath,
    onToggleSelection: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val forestGreen = Color(0xFF228B22)
    val paleForestGreen = Color(0xFF90EE90).copy(alpha = 0.2f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            // If selected, use a very light green tint
            containerColor = if (route.summary.isSelected) paleForestGreen
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = route.summary.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (route.summary.isSelected) forestGreen else Color.Unspecified
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = String.format("%.2f km", route.summary.distance / 1000.0),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = " • ",
                        modifier = Modifier.padding(horizontal = 4.dp),
                        color = Color.Gray
                    )
                    Text(
                        text = "${route.summary.elevationGain.toInt()} m",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    text = route.summary.source.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            // Visibility Toggle (Pinned to Map)
            Switch(
                checked = route.summary.isSelected,
                onCheckedChange = onToggleSelection,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = forestGreen,
                    checkedTrackColor = forestGreen.copy(alpha = 0.5f)
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Route",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Selected Route")
@Composable
fun PreviewSelectedRoute() {
    val mockRoute = RouteWithPath(
        summary = RouteSummary(
            id = 1,
            externalId = "sample_1",
            name = "Sunday Morning Forest Ride",
            distance = 45200.0, // 45.2 km
            elevationGain = 850.0,
            bSportType = BSportType.BIKE,
            source = RouteSource.STRAVA,
            isSelected = true
        ),
        path = emptyList() // Path points aren't visible in the Item UI
    )

    Box(modifier = Modifier.padding(8.dp)) {
        RouteItem(
            route = mockRoute,
            onToggleSelection = {},
            onDelete = {}
        )
    }
}

@Preview(showBackground = true, name = "Unselected Route")
@Composable
fun PreviewUnselectedRoute() {
    val mockRoute = RouteWithPath(
        summary = RouteSummary(
            id = 2,
            externalId = "sample_2",
            name = "City Park Loop",
            distance = 5400.0, // 5.4 km
            elevationGain = 25.0,
            bSportType = BSportType.RUN,
            source = RouteSource.LOCAL_GPX,
            isSelected = false
        ),
        path = emptyList()
    )

    Box(modifier = Modifier.padding(8.dp)) {
        RouteItem(
            route = mockRoute,
            onToggleSelection = {},
            onDelete = {}
        )
    }
}