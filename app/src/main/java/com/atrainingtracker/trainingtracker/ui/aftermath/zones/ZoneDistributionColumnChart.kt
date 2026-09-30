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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Pure mathematical calculations and string formatting for zone column charts.
 */
object ZoneDistributionChartMath {
    /**
     * Calculates the normalized height fraction [0.0f, 1.0f] of a zone relative to the max zone duration.
     * If max duration or duration is zero or negative, returns 0.0f.
     */
    fun calculateHeightFraction(durationSec: Long, maxDurationSec: Long): Float {
        if (maxDurationSec <= 0L || durationSec <= 0L) return 0f
        return (durationSec.toFloat() / maxDurationSec.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Formats duration in seconds to "m:ss" or "h:mm:ss".
     */
    fun formatZoneDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
        }
    }
}

/**
 * Reusable 5-column vertical histogram component for metabolic and physiological training zones.
 *
 * X-Axis: 5 discrete vertical columns for Zones 1 through 5.
 * Y-Axis: Height of each column reflects duration spent in that zone, scaled relative to the maximum zone duration.
 */
@Composable
fun ZoneDistributionColumnChart(
    distribution: ZoneDistributionData,
    modifier: Modifier = Modifier,
    barAreaHeight: Dp = 72.dp
) {
    val maxDuration = distribution.entries.maxOfOrNull { it.durationSec }?.coerceAtLeast(1L) ?: 1L

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row of 5 columns (Zone 1 to Zone 5) on the X-axis
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            distribution.entries.forEach { entry ->
                ZoneColumnItem(
                    entry = entry,
                    maxDurationSec = maxDuration,
                    barAreaHeight = barAreaHeight,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Baseline divider
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // X-Axis Zone Badges / Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            distribution.entries.forEach { entry ->
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(entry.color)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Z${entry.zoneIndex}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoneColumnItem(
    entry: ZoneTimeEntry,
    maxDurationSec: Long,
    barAreaHeight: Dp,
    modifier: Modifier = Modifier
) {
    val heightFraction = ZoneDistributionChartMath.calculateHeightFraction(entry.durationSec, maxDurationSec)

    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Duration and Percentage text
        Text(
            text = ZoneDistributionChartMath.formatZoneDuration(entry.durationSec),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        Text(
            text = String.format(Locale.getDefault(), "%.0f%%", entry.percentage),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Vertical Bar Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barAreaHeight),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Background track slot
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            )

            // Active bar
            if (entry.durationSec > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .fillMaxHeight(heightFraction)
                        .defaultMinSize(minHeight = 4.dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(entry.color)
                )
            }
        }
    }
}
