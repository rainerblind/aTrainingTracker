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
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.components.strava.PoweredByStrava
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

@Composable
fun SegmentDetails(
    summary: SegmentSummary,
    modifier: Modifier = Modifier,
    showStravaLogo: Boolean = true
) {
    val formatters = com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // --- ROW 1: Distance and Optional Branding ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MetricItem(
                iconRes = R.drawable.ic_distance,
                value = formatters.distance.format_with_units(summary.distance_raw),
                isPrimary = true
            )
            if (showStravaLogo) {
                PoweredByStrava(height = 24.dp)
            }
        }

        // --- ROW 2: Grades (Avg and Max) ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetricItem(
                iconRes = R.drawable.ic_grade,
                value = summary.averageGrade,
                isPrimary = true
            )
            VerticalDivider()
            Text(
                text = summary.maxGrade,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // --- ROW 3: Elevations (Altitude Icon + Gain/Min/Max Metrics) ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Section Identifier (Mirrors Workout Summary lead icon)
            Icon(
                painter = painterResource(id = R.drawable.ic_altitude),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))

            MetricItem(
                iconRes = R.drawable.ic_ascent,
                value = formatters.altitude.format_with_units(summary.elevationGain_raw),
                isPrimary = true
            )
            VerticalDivider()
            MetricItem(
                iconRes = R.drawable.ic_altitude_min,
                value = summary.elevationMin
            )
            VerticalDivider()
            MetricItem(
                iconRes = R.drawable.ic_altitude_max,
                value = summary.elevationMax
            )
        }
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
