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


import androidx.compose.foundation.layout.*
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
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.RouteColorSelected

@Composable
fun RouteSummaryHeader(
    summary: RouteSummary,
    onToggleSelection: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- LEFT COLUMN: Name, Source, and Icon-based Metrics ---
        Column(modifier = Modifier.weight(1f)) {
            // Route Name
            Text(
                text = summary.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Source
            Text(
                text = stringResource(summary.source.displayNameResId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

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
                        text = String.format("%.2f km", summary.distance / 1000.0),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Elevation Group
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_elevation_gain),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${summary.elevationGain.toInt()} m",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // --- RIGHT COLUMN: Sport Icon and Visibility Toggle ---
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sport Category Icon
            Icon(
                painter = painterResource(id = summary.bSportType.iconResId),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.Unspecified
            )

            // Visibility Switch
            Switch(
                checked = summary.isSelected,
                onCheckedChange = onToggleSelection,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = RouteColorSelected,
                    checkedTrackColor = RouteColorSelected.copy(alpha = 0.5f)
                )
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
                name = "Black Forest Alpine Cross - Long Epic Stage 1",
                isSelected = true,
                distance = 68450.0,
                elevationGain = 1250.0,
                bSportType = BSportType.BIKE,
                source = com.atrainingtracker.trainingtracker.database.RouteSource.LOCAL_GPX
            )
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
                isSelected = false,
                distance = 5200.0,
                elevationGain = 15.0,
                bSportType = BSportType.RUN,
                source = com.atrainingtracker.trainingtracker.database.RouteSource.STRAVA
            )
        )
    }
}
