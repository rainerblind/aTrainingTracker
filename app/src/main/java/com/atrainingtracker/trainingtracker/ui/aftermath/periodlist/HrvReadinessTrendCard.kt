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

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.hrv.HrvPosture
import com.atrainingtracker.trainingtracker.database.hrv.ReadinessStatus
import com.atrainingtracker.trainingtracker.ui.map.ChartGestureDisambiguator
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import java.util.Locale
import kotlin.math.max

/**
 * Longitudinal Heart Rate Variability (HRV) readiness trend card in Periods screen
 * (`REQ-PER-033`, `TST-PER-041`).
 *
 * Visualizes daily resting `ln(rMSSD)` or `rMSSD ms` scatter readings, the 7-day rolling average,
 * and the 60-day Smallest Worthwhile Change (SWC) normal corridor for the selected posture,
 * with optional synchronized daily training-load context and gesture-safe scrubbing.
 */
@Composable
fun HrvReadinessTrendCard(
    series: HrvTrendSeries?,
    selectedPosture: HrvPosture,
    onSelectPosture: (HrvPosture) -> Unit,
    metricMode: HrvTrendMetricMode,
    onSelectMetricMode: (HrvTrendMetricMode) -> Unit,
    showTrainingLoad: Boolean,
    onToggleTrainingLoad: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (series == null || (series.points.isEmpty() && series.availablePostures.isEmpty())) {
        return
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Title & Subtitle
            Text(
                text = stringResource(R.string.hrv_readiness_trend_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.hrv_readiness_trend_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Posture Selection Chips
            val available = if (series.availablePostures.isNotEmpty()) {
                series.availablePostures
            } else {
                listOf(HrvPosture.SUPINE, HrvPosture.SEATED, HrvPosture.STANDING)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                available.forEach { posture ->
                    val isSelected = posture == selectedPosture
                    val postureLabel = when (posture) {
                        HrvPosture.SUPINE -> stringResource(R.string.morning_hrv_posture_supine)
                        HrvPosture.SEATED -> stringResource(R.string.morning_hrv_posture_seated)
                        HrvPosture.STANDING -> stringResource(R.string.morning_hrv_posture_standing)
                        else -> posture.name
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectPosture(posture) },
                        label = { Text(postureLabel, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            // Controls Row: Metric mode & Training Load toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Metric Selector (ln(rMSSD) vs rMSSD)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = metricMode == HrvTrendMetricMode.LN_RMSSD,
                        onClick = { onSelectMetricMode(HrvTrendMetricMode.LN_RMSSD) },
                        label = {
                            Text(
                                stringResource(R.string.hrv_readiness_metric_ln_rmssd),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                    FilterChip(
                        selected = metricMode == HrvTrendMetricMode.RMSSD,
                        onClick = { onSelectMetricMode(HrvTrendMetricMode.RMSSD) },
                        label = {
                            Text(
                                stringResource(R.string.hrv_readiness_metric_rmssd),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }

                // Training Load Toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.hrv_readiness_toggle_training_load),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Switch(
                        checked = showTrainingLoad,
                        onCheckedChange = onToggleTrainingLoad,
                        modifier = Modifier.height(24.dp)
                    )
                }
            }

            // Chart Canvas
            if (series.points.isEmpty()) {
                val postureLabel = when (selectedPosture) {
                    HrvPosture.SUPINE -> stringResource(R.string.morning_hrv_posture_supine)
                    HrvPosture.SEATED -> stringResource(R.string.morning_hrv_posture_seated)
                    HrvPosture.STANDING -> stringResource(R.string.morning_hrv_posture_standing)
                    else -> selectedPosture.name
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.hrv_readiness_no_entries_posture, postureLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                HrvReadinessTrendCanvas(
                    series = series,
                    showTrainingLoad = showTrainingLoad,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(top = 8.dp)
                )
            }

            // Legend Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // SWC Corridor Pill
                Surface(
                    color = TTColor.ReadinessOptimal.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TTColor.ReadinessOptimal.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = stringResource(R.string.hrv_readiness_swc_baseline_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Rolling 7d Line Pill
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.hrv_readiness_rolling_avg_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (showTrainingLoad && series.maxTrainingLoad > 0.0) {
                    Surface(
                        color = TTColor.PmcAtlMagenta.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.hrv_readiness_toggle_training_load),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pure Canvas renderer for the longitudinal HRV readiness trend (`REQ-PER-033`, `TST-PER-041`).
 *
 * Implements touch scrubbing with slope disambiguation (`ChartGestureDisambiguator`),
 * Rule 48 outlier-resilient Y-clamping, explicit X/Y scale labels, and Rule 69 bottom-anchored
 * rounded load bars.
 */
@Composable
fun HrvReadinessTrendCanvas(
    series: HrvTrendSeries,
    showTrainingLoad: Boolean,
    modifier: Modifier = Modifier
) {
    val points = series.points
    val touchSlop = LocalViewConfiguration.current.touchSlop
    var scrubbedPoint by remember { mutableStateOf<HrvTrendPoint?>(null) }
    var scrubX by remember { mutableFloatStateOf(0f) }

    val currentPointsState by rememberUpdatedState(points)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val rollingCurveColor = MaterialTheme.colorScheme.primary
    val swcCorridorColor = TTColor.ReadinessOptimal.copy(alpha = 0.15f)
    val swcBoundaryColor = TTColor.ReadinessOptimal.copy(alpha = 0.60f)
    val trainingLoadBarColor = TTColor.PmcAtlMagenta.copy(alpha = 0.30f)
    val cursorColor = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = modifier.pointerInput(series.posture, series.metricMode) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var isDragging = false
                var isVerticalScrolling = false

                val leftPad = 48.dp.toPx()
                val rightPad = 16.dp.toPx()
                val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)

                val initialPoint = HrvReadinessTrendMath.findNearestPoint(down.position.x, leftPad, plotW, currentPointsState)
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
                            scrubbedPoint = HrvReadinessTrendMath.findNearestPoint(
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
        val leftPad = 48.dp.toPx()
        val rightPad = 16.dp.toPx()
        val topPad = 16.dp.toPx()
        val bottomPad = 26.dp.toPx()

        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        val yMin = series.yMin
        val yMax = series.yMax
        val ySpan = (yMax - yMin).coerceAtLeast(0.01)

        fun valueToY(v: Double): Float {
            val norm = ((v - yMin) / ySpan).coerceIn(0.0, 1.0).toFloat()
            return topPad + plotH * (1f - norm)
        }

        fun indexToX(i: Int): Float {
            if (points.size <= 1) return leftPad + plotW * 0.5f
            val frac = i.toFloat() / (points.size - 1).toFloat()
            return leftPad + plotW * frac
        }

        // 1. Horizontal Grid Lines & Y-Axis Scale Labels (Rule 48)
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = labelColor.toArgb()
            textSize = 10.sp.toPx()
        }

        val gridSteps = 3
        for (i in 0..gridSteps) {
            val frac = i.toFloat() / gridSteps.toFloat()
            val y = topPad + plotH * (1f - frac)
            val v = yMin + ySpan * frac

            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(leftPad + plotW, y),
                strokeWidth = 1.dp.toPx()
            )

            val label = if (series.metricMode == HrvTrendMetricMode.LN_RMSSD) {
                String.format(Locale.US, "%.1f", v)
            } else {
                String.format(Locale.US, "%.0f", v)
            }
            drawContext.canvas.nativeCanvas.drawText(label, 4.dp.toPx(), y + 4.dp.toPx(), textPaint)
        }

        // 2. Optional Training Load Bars in Background (Rule 69 rounded top, sharp bottom)
        if (showTrainingLoad && series.maxTrainingLoad > 0.0) {
            val maxLoad = series.maxTrainingLoad
            val barW = (plotW / points.size.toFloat() * 0.6f).coerceIn(3.dp.toPx(), 14.dp.toPx())
            points.forEachIndexed { i, p ->
                val load = p.trainingLoad
                if (load != null && load > 0.0) {
                    val barH = (load / maxLoad * (plotH * 0.45f)).toFloat().coerceAtLeast(2.dp.toPx())
                    val barX = indexToX(i) - barW * 0.5f
                    val barY = topPad + plotH - barH

                    drawRoundRect(
                        color = trainingLoadBarColor,
                        topLeft = Offset(barX, barY),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }
            }
        }

        // 3. Shaded 60-Day SWC Corridor
        val corridorPoints = points.mapIndexedNotNull { i, p ->
            if (p.swcLower != null && p.swcUpper != null) {
                Triple(indexToX(i), valueToY(p.swcLower), valueToY(p.swcUpper))
            } else {
                null
            }
        }

        if (corridorPoints.size >= 2) {
            val corridorPath = Path().apply {
                moveTo(corridorPoints.first().first, corridorPoints.first().third)
                for (pt in corridorPoints) {
                    lineTo(pt.first, pt.third) // upper boundary
                }
                for (k in corridorPoints.indices.reversed()) {
                    val pt = corridorPoints[k]
                    lineTo(pt.first, pt.second) // lower boundary
                }
                close()
            }
            drawPath(corridorPath, color = swcCorridorColor)

            // Dashed Upper and Lower SWC lines
            val upperPath = Path().apply {
                moveTo(corridorPoints.first().first, corridorPoints.first().third)
                for (pt in corridorPoints) lineTo(pt.first, pt.third)
            }
            val lowerPath = Path().apply {
                moveTo(corridorPoints.first().first, corridorPoints.first().second)
                for (pt in corridorPoints) lineTo(pt.first, pt.second)
            }
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            drawPath(
                upperPath,
                color = swcBoundaryColor,
                style = Stroke(width = 1.2.dp.toPx(), pathEffect = dashEffect)
            )
            drawPath(
                lowerPath,
                color = swcBoundaryColor,
                style = Stroke(width = 1.2.dp.toPx(), pathEffect = dashEffect)
            )
        }

        // 4. Continuous 7-Day Rolling Average Line
        val rollingPoints = points.mapIndexedNotNull { i, p ->
            p.mu7d?.let { Pair(indexToX(i), valueToY(it)) }
        }

        if (rollingPoints.size >= 2) {
            val rollingPath = Path().apply {
                moveTo(rollingPoints.first().first, rollingPoints.first().second)
                for (pt in rollingPoints) {
                    lineTo(pt.first, pt.second)
                }
            }
            drawPath(
                rollingPath,
                color = rollingCurveColor,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // 5. Daily Scatter Points (Color-coded by ReadinessStatus)
        points.forEachIndexed { i, p ->
            val ptX = indexToX(i)
            val ptY = valueToY(p.rawValue)
            val ptColor = when (p.status) {
                ReadinessStatus.OPTIMAL -> TTColor.ReadinessOptimal
                ReadinessStatus.FATIGUED_LOW -> TTColor.ReadinessFatigued
                ReadinessStatus.ABOVE_NORMAL -> TTColor.ReadinessElevated
                ReadinessStatus.CALIBRATING -> TTColor.ReadinessCalibrating
            }

            // Outer halo
            drawCircle(
                color = ptColor.copy(alpha = 0.35f),
                radius = 5.dp.toPx(),
                center = Offset(ptX, ptY)
            )
            // Solid center
            drawCircle(
                color = ptColor,
                radius = 3.dp.toPx(),
                center = Offset(ptX, ptY)
            )
        }

        // 6. X-Axis Date Markers (Rule 48)
        val dateStep = max(1, points.size / 4)
        for (i in points.indices step dateStep) {
            val x = indexToX(i)
            val dIso = points[i].dateIso
            val shortDate = if (dIso.length >= 10) dIso.substring(5) else dIso
            drawContext.canvas.nativeCanvas.drawText(
                shortDate,
                x - 14.dp.toPx(),
                topPad + plotH + 18.dp.toPx(),
                textPaint
            )
        }

        // 7. Touch Scrubbing Tooltip & Vertical Cursor
        val activeScrub = scrubbedPoint
        if (activeScrub != null) {
            val ptIdx = points.indexOf(activeScrub)
            val cursorX = if (ptIdx >= 0) indexToX(ptIdx) else scrubX.coerceIn(leftPad, leftPad + plotW)

            drawLine(
                color = cursorColor.copy(alpha = 0.70f),
                start = Offset(cursorX, topPad),
                end = Offset(cursorX, topPad + plotH),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
            )

            val valStr = if (series.metricMode == HrvTrendMetricMode.LN_RMSSD) {
                String.format(Locale.US, "%.2f", activeScrub.rawValue)
            } else {
                String.format(Locale.US, "%.1f ms", activeScrub.rawValue)
            }

            val swcStr = if (activeScrub.swcLower != null && activeScrub.swcUpper != null) {
                String.format(Locale.US, " [SWC: %.2f – %.2f]", activeScrub.swcLower, activeScrub.swcUpper)
            } else {
                ""
            }

            val tooltipText = "${activeScrub.dateIso}: $valStr$swcStr"

            val tooltipPaint = Paint().apply {
                isAntiAlias = true
                color = Color.White.toArgb()
                textSize = 10.sp.toPx()
                isFakeBoldText = true
            }

            val bgPaint = Paint().apply {
                isAntiAlias = true
                color = Color.Black.copy(alpha = 0.85f).toArgb()
            }

            val textW = tooltipPaint.measureText(tooltipText)
            val tipX = (cursorX - textW * 0.5f).coerceIn(leftPad, leftPad + plotW - textW)
            val tipY = topPad + 4.dp.toPx()

            drawContext.canvas.nativeCanvas.drawRoundRect(
                tipX - 6.dp.toPx(),
                tipY - 12.dp.toPx(),
                tipX + textW + 6.dp.toPx(),
                tipY + 4.dp.toPx(),
                4.dp.toPx(),
                4.dp.toPx(),
                bgPaint
            )

            drawContext.canvas.nativeCanvas.drawText(
                tooltipText,
                tipX,
                tipY,
                tooltipPaint
            )
        }
    }
}

@Preview(name = "HRV Readiness Trend - Light Mode", showBackground = true)
@Composable
private fun HrvReadinessTrendCardLightPreview() {
    ATrainingTrackerTheme {
        val samplePoints = listOf(
            HrvTrendPoint("2026-11-01", 1730419200000L, 4.10, 4.05, 3.85, 4.25, ReadinessStatus.OPTIMAL, 65.0),
            HrvTrendPoint("2026-11-02", 1730505600000L, 4.22, 4.10, 3.85, 4.25, ReadinessStatus.OPTIMAL, 40.0),
            HrvTrendPoint("2026-11-03", 1730592000000L, 3.78, 4.02, 3.85, 4.25, ReadinessStatus.FATIGUED_LOW, 110.0),
            HrvTrendPoint("2026-11-04", 1730678400000L, 4.15, 4.06, 3.85, 4.25, ReadinessStatus.OPTIMAL, 25.0),
            HrvTrendPoint("2026-11-05", 1730764800000L, 4.35, 4.12, 3.85, 4.25, ReadinessStatus.ABOVE_NORMAL, 50.0)
        )
        val series = HrvTrendSeries(
            posture = HrvPosture.SUPINE,
            metricMode = HrvTrendMetricMode.LN_RMSSD,
            points = samplePoints,
            availablePostures = listOf(HrvPosture.SUPINE, HrvPosture.SEATED),
            yMin = 3.6,
            yMax = 4.5,
            maxTrainingLoad = 110.0
        )
        HrvReadinessTrendCard(
            series = series,
            selectedPosture = HrvPosture.SUPINE,
            onSelectPosture = {},
            metricMode = HrvTrendMetricMode.LN_RMSSD,
            onSelectMetricMode = {},
            showTrainingLoad = true,
            onToggleTrainingLoad = {}
        )
    }
}

@Preview(name = "HRV Readiness Trend - Dark Mode", showBackground = true)
@Composable
private fun HrvReadinessTrendCardDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true) {
        val samplePoints = listOf(
            HrvTrendPoint("2026-11-01", 1730419200000L, 4.10, 4.05, 3.85, 4.25, ReadinessStatus.OPTIMAL, 65.0),
            HrvTrendPoint("2026-11-02", 1730505600000L, 4.22, 4.10, 3.85, 4.25, ReadinessStatus.OPTIMAL, 40.0)
        )
        val series = HrvTrendSeries(
            posture = HrvPosture.SUPINE,
            metricMode = HrvTrendMetricMode.LN_RMSSD,
            points = samplePoints,
            availablePostures = listOf(HrvPosture.SUPINE),
            yMin = 3.6,
            yMax = 4.5,
            maxTrainingLoad = 65.0
        )
        HrvReadinessTrendCard(
            series = series,
            selectedPosture = HrvPosture.SUPINE,
            onSelectPosture = {},
            metricMode = HrvTrendMetricMode.LN_RMSSD,
            onSelectMetricMode = {},
            showTrainingLoad = true,
            onToggleTrainingLoad = {}
        )
    }
}
