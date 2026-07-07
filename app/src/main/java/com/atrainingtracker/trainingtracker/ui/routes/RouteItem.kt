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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.ui.components.DeleteConfirmationDialog
import com.atrainingtracker.trainingtracker.ui.components.MappableListItem
import com.atrainingtracker.trainingtracker.ui.map.ElevationProfile
import com.atrainingtracker.trainingtracker.ui.map.MapRoute
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.PathPreviewMap
import com.atrainingtracker.trainingtracker.ui.theme.TTColor

@Composable
fun RouteItem(
    summary: RouteSummary,
    pathPoints: List<PathPoint>,
    onMapClick: (Long) -> Unit,
    onHeaderClick: (Long) -> Unit,
    onToggleSelection: (Long, Boolean) -> Unit,
    onDeleteConfirmed: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showContextMenu by remember { mutableStateOf(false) }
    var confirmDeletion by remember { mutableStateOf(false) }

    MappableListItem(
        modifier = modifier,
        onClick = { onMapClick(summary.id) }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. TOP: Route Summary Header (Title, Source, Metrics, Sport Icon, Switch)
            RouteSummaryHeader(
                summary = summary,
                onToggleSelection = { onToggleSelection(summary.id, it) },
                switchScale = 0.6f,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onHeaderClick(summary.id) },
                        onLongClick = { showContextMenu = true }
                    )
            )

            // 2. MIDDLE: Map Preview
            // We use height(200.dp) to give the route map more prominence than the small segment square
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                PathPreviewMap(
                    path = MapRoute(
                        id = summary.id,
                        name = summary.name,
                        isSelected = summary.isSelected,
                        bSportType = summary.bSportType,
                        path = pathPoints
                    ),
                    modifier = Modifier.fillMaxSize(),
                    onMapClick = { onMapClick(summary.id) }
                )
            }

            // 3. BOTTOM: Elevation Profile
            ElevationProfile(
                pathPoints = pathPoints,
                currentDistance = null, // No seeker in list view
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMapClick(summary.id) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // Context Menu for deletion
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                onClick = { showContextMenu = false; confirmDeletion = true },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
            )
        }

        // Delete Confirmation Dialog
        if (confirmDeletion) {
            DeleteConfirmationDialog(
                title = stringResource(R.string.delete),
                message = stringResource(R.string.really_delete_format, summary.name),
                onConfirm = { onDeleteConfirmed(summary.id) },
                onDismiss = { confirmDeletion = false }
            )
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
            description = "Long Epic Stage 1",
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
            onMapClick = {},
            onHeaderClick = {},
            onToggleSelection = { _, _ -> },
            onDeleteConfirmed = {}
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
        description = "",
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
            onMapClick = {},
            onHeaderClick = {},
            onToggleSelection = {} as (Long, Boolean) -> Unit,
            onDeleteConfirmed = {}
        )
    }
}