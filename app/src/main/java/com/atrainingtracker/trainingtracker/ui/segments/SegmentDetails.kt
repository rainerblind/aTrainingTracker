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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.segments.SegmentSummary
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

@Composable
fun SegmentDetails(
    summary: SegmentSummary,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // --- ROW 1: Distance ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatItem(R.drawable.ic_distance, summary.distance)
        }

        // --- ROW 2: Grades (Avg and Max) ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatItem(R.drawable.ic_grade, summary.averageGrade)
            VerticalDivider()
            Text(
                text = summary.maxGrade,
                style = MaterialTheme.typography.titleMedium
            )
        }

        // --- ROW 3: Elevations (Gain, Min, Max) ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_altitude),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))

            StatItem(R.drawable.ic_ascent, summary.elevationGain)
            VerticalDivider()
            StatItem(R.drawable.ic_altitude_min, summary.elevationMin)
            VerticalDivider()
            StatItem(R.drawable.ic_altitude_max, summary.elevationMax)
        }
    }
}

@Composable
private fun StatItem(iconRes: Int, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .width(1.dp)
            .height(16.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Preview(showBackground = true, name = "Segment Details")
@Composable
fun PreviewSegmentDetails() {
    ATrainingTrackerTheme {
        SegmentDetails(
            summary = SegmentSummary(
                stravaId = 0,
                name = "Sample",
                bSportType = BSportType.BIKE,
                climbCategory_raw = 0,
                climbCategory = "",
                prTime_raw = 0,
                prTime = "",
                city = "",
                distance = "12.5 km",
                distance_raw = 12500.0,
                averageGrade_raw = 5.0,
                averageGrade = "5.0%",
                maxGrade = "8.2%",
                elevationGain_raw = 450.0,
                elevationGain = "450m",
                elevationMin = "100m",
                elevationMax = "550m",
                map_polyline = ""
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}
