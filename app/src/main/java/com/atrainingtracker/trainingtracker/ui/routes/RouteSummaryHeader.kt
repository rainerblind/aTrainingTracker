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

package com.atrainingtracker.trainingtracker.ui.routes


import androidx.compose.animation.core.copy
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.AltitudeFormatter
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.RouteColorSelected
import com.atrainingtracker.trainingtracker.ui.theme.RouteColorUnselected

@Composable
fun RouteSummaryHeader(
    summary: RouteSummary,
    onToggleSelection: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showSwitch: Boolean = true
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape, //RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- TOP ROW: Sport Icon and Route Name ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = painterResource(id = summary.bSportType.iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = Color.Unspecified // Original color
                )
                Text(
                    text = summary.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Visibility Switch (Right-aligned in top row)
                if (showSwitch) {
                    Switch(
                        modifier = Modifier.scale(0.8f),
                        checked = summary.isSelected,
                        onCheckedChange = onToggleSelection,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = RouteColorSelected,
                            checkedTrackColor = RouteColorSelected.copy(alpha = 0.5f),
                            checkedBorderColor = RouteColorSelected,
                            uncheckedThumbColor = RouteColorUnselected,
                            uncheckedTrackColor = RouteColorUnselected.copy(alpha = 0.5f),
                            uncheckedBorderColor = RouteColorUnselected
                        )
                    )
                }
            }

            // --- BOTTOM CONTENT: Source and Metrics ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Source
                Text(
                    text = stringResource(summary.source.displayNameResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Metrics Row: Distance and Elevation Gain with Icons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Distance Group
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_distance),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = DistanceFormatter().format_with_units(summary.distance),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Elevation Group
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_ascent),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = AltitudeFormatter().format_with_units(summary.elevationGain),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        // Add the description if it is not empty
        if (summary.description.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text(
                text = summary.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}

@Preview(showBackground = true, name = "Cycling Route - Selected")
@Composable
fun PreviewRouteSummaryHeaderBike() {
    ATrainingTrackerTheme {
        RouteSummaryHeader(
            summary = RouteSummary(
                id = 1,
                externalId = "B-2026-X1",
                name = "Black Forest Alpine Cross",
                description =  "Long Epic Stage 1",
                isSelected = true,
                distance = 68450.0,
                elevationGain = 1250.0,
                bSportType = BSportType.BIKE,
                source = RouteSource.LOCAL_GPX,
            ),
            onToggleSelection = {},
        )
    }
}

@Preview(showBackground = true, name = "Running Route - Unselected")
@Composable
fun PreviewRouteSummaryHeaderRun() {
    ATrainingTrackerTheme {
        RouteSummaryHeader(
            summary = RouteSummary(
                id = 2,
                externalId = "",
                name = "Park Loop",
                description = "",
                isSelected = false,
                distance = 5200.0,
                elevationGain = 15.0,
                bSportType = BSportType.RUN,
                source = RouteSource.STRAVA
            ),
            onToggleSelection = {},
        )
    }
}
