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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
 * Time-range filter options for workout cluster trend charts (REQ-UI-490.3).
 */
enum class ClusterTrendTimeRange(val stringResId: Int, val spanDays: Int?) {
    ALL(R.string.cluster_trend_range_all, null),
    THREE_YEARS(R.string.cluster_trend_range_3y, 3 * 365),
    ONE_YEAR(R.string.cluster_trend_range_1y, 365)
}

/**
 * Historical duration and speed/pace progression trend chart for Workout Clusters (REQ-SET-078, REQ-UI-490).
 * Renders an athletic trend line where higher vertical placement represents better performance.
 */
@Composable
fun WorkoutClusterTrendChartCard(
    workouts: List<WorkoutData>,
    sportType: BSportType,
    onSelectWorkout: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMode by rememberSaveable { mutableStateOf(ClusterTrendMetricMode.DURATION) }
    var selectedTimeRange by rememberSaveable { mutableStateOf(ClusterTrendTimeRange.ALL) }

    val filteredWorkouts = remember(workouts, selectedTimeRange) {
        if (selectedTimeRange.spanDays == null || workouts.isEmpty()) {
            workouts
        } else {
            val latestTimeS = workouts.maxOfOrNull { it.startTimeS } ?: 0L
            val thresholdS = latestTimeS - (selectedTimeRange.spanDays!!.toLong() * 86400L)
            val inRange = workouts.filter { it.startTimeS >= thresholdS }
            val validCount = inRange.count { it.activeTimeSec > 0 && it.avgSpeedMps > 0.0 }
            if (validCount >= 2) inRange else workouts
        }
    }

    val trendSeries = remember(filteredWorkouts, sportType, selectedMode) {
        WorkoutClusterTrendMath.buildTrendSeries(
            workouts = filteredWorkouts,
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
            // Header: Time Range Filter Chips & Metric Toggle (REQ-UI-490.3)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time Range Filter Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClusterTrendTimeRange.values().forEach { range ->
                        val isSelected = selectedTimeRange == range
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedTimeRange = range },
                            label = {
                                Text(
                                    text = stringResource(range.stringResId),
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
                val leftGutter = 48.dp
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                val leftPadPx = leftGutter.toPx()
                                val topPadPx = 16.dp.toPx()
                                val rightPadPx = 12.dp.toPx()
                                val bottomPadPx = 24.dp.toPx()
                                val hit = WorkoutClusterTrendMath.findNearestPoint(
                                    points = points,
                                    touchX = offset.x - leftPadPx,
                                    touchY = offset.y - topPadPx,
                                    widthPx = (size.width - leftPadPx - rightPadPx).coerceAtLeast(1f),
                                    heightPx = (size.height - topPadPx - bottomPadPx).coerceAtLeast(1f),
                                    maxDistancePx = 36.dp.toPx()
                                )
                                if (hit != null) {
                                    selectedPointId = hit.workoutId
                                    onSelectWorkout(hit.workoutId)
                                }
                            }
                        }
                ) {
                    val leftPadding = leftGutter.toPx()
                    val rightPadding = 12.dp.toPx()
                    val topPadding = 16.dp.toPx()
                    val bottomPadding = 24.dp.toPx()
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

                    // Native canvas text rendering for X/Y axis labels in dedicated gutter (REQ-UI-490.1)
                    drawContext.canvas.nativeCanvas.apply {
                        if (trendSeries.yAxisTopLabel.isNotEmpty()) {
                            drawText(trendSeries.yAxisTopLabel, 2.dp.toPx(), topPadding + 4.dp.toPx(), textPaint)
                        }
                        if (trendSeries.yAxisBottomLabel.isNotEmpty()) {
                            drawText(trendSeries.yAxisBottomLabel, 2.dp.toPx(), topPadding + plotHeight, textPaint)
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

                    // Average label with rounded surface badge (drawn on top of lines & points for legibility, REQ-UI-490.2)
                    if (trendSeries.formattedAverage.isNotEmpty()) {
                        val avgText = "$avgPrefix ${trendSeries.formattedAverage}"
                        val avgWidth = textPaint.measureText(avgText)
                        val avgTextY = if (avgCanvasY - 4.dp.toPx() < topPadding + 10.dp.toPx()) {
                            avgCanvasY + 12.dp.toPx()
                        } else {
                            avgCanvasY - 4.dp.toPx()
                        }
                        val badgeLeft = size.width - rightPadding - avgWidth - 5.dp.toPx()
                        val badgeTop = avgTextY - axisTextSizePx - 1.dp.toPx()
                        val badgeWidth = avgWidth + 10.dp.toPx()
                        val badgeHeight = axisTextSizePx + 4.dp.toPx()

                        drawRoundRect(
                            color = surfaceColor.copy(alpha = 0.92f),
                            topLeft = Offset(badgeLeft, badgeTop),
                            size = Size(badgeWidth, badgeHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )

                        drawContext.canvas.nativeCanvas.drawText(
                            avgText,
                            size.width - rightPadding - avgWidth,
                            avgTextY,
                            textPaint
                        )
                    }
                }
            }
        }
    }
}
