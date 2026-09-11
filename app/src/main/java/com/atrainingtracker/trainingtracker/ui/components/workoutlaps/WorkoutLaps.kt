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

package com.atrainingtracker.trainingtracker.ui.components.workoutlaps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter

@Composable
fun WorkoutLaps(
    laps: List<LapData>,
    bSportType: BSportType = BSportType.UNKNOWN,
    modifier: Modifier = Modifier
) {
    if (laps.isEmpty()) return

    val formatters = LocalMetricFormatter.current
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    // Performance highlight badges: Rabbit (fastest) and Hedgehog (slowest)
    // Only applied when >= 2 laps exist and speeds differ.
    val validSpeeds = remember(laps) {
        laps.map { it.speedAverageMps }.filter { it > 0.001 }
    }
    val hasDifferentSpeeds = remember(validSpeeds) {
        validSpeeds.distinct().size > 1
    }
    val fastestSpeed = remember(hasDifferentSpeeds, validSpeeds) {
        if (hasDifferentSpeeds) validSpeeds.maxOrNull() else null
    }
    val slowestSpeed = remember(hasDifferentSpeeds, validSpeeds) {
        if (hasDifferentSpeeds) validSpeeds.minOrNull() else null
    }

    val isRunningSport = (bSportType == BSportType.RUN)

    val displayedLaps = if (isExpanded || laps.size <= 3) laps else laps.take(3)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Section Header: "Laps (X)"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.laps_header, laps.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Split Table Rows
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            displayedLaps.forEachIndexed { index, lap ->
                val lapIndex = laps.indexOf(lap) + 1
                val displayName = lap.getDisplayName(lapIndex)

                val isFastest = hasDifferentSpeeds && fastestSpeed != null &&
                        kotlin.math.abs(lap.speedAverageMps - fastestSpeed) < 1e-5
                val isSlowest = hasDifferentSpeeds && slowestSpeed != null &&
                        kotlin.math.abs(lap.speedAverageMps - slowestSpeed) < 1e-5

                val badgeEmoji = when {
                    isFastest -> "🐇"
                    isSlowest -> "🦔"
                    else -> null
                }
                val badgeDescription = when {
                    isFastest -> stringResource(R.string.fastest_lap)
                    isSlowest -> stringResource(R.string.slowest_lap)
                    else -> null
                }

                // Speed / Pace formatted string
                val speedPaceFormatted = if (isRunningSport) {
                    if (lap.speedAverageMps > 0.001) {
                        formatters.pace.format_with_units(1.0 / lap.speedAverageMps)
                    } else {
                        "--"
                    }
                } else {
                    formatters.speed.format_with_units(lap.speedAverageMps)
                }

                LapRow(
                    displayName = displayName,
                    description = lap.description,
                    timeFormatted = formatters.time.format(lap.timeTotalS.toLong()),
                    distanceFormatted = formatters.distance.format_with_units(lap.distanceTotalM),
                    speedPaceFormatted = speedPaceFormatted,
                    badgeEmoji = badgeEmoji,
                    badgeDescription = badgeDescription,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                if (index < displayedLaps.size - 1) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        // Expandable toggle button if > 3 laps
        if (laps.size > 3) {
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .align(Alignment.Start)
            ) {
                Text(
                    text = if (!isExpanded) {
                        stringResource(R.string.show_all_laps, laps.size)
                    } else {
                        stringResource(R.string.show_fewer_laps)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LapRow(
    displayName: String,
    description: String?,
    timeFormatted: String,
    distanceFormatted: String,
    speedPaceFormatted: String,
    badgeEmoji: String?,
    badgeDescription: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Col 1: Lap Name & optional description
        Column(
            modifier = Modifier.weight(1.3f)
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Col 2: Duration
        Text(
            text = timeFormatted,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.9f)
        )

        // Col 3: Distance
        Text(
            text = distanceFormatted,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.0f)
        )

        // Col 4: Pace or Speed
        Text(
            text = speedPaceFormatted,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.1f)
        )

        // Col 5: Badge (Rabbit / Hedgehog)
        Box(
            modifier = Modifier
                .width(26.dp)
                .padding(start = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (badgeEmoji != null) {
                Text(
                    text = badgeEmoji,
                    fontSize = 14.sp,
                    modifier = Modifier.semantics {
                        if (badgeDescription != null) {
                            this.contentDescription = badgeDescription
                        }
                    }
                )
            }
        }
    }
}

/**
 * Pure helper functions for lap badge computation and list display logic.
 */
object WorkoutLapsHelper {
    enum class PerformanceBadge { FASTEST_RABBIT, SLOWEST_HEDGEHOG, NONE }

    fun determineBadges(laps: List<LapData>): Map<Long, PerformanceBadge> {
        val validSpeeds = laps.map { it.speedAverageMps }.filter { it > 0.001 }
        val distinctSpeeds = validSpeeds.distinct()
        if (distinctSpeeds.size <= 1) return emptyMap()

        val fastestSpeed = validSpeeds.maxOrNull() ?: return emptyMap()
        val slowestSpeed = validSpeeds.minOrNull() ?: return emptyMap()

        return laps.associate { lap ->
            val badge = when {
                kotlin.math.abs(lap.speedAverageMps - fastestSpeed) < 1e-5 -> PerformanceBadge.FASTEST_RABBIT
                kotlin.math.abs(lap.speedAverageMps - slowestSpeed) < 1e-5 -> PerformanceBadge.SLOWEST_HEDGEHOG
                else -> PerformanceBadge.NONE
            }
            lap.lapNr to badge
        }
    }

    fun getDisplayedLaps(laps: List<LapData>, isExpanded: Boolean): List<LapData> {
        return if (isExpanded || laps.size <= 3) laps else laps.take(3)
    }
}

