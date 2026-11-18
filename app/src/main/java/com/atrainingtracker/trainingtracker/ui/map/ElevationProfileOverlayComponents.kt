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

package com.atrainingtracker.trainingtracker.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.formater.AltitudeFormatter
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import java.util.Locale

/**
 * Multi-source overlay bounds, canvas rendering, and scrubbing helpers extracted from
 * [ElevationProfile] (`REQ-PER-044.3`) to satisfy LoC modularity ceilings.
 */
internal fun calculateElevationBoundsWithOverlays(
    pathPoints: List<PathPoint>,
    minAltitudeOverride: Double?,
    maxAltitudeOverride: Double?,
    activeOverlaySeries: List<TelemetrySourceSeries>
): ElevationBounds {
    val base = calculateElevationBounds(
        pathPoints = pathPoints,
        minAltitudeOverride = minAltitudeOverride,
        maxAltitudeOverride = maxAltitudeOverride
    )
    if (activeOverlaySeries.isEmpty()) return base

    var min = base.min
    var max = base.max
    var hasOverlaySamples = false
    for (series in activeOverlaySeries) {
        for (pt in series.points) {
            if (!pt.value.isFinite()) continue
            hasOverlaySamples = true
            if (pt.value < min) min = pt.value
            if (pt.value > max) max = pt.value
        }
    }
    if (!hasOverlaySamples) return base
    return ElevationBounds(min, max, (max - min).coerceAtLeast(1.0))
}

internal fun DrawScope.drawElevationOverlayCurves(
    activeOverlaySeries: List<TelemetrySourceSeries>,
    allOverlaySeries: List<TelemetrySourceSeries>,
    isTimeDomain: Boolean,
    currentStartDist: Double,
    visibleSpan: Double,
    minAlt: Double,
    altRange: Double,
    width: Float,
    height: Float
) {
    for (series in activeOverlaySeries) {
        val colorIdx = allOverlaySeries.indexOfFirst { it.columnName == series.columnName }.coerceAtLeast(0)
        val color = overlayStrokeColor(colorIdx)
        val dashLength = if (colorIdx % 2 == 0) 6.dp.toPx() else 3.dp.toPx()
        val gapLength = 3.dp.toPx()
        val path = Path()
        var started = false
        for (pt in series.points) {
            if (!pt.value.isFinite()) continue
            val xVal = if (isTimeDomain) pt.timeSec.toDouble() else pt.distance
            if (xVal < currentStartDist - visibleSpan || xVal > currentStartDist + visibleSpan * 2.0) continue
            val x = ElevationProfileZoomMath.distanceToCanvasX(xVal, currentStartDist, visibleSpan, width)
            val y = height - (((pt.value - minAlt) / altRange).toFloat() * height)
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }
        if (started) {
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = 1.8.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength, gapLength), 0f),
                    cap = StrokeCap.Round
                )
            )
        }
    }
}

internal fun DrawScope.drawElevationOverlayScrubDots(
    activeOverlaySeries: List<TelemetrySourceSeries>,
    allOverlaySeries: List<TelemetrySourceSeries>,
    targetValue: Double,
    isTimeDomain: Boolean,
    markerX: Float,
    minAlt: Double,
    altRange: Double,
    height: Float
) {
    for (series in activeOverlaySeries) {
        val sampleAlt = TelemetryMetricUtils.findNearestSourceValue(series.points, targetValue, isTimeDomain)
            ?.takeIf { it.isFinite() } ?: continue
        val colorIdx = allOverlaySeries.indexOfFirst { it.columnName == series.columnName }.coerceAtLeast(0)
        val y = height - (((sampleAlt - minAlt) / altRange).toFloat() * height)
        drawCircle(
            color = overlayStrokeColor(colorIdx),
            radius = 3.dp.toPx(),
            center = Offset(markerX, y)
        )
    }
}

@Composable
internal fun formatElevationOverlayScrubSummary(
    activeOverlaySeries: List<TelemetrySourceSeries>,
    targetValue: Double?,
    isTimeDomain: Boolean,
    unit: MyUnits,
    altitudeFormatter: AltitudeFormatter,
    locale: Locale = Locale.getDefault()
): String? {
    if (targetValue == null || activeOverlaySeries.isEmpty()) return null
    val parts = mutableListOf<String>()
    for (series in activeOverlaySeries) {
        val altMeters = TelemetryMetricUtils.findNearestSourceValue(series.points, targetValue, isTimeDomain)
            ?.takeIf { it.isFinite() } ?: continue
        val sourceLabel = resolveOverlaySourceLabel(series.sourceName)
        val formattedAlt = formatOverlayScalarWithUnit(
            rawValue = altMeters,
            metricType = TelemetryMetricType.ALTITUDE,
            unit = unit,
            locale = locale,
            altitudeFormatter = altitudeFormatter
        )
        parts.add("$sourceLabel: $formattedAlt")
    }
    return if (parts.isEmpty()) null else parts.joinToString(" • ")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GradeLegend(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.widthIn(max = 300.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 4.dp
    ) {
        FlowRow(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GradeLegendItem(TTColor.Zone1, stringResource(R.string.grade_legend_under_2))
            GradeLegendItem(TTColor.Zone2, stringResource(R.string.grade_legend_2_to_5))
            GradeLegendItem(TTColor.Zone3, stringResource(R.string.grade_legend_5_to_10))
            GradeLegendItem(TTColor.Zone4, stringResource(R.string.grade_legend_10_to_15))
            GradeLegendItem(TTColor.Zone5, stringResource(R.string.grade_legend_15_to_20))
            GradeLegendItem(TTColor.Zone6, stringResource(R.string.grade_legend_20_to_25))
            GradeLegendItem(TTColor.Zone7, stringResource(R.string.grade_legend_over_25))
        }
    }
}

@Composable
private fun GradeLegendItem(
    color: Color,
    label: String,
    hasOutline: Boolean = color == TTColor.Zone6 || color == TTColor.Zone7
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            modifier = Modifier.size(12.dp),
            color = color,
            shape = RoundedCornerShape(2.dp),
            border = if (hasOutline) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
        ) {}
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            softWrap = false
        )
    }
}
