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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val avgPrefix = stringResource(R.string.cluster_trend_avg)

    val density = LocalDensity.current
    val axisTextSizePx = with(density) { 10.sp.toPx() }
    val onSurfaceVariantArgb = onSurfaceVariantColor.toArgb()
    val textPaint = remember(onSurfaceVariantArgb, axisTextSizePx) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = onSurfaceVariantArgb
            textSize = axisTextSizePx
            typeface = android.graphics.Typeface.DEFAULT
        }
    }

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
                    .height(150.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                val hit = WorkoutClusterTrendMath.findNearestPoint(
                                    points = points,
                                    touchX = offset.x - 12.dp.toPx(),
                                    touchY = offset.y - 16.dp.toPx(),
                                    widthPx = (size.width - 24.dp.toPx()).coerceAtLeast(1f),
                                    heightPx = (size.height - 44.dp.toPx()).coerceAtLeast(1f),
                                    maxDistancePx = 36.dp.toPx()
                                )
                                if (hit != null) selectedPointId = hit.workoutId
                            }
                        }
                ) {
                    val leftPadding = 12.dp.toPx()
                    val rightPadding = 12.dp.toPx()
                    val topPadding = 16.dp.toPx()
                    val bottomPadding = 28.dp.toPx()
                    val plotWidth = (size.width - leftPadding - rightPadding).coerceAtLeast(1f)
                    val plotHeight = (size.height - topPadding - bottomPadding).coerceAtLeast(1f)

                    // Reference horizontal gridlines (top and bottom bounds)
                    drawLine(outlineVariantColor.copy(alpha = 0.35f), Offset(leftPadding, topPadding), Offset(size.width - rightPadding, topPadding), 1.dp.toPx())
                    drawLine(outlineVariantColor.copy(alpha = 0.35f), Offset(leftPadding, topPadding + plotHeight), Offset(size.width - rightPadding, topPadding + plotHeight), 1.dp.toPx())

                    // Average horizontal dashed line
                    val avgCanvasY = topPadding + (1f - avgY) * plotHeight
                    val dashPath = Path().apply {
                        moveTo(leftPadding, avgCanvasY)
                        lineTo(size.width - rightPadding, avgCanvasY)
                    }
                    drawPath(
                        path = dashPath,
                        color = outlineVariantColor.copy(alpha = 0.8f),
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f))
                    )

                    // Compute pixel coordinates
                    val coords = points.map { p ->
                        Offset(leftPadding + p.normalizedX * plotWidth, topPadding + (1f - p.normalizedY) * plotHeight)
                    }

                    if (coords.size >= 2) {
                        // Area fill under curve
                        val fillPath = Path().apply {
                            moveTo(coords.first().x, coords.first().y)
                            for (i in 1 until coords.size) lineTo(coords[i].x, coords[i].y)
                            lineTo(coords.last().x, topPadding + plotHeight)
                            lineTo(coords.first().x, topPadding + plotHeight)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(primaryColor.copy(alpha = 0.25f), primaryColor.copy(alpha = 0.02f)),
                                startY = topPadding,
                                endY = topPadding + plotHeight
                            )
                        )

                        // Trend line stroke
                        val linePath = Path().apply {
                            moveTo(coords.first().x, coords.first().y)
                            for (i in 1 until coords.size) lineTo(coords[i].x, coords[i].y)
                        }
                        drawPath(path = linePath, color = primaryColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }

                    // Native canvas text rendering for X/Y axis labels (REQ-SET-078.7)
                    drawContext.canvas.nativeCanvas.apply {
                        if (trendSeries.yAxisTopLabel.isNotEmpty()) {
                            drawText(trendSeries.yAxisTopLabel, leftPadding, topPadding - 4.dp.toPx(), textPaint)
                        }
                        if (trendSeries.yAxisBottomLabel.isNotEmpty()) {
                            drawText(trendSeries.yAxisBottomLabel, leftPadding, topPadding + plotHeight + 12.dp.toPx(), textPaint)
                        }
                        if (trendSeries.formattedAverage.isNotEmpty()) {
                            val avgText = "$avgPrefix ${trendSeries.formattedAverage}"
                            val avgWidth = textPaint.measureText(avgText)
                            val avgTextY = if (avgCanvasY - 4.dp.toPx() < topPadding + 10.dp.toPx()) avgCanvasY + 12.dp.toPx() else avgCanvasY - 4.dp.toPx()
                            drawText(avgText, size.width - rightPadding - avgWidth, avgTextY, textPaint)
                        }
                        if (trendSeries.xAxisStartLabel.isNotEmpty()) {
                            drawText(trendSeries.xAxisStartLabel, leftPadding, size.height - 4.dp.toPx(), textPaint)
                        }
                        if (trendSeries.xAxisEndLabel.isNotEmpty()) {
                            val endWidth = textPaint.measureText(trendSeries.xAxisEndLabel)
                            drawText(trendSeries.xAxisEndLabel, size.width - rightPadding - endWidth, size.height - 4.dp.toPx(), textPaint)
                        }
                    }

                    // Draw points with density-adaptive radius (REQ-SET-078.8)
                    val pointRadius = if (points.size > 20) 2.dp.toPx() else 4.dp.toPx()
                    val innerRadius = if (points.size > 20) 1.dp.toPx() else 1.8.dp.toPx()

                    for (i in points.indices) {
                        val p = points[i]
                        val c = coords[i]
                        val isSelected = p.workoutId == selectedPointId

                        if (isSelected) {
                            drawCircle(color = primaryColor.copy(alpha = 0.3f), radius = 9.dp.toPx(), center = c)
                            drawCircle(color = primaryColor, radius = 5.dp.toPx(), center = c)
                            drawCircle(color = surfaceColor, radius = 2.5.dp.toPx(), center = c)
                        } else if (p.isPr) {
                            drawCircle(color = TTColor.Gold, radius = 6.dp.toPx(), center = c)
                            drawCircle(color = surfaceColor, radius = 2.5.dp.toPx(), center = c)
                        } else if (p.isClampedOutlier) {
                            drawCircle(color = outlineVariantColor, radius = pointRadius + 1.5.dp.toPx(), center = c, style = Stroke(width = 1.5.dp.toPx()))
                            drawCircle(color = primaryColor.copy(alpha = 0.7f), radius = pointRadius, center = c)
                            drawCircle(color = surfaceColor, radius = innerRadius, center = c)
                        } else {
                            drawCircle(color = primaryColor, radius = pointRadius, center = c)
                            drawCircle(color = surfaceColor, radius = innerRadius, center = c)
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
                                if (selectedPoint.isClampedOutlier) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.cluster_trend_outlier),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
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
