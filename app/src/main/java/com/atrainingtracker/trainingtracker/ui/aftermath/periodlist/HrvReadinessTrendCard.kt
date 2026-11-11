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
import androidx.annotation.VisibleForTesting
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
                        HrvPosture.OVERNIGHT_WEARABLE -> stringResource(R.string.morning_hrv_posture_overnight_wearable)
                        else -> posture.name
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectPosture(posture) },
                        label = { Text(postureLabel, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            // Provenance Badge for Overnight Wearable (REQ-PER-033.1)
            if (selectedPosture == HrvPosture.OVERNIGHT_WEARABLE) {
                val latestNote = series.points.lastOrNull { it.notes != null }?.notes
                val badgeText = parseProvenanceBadge(latestNote)
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.height(28.dp)
                ) {
                    HrvTrendMetricMode.values().forEachIndexed { index, mode ->
                        val label = when (mode) {
                            HrvTrendMetricMode.LN_RMSSD -> stringResource(R.string.hrv_readiness_metric_ln_rmssd)
                            HrvTrendMetricMode.RMSSD -> stringResource(R.string.hrv_readiness_metric_rmssd)
                        }
                        SegmentedButton(
                            selected = metricMode == mode,
                            onClick = { onSelectMetricMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                            icon = {},
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (metricMode == mode) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
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
                    HrvPosture.OVERNIGHT_WEARABLE -> stringResource(R.string.morning_hrv_posture_overnight_wearable)
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
    val swcCorridorColor = TTColor.ReadinessOptimal.copy(alpha = 0.15f)
    val swcBoundaryColor = TTColor.ReadinessOptimal.copy(alpha = 0.60f)
    val trainingLoadBarColor = TTColor.PmcAtlMagenta.copy(alpha = 0.30f)
    val cursorColor = MaterialTheme.colorScheme.onSurface
    val tooltipBgColor = MaterialTheme.colorScheme.inverseSurface
    val tooltipTextColor = MaterialTheme.colorScheme.inverseOnSurface

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

        val minEpoch = points.first().epochDay
        val maxEpoch = points.last().epochDay
        val epochSpan = (maxEpoch - minEpoch).coerceAtLeast(1L).toFloat()

        fun valueToY(v: Double): Float {
            val norm = ((v - yMin) / ySpan).coerceIn(0.0, 1.0).toFloat()
            return topPad + plotH * (1f - norm)
        }

        fun epochToX(epochDay: Long): Float {
            if (points.size <= 1 || maxEpoch == minEpoch) return leftPad + plotW * 0.5f
            val frac = ((epochDay - minEpoch).toFloat() / epochSpan).coerceIn(0f, 1f)
            return leftPad + plotW * frac
        }

        // 1. Horizontal Grid Lines & Y-Axis Scale Labels (Rule 48)
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = labelColor.toArgb()
            textSize = 10.sp.toPx()
        }

        val gridSteps = 3
        val locale = Locale.getDefault()
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
                String.format(locale, "%.1f", v)
            } else {
                String.format(locale, "%.0f", v)
            }
            drawContext.canvas.nativeCanvas.drawText(label, 4.dp.toPx(), y + 4.dp.toPx(), textPaint)
        }

        // 2. Optional Training Load Bars in Background (Rule 69 rounded top, sharp bottom)
        if (showTrainingLoad && series.maxTrainingLoad > 0.0) {
            val maxLoad = series.maxTrainingLoad
            val totalDays = (maxEpoch - minEpoch + 1).coerceAtLeast(1L)
            val barW = (plotW / totalDays.toFloat() * 0.6f).coerceIn(3.dp.toPx(), 14.dp.toPx())
            points.forEach { p ->
                val load = p.trainingLoad
                if (load != null && load > 0.0) {
                    val barH = (load / maxLoad * (plotH * 0.45f)).toFloat().coerceAtLeast(2.dp.toPx())
                    val barX = epochToX(p.epochDay) - barW * 0.5f
                    val barY = topPad + plotH - barH

                    val barPath = Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                rect = androidx.compose.ui.geometry.Rect(barX, barY, barX + barW, topPad + plotH),
                                topLeft = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                                topRight = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                                bottomLeft = CornerRadius.Zero,
                                bottomRight = CornerRadius.Zero
                            )
                        )
                    }
                    drawPath(barPath, color = trainingLoadBarColor)
                }
            }
        }

        // 3. Shaded 60-Day SWC Corridor
        val corridorPoints = points.mapNotNull { p ->
            if (p.swcLower != null && p.swcUpper != null) {
                Triple(epochToX(p.epochDay), valueToY(p.swcLower), valueToY(p.swcUpper))
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

        // 4. Status-Colored 7-Day Rolling Average Line
        val rollingPoints = points.mapNotNull { p ->
            p.mu7d?.let { Pair(p, Offset(epochToX(p.epochDay), valueToY(it))) }
        }

        if (rollingPoints.size >= 2) {
            for (k in 0 until rollingPoints.size - 1) {
                val (p1, offset1) = rollingPoints[k]
                val (p2, offset2) = rollingPoints[k + 1]
                val segmentColor = when (p2.status) {
                    ReadinessStatus.OPTIMAL -> TTColor.ReadinessOptimal
                    ReadinessStatus.FATIGUED_LOW -> TTColor.ReadinessFatigued
                    ReadinessStatus.ABOVE_NORMAL -> TTColor.ReadinessElevated
                    ReadinessStatus.CALIBRATING -> TTColor.ReadinessCalibrating
                }
                drawLine(
                    color = segmentColor,
                    start = offset1,
                    end = offset2,
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // 5. Daily Scatter Points (Color-coded by ReadinessStatus)
        points.forEach { p ->
            val raw = p.rawValue ?: return@forEach
            val ptX = epochToX(p.epochDay)
            val ptY = valueToY(raw)
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
        val dateFormatter = DateTimeFormatter.ofPattern("d MMM", locale)
        for (i in points.indices step dateStep) {
            val p = points[i]
            val x = epochToX(p.epochDay)
            val shortDate = try {
                LocalDate.parse(p.dateIso).format(dateFormatter)
            } catch (_: Exception) {
                if (p.dateIso.length >= 10) p.dateIso.substring(5) else p.dateIso
            }
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
            val cursorX = epochToX(activeScrub.epochDay)

            drawLine(
                color = cursorColor.copy(alpha = 0.70f),
                start = Offset(cursorX, topPad),
                end = Offset(cursorX, topPad + plotH),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
            )

            val formattedDate = try {
                LocalDate.parse(activeScrub.dateIso).format(DateTimeFormatter.ofPattern("d MMM", locale))
            } catch (_: Exception) {
                activeScrub.dateIso
            }

            val tooltipText = if (activeScrub.rawValue != null) {
                val valStr = if (series.metricMode == HrvTrendMetricMode.LN_RMSSD) {
                    String.format(locale, "%.2f", activeScrub.rawValue)
                } else {
                    String.format(locale, "%.1f ms", activeScrub.rawValue)
                }
                val swcStr = if (activeScrub.swcLower != null && activeScrub.swcUpper != null) {
                    String.format(locale, " [SWC: %.2f – %.2f]", activeScrub.swcLower, activeScrub.swcUpper)
                } else {
                    ""
                }
                "$formattedDate: $valStr$swcStr"
            } else {
                val loadStr = if (activeScrub.trainingLoad != null && activeScrub.trainingLoad > 0.0) {
                    String.format(locale, "Load: %.0f", activeScrub.trainingLoad)
                } else {
                    ""
                }
                if (loadStr.isNotEmpty()) "$formattedDate: $loadStr" else formattedDate
            }

            val tooltipPaint = Paint().apply {
                isAntiAlias = true
                color = tooltipTextColor.toArgb()
                textSize = 10.sp.toPx()
                isFakeBoldText = true
            }

            val bgPaint = Paint().apply {
                isAntiAlias = true
                color = tooltipBgColor.toArgb()
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

/**
 * Parses source provenance badge text from notes string (`REQ-PER-033.1`).
 */
@VisibleForTesting
internal fun parseProvenanceBadge(notes: String?): String {
    if (notes == null || !notes.startsWith("HealthConnect", ignoreCase = true)) return "Health Connect"
    val parts = notes.split(":")
    if (parts.size >= 2) {
        val pkg = parts[1].lowercase()
        val appName = when {
            pkg.contains("oura") -> "Oura"
            pkg.contains("fitbit") -> "Fitbit"
            pkg.contains("whoop") -> "Whoop"
            pkg.contains("garmin") -> "Garmin"
            pkg.contains("shealth") || pkg.contains("samsung") -> "Samsung Health"
            pkg.contains("fitness") || pkg.contains("google") -> "Google Fit"
            pkg.contains("withings") -> "Withings"
            pkg.contains("polar") -> "Polar"
            pkg.contains("coros") -> "Coros"
            else -> {
                val lastPart = pkg.substringAfterLast('.')
                if (lastPart.isNotBlank()) lastPart.replaceFirstChar { it.uppercase() } else ""
            }
        }
        return if (appName.isNotBlank()) "Health Connect ($appName)" else "Health Connect"
    }
    return "Health Connect"
}
