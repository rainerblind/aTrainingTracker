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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import java.util.Locale

/**
 * Workout Details card displaying autonomic durability metrics: fresh baseline DFA alpha 1,
 * late-workout fatigue mean, durability drift percentage (Δα1%), and 15-minute slice trajectories
 * pursuant to REQ-PER-038 and ATT-4970.
 */
@Composable
fun HrvDurabilityCard(
    points: List<PathPoint>,
    freshBaseline: Double? = null,
    lateMean: Double? = null,
    durabilityDriftPct: Double? = null,
    modifier: Modifier = Modifier
) {
    // Evaluate durability result dynamically from points or use precomputed fields
    val calculatedResult = remember(points) {
        if (points.isNotEmpty() && points.any { it.hrvDfaA1 != null }) {
            HrvDurabilityCalculator.calculateDurability(points)
        } else null
    }

    val effectiveFreshBaseline = freshBaseline ?: calculatedResult?.freshBaselineMean
    val effectiveLateMean = lateMean ?: calculatedResult?.lateMean
    val effectiveDriftPct = durabilityDriftPct ?: calculatedResult?.durabilityDriftPct

    // Graceful omission if no durability metrics exist (REQ-PER-038.6)
    if (effectiveFreshBaseline == null && effectiveLateMean == null && effectiveDriftPct == null) {
        return
    }

    ElevatedCard(
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.testTag("hrv_durability_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Card Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.TrendingDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.hrv_durability_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.hrv_durability_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // Primary Metrics Row: Fresh Baseline, Late Mean, Drift %
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Fresh Baseline
                Column(modifier = Modifier.testTag("hrv_durability_fresh_baseline")) {
                    Text(
                        text = stringResource(R.string.hrv_durability_fresh_baseline),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = effectiveFreshBaseline?.let { String.format(Locale.US, "%.2f", it) } ?: "--",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Late Mean
                Column(modifier = Modifier.testTag("hrv_durability_late_mean")) {
                    Text(
                        text = stringResource(R.string.hrv_durability_late_mean),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = effectiveLateMean?.let { String.format(Locale.US, "%.2f", it) } ?: "--",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Autonomic Drift (Δα1%)
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.testTag("hrv_durability_drift")
                ) {
                    Text(
                        text = stringResource(R.string.hrv_durability_drift),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val driftColor = when {
                        effectiveDriftPct == null -> MaterialTheme.colorScheme.onSurface
                        effectiveDriftPct <= -15.0 -> MaterialTheme.colorScheme.error
                        effectiveDriftPct <= -5.0 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Text(
                        text = effectiveDriftPct?.let { stringResource(R.string.hrv_durability_drift_fmt, it) } ?: "--",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = driftColor
                    )
                }
            }

            // Matched Intensity Chip if reference intensity is known
            calculatedResult?.referenceIntensity?.let { refInt ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (calculatedResult.isPowerBased) {
                            stringResource(R.string.hrv_durability_matched_intensity, refInt.toInt())
                        } else {
                            stringResource(R.string.hrv_durability_matched_speed, String.format(Locale.US, "%.1f m/s", refInt))
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // 15-Minute Slice Trajectory Section
            val activeSlices = calculatedResult?.slices?.filter { it.meanNormalizedAlpha1 != null } ?: emptyList()
            if (activeSlices.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.hrv_durability_trajectory_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .testTag("hrv_durability_trajectory"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (slice in activeSlices) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.hrv_durability_slice_label, slice.startMinute, slice.endMinute),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = slice.meanNormalizedAlpha1?.let { String.format(Locale.US, "%.2f", it) } ?: "--",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Explanatory note
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .padding(end = 6.dp, top = 2.dp)
                        .width(14.dp)
                        .height(14.dp)
                )
                Text(
                    text = stringResource(R.string.hrv_durability_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}
