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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.AltitudeFormatter
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.PaceFormatter
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomain
import com.atrainingtracker.trainingtracker.ui.theme.*
import com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils
import com.google.android.gms.maps.model.LatLng
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.pow

// Data class to cache the pre-calculated geometry and metadata
private data class CachedProfileData(
    val segments: List<ElevationSegment>,
    val minAlt: Double,
    val maxAlt: Double,
    val totalDist: Double,
    val totalTimeSec: Long,
    val altRange: Double,
    val distStep: Float,
    val altStep: Float,
    val adaptiveHeight: androidx.compose.ui.unit.Dp
)

/**
 * ATT-508 / REQ-UI-126: Encapsulates sanitized vertical elevation bounds and calculated range.
 *
 * @property min The sanitized effective minimum altitude (in meters).
 * @property max The sanitized effective maximum altitude (in meters).
 * @property range The effective vertical range (max - min), guaranteed to be >= 1.0.
 */
data class ElevationBounds(
    val min: Double,
    val max: Double,
    val range: Double
)

/**
 * ATT-508 / REQ-UI-126: Pure helper to calculate sanitized vertical elevation bounds for the profile chart.
 *
 * Scrutinizes [minAltitudeOverride] and [maxAltitudeOverride] against the actual [pathPoints]
 * envelope. If an override deviates from stream bounds by more than [outlierToleranceMeters] (e.g. from
 * historical GPS cold-start spikes), it is clamped to the stream envelope. Furthermore, enforces an aesthetic
 * minimum vertical span ([minSpanMeters]) centered around the route elevation to prevent flat routes
 * (such as coastal rides) from compressing into a hairline or magnifying millibar noise.
 *
 * @param pathPoints The list of route points containing altitude values.
 * @param minAltitudeOverride Optional persisted minimum altitude override from database summary extrema.
 * @param maxAltitudeOverride Optional persisted maximum altitude override from database summary extrema.
 * @param outlierToleranceMeters Tolerance threshold in meters beyond which an override is treated as corrupt.
 * @param minSpanMeters Aesthetic minimum vertical span in meters centered around route midpoint.
 * @return [ElevationBounds] containing sanitized min, max, and range.
 */
fun calculateElevationBounds(
    pathPoints: List<PathPoint>,
    minAltitudeOverride: Double? = null,
    maxAltitudeOverride: Double? = null,
    outlierToleranceMeters: Double = 15.0,
    minSpanMeters: Double = 20.0
): ElevationBounds {
    if (pathPoints.isEmpty()) {
        val fallbackMin = minAltitudeOverride ?: 0.0
        val fallbackMax = maxAltitudeOverride ?: (fallbackMin + minSpanMeters)
        val range = (fallbackMax - fallbackMin).coerceAtLeast(1.0)
        return ElevationBounds(fallbackMin, fallbackMax, range)
    }

    val streamMin = pathPoints.minOf { it.altitude }
    val streamMax = pathPoints.maxOf { it.altitude }

    // Sanitize min override:
    // If override is an outlier (< streamMin - 15) OR higher than stream points (> streamMin),
    // clamp to streamMin to guarantee that rendered points never drop below the chart baseline.
    val sanitizedMin = if (minAltitudeOverride != null) {
        if (minAltitudeOverride < streamMin - outlierToleranceMeters || minAltitudeOverride > streamMin) {
            streamMin
        } else {
            minAltitudeOverride
        }
    } else {
        streamMin
    }

    // Sanitize max override:
    // If override is an outlier (> streamMax + 15) OR lower than stream points (< streamMax),
    // clamp to streamMax to guarantee that rendered points never clip above the chart ceiling.
    val sanitizedMax = if (maxAltitudeOverride != null) {
        if (maxAltitudeOverride > streamMax + outlierToleranceMeters || maxAltitudeOverride < streamMax) {
            streamMax
        } else {
            maxAltitudeOverride
        }
    } else {
        streamMax
    }

    // Ensure valid order and envelope containment
    val effectiveMinRaw = minOf(sanitizedMin, streamMin)
    val effectiveMaxRaw = maxOf(sanitizedMax, streamMax)
    val currentSpan = effectiveMaxRaw - effectiveMinRaw

    // Enforce aesthetic minimum vertical span centered on the route
    return if (currentSpan < minSpanMeters) {
        val mid = (effectiveMinRaw + effectiveMaxRaw) / 2.0
        val expandedMin = minOf(mid - (minSpanMeters / 2.0), streamMin)
        val expandedMax = maxOf(mid + (minSpanMeters / 2.0), streamMax)
        val span = expandedMax - expandedMin
        ElevationBounds(expandedMin, expandedMax, span.coerceAtLeast(1.0))
    } else {
        ElevationBounds(effectiveMinRaw, effectiveMaxRaw, currentSpan.coerceAtLeast(1.0))
    }
}

/**
 * Calculates height based on altitude range.
 * Min: 70dp, Max: 200dp (at 1000m range)
 */
fun calculateElevationProfileHeight(range: Double): androidx.compose.ui.unit.Dp {
    val minH = 70f
    val maxH = 200f
    val threshold = 1000.0
    val normalizedRange = (range.coerceIn(0.0, threshold) / threshold).toFloat()
    val curvedRange = normalizedRange.toDouble().pow(0.6).toFloat()
    val height = minH + curvedRange * (maxH - minH)
    return height.toInt().dp
}

private data class ElevationSegment(
    val dist1: Double,
    val timeSec1: Long,
    val altNorm1: Float,
    val dist2: Double,
    val timeSec2: Long,
    val altNorm2: Float,
    val color: Color
)

@Composable
fun ElevationProfile(
    encodedAltitudes: String,
    encodedDistances: String,
    currentDistance: Double? = null,
    minAltitudeOverride: Double? = null,
    maxAltitudeOverride: Double? = null,
    onDistanceSelected: (Double?) -> Unit = {},
    showZoomControls: Boolean = false,
    xAxisDomain: ProfileXAxisDomain = ProfileXAxisDomain.DISTANCE,
    bSportType: BSportType = BSportType.BIKE,
    onPointSelected: ((PathPoint?) -> Unit)? = null,
    zoomScale: Float = 1.0f,
    startDist: Double = 0.0,
    onZoomChanged: ((zoomScale: Float, startDist: Double) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val decodedData = remember(encodedAltitudes, encodedDistances) {
        val alts = NumericalEncodingUtils.decodeDoubles(encodedAltitudes)
        val dists = NumericalEncodingUtils.decodeDoubles(encodedDistances)
        dists.zip(alts) { dist, alt ->
            PathPoint(distance = dist, altitude = alt, latLng = LatLng(0.0, 0.0))
        }
    }

    ElevationProfile(
        pathPoints = decodedData,
        currentDistance = currentDistance,
        minAltitudeOverride = minAltitudeOverride,
        maxAltitudeOverride = maxAltitudeOverride,
        onDistanceSelected = onDistanceSelected,
        showZoomControls = showZoomControls,
        xAxisDomain = xAxisDomain,
        bSportType = bSportType,
        onPointSelected = onPointSelected,
        zoomScale = zoomScale,
        startDist = startDist,
        onZoomChanged = onZoomChanged,
        modifier = modifier
    )
}

@Composable
fun ElevationProfile(
    pathPoints: List<PathPoint>,
    currentDistance: Double?,
    minAltitudeOverride: Double? = null,
    maxAltitudeOverride: Double? = null,
    onDistanceSelected: (Double?) -> Unit = {},
    showZoomControls: Boolean = false,
    xAxisDomain: ProfileXAxisDomain = ProfileXAxisDomain.DISTANCE,
    bSportType: BSportType = BSportType.BIKE,
    onPointSelected: ((PathPoint?) -> Unit)? = null,
    zoomScale: Float = 1.0f,
    startDist: Double = 0.0,
    onZoomChanged: ((zoomScale: Float, startDist: Double) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (pathPoints.isEmpty()) return

    val colorScheme = MaterialTheme.colorScheme
    val unit = TrainingApplication.getUnit()
    var showLegend by remember { mutableStateOf(false) }

    var internalZoomScale by remember(pathPoints) { mutableFloatStateOf(1.0f) }
    var internalStartDist by remember(pathPoints) { mutableDoubleStateOf(0.0) }
    val currentZoomScale = if (onZoomChanged != null) zoomScale else internalZoomScale
    val currentStartDist = if (onZoomChanged != null) startDist else internalStartDist

    fun updateZoom(newZoom: Float, newStart: Double) {
        if (onZoomChanged != null) {
            onZoomChanged(newZoom, newStart)
        } else {
            internalZoomScale = newZoom
            internalStartDist = newStart
        }
    }

    var isPanMode by remember { mutableStateOf(false) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    val cachedData = remember(pathPoints, unit, minAltitudeOverride, maxAltitudeOverride) {
        val maxPoints = 500
        val pathPointsDownsampled = if (pathPoints.size > maxPoints) {
            val step = pathPoints.size / maxPoints
            pathPoints.filterIndexed { index, _ -> index % step == 0 || index == pathPoints.size - 1 }
        } else {
            pathPoints
        }

        val totalDist = pathPointsDownsampled.last().distance
        val totalTimeSec = pathPointsDownsampled.last().timeSec
        val pointCount = pathPointsDownsampled.size
        val avgPointSpacing = if (pointCount > 0) totalDist / pointCount else 1.0
        val targetWindowMeters = 75f
        val calculatedWindow = (targetWindowMeters / avgPointSpacing).toInt().coerceIn(3, 21)
        val windowSize = if (calculatedWindow % 2 == 0) calculatedWindow + 1 else calculatedWindow
        val halfWindow = windowSize / 2

        val smoothedAltitudes = pathPointsDownsampled.indices.map { i ->
            val start = (i - halfWindow).coerceAtLeast(0)
            val end = (i + halfWindow).coerceAtMost(pathPointsDownsampled.size - 1)
            var sum = 0.0
            var count = 0
            for (j in start..end) {
                sum += pathPointsDownsampled[j].altitude
                count++
            }
            sum / count
        }

        val bounds = calculateElevationBounds(
            pathPoints = pathPointsDownsampled,
            minAltitudeOverride = minAltitudeOverride,
            maxAltitudeOverride = maxAltitudeOverride
        )
        val min = bounds.min
        val max = bounds.max
        val range = bounds.range

        val distStep = ElevationProfileZoomMath.calculateAdaptiveDistanceStep(totalDist, unit)

        val altStep = if (unit == MyUnits.METRIC) {
            when {
                range > 2000 -> 1000f
                range > 1000 -> 500f
                range > 500 -> 200f
                range > 100 -> 100f
                else -> 50f
            }
        } else {
            val rangeFeet = range / BANALService.METER_PER_FOOT
            val feetStep = when {
                rangeFeet > 10000 -> 5000f
                rangeFeet > 5000 -> 2000f
                rangeFeet > 2000 -> 1000f
                rangeFeet > 1000 -> 500f
                rangeFeet > 500 -> 200f
                else -> 100f
            }
            (feetStep * BANALService.METER_PER_FOOT).toFloat()
        }

        val segments = mutableListOf<ElevationSegment>()
        for (i in 0 until pathPointsDownsampled.size - 1) {
            val p1 = pathPointsDownsampled[i]
            val p2 = pathPointsDownsampled[i + 1]
            val sAlt1 = smoothedAltitudes[i]
            val sAlt2 = smoothedAltitudes[i + 1]
            val a1 = ((sAlt1 - min) / range).toFloat()
            val a2 = ((sAlt2 - min) / range).toFloat()
            val distDiff = p2.distance - p1.distance
            val grade = if (distDiff > 1.0) ((sAlt2 - sAlt1) / distDiff) * 100 else 0.0

            val color = when {
                grade < 2.0 -> TTColor.Zone1
                grade < 5.0 -> TTColor.Zone2
                grade < 10.0 -> TTColor.Zone3
                grade < 15.0 -> TTColor.Zone4
                grade < 20.0 -> TTColor.Zone5
                else -> Color.Black
            }
            segments.add(ElevationSegment(p1.distance, p1.timeSec, a1, p2.distance, p2.timeSec, a2, color))
        }

        val adaptiveHeight = calculateElevationProfileHeight(range)
        CachedProfileData(segments, min, max, totalDist, totalTimeSec, range, distStep, altStep, adaptiveHeight)
    }

    val isTimeDomain = xAxisDomain == ProfileXAxisDomain.TIME && cachedData.totalTimeSec > 0
    val totalSpan = if (isTimeDomain) cachedData.totalTimeSec.toDouble() else cachedData.totalDist
    val visibleSpan = ElevationProfileZoomMath.calculateVisibleDistance(totalSpan, currentZoomScale)

    val altitudeFormatter = remember(unit) { AltitudeFormatter() }
    val distanceFormatter = remember(unit) { DistanceFormatter() }

    val textPaint = remember(colorScheme) {
        Paint().apply {
            color = colorScheme.onSurfaceVariant.toArgb()
            textSize = 32f
            isAntiAlias = true
        }
    }
    val highlightPaint = remember(colorScheme) {
        Paint().apply {
            color = colorScheme.onSurface.toArgb()
            textSize = 32f
            isAntiAlias = true
            isFakeBoldText = true
        }
    }

    val topPadding = if (showZoomControls) 72.dp else 16.dp
    val totalCanvasHeight = if (showZoomControls) cachedData.adaptiveHeight + 48.dp else cachedData.adaptiveHeight

    Box(modifier = modifier.fillMaxWidth()) {
        val baseCanvasModifier = Modifier
            .fillMaxWidth()
            .height(totalCanvasHeight)

        val canvasModifier = if (showZoomControls) {
            baseCanvasModifier.pointerInput(totalSpan, isTimeDomain, isPanMode, currentZoomScale, currentStartDist) {
                val startPaddingPx = 50.dp.toPx()
                val endPaddingPx = 25.dp.toPx()
                val chartWidthPx = (size.width - startPaddingPx - endPaddingPx).coerceAtLeast(1f)

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var prevCentroid = down.position
                    var prevSpan = 0f
                    var isTransforming = false
                    var isDragging = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        if (pressed.size >= 2 && totalSpan > 10.0) {
                            isTransforming = true
                            val p1 = pressed[0].position
                            val p2 = pressed[1].position
                            val centroid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                            val span = (p1 - p2).getDistance()

                            if (prevSpan > 0f && span > 0f) {
                                val zoomFactor = span / prevSpan
                                val panDeltaX = centroid.x - prevCentroid.x
                                val adjustedCentroidX = (centroid.x - startPaddingPx).coerceIn(0f, chartWidthPx)

                                val (newZoom, newStart) = ElevationProfileZoomMath.applyZoomAtCentroid(
                                    totalDist = totalSpan,
                                    currentZoom = currentZoomScale,
                                    targetZoom = currentZoomScale * zoomFactor,
                                    centroidX = adjustedCentroidX,
                                    canvasWidth = chartWidthPx,
                                    currentStartDist = currentStartDist
                                )
                                val panStart = ElevationProfileZoomMath.applyPan(
                                    currentStartDist = newStart,
                                    visibleDist = ElevationProfileZoomMath.calculateVisibleDistance(totalSpan, newZoom),
                                    panDeltaX = panDeltaX,
                                    canvasWidth = chartWidthPx,
                                    totalDist = totalSpan
                                )
                                updateZoom(newZoom, panStart)
                            }
                            prevSpan = span
                            prevCentroid = centroid
                            pressed.forEach { it.consume() }
                        } else if (pressed.size == 1 && !isTransforming) {
                            val pointer = pressed[0]
                            val diffX = pointer.position.x - down.position.x
                            val diffY = pointer.position.y - down.position.y
                            if (!isDragging && (diffX * diffX + diffY * diffY > 64f)) {
                                isDragging = true
                            }

                            if (isDragging) {
                                val dragDeltaX = pointer.position.x - prevCentroid.x
                                pointer.consume()
                                if (isPanMode && totalSpan > 10.0) {
                                    val panStart = ElevationProfileZoomMath.applyPan(
                                        currentStartDist = currentStartDist,
                                        visibleDist = visibleSpan,
                                        panDeltaX = dragDeltaX,
                                        canvasWidth = chartWidthPx,
                                        totalDist = totalSpan
                                    )
                                    updateZoom(currentZoomScale, panStart)
                                } else {
                                    val adjustedX = (pointer.position.x - startPaddingPx).coerceIn(0f, chartWidthPx)
                                    val selectedValue = ElevationProfileZoomMath.canvasXToDistance(
                                        canvasX = adjustedX,
                                        startDist = currentStartDist,
                                        visibleDist = visibleSpan,
                                        canvasWidth = chartWidthPx,
                                        totalDist = totalSpan
                                    )
                                    val activeIndex = if (isTimeDomain) {
                                        pathPoints.indexOfLast { it.timeSec.toDouble() <= selectedValue }.coerceAtLeast(0)
                                    } else {
                                        pathPoints.indexOfLast { it.distance <= selectedValue }.coerceAtLeast(0)
                                    }
                                    val activePoint = pathPoints.getOrNull(activeIndex) ?: pathPoints.firstOrNull()
                                    onDistanceSelected(activePoint?.distance ?: selectedValue)
                                    onPointSelected?.invoke(activePoint)
                                }
                            }
                            prevCentroid = pointer.position
                        }
                    }

                    // On gesture completion
                    if (!isTransforming) {
                        if (isDragging) {
                            if (!isPanMode) {
                                onDistanceSelected(null)
                                onPointSelected?.invoke(null)
                            }
                        } else {
                            // Tap detection
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastTapTime < 350L && totalSpan > 10.0) {
                                // Double tap reset
                                updateZoom(1.0f, 0.0)
                                lastTapTime = 0L
                                onDistanceSelected(null)
                                onPointSelected?.invoke(null)
                            } else {
                                // Single tap inspection
                                lastTapTime = currentTime
                                val adjustedX = (down.position.x - startPaddingPx).coerceIn(0f, chartWidthPx)
                                val selectedValue = ElevationProfileZoomMath.canvasXToDistance(
                                    canvasX = adjustedX,
                                    startDist = currentStartDist,
                                    visibleDist = visibleSpan,
                                    canvasWidth = chartWidthPx,
                                    totalDist = totalSpan
                                )
                                val activeIndex = if (isTimeDomain) {
                                    pathPoints.indexOfLast { it.timeSec.toDouble() <= selectedValue }.coerceAtLeast(0)
                                } else {
                                    pathPoints.indexOfLast { it.distance <= selectedValue }.coerceAtLeast(0)
                                }
                                val activePoint = pathPoints.getOrNull(activeIndex) ?: pathPoints.firstOrNull()
                                onDistanceSelected(activePoint?.distance ?: selectedValue)
                                onPointSelected?.invoke(activePoint)
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
                .padding(bottom = 24.dp, start = 50.dp, end = 25.dp, top = topPadding)
        ) {
            val width = size.width
            val height = size.height

            drawIntoCanvas { canvas ->
                val minAltLabel = altitudeFormatter.format_with_units(cachedData.minAlt)
                val maxAltLabel = altitudeFormatter.format_with_units(cachedData.maxAlt)
                val minAltWidth = highlightPaint.measureText(minAltLabel)
                val maxAltWidth = highlightPaint.measureText(maxAltLabel)
                canvas.nativeCanvas.drawText(minAltLabel, -minAltWidth - 10f, height, highlightPaint)
                canvas.nativeCanvas.drawText(maxAltLabel, -maxAltWidth - 10f, highlightPaint.textSize, highlightPaint)

                val endLabel = if (isTimeDomain) {
                    ElevationProfileZoomMath.formatTimeTick((currentStartDist + visibleSpan).toLong())
                } else {
                    distanceFormatter.format_with_units(currentStartDist + visibleSpan)
                }
                val endLabelWidth = highlightPaint.measureText(endLabel)
                canvas.nativeCanvas.drawText(endLabel, width - endLabelWidth, height + 45f, highlightPaint)

                if (currentZoomScale > 1.01f) {
                    val startLabel = if (isTimeDomain) {
                        ElevationProfileZoomMath.formatTimeTick(currentStartDist.toLong())
                    } else {
                        distanceFormatter.format_with_units(currentStartDist)
                    }
                    canvas.nativeCanvas.drawText(startLabel, 0f, height + 45f, highlightPaint)
                }

                if (isTimeDomain) {
                    val adaptiveTimeStep = ElevationProfileZoomMath.calculateAdaptiveTimeStep(visibleSpan).toDouble()
                    var currentT = (ceil(currentStartDist / adaptiveTimeStep) * adaptiveTimeStep)
                    if (currentT <= currentStartDist) {
                        currentT += adaptiveTimeStep
                    }
                    while (currentT < currentStartDist + visibleSpan) {
                        val x = ElevationProfileZoomMath.distanceToCanvasX(currentT, currentStartDist, visibleSpan, width)
                        if (x > 60f && (width - x) > (endLabelWidth + 50f)) {
                            canvas.nativeCanvas.drawLine(x, height, x, height - 10f, textPaint)
                            val label = ElevationProfileZoomMath.formatTimeTick(currentT.toLong())
                            val lWidth = textPaint.measureText(label)
                            canvas.nativeCanvas.drawText(label, x - (lWidth / 2), height + 45f, textPaint)
                        }
                        currentT += adaptiveTimeStep
                    }
                } else {
                    val adaptiveDistStep = ElevationProfileZoomMath.calculateAdaptiveDistanceStep(visibleSpan, unit).toDouble()
                    var currentD = (ceil(currentStartDist / adaptiveDistStep) * adaptiveDistStep)
                    if (currentD <= currentStartDist) {
                        currentD += adaptiveDistStep
                    }
                    while (currentD < currentStartDist + visibleSpan) {
                        val x = ElevationProfileZoomMath.distanceToCanvasX(currentD, currentStartDist, visibleSpan, width)
                        if (x > 60f && (width - x) > (endLabelWidth + 50f)) {
                            canvas.nativeCanvas.drawLine(x, height, x, height - 10f, textPaint)
                            val label = if (unit == MyUnits.METRIC) {
                                if (visibleSpan < 1500) "${currentD.toInt()}m"
                                else if (currentD % 1000.0 != 0.0) String.format(Locale.getDefault(), "%.1f", currentD / 1000.0)
                                else "${(currentD / 1000.0).toInt()}"
                            } else {
                                val miles = currentD / BANALService.METER_PER_MILE
                                if (miles % 1.0 != 0.0) String.format(Locale.getDefault(), "%.1f", miles)
                                else "${miles.toInt()}"
                            }
                            val lWidth = textPaint.measureText(label)
                            canvas.nativeCanvas.drawText(label, x - (lWidth / 2), height + 45f, textPaint)
                        }
                        currentD += adaptiveDistStep
                    }
                }

                var currentA = (ceil(cachedData.minAlt / cachedData.altStep) * cachedData.altStep).toFloat()
                var lastY = -1000f
                while (currentA < cachedData.maxAlt) {
                    val y = height - ((currentA - cachedData.minAlt) / cachedData.altRange).toFloat() * height
                    if ((height - y) > (textPaint.textSize * 1.2f) && Math.abs(y - textPaint.textSize) > (textPaint.textSize * 1.2f) && Math.abs(y - lastY) > (textPaint.textSize * 1.2f)) {
                        canvas.nativeCanvas.drawLine(-10f, y, 0f, y, textPaint)
                        drawLine(colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Ghost), Offset(0f, y), Offset(width, y), 1.dp.toPx())
                        canvas.nativeCanvas.drawText(altitudeFormatter.format(currentA.toDouble()), -110f, y + 10f, textPaint)
                        lastY = y
                    }
                    currentA += cachedData.altStep
                }
            }

            clipRect(left = 0f, top = 0f, right = width, bottom = height) {
                cachedData.segments.forEach { seg ->
                    val v1 = if (isTimeDomain) seg.timeSec1.toDouble() else seg.dist1
                    val v2 = if (isTimeDomain) seg.timeSec2.toDouble() else seg.dist2
                    if (v2 < currentStartDist) return@forEach
                    if (v1 > currentStartDist + visibleSpan) return@forEach

                    val x1 = ElevationProfileZoomMath.distanceToCanvasX(v1, currentStartDist, visibleSpan, width)
                    val y1 = height - (seg.altNorm1 * height)
                    val x2 = ElevationProfileZoomMath.distanceToCanvasX(v2, currentStartDist, visibleSpan, width)
                    val y2 = height - (seg.altNorm2 * height)
                    drawPath(
                        Path().apply {
                            moveTo(x1, y1)
                            lineTo(x2, y2)
                            lineTo(x2, height)
                            lineTo(x1, height)
                            close()
                        },
                        seg.color.copy(alpha = TTAlpha.Disabled)
                    )
                    drawLine(seg.color, Offset(x1, y1), Offset(x2, y2), 2.dp.toPx())
                }
            }

            currentDistance?.let { dist ->
                val activeIndex = pathPoints.indexOfLast { it.distance <= dist }.coerceAtLeast(0)
                val activePoint = pathPoints.getOrNull(activeIndex) ?: pathPoints.firstOrNull()
                val pLeft = pathPoints[activeIndex]
                val pRight = pathPoints.getOrNull(activeIndex + 1)
                val interAlt = if (pRight != null && pRight.distance > pLeft.distance) {
                    pLeft.altitude + ((dist - pLeft.distance) / (pRight.distance - pLeft.distance)) * (pRight.altitude - pLeft.altitude)
                } else {
                    pLeft.altitude
                }

                val markerVal = if (isTimeDomain) (activePoint?.timeSec?.toDouble() ?: 0.0) else dist
                if (markerVal in currentStartDist..(currentStartDist + visibleSpan)) {
                    val markerX = ElevationProfileZoomMath.distanceToCanvasX(markerVal, currentStartDist, visibleSpan, width)
                    val markerY = height - (((interAlt - cachedData.minAlt) / cachedData.altRange).toFloat() * height)

                    drawLine(
                        color = colorScheme.primary,
                        start = Offset(markerX, 0f),
                        end = Offset(markerX, height),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    )
                    if (!showZoomControls) {
                        drawIntoCanvas { canvas ->
                            val combinedLabel = "${distanceFormatter.format_with_units(dist)} | ${altitudeFormatter.format_with_units(interAlt)}"
                            val lWidth = highlightPaint.measureText(combinedLabel)
                            canvas.nativeCanvas.drawText(
                                combinedLabel,
                                (markerX - lWidth / 2f).coerceIn(0f, (width - lWidth).coerceAtLeast(0f)),
                                -4.dp.toPx(),
                                highlightPaint
                            )
                        }
                    }
                    drawCircle(colorScheme.onSurface, 5.dp.toPx(), Offset(markerX, markerY))
                    drawCircle(colorScheme.primary, 3.dp.toPx(), Offset(markerX, markerY))
                }
            }
        }

        // Multi-metric telemetry floating badge in detailed view (REQ-UI-201 / ATT-1391)
        if (showZoomControls && currentDistance != null) {
            val activeIndex = pathPoints.indexOfLast { it.distance <= currentDistance }.coerceAtLeast(0)
            val activePoint = pathPoints.getOrNull(activeIndex) ?: pathPoints.firstOrNull()
            if (activePoint != null) {
                val pLeft = pathPoints[activeIndex]
                val pRight = pathPoints.getOrNull(activeIndex + 1)
                val interAlt = if (pRight != null && pRight.distance > pLeft.distance) {
                    pLeft.altitude + ((currentDistance - pLeft.distance) / (pRight.distance - pLeft.distance)) * (pRight.altitude - pLeft.altitude)
                } else {
                    pLeft.altitude
                }

                ScrubbingTelemetryBadge(
                    point = activePoint,
                    bSportType = bSportType,
                    altitude = interAlt,
                    unit = unit,
                    xAxisDomain = xAxisDomain,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 28.dp)
                )
            }
        }

        if (showZoomControls && cachedData.totalDist > 10.0) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 50.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        val (newZoom, newStart) = ElevationProfileZoomMath.applyZoomAtCentroid(
                            totalDist = totalSpan,
                            currentZoom = currentZoomScale,
                            targetZoom = currentZoomScale * 1.5f,
                            centroidX = 0.5f,
                            canvasWidth = 1.0f,
                            currentStartDist = currentStartDist
                        )
                        updateZoom(newZoom, newStart)
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom In",
                        modifier = Modifier.size(16.dp),
                        tint = colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        val (newZoom, newStart) = ElevationProfileZoomMath.applyZoomAtCentroid(
                            totalDist = totalSpan,
                            currentZoom = currentZoomScale,
                            targetZoom = currentZoomScale / 1.5f,
                            centroidX = 0.5f,
                            canvasWidth = 1.0f,
                            currentStartDist = currentStartDist
                        )
                        updateZoom(newZoom, newStart)
                    },
                    enabled = currentZoomScale > 1.01f,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        modifier = Modifier.size(16.dp),
                        tint = if (currentZoomScale > 1.01f) colorScheme.onSurfaceVariant else colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Disabled)
                    )
                }

                IconButton(
                    onClick = { isPanMode = !isPanMode },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isPanMode) Icons.Default.PanTool else Icons.Default.TouchApp,
                        contentDescription = if (isPanMode) "Pan Mode" else "Scrub Mode",
                        modifier = Modifier.size(16.dp),
                        tint = if (isPanMode) colorScheme.primary else colorScheme.onSurfaceVariant
                    )
                }

                if (currentZoomScale > 1.01f) {
                    Surface(
                        onClick = {
                            updateZoom(1.0f, 0.0)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.primaryContainer,
                        modifier = Modifier.height(22.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.1fx", currentZoomScale),
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onPrimaryContainer
                            )
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset Zoom",
                                modifier = Modifier.size(12.dp),
                                tint = colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        if (showZoomControls) {
            IconButton(
                onClick = { showLegend = !showLegend },
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp).size(24.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = "Legend", modifier = Modifier.size(16.dp), tint = colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium))
            }

            if (showLegend) {
                GradeLegend(modifier = Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 4.dp))
            }
        }
    }
}

/**
 * Floating telemetry badge for multi-metric post-workout profile scrubbing.
 * Displays instantaneous Distance, Time, Altitude, Slope, HR, Power, and Speed/Pace.
 * (REQ-UI-201 / ATT-1391)
 */
@Composable
fun ScrubbingTelemetryBadge(
    point: PathPoint,
    bSportType: BSportType,
    altitude: Double,
    unit: MyUnits,
    xAxisDomain: ProfileXAxisDomain,
    modifier: Modifier = Modifier
) {
    val distanceFormatter = remember(unit) { DistanceFormatter() }
    val altitudeFormatter = remember(unit) { AltitudeFormatter() }
    val speedFormatter = remember(unit) { SpeedFormatter() }
    val paceFormatter = remember(unit) { PaceFormatter() }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = TTAlpha.Overlay),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = 3.dp,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier.padding(horizontal = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Row 1: Primary Spatial & Temporal Tracking
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val primaryText = if (xAxisDomain == ProfileXAxisDomain.TIME) {
                    val distStr = if (point.distance > 0.0) " • ${distanceFormatter.format_with_units(point.distance)}" else ""
                    "${ElevationProfileZoomMath.formatTimeTick(point.timeSec)}$distStr"
                } else {
                    val timeStr = if (point.timeSec > 0) " • ${ElevationProfileZoomMath.formatTimeTick(point.timeSec)}" else ""
                    "${distanceFormatter.format_with_units(point.distance)}$timeStr"
                }
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val altStr = altitudeFormatter.format_with_units(altitude)
                val slopeVal = point.slope
                val slopeStr = if (slopeVal != null) {
                    val sign = if (slopeVal > 0) "+" else ""
                    " ($sign${String.format(Locale.getDefault(), "%.1f", slopeVal)}%)"
                } else ""
                Text(
                    text = "$altStr$slopeStr",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Row 2: Telemetry (HR, Power, Speed/Pace)
            val hasHr = point.hr != null && point.hr > 0
            val hasPower = point.power != null && point.power >= 0
            val hasSpeed = point.speedMps != null && point.speedMps > 0.0

            if (hasHr || hasPower || hasSpeed) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasHr) {
                        Text(
                            text = "${point.hr} bpm",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TTColor.Zone4
                        )
                    }
                    if (hasPower) {
                        Text(
                            text = "${point.power} W",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TTColor.Zone5
                        )
                    }
                    if (hasSpeed) {
                        val speedStr = if (bSportType == BSportType.RUN) {
                            val spd = point.speedMps
                            if (spd < 0.55) {
                                paceFormatter.format_with_units(null)
                            } else {
                                paceFormatter.format_with_units(1.0 / spd)
                            }
                        } else {
                            speedFormatter.format_with_units(point.speedMps)
                        }
                        Text(
                            text = speedStr,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradeLegend(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.widthIn(max = 280.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 4.dp
    ) {
        FlowRow(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GradeLegendItem(TTColor.Zone1, "< 2%")
            GradeLegendItem(TTColor.Zone2, "2 - 5%")
            GradeLegendItem(TTColor.Zone3, "5 - 10%")
            GradeLegendItem(TTColor.Zone4, "10 - 15%")
            GradeLegendItem(TTColor.Zone5, "15 - 20%")
            GradeLegendItem(Color.Black, "> 20%")
        }
    }
}

@Composable
private fun GradeLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(modifier = Modifier.size(12.dp), color = color, shape = RoundedCornerShape(2.dp)) {}
        Text(
            text = label, 
            style = MaterialTheme.typography.labelSmall, 
            color = MaterialTheme.colorScheme.onSurface,
            softWrap = false
        )
    }
}
