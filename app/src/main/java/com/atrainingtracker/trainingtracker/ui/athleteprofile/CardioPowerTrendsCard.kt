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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.ui.athleteprofile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.map.ChartGestureDisambiguator
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor

/**
 * Dedicated chart card rendering longitudinal season trends for 1st-order dynamical Power-to-Heart-Rate
 * parameters (HR@200W, \tau, 1/K) in Athlete Profile (REQ-REV-011 / ATT-3906).
 */
@Composable
fun CardioPowerTrendsCard(
    points: List<CardioPlotPoint>,
    rollingPoints: List<CardioPlotPoint>,
    selectedMetric: CardioTrendMetric,
    selectedTimeRange: MetricsTimeRange,
    yDomain: Pair<Double, Double>,
    onSelectMetric: (CardioTrendMetric) -> Unit,
    onSelectTimeRange: (MetricsTimeRange) -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Domain icon + Title + Info Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_cardio_load),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.cardio_power_trends_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.cardio_power_info_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Direction Note Subtitle (Rule 48 / Design Guidelines §2.6)
            val directionText = when (selectedMetric) {
                CardioTrendMetric.HR_200W -> stringResource(R.string.cardio_power_trends_direction_hr200)
                CardioTrendMetric.TAU -> stringResource(R.string.cardio_power_trends_direction_tau)
                CardioTrendMetric.SLOPE -> stringResource(R.string.cardio_power_trends_direction_slope)
            }
            Text(
                text = directionText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Metric Selector Pills (HR@200W, \tau, 1/K)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val metricOptions = listOf(
                    CardioTrendMetric.HR_200W to stringResource(R.string.cardio_power_hr200_label),
                    CardioTrendMetric.TAU to stringResource(R.string.cardio_power_tau_label),
                    CardioTrendMetric.SLOPE to stringResource(R.string.cardio_power_slope_label)
                )
                metricOptions.forEach { (metric, label) ->
                    CompactSelectorPill(
                        label = label,
                        selected = selectedMetric == metric,
                        onClick = { onSelectMetric(metric) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Time Range Selector Pills (3M, 6M, 1Y, All)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val rangeOptions = listOf(
                    MetricsTimeRange.ONE_MONTH to stringResource(R.string.athlete_profile_range_1m),
                    MetricsTimeRange.THREE_MONTHS to stringResource(R.string.athlete_profile_range_3m),
                    MetricsTimeRange.SIX_MONTHS to stringResource(R.string.athlete_profile_range_6m),
                    MetricsTimeRange.ONE_YEAR to stringResource(R.string.athlete_profile_range_1y),
                    MetricsTimeRange.ALL to stringResource(R.string.athlete_profile_range_all)
                )
                rangeOptions.forEach { (range, label) ->
                    CompactSelectorPill(
                        label = label,
                        selected = selectedTimeRange == range,
                        onClick = { onSelectTimeRange(range) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Canvas Plot Area
            if (points.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.cardio_power_trends_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                CardioPowerTrendCanvas(
                    points = points,
                    rollingPoints = rollingPoints,
                    metric = selectedMetric,
                    yDomain = yDomain,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )

                // Legend indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(3.dp)
                            .background(TTColor.Zone2, RoundedCornerShape(1.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.cardio_power_trends_rolling_trend),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CardioPowerTrendCanvas(
    points: List<CardioPlotPoint>,
    rollingPoints: List<CardioPlotPoint>,
    metric: CardioTrendMetric,
    yDomain: Pair<Double, Double>,
    modifier: Modifier = Modifier
) {
    val touchSlop = LocalViewConfiguration.current.touchSlop
    var scrubbedPoint by remember { mutableStateOf<CardioPlotPoint?>(null) }
    var scrubX by remember { mutableFloatStateOf(0f) }

    val currentPointsState by rememberUpdatedState(points)

    val pointColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    val trendLineColor = TTColor.Zone2
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cursorColor = MaterialTheme.colorScheme.onSurface
    val scrubHighlightColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier.pointerInput(points, metric) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var isDragging = false
                var isVerticalScrolling = false

                val leftPad = 54.dp.toPx()
                val rightPad = 14.dp.toPx()
                val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)

                val initialPoint = CardioPowerTrendsPlotMath.findNearestPoint(down.position.x, leftPad, plotW, currentPointsState)
                if (initialPoint != null) {
                    scrubbedPoint = initialPoint
                    scrubX = down.position.x
                }

                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) {
                        scrubbedPoint = null
                        break
                    }

                    if (pressed.size == 1) {
                        val pointer = pressed[0]
                        val diffX = pointer.position.x - down.position.x
                        val diffY = pointer.position.y - down.position.y

                        if (!isDragging && !isVerticalScrolling) {
                            if (ChartGestureDisambiguator.isDominantVertical(diffX, diffY, touchSlop)) {
                                isVerticalScrolling = true
                                scrubbedPoint = null
                            } else if (ChartGestureDisambiguator.isDominantHorizontal(diffX, diffY, touchSlop)) {
                                isDragging = true
                            }
                        }

                        if (isDragging) {
                            pointer.consume()
                            scrubX = pointer.position.x
                            scrubbedPoint = CardioPowerTrendsPlotMath.findNearestPoint(
                                pointer.position.x,
                                leftPad,
                                plotW,
                                currentPointsState
                            )
                        }
                    }
                }
            }
        }
    ) {
        val leftPad = 54.dp.toPx()
        val rightPad = 14.dp.toPx()
        val topPad = 16.dp.toPx()
        val bottomPad = 24.dp.toPx()

        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        val (yMin, yMax) = yDomain
        val yRange = (yMax - yMin).coerceAtLeast(1e-3)

        val minTs = points.first().timestampMs
        val maxTs = points.last().timestampMs
        val tsSpan = (maxTs - minTs).coerceAtLeast(1L)

        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = labelColor.toArgb()
            textSize = 10.sp.toPx()
        }

        // Draw 3 horizontal grid lines + Y-axis scale markers (Rule 48)
        val gridSteps = 2
        for (i in 0..gridSteps) {
            val frac = i.toFloat() / gridSteps.toFloat()
            val y = topPad + plotH * (1f - frac)
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(leftPad + plotW, y),
                strokeWidth = 1.dp.toPx()
            )
            val v = yMin + frac * yRange
            val label = CardioPowerTrendsPlotMath.formatYAxisValue(v, metric)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                4.dp.toPx(),
                y + 4.dp.toPx(),
                textPaint
            )
        }

        // 1. Scatter points
        points.forEach { pt ->
            val x = if (points.size == 1 || maxTs == minTs) {
                leftPad + plotW * 0.5f
            } else {
                leftPad + ((pt.timestampMs - minTs).toDouble() / tsSpan.toDouble()).toFloat() * plotW
            }
            val normY = ((pt.value - yMin) / yRange).toFloat().coerceIn(0f, 1f)
            val y = topPad + plotH * (1f - normY)
            drawCircle(
                color = pointColor,
                radius = 3.5.dp.toPx(),
                center = Offset(x, y)
            )
        }

        // 2. Rolling 30-day trend curve
        if (rollingPoints.size >= 2) {
            val canvasTrendPoints = rollingPoints.map { pt ->
                val x = if (maxTs == minTs) {
                    leftPad + plotW * 0.5f
                } else {
                    leftPad + ((pt.timestampMs - minTs).toDouble() / tsSpan.toDouble()).toFloat() * plotW
                }
                val normY = ((pt.value - yMin) / yRange).toFloat().coerceIn(0f, 1f)
                val y = topPad + plotH * (1f - normY)
                Offset(x, y)
            }

            val strokePath = Path().apply {
                moveTo(canvasTrendPoints.first().x, canvasTrendPoints.first().y)
                for (i in 1 until canvasTrendPoints.size) {
                    lineTo(canvasTrendPoints[i].x, canvasTrendPoints[i].y)
                }
            }
            drawPath(
                path = strokePath,
                color = trendLineColor,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // 3. X-axis date ticks with intermediate ticks across multi-month spans (Rule 48)
        if (points.size == 1 || minTs == maxTs) {
            val dateStr = CardioPowerTrendsPlotMath.formatAxisDate(minTs, tsSpan)
            val w = textPaint.measureText(dateStr)
            drawContext.canvas.nativeCanvas.drawText(
                dateStr,
                (leftPad + plotW * 0.5f - w * 0.5f).coerceAtLeast(leftPad),
                size.height - 4.dp.toPx(),
                textPaint
            )
        } else {
            val tickSteps = if (tsSpan > 60L * CardioPowerTrendsPlotMath.MILLIS_PER_DAY) 3 else 2
            for (k in 0..tickSteps) {
                val frac = k.toFloat() / tickSteps.toFloat()
                val tickTs = minTs + (tsSpan * frac).toLong()
                val x = leftPad + frac * plotW
                val dateStr = CardioPowerTrendsPlotMath.formatAxisDate(tickTs, tsSpan)
                val w = textPaint.measureText(dateStr)
                val drawX = (x - w * 0.5f).coerceIn(leftPad, leftPad + plotW - w)
                drawContext.canvas.nativeCanvas.drawText(
                    dateStr,
                    drawX,
                    size.height - 4.dp.toPx(),
                    textPaint
                )
            }
        }

        // 4. Touch Scrubbing Tooltip & Vertical Cursor
        val activeScrub = scrubbedPoint
        if (activeScrub != null) {
            val cursorX = if (maxTs == minTs) {
                leftPad + plotW * 0.5f
            } else {
                leftPad + ((activeScrub.timestampMs - minTs).toDouble() / tsSpan.toDouble()).toFloat() * plotW
            }

            drawLine(
                color = cursorColor.copy(alpha = 0.70f),
                start = Offset(cursorX, topPad),
                end = Offset(cursorX, topPad + plotH),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
            )

            val normY = ((activeScrub.value - yMin) / yRange).toFloat().coerceIn(0f, 1f)
            val cursorY = topPad + plotH * (1f - normY)
            drawCircle(
                color = scrubHighlightColor,
                radius = 5.dp.toPx(),
                center = Offset(cursorX, cursorY)
            )

            val dateStr = CardioPowerTrendsPlotMath.formatAxisDate(activeScrub.timestampMs, tsSpan)
            val valStr = CardioPowerTrendsPlotMath.formatYAxisValue(activeScrub.value, metric)
            val tooltipText = "$dateStr: $valStr"
            val tooltipPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = labelColor.toArgb()
                textSize = 10.sp.toPx()
                isFakeBoldText = true
            }
            val tooltipW = tooltipPaint.measureText(tooltipText)
            val tooltipX = (cursorX - tooltipW * 0.5f).coerceIn(leftPad, leftPad + plotW - tooltipW)
            drawContext.canvas.nativeCanvas.drawText(
                tooltipText,
                tooltipX,
                topPad - 3.dp.toPx(),
                tooltipPaint
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewCardioPowerTrendsCard() {
    ATrainingTrackerTheme {
        val baseMs = 1700000000000L
        val dayMs = 86_400_000L
        val points = listOf(
            CardioPlotPoint(baseMs, 148.0, 1L),
            CardioPlotPoint(baseMs + 10 * dayMs, 142.0, 2L),
            CardioPlotPoint(baseMs + 25 * dayMs, 138.0, 3L),
            CardioPlotPoint(baseMs + 40 * dayMs, 135.0, 4L),
            CardioPlotPoint(baseMs + 60 * dayMs, 131.0, 5L)
        )
        val rolling = CardioPowerTrendsPlotMath.computeRollingTrend(points)
        CardioPowerTrendsCard(
            points = points,
            rollingPoints = rolling,
            selectedMetric = CardioTrendMetric.HR_200W,
            selectedTimeRange = MetricsTimeRange.ALL,
            yDomain = 125.0 to 155.0,
            onSelectMetric = {},
            onSelectTimeRange = {},
            onInfoClick = {}
        )
    }
}
