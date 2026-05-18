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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.work.Configuration
import com.atrainingtracker.banalservice.sensor.formater.DefaultNumberFormatter
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@Composable
fun WorkoutSummaryCompact(
    workoutData: WorkoutData,
    onEditWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Maintain the "unfinished" state visual feedback
    val contentAlpha = if (workoutData.headerData.finished) 1.0f else 0.5f

    val df = DistanceFormatter()
    val tf = TimeFormatter()
    val sf = SpeedFormatter()
    val dnf = DefaultNumberFormatter()

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = contentAlpha)
            .clickable { onEditWorkout() },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // --- ROW 1: Workout Name ---
            Text(
                text = workoutData.workoutName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // --- ROW 2: Icon, Sport, Equipment, Date & Time ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sport Icon
                Icon(
                    painter = painterResource(id = workoutData.bSportType.iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                    tint = Color.Unspecified
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Sport Name
                Text(
                    text = workoutData.headerData.sportName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Equipment (if present)
                if (!workoutData.headerData.equipmentName.isNullOrBlank()) {
                    Text(
                        text = ": ${workoutData.headerData.equipmentName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f) //, fill = false)
                            .padding(start = 4.dp)
                    )
                }
                else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Date and Time
                Text(
                    text = "${workoutData.headerData.formattedDate}  ${workoutData.headerData.formattedTime}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- ROW 3: Distance, Active Time, Speed/Pace, Ascent ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Distance
                CompactMetricItem(
                    label = "Distance",
                    value = df.format_with_units(workoutData.detailsData.totalDistance)
                )

                // Active Time
                CompactMetricItem(
                    label = "Time",
                    value = tf.format_with_units(workoutData.detailsData.activeTimeSec)
                )

                // Speed/Pace
                CompactMetricItem(
                    label = "Avg Speed",
                    value = sf.format_with_units(workoutData.detailsData.avgSpeedMps)
                )

                // Ascent
                CompactMetricItem(
                    label = "Ascent",
                    value = dnf.format(workoutData.ascentMeters) + " m"
                )
            }
        }
    }
}

@Composable
private fun CompactMetricItem(
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
