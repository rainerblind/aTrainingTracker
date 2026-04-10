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

package com.atrainingtracker.trainingtracker.ui.segments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.segments.SegmentSummary
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

@Composable
fun SegmentSummaryHeader(
    summary: SegmentSummary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // --- TOP ROW: Name and City on Left, Category and PR on Right ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top // Align to top so metadata stays pinned if title wraps
            ) {
                // 1. Left Column: Name and City
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    Text(
                        text = summary.city,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 2. Right Column: Category (Top) and PR (Bottom)
                if (summary.climbCategory.isNotBlank() || (summary.prTime.isNotBlank() && summary.prTime != "--:--")) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Climb Category Chip
                        if (summary.climbCategory.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = summary.climbCategory,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        // PR Time Row
                        if (summary.prTime.isNotBlank() && summary.prTime != "--:--") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_pr_time),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = summary.prTime,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // --- ROW 1: Distance ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatItem(R.drawable.ic_distance, summary.distance)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // --- ROW 2: Grades (Avg and Max) ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatItem(R.drawable.ic_grade, summary.averageGrade)
                VerticalDivider()
                Text(text = summary.maxGrade, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // --- ROW 3: Elevations (Gain, Min, Max) ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_altitude),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))

                StatItem(R.drawable.ic_elevation_gain, summary.elevationGain)
                VerticalDivider()
                StatItem(R.drawable.ic_altitude_min, summary.elevationMin)
                VerticalDivider()
                StatItem(R.drawable.ic_altitude_max, summary.elevationMax)
            }
        }
    }
}

@Composable
private fun StatItem(iconRes: Int, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .width(1.dp)
            .height(12.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Preview(showBackground = true, name = "Full Info")
@Composable
fun PreviewSegmentSummaryFull() {
    ATrainingTrackerTheme {
        SegmentSummaryHeader(
            summary = SegmentSummary(
                stravaId = 12345L,
                name = "Alpe d'Huez Climb",
                bSportType = BSportType.BIKE,
                climbCategory = "HC",
                prTime_raw = 45 * 60 + 20,
                prTime = "45:20",
                city = "Bourg d'Oisans",
                distance = "13.80 km",
                averageGrade = "Ø 8.1%",
                maxGrade = "12.0% Max",
                elevationGain = "1073 m",
                elevationMin = "720 m",
                elevationMax = "1793 m"
            )
        )
    }
}

@Preview(showBackground = true, name = "No Category & No PR")
@Composable
fun PreviewSegmentSummaryMinimal() {
    ATrainingTrackerTheme {
        SegmentSummaryHeader(
            summary = SegmentSummary(
                stravaId = 67890L,
                name = "Short Flat Sprint",
                bSportType = BSportType.BIKE,
                climbCategory = "", // Empty category
                prTime_raw = -1,
                prTime = "",   // Empty/Placeholder PR
                city = "Berlin",
                distance = "1.20 km",
                averageGrade = "Ø 0.5%",
                maxGrade = "1.2% Max",
                elevationGain = "5 m",
                elevationMin = "34 m",
                elevationMax = "39 m"
            )
        )
    }
}