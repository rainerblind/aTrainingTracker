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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R

/**
 * Resolves the sparkline container height dynamically based on tile [ViewSize] and display mode (REQ-TRK-059.5, ATT-1294, ATT-6695).
 */
fun resolveSparklineHeight(viewSize: ViewSize, isGraphOnly: Boolean = false): Dp =
    if (isGraphOnly) {
        when {
            viewSize <= ViewSize.SMALL -> 24.dp
            viewSize == ViewSize.NORMAL -> 36.dp
            viewSize == ViewSize.LARGE -> 52.dp
            else -> 68.dp
        }
    } else {
        when {
            viewSize <= ViewSize.SMALL -> 26.dp
            viewSize == ViewSize.NORMAL -> 34.dp
            viewSize == ViewSize.LARGE -> 44.dp
            else -> 52.dp
        }
    }

/**
 * Formats a relative temporal elapsed offset in seconds into glanceable compact sparkline X-axis notation (REQ-TRK-059.9, ATT-6695).
 *
 * <p>Formats sub-hour durations ($T &lt; 3600\text{ s}$) as `"-M:SS"` and multi-hour durations
 * ($T \ge 3600\text{ s}$) as `"-H:MM:SS"`.</p>
 */
fun formatSparklineTimeOffset(seconds: Int): String {
    val sec = seconds.coerceAtLeast(0)
    return if (sec < 3600) {
        val minutes = sec / 60
        val remainingSec = sec % 60
        "-$minutes:${remainingSec.toString().padStart(2, '0')}"
    } else {
        val hours = sec / 3600
        val rem = sec % 3600
        val minutes = rem / 60
        val remainingSec = rem % 60
        "-$hours:${minutes.toString().padStart(2, '0')}:${remainingSec.toString().padStart(2, '0')}"
    }
}

/**
 * Synthetic representative telemetry sample sequence used for layout preview when
 * a sensor is inactive or disconnected in configuration mode (REQ-TRK-059.11, ATT-7314).
 *
 * <p>Represents a realistic 24-point cardiovascular/power progression with subtle fluctuations,
 * providing the athlete with an immediate, glanceable visualization of sparkline trend rendering.</p>
 */
val DEFAULT_PREVIEW_SPARKLINE_SAMPLES: List<Float> = listOf(
    130f, 132f, 135f, 133f, 137f, 142f, 140f, 145f,
    148f, 146f, 151f, 155f, 153f, 158f, 162f, 160f,
    165f, 168f, 166f, 171f, 169f, 167f, 164f, 166f
)

/**
 * Resolves whether the sparkline curve should be rendered in subtle placeholder mode (REQ-TRK-059.11, ATT-7522).
 *
 * <p>A placeholder curve is displayed when the sensor is inactive / awaiting initial telemetry
 * in live tracking mode ([ScreenMode.TRACKING]). Configuration and preview modes display the standard
 * preview curve with full contrast.</p>
 */
fun resolveSparklineIsPlaceholder(hasLiveSamples: Boolean, screenMode: ScreenMode): Boolean =
    !hasLiveSamples && screenMode == ScreenMode.TRACKING

/**
 * Resolves effective sparkline sample data, falling back to representative preview samples when no live samples exist (REQ-TRK-059.11, ATT-7314, ATT-7522).
 */
fun resolveSparklineEffectiveSamples(liveSamples: List<Float>): List<Float> =
    if (liveSamples.isNotEmpty()) liveSamples else DEFAULT_PREVIEW_SPARKLINE_SAMPLES

/**
 * Resolves effective elapsed span in seconds, falling back to configured horizon duration when no live samples exist (REQ-TRK-059.11, ATT-7314, ATT-7522).
 */
fun resolveSparklineEffectiveSpanSeconds(liveSamples: List<Float>, liveSpanSeconds: Int, graphHorizonSeconds: Int): Int =
    if (liveSamples.isNotEmpty()) liveSpanSeconds else (if (graphHorizonSeconds > 0) graphHorizonSeconds else 300)

/**
 * Lightweight, hardware-accelerated Canvas sparkline composable rendering temporal telemetry trends
 * and glanceable X-axis labels with zero recomposition churn or snapshot state mutations (REQ-TRK-059.5, REQ-TRK-059.9, REQ-TRK-059.11, Rule 66).
 *
 * @param samples Time-series numeric telemetry values ordered chronologically.
 * @param modifier Composable layout modifier.
 * @param spanSeconds Cumulative elapsed time span represented by the sparkline buffer in seconds.
 * @param lineColor Stroke color for the sparkline trend polyline and terminal point.
 * @param fillGradient Whether to render a subtle vertical gradient fill below the polyline.
 * @param isPlaceholder Whether to render the sparkline in subtle standby/placeholder mode with dashed curve, dimmed opacity, and suppressed terminal dot.
 */
@Composable
fun SensorSparklineView(
    samples: List<Float>,
    modifier: Modifier = Modifier,
    spanSeconds: Int = 0,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillGradient: Boolean = true,
    isPlaceholder: Boolean = false
) {
    if (samples.isEmpty()) {
        return
    }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (size.width <= 0f || size.height <= 0f) {
                return@Canvas
            }

            val strokeWidthPx = 1.75.dp.toPx()
            val verticalPaddingPx = 3.dp.toPx()
            val usableHeight = (size.height - 2f * verticalPaddingPx).coerceAtLeast(1f)

            if (samples.size == 1) {
                // Single point: render a centered dot (suppressed for placeholder curves)
                if (!isPlaceholder) {
                    drawCircle(
                        color = lineColor,
                        radius = 2.5.dp.toPx(),
                        center = Offset(size.width / 2f, size.height / 2f)
                    )
                }
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
            val fillPath = if (fillGradient && !isPlaceholder) Path() else null

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

            val effectiveLineColor = if (isPlaceholder) lineColor.copy(alpha = 0.35f) else lineColor
            val strokeStyle = if (isPlaceholder) {
                Stroke(
                    width = strokeWidthPx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
                )
            } else {
                Stroke(
                    width = strokeWidthPx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            }

            // Draw trend stroke polyline
            drawPath(
                path = strokePath,
                color = effectiveLineColor,
                style = strokeStyle
            )

            // Draw terminal dot at current (latest) reading (suppressed for placeholder curves)
            if (!isPlaceholder) {
                drawCircle(
                    color = lineColor,
                    radius = 2.75.dp.toPx(),
                    center = Offset(lastX, lastY)
                )
            }
        }

        if (samples.size > 1) {
            val labelColor = if (isPlaceholder) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatSparklineTimeOffset(spanSeconds),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = labelColor,
                    maxLines = 1
                )
                Text(
                    text = formatSparklineTimeOffset(spanSeconds / 2),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = labelColor,
                    maxLines = 1
                )
                Text(
                    text = stringResource(R.string.sparkline_axis_now),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = labelColor,
                    maxLines = 1
                )
            }
        }
    }
}
