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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeader
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneDistributionCard
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneDistributionCard
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionData
import com.atrainingtracker.trainingtracker.ui.aftermath.splits.LapSplitCalculator
import com.atrainingtracker.trainingtracker.ui.aftermath.splits.LapSplitVisualizerCard
import com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter
import com.atrainingtracker.trainingtracker.ui.map.*
import androidx.compose.ui.platform.LocalContext
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.WorkoutDescription
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.WorkoutExtrema
import com.atrainingtracker.trainingtracker.ui.components.strava.StravaActivitySection

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
    onClusterClick: ((Long) -> Unit)? = null,
    onEditWorkout: ((Long) -> Unit)? = null,
    headerActions: @Composable RowScope.() -> Unit = {},
    hrZoneDistribution: ZoneDistributionData? = null,
    powerZoneDistribution: ZoneDistributionData? = null,
    analyticsContent: (@Composable ColumnScope.() -> Unit)? = null,
    metadataContent: (@Composable ColumnScope.() -> Unit)? = null,
    telemetryPath: List<PathPoint> = emptyList(),
    detailPreferences: WorkoutDetailPreferences? = null
) {
    val context = LocalContext.current
    val preferenceManager = remember { MyPreferenceManager(context.applicationContext) }
    val persistedDetailPrefs by preferenceManager.workoutDetailPreferencesFlow.collectAsState(initial = WorkoutDetailPreferences())
    val activeDetailPrefs = detailPreferences ?: persistedDetailPrefs

    // PERFORMANCE: Memoize the filtered tracks list
    val filteredTracks = remember(tracks, enabledTrackTypes) {
        tracks.filter { it.type in enabledTrackTypes }
    }

    val hasGpsTrack = remember(tracks) {
        tracks.any { track ->
            track.path.isNotEmpty() && track.latLngs.any { it.latitude != 0.0 || it.longitude != 0.0 }
        }
    }

    var selectedLapNr by rememberSaveable { mutableStateOf<Long?>(null) }

    val formatters = LocalMetricFormatter.current
    val splitChartData = remember(workoutData.laps, workoutData.bSportType, formatters) {
        LapSplitCalculator.calculateSplitData(
            laps = workoutData.laps,
            bSportType = workoutData.bSportType,
            paceFormatter = { formatters.pace.format_with_units(it) },
            speedFormatter = { formatters.speed.format_with_units(it) }
        )
    }

    val bestTrack = remember(tracks) {
        tracks.find { it.type == TrackType.BEST } ?: tracks.firstOrNull()
    }
    val allPoints = remember(bestTrack) {
        bestTrack?.latLngs ?: emptyList()
    }
    val allDistances = remember(bestTrack) {
        bestTrack?.path?.map { it.distance } ?: emptyList()
    }

    val activeScrubPath = remember(hasGpsTrack, bestTrack, tracks, telemetryPath) {
        if (hasGpsTrack) {
            bestTrack?.path ?: tracks.firstOrNull()?.path
        } else {
            telemetryPath.ifEmpty { null }
        }
    }

    val (startDistM, endDistM) = remember(workoutData.laps, selectedLapNr) {
        if (selectedLapNr != null) {
            LapSegmentUtils.calculateLapDistanceRange(workoutData.laps, selectedLapNr!!)
        } else {
            0.0 to 0.0
        }
    }

    val lapSegment = remember(allPoints, allDistances, startDistM, endDistM, selectedLapNr) {
        if (selectedLapNr != null && allPoints.isNotEmpty() && allDistances.isNotEmpty()) {
            LapSegmentUtils.sliceLapSegment(allPoints, allDistances, startDistM, endDistM)
        } else {
            emptyList()
        }
    }

    MapDetailLayout(
        bSportType = workoutData.bSportType,
        zoomFocus = MapZoomFocus.FIT_PRIMARY,
        activeScrubPath = activeScrubPath,
        minAltitudeOverride = workoutData.minAltitude,
        maxAltitudeOverride = workoutData.maxAltitude,
        useStatusBarsPadding = useStatusBarsPadding,
        showMap = showMap && hasGpsTrack && activeDetailPrefs.showMap,
        showElevationProfile = hasGpsTrack && activeDetailPrefs.showElevationProfile && (workoutData.minAltitude != null || (activeScrubPath?.any { it.altitude != 0.0 } == true)),
        showTelemetryCharts = activeDetailPrefs.showTelemetryCharts,
        header = {
            WorkoutHeader(
                modifier = Modifier.fillMaxWidth(),
                data = workoutData.headerData,
                menuEnabled = false,
                canDelete = false,
                onClicked = onEditWorkout?.let { edit -> { edit(workoutData.id) } },
                onExport = { },
                onSaveAsRoute = { },
                onDeleteRequest = { },
                onClusterClick = onClusterClick,
                onEditWorkout = onEditWorkout?.let { edit -> { edit(workoutData.id) } },
                actions = {
                    headerActions()
                }
            )
        },
        mapContent = {
            tracks(filteredTracks)
            contextualPaths(segments)
            contextualPaths(routes)
            if (lapSegment.isNotEmpty()) {
                val lapMarkers = mutableListOf<LocationMarker>()
                lapSegment.firstOrNull()?.let { startPoint ->
                    lapMarkers.add(LocationMarker(position = startPoint, iconResId = R.drawable.control_start, title = "Lap Start"))
                }
                lapSegment.lastOrNull()?.let { endPoint ->
                    lapMarkers.add(LocationMarker(position = endPoint, iconResId = R.drawable.control_stop, title = "Lap End"))
                }
                markers(markers + lapMarkers)
                lapHighlight(lapSegment)
            } else {
                markers(markers)
            }
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
        },
        metadataContent = {
            if (metadataContent != null) {
                metadataContent()
            } else {
                if (activeDetailPrefs.showDescription) {
                    WorkoutDescription(
                        data = workoutData.descriptionData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                if (activeDetailPrefs.showExtrema && workoutData.extremaData.dataRows.isNotEmpty()) {
                    WorkoutExtrema(
                        data = workoutData.extremaData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        },
        analyticsContent = {
            if (analyticsContent != null) {
                analyticsContent()
            } else {
                if (activeDetailPrefs.showZoneAnalysis) {
                    hrZoneDistribution?.let { distribution ->
                        HeartRateZoneDistributionCard(
                            distribution = distribution,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    powerZoneDistribution?.let { distribution ->
                        PowerZoneDistributionCard(
                            distribution = distribution,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
                if (activeDetailPrefs.showLaps) {
                    splitChartData?.let { splits ->
                        LapSplitVisualizerCard(
                            splitData = splits,
                            selectedLapNr = selectedLapNr,
                            onLapClick = { tappedLapNr ->
                                selectedLapNr = if (selectedLapNr == tappedLapNr) null else tappedLapNr
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
                if (activeDetailPrefs.showStrava && !workoutData.stravaActivityData.isNullOrBlank()) {
                    StravaActivitySection(
                        rawActivityJson = workoutData.stravaActivityData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
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
