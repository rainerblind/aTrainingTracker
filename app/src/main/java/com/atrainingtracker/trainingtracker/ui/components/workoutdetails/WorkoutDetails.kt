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
    // Initialize formatters as used in the original ViewHolder
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
        // --- Section 1: Distance and Time ---
        DetailCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val distanceUnit = stringResource(id = MyHelper.getDistanceUnitNameId())

                // Main Distance
                DetailRow(
                    label = stringResource(R.string.distance),
                    value = stringResource(
                        R.string.value_unit_string_string,
                        distanceFormatter.format(data.totalDistance),
                        distanceUnit
                    ),
                    iconRes = R.drawable.ic_distance
                )

                // Max Displacement
                data.maxDisplacement?.let {
                    val maxDisplacementFormatted = distanceFormatter.format(it)
                    val maxDisplacementString = stringResource(R.string.value_unit_string_string, maxDisplacementFormatted, distanceUnit)
                    DetailRow(
                        label = stringResource(R.string.format_max_displacement, maxDisplacementString),
                        value = "",
                        isSecondary = true,
                        iconRes = R.drawable.ic_distance
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)

                // Active Time
                DetailRow(
                    label = stringResource(R.string.time_active),
                    value = timeFormatter.format(data.activeTimeSec),
                    iconRes = R.drawable.ic_time_active
                )

                // Total Time
                DetailRow(
                    label = stringResource(R.string.time_total),
                    value = timeFormatter.format(data.totalTimeSec),
                    isSecondary = true,
                    iconRes = R.drawable.ic_time_active
                )
            }
        }

        // --- Section 2: Speed / Pace ---
        DetailCard {
            val (formattedValue, unit) = if (data.bSportType == BSportType.RUN) {
                val paceSpm = if (data.avgSpeedMps > 0) 1.0 / data.avgSpeedMps else 0.0
                paceFormatter.format(paceSpm) to stringResource(MyHelper.getPaceUnitNameId())
            } else {
                speedFormatter.format(data.avgSpeedMps.toDouble()) to stringResource(MyHelper.getSpeedUnitNameId())
            }

            DetailRow(
                label = if (data.bSportType == BSportType.RUN) stringResource(R.string.pace) else stringResource(R.string.speed),
                value = stringResource(R.string.value_unit_string_string, formattedValue, unit),
                iconRes = R.drawable.ic_speed
            )
        }

        // --- Section 3: Altitude ---
        val hasAltitudeData = data.ascentMeters > 0 || data.descentMeters > 0 || data.minAltitude != null || data.maxAltitude != null

        if (hasAltitudeData) {
            DetailCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isSecondary) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = if (isSecondary) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = if (isSecondary) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = value,
            style = if (isSecondary) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
            fontWeight = if (isSecondary) FontWeight.Normal else FontWeight.Bold
        )
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
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
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