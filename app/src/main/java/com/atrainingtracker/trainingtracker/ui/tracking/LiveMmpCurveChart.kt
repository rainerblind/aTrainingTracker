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

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Benchmark interval definitions for Mean Maximal Power (MMP) analysis.
 *
 * @property durationSeconds Interval duration in seconds.
 * @property label Short label for axis and callout badges.
 */
enum class MmpBenchmark(val durationSeconds: Int, val label: String) {
    SPRINT_5S(5, "5s"),
    ANAEROBIC_1M(60, "1m"),
    VO2MAX_5M(300, "5m"),
    FTP_20M(1200, "20m");

    companion object {
        val ALL = entries
        const val MIN_DURATION_SEC = 1
        const val MAX_DURATION_SEC = 7200

        /**
         * Maps duration in seconds to a normalized logarithmic fraction [0.0..1.0].
         */
        fun durationToLogFraction(
            seconds: Int,
            minSec: Int = MIN_DURATION_SEC,
            maxSec: Int = MAX_DURATION_SEC
        ): Float {
            val clamped = seconds.coerceIn(minSec, maxSec).toDouble()
            val logMin = ln(minSec.toDouble())
            val logMax = ln(maxSec.toDouble())
            return ((ln(clamped) - logMin) / (logMax - logMin)).toFloat().coerceIn(0f, 1f)
        }
    }
}

/**
 * Telemetry model holding real-time Mean Maximal Power (MMP) data for chart presentation.
 *
 * @property rollingMeans Map of benchmark duration in seconds to current rolling average power in Watts.
 * @property peakPowers Map of benchmark duration in seconds to session peak sustained power in Watts.
 * @property instantaneousPower Current 1-second cycling power in Watts.
 */
data class LiveMmpCurveData(
    val rollingMeans: Map<Int, Double> = emptyMap(),
    val peakPowers: Map<Int, Double> = emptyMap(),
    val instantaneousPower: Double? = null
)

/**
 * Real-time vector Mean Maximal Power (MMP) curve chart composable (REQ-TRK-058.4).
 *
 * Renders an interactive logarithmic time scale (1s to 2h) with dynamic vector curve strokes
 * for session peak powers, current rolling duration averages, and prominent benchmark markers
 * (5s, 1m, 5m, 20m). Adheres strictly to Rule 66 (zero snapshot state writes during composition).
 */
@Composable
fun LiveMmpCurveChart(
    curveData: LiveMmpCurveData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.mmp_curve),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                LiveMmpCurveCanvas(
                    curveData = curveData,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun LiveMmpCurveCanvas(
    curveData: LiveMmpCurveData,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    val textPaint = remember(density, onSurfaceVariantColor) {
        Paint().apply {
            color = onSurfaceVariantColor.toArgb()
            textSize = with(density) { 10.sp.toPx() }
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
    }

    val valuePaint = remember(density, primaryColor) {
        Paint().apply {
            color = primaryColor.toArgb()
            textSize = with(density) { 9.sp.toPx() }
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
    }

    Canvas(modifier = modifier) {
        val leftPadding = 32.dp.toPx()
        val rightPadding = 16.dp.toPx()
        val topPadding = 18.dp.toPx()
        val bottomPadding = 20.dp.toPx()

        val plotWidth = (size.width - leftPadding - rightPadding).coerceAtLeast(1f)
        val plotHeight = (size.height - topPadding - bottomPadding).coerceAtLeast(1f)

        // Determine max power with 15% headroom
        val allWatts = curveData.peakPowers.values + curveData.rollingMeans.values +
                listOfNotNull(curveData.instantaneousPower)
        val maxObserved = allWatts.maxOrNull() ?: 200.0
        val maxWatts = max(400.0, maxObserved * 1.15)

        // Draw horizontal grid lines (Y-axis)
        val ySteps = 3
        for (i in 0..ySteps) {
            val frac = i.toFloat() / ySteps
            val y = topPadding + (1f - frac) * plotHeight
            val wattsLabel = (frac * maxWatts).roundToInt().toString() + "W"

            drawLine(
                color = outlineColor.copy(alpha = 0.4f),
                start = Offset(leftPadding, y),
                end = Offset(size.width - rightPadding, y),
                strokeWidth = 1.dp.toPx()
            )

            drawContext.canvas.nativeCanvas.drawText(
                wattsLabel,
                leftPadding - 4.dp.toPx(),
                y + 3.dp.toPx(),
                Paint(textPaint).apply { textAlign = Paint.Align.RIGHT }
            )
        }

        // Draw benchmark duration vertical grid lines & X-axis labels
        MmpBenchmark.ALL.forEach { benchmark ->
            val frac = MmpBenchmark.durationToLogFraction(benchmark.durationSeconds)
            val x = leftPadding + frac * plotWidth

            drawLine(
                color = outlineColor.copy(alpha = 0.35f),
                start = Offset(x, topPadding),
                end = Offset(x, topPadding + plotHeight),
                strokeWidth = 1.dp.toPx()
            )

            drawContext.canvas.nativeCanvas.drawText(
                benchmark.label,
                x,
                size.height - 4.dp.toPx(),
                textPaint
            )
        }

        // Map benchmark points for curve drawing
        val benchmarkSeconds = listOf(5, 60, 300, 1200)

        // 1. Session Peak Power Curve
        val peakPoints = benchmarkSeconds.mapNotNull { sec ->
            val watts = curveData.peakPowers[sec]
            if (watts != null && watts > 0.0) {
                val fracX = MmpBenchmark.durationToLogFraction(sec)
                val x = leftPadding + fracX * plotWidth
                val fracY = (watts / maxWatts).toFloat().coerceIn(0f, 1f)
                val y = topPadding + (1f - fracY) * plotHeight
                Offset(x, y) to watts
            } else null
        }

        if (peakPoints.isNotEmpty()) {
            val peakPath = Path().apply {
                moveTo(peakPoints.first().first.x, peakPoints.first().first.y)
                for (i in 1 until peakPoints.size) {
                    lineTo(peakPoints[i].first.x, peakPoints[i].first.y)
                }
            }

            // Fill gradient under peak curve
            val fillPath = Path().apply {
                addPath(peakPath)
                lineTo(peakPoints.last().first.x, topPadding + plotHeight)
                lineTo(peakPoints.first().first.x, topPadding + plotHeight)
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

            // Stroke peak curve
            drawPath(
                path = peakPath,
                color = primaryColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Draw peak marker pins with Watt labels
            peakPoints.forEach { (offset, watts) ->
                drawCircle(
                    color = primaryColor,
                    radius = 4.dp.toPx(),
                    center = offset
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.dp.toPx(),
                    center = offset
                )

                val label = "${watts.roundToInt()}W"
                val textY = (offset.y - 6.dp.toPx()).coerceAtLeast(topPadding - 2.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    offset.x,
                    textY,
                    valuePaint
                )
            }
        }

        // 2. Rolling Means Curve
        val rollingPoints = benchmarkSeconds.mapNotNull { sec ->
            val watts = curveData.rollingMeans[sec]
            if (watts != null && watts > 0.0) {
                val fracX = MmpBenchmark.durationToLogFraction(sec)
                val x = leftPadding + fracX * plotWidth
                val fracY = (watts / maxWatts).toFloat().coerceIn(0f, 1f)
                val y = topPadding + (1f - fracY) * plotHeight
                Offset(x, y)
            } else null
        }

        if (rollingPoints.size >= 2) {
            val rollingPath = Path().apply {
                moveTo(rollingPoints.first().x, rollingPoints.first().y)
                for (i in 1 until rollingPoints.size) {
                    lineTo(rollingPoints[i].x, rollingPoints[i].y)
                }
            }
            drawPath(
                path = rollingPath,
                color = secondaryColor.copy(alpha = 0.85f),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            rollingPoints.forEach { offset ->
                drawCircle(
                    color = secondaryColor,
                    radius = 2.5.dp.toPx(),
                    center = offset
                )
            }
        }
    }
}
