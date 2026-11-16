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

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ColorScheme
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.settings.TelemetryGraphColorPreferences
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.ui.theme.TelemetryGraphColor

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
 * @param lineColor Stroke color for the sparkline trend polyline and terminal point when solid rendering is used.
 * @param fillGradient Whether to render a subtle vertical gradient fill below the polyline.
 * @param isPlaceholder Whether to render the sparkline in subtle standby/placeholder mode with dashed curve, dimmed opacity, and suppressed terminal dot.
 * @param zoneThresholds Optional zone threshold specification for multi-color Dynamic Zone gradient rendering (REQ-TRK-082.2).
 */
@Composable
fun SensorSparklineView(
    samples: List<Float>,
    modifier: Modifier = Modifier,
    spanSeconds: Int = 0,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillGradient: Boolean = true,
    isPlaceholder: Boolean = false,
    zoneThresholds: SparklineZoneThresholds? = null
) {
    if (samples.isEmpty()) {
        return
    }

    val colorScheme = MaterialTheme.colorScheme
    val fallbackLineColor = resolveSparklineZoneContrastColor(lineColor, colorScheme)
    val resolvedStyling = remember(samples, isPlaceholder, zoneThresholds, colorScheme, fallbackLineColor) {
        resolveSparklineDynamicStyling(
            samples = samples,
            isPlaceholder = isPlaceholder,
            zoneThresholds = zoneThresholds,
            colorScheme = colorScheme,
            fallbackLineColor = fallbackLineColor
        )
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
                        color = resolvedStyling.terminalDotColor,
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

            val gradientStops = resolvedStyling.gradientStops
            val gradientEndY = (size.height - verticalPaddingPx).coerceAtLeast(verticalPaddingPx + 1f)

            // Draw gradient fill below polyline
            if (fillPath != null) {
                fillPath.lineTo(size.width, size.height)
                fillPath.close()
                val fillBrush = if (gradientStops != null) {
                    val fillStops = Array(gradientStops.size) { idx ->
                        val (stop, color) = gradientStops[idx]
                        val alpha = (0.28f - 0.18f * stop).coerceIn(0.08f, 0.28f)
                        stop to color.copy(alpha = alpha)
                    }
                    Brush.verticalGradient(
                        colorStops = fillStops,
                        startY = verticalPaddingPx,
                        endY = gradientEndY
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            resolvedStyling.solidColor.copy(alpha = 0.28f),
                            resolvedStyling.solidColor.copy(alpha = 0.02f)
                        ),
                        startY = 0f,
                        endY = size.height
                    )
                }
                drawPath(path = fillPath, brush = fillBrush)
            }

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
            if (!isPlaceholder && gradientStops != null) {
                drawPath(
                    path = strokePath,
                    brush = Brush.verticalGradient(
                        colorStops = gradientStops.toTypedArray(),
                        startY = verticalPaddingPx,
                        endY = gradientEndY
                    ),
                    style = strokeStyle
                )
            } else {
                val strokeColor = if (isPlaceholder) {
                    resolvedStyling.solidColor.copy(alpha = 0.35f)
                } else {
                    resolvedStyling.solidColor
                }
                drawPath(
                    path = strokePath,
                    color = strokeColor,
                    style = strokeStyle
                )
            }

            // Draw terminal dot at current (latest) reading (suppressed for placeholder curves)
            if (!isPlaceholder) {
                drawCircle(
                    color = resolvedStyling.terminalDotColor,
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

/**
 * Resolves the sparkline stroke color for a cockpit sensor field (REQ-TRK-081.2, REQ-TRK-082.1, ATT-7760, ATT-7761).
 *
 * Completely decoupled from text zone coloring (`showTextColor`):
 * - "DYNAMIC_ZONE": active physiological zone color (with dark-surface [TTColor.Zone7] contrast substitution), falling back to primary.
 * - "THEME_DEFAULT": [ColorScheme.primary].
 * - "GLOBAL_DEFAULT": global preference store for the sensor type, falling back to [ColorScheme.primary] when `sensorType == null`.
 * - Explicit 8-color palette token: [TelemetryGraphColor.valueOf(token).resolveColor(colorScheme, isDark)].
 */
fun resolveSparklineLineColor(
    graphColor: String?,
    zoneColor: Color,
    sensorType: SensorType?,
    colorScheme: ColorScheme,
    context: Context? = null
): Color {
    val token = graphColor ?: "GLOBAL_DEFAULT"
    return when (token) {
        "DYNAMIC_ZONE" -> {
            if (zoneColor != Color.Transparent) {
                resolveSparklineZoneContrastColor(zoneColor, colorScheme)
            } else {
                colorScheme.primary
            }
        }
        "THEME_DEFAULT" -> colorScheme.primary
        "GLOBAL_DEFAULT" -> {
            if (sensorType != null) {
                val isDark = TelemetryGraphColor.isDarkSurface(colorScheme)
                if (context != null) {
                    val prefColor = TelemetryGraphColorPreferences.getGraphColor(context, sensorType)
                    prefColor.resolveColor(colorScheme, isDark)
                } else {
                    val defaultColor = TelemetryGraphColorPreferences.getDefaultColor(sensorType)
                    defaultColor.resolveColor(colorScheme, isDark)
                }
            } else {
                colorScheme.primary
            }
        }
        else -> {
            try {
                val isDark = TelemetryGraphColor.isDarkSurface(colorScheme)
                TelemetryGraphColor.valueOf(token).resolveColor(colorScheme, isDark)
            } catch (ignored: IllegalArgumentException) {
                colorScheme.primary
            }
        }
    }
}

private const val GRADIENT_STOP_TRANSITION_EPSILON = 0.002f

internal data class SparklineDynamicStyling(
    val gradientStops: List<Pair<Float, Color>>?,
    val solidColor: Color,
    val terminalDotColor: Color
)

/**
 * Substitutes a high-contrast dark-surface color when [TTColor.Zone7] (`#000000`) is rendered
 * on dark or AMOLED tiles (REQ-TRK-082.3, ATT-7761).
 */
fun resolveSparklineZoneContrastColor(color: Color, colorScheme: ColorScheme): Color =
    if (color == TTColor.Zone7 && TelemetryGraphColor.isDarkSurface(colorScheme)) {
        colorScheme.onSurface
    } else {
        color
    }

/**
 * Resolves the zone [Color] for a single telemetry [value] given [thresholds] and [colorScheme]
 * (REQ-TRK-082.2, REQ-TRK-082.3, ATT-7761).
 */
fun resolveSparklineZoneColorForValue(
    value: Float,
    thresholds: SparklineZoneThresholds,
    colorScheme: ColorScheme
): Color? {
    if (!value.isFinite() || thresholds.zoneColors.isEmpty() || thresholds.zoneColors.size != thresholds.cutoffs.size + 1) {
        return null
    }
    val index = if (thresholds.invertedPolarity && thresholds.cutoffs.size == 2) {
        val t2 = thresholds.cutoffs[0]
        val t1 = thresholds.cutoffs[1]
        when {
            value < t2 -> 0
            value <= t1 -> 1
            else -> 2
        }
    } else {
        var idx = thresholds.cutoffs.size
        for (i in thresholds.cutoffs.indices) {
            if (value <= thresholds.cutoffs[i]) {
                idx = i
                break
            }
        }
        idx
    }
    return resolveSparklineZoneContrastColor(thresholds.zoneColors[index], colorScheme)
}

/**
 * Maps [thresholds] onto monotonically non-decreasing `[0f, 1f]` `Brush.verticalGradient` color
 * stops spanning `[minVal, maxVal]` where `0f` aligns to `maxVal` (top) and `1f` aligns to
 * `minVal` (bottom) (REQ-TRK-082.2, REQ-TRK-082.3, ATT-7761).
 *
 * Returns `null` when `[minVal, maxVal]` has zero/invalid span or [thresholds] is malformed.
 */
fun buildDynamicZoneVerticalGradientStops(
    minVal: Float,
    maxVal: Float,
    thresholds: SparklineZoneThresholds,
    colorScheme: ColorScheme
): List<Pair<Float, Color>>? {
    if (!minVal.isFinite() || !maxVal.isFinite() || maxVal <= minVal) {
        return null
    }
    if (thresholds.zoneColors.isEmpty() || thresholds.zoneColors.size != thresholds.cutoffs.size + 1) {
        return null
    }
    for (i in 1 until thresholds.cutoffs.size) {
        if (thresholds.cutoffs[i] < thresholds.cutoffs[i - 1]) {
            return null
        }
    }

    val range = maxVal - minVal
    val topColor = resolveSparklineZoneColorForValue(maxVal, thresholds, colorScheme) ?: return null
    val bottomColor = resolveSparklineZoneColorForValue(minVal, thresholds, colorScheme) ?: return null

    val stops = mutableListOf<Pair<Float, Color>>()
    stops.add(0f to topColor)
    var lastStop = 0f

    // Traverse cutoffs strictly inside (minVal, maxVal) from highest (near top = 0f) to lowest (near bottom = 1f)
    for (i in thresholds.cutoffs.indices.reversed()) {
        val cutoff = thresholds.cutoffs[i]
        if (cutoff > minVal && cutoff < maxVal) {
            val fraction = ((maxVal - cutoff) / range).coerceIn(0f, 1f)
            val upperColor = resolveSparklineZoneContrastColor(thresholds.zoneColors[i + 1], colorScheme)
            val lowerColor = resolveSparklineZoneContrastColor(thresholds.zoneColors[i], colorScheme)
            val upperStop = (fraction - GRADIENT_STOP_TRANSITION_EPSILON).coerceIn(lastStop, 1f)
            val lowerStop = (fraction + GRADIENT_STOP_TRANSITION_EPSILON).coerceIn(upperStop, 1f)
            stops.add(upperStop to upperColor)
            stops.add(lowerStop to lowerColor)
            lastStop = lowerStop
        }
    }

    stops.add(1f to bottomColor)
    return stops
}

internal fun resolveSparklineDynamicStyling(
    samples: List<Float>,
    isPlaceholder: Boolean,
    zoneThresholds: SparklineZoneThresholds?,
    colorScheme: ColorScheme,
    fallbackLineColor: Color
): SparklineDynamicStyling {
    if (isPlaceholder || zoneThresholds == null || samples.isEmpty()) {
        return SparklineDynamicStyling(
            gradientStops = null,
            solidColor = fallbackLineColor,
            terminalDotColor = fallbackLineColor
        )
    }

    if (samples.size == 1) {
        val singleColor = resolveSparklineZoneColorForValue(samples[0], zoneThresholds, colorScheme)
            ?: fallbackLineColor
        return SparklineDynamicStyling(
            gradientStops = null,
            solidColor = singleColor,
            terminalDotColor = singleColor
        )
    }

    var minVal = Float.MAX_VALUE
    var maxVal = -Float.MAX_VALUE
    for (v in samples) {
        if (v < minVal) minVal = v
        if (v > maxVal) maxVal = v
    }

    val terminalColor = resolveSparklineZoneColorForValue(samples.last(), zoneThresholds, colorScheme)
        ?: fallbackLineColor

    if (!minVal.isFinite() || !maxVal.isFinite() || maxVal <= minVal) {
        return SparklineDynamicStyling(
            gradientStops = null,
            solidColor = terminalColor,
            terminalDotColor = terminalColor
        )
    }

    val stops = buildDynamicZoneVerticalGradientStops(minVal, maxVal, zoneThresholds, colorScheme)
    return SparklineDynamicStyling(
        gradientStops = stops,
        solidColor = terminalColor,
        terminalDotColor = terminalColor
    )
}

/**
 * Provides representative preview [SparklineZoneThresholds] scaled to [DEFAULT_PREVIEW_SPARKLINE_SAMPLES]
 * (`130..171`) when `"DYNAMIC_ZONE"` is selected in [EditSensorFieldDialog] (REQ-TRK-082.2, ATT-7761).
 */
fun resolvePreviewSparklineZoneThresholds(
    sensorType: SensorType?,
    graphColor: String?
): SparklineZoneThresholds? {
    if (graphColor != "DYNAMIC_ZONE" || sensorType == null) {
        return null
    }
    return when (sensorType) {
        SensorType.HR -> SparklineZoneThresholds(
            cutoffs = listOf(132f, 145f, 158f, 171f),
            zoneColors = listOf(TTColor.Zone1, TTColor.Zone2, TTColor.Zone3, TTColor.Zone4, TTColor.Zone5)
        )
        SensorType.POWER,
        SensorType.NORMALIZED_POWER,
        SensorType.POWER_5S,
        SensorType.POWER_1M,
        SensorType.POWER_5M,
        SensorType.POWER_20M,
        SensorType.POWER_PEAK_5S,
        SensorType.POWER_PEAK_1M,
        SensorType.POWER_PEAK_5M,
        SensorType.POWER_PEAK_20M -> SparklineZoneThresholds(
            cutoffs = listOf(135f, 142f, 148f, 154f, 160f, 166f),
            zoneColors = listOf(
                TTColor.Zone1,
                TTColor.Zone2,
                TTColor.Zone3,
                TTColor.Zone4,
                TTColor.Zone5,
                TTColor.Zone6,
                TTColor.Zone7
            )
        )
        SensorType.HRV_DFA_A1 -> SparklineZoneThresholds(
            cutoffs = listOf(143f, 158f),
            zoneColors = listOf(TTColor.Zone4, TTColor.Zone3, TTColor.Zone2),
            invertedPolarity = true
        )
        else -> null
    }
}

