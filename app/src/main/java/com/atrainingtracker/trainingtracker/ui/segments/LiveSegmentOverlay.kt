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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.segments.LiveSegment
import com.atrainingtracker.trainingtracker.segments.LiveSegmentStatus
import com.atrainingtracker.trainingtracker.segments.SegmentSummary
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng

@Composable
fun LiveSegmentOverlay(
    liveSegments: List<LiveSegment>,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = liveSegments.isNotEmpty(),
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            liveSegments.forEach { segment ->
                LiveSegmentItem(segment)
            }
        }
    }
}

@Composable
private fun LiveSegmentItem(segment: LiveSegment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Segment Name
            Text(
                text = segment.summary.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            val df = DistanceFormatter()

            // Dynamic Info based on status
            when (segment.liveData.segmentStatus) {
                LiveSegmentStatus.APPROACHING -> {
                    val summary = "${segment.summary.distance} ${segment.summary.averageGrade}"
                    val formattedDistanceToStart = df.format_with_units(segment.liveData.distanceToStart)

                    Text(
                        text = "Start in $formattedDistanceToStart • $summary",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                LiveSegmentStatus.ON_SEGMENT, LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH -> {
                    val distDone = df.format_with_units(segment.liveData.distanceOnSegment)
                    val distOff = "${segment.liveData.distanceToSegment.toInt()}m off"

                    var subtitle = "$distDone • $distOff"

                    if (segment.liveData.segmentStatus == LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH) {
                        subtitle += " • Finish in ${segment.liveData.distanceToEnd.toInt()}m"
                    }

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (segment.liveData.distanceToSegment > 20) Color.Red else MaterialTheme.colorScheme.primary,
                        fontWeight = if (segment.liveData.distanceToSegment > 20) FontWeight.Bold else FontWeight.Normal
                    )
                }
                else -> {}
            }
        }

        // Timer Display (Only if active)
        if (segment.liveData.timeOnSegment >= 0) {
            val tf = TimeFormatter()
            Text(
                text = tf.format_with_units(segment.liveData.timeOnSegment),
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}