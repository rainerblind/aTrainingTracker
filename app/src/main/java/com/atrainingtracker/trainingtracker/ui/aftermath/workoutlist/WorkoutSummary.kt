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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusGroup
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusGroupData
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.DescriptionData
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.WorkoutDescription
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.*
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaData
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.WorkoutExtrema
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeader
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderData

/**
 * A comprehensive summary of a workout.
 * Note: This does NOT include a scroll modifier so it can be used
 * inside a LazyColumn/LazyVerticalChain in the future.
 */
@Composable
fun WorkoutSummary(
    headerData: WorkoutHeaderData,
    descriptionData: DescriptionData,
    detailsData: WorkoutDetailsData,
    extremaData: ExtremaData,
    exportStatuses: List<ExportStatusGroupData>,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {

        // 1. Header (Blue Scrim Section)
        WorkoutHeader(
            data = headerData,
            onMenuClick = onMenuClick
        )

        // 2. Description Section (Notes, Goals, Method)
        // Hidden automatically if all fields are null/blank
        WorkoutDescription(data = descriptionData)

        // 3. Main Details Section (Distance, Time, Speed/Pace)
        WorkoutDetails(data = detailsData)

        // 4. Extrema Values Section
        // Show a subtle divider if extrema data exists
        if (extremaData.dataRows.isNotEmpty() || extremaData.isCalculating) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            WorkoutExtrema(data = extremaData)
        }

        // TODO: add ATrainingTrackerMap
        // TODO: add Elevation Profile

        // 5. Export Status Section
        val activeExports = exportStatuses.filter { it.hasContent }
        if (activeExports.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activeExports.forEach { statusGroup ->
                    ExportStatusGroup(
                        data = statusGroup
                    )
                }
            }
        }

        // Final spacing at the bottom of the summary
        Spacer(modifier = Modifier.height(16.dp))
    }
}