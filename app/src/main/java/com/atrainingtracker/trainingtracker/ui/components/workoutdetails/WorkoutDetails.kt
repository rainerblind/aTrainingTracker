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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.PaceFormatter
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.MyHelper

@Composable
fun WorkoutDetails(
    data: WorkoutDetailsData,
    modifier: Modifier = Modifier
) {
    val distanceFormatter = DistanceFormatter()
    val timeFormatter = TimeFormatter()
    val speedFormatter = SpeedFormatter()
    val paceFormatter = PaceFormatter()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Section 1: Distance and Time (Replicates the first Card/Group) ---
            Row(modifier = Modifier.fillMaxWidth()) {

                // Distance
                // TODO: move this logic to the viewModel?
                val maxDispString = if (data.maxDisplacement != null) {
                    val maxDispFormatted = distanceFormatter.format_with_units(data.maxDisplacement)
                    stringResource(R.string.format_max_displacement, maxDispFormatted)
                }
                else {
                    null
                }
                MainItem(
                    iconRes = R.drawable.ic_distance,
                    mainString = distanceFormatter.format_with_units(data.totalDistance),
                    secondaryString = maxDispString,
                    modifier = Modifier.weight(1f)
                )

                // Active Time
                MainItem(
                    iconRes = R.drawable.ic_time_active,
                    mainString = timeFormatter.format(data.activeTimeSec),
                    secondaryString = stringResource(R.string.total_time_format, timeFormatter.format(data.totalTimeSec)),
                    modifier = Modifier.weight(1f)
                )
            }
        // Speed (or pace)
        val mainSpeedString = if (data.bSportType == BSportType.RUN) {
            paceFormatter.format_with_units(1/data.avgSpeedMps)
        }
        else {
            speedFormatter.format_with_units(data.avgSpeedMps)
        }
        MainItem(
            iconRes = R.drawable.ic_speed,
            mainString = mainSpeedString,
            secondaryString = if (BSportType.RUN == data.bSportType) "          " + speedFormatter.format_with_units(data.avgSpeedMps) else null,
            modifier = Modifier
        )

        // --- Section 3: Altitude (Identical to bindAltitude in ViewHolder) ---
        val hasAltitudeData = data.ascentMeters > 0 || data.descentMeters > 0 || data.minAltitude != null || data.maxAltitude != null

        if (hasAltitudeData) {
            DetailCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Ascent / Descent Row
                    if (data.ascentMeters > 0 || data.descentMeters > 0) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            if (data.ascentMeters > 0) {
                                DetailItem(
                                    modifier = Modifier.weight(1f),
                                    label = stringResource(R.string.ascent),
                                    value = "${data.ascentMeters} m",
                                    iconRes = R.drawable.ic_ascent
                                )
                            }
                            if (data.descentMeters > 0) {
                                DetailItem(
                                    modifier = Modifier.weight(1f),
                                    label = stringResource(R.string.descent),
                                    value = "${data.descentMeters} m",
                                    iconRes = R.drawable.ic_descent
                                )
                            }
                        }
                    }

                    // Min / Max Altitude Row
                    if (data.minAltitude != null || data.maxAltitude != null) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            data.minAltitude?.let {
                                DetailItem(
                                    modifier = Modifier.weight(1f),
                                    label = stringResource(R.string.altitude),
                                    value = "%.0f m".format(it),
                                    iconRes = R.drawable.ic_altitude_min
                                )
                            }
                            data.maxAltitude?.let {
                                DetailItem(
                                    modifier = Modifier.weight(1f),
                                    label = stringResource(R.string.altitude),
                                    value = "%.0f m".format(it),
                                    iconRes = R.drawable.ic_altitude_max
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Flat look like standard lists
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    iconRes: Int,
    isSecondary: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isSecondary)
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                else
                    MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = if (isSecondary) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = if (isSecondary) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSecondary) FontWeight.Normal else FontWeight.Medium
            )
        }
        if (value.isNotEmpty()) {
            Text(
                text = value,
                style = if (isSecondary) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium,
                color = if (isSecondary) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSecondary) FontWeight.Normal else FontWeight.Bold
            )
        }
    }
}


@Composable
private fun MainItem(
    iconRes: Int,
    mainString: String,
    secondaryString: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = mainString,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (secondaryString != null) {
            Text(
                text = secondaryString,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailItem(
    modifier: Modifier,
    label: String,
    value: String,
    iconRes: Int
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Preview
@Composable
fun PreviewDetailItem() {
    MaterialTheme {
        MainItem(
            iconRes = R.drawable.ic_distance,
            modifier = Modifier.fillMaxWidth(),
            mainString = "10,00 km",
            secondaryString = "(Max. Luftlinie: 5,00 km)"
        )
    }
}

@Preview
@Composable
fun PreviewDistanceAndTime() {
    MaterialTheme {
        Row(modifier = Modifier.fillMaxWidth()) {
            MainItem(
                iconRes = R.drawable.ic_distance,
                mainString = "10,00 km",
                secondaryString = "(Max. Luftlinie: 5,00 km)",
                modifier = Modifier.weight(1f)
            )

            // Active Time
            MainItem(
                iconRes = R.drawable.ic_time_active,
                mainString = "0:30:00",
                secondaryString = "(Total: 0:45:00)",
                modifier = Modifier.weight(1f)
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
                avgSpeedMps = 3.47f,
                bSportType = BSportType.BIKE,
                ascentMeters = 250,
                descentMeters = 240,
                minAltitude = 150.0,
                maxAltitude = 410.0
            )
        )
    }
}