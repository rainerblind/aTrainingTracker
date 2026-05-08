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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatus
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.WorkoutDescription
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.*
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.WorkoutExtrema
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeader
import com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMap
import com.atrainingtracker.trainingtracker.ui.map.ElevationProfile
import com.atrainingtracker.trainingtracker.ui.map.MapState
import com.atrainingtracker.trainingtracker.ui.map.MapTrack
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.TrackOrSegmentOnMap
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import kotlinx.coroutines.flow.MutableStateFlow

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
    onDeleteConfirmed: () -> Unit,
    onEditWorkout: () -> Unit,
    onMapClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // When the workout is not yet finished (properly), we show it with an alpha of 0.5
    val contentAlpha = if (workoutData.headerData.finished) 1.0f else 0.5f

    // Shared modifier for the clickable sections
    val editWorkoutModifier = Modifier.clickable {
        if (workoutData.headerData.finished) {
            onEditWorkout()
        }
    }
    // TODO: Add functionality to show more detailed stats when clicking on the WorkoutDetails or Extrema Values.

    ElevatedCard(
        modifier = modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            // Apply the alpha to the entire summary container
            .graphicsLayer(alpha = contentAlpha),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface // This is your Color.White
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {

        // 1. Header (Blue Scrim Section)
        WorkoutHeader(
            data = workoutData.headerData,
            onClicked = onEditWorkout,
            onExport = onExport,
            onDeleteConfirmed = onDeleteConfirmed,
            menuEnabled = workoutData.headerData.finished
        )

        // 2. Description Section (Notes, Goals, Method)
        // Hidden automatically if all fields are null/blank
        WorkoutDescription(
            data = workoutData.descriptionData,
            modifier = editWorkoutModifier
        )

        // 3. Main Details Section (Distance, Time, Speed/Pace)
        WorkoutDetails(
            data = workoutData.detailsData,
            modifier = editWorkoutModifier
        )

        // 4. Extrema Values Section
        // Show a subtle divider if extrema data exists
        if (workoutData.extremaData.dataRows.isNotEmpty() || workoutData.extremaData.isCalculating) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            WorkoutExtrema(data = workoutData.extremaData,
                modifier = editWorkoutModifier
            )
        }

        if (isPlayServiceAvailable && workoutData.map_polyline != "") {
            WorkoutMediaSection(
                // workoutId = workoutData.id,
                workoutData = workoutData,
                onMapClick = onMapClick
            )
        }

        // 5. Export Status Section
        ExportStatus(
            exportStatuses = workoutData.exportStatuses
        )

        // Final spacing at the bottom of the summary
        Spacer(modifier = Modifier.height(16.dp))
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
        TrackOrSegmentOnMap(
            polyline = workoutData.map_polyline,
            color = TrackType.BEST.color,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            onMapClick = { onMapClick() }
        )

        // 2. The Elevation Profile
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clickable { onMapClick() }
        ) {
            ElevationProfile(
                // pathPoints = points,
                encodedAltitudes = workoutData.encodedAltitudes,
                encodedDistances = workoutData.encodedDistances,
                currentDistance = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}