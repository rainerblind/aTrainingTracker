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

package com.atrainingtracker.trainingtracker.ui.aftermath

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.components.MetricLayout
import java.util.Locale

/**
 * Dedicated Aftermath summary card displaying post-workout cycling rearview radar traffic statistics (REQ-RAD-005 / ATT-5088).
 */
@Composable
fun RadarTrafficSummaryCard(
    summary: RadarTrafficSummary?,
    modifier: Modifier = Modifier
) {
    if (summary == null || summary.totalVehicles <= 0) return

    ElevatedCard(
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Icon + Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.bike_radar),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = androidx.compose.ui.graphics.Color.Unspecified
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.radar_traffic_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Row 1: Volume & Density
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricItem(
                    iconRes = null,
                    label = stringResource(R.string.radar_total_vehicles),
                    value = summary.totalVehicles.toString(),
                    layout = MetricLayout.VERTICAL,
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    iconRes = null,
                    label = stringResource(R.string.radar_traffic_density_km),
                    value = String.format(Locale.getDefault(), "%.1f", summary.vehiclesPerKm),
                    layout = MetricLayout.VERTICAL,
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    iconRes = null,
                    label = stringResource(R.string.radar_traffic_density_hour),
                    value = String.format(Locale.getDefault(), "%.1f", summary.vehiclesPerHour),
                    layout = MetricLayout.VERTICAL,
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 2: Speeds & Threat Levels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricItem(
                    iconRes = null,
                    label = stringResource(R.string.radar_max_closing_speed),
                    value = String.format(Locale.getDefault(), "%.1f km/h", summary.maxApproachSpeedMps * 3.6),
                    layout = MetricLayout.VERTICAL,
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    iconRes = null,
                    label = stringResource(R.string.radar_max_vehicle_speed),
                    value = String.format(Locale.getDefault(), "%.1f km/h", summary.maxVehicleSpeedMps * 3.6),
                    layout = MetricLayout.VERTICAL,
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    iconRes = null,
                    label = stringResource(R.string.radar_high_threat_passes),
                    value = summary.highThreatPassCount.toString(),
                    layout = MetricLayout.VERTICAL,
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 3: Clearance & Vehicle Sizes (REQ-TRK-073.7 / ATT-5096)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val minClearanceStr = summary.minPassingDistanceM?.let {
                    String.format(Locale.getDefault(), "%.1f m", it)
                } ?: "-"
                MetricItem(
                    iconRes = null,
                    label = stringResource(R.string.radar_min_passing_distance),
                    value = minClearanceStr,
                    layout = MetricLayout.VERTICAL,
                    modifier = Modifier.weight(1f)
                )
                val closePassStr = if (summary.closePassCount > 0) {
                    "⚠️ ${summary.closePassCount}"
                } else {
                    "0"
                }
                MetricItem(
                    iconRes = null,
                    label = stringResource(R.string.radar_close_pass_threshold_title),
                    value = closePassStr,
                    layout = MetricLayout.VERTICAL,
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    iconRes = null,
                    label = stringResource(R.string.radar_heavy_vehicle_share),
                    value = summary.heavyVehicleCount.toString(),
                    layout = MetricLayout.VERTICAL,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
