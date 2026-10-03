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

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.PaceFormatter
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomain
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import java.util.Locale
import kotlin.math.abs

/**
 * Metric types supported by [TelemetryMetricGraph].
 * (REQ-UI-206 / ATT-1740)
 */
enum class TelemetryMetricType {
    HEART_RATE,
    SPEED,
    PACE,
    POWER
}

/**
 * Helper object providing metric availability verification and data processing.
 * (REQ-UI-206 / ATT-1740)
 */
object TelemetryMetricUtils {
    /**
     * Checks whether valid heart rate samples exist in [path].
     */
    fun hasHeartRateData(path: List<PathPoint>?): Boolean {
        if (path.isNullOrEmpty()) return false
        return path.any { it.hr != null && it.hr > 0 }
    }

    /**
     * Checks whether valid speed samples exist in [path].
     */
    fun hasSpeedData(path: List<PathPoint>?): Boolean {
        if (path.isNullOrEmpty()) return false
        return path.any { it.speedMps != null && it.speedMps > 0.0 }
    }

    /**
     * Checks whether valid cycling power samples exist in [path].
     */
    fun hasPowerData(path: List<PathPoint>?): Boolean {
        if (path.isNullOrEmpty()) return false
        return path.any { it.power != null && it.power > 0 }
    }

    /**
     * Extracts scalar value for a given [PathPoint] according to [metricType] and [unit].
     * Returns null if sample is invalid or unrecorded.
     */
    fun extractMetricValue(
        point: PathPoint,
        metricType: TelemetryMetricType,
        unit: MyUnits,
        paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM
    ): Double? {
        return when (metricType) {
            TelemetryMetricType.HEART_RATE -> {
                point.hr?.takeIf { it > 0 }?.toDouble()
            }
            TelemetryMetricType.SPEED -> {
                point.speedMps?.takeIf { it > 0.0 }?.let { mps ->
                    if (unit == MyUnits.METRIC) mps * 3.6 else mps * 2.236936
                }
            }
            TelemetryMetricType.PACE -> {
                point.speedMps?.takeIf { it >= 0.55 }?.let { mps ->
                    val secPerKm = 1000.0 / mps
                    val secPerUnit = if (unit == MyUnits.METRIC) {
                        secPerKm
                    } else {
                        secPerKm * (BANALService.METER_PER_MILE / 1000.0)
                    }
                    val effectiveCeiling = if (unit == MyUnits.METRIC) {
                        paceCeilingMinKm.toDouble()
                    } else {
                        paceCeilingMinKm.toDouble() * (BANALService.METER_PER_MILE / 1000.0)
                    }
                    (secPerUnit / 60.0).coerceIn(effectiveCeiling, 20.0)
                }
            }
            TelemetryMetricType.POWER -> {
                point.power?.takeIf { it >= 0 }?.toDouble()
            }
        }
    }

    /**
     * Formats a pace value in decimal minutes (e.g. 4.5) to athletic "mm:ss" format (e.g. "4:30").
     * (REQ-UI-219 / ATT-1818)
     */
    fun formatPaceMinutes(paceMinutes: Double): String {
        val totalSec = kotlin.math.round(paceMinutes * 60.0).toInt().coerceAtLeast(0)
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.US, "%d:%02d", min, sec)
    }

    /**
     * Formats instantaneous or extreme metric value for display.
     */
    fun formatValue(
        value: Double?,
        metricType: TelemetryMetricType,
        unit: MyUnits,
        speedFormatter: SpeedFormatter,
        paceFormatter: PaceFormatter,
        hrZoneThresholds: HeartRateZoneThresholds? = null,
        powerZoneThresholds: PowerZoneThresholds? = null
    ): String {
        if (value == null) return "--"
        return when (metricType) {
            TelemetryMetricType.HEART_RATE -> {
                val base = "${value.toInt()} bpm"
                if (hrZoneThresholds != null) {
                    val zone = TelemetryZoneMath.determineHeartRateZone(value, hrZoneThresholds)
                    "$base • Z$zone"
                } else {
                    base
                }
            }
            TelemetryMetricType.SPEED -> {
                val mps = if (unit == MyUnits.METRIC) value / 3.6 else value / 2.236936
                speedFormatter.format_with_units(mps)
            }
            TelemetryMetricType.PACE -> {
                val secPerUnit = value * 60.0
                val spm = if (unit == MyUnits.METRIC) {
                    secPerUnit / 1000.0
                } else {
                    secPerUnit / BANALService.METER_PER_MILE
                }
                paceFormatter.format_with_units(spm)
            }
            TelemetryMetricType.POWER -> {
                val base = "${value.toInt()} W"
                if (powerZoneThresholds != null) {
                    val zone = TelemetryZoneMath.determinePowerZone(value, powerZoneThresholds)
                    "$base • Z$zone"
                } else {
                    base
                }
            }
        }
    }

    /**
     * Formats an intermediate milestone distance label without repeating units.
     * (REQ-UI-220 / ATT-1819)
     */
    fun formatMilestoneLabel(dist: Double, visibleSpan: Double, unit: MyUnits): String {
        return if (unit == MyUnits.METRIC) {
            if (visibleSpan < 1500) {
                "${dist.toInt()}m"
            } else if (dist % 1000.0 != 0.0) {
                String.format(Locale.US, "%.1f", dist / 1000.0)
            } else {
                "${(dist / 1000.0).toInt()}"
            }
        } else {
            val miles = dist / BANALService.METER_PER_MILE
            if (miles % 1.0 != 0.0) {
                String.format(Locale.US, "%.1f", miles)
            } else {
                "${miles.toInt()}"
            }
        }
    }

    /**
     * Determines whether an intermediate milestone label satisfies boundary clearance
     * and minimum spacing clearance from the previously rendered label.
     * (REQ-UI-220 / ATT-1819)
     */
    fun shouldRenderMilestoneLabel(
        labelLeft: Float,
        labelRight: Float,
        lastDrawnRightX: Float,
        startBoundaryThreshold: Float,
        endBoundaryThreshold: Float,
        minSpacing: Float = 36f
    ): Boolean {
        val startClearance = labelLeft >= startBoundaryThreshold
        val endClearance = labelRight <= endBoundaryThreshold
        val spacingClearance = labelLeft >= lastDrawnRightX + minSpacing
        return startClearance && endClearance && spacingClearance
    }
}

/**
 * High-performance, aesthetic continuous telemetry graph composable for Heart Rate,
 * Speed/Pace, and Cycling Power curves in Aftermath detailed inspection.
 * (REQ-UI-206 / ATT-1740)
 *
 * Invariant: Horizontal paddings strictly match [ElevationProfile] (start = 50.dp, end = 25.dp, bottom = 24.dp),
 * guaranteeing pixel-perfect vertical alignment of the horizontal axes and synchronized scrubbing markers across all charts.
 */
@Composable
fun TelemetryMetricGraph(
    pathPoints: List<PathPoint>,
    metricType: TelemetryMetricType,
    currentDistance: Double?,
    onDistanceSelected: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    xAxisDomain: ProfileXAxisDomain = ProfileXAxisDomain.DISTANCE,
    bSportType: BSportType = BSportType.UNKNOWN,
    zoomScale: Float = 1.0f,
    startDist: Double = 0.0,
    isPanMode: Boolean = false,
    onZoomChanged: ((Float, Double) -> Unit)? = null,
    hrZoneThresholds: HeartRateZoneThresholds? = null,
    powerZoneThresholds: PowerZoneThresholds? = null,
    paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM,
    enableGestures: Boolean = true
) {
    if (pathPoints.isEmpty()) return

    val context = LocalContext.current
    val effectiveHrThresholds = remember(hrZoneThresholds, bSportType, context) {
        hrZoneThresholds ?: runCatching {
            val zoneType = if (bSportType == BSportType.BIKE) SettingsDataStore.ZoneType.HR_BIKE else SettingsDataStore.ZoneType.HR_RUN
            val z1 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 1)
            val z2 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 2)
            val z3 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 3)
            val z4 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 4)
            if (z1 > 0 && z2 > z1 && z3 > z2 && z4 > z3) {
                HeartRateZoneThresholds(z1, z2, z3, z4)
            } else null
        }.getOrNull()
    }

    val effectivePowerThresholds = remember(powerZoneThresholds, context) {
        powerZoneThresholds ?: runCatching {
            val zoneType = SettingsDataStore.ZoneType.PWR_BIKE
            val z1 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 1)
            val z2 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 2)
            val z3 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 3)
            val z4 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 4)
            if (z1 > 0 && z2 > z1 && z3 > z2 && z4 > z3) {
                PowerZoneThresholds(z1, z2, z3, z4)
            } else null
        }.getOrNull()
    }

    val unit = remember {
        try {
            TrainingApplication.getUnit()
        } catch (_: Exception) {
            MyUnits.METRIC
        }
    }

    val speedFormatter = remember(unit) { SpeedFormatter() }
    val paceFormatter = remember(unit) { PaceFormatter() }
    val distanceFormatter = remember(unit) { DistanceFormatter() }

    val colorScheme = MaterialTheme.colorScheme

    val accentColor = remember(metricType, colorScheme) {
        when (metricType) {
            TelemetryMetricType.HEART_RATE -> TTColor.Zone4
            TelemetryMetricType.SPEED, TelemetryMetricType.PACE -> colorScheme.primary
            TelemetryMetricType.POWER -> TTColor.Zone5
        }
    }

    val isTrackless = remember(pathPoints) {
        (pathPoints.lastOrNull()?.distance ?: 0.0) == 0.0 && (pathPoints.lastOrNull()?.timeSec ?: 0L) > 0L
    }
    val isTimeDomain = xAxisDomain == ProfileXAxisDomain.TIME || isTrackless
    val totalSpan = remember(pathPoints, isTimeDomain) {
        if (isTimeDomain) {
            (pathPoints.lastOrNull()?.timeSec ?: 0L).toDouble().coerceAtLeast(1.0)
        } else {
            (pathPoints.lastOrNull()?.distance ?: 0.0).coerceAtLeast(1.0)
        }
    }
    val visibleSpan = remember(totalSpan, zoomScale) {
        ElevationProfileZoomMath.calculateVisibleDistance(totalSpan, zoomScale)
    }

    // Extract scalar values
    val rawValues = remember(pathPoints, metricType, unit, paceCeilingMinKm) {
        pathPoints.map { TelemetryMetricUtils.extractMetricValue(it, metricType, unit, paceCeilingMinKm) }
    }

    val validValues = remember(rawValues) { rawValues.filterNotNull() }
    if (validValues.isEmpty()) return

    val (dataMin, dataMax) = remember(validValues, metricType, paceCeilingMinKm, unit) {
        val min = validValues.minOrNull() ?: 0.0
        val max = validValues.maxOrNull() ?: 1.0
        when (metricType) {
            TelemetryMetricType.POWER -> {
                0.0 to (max * 1.1).coerceAtLeast(50.0)
            }
            TelemetryMetricType.HEART_RATE -> {
                (min - 5.0).coerceAtLeast(30.0) to (max + 5.0).coerceAtLeast(100.0)
            }
            TelemetryMetricType.SPEED -> {
                0.0 to (max * 1.1).coerceAtLeast(5.0)
            }
            TelemetryMetricType.PACE -> {
                val effectiveCeiling = if (unit == MyUnits.METRIC) {
                    paceCeilingMinKm.toDouble()
                } else {
                    paceCeilingMinKm.toDouble() * (BANALService.METER_PER_MILE / 1000.0)
                }
                // For Pace: min is fastest, max is slowest
                val paceMin = (min * 0.95).coerceAtLeast(effectiveCeiling)
                val paceMax = (max * 1.05).coerceAtMost(20.0)
                paceMin to paceMax.coerceAtLeast(paceMin + 1.0)
            }
        }
    }

    val axisTextPaint = remember(colorScheme) {
        Paint().apply {
            color = colorScheme.onSurfaceVariant.toArgb()
            textSize = 28f
            isAntiAlias = true
        }
    }

    val axisGridPaint = remember(colorScheme) {
        Paint().apply {
            color = colorScheme.outlineVariant.copy(alpha = 0.35f).toArgb()
            strokeWidth = 1f
            isAntiAlias = true
        }
    }

    val zoneBands = remember(metricType, effectiveHrThresholds, effectivePowerThresholds, dataMin, dataMax) {
        when (metricType) {
            TelemetryMetricType.HEART_RATE -> effectiveHrThresholds?.let {
                TelemetryZoneMath.calculateHeartRateZoneBands(it, dataMin, dataMax)
            } ?: emptyList()
            TelemetryMetricType.POWER -> effectivePowerThresholds?.let {
                TelemetryZoneMath.calculatePowerZoneBands(it, dataMin, dataMax)
            } ?: emptyList()
            else -> emptyList()
        }
    }

    val thresholdDashes = remember(metricType, effectiveHrThresholds, effectivePowerThresholds, dataMin, dataMax) {
        when (metricType) {
            TelemetryMetricType.HEART_RATE -> effectiveHrThresholds?.let {
                TelemetryZoneMath.calculateThresholdDashes(it.z1Max, it.z2Max, it.z3Max, it.z4Max, dataMin, dataMax)
            } ?: emptyList()
            TelemetryMetricType.POWER -> effectivePowerThresholds?.let {
                TelemetryZoneMath.calculateThresholdDashes(it.z1Max, it.z2Max, it.z3Max, it.z4Max, dataMin, dataMax)
            } ?: emptyList()
            else -> emptyList()
        }
    }

    val zoneLabelPaint = remember(colorScheme) {
        Paint().apply {
            color = colorScheme.onSurfaceVariant.toArgb()
            textSize = 24f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
    }

    val currentStartDistState by rememberUpdatedState(startDist)
    val currentZoomScaleState by rememberUpdatedState(zoomScale)
    val currentOnZoomChangedState by rememberUpdatedState(onZoomChanged)
    val currentOnDistanceSelectedState by rememberUpdatedState(onDistanceSelected)

    Box(modifier = modifier.fillMaxWidth()) {
        val baseCanvasModifier = Modifier
            .fillMaxWidth()
            .height(110.dp)

        val canvasModifier = if (enableGestures) {
            baseCanvasModifier.pointerInput(totalSpan, isTimeDomain, isPanMode) {
                val startPaddingPx = 50.dp.toPx()
                val endPaddingPx = 25.dp.toPx()
                val chartWidthPx = (size.width - startPaddingPx - endPaddingPx).coerceAtLeast(1f)
                val touchSlop = viewConfiguration.touchSlop

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var prevX = down.position.x
                    var isDragging = false
                    var isVerticalScrolling = false
                    var localStartDist = currentStartDistState

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        if (pressed.size == 1) {
                            val pointer = pressed[0]
                            val diffX = pointer.position.x - down.position.x
                            val diffY = pointer.position.y - down.position.y

                            if (!isDragging && !isVerticalScrolling) {
                                if (ChartGestureDisambiguator.isDominantVertical(diffX, diffY, touchSlop)) {
                                    isVerticalScrolling = true
                                } else if (ChartGestureDisambiguator.isDominantHorizontal(diffX, diffY, touchSlop)) {
                                    isDragging = true
                                }
                            }

                            if (isDragging) {
                                val dragDeltaX = pointer.position.x - prevX
                                pointer.consume()
                                val activeZoom = currentZoomScaleState
                                val activeVisibleSpan = ElevationProfileZoomMath.calculateVisibleDistance(totalSpan, activeZoom)
                                val onZoomChangedFn = currentOnZoomChangedState
                                if (isPanMode && totalSpan > 10.0 && onZoomChangedFn != null) {
                                    val panStart = ElevationProfileZoomMath.applyPan(
                                        currentStartDist = localStartDist,
                                        visibleDist = activeVisibleSpan,
                                        panDeltaX = dragDeltaX,
                                        canvasWidth = chartWidthPx,
                                        totalDist = totalSpan
                                    )
                                    localStartDist = panStart
                                    onZoomChangedFn(activeZoom, panStart)
                                } else {
                                    val localX = (pointer.position.x - startPaddingPx).coerceIn(0f, chartWidthPx)
                                    val selectedVal = ElevationProfileZoomMath.canvasXToDistance(
                                        canvasX = localX,
                                        startDist = currentStartDistState,
                                        visibleDist = activeVisibleSpan,
                                        canvasWidth = chartWidthPx,
                                        totalDist = totalSpan
                                    )
                                    if (isTimeDomain) {
                                        val targetTimeSec = selectedVal.toLong()
                                        val nearest = pathPoints.minByOrNull { abs(it.timeSec - targetTimeSec) }
                                        if (isTrackless) {
                                            currentOnDistanceSelectedState(nearest?.timeSec?.toDouble())
                                        } else {
                                            currentOnDistanceSelectedState(nearest?.distance)
                                        }
                                    } else {
                                        currentOnDistanceSelectedState(selectedVal)
                                    }
                                }
                                prevX = pointer.position.x
                            } else if (isVerticalScrolling) {
                                if (pointer.isConsumed) {
                                    break
                                }
                            }
                        }
                    }

                    // On gesture completion
                    if (isDragging) {
                        if (!isPanMode) {
                            currentOnDistanceSelectedState(null)
                        }
                    } else if (!isVerticalScrolling) {
                        if (!isPanMode) {
                            // Tap selection
                            val activeVisibleSpan = ElevationProfileZoomMath.calculateVisibleDistance(totalSpan, currentZoomScaleState)
                            val localX = (down.position.x - startPaddingPx).coerceIn(0f, chartWidthPx)
                            val selectedVal = ElevationProfileZoomMath.canvasXToDistance(
                                canvasX = localX,
                                startDist = currentStartDistState,
                                visibleDist = activeVisibleSpan,
                                canvasWidth = chartWidthPx,
                                totalDist = totalSpan
                            )
                            if (isTimeDomain) {
                                val targetTimeSec = selectedVal.toLong()
                                val nearest = pathPoints.minByOrNull { abs(it.timeSec - targetTimeSec) }
                                if (isTrackless) {
                                    currentOnDistanceSelectedState(nearest?.timeSec?.toDouble())
                                } else {
                                    currentOnDistanceSelectedState(nearest?.distance)
                                }
                            } else {
                                currentOnDistanceSelectedState(selectedVal)
                            }
                        }
                    }
                }
            }
        } else {
            baseCanvasModifier
        }

        Canvas(
            modifier = canvasModifier
        ) {
            val startPaddingPx = 50.dp.toPx()
            val endPaddingPx = 25.dp.toPx()
            val bottomPaddingPx = 24.dp.toPx()
            val topPaddingPx = 10.dp.toPx()

            val chartWidthPx = (size.width - startPaddingPx - endPaddingPx).coerceAtLeast(1f)
            val chartHeightPx = (size.height - topPaddingPx - bottomPaddingPx).coerceAtLeast(1f)

            val valRange = (dataMax - dataMin).coerceAtLeast(1.0)

            // Function to map metric value to Y coordinate
            fun valueToY(v: Double): Float {
                return if (metricType == TelemetryMetricType.PACE) {
                    // For pace: faster (smaller value) is plotted higher (near top)
                    val ratio = ((v - dataMin) / valRange).coerceIn(0.0, 1.0)
                    (topPaddingPx + ratio * chartHeightPx).toFloat()
                } else {
                    // Standard: higher value is plotted higher
                    val ratio = ((v - dataMin) / valRange).coerceIn(0.0, 1.0)
                    (topPaddingPx + (1.0 - ratio) * chartHeightPx).toFloat()
                }
            }

            val nativeCanvas = drawContext.canvas.nativeCanvas

            // 1. Draw horizontal background training zone bands (REQ-UI-230 / ATT-1839)
            if (zoneBands.isNotEmpty()) {
                for (band in zoneBands) {
                    val yTop = valueToY(band.maxVal)
                    val yBottom = valueToY(band.minVal)
                    val rectTop = minOf(yTop, yBottom)
                    val rectHeight = abs(yTop - yBottom)

                    drawRect(
                        color = band.color.copy(alpha = TelemetryZoneMath.ZONE_BAND_ALPHA),
                        topLeft = Offset(startPaddingPx, rectTop),
                        size = androidx.compose.ui.geometry.Size(chartWidthPx, rectHeight)
                    )

                    // Draw right-hand secondary zone axis label (Z1-Z5)
                    if (TelemetryZoneMath.shouldRenderZoneLabel(rectHeight, 12.dp.toPx())) {
                        val yCenter = rectTop + (rectHeight / 2f)
                        val textBaseline = yCenter - ((zoneLabelPaint.descent() + zoneLabelPaint.ascent()) / 2f)
                        val rightAxisCenterX = startPaddingPx + chartWidthPx + (endPaddingPx / 2f)
                        nativeCanvas.drawText(band.label, rightAxisCenterX, textBaseline, zoneLabelPaint)
                    }
                }
            }

            // 2. Draw horizontal threshold boundary guidelines (REQ-UI-230 / ATT-1839)
            if (thresholdDashes.isNotEmpty()) {
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()), 0f)
                for (dashVal in thresholdDashes) {
                    val dashY = valueToY(dashVal)
                    drawLine(
                        color = colorScheme.outlineVariant.copy(alpha = 0.35f),
                        start = Offset(startPaddingPx, dashY),
                        end = Offset(startPaddingPx + chartWidthPx, dashY),
                        strokeWidth = 1f,
                        pathEffect = dashEffect
                    )
                }
            }

            // Draw Y-axis labels (min and max)
            val maxLabel = if (metricType == TelemetryMetricType.PACE) {
                TelemetryMetricUtils.formatPaceMinutes(dataMin)
            } else {
                "${dataMax.toInt()}"
            }
            val minLabel = if (metricType == TelemetryMetricType.PACE) {
                TelemetryMetricUtils.formatPaceMinutes(dataMax)
            } else {
                "${dataMin.toInt()}"
            }

            nativeCanvas.drawText(maxLabel, 6.dp.toPx(), topPaddingPx + 10f, axisTextPaint)
            nativeCanvas.drawText(minLabel, 6.dp.toPx(), topPaddingPx + chartHeightPx, axisTextPaint)

            // Draw horizontal boundary grid lines
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

            // Draw X-axis boundary labels (start and end) (REQ-UI-220 / ATT-1819)
            val labelY = size.height - 4.dp.toPx()
            val endLabel = if (isTimeDomain) {
                ElevationProfileZoomMath.formatTimeTick((startDist + visibleSpan).toLong())
            } else {
                distanceFormatter.format_with_units(startDist + visibleSpan)
            }
            val endLabelWidth = axisTextPaint.measureText(endLabel)
            nativeCanvas.drawText(
                endLabel,
                startPaddingPx + chartWidthPx - endLabelWidth,
                labelY,
                axisTextPaint
            )

            if (zoomScale > 1.01f) {
                val startLabel = if (isTimeDomain) {
                    ElevationProfileZoomMath.formatTimeTick(startDist.toLong())
                } else {
                    distanceFormatter.format_with_units(startDist)
                }
                nativeCanvas.drawText(startLabel, startPaddingPx, labelY, axisTextPaint)
            }

            var lastDrawnRightX = if (zoomScale > 1.01f) {
                val startLabel = if (isTimeDomain) {
                    ElevationProfileZoomMath.formatTimeTick(startDist.toLong())
                } else {
                    distanceFormatter.format_with_units(startDist)
                }
                startPaddingPx + axisTextPaint.measureText(startLabel)
            } else {
                startPaddingPx
            }

            val startBoundaryThreshold = startPaddingPx + 40.dp.toPx()
            val endBoundaryThreshold = startPaddingPx + chartWidthPx - (endLabelWidth + 16.dp.toPx())
            val minMilestoneSpacing = 12.dp.toPx()

            // Draw X-axis ticks along bottom
            if (isTimeDomain) {
                val timeStep = ElevationProfileZoomMath.calculateAdaptiveTimeStep(visibleSpan).toDouble()
                var currentSec = (kotlin.math.ceil(startDist / timeStep) * timeStep)
                if (currentSec <= startDist) {
                    currentSec += timeStep
                }
                while (currentSec < startDist + visibleSpan) {
                    val tickX = startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(currentSec, startDist, visibleSpan, chartWidthPx)
                    if (tickX in startPaddingPx..(startPaddingPx + chartWidthPx)) {
                        drawLine(
                            color = colorScheme.outlineVariant.copy(alpha = 0.3f),
                            start = Offset(tickX, topPaddingPx),
                            end = Offset(tickX, topPaddingPx + chartHeightPx),
                            strokeWidth = 1f
                        )
                        val tickLabel = ElevationProfileZoomMath.formatTimeTick(currentSec.toLong())
                        val labelWidth = axisTextPaint.measureText(tickLabel)
                        val labelLeft = tickX - (labelWidth / 2f)
                        val labelRight = tickX + (labelWidth / 2f)

                        if (TelemetryMetricUtils.shouldRenderMilestoneLabel(
                                labelLeft = labelLeft,
                                labelRight = labelRight,
                                lastDrawnRightX = lastDrawnRightX,
                                startBoundaryThreshold = startBoundaryThreshold,
                                endBoundaryThreshold = endBoundaryThreshold,
                                minSpacing = minMilestoneSpacing
                            )
                        ) {
                            nativeCanvas.drawText(tickLabel, labelLeft, labelY, axisTextPaint)
                            lastDrawnRightX = labelRight
                        }
                    }
                    currentSec += timeStep
                }
            } else {
                val distStep = ElevationProfileZoomMath.calculateAdaptiveDistanceStep(visibleSpan, unit).toDouble()
                var currentDist = (kotlin.math.ceil(startDist / distStep) * distStep)
                if (currentDist <= startDist) {
                    currentDist += distStep
                }
                while (currentDist < startDist + visibleSpan) {
                    val tickX = startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(currentDist, startDist, visibleSpan, chartWidthPx)
                    if (tickX in startPaddingPx..(startPaddingPx + chartWidthPx)) {
                        drawLine(
                            color = colorScheme.outlineVariant.copy(alpha = 0.3f),
                            start = Offset(tickX, topPaddingPx),
                            end = Offset(tickX, topPaddingPx + chartHeightPx),
                            strokeWidth = 1f
                        )
                        val label = TelemetryMetricUtils.formatMilestoneLabel(currentDist, visibleSpan, unit)
                        val labelWidth = axisTextPaint.measureText(label)
                        val labelLeft = tickX - (labelWidth / 2f)
                        val labelRight = tickX + (labelWidth / 2f)

                        if (TelemetryMetricUtils.shouldRenderMilestoneLabel(
                                labelLeft = labelLeft,
                                labelRight = labelRight,
                                lastDrawnRightX = lastDrawnRightX,
                                startBoundaryThreshold = startBoundaryThreshold,
                                endBoundaryThreshold = endBoundaryThreshold,
                                minSpacing = minMilestoneSpacing
                            )
                        ) {
                            nativeCanvas.drawText(label, labelLeft, labelY, axisTextPaint)
                            lastDrawnRightX = labelRight
                        }
                    }
                    currentDist += distStep
                }
            }

            // Construct and render curve path
            clipRect(
                left = startPaddingPx,
                top = topPaddingPx,
                right = startPaddingPx + chartWidthPx,
                bottom = topPaddingPx + chartHeightPx
            ) {
                val strokePath = Path()
                val fillPath = Path()

                var isFirst = true
                var firstX = startPaddingPx
                var lastX = startPaddingPx
                val baselineY = topPaddingPx + chartHeightPx

                for (i in pathPoints.indices) {
                    val pt = pathPoints[i]
                    val v = rawValues[i] ?: continue

                    val xSpan = if (isTimeDomain) pt.timeSec.toDouble() else pt.distance
                    val x = startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(xSpan, startDist, visibleSpan, chartWidthPx)
                    val y = valueToY(v)

                    if (isFirst) {
                        strokePath.moveTo(x, y)
                        fillPath.moveTo(x, baselineY)
                        fillPath.lineTo(x, y)
                        firstX = x
                        isFirst = false
                    } else {
                        strokePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                    lastX = x
                }

                if (!isFirst) {
                    fillPath.lineTo(lastX, baselineY)
                    fillPath.lineTo(firstX, baselineY)
                    fillPath.close()

                    // Fill subtle gradient area under curve
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.20f),
                                accentColor.copy(alpha = 0.02f)
                            ),
                            startY = topPaddingPx,
                            endY = baselineY
                        )
                    )

                    // Draw stroke line
                    drawPath(
                        path = strokePath,
                        color = accentColor,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                }
            }

            // Synchronized Scrubbing Cursor & Marker Dot
            if (currentDistance != null) {
                val cursorDistSpan = if (isTimeDomain) {
                    if (isTrackless) {
                        currentDistance
                    } else {
                        val nearestPt = pathPoints.minByOrNull { abs(it.distance - currentDistance) }
                        (nearestPt?.timeSec ?: 0L).toDouble()
                    }
                } else {
                    currentDistance
                }

                if (cursorDistSpan in 0.0..totalSpan && cursorDistSpan in startDist..(startDist + visibleSpan)) {
                    val cursorX = (startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(cursorDistSpan, startDist, visibleSpan, chartWidthPx))
                        .coerceIn(startPaddingPx, startPaddingPx + chartWidthPx)

                    // 1. Vertical dashed cursor line
                    drawLine(
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        start = Offset(cursorX, topPaddingPx),
                        end = Offset(cursorX, topPaddingPx + chartHeightPx),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )

                    // 2. Highlight circle on the curve
                    val nearestPoint = if (isTrackless && isTimeDomain) {
                        pathPoints.minByOrNull { abs(it.timeSec - currentDistance.toLong()) }
                    } else {
                        pathPoints.minByOrNull { abs(it.distance - currentDistance) }
                    }
                    if (nearestPoint != null) {
                        val metricVal = TelemetryMetricUtils.extractMetricValue(nearestPoint, metricType, unit)
                        if (metricVal != null) {
                            val cursorY = valueToY(metricVal)

                            // Outer halo
                            drawCircle(
                                color = accentColor.copy(alpha = 0.35f),
                                radius = 6.dp.toPx(),
                                center = Offset(cursorX, cursorY)
                            )
                            // Inner dot
                            drawCircle(
                                color = accentColor,
                                radius = 3.5.dp.toPx(),
                                center = Offset(cursorX, cursorY)
                            )
                        }
                    }
                }
            }
        }
    }
}
