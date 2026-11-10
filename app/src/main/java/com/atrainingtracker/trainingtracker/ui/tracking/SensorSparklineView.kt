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

package com.atrainingtracker.trainingtracker.ui.tracking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Resolves the sparkline container height dynamically based on tile [ViewSize] and display mode (REQ-TRK-059.5, ATT-1294).
 */
fun resolveSparklineHeight(viewSize: ViewSize, isGraphOnly: Boolean = false): Dp =
    if (isGraphOnly) {
        when {
            viewSize <= ViewSize.SMALL -> 20.dp
            viewSize == ViewSize.NORMAL -> 32.dp
            viewSize == ViewSize.LARGE -> 48.dp
            else -> 64.dp
        }
    } else {
        when {
            viewSize <= ViewSize.SMALL -> 14.dp
            viewSize == ViewSize.NORMAL -> 20.dp
            viewSize == ViewSize.LARGE -> 28.dp
            else -> 36.dp
        }
    }

/**
 * Lightweight, hardware-accelerated Canvas sparkline composable rendering temporal telemetry trends
 * with zero recomposition churn or snapshot state mutations (REQ-TRK-059.5, Rule 66).
 *
 * @param samples Time-series numeric telemetry values ordered chronologically.
 * @param modifier Composable layout modifier.
 * @param lineColor Stroke color for the sparkline trend polyline and terminal point.
 * @param fillGradient Whether to render a subtle vertical gradient fill below the polyline.
 */
@Composable
fun SensorSparklineView(
    samples: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillGradient: Boolean = true
) {
    Canvas(modifier = modifier) {
        if (samples.isEmpty() || size.width <= 0f || size.height <= 0f) {
            return@Canvas
        }

        val strokeWidthPx = 1.75.dp.toPx()
        val verticalPaddingPx = 3.dp.toPx()
        val usableHeight = (size.height - 2f * verticalPaddingPx).coerceAtLeast(1f)

        if (samples.size == 1) {
            // Single point: render a centered dot
            drawCircle(
                color = lineColor,
                radius = 2.5.dp.toPx(),
                center = Offset(size.width / 2f, size.height / 2f)
            )
            return@Canvas
        }

        var minVal = Float.MAX_VALUE
        var maxVal = -Float.MAX_VALUE
        for (v in samples) {
            if (v < minVal) minVal = v
            if (v > maxVal) maxVal = v
        }

        val range = maxVal - minVal
        val stepX = size.width / (samples.size - 1).toFloat()

        val strokePath = Path()
        val fillPath = if (fillGradient) Path() else null

        val firstY = if (range > 0f) {
            size.height - verticalPaddingPx - ((samples[0] - minVal) / range) * usableHeight
        } else {
            size.height / 2f
        }

        strokePath.moveTo(0f, firstY)
        fillPath?.moveTo(0f, size.height)
        fillPath?.lineTo(0f, firstY)

        var lastX = 0f
        var lastY = firstY

        for (i in 1 until samples.size) {
            val currentX = i * stepX
            val currentY = if (range > 0f) {
                size.height - verticalPaddingPx - ((samples[i] - minVal) / range) * usableHeight
            } else {
                size.height / 2f
            }
            strokePath.lineTo(currentX, currentY)
            fillPath?.lineTo(currentX, currentY)
            lastX = currentX
            lastY = currentY
        }

        // Draw gradient fill below polyline
        if (fillPath != null) {
            fillPath.lineTo(size.width, size.height)
            fillPath.close()
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.28f),
                        lineColor.copy(alpha = 0.02f)
                    ),
                    startY = 0f,
                    endY = size.height
                )
            )
        }

        // Draw trend stroke polyline
        drawPath(
            path = strokePath,
            color = lineColor,
            style = Stroke(
                width = strokeWidthPx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Draw terminal dot at current (latest) reading
        drawCircle(
            color = lineColor,
            radius = 2.75.dp.toPx(),
            center = Offset(lastX, lastY)
        )
    }
}
