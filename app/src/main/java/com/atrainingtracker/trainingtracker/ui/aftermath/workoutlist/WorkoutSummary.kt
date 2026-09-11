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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.components.workoutlaps.LapEditBottomSheet
import kotlinx.coroutines.launch
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatus
import com.atrainingtracker.trainingtracker.ui.components.MappableListItem
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.WorkoutDescription
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetails
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.WorkoutExtrema
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeader
import com.atrainingtracker.trainingtracker.ui.components.workoutlaps.WorkoutLaps
import com.atrainingtracker.trainingtracker.ui.components.strava.StravaActivitySection
import com.atrainingtracker.trainingtracker.ui.map.ElevationProfile
import com.atrainingtracker.trainingtracker.ui.map.PathPreviewMap
import com.atrainingtracker.trainingtracker.ui.map.toMapTrack
import com.atrainingtracker.trainingtracker.ui.map.TrackType

/**
 * A comprehensive summary of a workout.
 * Note: This does NOT include a scroll modifier so it can be used
 * inside a LazyColumn/LazyVerticalChain in the future.
 */
@Composable
fun WorkoutSummary(
    workoutData: WorkoutData,
    isPlayServiceAvailable: Boolean,
    onExport: (FileFormat) -> Unit,
    onSaveAsRoute: () -> Unit,
    onDeleteRequest: () -> Unit,
    onEditWorkout: () -> Unit,
    onMapClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClusterClick: ((Long) -> Unit)? = null
) {
    // When the workout is not yet finished (properly), we show it with an alpha of 0.5
    val contentAlpha = if (workoutData.headerData.finished) TTAlpha.High else 0.5f

    var activeEditingLap by remember { mutableStateOf<LapData?>(null) }

    // Shared modifier for the clickable body sections (navigates to map view, ATT-850)
    val mapClickModifier = Modifier.clickable {
        if (workoutData.headerData.finished) {
            onMapClick()
        }
    }
    // TODO: Add functionality to show more detailed stats when clicking on the WorkoutDetails or Extrema Values.

    MappableListItem(
        alpha = contentAlpha,
        modifier = modifier
    ) {
        // 1. Header
        WorkoutHeader(
            data = workoutData.headerData,
            onClicked = onMapClick,
            onExport = onExport,
            onSaveAsRoute = onSaveAsRoute,
            onDeleteRequest = onDeleteRequest,
            menuEnabled = workoutData.headerData.finished,
            onClusterClick = onClusterClick,
            onEditWorkout = onEditWorkout
        )

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // 2. Description Section (Notes, Goals, Method)
        // Hidden automatically if all fields are null/blank
        WorkoutDescription(
            data = workoutData.descriptionData,
            modifier = mapClickModifier
        )

        // 3. Main Details Section (Distance, Time, Speed/Pace)
        WorkoutDetails(
            data = workoutData.detailsData,
            modifier = mapClickModifier
        )

        // 4. Extrema Values Section
        if (workoutData.extremaData.dataRows.isNotEmpty()) {
            WorkoutExtrema(
                data = workoutData.extremaData,
                modifier = mapClickModifier
            )
        }

        // ATT-510: Laps Overview Section
        if (workoutData.laps.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            WorkoutLaps(
                laps = workoutData.laps,
                bSportType = workoutData.bSportType,
                onLapClick = { lap -> activeEditingLap = lap }
            )
        }

        // 5. Strava Activity Data Section
        if (!workoutData.stravaActivityData.isNullOrBlank()) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            StravaActivitySection(
                rawActivityJson = workoutData.stravaActivityData
            )
        }

        if (isPlayServiceAvailable && workoutData.mapPolyline != "") {
            WorkoutMediaSection(
                // workoutId = workoutData.id,
                workoutData = workoutData,
                onMapClick = onMapClick
            )
        }

        // 6. Export Status Section
        ExportStatus(
            exportStatuses = workoutData.exportStatuses
        )

        // Final spacing at the bottom of the summary
        Spacer(modifier = Modifier.height(12.dp))
    }

    activeEditingLap?.let { lap ->
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        LapEditBottomSheet(
            workoutData = workoutData,
            initialLapNr = lap.lapNr,
            isPlayServiceAvailable = isPlayServiceAvailable,
            onDismissRequest = { activeEditingLap = null },
            onSaveLap = { lapNr, name, description ->
                coroutineScope.launch {
                    WorkoutRepository.getInstance(context.applicationContext as Application)
                        .updateLapDetails(workoutData.id, lapNr, name, description)
                }
            }
        )
    }
}

/**
 * Replicates the logic from SummaryViewHolder.kt init block:
 * A Map with an Elevation Profile overlaid at the bottom.
 */
@Composable
private fun WorkoutMediaSection(
    workoutData: WorkoutData,
    onMapClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp) // Total height for map + profile area
    ) {
        // 1. The Map (Weight 1 lets it take remaining space above profile)
        PathPreviewMap(
            path = workoutData.toMapTrack(),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            start = workoutData.startLatLng,
            end = workoutData.endLatLng,
            apex = workoutData.maxDisplacementLatLng,
            onMapClick = { onMapClick() }
        )

        // 2. The Elevation Profile
        ElevationProfile(
            // pathPoints = points,
            encodedAltitudes = workoutData.encodedAltitudes,
            encodedDistances = workoutData.encodedDistances,
            currentDistance = null,
            minAltitudeOverride = workoutData.minAltitude,
            maxAltitudeOverride = workoutData.maxAltitude,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onMapClick() }
        )
    }
}