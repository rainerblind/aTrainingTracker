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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.atrainingtracker.trainingtracker.ui.routes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.routes.MatchedRouteSegment
import com.atrainingtracker.trainingtracker.ui.climbs.ClimbCategoryChip
import com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter
import kotlin.math.roundToInt

/**
 * Breakdown list of matched segments along a route displaying distance marker, length,
 * average grade, and athlete PR badge (REQ-UI-302, TST-UI-262, ATT-2583).
 * Now supports item-level visibility toggling on the route map (REQ-UI-308, ATT-2763).
 */
@Composable
fun RouteSegmentsBreakdownSection(
    segments: List<MatchedRouteSegment>,
    modifier: Modifier = Modifier,
    hiddenSegmentIds: Set<Long> = emptySet(),
    onToggleSegmentVisibility: ((Long) -> Unit)? = null,
    onSegmentClick: ((MatchedRouteSegment) -> Unit)? = null
) {
    val formatters = LocalMetricFormatter.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.routes_segments_section_title, segments.size),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        segments.forEach { matched ->
            val summary = matched.segment.summary
            ElevatedCard(
                onClick = { onSegmentClick?.invoke(matched) },
                enabled = onSegmentClick != null,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Top Row: Sport icon, Category chip (if any), Segment name, PR badge, and Visibility Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = summary.bSportType.iconResId),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.Unspecified
                            )
                            val category = ClimbCategory.fromCode(summary.climbCategory)
                            if (category != ClimbCategory.UNCATEGORIZED) {
                                ClimbCategoryChip(category = category)
                            }
                            Text(
                                text = summary.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (summary.prTime.isNotBlank() && summary.prTime != "--:--") {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_pr_time),
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = stringResource(R.string.routes_segment_pr, summary.prTime),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            if (onToggleSegmentVisibility != null) {
                                val isHidden = summary.stravaId in hiddenSegmentIds
                                IconButton(
                                    onClick = { onToggleSegmentVisibility(summary.stravaId) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = stringResource(if (isHidden) R.string.route_item_show else R.string.route_item_hide),
                                        tint = if (isHidden) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Metrics Row: Start at km, Length, Average gradient
                    val startDistMeters = matched.startDistanceMeters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                R.string.routes_segment_start_at,
                                formatters.distance.format_with_units(startDistMeters) ?: "${(startDistMeters / 1000.0).roundToInt()} km"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val distStr = summary.distance.ifBlank {
                            formatters.distance.format_with_units(summary.distance_raw) ?: ""
                        }
                        if (distStr.isNotBlank()) {
                            Text(
                                text = distStr,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val gradeStr = summary.averageGrade.ifBlank {
                            if (summary.averageGrade_raw != 0.0) "${summary.averageGrade_raw}%" else ""
                        }
                        if (gradeStr.isNotBlank()) {
                            Text(
                                text = gradeStr,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
