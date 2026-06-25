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

package com.atrainingtracker.trainingtracker.ui.components.workoutdetails

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.AltitudeFormatter
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.components.MetricLayout


@Composable
fun WorkoutDetails(
    data: WorkoutDetailsData,
    modifier: Modifier = Modifier
) {
    val formatters = com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Metrics Row: Time and Distance ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Active Time
            MetricItem(
                iconRes = R.drawable.ic_time_active,
                label = stringResource(R.string.time_active),
                value = formatters.time.format(data.activeTimeSec),
                secondaryValue = stringResource(R.string.total_time_format, formatters.time.format(data.totalTimeSec)),
                layout = MetricLayout.VERTICAL,
                isPrimary = true,
                iconSize = 28.dp,
                modifier = Modifier.weight(1f)
            )

            // 2. Distance
            val maxDispString = if (data.maxDisplacement != null) {
                val maxDispFormatted = formatters.distance.format_with_units(data.maxDisplacement)
                stringResource(R.string.format_max_displacement, maxDispFormatted)
            } else null

            MetricItem(
                iconRes = R.drawable.ic_distance,
                label = stringResource(R.string.distance),
                value = formatters.distance.format_with_units(data.totalDistance),
                secondaryValue = maxDispString,
                layout = MetricLayout.VERTICAL,
                isPrimary = true,
                iconSize = 28.dp,
                modifier = Modifier.weight(1f)
            )
        }

        // --- Section 3: Altitude (Ascent and Descent) ---
        AltitudeRow(
            data.ascentMeters,
            data.descentMeters,
            modifier = Modifier
        )
    }
}

@Composable
private fun AltitudeRow(
    ascentMeters: Long,
    descentMeters: Long,
    modifier: Modifier = Modifier,
){
    val formatters = com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter.current

    if (ascentMeters > 0 || descentMeters > 0) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Icon(
                    painter = painterResource(id = R.drawable.ic_altitude),
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp), // Slightly smaller than Time/Distance (28dp)
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.altitude),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricItem(
                        iconRes = R.drawable.ic_ascent,
                        label = stringResource(R.string.ascent_short),
                        value = formatters.altitude.format_with_units(ascentMeters),
                        layout = MetricLayout.VERTICAL,
                        modifier = Modifier.weight(1f)
                    )
                    MetricItem(
                        iconRes = R.drawable.ic_descent,
                        label = stringResource(R.string.descent_short),
                        value = formatters.altitude.format_with_units(descentMeters),
                        layout = MetricLayout.VERTICAL,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}



@Preview
@Composable
fun PreviewDetailItem() {
    MaterialTheme {
        MetricItem(
            iconRes = R.drawable.ic_distance,
            label = "Distance",
            modifier = Modifier.fillMaxWidth(),
            value = "10,00 km",
            secondaryValue = "(Max. Luftlinie: 5,00 km)",
            layout = MetricLayout.VERTICAL
        )
    }
}

@Preview
@Composable
fun PreviewDistanceAndTime() {
    MaterialTheme {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Active Time
            MetricItem(
                iconRes = R.drawable.ic_time_active,
                label = "Active Time",
                value = "0:30:00",
                secondaryValue = "(Total: 0:45:00)",
                modifier = Modifier.weight(1f),
                layout = MetricLayout.VERTICAL,
                iconSize = 28.dp
            )

            MetricItem(
                iconRes = R.drawable.ic_distance,
                label = "Distance",
                value = "10,00 km",
                secondaryValue = "(Max. Luftlinie: 5,00 km)",
                modifier = Modifier.weight(1f),
                layout = MetricLayout.VERTICAL,
                iconSize = 28.dp
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
fun PreviewWorkoutDetails() {
    MaterialTheme {
        WorkoutDetails(
            data = WorkoutDetailsData(
                totalDistance = 12500.0,
                maxDisplacement = 4500.0,
                activeTimeSec = 3600,
                totalTimeSec = 4200,
                avgSpeedMps = 3.47,
                bSportType = BSportType.BIKE,
                ascentMeters = 250,
                descentMeters = 240
            )
        )
    }
}