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

package com.atrainingtracker.trainingtracker.ui.segments

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.segments.LiveSegmentData
import com.atrainingtracker.trainingtracker.segments.LiveSegmentStatus
import com.atrainingtracker.trainingtracker.segments.SegmentSummary
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

@Composable
fun SegmentSummaryHeader(
    summary: SegmentSummary,
    compact: Boolean = false,
    liveSegmentData: LiveSegmentData? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 1. Common Header
        SegmentHeader(
            summary = summary,
            compact = compact,
            liveSegmentStatus = liveSegmentData?.segmentStatus
        )

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // 2. Specific Data (Static Details OR Live Metrics)
        if (liveSegmentData == null) {
            SegmentDetails(
                summary = summary,
                compact = compact
            )
        } else {
            SegmentLiveDetails(
                liveSegmentData = liveSegmentData
            )
        }
    }
}

@Preview(showBackground = true, name = "Full Info")
@Composable
fun PreviewSegmentSummaryFull() {
    ATrainingTrackerTheme {
        SegmentSummaryHeader(
            summary = SegmentSummary(
                stravaId = 12345L,
                name = "Alpe d'Huez Climb",
                bSportType = BSportType.BIKE,
                climbCategory_raw = 5,
                climbCategory = "HC",
                prTime_raw = 45 * 60 + 20,
                prTime = "45:20",
                city = "Bourg d'Oisans",
                distance = "13.80 km",
                distance_raw = 13800.0,
                averageGrade_raw = 8.1,
                averageGrade = "Ø 8.1%",
                maxGrade = "12.0% Max",
                elevationGain_raw = 1073.0,
                elevationGain = "1073 m",
                elevationMin = "720 m",
                elevationMax = "1793 m",
                map_polyline = ""
            )
        )
    }
}

@Preview(showBackground = true, name = "Live Effort View (On Segment)")
@Composable
fun PreviewSegmentSummaryLiveOnSegment() {
    ATrainingTrackerTheme {
        SegmentSummaryHeader(
            summary = SegmentSummary(
                stravaId = 12345L,
                name = "Alpe d'Huez Climb",
                bSportType = BSportType.BIKE,
                climbCategory_raw = 5,
                climbCategory = "HC",
                prTime_raw = 2720,
                prTime = "45:20",
                city = "Bourg d'Oisans",
                distance = "13.80 km",
                distance_raw = 13800.0,
                averageGrade_raw = 8.1,
                averageGrade = "Ø 8.1%",
                maxGrade = "12.0% Max",
                elevationGain_raw = 1073.1,
                elevationGain = "1073 m",
                elevationMin = "720 m",
                elevationMax = "1793 m",
                map_polyline = ""
            ),
            liveSegmentData = LiveSegmentData(
                segmentStatus = LiveSegmentStatus.ON_SEGMENT,
                timeOnSegment = "2:45",
                distanceOnSegment = "1.20 km",
                remainingDistance = "12.80 m",
                segmentOffset = "40 m",
                segmentOffset_raw = 40.0
            )
        )
    }
}
