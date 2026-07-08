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

package com.atrainingtracker.trainingtracker.ui.segments.segmentlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.segments.SegmentSummary
import com.atrainingtracker.trainingtracker.ui.components.MappableListItem
import com.atrainingtracker.trainingtracker.ui.map.ElevationProfile
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.segments.SegmentHeader
import com.atrainingtracker.trainingtracker.ui.segments.SegmentDetails
import com.atrainingtracker.trainingtracker.ui.theme.TTColor

@Composable
fun SegmentItem(
    summary: SegmentSummary,
    pathPoints: List<PathPoint>,
    onSegmentClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    MappableListItem(
        modifier = modifier,
        onClick = { onSegmentClick(summary.stravaId) }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. TOP: Segment Identity (Full Width)
            SegmentHeader(
                summary = summary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            )

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // 2. Performance Metrics (Full Width)
            SegmentDetails(
                summary = summary,
                showStravaLogo = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            )

            // 3. BOTTOM: Elevation Profile (Full Width)
            ElevationProfile(
                pathPoints = pathPoints,
                currentDistance = null, // No seeker in list view
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }
    }
}
