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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.segments.LiveSegmentData
import com.atrainingtracker.trainingtracker.segments.LiveSegmentStatus
import com.atrainingtracker.trainingtracker.segments.SegmentSummary
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.Zone3
import com.atrainingtracker.trainingtracker.ui.theme.Zone4

@Composable
fun SegmentSummaryHeader(
    summary: SegmentSummary,
    compact: Boolean = false,
    liveSegmentData: LiveSegmentData? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape, //RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 2.dp else 8.dp)
        ) {
            // --- TOP ROW: Name and City on Left, Category and PR on Right ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top // Align to top so metadata stays pinned if title wraps
            ) {
                // 1. Left Column: Name and City of Live or state of LiveSegment
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.name,
                        style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    if (liveSegmentData == null) {
                        Text(
                            text = summary.city,
                            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = liveSegmentData.segmentStatus.label(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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
                                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
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
                                    modifier = Modifier.size(if (compact) 14.dp else 18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = summary.prTime,
                                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            if (liveSegmentData == null) {
                Spacer(modifier = Modifier.height(if (compact) 2.dp else 6.dp))

                // --- ROW 1: Distance ---
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatItem(R.drawable.ic_distance, summary.distance, compact)
                }

                if (!compact) {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // --- ROW 2: Grades (Avg and Max) ---
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatItem(R.drawable.ic_grade, summary.averageGrade, compact)
                    VerticalDivider(compact)
                    Text(text = summary.maxGrade, style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
                }

                if (!compact) {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // --- ROW 3: Elevations (Gain, Min, Max) ---
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!compact) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_altitude),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    StatItem(R.drawable.ic_elevation_gain, summary.elevationGain, compact)
                    VerticalDivider(compact)
                    StatItem(R.drawable.ic_altitude_min, summary.elevationMin, compact)
                    VerticalDivider(compact)
                    StatItem(R.drawable.ic_altitude_max, summary.elevationMax, compact)
                }
            }

            // --- LIVE DATA SECTION ---
            else {
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Distance Progress (Matches static distance alignment)
                    Column {
                        Text(
                            text = if (liveSegmentData.segmentStatus == LiveSegmentStatus.APPROACHING) {
                                stringResource(id = R.string.segment_status_start_in, liveSegmentData.distanceToStart)
                            }
                            else {
                                liveSegmentData.distanceOnSegment
                            },
                            style = if (liveSegmentData.segmentStatus != LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH) {
                                MaterialTheme.typography.titleLarge
                            }
                            else {
                                MaterialTheme.typography.bodyLarge
                            },
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(id = R.string.segment_status_remaining, liveSegmentData.remainingDistance),
                            style = if (liveSegmentData.segmentStatus == LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH) {
                                MaterialTheme.typography.titleLarge
                            }
                            else {
                                MaterialTheme.typography.bodyLarge
                            },

                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(id = R.string.segment_status_offset, liveSegmentData.segmentOffset),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (liveSegmentData.segmentOffset_raw < SegmentsRepository.SEGMENT_DISTANCE_THRESHOLD * 0.5f) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            else if (liveSegmentData.segmentOffset_raw < SegmentsRepository.SEGMENT_DISTANCE_THRESHOLD * 0.75f ) {
                                Zone3
                            }
                            else {
                                Zone4
                            }
                        )
                    }

                    // Right: Live Time (Matches PR time alignment)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = liveSegmentData.timeOnSegment,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

        }
    }
}

@Composable
fun LiveSegmentStatus.label(): String {
    return stringResource(id = this.resId)
}

@Composable
private fun StatItem(iconRes: Int, value: String, compact: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(if (compact) 14.dp else 16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = value, style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun VerticalDivider(compact: Boolean) {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .width(1.dp)
            .height(if (compact) 10.dp else 12.dp)
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
                climbCategory_raw = 5,
                climbCategory = "HC",
                prTime_raw = 45 * 60 + 20,
                prTime = "45:20",
                city = "Bourg d'Oisans",
                distance = "13.80 km",
                distance_raw = 13800.0,
                averageGrade_raw = 8.1,
                averageGrade = "Ø 8.1%",
                maxGrade = "12.0% Max",
                elevationGain_raw = 1073.0,
                elevationGain = "1073 m",
                elevationMin = "720 m",
                elevationMax = "1793 m",
                map_polyline = ""
            )
        )
    }
}

@Preview(showBackground = true, name = "Compact Full Info")
@Composable
fun PreviewSegmentSummaryFullCompact() {
    ATrainingTrackerTheme {
        SegmentSummaryHeader(
            summary = SegmentSummary(
                stravaId = 12345L,
                name = "Alpe d'Huez Climb",
                bSportType = BSportType.BIKE,
                climbCategory_raw = 5,
                climbCategory = "HC",
                prTime_raw = 45 * 60 + 20,
                prTime = "45:20",
                city = "Bourg d'Oisans",
                distance = "13.80 km",
                distance_raw = 13800.0,
                averageGrade_raw = 8.1,
                averageGrade = "Ø 8.1%",
                maxGrade = "12.0% Max",
                elevationGain_raw = 1073.1,
                elevationGain = "1073 m",
                elevationMin = "720 m",
                elevationMax = "1793 m",
                map_polyline = ""
            ),
            compact = true
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
                climbCategory_raw = 0,
                climbCategory = "", // Empty category
                prTime_raw = -1,
                prTime = "",   // Empty/Placeholder PR
                city = "Berlin",
                distance = "1.20 km",
                distance_raw = 1200.0,
                averageGrade_raw = 0.5,
                averageGrade = "Ø 0.5%",
                maxGrade = "1.2% Max",
                elevationGain_raw = 5.2,
                elevationGain = "5 m",
                elevationMin = "34 m",
                elevationMax = "39 m",
                map_polyline = ""
            )
        )
    }
}

@Preview(showBackground = true, name = "Live Effort View (Approaching)")
@Composable
fun PreviewSegmentSummaryLiveApproaching() {
    ATrainingTrackerTheme {
        SegmentSummaryHeader(
            summary = SegmentSummary(
                stravaId = 12345L,
                name = "Alpe d'Huez Climb",
                bSportType = BSportType.BIKE,
                climbCategory_raw = 5,
                climbCategory = "HC",
                prTime_raw = 2720,
                prTime = "45:20",
                city = "Bourg d'Oisans",
                distance = "13.80 km",
                distance_raw = 13800.0,
                averageGrade_raw = 8.1,
                averageGrade = "Ø 8.1%",
                maxGrade = "12.0% Max",
                elevationGain_raw = 1073.1,
                elevationGain = "1073 m",
                elevationMin = "720 m",
                elevationMax = "1793 m",
                map_polyline = ""
            ),
            liveSegmentData = LiveSegmentData(
                segmentStatus = LiveSegmentStatus.APPROACHING,
                distanceToStart = "222 m",
                timeOnSegment = "--",
                distanceOnSegment = "--",
                remainingDistance = "--",
                segmentOffset = "--"
            )
        )
    }
}

@Preview(showBackground = true, name = "Live Effort View (On Segment)")
@Composable
fun PreviewSegmentSummaryLiveOnSegment() {
    ATrainingTrackerTheme {
        SegmentSummaryHeader(
            summary = SegmentSummary(
                stravaId = 12345L,
                name = "Alpe d'Huez Climb",
                bSportType = BSportType.BIKE,
                climbCategory_raw = 5,
                climbCategory = "HC",
                prTime_raw = 2720,
                prTime = "45:20",
                city = "Bourg d'Oisans",
                distance = "13.80 km",
                distance_raw = 13800.0,
                averageGrade_raw = 8.1,
                averageGrade = "Ø 8.1%",
                maxGrade = "12.0% Max",
                elevationGain_raw = 1073.1,
                elevationGain = "1073 m",
                elevationMin = "720 m",
                elevationMax = "1793 m",
                map_polyline = ""
            ),
            liveSegmentData = LiveSegmentData(
                segmentStatus = LiveSegmentStatus.ON_SEGMENT,
                timeOnSegment = "2:45",
                distanceOnSegment = "1.20 km",
                remainingDistance = "12.80 m",
                segmentOffset = "40 m",
                segmentOffset_raw = 40.0
            )
        )
    }
}

@Preview(showBackground = true, name = "Live Effort View (Close to finish)")
@Composable
fun PreviewSegmentSummaryLive() {
    ATrainingTrackerTheme {
        SegmentSummaryHeader(
            summary = SegmentSummary(
                stravaId = 12345L,
                name = "Alpe d'Huez Climb",
                bSportType = BSportType.BIKE,
                climbCategory_raw = 5,
                climbCategory = "HC",
                prTime_raw = 2720,
                prTime = "45:20",
                city = "Bourg d'Oisans",
                distance = "13.80 km",
                distance_raw = 13800.0,
                averageGrade_raw = 8.1,
                averageGrade = "Ø 8.1%",
                maxGrade = "12.0% Max",
                elevationGain_raw = 1073.1,
                elevationGain = "1073 m",
                elevationMin = "720 m",
                elevationMax = "1793 m",
                map_polyline = ""
            ),
            liveSegmentData = LiveSegmentData(
                segmentStatus = LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH,
                timeOnSegment = "12:45",
                distanceOnSegment = "3.20 km",
                remainingDistance = "400 m",
                segmentOffset = "30 m",
                segmentOffset_raw = 30.0
            )
        )
    }
}
