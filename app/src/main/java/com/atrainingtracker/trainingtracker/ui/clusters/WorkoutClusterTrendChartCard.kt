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

package com.atrainingtracker.trainingtracker.ui.clusters

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.trainingtracker.ui.theme.TTColor

/**
 * Historical duration and speed/pace progression trend chart for Workout Clusters (REQ-SET-078).
 * Renders an athletic trend line where higher vertical placement represents better performance.
 */
@Composable
fun WorkoutClusterTrendChartCard(
    workouts: List<WorkoutData>,
    sportType: BSportType,
    onSelectWorkout: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMode by remember { mutableStateOf(ClusterTrendMetricMode.DURATION) }

    val trendSeries = remember(workouts, sportType, selectedMode) {
        WorkoutClusterTrendMath.buildTrendSeries(
            workouts = workouts,
            clusterSportType = sportType,
            metricMode = selectedMode
        )
    }

    if (trendSeries.points.size < 2) {
        return
    }

    var selectedPointId by remember(trendSeries) {
        mutableStateOf<Long?>(trendSeries.points.lastOrNull()?.workoutId)
    }

    val selectedPoint = remember(trendSeries, selectedPointId) {
        trendSeries.points.find { it.workoutId == selectedPointId } ?: trendSeries.points.lastOrNull()
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Title & Metric Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.cluster_trend_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceColor
                    )
                }

                // Dual Metric Toggle Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isDuration = selectedMode == ClusterTrendMetricMode.DURATION
                    FilterChip(
                        selected = isDuration,
                        onClick = { selectedMode = ClusterTrendMetricMode.DURATION },
                        label = {
                            Text(
                                text = stringResource(R.string.cluster_trend_duration),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(28.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = primaryColor.copy(alpha = TTAlpha.Faint),
                            selectedLabelColor = primaryColor
                        )
                    )

                    val velocityLabel = if (trendSeries.isPace) {
                        stringResource(R.string.cluster_trend_pace)
                    } else {
                        stringResource(R.string.cluster_trend_speed)
                    }

                    FilterChip(
                        selected = !isDuration,
                        onClick = { selectedMode = ClusterTrendMetricMode.SPEED_OR_PACE },
                        label = {
                            Text(
                                text = velocityLabel,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(28.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = primaryColor.copy(alpha = TTAlpha.Faint),
                            selectedLabelColor = primaryColor
                        )
                    )
                }
            }

            // Canvas Chart Area
            val points = trendSeries.points
            val avgY = trendSeries.avgNormalizedY

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                val hit = WorkoutClusterTrendMath.findNearestPoint(
                                    points = points,
                                    touchX = offset.x - 24.dp.toPx(),
                                    touchY = offset.y - 12.dp.toPx(),
                                    widthPx = size.width - 48.dp.toPx(),
                                    heightPx = size.height - 24.dp.toPx(),
                                    maxDistancePx = 36.dp.toPx()
                                )
                                if (hit != null) {
                                    selectedPointId = hit.workoutId
                                }
                            }
                        }
                ) {
                    val horizontalPadding = 24.dp.toPx()
                    val verticalPadding = 12.dp.toPx()
                    val plotWidth = (size.width - 2 * horizontalPadding).coerceAtLeast(1f)
                    val plotHeight = (size.height - 2 * verticalPadding).coerceAtLeast(1f)

                    // Average horizontal dashed line
                    val avgCanvasY = verticalPadding + (1f - avgY) * plotHeight
                    val dashPath = Path().apply {
                        moveTo(horizontalPadding, avgCanvasY)
                        lineTo(size.width - horizontalPadding, avgCanvasY)
                    }
                    drawPath(
                        path = dashPath,
                        color = outlineVariantColor.copy(alpha = 0.8f),
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                        )
                    )

                    // Compute pixel coordinates
                    val coords = points.map { p ->
                        val px = horizontalPadding + p.normalizedX * plotWidth
                        val py = verticalPadding + (1f - p.normalizedY) * plotHeight
                        Offset(px, py)
                    }

                    if (coords.size >= 2) {
                        // Area fill under curve
                        val fillPath = Path().apply {
                            moveTo(coords.first().x, coords.first().y)
                            for (i in 1 until coords.size) {
                                lineTo(coords[i].x, coords[i].y)
                            }
                            lineTo(coords.last().x, size.height - verticalPadding)
                            lineTo(coords.first().x, size.height - verticalPadding)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.25f),
                                    primaryColor.copy(alpha = 0.02f)
                                ),
                                startY = verticalPadding,
                                endY = size.height - verticalPadding
                            )
                        )

                        // Trend line stroke
                        val linePath = Path().apply {
                            moveTo(coords.first().x, coords.first().y)
                            for (i in 1 until coords.size) {
                                lineTo(coords[i].x, coords[i].y)
                            }
                        }
                        drawPath(
                            path = linePath,
                            color = primaryColor,
                            style = Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    // Draw points
                    for (i in points.indices) {
                        val p = points[i]
                        val c = coords[i]
                        val isSelected = p.workoutId == selectedPointId

                        if (isSelected) {
                            // Outer ring
                            drawCircle(
                                color = primaryColor.copy(alpha = 0.3f),
                                radius = 9.dp.toPx(),
                                center = c
                            )
                            drawCircle(
                                color = primaryColor,
                                radius = 5.dp.toPx(),
                                center = c
                            )
                            drawCircle(
                                color = surfaceColor,
                                radius = 2.5.dp.toPx(),
                                center = c
                            )
                        } else if (p.isPr) {
                            // Gold PR point
                            drawCircle(
                                color = TTColor.Gold,
                                radius = 6.dp.toPx(),
                                center = c
                            )
                            drawCircle(
                                color = surfaceColor,
                                radius = 2.5.dp.toPx(),
                                center = c
                            )
                        } else {
                            // Regular point
                            drawCircle(
                                color = primaryColor,
                                radius = 4.dp.toPx(),
                                center = c
                            )
                            drawCircle(
                                color = surfaceColor,
                                radius = 1.8.dp.toPx(),
                                center = c
                            )
                        }
                    }
                }
            }

            // Interactive Callout / Inspector Card
            if (selectedPoint != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectWorkout(selectedPoint.workoutId) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = selectedPoint.formattedDate,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onSurfaceVariantColor
                                )
                                if (selectedPoint.isPr) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = TTColor.Gold.copy(alpha = 0.2f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.EmojiEvents,
                                                contentDescription = null,
                                                tint = TTColor.Gold,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Text(
                                                text = stringResource(R.string.cluster_trend_pr),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = TTColor.Gold
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedPoint.formattedDuration,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = onSurfaceColor
                                )
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onSurfaceVariantColor
                                )
                                Text(
                                    text = selectedPoint.formattedSpeedOrPace,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = onSurfaceColor
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = onSurfaceVariantColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
