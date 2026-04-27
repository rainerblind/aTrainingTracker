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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.segments.SegmentSummary
import com.atrainingtracker.trainingtracker.ui.map.ElevationProfile
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.segments.SegmentSummaryHeader

@Composable
fun SegmentItem(
    summary: SegmentSummary,
    pathPoints: List<PathPoint>,
    onSegmentClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        onClick = { onSegmentClick(summary.stravaId) }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // TOP PART: Row with Map and Summary Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min) // Balance Map and Header height
            ) {
                // 1. Static Segment Map (120dp square)
                SegmentOnMap(
                    pathPoints = pathPoints,
                    modifier = Modifier.size(120.dp),
                    onMapClick = { onSegmentClick(summary.stravaId) }
                )

                // 2. Summary Header
                SegmentSummaryHeader(
                    summary = summary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
            }

            // BOTTOM PART: Elevation Profile
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(vertical = 4.dp)
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
