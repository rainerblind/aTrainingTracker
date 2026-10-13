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

package com.atrainingtracker.trainingtracker.ui.segments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.atrainingtracker.trainingtracker.routes.SegmentMatchedRoute
import com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter
import kotlin.math.roundToInt

/**
 * Breakdown section displaying saved routes that contain the currently viewed segment
 * (REQ-UI-305, TST-UI-265, ATT-2585).
 *
 * Renders route cards with sport icon, name, total distance, elevation gain, and start offset.
 * Tapping a route item triggers [onRouteClick] with the route ID.
 */
@Composable
fun SegmentRoutesSection(
    matchingRoutes: List<SegmentMatchedRoute>,
    modifier: Modifier = Modifier,
    onRouteClick: ((Long) -> Unit)? = null
) {
    val formatters = LocalMetricFormatter.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.segment_routes_containing_title, matchingRoutes.size),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        matchingRoutes.forEach { matched ->
            val summary = matched.route.summary
            ElevatedCard(
                onClick = { onRouteClick?.invoke(summary.id) },
                enabled = onRouteClick != null,
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
                    // Top Row: Sport icon and Route name
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = summary.bSportType.iconResId),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color.Unspecified
                        )
                        Text(
                            text = summary.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Metrics Row: Total Distance, Elevation Gain, Start at km
                    val startDistMeters = matched.startDistanceMeters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val routeDistStr = formatters.distance.format_with_units(summary.distance)
                        if (!routeDistStr.isNullOrBlank()) {
                            Text(
                                text = routeDistStr,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val eleGainStr = formatters.altitude.format_with_units(summary.elevationGain)
                        if (!eleGainStr.isNullOrBlank()) {
                            Text(
                                text = eleGainStr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = stringResource(
                                R.string.routes_segment_start_at,
                                formatters.distance.format_with_units(startDistMeters)
                                    ?: "${(startDistMeters / 1000.0).roundToInt()} km"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
