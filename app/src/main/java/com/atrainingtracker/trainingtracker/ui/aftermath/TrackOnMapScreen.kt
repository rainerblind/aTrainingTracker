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

package com.atrainingtracker.trainingtracker.ui.aftermath

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeader
import com.atrainingtracker.trainingtracker.ui.map.*

@Composable
fun TrackOnMapScreen(
    workoutData: WorkoutData,
    modifier: Modifier = Modifier,
    tracks: List<MapTrack> = emptyList(),
    availableTrackTypes: Set<TrackType> = setOf(TrackType.BEST),
    segments: List<MapSegment> = emptyList(),
    routes: List<MapRoute> = emptyList(),
    markers: List<LocationMarker> = emptyList(),
    enabledTrackTypes: Set<TrackType> = setOf(TrackType.BEST),
    onToggleTrackType: (TrackType) -> Unit = {},
    showTechnicalTracks: Boolean = false,
    useStatusBarsPadding: Boolean = true,
    showMap: Boolean = true,
    headerActions: @Composable RowScope.() -> Unit = {}
) {
    // PERFORMANCE: Memoize the filtered tracks list
    val filteredTracks = remember(tracks, enabledTrackTypes) {
        tracks.filter { it.type in enabledTrackTypes }
    }

    MapDetailLayout(
        bSportType = workoutData.bSportType,
        zoomFocus = MapZoomFocus.FIT_PRIMARY,
        activeScrubPath = tracks.find { it.type == TrackType.BEST }?.path ?: tracks.firstOrNull()?.path,
        minAltitudeOverride = workoutData.minAltitude,
        maxAltitudeOverride = workoutData.maxAltitude,
        useStatusBarsPadding = useStatusBarsPadding,
        showMap = showMap,
        header = {
            WorkoutHeader(
                modifier = modifier,
                data = workoutData.headerData,
                menuEnabled = false,
                onClicked = { },
                onExport = { },
                onSaveAsRoute = { },
                onDeleteRequest = { },
                actions = headerActions
            )
        },
        mapContent = {
            tracks(filteredTracks)
            contextualPaths(segments)
            contextualPaths(routes)
            markers(markers)
        },
        modifier = modifier,
        overlay = {
            if (showTechnicalTracks) {
                // Technical Track Selection FAB (Top-Right, but below the share button handled by layout if any)
                // Actually MapDetailLayout aligns its share button TopEnd with 16dp padding.
                // We'll place ours just below it or to the left.
                // Let's use a Column to stack them if needed, or just let them overlap if they are in different corners.
                // MapDetailLayout aligns SHARE to TopEnd.
                
                var showTrackMenu by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 76.dp, end = 16.dp) // Offset vertically to not overlap share button
                ) {
                    Surface(
                        onClick = { showTrackMenu = true },
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = TTAlpha.Overlay),
                        shadowElevation = 6.dp,
                        tonalElevation = 2.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = stringResource(R.string.track_layers),
                                modifier = Modifier.size(22.dp),
                                tint = if (enabledTrackTypes.size > 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = TTAlpha.Disabled)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showTrackMenu,
                        onDismissRequest = { showTrackMenu = false }
                    ) {
                        TrackType.entries.forEach { type ->
                            val isAvailable = type in availableTrackTypes
                            
                            DropdownMenuItem(
                                enabled = isAvailable,
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.alpha(if (isAvailable) TTAlpha.High else TTAlpha.Disabled)
                                    ) {
                                        Checkbox(
                                            checked = enabledTrackTypes.contains(type),
                                            onCheckedChange = null,
                                            enabled = isAvailable
                                        )
                                        // THE LEGEND: Small color square
                                        Surface(
                                            modifier = Modifier.size(12.dp),
                                            color = type.color,
                                            shape = RoundedCornerShape(2.dp)
                                        ) {}
                                        Text(
                                            text = getTrackTypeName(type),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                },
                                onClick = {
                                    onToggleTrackType(type)
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun getTrackTypeName(type: TrackType): String {
    return when (type) {
        TrackType.BEST -> stringResource(R.string.track_type_best)
        TrackType.GPS -> stringResource(R.string.track_type_gps)
        TrackType.FUSED -> stringResource(R.string.track_type_fused)
        TrackType.NETWORK -> stringResource(R.string.track_type_network)
    }
}
