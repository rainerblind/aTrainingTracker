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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.sensor.formater.AltitudeFormatter
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
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
    val altRange: Double,
    val distStep: Float,
    val altStep: Float,
    val adaptiveHeight: androidx.compose.ui.unit.Dp
)

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
    val p1: Offset, // Normalized 0..1
    val p2: Offset, // Normalized 0..1
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
    modifier: Modifier = Modifier
) {
    if (pathPoints.isEmpty()) return

    val colorScheme = MaterialTheme.colorScheme
    val unit = TrainingApplication.getUnit()
    var showLegend by remember { mutableStateOf(false) }

    val cachedData = remember(pathPoints, unit, minAltitudeOverride, maxAltitudeOverride) {
        val maxPoints = 500
        val pathPointsDownsampled = if (pathPoints.size > maxPoints) {
            val step = pathPoints.size / maxPoints
            pathPoints.filterIndexed { index, _ -> index % step == 0 || index == pathPoints.size - 1 }
        } else {
            pathPoints
        }

        val totalDist = pathPointsDownsampled.last().distance
        val pointCount = pathPointsDownsampled.size
        val avgPointSpacing = totalDist / pointCount
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

        val min = minAltitudeOverride ?: (pathPointsDownsampled.minOfOrNull { it.altitude } ?: 0.0)
        val max = maxAltitudeOverride ?: (pathPointsDownsampled.maxOfOrNull { it.altitude } ?: 1.0)
        val range = (max - min).coerceAtLeast(1.0)

        val distStep = if (unit == MyUnits.METRIC) {
            when {
                totalDist > 100_000 -> 20_000f
                totalDist > 50_000 -> 10_000f
                totalDist > 20_000 -> 5_000f
                totalDist > 5_000 -> 1_000f
                totalDist > 1_500 -> 500f
                else -> 200f
            }
        } else {
            val totalDistMiles = totalDist / BANALService.METER_PER_MILE
            val mileStep = when {
                totalDistMiles > 60 -> 10f
                totalDistMiles > 30 -> 5f
                totalDistMiles > 10 -> 2f
                totalDistMiles > 3 -> 1f
                totalDistMiles > 1 -> 0.5f
                else -> 0.2f
            }
            (mileStep * BANALService.METER_PER_MILE).toFloat()
        }

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
            val d1 = p1.distance / totalDist
            val a1 = (sAlt1 - min) / range
            val d2 = p2.distance / totalDist
            val a2 = (sAlt2 - min) / range
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
            segments.add(ElevationSegment(Offset(d1.toFloat(), a1.toFloat()), Offset(d2.toFloat(), a2.toFloat()), color))
        }

        val adaptiveHeight = calculateElevationProfileHeight(range)
        CachedProfileData(segments, min, max, totalDist, range, distStep, altStep, adaptiveHeight)
    }

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

    Box(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(cachedData.adaptiveHeight)
                .pointerInput(pathPoints) {
                    val startPaddingPx = 50.dp.toPx()
                    val endPaddingPx = 25.dp.toPx()
                    detectDragGestures(
                        onDragStart = { offset ->
                            val chartWidthPx = size.width - startPaddingPx - endPaddingPx
                            val adjustedX = (offset.x - startPaddingPx).coerceIn(0f, chartWidthPx)
                            val dist = (adjustedX / chartWidthPx) * cachedData.totalDist
                            onDistanceSelected(dist)
                        },
                        onDrag = { change, _ ->
                            val chartWidthPx = size.width - startPaddingPx - endPaddingPx
                            val adjustedX = (change.position.x - startPaddingPx).coerceIn(0f, chartWidthPx)
                            val dist = (adjustedX / chartWidthPx) * cachedData.totalDist
                            onDistanceSelected(dist)
                        },
                        onDragEnd = { onDistanceSelected(null) },
                        onDragCancel = { onDistanceSelected(null) }
                    )
                }
                .padding(bottom = 24.dp, start = 50.dp, end = 25.dp, top = 24.dp)
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

                val endLabel = distanceFormatter.format_with_units(cachedData.totalDist)
                val endLabelWidth = highlightPaint.measureText(endLabel)
                canvas.nativeCanvas.drawText(endLabel, width - endLabelWidth, height + 45f, highlightPaint)

                var currentD = cachedData.distStep.toDouble()
                while (currentD < cachedData.totalDist) {
                    val x = (currentD / cachedData.totalDist) * width
                    if (x > 60f && (width - x) > (endLabelWidth + 50f)) {
                        canvas.nativeCanvas.drawLine(x.toFloat(), height, x.toFloat(), height - 10f, textPaint)
                        val label = if (unit == MyUnits.METRIC) {
                            if (cachedData.totalDist < 1500) "${currentD.toInt()}m"
                            else if (currentD % 1000.0 != 0.0) String.format(Locale.getDefault(), "%.1f", currentD / 1000.0)
                            else "${(currentD / 1000.0).toInt()}"
                        } else {
                            val miles = currentD / BANALService.METER_PER_MILE
                            if (miles % 1.0 != 0.0) String.format(Locale.getDefault(), "%.1f", miles)
                            else "${miles.toInt()}"
                        }
                        val lWidth = textPaint.measureText(label)
                        canvas.nativeCanvas.drawText(label, x.toFloat() - (lWidth / 2), height + 45f, textPaint)
                    }
                    currentD += cachedData.distStep
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

            cachedData.segments.forEach { seg ->
                val x1 = seg.p1.x * width
                val y1 = height - (seg.p1.y * height)
                val x2 = seg.p2.x * width
                val y2 = height - (seg.p2.y * height)
                drawPath(Path().apply { moveTo(x1, y1); lineTo(x2, y2); lineTo(x2, height); lineTo(x1, height); close() }, seg.color.copy(alpha = TTAlpha.Disabled))
                drawLine(seg.color, Offset(x1, y1), Offset(x2, y2), 2.dp.toPx())
            }

            currentDistance?.let { dist ->
                val clampedDist = dist.coerceIn(0.0, cachedData.totalDist)
                val markerX = (clampedDist / cachedData.totalDist) * width
                val activeIndex = pathPoints.indexOfLast { it.distance <= clampedDist }.coerceAtLeast(0)
                val pLeft = pathPoints[activeIndex]
                val pRight = pathPoints.getOrNull(activeIndex + 1)
                val interAlt = if (pRight != null) pLeft.altitude + ((clampedDist - pLeft.distance) / (pRight.distance - pLeft.distance)) * (pRight.altitude - pLeft.altitude) else pLeft.altitude
                val markerY = height - ((interAlt - cachedData.minAlt) / cachedData.altRange) * height

                drawLine(
                    color = colorScheme.primary,
                    start = Offset(markerX.toFloat(), 0f),
                    end = Offset(markerX.toFloat(), height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
                drawIntoCanvas { canvas ->
                    val combinedLabel = "${distanceFormatter.format_with_units(clampedDist)} | ${altitudeFormatter.format_with_units(interAlt)}"
                    val lWidth = highlightPaint.measureText(combinedLabel)
                    canvas.nativeCanvas.drawText(combinedLabel, (markerX.toFloat() - lWidth / 2).coerceIn(0f, width - lWidth), -15f, highlightPaint)
                }
                drawCircle(colorScheme.onSurface, 5.dp.toPx(), Offset(markerX.toFloat(), markerY.toFloat()))
                drawCircle(colorScheme.primary, 3.dp.toPx(), Offset(markerX.toFloat(), markerY.toFloat()))
            }
        }

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
