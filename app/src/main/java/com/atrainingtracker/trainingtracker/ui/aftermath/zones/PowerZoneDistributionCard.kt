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

package com.atrainingtracker.trainingtracker.ui.aftermath.zones

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R

/**
 * Compact card displaying a 5-zone Cycling Power vertical column histogram and time-in-zone breakdown,
 * with an interactive toggle to switch to a fine-grained telemetry frequency histogram (10 W bins).
 */
@Composable
fun PowerZoneDistributionCard(
    distribution: ZoneDistributionData,
    modifier: Modifier = Modifier
) {
    var displayMode by rememberSaveable { mutableStateOf(ZoneCardDisplayMode.FIVE_ZONES) }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Power Icon + Localized Title + Optional Mode Toggle + Total Active Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_power),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.aftermath_power_zones_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))

                if (distribution.histogram != null) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.height(28.dp)
                    ) {
                        SegmentedButton(
                            selected = displayMode == ZoneCardDisplayMode.FIVE_ZONES,
                            onClick = { displayMode = ZoneCardDisplayMode.FIVE_ZONES },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = {},
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.zone_mode_5_zones),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        SegmentedButton(
                            selected = displayMode == ZoneCardDisplayMode.HISTOGRAM,
                            onClick = { displayMode = ZoneCardDisplayMode.HISTOGRAM },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = {},
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.zone_mode_histogram),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Text(
                    text = ZoneDistributionChartMath.formatZoneDuration(distribution.totalActiveTimeSec),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Body: 5-Column Vertical Histogram or Fine-Grained Telemetry Frequency Histogram
            when (displayMode) {
                ZoneCardDisplayMode.FIVE_ZONES -> {
                    ZoneDistributionColumnChart(
                        distribution = distribution,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ZoneCardDisplayMode.HISTOGRAM -> {
                    TelemetryHistogramChart(
                        histogram = distribution.histogram,
                        unit = "W",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
