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
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.components.strava.PoweredByStrava
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.RouteColorSelected
import com.atrainingtracker.trainingtracker.ui.theme.RouteColorUnselected

@Composable
fun RouteSummaryHeader(
    summary: RouteSummary,
    onToggleSelection: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showSwitch: Boolean = true,
    switchScale: Float = 0.7f
) {
    val formatters = com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // --- TOP ROW: Sport Icon and Route Name ---
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // --- SECOND ROW: Source and Visibility Switch ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.routes_source_label, stringResource(summary.source.displayNameResId)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )

            if (showSwitch) {
                Switch(
                    modifier = Modifier.scale(switchScale),
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

        // --- THIRD ROW: Metrics and Mandatory Branding ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricItem(
                    iconRes = R.drawable.ic_distance,
                    value = formatters.distance.format_with_units(summary.distance),
                    isPrimary = true
                )

                MetricItem(
                    iconRes = R.drawable.ic_ascent,
                    value = formatters.altitude.format_with_units(summary.elevationGain),
                    isPrimary = true
                )
            }

            if (summary.source == RouteSource.STRAVA) {
                PoweredByStrava(height = 24.dp)
            }
        }

        // Add the description if it is not empty
        if (summary.description.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text(
                text = summary.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
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
