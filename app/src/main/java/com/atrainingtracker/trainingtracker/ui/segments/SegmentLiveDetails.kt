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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.segments.LiveSegmentData
import com.atrainingtracker.trainingtracker.segments.LiveSegmentStatus
import com.atrainingtracker.trainingtracker.segments.LiveSegmentsRepository
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.Zone3
import com.atrainingtracker.trainingtracker.ui.theme.Zone4

@Composable
fun SegmentLiveDetails(
    liveSegmentData: LiveSegmentData,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Distance Progress
            Column {
                Text(
                    text = if (liveSegmentData.segmentStatus == LiveSegmentStatus.APPROACHING) {
                        stringResource(id = R.string.segment_status_start_in, liveSegmentData.distanceToStart)
                    } else {
                        liveSegmentData.distanceOnSegment
                    },
                    style = if (liveSegmentData.segmentStatus != LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(id = R.string.segment_status_remaining, liveSegmentData.remainingDistance),
                    style = if (liveSegmentData.segmentStatus == LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(id = R.string.segment_status_offset, liveSegmentData.segmentOffset),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (liveSegmentData.segmentOffset_raw < LiveSegmentsRepository.SEGMENT_DISTANCE_THRESHOLD * 0.5f) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else if (liveSegmentData.segmentOffset_raw < LiveSegmentsRepository.SEGMENT_DISTANCE_THRESHOLD * 0.75f) {
                        Zone3
                    } else {
                        Zone4
                    }
                )
            }

            // Right: Live Time
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = liveSegmentData.timeOnSegment,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Live Segment Details")
@Composable
fun PreviewSegmentLiveDetails() {
    ATrainingTrackerTheme {
        SegmentLiveDetails(
            liveSegmentData = LiveSegmentData(
                segmentStatus = LiveSegmentStatus.ON_SEGMENT,
                timeOnSegment = "12:34",
                distanceOnSegment = "3.2 km",
                remainingDistance = "1.8 km",
                segmentOffset = "+5m",
                segmentOffset_raw = 5.0
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}
