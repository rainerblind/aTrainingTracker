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


@Composable
fun WorkoutDetails(
    data: WorkoutDetailsData,
    modifier: Modifier = Modifier
) {
    val distanceFormatter = DistanceFormatter()
    val timeFormatter = TimeFormatter()

    val iconColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textColorMain = MaterialTheme.colorScheme.onSurface
    val textColorSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Metrics Row: Time and Distance ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Active Time
            MainItem(
                iconColor = iconColor,
                textColorMain = textColorMain,
                textColorSecondary = textColorSecondary,
                iconRes = R.drawable.ic_time_active,
                label = stringResource(R.string.time_active),
                mainValueString = timeFormatter.format(data.activeTimeSec),
                secondaryValueString = stringResource(R.string.total_time_format, timeFormatter.format(data.totalTimeSec)),
                modifier = Modifier.weight(1f)
            )

            // 2. Distance
            val maxDispString = if (data.maxDisplacement != null) {
                val maxDispFormatted = distanceFormatter.format_with_units(data.maxDisplacement)
                stringResource(R.string.format_max_displacement, maxDispFormatted)
            } else null

            MainItem(
                iconColor = iconColor,
                textColorMain = textColorMain,
                textColorSecondary = textColorSecondary,
                iconRes = R.drawable.ic_distance,
                label = stringResource(R.string.distance),
                mainValueString = distanceFormatter.format_with_units(data.totalDistance),
                secondaryValueString = maxDispString,
                modifier = Modifier.weight(1f)
            )
        }

        // --- Section 3: Altitude (Identical to bindAltitude in ViewHolder) ---
        AltitudeRow(
            data.ascentMeters,
            data.descentMeters,
            data.minAltitude,
            data.maxAltitude,
            modifier = Modifier,
            iconColor = iconColor,
            textColorMain = textColorMain,
            textColorSecondary = textColorSecondary
        )
    }
}


@Composable
private fun MainItem(
    iconColor: Color,
    textColorMain: Color,
    textColorSecondary: Color,
    iconRes: Int,
    label: String,
    mainValueString: String,
    secondaryValueString: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .padding(bottom = 0.dp),
                tint = iconColor
            )
            Spacer(modifier = Modifier.width(5.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColorSecondary
                )
                Text(
                    text = mainValueString,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColorMain
                )
            }
        }
        if (secondaryValueString != null) {
            Text(
                text = secondaryValueString,
                style = MaterialTheme.typography.bodySmall,
                color = textColorSecondary
            )
        }
    }
}

@Composable
private fun AltitudeRow(
    ascentMeters: Long,
    descentMeters: Long,
    minAltitude: Double?,
    maxAltitude: Double?,
    iconColor: Color,
    textColorMain: Color,
    textColorSecondary: Color,
    modifier: Modifier,
){
    val altitudeFormatter = AltitudeFormatter()

    if (ascentMeters > 0 || descentMeters > 0 || minAltitude != null || maxAltitude != null) {
        HorizontalDivider(
            // modifier = Modifier.padding(8.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )

        Row(
            modifier = modifier
                .fillMaxWidth()
        ) {
            Column() {
                Icon(
                    painter = painterResource(id = R.drawable.ic_altitude),
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .padding(bottom = 0.dp),
                    tint = iconColor
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.altitude),
                    style = MaterialTheme.typography.labelMedium,
                    color = textColorSecondary
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column() {
                Row(modifier = Modifier.fillMaxWidth()) {
                    AltitudeItem(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.ascent_short),
                        value = altitudeFormatter.format_with_units(ascentMeters),
                        iconRes = R.drawable.ic_ascent,
                        iconColor = iconColor,
                        textColorMain = textColorMain,
                        textColorSecondary = textColorSecondary
                    )
                    maxAltitude?.let {
                        AltitudeItem(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.max),
                            value = altitudeFormatter.format_with_units(it),
                            iconRes = R.drawable.ic_altitude_max,
                            iconColor = iconColor,
                            textColorMain = textColorMain,
                            textColorSecondary = textColorSecondary
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    AltitudeItem(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.descent_short),
                        value = altitudeFormatter.format_with_units(descentMeters),
                        iconRes = R.drawable.ic_descent,
                        iconColor = iconColor,
                        textColorMain = textColorMain,
                        textColorSecondary = textColorSecondary
                    )
                    minAltitude?.let {
                        AltitudeItem(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.min),
                            value = altitudeFormatter.format_with_units(it),
                            iconRes = R.drawable.ic_altitude_min,
                            iconColor = iconColor,
                            textColorMain = textColorMain,
                            textColorSecondary = textColorSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AltitudeItem(
    modifier: Modifier,
    label: String,
    value: String,
    iconRes: Int,
    iconColor: Color,
    textColorMain: Color,
    textColorSecondary: Color
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = iconColor
        )
        Spacer(modifier = Modifier.width(2.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textColorSecondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = textColorMain
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
            iconColor = MaterialTheme.colorScheme.onSurface,
            textColorMain = MaterialTheme.colorScheme.onSurface,
            textColorSecondary = MaterialTheme.colorScheme.onSurfaceVariant,
            label = "Distance",
            modifier = Modifier.fillMaxWidth(),
            mainValueString = "10,00 km",
            secondaryValueString = "(Max. Luftlinie: 5,00 km)"
        )
    }
}

@Preview
@Composable
fun PreviewDistanceAndTime() {
    MaterialTheme {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Active Time
            MainItem(
                iconRes = R.drawable.ic_time_active,
                iconColor = MaterialTheme.colorScheme.onSurface,
                textColorMain = MaterialTheme.colorScheme.onSurface,
                textColorSecondary = MaterialTheme.colorScheme.onSurfaceVariant,
                label = "Active Time",
                mainValueString = "0:30:00",
                secondaryValueString = "(Total: 0:45:00)",
                modifier = Modifier.weight(1f)
            )

            MainItem(
                iconRes = R.drawable.ic_distance,
                iconColor = MaterialTheme.colorScheme.onSurface,
                textColorMain = MaterialTheme.colorScheme.onSurface,
                textColorSecondary = MaterialTheme.colorScheme.onSurfaceVariant,
                label = "Distance",
                mainValueString = "10,00 km",
                secondaryValueString = "(Max. Luftlinie: 5,00 km)",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Preview
@Composable
fun PreviewAltitudeRow() {
    MaterialTheme {
        AltitudeRow(
            ascentMeters = 1250,
            descentMeters = 1240,
            minAltitude = 1150.0,
            maxAltitude = 1410.0,
            modifier = Modifier,
            iconColor = MaterialTheme.colorScheme.onSurface,
            textColorMain = MaterialTheme.colorScheme.onSurface,
            textColorSecondary = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                descentMeters = 240,
                minAltitude = 150.0,
                maxAltitude = 410.0
            )
        )
    }
}