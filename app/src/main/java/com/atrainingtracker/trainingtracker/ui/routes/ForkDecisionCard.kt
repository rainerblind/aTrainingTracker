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

package com.atrainingtracker.trainingtracker.ui.routes

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.routes.ForkBranchOption
import com.atrainingtracker.trainingtracker.routes.ForkDecisionState
import com.atrainingtracker.trainingtracker.routes.ForkDirection
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Ambient HUD card presenting branching route options at a fork in the road.
 *
 * Conforms to REQ-MAP-031 clause 3 and REQ-UI-330 (ATT-2962).
 */
@Composable
fun ForkDecisionCard(
    decisionState: ForkDecisionState?,
    overlayAlpha: Float = com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_TRANSPARENCY,
    modifier: Modifier = Modifier,
    onRouteSelected: (Long) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    AnimatedVisibility(
        visible = decisionState != null && decisionState.branches.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        if (decisionState == null) return@AnimatedVisibility

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CallSplit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.fork_alert_title) + " (" +
                                stringResource(R.string.fork_approaching_in_m, decisionState.distanceToForkMeters.roundToInt()) + ")",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.fork_dismiss),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Directional Grouping (REQ-UI-330)
                val groupedBranches = decisionState.branches.groupBy { it.direction }
                groupedBranches.forEach { (direction, branches) ->
                    ForkDirectionGroup(
                        direction = direction,
                        branches = branches,
                        onRouteSelected = onRouteSelected
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

/**
 * Directional group container grouping candidate routes heading in the same relative direction.
 */
@Composable
fun ForkDirectionGroup(
    direction: ForkDirection,
    branches: List<ForkBranchOption>,
    onRouteSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Direction Column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .width(54.dp)
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                val icon = when (direction) {
                    ForkDirection.LEFT -> Icons.AutoMirrored.Filled.ArrowBack
                    ForkDirection.STRAIGHT -> Icons.Default.ArrowUpward
                    ForkDirection.RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
                }
                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(direction.labelRes),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(direction.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            // Subtle vertical divider separating direction from routes
            VerticalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
            )

            // Routes Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp)
            ) {
                branches.forEachIndexed { index, branch ->
                    if (index > 0) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                    ForkRouteRow(
                        branch = branch,
                        onClick = { onRouteSelected(branch.routeId) }
                    )
                }
            }
        }
    }
}

/**
 * Clickable row displaying a single route candidate's title and metrics.
 * Note: Redundant tap hint removed to optimize screen space and glanceability (REQ-UI-330).
 */
@Composable
fun ForkRouteRow(
    branch: ForkBranchOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = branch.routeName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val distKm = branch.totalDistanceMeters / 1000.0
                Text(
                    text = String.format(Locale.US, "%.1f km", distKm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "+${branch.totalElevationMeters.roundToInt()} m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Composable
private fun ForkDecisionCardPreview_Light() {
    ATrainingTrackerTheme(darkTheme = false) {
        val sampleBranches = listOf(
            ForkBranchOption(1L, "Waldenbuch Scenic Loop", 38400.0, 420.0, ForkDirection.STRAIGHT, 0.0),
            ForkBranchOption(2L, "Steinenbronn Direct Cut", 31200.0, 310.0, ForkDirection.STRAIGHT, 4.0),
            ForkBranchOption(3L, "Dettenhausen Fast Ridge", 54200.0, 680.0, ForkDirection.RIGHT, 45.0)
        )
        val state = ForkDecisionState(
            divergenceCoordinate = LatLng(48.5100, 9.0000),
            distanceToForkMeters = 245.0,
            branches = sampleBranches,
            isApproaching = true
        )
        ForkDecisionCard(decisionState = state)
    }
}

@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun ForkDecisionCardPreview_Dark() {
    ATrainingTrackerTheme(darkTheme = true) {
        val sampleBranches = listOf(
            ForkBranchOption(1L, "Waldenbuch Scenic Loop", 38400.0, 420.0, ForkDirection.STRAIGHT, 0.0),
            ForkBranchOption(2L, "Dettenhausen Fast Ridge", 54200.0, 680.0, ForkDirection.RIGHT, 45.0)
        )
        val state = ForkDecisionState(
            divergenceCoordinate = LatLng(48.5100, 9.0000),
            distanceToForkMeters = 180.0,
            branches = sampleBranches,
            isApproaching = true
        )
        ForkDecisionCard(decisionState = state)
    }
}
