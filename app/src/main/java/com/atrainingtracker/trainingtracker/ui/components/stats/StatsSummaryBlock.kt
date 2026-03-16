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

package com.atrainingtracker.trainingtracker.ui.components.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R

@Composable
fun StatsSummaryBlock(
    stats: StatsData,
    modifier: Modifier = Modifier,
    onStatsClick: (StatsData) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = { onStatsClick(stats) }
            )
            .padding(vertical = 4.dp) // Add slight padding for touch target
    ) {
        // Section Title (e.g., "All Time Stats")
        Text(
            text = stats.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Main Metrics Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatItem(
                label = stringResource(R.string.stats_workouts),
                value = "${stats.totalWorkouts}"
            )
            StatItem(
                label = stringResource(R.string.stats_distance),
                value = stats.totalDistanceWithUnits
            )
            StatItem(
                label = stringResource(R.string.stats_time),
                value = stats.timeWithUnits
            )
            StatItem(
                label = stringResource(R.string.stats_ascent),
                value = stats.totalAscentWithUnits
            )
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun UsageItem(label: String, date: String, alignEnd: Boolean = false) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            text = date,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Full Stats (Metric)")
@Composable
fun PreviewStatsSummaryFull() {
    val mockData = StatsData(
        title = "Gesamt", // German for "All Time"
        totalWorkouts = 42,
        totalDistanceWithUnits = "1.250,5 km",
        timeWithUnits = "42:34:00",
        totalAscentWithUnits = "15.400 m"
    )
    androidx.compose.material3.Surface(
        modifier = androidx.compose.ui.Modifier.padding(16.dp),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surface
    ) {
        StatsSummaryBlock(
            stats = mockData,
            onStatsClick = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Minimal Stats (Imperial)")
@Composable
fun PreviewStatsSummaryMinimal() {
    val mockData = StatsData(
        title = "All Time",
        totalWorkouts = 12,
        totalDistanceWithUnits = "280.0 mi",
        timeWithUnits = "42:34:00",
        totalAscentWithUnits = "4,500 ft"
    )
    androidx.compose.material3.Surface(
        modifier = androidx.compose.ui.Modifier.padding(16.dp),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surface
    ) {
        StatsSummaryBlock(
            stats = mockData,
            onStatsClick = {}
        )
    }
}