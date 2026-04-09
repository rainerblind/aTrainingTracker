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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.segments.LiveSegment
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

@Composable
fun LiveSegmentOverlay(
    segments: List<LiveSegment>,
    modifier: Modifier = Modifier
) {
    // Only show if there are segments in proximity
    AnimatedVisibility(
        visible = segments.isNotEmpty(),
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            segments.forEach { segment ->
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
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = segment.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            val subtitle = if (segment.timeOnSegment >= 0) {
                // Currently on the segment
                val km = segment.distanceOnSegment / 1000.0
                "${String.format("%.2f", km)} km done"
            } else {
                // Approaching the segment
                "Start in ${segment.distanceToStart.toInt()}m"
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        // Timer Display (Only if active)
        if (segment.timeOnSegment >= 0) {
            val minutes = segment.timeOnSegment / 60
            val seconds = segment.timeOnSegment % 60
            Text(
                text = String.format("%02d:%02d", minutes, seconds),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Preview(showBackground = true, name = "Approaching Segment")
@Composable
fun PreviewApproaching() {
    ATrainingTrackerTheme {
        val segments = listOf(
            LiveSegment(
                stravaId = 1,
                name = "Kesselberg Climb",
                prTime = "12:30",
                distanceToStart = 145.0,
                timeOnSegment = -1
            )
        )
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            LiveSegmentOverlay(segments = segments)
        }
    }
}

@Preview(showBackground = true, name = "Active Segment")
@Composable
fun PreviewActive() {
    ATrainingTrackerTheme {
        val segments = listOf(
            LiveSegment(
                stravaId = 2,
                name = "Sector 4 Sprint",
                prTime = "01:15",
                distanceToStart = 0.0,
                timeOnSegment = 74,
                distanceOnSegment = 850.0
            )
        )
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            LiveSegmentOverlay(segments = segments)
        }
    }
}