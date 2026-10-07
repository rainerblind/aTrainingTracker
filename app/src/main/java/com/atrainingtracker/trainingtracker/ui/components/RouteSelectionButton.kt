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

package com.atrainingtracker.trainingtracker.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.routes.ReturnNavigationState
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import java.util.Locale

/**
 * Branded route selection entry point button for the Control Tracking screen (REQ-UI-279 / ATT-2458, REQ-UI-281 / ATT-2460).
 *
 * Adheres strictly to Rule 23 design tokens and Section 5.4 green domain semantic accent guidelines:
 * - 12.dp rounded corners
 * - Subtle green border accent ([TTColor.RouteSelected] at 35% alpha)
 * - 36.dp green-tinted icon container with 8.dp rounded corners
 * - Inactive state displaying title, descriptive subtitle, and forward chevron
 * - Active state displaying active route name ("✓ <Route Name>"), distance + elevation metrics,
 *   and a quick-clear close button
 * - Dimmed state (0.45f alpha) when no route qualifies or is imported (REQ-UI-281)
 */
@Composable
fun RouteSelectionButton(
    activeRoute: RouteWithPath?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDimmed: Boolean = false,
    returnNavState: ReturnNavigationState? = null,
    onClearRoute: (() -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, TTColor.RouteSelected.copy(alpha = 0.35f)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isDimmed) 0.45f else 1.0f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Branded Route Icon Container (36.dp box, 8.dp radius, subtle green tint)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = TTColor.RouteSelected.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_route),
                        contentDescription = null,
                        tint = TTColor.RouteSelected,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (activeRoute != null) {
                        Text(
                            text = "✓ ${activeRoute.summary.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitleText = if (returnNavState != null && returnNavState.hasRemainingMetrics) {
                            "${returnNavState.formattedRemainingDistance} (${returnNavState.formattedClockTime})"
                        } else {
                            val distKm = activeRoute.summary.distance / 1000.0
                            val distStr = String.format(Locale.getDefault(), "%.1f km", distKm)
                            val eleStr = String.format(Locale.getDefault(), "+%.0f m", activeRoute.summary.elevationGain)
                            stringResource(id = R.string.route_metrics_format, distStr, eleStr)
                        }
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = stringResource(id = R.string.route_action_select),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(id = R.string.route_action_select_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (activeRoute != null && onClearRoute != null) {
                IconButton(
                    onClick = onClearRoute,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(id = R.string.route_action_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// --- Previews ---

@Preview(showBackground = true, name = "Inactive - Light")
@Preview(showBackground = true, name = "Inactive - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RouteSelectionButtonInactivePreview() {
    ATrainingTrackerTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            RouteSelectionButton(
                activeRoute = null,
                onClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Active - Light")
@Preview(showBackground = true, name = "Active - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RouteSelectionButtonActivePreview() {
    ATrainingTrackerTheme {
        val dummyRoute = RouteWithPath(
            summary = RouteSummary(
                id = 1L,
                externalId = "preview_1",
                name = "Grosser Feldberg Taunus Loop",
                description = "Scenic loop",
                isSelected = true,
                distance = 42500.0,
                elevationGain = 890.0,
                bSportType = com.atrainingtracker.banalservice.BSportType.BIKE,
                source = com.atrainingtracker.trainingtracker.database.RouteSource.LOCAL_GPX,
                syncedAt = System.currentTimeMillis()
            ),
            path = emptyList()
        )
        Box(modifier = Modifier.padding(16.dp)) {
            RouteSelectionButton(
                activeRoute = dummyRoute,
                onClick = {},
                onClearRoute = {}
            )
        }
    }
}
