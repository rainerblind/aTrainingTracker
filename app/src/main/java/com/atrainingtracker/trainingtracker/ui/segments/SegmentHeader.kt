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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.segments.LiveSegmentStatus
import com.atrainingtracker.trainingtracker.segments.SegmentSummary
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

@Composable
fun SegmentHeader(
    summary: SegmentSummary,
    liveSegmentStatus: LiveSegmentStatus? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // --- TOP ROW: Sport Icon and Name ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Sport Icon
            Icon(
                painter = painterResource(id = summary.bSportType.iconResId),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = Color.Unspecified // Original color
            )

            // 2. Name
            Text(
                text = summary.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // 3. Category Chip (Right-aligned in top row)
            if (summary.climbCategory.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = summary.climbCategory,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // --- SECOND ROW: City / Status and PR ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (liveSegmentStatus == null) {
                Text(
                    text = summary.city,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = liveSegmentStatus.label(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (summary.prTime.isNotBlank() && summary.prTime != "--:--") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pr_time),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "PR ${summary.prTime}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun LiveSegmentStatus.label(): String {
    return stringResource(id = this.resId)
}

@Preview(showBackground = true, name = "Static Segment Header")
@Composable
fun PreviewSegmentHeader() {
    ATrainingTrackerTheme {
        SegmentHeader(
            summary = SegmentSummary(
                stravaId = 123L,
                name = "Col du Galibier",
                bSportType = BSportType.BIKE,
                climbCategory_raw = 5,
                climbCategory = "HC",
                prTime_raw = 3600,
                prTime = "1:00:00",
                city = "Valloire",
                distance = "18.1 km",
                distance_raw = 18100.0,
                averageGrade_raw = 6.9,
                averageGrade = "6.9%",
                maxGrade = "12%",
                elevationGain_raw = 1245.0,
                elevationGain = "1245m",
                elevationMin = "1401m",
                elevationMax = "2642m",
                map_polyline = ""
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, name = "Live Segment Header")
@Composable
fun PreviewSegmentHeaderLive() {
    ATrainingTrackerTheme {
        SegmentHeader(
            summary = SegmentSummary(
                stravaId = 123L,
                name = "Galibier (Live)",
                bSportType = BSportType.BIKE,
                climbCategory_raw = 5,
                climbCategory = "HC",
                prTime_raw = 3600,
                prTime = "1:00:00",
                city = "Valloire",
                distance = "18.1 km",
                distance_raw = 18100.0,
                averageGrade_raw = 6.9,
                averageGrade = "6.9%",
                maxGrade = "12%",
                elevationGain_raw = 1245.0,
                elevationGain = "1245m",
                elevationMin = "1401m",
                elevationMax = "2642m",
                map_polyline = ""
            ),
            liveSegmentStatus = LiveSegmentStatus.ON_SEGMENT,
            modifier = Modifier.padding(16.dp)
        )
    }
}
