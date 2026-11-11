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

import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.PaceFormatter
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.CardioPowerDynamicsRecord
import com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculator
import com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomain
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.LinearHistogramBin
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.TelemetryHistogramCalculator
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionData
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneTimeEntry
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
    POWER,
    W_PRIME_BAL,
    HRV_DFA_A1,
    HRV_RMSSD
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
    onPointSelected: (PathPoint?) -> Unit = {},
    xAxisDomain: ProfileXAxisDomain = ProfileXAxisDomain.DISTANCE,
    bSportType: BSportType = BSportType.UNKNOWN,
    zoomScale: Float = 1.0f,
    startDist: Double = 0.0,
    isPanMode: Boolean = false,
    onZoomChanged: ((Float, Double) -> Unit)? = null,
    hrZoneThresholds: HeartRateZoneThresholds? = null,
    powerZoneThresholds: PowerZoneThresholds? = null,
    paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM,
    enableGestures: Boolean = true,
    zoneDistribution: ZoneDistributionData? = null,
    zoneDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.FIVE_ZONES,
    cardioPowerDynamics: CardioPowerDynamicsRecord? = null,
    enableCardioPowerDynamics: Boolean = true,
    showFreshModelOverlay: Boolean? = null,
    onShowFreshModelOverlayChange: ((Boolean) -> Unit)? = null
) {
    if (pathPoints.isEmpty()) return

    var localShowFreshModel by rememberSaveable { mutableStateOf(false) }
    val effectiveShowFreshModel = showFreshModelOverlay ?: localShowFreshModel

    val hasPowerData = remember(pathPoints) {
        pathPoints.any { it.power != null && it.power > 0 }
    }
    val canRenderFreshModelOverlay = metricType == TelemetryMetricType.HEART_RATE &&
        cardioPowerDynamics != null &&
        enableCardioPowerDynamics &&
        hasPowerData

    val freshHrTrajectory = remember(pathPoints, cardioPowerDynamics, canRenderFreshModelOverlay) {
        if (canRenderFreshModelOverlay && cardioPowerDynamics != null) {
            val powerSamples = pathPoints.map { it.power?.toDouble() }
            val timeSecSamples = pathPoints.map { it.timeSec }
            PowerHrDynamicsCalculator.simulateTrajectory(
                powerSamples = powerSamples,
                timestampsSec = timeSecSamples,
                tau = cardioPowerDynamics.tauStart,
                hr0 = cardioPowerDynamics.hr0Start,
                k = cardioPowerDynamics.kStart
            )
        } else emptyList()
    }

    val context = LocalContext.current
    val effectiveHrThresholds = remember(hrZoneThresholds, bSportType, context) {
        hrZoneThresholds ?: TelemetryZoneMath.loadHeartRateThresholds(context, bSportType)
    }

    val effectivePowerThresholds = remember(powerZoneThresholds, context) {
        powerZoneThresholds ?: TelemetryZoneMath.loadPowerThresholds(context)
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
            TelemetryMetricType.W_PRIME_BAL -> TTColor.WPrimeBalGreen
            TelemetryMetricType.HRV_DFA_A1 -> TTColor.Zone3
            TelemetryMetricType.HRV_RMSSD -> TTColor.Zone1
        }
    }

    val isTrackless = remember(pathPoints) {
        ProfileDomainMath.isTracklessWorkout(pathPoints)
    }
    val isTimeDomain = remember(xAxisDomain, pathPoints) {
        ProfileDomainMath.isEffectiveTimeDomain(xAxisDomain, pathPoints)
    }
    val totalSpan = remember(xAxisDomain, pathPoints) {
        ProfileDomainMath.calculateTotalSpan(xAxisDomain, pathPoints)
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

    val (dataMin, dataMax) = remember(
        validValues,
        metricType,
        paceCeilingMinKm,
        unit,
        canRenderFreshModelOverlay,
        effectiveShowFreshModel,
        freshHrTrajectory
    ) {
        val effectiveValues = if (metricType == TelemetryMetricType.HEART_RATE &&
            canRenderFreshModelOverlay &&
            effectiveShowFreshModel &&
            freshHrTrajectory.isNotEmpty()
        ) {
            validValues + freshHrTrajectory.filterNotNull()
        } else {
            validValues
        }
        val min = effectiveValues.minOrNull() ?: 0.0
        val max = effectiveValues.maxOrNull() ?: 1.0
        when (metricType) {
            TelemetryMetricType.POWER -> {
                0.0 to (max * 1.1).coerceAtLeast(50.0)
            }
            TelemetryMetricType.W_PRIME_BAL -> {
                0.0 to (max * 1.1).coerceAtLeast(10.0)
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
            TelemetryMetricType.HRV_DFA_A1 -> {
                // Clamped to [0.2, 1.4] with outlier resilience per REQ-PER-035.3 & Rule 48
                0.2 to 1.4
            }
            TelemetryMetricType.HRV_RMSSD -> {
                0.0 to (max * 1.15).coerceAtLeast(100.0)
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
                TelemetryZoneMath.calculatePowerThresholdDashes(it, dataMin, dataMax)
            } ?: emptyList()
            TelemetryMetricType.HRV_DFA_A1 -> listOf(0.50, 0.75)
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
    val currentOnPointSelectedState by rememberUpdatedState(onPointSelected)

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val baseCanvasModifier = Modifier
        .fillMaxWidth()
        .height(110.dp)

    val canvasModifier = if (enableGestures) {
        baseCanvasModifier.pointerInput(totalSpan, isTimeDomain, isPanMode) {
            val startPaddingPx = 50.dp.toPx()
            val endPaddingPx = if (isLandscape) 0.dp.toPx() else 52.dp.toPx()
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
                                    val nearest = TelemetryMetricUtils.findNearestPoint(
                                        points = pathPoints,
                                        targetValue = selectedVal,
                                        isTimeDomain = isTimeDomain
                                    )
                                    currentOnPointSelectedState(nearest)
                                    if (isTimeDomain) {
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
                            currentOnPointSelectedState(null)
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
                            val nearest = TelemetryMetricUtils.findNearestPoint(
                                points = pathPoints,
                                targetValue = selectedVal,
                                isTimeDomain = isTimeDomain
                            )
                            currentOnPointSelectedState(nearest)
                            if (isTimeDomain) {
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

    val nonZonedHistogram = remember(rawValues, pathPoints, dataMin, dataMax, accentColor, zoneDistribution) {
        if (zoneDistribution != null || validValues.isEmpty() || dataMax <= dataMin) {
            emptyList()
        } else {
            val timestamps = pathPoints.map { it.timeSec }
            TelemetryHistogramCalculator.computeLinearMarginalHistogram(
                values = rawValues,
                timestamps = timestamps,
                dataMin = dataMin,
                dataMax = dataMax,
                numBins = 20,
                color = accentColor
            )
        }
    }

    val chartCanvas: @Composable () -> Unit = {
        Canvas(
            modifier = canvasModifier
        ) {
            val startPaddingPx = 50.dp.toPx()
            val endPaddingPx = if (isLandscape) 0.dp.toPx() else 52.dp.toPx()
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

                    // Draw right-hand secondary zone axis label (Z1-Z5) only if marginal bars are not rendered
                    if (zoneDistribution == null && !isLandscape) {
                        if (TelemetryZoneMath.shouldRenderZoneLabel(rectHeight, 12.dp.toPx())) {
                            val yCenter = rectTop + (rectHeight / 2f)
                            val textBaseline = yCenter - ((zoneLabelPaint.descent() + zoneLabelPaint.ascent()) / 2f)
                            val rightAxisCenterX = startPaddingPx + chartWidthPx + (endPaddingPx / 2f)
                            nativeCanvas.drawText(band.label, rightAxisCenterX, textBaseline, zoneLabelPaint)
                        }
                    }
                }
            }

            // 1b. Draw minimalist marginal zone bars / histogram (REQ-UI-251 / ATT-2147 / REQ-UI-446 / ATT-5370)
            if (zoneDistribution != null && !isLandscape) {
                val stripStartPx = startPaddingPx + chartWidthPx + 3.dp.toPx()
                val stripWidthPx = (endPaddingPx - 8.dp.toPx()).coerceAtLeast(10.dp.toPx())

                if (zoneDisplayMode == ZoneCardDisplayMode.FIVE_ZONES || zoneDisplayMode == ZoneCardDisplayMode.COLUMNS || zoneDisplayMode == ZoneCardDisplayMode.STACKED_BAR) {
                    // 5-Zones mode: horizontal bars aligned with zone threshold boundaries
                    for (entry in zoneDistribution.entries) {
                        val band = zoneBands.getOrNull(entry.zoneIndex - 1)
                        if (band != null) {
                            val yTop = valueToY(band.maxVal)
                            val yBottom = valueToY(band.minVal)
                            val rectTop = minOf(yTop, yBottom)
                            val rectHeight = abs(yTop - yBottom)

                            if (rectHeight > 0f) {
                                // Background container track
                                drawRect(
                                    color = entry.color.copy(alpha = 0.15f),
                                    topLeft = Offset(stripStartPx, rectTop),
                                    size = androidx.compose.ui.geometry.Size(stripWidthPx, rectHeight)
                                )

                                // Weighted horizontal bar
                                val barWidthPx = (entry.percentage / 100f).coerceIn(0f, 1f) * stripWidthPx
                                if (barWidthPx > 0f) {
                                    drawRect(
                                        color = entry.color,
                                        topLeft = Offset(stripStartPx, rectTop),
                                        size = androidx.compose.ui.geometry.Size(barWidthPx, rectHeight)
                                    )
                                }
                            }
                        }
                    }
                } else if (zoneDisplayMode == ZoneCardDisplayMode.HISTOGRAM && zoneDistribution.histogram != null) {
                    // Fine-grained histogram mode: frequency bars for each histogram bin (REQ-UI-443)
                    val histogram = zoneDistribution.histogram
                    val linearBins = histogram.linearBins
                    val maxBinPct = linearBins.maxOfOrNull { it.percentage }?.takeIf { it > 0f } ?: 1f

                    for (bin in linearBins) {
                        // Domain boundary filtering & clamping (Rule 70 / ATT-5311)
                        if (bin.rangeMax <= dataMin || bin.rangeMin >= dataMax) continue
                        val clampedMin = maxOf(dataMin, bin.rangeMin.toDouble())
                        val clampedMax = minOf(dataMax, bin.rangeMax.toDouble())
                        if (clampedMin >= clampedMax) continue

                        val yTop = valueToY(clampedMax)
                        val yBottom = valueToY(clampedMin)
                        val rectTop = minOf(yTop, yBottom)
                        val rectHeight = abs(yTop - yBottom).coerceAtLeast(1f)

                        // Background container track (unconditional across linearBins within [dataMin, dataMax], REQ-UI-443.2 / ATT-5281 / ATT-5311)
                        drawRect(
                            color = bin.color.copy(alpha = 0.15f),
                            topLeft = Offset(stripStartPx, rectTop),
                            size = androidx.compose.ui.geometry.Size(stripWidthPx, rectHeight)
                        )

                        // Weighted horizontal frequency bar
                        val barWidthPx = (bin.percentage / maxBinPct).coerceIn(0f, 1f) * stripWidthPx
                        if (barWidthPx > 0f) {
                            drawRect(
                                color = bin.color,
                                topLeft = Offset(stripStartPx, rectTop),
                                size = androidx.compose.ui.geometry.Size(barWidthPx, rectHeight)
                            )
                        }
                    }
                }
            } else if (nonZonedHistogram.isNotEmpty() && !isLandscape) {
                // Non-zoned uniform linear marginal histogram in series color (REQ-UI-446 / ATT-5370)
                val stripStartPx = startPaddingPx + chartWidthPx + 3.dp.toPx()
                val stripWidthPx = (endPaddingPx - 8.dp.toPx()).coerceAtLeast(10.dp.toPx())
                val maxBinPct = nonZonedHistogram.maxOfOrNull { it.percentage }?.takeIf { it > 0f } ?: 1f

                for (bin in nonZonedHistogram) {
                    // Domain boundary filtering & clamping (Rule 70 / ATT-5311)
                    if (bin.rangeMax <= dataMin || bin.rangeMin >= dataMax) continue
                    val clampedMin = maxOf(dataMin, bin.rangeMin)
                    val clampedMax = minOf(dataMax, bin.rangeMax)
                    if (clampedMin >= clampedMax) continue

                    val yTop = valueToY(clampedMax)
                    val yBottom = valueToY(clampedMin)
                    val rectTop = minOf(yTop, yBottom)
                    val rectHeight = abs(yTop - yBottom)
                    if (rectHeight <= 0f) continue

                    // Background container track (unconditional across linearBins within [dataMin, dataMax], Rule 69)
                    drawRect(
                        color = bin.color.copy(alpha = 0.15f),
                        topLeft = Offset(stripStartPx, rectTop),
                        size = androidx.compose.ui.geometry.Size(stripWidthPx, rectHeight)
                    )

                    // Weighted horizontal frequency bar
                    if (bin.durationSec > 0L) {
                        val barWidthPx = (bin.percentage / maxBinPct).coerceIn(0f, 1f) * stripWidthPx
                        if (barWidthPx > 0f) {
                            drawRect(
                                color = bin.color,
                                topLeft = Offset(stripStartPx, rectTop),
                                size = androidx.compose.ui.geometry.Size(barWidthPx, rectHeight)
                            )
                        }
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
            val maxLabel = when (metricType) {
                TelemetryMetricType.PACE -> TelemetryMetricUtils.formatPaceMinutes(dataMin)
                TelemetryMetricType.HRV_DFA_A1 -> String.format(Locale.US, "%.2f", dataMax)
                else -> "${dataMax.toInt()}"
            }
            val minLabel = when (metricType) {
                TelemetryMetricType.PACE -> TelemetryMetricUtils.formatPaceMinutes(dataMax)
                TelemetryMetricType.HRV_DFA_A1 -> String.format(Locale.US, "%.2f", dataMin)
                else -> "${dataMin.toInt()}"
            }

            nativeCanvas.drawText(maxLabel, 6.dp.toPx(), topPaddingPx + 10f, axisTextPaint)
            nativeCanvas.drawText(minLabel, 6.dp.toPx(), topPaddingPx + chartHeightPx, axisTextPaint)

            // Draw threshold guide indicators for DFA a1
            if (metricType == TelemetryMetricType.HRV_DFA_A1) {
                val y075 = valueToY(0.75)
                val y050 = valueToY(0.50)
                nativeCanvas.drawText("0.75", 6.dp.toPx(), y075 + 10f, axisTextPaint)
                nativeCanvas.drawText("0.50", 6.dp.toPx(), y050 + 10f, axisTextPaint)
            }

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

            // Construct and render curve path (with artifact gap breaking & hatching per REQ-PER-035.2)
            clipRect(
                left = startPaddingPx,
                top = topPaddingPx,
                right = startPaddingPx + chartWidthPx,
                bottom = topPaddingPx + chartHeightPx
            ) {
                val isHrvMetric = metricType == TelemetryMetricType.HRV_DFA_A1 || metricType == TelemetryMetricType.HRV_RMSSD
                val baselineY = topPaddingPx + chartHeightPx

                // Collect contiguous valid segments and gap intervals
                val segments = mutableListOf<MutableList<Offset>>()
                var currentSegment = mutableListOf<Offset>()

                data class GapInterval(val startX: Float, val endX: Float)
                val gapIntervals = mutableListOf<GapInterval>()

                var inGap = false
                var gapStartX = startPaddingPx

                for (i in pathPoints.indices) {
                    val pt = pathPoints[i]
                    val v = rawValues[i]
                    val isArtifact = isHrvMetric && (pt.hrvArtifactRate != null && pt.hrvArtifactRate > 5.0)
                    val isValid = v != null && !isArtifact

                    val xSpan = if (isTimeDomain) pt.timeSec.toDouble() else pt.distance
                    val x = startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(xSpan, startDist, visibleSpan, chartWidthPx)

                    if (isValid) {
                        val y = valueToY(v)
                        if (inGap) {
                            gapIntervals.add(GapInterval(gapStartX, x))
                            inGap = false
                        }
                        currentSegment.add(Offset(x, y))
                    } else {
                        if (!inGap) {
                            gapStartX = currentSegment.lastOrNull()?.x ?: x
                            inGap = true
                        }
                        if (currentSegment.isNotEmpty()) {
                            segments.add(currentSegment)
                            currentSegment = mutableListOf()
                        }
                    }
                }
                if (currentSegment.isNotEmpty()) {
                    segments.add(currentSegment)
                } else if (inGap && isHrvMetric) {
                    gapIntervals.add(GapInterval(gapStartX, startPaddingPx + chartWidthPx))
                }

                // 1. Render hatched background overlay across artifact gaps (REQ-PER-035.2)
                if (isHrvMetric && gapIntervals.isNotEmpty()) {
                    for (gap in gapIntervals) {
                        val gLeft = maxOf(startPaddingPx, minOf(gap.startX, gap.endX))
                        val gRight = minOf(startPaddingPx + chartWidthPx, maxOf(gap.startX, gap.endX))
                        if (gRight > gLeft) {
                            clipRect(left = gLeft, top = topPaddingPx, right = gRight, bottom = baselineY) {
                                // Translucent muted gap background
                                drawRect(
                                    color = colorScheme.outlineVariant.copy(alpha = 0.12f),
                                    topLeft = Offset(gLeft, topPaddingPx),
                                    size = Size(gRight - gLeft, chartHeightPx)
                                )
                                // 45-degree angled hatched lines
                                val hatchSpacing = 10.dp.toPx()
                                var hatchX = gLeft - chartHeightPx
                                while (hatchX < gRight + chartHeightPx) {
                                    drawLine(
                                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.22f),
                                        start = Offset(hatchX, baselineY),
                                        end = Offset(hatchX + chartHeightPx, topPaddingPx),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                    hatchX += hatchSpacing
                                }
                            }
                        }
                    }
                }

                // 2. Render valid continuous segments (with polyline break across gaps)
                for (seg in segments) {
                    if (seg.isEmpty()) continue
                    if (seg.size == 1) {
                        // Single point: draw circle dot
                        drawCircle(
                            color = accentColor,
                            radius = 2.5.dp.toPx(),
                            center = seg[0]
                        )
                        continue
                    }
                    val strokePath = Path()
                    val fillPath = Path()

                    val firstPt = seg.first()
                    strokePath.moveTo(firstPt.x, firstPt.y)
                    fillPath.moveTo(firstPt.x, baselineY)
                    fillPath.lineTo(firstPt.x, firstPt.y)

                    for (j in 1 until seg.size) {
                        val pt = seg[j]
                        strokePath.lineTo(pt.x, pt.y)
                        fillPath.lineTo(pt.x, pt.y)
                    }

                    val lastPt = seg.last()
                    fillPath.lineTo(lastPt.x, baselineY)
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

                // Fresh Model HR Overlay & Cardiac Drift Gap Shading (REQ-REV-010 / ATT-3905)
                if (canRenderFreshModelOverlay && effectiveShowFreshModel && freshHrTrajectory.isNotEmpty()) {
                    // 1. Highlight Fresh Calibration Window (minutes 8–55)
                    val calibStartX = if (isTimeDomain) {
                        startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(480.0, startDist, visibleSpan, chartWidthPx)
                    } else {
                        val pt480 = pathPoints.firstOrNull { it.timeSec >= 480L } ?: pathPoints.firstOrNull()
                        if (pt480 != null) {
                            startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(pt480.distance, startDist, visibleSpan, chartWidthPx)
                        } else startPaddingPx
                    }

                    val calibEndX = if (isTimeDomain) {
                        startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(3300.0, startDist, visibleSpan, chartWidthPx)
                    } else {
                        val pt3300 = pathPoints.firstOrNull { it.timeSec >= 3300L } ?: pathPoints.lastOrNull()
                        if (pt3300 != null) {
                            startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(pt3300.distance, startDist, visibleSpan, chartWidthPx)
                        } else startPaddingPx + chartWidthPx
                    }

                    val leftX = maxOf(startPaddingPx, minOf(calibStartX, calibEndX))
                    val rightX = minOf(startPaddingPx + chartWidthPx, maxOf(calibStartX, calibEndX))
                    if (rightX > leftX) {
                        drawRect(
                            color = TTColor.Zone2.copy(alpha = 0.08f),
                            topLeft = Offset(leftX, topPaddingPx),
                            size = Size(rightX - leftX, chartHeightPx)
                        )
                    }

                    // 2. Cardiac Drift Gap Shading (where actual HR > fresh model HR)
                    val driftActualPts = mutableListOf<Offset>()
                    val driftFreshPts = mutableListOf<Offset>()

                    fun flushDrift() {
                        if (driftActualPts.size >= 2) {
                            val driftPoly = Path()
                            driftPoly.moveTo(driftActualPts.first().x, driftActualPts.first().y)
                            for (j in 1 until driftActualPts.size) {
                                driftPoly.lineTo(driftActualPts[j].x, driftActualPts[j].y)
                            }
                            for (j in driftFreshPts.indices.reversed()) {
                                driftPoly.lineTo(driftFreshPts[j].x, driftFreshPts[j].y)
                            }
                            driftPoly.close()
                            drawPath(driftPoly, color = TTColor.Zone4.copy(alpha = 0.22f))
                        }
                        driftActualPts.clear()
                        driftFreshPts.clear()
                    }

                    for (i in pathPoints.indices) {
                        val act = rawValues[i]
                        val fr = freshHrTrajectory.getOrNull(i)
                        if (act != null && fr != null) {
                            val pt = pathPoints[i]
                            val xSpan = if (isTimeDomain) pt.timeSec.toDouble() else pt.distance
                            val x = startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(xSpan, startDist, visibleSpan, chartWidthPx)
                            val yAct = valueToY(act)
                            val yFr = valueToY(fr)

                            if (act > fr) {
                                driftActualPts.add(Offset(x, yAct))
                                driftFreshPts.add(Offset(x, yFr))
                            } else {
                                if (driftActualPts.isNotEmpty()) {
                                    driftActualPts.add(Offset(x, yAct))
                                    driftFreshPts.add(Offset(x, yFr))
                                    flushDrift()
                                }
                            }
                        } else {
                            if (driftActualPts.isNotEmpty()) {
                                flushDrift()
                            }
                        }
                    }
                    flushDrift()

                    // 3. Dashed Cyan Curve for Fresh HR Model (TTColor.Zone2)
                    val freshPath = Path()
                    var freshStarted = false
                    for (i in pathPoints.indices) {
                        val fr = freshHrTrajectory.getOrNull(i) ?: continue
                        val pt = pathPoints[i]
                        val xSpan = if (isTimeDomain) pt.timeSec.toDouble() else pt.distance
                        val x = startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(xSpan, startDist, visibleSpan, chartWidthPx)
                        val y = valueToY(fr)
                        if (!freshStarted) {
                            freshPath.moveTo(x, y)
                            freshStarted = true
                        } else {
                            freshPath.lineTo(x, y)
                        }
                    }
                    if (freshStarted) {
                        drawPath(
                            path = freshPath,
                            color = TTColor.Zone2,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f),
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }
            }

            // Synchronized Scrubbing Cursor & Marker Dot
            if (currentDistance != null) {
                val cursorDistSpan = if (isTimeDomain) {
                    if (isTrackless) {
                        currentDistance
                    } else {
                        val nearestPt = TelemetryMetricUtils.findNearestPoint(
                            points = pathPoints,
                            targetValue = currentDistance,
                            isTimeDomain = false
                        )
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
                    val nearestPoint = TelemetryMetricUtils.findNearestPoint(
                        points = pathPoints,
                        targetValue = currentDistance,
                        isTimeDomain = isTrackless && isTimeDomain
                    )
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

    val freshModelToggleChip: @Composable () -> Unit = {
        if (canRenderFreshModelOverlay) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = if (isLandscape) 0.dp else 16.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                FilterChip(
                    selected = effectiveShowFreshModel,
                    onClick = {
                        val next = !effectiveShowFreshModel
                        localShowFreshModel = next
                        onShowFreshModelOverlayChange?.invoke(next)
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.cardio_power_fresh_model_chip),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("fresh_hr_model_toggle_chip")
                )
            }
        }
    }

    if (isLandscape) {
        Column(modifier = modifier.fillMaxWidth()) {
            freshModelToggleChip()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(0.72f)) {
                    chartCanvas()
                }
                if (zoneDistribution != null) {
                    AlignedMarginalZoneDistribution(
                        zoneDistribution = zoneDistribution,
                        zoneDisplayMode = zoneDisplayMode,
                        zoneBands = zoneBands,
                        dataMin = dataMin,
                        dataMax = dataMax,
                        metricType = metricType,
                        thresholdDashes = thresholdDashes,
                        modifier = Modifier.weight(0.28f).height(110.dp)
                    )
                } else if (nonZonedHistogram.isNotEmpty()) {
                    AlignedMarginalLinearDistribution(
                        bins = nonZonedHistogram,
                        dataMin = dataMin,
                        dataMax = dataMax,
                        metricType = metricType,
                        modifier = Modifier.weight(0.28f).height(110.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(0.28f))
                }
            }
        }
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            freshModelToggleChip()
            Box(modifier = Modifier.fillMaxWidth()) {
                chartCanvas()
            }
        }
    }
}

/**
 * Aligned marginal linear histogram distribution pane for landscape mode telemetry and elevation graphs (REQ-UI-446).
 */
@Composable
fun AlignedMarginalLinearDistribution(
    bins: List<LinearHistogramBin>,
    dataMin: Double,
    dataMax: Double,
    metricType: TelemetryMetricType? = null,
    modifier: Modifier = Modifier,
    topPadding: Dp = 10.dp,
    bottomPadding: Dp = 24.dp
) {
    if (bins.isEmpty()) return
    val colorScheme = MaterialTheme.colorScheme

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        val topPaddingPx = topPadding.toPx()
        val bottomPaddingPx = bottomPadding.toPx()
        val chartHeightPx = (size.height - topPaddingPx - bottomPaddingPx).coerceAtLeast(1f)
        val availableWidthPx = size.width
        val valRange = (dataMax - dataMin).coerceAtLeast(1.0)

        fun valueToY(v: Double): Float {
            return if (metricType == TelemetryMetricType.PACE) {
                val ratio = ((v - dataMin) / valRange).coerceIn(0.0, 1.0)
                (topPaddingPx + ratio * chartHeightPx).toFloat()
            } else {
                val ratio = ((v - dataMin) / valRange).coerceIn(0.0, 1.0)
                (topPaddingPx + (1.0 - ratio) * chartHeightPx).toFloat()
            }
        }

        val maxBinPct = bins.maxOfOrNull { it.percentage }?.takeIf { it > 0f } ?: 1f

        for (bin in bins) {
            // Rule 70: Domain boundary filtering & clamping
            if (bin.rangeMax <= dataMin || bin.rangeMin >= dataMax) continue
            val clampedMin = maxOf(dataMin, bin.rangeMin)
            val clampedMax = minOf(dataMax, bin.rangeMax)
            if (clampedMin >= clampedMax) continue

            val yTop = valueToY(clampedMax)
            val yBottom = valueToY(clampedMin)
            val rectTop = minOf(yTop, yBottom)
            val rectHeight = abs(yTop - yBottom)
            if (rectHeight <= 0f) continue

            // Rule 69: Unconditional background container track
            drawRoundRect(
                color = bin.color.copy(alpha = 0.15f),
                topLeft = Offset(0f, rectTop),
                size = Size(availableWidthPx, rectHeight),
                cornerRadius = CornerRadius(2.dp.toPx())
            )

            // Weighted horizontal frequency bar
            if (bin.durationSec > 0L) {
                val barWidthPx = (bin.percentage / maxBinPct).coerceIn(0f, 1f) * availableWidthPx
                if (barWidthPx > 0f) {
                    drawRoundRect(
                        color = bin.color.copy(alpha = 0.85f),
                        topLeft = Offset(0f, rectTop),
                        size = Size(barWidthPx, rectHeight),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }
            }
        }

        // Draw horizontal boundary grid lines matching adjacent graph
        drawLine(
            color = colorScheme.outlineVariant.copy(alpha = 0.35f),
            start = Offset(0f, topPaddingPx),
            end = Offset(availableWidthPx, topPaddingPx),
            strokeWidth = 1f
        )
        drawLine(
            color = colorScheme.outlineVariant.copy(alpha = 0.35f),
            start = Offset(0f, topPaddingPx + chartHeightPx),
            end = Offset(availableWidthPx, topPaddingPx + chartHeightPx),
            strokeWidth = 1f
        )
    }
}

/**
 * Aligned marginal zone distribution pane for landscape mode telemetry graphs (REQ-UI-431 / ATT-2145).
 *
 * Renders horizontal zone bars (or fine histogram frequency bars) docked immediately to the right
 * of the telemetry graph curve in a 72/28 dual-pane split, maintaining 1:1 vertical Y-axis alignment
 * with the adjacent graph's zone thresholds, and displaying legible zone labels, percentage shares,
 * and formatted durations.
 */
@Composable
fun AlignedMarginalZoneDistribution(
    zoneDistribution: ZoneDistributionData,
    zoneDisplayMode: ZoneCardDisplayMode,
    zoneBands: List<TelemetryZoneBand>,
    dataMin: Double,
    dataMax: Double,
    metricType: TelemetryMetricType,
    modifier: Modifier = Modifier,
    thresholdDashes: List<Double> = emptyList(),
    topPadding: Dp = 10.dp,
    bottomPadding: Dp = 24.dp
) {
    val colorScheme = MaterialTheme.colorScheme
    val timeFormatter = remember { TimeFormatter() }

    val textPaint = remember(colorScheme) {
        Paint().apply {
            color = android.graphics.Color.WHITE
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(4f, 0f, 1f, android.graphics.Color.BLACK)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        val topPaddingPx = topPadding.toPx()
        val bottomPaddingPx = bottomPadding.toPx()
        val chartHeightPx = (size.height - topPaddingPx - bottomPaddingPx).coerceAtLeast(1f)
        val availableWidthPx = size.width
        val valRange = (dataMax - dataMin).coerceAtLeast(1.0)

        fun valueToY(v: Double): Float {
            return if (metricType == TelemetryMetricType.PACE) {
                val ratio = ((v - dataMin) / valRange).coerceIn(0.0, 1.0)
                (topPaddingPx + ratio * chartHeightPx).toFloat()
            } else {
                val ratio = ((v - dataMin) / valRange).coerceIn(0.0, 1.0)
                (topPaddingPx + (1.0 - ratio) * chartHeightPx).toFloat()
            }
        }

        val nativeCanvas = drawContext.canvas.nativeCanvas

        if (zoneDisplayMode == ZoneCardDisplayMode.FIVE_ZONES ||
            zoneDisplayMode == ZoneCardDisplayMode.COLUMNS ||
            zoneDisplayMode == ZoneCardDisplayMode.STACKED_BAR
        ) {
            for (entry in zoneDistribution.entries) {
                val band = zoneBands.getOrNull(entry.zoneIndex - 1)
                if (band != null) {
                    val yTop = valueToY(band.maxVal)
                    val yBottom = valueToY(band.minVal)
                    val rectTop = minOf(yTop, yBottom)
                    val rectHeight = abs(yTop - yBottom)

                    if (rectHeight > 0f) {
                        // 1. Background container track
                        drawRoundRect(
                            color = entry.color.copy(alpha = 0.18f),
                            topLeft = Offset(0f, rectTop),
                            size = Size(availableWidthPx, rectHeight),
                            cornerRadius = CornerRadius(3.dp.toPx())
                        )

                        // 2. Weighted horizontal bar
                        val barWidthPx = (entry.percentage / 100f).coerceIn(0f, 1f) * availableWidthPx
                        if (barWidthPx > 0f) {
                            drawRoundRect(
                                color = entry.color.copy(alpha = 0.85f),
                                topLeft = Offset(0f, rectTop),
                                size = Size(barWidthPx, rectHeight),
                                cornerRadius = CornerRadius(3.dp.toPx())
                            )
                        }

                        // 3. Granular metrics text overlay (REQ-UI-431.3)
                        if (rectHeight >= 12.dp.toPx()) {
                            val zoneLabel = "Z${entry.zoneIndex}"
                            val pctLabel = "${entry.percentage.toInt()}%"
                            val durLabel = timeFormatter.format_with_units(entry.durationSec)
                            val fullText = "$zoneLabel  $pctLabel  $durLabel"

                            textPaint.textSize = (rectHeight * 0.55f).coerceIn(16f, 24f)
                            val yCenter = rectTop + (rectHeight / 2f)
                            val textBaseline = yCenter - ((textPaint.descent() + textPaint.ascent()) / 2f)

                            val textToDraw = if (textPaint.measureText(fullText) <= availableWidthPx - 6.dp.toPx()) {
                                fullText
                            } else if (textPaint.measureText("$zoneLabel $pctLabel") <= availableWidthPx - 6.dp.toPx()) {
                                "$zoneLabel $pctLabel"
                            } else {
                                zoneLabel
                            }

                            nativeCanvas.drawText(textToDraw, 4.dp.toPx(), textBaseline, textPaint)
                        }
                    }
                }
            }
        } else if (zoneDisplayMode == ZoneCardDisplayMode.HISTOGRAM && zoneDistribution.histogram != null) {
            val histogram = zoneDistribution.histogram
            val linearBins = histogram.linearBins
            val maxBinPct = linearBins.maxOfOrNull { it.percentage }?.takeIf { it > 0f } ?: 1f

            for (bin in linearBins) {
                // Domain boundary filtering & clamping (Rule 70 / ATT-5311)
                if (bin.rangeMax <= dataMin || bin.rangeMin >= dataMax) continue
                val clampedMin = maxOf(dataMin, bin.rangeMin.toDouble())
                val clampedMax = minOf(dataMax, bin.rangeMax.toDouble())
                if (clampedMin >= clampedMax) continue

                val yTop = valueToY(clampedMax)
                val yBottom = valueToY(clampedMin)
                val rectTop = minOf(yTop, yBottom)
                val rectHeight = abs(yTop - yBottom).coerceAtLeast(1f)

                // Background container track (unconditional across linearBins within [dataMin, dataMax], REQ-UI-443.3 / ATT-5281 / ATT-5311)
                drawRoundRect(
                    color = bin.color.copy(alpha = 0.15f),
                    topLeft = Offset(0f, rectTop),
                    size = Size(availableWidthPx, rectHeight),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )

                // Weighted horizontal frequency bar
                val barWidthPx = (bin.percentage / maxBinPct).coerceIn(0f, 1f) * availableWidthPx
                if (barWidthPx > 0f) {
                    drawRoundRect(
                        color = bin.color.copy(alpha = 0.85f),
                        topLeft = Offset(0f, rectTop),
                        size = Size(barWidthPx, rectHeight),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }

                // Text overlay for bins with sufficient height
                if (rectHeight >= 12.dp.toPx() && bin.durationSec > 0L) {
                    textPaint.textSize = (rectHeight * 0.50f).coerceIn(14f, 20f)
                    val binText = "${bin.percentage.toInt()}%"
                    val yCenter = rectTop + (rectHeight / 2f)
                    val textBaseline = yCenter - ((textPaint.descent() + textPaint.ascent()) / 2f)
                    nativeCanvas.drawText(binText, 4.dp.toPx(), textBaseline, textPaint)
                }
            }
        }

        // Draw horizontal boundary grid lines matching adjacent graph
        drawLine(
            color = colorScheme.outlineVariant.copy(alpha = 0.35f),
            start = Offset(0f, topPaddingPx),
            end = Offset(availableWidthPx, topPaddingPx),
            strokeWidth = 1f
        )
        drawLine(
            color = colorScheme.outlineVariant.copy(alpha = 0.35f),
            start = Offset(0f, topPaddingPx + chartHeightPx),
            end = Offset(availableWidthPx, topPaddingPx + chartHeightPx),
            strokeWidth = 1f
        )

        // Draw threshold boundary dash guidelines matching adjacent graph
        if (thresholdDashes.isNotEmpty()) {
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()), 0f)
            for (dashVal in thresholdDashes) {
                val dashY = valueToY(dashVal)
                drawLine(
                    color = colorScheme.outlineVariant.copy(alpha = 0.35f),
                    start = Offset(0f, dashY),
                    end = Offset(availableWidthPx, dashY),
                    strokeWidth = 1f,
                    pathEffect = dashEffect
                )
            }
        }
    }
}

@Preview(name = "Aligned Marginal Zone Distribution - Light", widthDp = 220, heightDp = 120, showBackground = true)
@Composable
private fun AlignedMarginalZoneDistributionLightPreview() {
    MaterialTheme {
        val entries = listOf(
            ZoneTimeEntry(1, 0, 120L, 10f, TTColor.Zone1),
            ZoneTimeEntry(2, 0, 360L, 30f, TTColor.Zone2),
            ZoneTimeEntry(3, 0, 480L, 40f, TTColor.Zone3),
            ZoneTimeEntry(4, 0, 180L, 15f, TTColor.Zone4),
            ZoneTimeEntry(5, 0, 60L, 5f, TTColor.Zone5)
        )
        val zoneBands = listOf(
            TelemetryZoneBand(1, 100.0, 120.0, TTColor.Zone1, "Z1"),
            TelemetryZoneBand(2, 120.0, 140.0, TTColor.Zone2, "Z2"),
            TelemetryZoneBand(3, 140.0, 160.0, TTColor.Zone3, "Z3"),
            TelemetryZoneBand(4, 160.0, 180.0, TTColor.Zone4, "Z4"),
            TelemetryZoneBand(5, 180.0, 200.0, TTColor.Zone5, "Z5")
        )
        AlignedMarginalZoneDistribution(
            zoneDistribution = ZoneDistributionData(1200L, entries),
            zoneDisplayMode = ZoneCardDisplayMode.FIVE_ZONES,
            zoneBands = zoneBands,
            dataMin = 95.0,
            dataMax = 205.0,
            metricType = TelemetryMetricType.HEART_RATE
        )
    }
}

@Preview(name = "Aligned Marginal Zone Distribution - Dark", widthDp = 220, heightDp = 120, showBackground = true)
@Composable
private fun AlignedMarginalZoneDistributionDarkPreview() {
    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
        val entries = listOf(
            ZoneTimeEntry(1, 0, 120L, 10f, TTColor.Zone1),
            ZoneTimeEntry(2, 0, 360L, 30f, TTColor.Zone2),
            ZoneTimeEntry(3, 0, 480L, 40f, TTColor.Zone3),
            ZoneTimeEntry(4, 0, 180L, 15f, TTColor.Zone4),
            ZoneTimeEntry(5, 0, 60L, 5f, TTColor.Zone5)
        )
        val zoneBands = listOf(
            TelemetryZoneBand(1, 100.0, 120.0, TTColor.Zone1, "Z1"),
            TelemetryZoneBand(2, 120.0, 140.0, TTColor.Zone2, "Z2"),
            TelemetryZoneBand(3, 140.0, 160.0, TTColor.Zone3, "Z3"),
            TelemetryZoneBand(4, 160.0, 180.0, TTColor.Zone4, "Z4"),
            TelemetryZoneBand(5, 180.0, 200.0, TTColor.Zone5, "Z5")
        )
        AlignedMarginalZoneDistribution(
            zoneDistribution = ZoneDistributionData(1200L, entries),
            zoneDisplayMode = ZoneCardDisplayMode.FIVE_ZONES,
            zoneBands = zoneBands,
            dataMin = 95.0,
            dataMax = 205.0,
            metricType = TelemetryMetricType.HEART_RATE
        )
    }
}
