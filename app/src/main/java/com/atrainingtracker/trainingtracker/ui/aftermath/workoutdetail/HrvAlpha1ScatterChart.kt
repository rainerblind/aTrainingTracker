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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.ui.tracking.HrvZoneIndicator
import com.google.android.gms.maps.model.LatLng
import java.util.Locale
import kotlin.math.abs

/**
 * Intensity mode for the DFA alpha 1 scatter plot (REQ-PER-035.4).
 */
enum class HrvScatterIntensityMode {
    HEART_RATE,
    POWER
}

/**
 * Represents a single artifact-free telemetry sample plotted on the scatter canvas.
 */
data class HrvScatterPoint(
    val intensity: Double,
    val alpha1: Double,
    val timeSec: Long
)

/**
 * Ordinary Least Squares linear regression result for alpha1 vs. intensity (REQ-PER-035.4).
 * Model: alpha1 = slope * intensity + intercept
 */
data class HrvScatterRegression(
    val slope: Double,
    val intercept: Double,
    val rSquared: Double,
    val intensityAt075: Double?,
    val intensityAt050: Double?
)

/**
 * Mathematical calculations and point filtering for DFA alpha 1 scatter analysis.
 */
object HrvScatterMath {

    const val MIN_ALPHA1 = 0.35
    const val MAX_ALPHA1 = 1.15
    const val MAX_ARTIFACT_RATE = 5.0

    /**
     * Filters track points strictly within valid physiological transition bounds
     * (alpha1 in [0.35, 1.15], artifact rate <= 5.0%, non-null intensity).
     */
    fun filterScatterPoints(
        points: List<PathPoint>,
        mode: HrvScatterIntensityMode
    ): List<HrvScatterPoint> {
        return points.mapNotNull { pt ->
            val a1 = pt.hrvDfaA1 ?: return@mapNotNull null
            if (a1 < MIN_ALPHA1 || a1 > MAX_ALPHA1) return@mapNotNull null
            val artifact = pt.hrvArtifactRate
            if (artifact != null && artifact > MAX_ARTIFACT_RATE) return@mapNotNull null

            val intensity = when (mode) {
                HrvScatterIntensityMode.HEART_RATE -> pt.hr?.takeIf { it > 0 }?.toDouble()
                HrvScatterIntensityMode.POWER -> pt.power?.takeIf { it > 0 }?.toDouble()
            } ?: return@mapNotNull null

            HrvScatterPoint(intensity = intensity, alpha1 = a1, timeSec = pt.timeSec)
        }
    }

    /**
     * Computes Ordinary Least Squares (OLS) linear regression and threshold crossing estimates.
     */
    fun calculateLinearRegression(points: List<HrvScatterPoint>): HrvScatterRegression? {
        if (points.size < 2) return null

        val n = points.size.toDouble()
        val meanX = points.sumOf { it.intensity } / n
        val meanY = points.sumOf { it.alpha1 } / n

        var ssXx = 0.0
        var ssYy = 0.0
        var ssXy = 0.0

        for (p in points) {
            val dx = p.intensity - meanX
            val dy = p.alpha1 - meanY
            ssXx += dx * dx
            ssYy += dy * dy
            ssXy += dx * dy
        }

        if (ssXx < 1e-6) return null

        val slope = ssXy / ssXx
        val intercept = meanY - slope * meanX
        val rSquared = if (ssYy > 1e-6) {
            ((ssXy * ssXy) / (ssXx * ssYy)).coerceIn(0.0, 1.0)
        } else 0.0

        val intensity075 = if (abs(slope) > 1e-6) (0.75 - intercept) / slope else null
        val intensity050 = if (abs(slope) > 1e-6) (0.50 - intercept) / slope else null

        return HrvScatterRegression(
            slope = slope,
            intercept = intercept,
            rSquared = rSquared,
            intensityAt075 = intensity075,
            intensityAt050 = intensity050
        )
    }
}

/**
 * Interactive DFA alpha 1 vs. Intensity (Heart Rate / Cycling Power) scatter plot composable
 * conforming to REQ-PER-035.4, Rule 48, and Rule 64.
 */
@Composable
fun HrvAlpha1ScatterChart(
    points: List<PathPoint>,
    intensityMode: HrvScatterIntensityMode = HrvScatterIntensityMode.HEART_RATE,
    modifier: Modifier = Modifier
) {
    val scatterPoints = remember(points, intensityMode) {
        HrvScatterMath.filterScatterPoints(points, intensityMode)
    }

    val regression = remember(scatterPoints) {
        HrvScatterMath.calculateLinearRegression(scatterPoints)
    }

    val colorScheme = MaterialTheme.colorScheme

    val axisTextPaint = remember(colorScheme) {
        Paint().apply {
            color = colorScheme.onSurfaceVariant.toArgb()
            textSize = 26f
            isAntiAlias = true
        }
    }

    val thresholdLabelPaint = remember(colorScheme) {
        Paint().apply {
            color = colorScheme.onSurfaceVariant.toArgb()
            textSize = 22f
            isAntiAlias = true
        }
    }

    if (scatterPoints.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp)
                .testTag("hrv_scatter_chart_empty"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.hrv_scatter_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val minIntensity = remember(scatterPoints) {
        (scatterPoints.minOf { it.intensity } - 5.0).coerceAtLeast(0.0)
    }
    val maxIntensity = remember(scatterPoints) {
        (scatterPoints.maxOf { it.intensity } + 5.0).coerceAtLeast(minIntensity + 10.0)
    }

    val minAlpha1 = 0.30
    val maxAlpha1 = 1.20

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .testTag("hrv_scatter_chart_canvas")
    ) {
        val startPaddingPx = 50.dp.toPx()
        val endPaddingPx = 25.dp.toPx()
        val topPaddingPx = 12.dp.toPx()
        val bottomPaddingPx = 24.dp.toPx()

        val chartWidthPx = (size.width - startPaddingPx - endPaddingPx).coerceAtLeast(1f)
        val chartHeightPx = (size.height - topPaddingPx - bottomPaddingPx).coerceAtLeast(1f)

        fun intensityToX(intensity: Double): Float {
            val ratio = ((intensity - minIntensity) / (maxIntensity - minIntensity)).coerceIn(0.0, 1.0)
            return (startPaddingPx + ratio * chartWidthPx).toFloat()
        }

        fun alpha1ToY(a1: Double): Float {
            val ratio = ((a1 - minAlpha1) / (maxAlpha1 - minAlpha1)).coerceIn(0.0, 1.0)
            return (topPaddingPx + (1.0 - ratio) * chartHeightPx).toFloat()
        }

        val nativeCanvas = drawContext.canvas.nativeCanvas

        // 1. Draw horizontal threshold lines (0.75 and 0.50)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
        val y075 = alpha1ToY(0.75)
        val y050 = alpha1ToY(0.50)

        // Threshold 0.75 line (Aerobic / T1)
        drawLine(
            color = TTColor.Zone2.copy(alpha = 0.45f),
            start = Offset(startPaddingPx, y075),
            end = Offset(startPaddingPx + chartWidthPx, y075),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = dashEffect
        )

        // Threshold 0.50 line (Anaerobic / T2)
        drawLine(
            color = TTColor.Zone4.copy(alpha = 0.45f),
            start = Offset(startPaddingPx, y050),
            end = Offset(startPaddingPx + chartWidthPx, y050),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = dashEffect
        )

        // Draw threshold labels on Y-axis
        nativeCanvas.drawText("0.75", 6.dp.toPx(), y075 + 8f, thresholdLabelPaint)
        nativeCanvas.drawText("0.50", 6.dp.toPx(), y050 + 8f, thresholdLabelPaint)

        // 2. Draw border lines
        drawLine(
            color = colorScheme.outlineVariant.copy(alpha = 0.35f),
            start = Offset(startPaddingPx, topPaddingPx),
            end = Offset(startPaddingPx + chartWidthPx, topPaddingPx),
            strokeWidth = 1f
        )
        drawLine(
            color = colorScheme.outlineVariant.copy(alpha = 0.35f),
            start = Offset(startPaddingPx, topPaddingPx + chartHeightPx),
            end = Offset(startPaddingPx + chartWidthPx, topPaddingPx + chartHeightPx),
            strokeWidth = 1f
        )

        // 3. Draw X-axis labels
        val unitLabel = if (intensityMode == HrvScatterIntensityMode.HEART_RATE) "bpm" else "W"
        val minXLabel = "${minIntensity.toInt()} $unitLabel"
        val maxXLabel = "${maxIntensity.toInt()} $unitLabel"
        val maxXLabelWidth = axisTextPaint.measureText(maxXLabel)

        nativeCanvas.drawText(minXLabel, startPaddingPx, size.height - 4.dp.toPx(), axisTextPaint)
        nativeCanvas.drawText(maxXLabel, startPaddingPx + chartWidthPx - maxXLabelWidth, size.height - 4.dp.toPx(), axisTextPaint)

        // 4. Draw clipped scatter points and linear regression line
        clipRect(
            left = startPaddingPx,
            top = topPaddingPx,
            right = startPaddingPx + chartWidthPx,
            bottom = topPaddingPx + chartHeightPx
        ) {
            // Regression line
            if (regression != null) {
                val x1 = minIntensity
                val y1Val = regression.slope * x1 + regression.intercept
                val x2 = maxIntensity
                val y2Val = regression.slope * x2 + regression.intercept

                drawLine(
                    color = colorScheme.primary,
                    start = Offset(intensityToX(x1), alpha1ToY(y1Val)),
                    end = Offset(intensityToX(x2), alpha1ToY(y2Val)),
                    strokeWidth = 2.dp.toPx()
                )

                // Optional vertical threshold guides
                regression.intensityAt075?.let { int075 ->
                    if (int075 in minIntensity..maxIntensity) {
                        val x075 = intensityToX(int075)
                        drawLine(
                            color = TTColor.Zone2.copy(alpha = 0.35f),
                            start = Offset(x075, y075),
                            end = Offset(x075, topPaddingPx + chartHeightPx),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )
                    }
                }

                regression.intensityAt050?.let { int050 ->
                    if (int050 in minIntensity..maxIntensity) {
                        val x050 = intensityToX(int050)
                        drawLine(
                            color = TTColor.Zone4.copy(alpha = 0.35f),
                            start = Offset(x050, y050),
                            end = Offset(x050, topPaddingPx + chartHeightPx),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )
                    }
                }
            }

            // Scatter points
            val dotRadius = 3.dp.toPx()
            for (p in scatterPoints) {
                val px = intensityToX(p.intensity)
                val py = alpha1ToY(p.alpha1)
                val dotColor = HrvZoneIndicator.calculateZoneColor(p.alpha1)

                drawCircle(
                    color = dotColor.copy(alpha = 0.65f),
                    radius = dotRadius,
                    center = Offset(px, py)
                )
            }
        }
    }
}

@Preview(name = "HRV Alpha1 Scatter Chart Preview", showBackground = true)
@Composable
private fun HrvAlpha1ScatterChartPreview() {
    val samplePoints = listOf(
        PathPoint(distance = 0.0, latLng = LatLng(0.0, 0.0), hr = 120, power = 160, hrvDfaA1 = 0.95, hrvArtifactRate = 0.0),
        PathPoint(distance = 100.0, latLng = LatLng(0.0, 0.0), hr = 135, power = 190, hrvDfaA1 = 0.82, hrvArtifactRate = 1.0),
        PathPoint(distance = 200.0, latLng = LatLng(0.0, 0.0), hr = 145, power = 210, hrvDfaA1 = 0.74, hrvArtifactRate = 0.5),
        PathPoint(distance = 300.0, latLng = LatLng(0.0, 0.0), hr = 155, power = 240, hrvDfaA1 = 0.61, hrvArtifactRate = 2.0),
        PathPoint(distance = 400.0, latLng = LatLng(0.0, 0.0), hr = 168, power = 280, hrvDfaA1 = 0.48, hrvArtifactRate = 1.2)
    )

    ATrainingTrackerTheme {
        HrvAlpha1ScatterChart(points = samplePoints)
    }
}
