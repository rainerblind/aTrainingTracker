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
import android.util.Log
import androidx.compose.animation.core.copy
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.forEach
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.text.color
import com.atrainingtracker.trainingtracker.ui.theme.Zone1
import com.atrainingtracker.trainingtracker.ui.theme.Zone2
import com.atrainingtracker.trainingtracker.ui.theme.Zone3
import com.atrainingtracker.trainingtracker.ui.theme.Zone4
import com.atrainingtracker.trainingtracker.ui.theme.Zone5

// Data class to cache the pre-calculated geometry and metadata
private data class CachedProfileData(
    val segments: List<ElevationSegment>,
    val minAlt: Float,
    val maxAlt: Float,
    val totalDist: Float,
    val altRange: Float
)

private data class ElevationSegment(
    val p1: Offset, // Normalized 0..1
    val p2: Offset, // Normalized 0..1
    val color: Color
)

@Composable
fun ElevationProfile(
    pathPoints: List<PathPoint>,
    currentDistance: Double?,
    modifier: Modifier = Modifier
) {
    if (pathPoints.isEmpty()) return

    val colorScheme = MaterialTheme.colorScheme

    // --- 1. PRO OPTIMIZATION: Cache Static Geometry & Labels ---
    val cachedData = remember(pathPoints) {
        val min = pathPoints.minOf { it.altitude }
        val max = pathPoints.maxOf { it.altitude }
        val dist = pathPoints.last().distance
        val range = (max - min).coerceAtLeast(1f)

        val segments = mutableListOf<ElevationSegment>()
        for (i in 0 until pathPoints.size - 1) {
            val p1 = pathPoints[i]
            val p2 = pathPoints[i + 1]

            val d1 = p1.distance / dist
            val a1 = (p1.altitude - min) / range
            val d2 = p2.distance / dist
            val a2 = (p2.altitude - min) / range

            val distDiff = p2.distance - p1.distance
            val grade = if (distDiff > 0) ((p2.altitude - p1.altitude) / distDiff) * 100 else 0f

            val color = when {
                grade < 2f -> Zone1
                grade < 5f -> Zone2
                grade < 10f -> Zone3
                grade < 15f -> Zone4
                grade < 20f -> Zone5
                else -> Color.Black
            }

            segments.add(ElevationSegment(Offset(d1, a1),
                Offset(d2, a2), color))
        }
        CachedProfileData(segments, min, max, dist, range)
    }

    val textPaint = remember(colorScheme) {
        Paint().apply {
            color = colorScheme.onSurfaceVariant.toArgb()
            textSize = 36f
            isAntiAlias = true
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(colorScheme.surfaceVariant.copy(alpha = 0.1f))
            .padding(bottom = 24.dp, start = 45.dp, end = 20.dp, top = 10.dp)
    ) {
        val width = size.width
        val height = size.height

        // --- 2. Static Background: Axes, Ticks, and Grid ---
        drawIntoCanvas { canvas ->
            val textHeightBuffer = textPaint.textSize
            val textWidthBuffer = 80f

            // Altitude labels (Min/Max)
            canvas.nativeCanvas.drawText("${cachedData.minAlt.toInt()} m", -100f, height, textPaint)
            canvas.nativeCanvas.drawText("${cachedData.maxAlt.toInt()} m", -100f, 10f, textPaint)

            // Distance label (End)
            val endLabel = "${String.format("%.1f", cachedData.totalDist / 1000f)} km"
            canvas.nativeCanvas.drawText(endLabel, width - 60f, height + 40f, textPaint)

            // Distance Ticks (Preserving your optimization logic)
            val kmStep = 1000f
            var currentKm = 0f
            while (currentKm <= cachedData.totalDist) {
                val x = (currentKm / cachedData.totalDist) * width
                val isTooCloseToStart = x < 40f
                val isTooCloseToEnd = (width - x) < textWidthBuffer

                if (!isTooCloseToStart && !isTooCloseToEnd) {
                    canvas.nativeCanvas.drawLine(x, height, x, height + 10f, textPaint)
                    if (cachedData.totalDist < 10000f || currentKm % 5000f == 0f) {
                        val label = "${(currentKm / 1000).toInt()} km"
                        canvas.nativeCanvas.drawText(label, x - 20f, height + 40f, textPaint)
                    }
                }
                currentKm += kmStep
            }

            // Altitude Ticks & Horizontal Grid
            val altStep = 100f
            var currentAlt = (Math.ceil(cachedData.minAlt.toDouble() / altStep) * altStep).toFloat()
            while (currentAlt <= cachedData.maxAlt) {
                val y = height - ((currentAlt - cachedData.minAlt) / cachedData.altRange) * height
                val isTooCloseToBottom = (height - y) < textHeightBuffer
                val isTooCloseToTop = y < textHeightBuffer

                if (!isTooCloseToBottom && !isTooCloseToTop) {
                    canvas.nativeCanvas.drawLine(-10f, y, 0f, y, textPaint)
                    // Grid line
                    drawLine(
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                    canvas.nativeCanvas.drawText("${currentAlt.toInt()} m", -100f, y + 10f, textPaint)
                }
                currentAlt += altStep
            }
        }

        // --- 3. Optimized Segment Drawing ---
        cachedData.segments.forEach { seg ->
            val x1 = seg.p1.x * width
            val y1 = height - (seg.p1.y * height)
            val x2 = seg.p2.x * width
            val y2 = height - (seg.p2.y * height)

            drawPath(
                path = Path().apply {
                    moveTo(x1, y1)
                    lineTo(x2, y2)
                    lineTo(x2, height)
                    lineTo(x1, height)
                    close()
                },
                color = seg.color.copy(alpha = 0.4f)
            )
            drawLine(
                color = seg.color,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 2.dp.toPx()
            )
        }

        // --- 4. Dynamic Progress Marker ---
        currentDistance?.let { dist ->
            val clampedDist = dist.coerceIn(0.0, cachedData.totalDist.toDouble()).toFloat()
            val markerX = (clampedDist / cachedData.totalDist) * width

            // Interpolate Y position (Altitude) for smooth movement
            val activePointIndex = pathPoints.indexOfLast { it.distance <= clampedDist }.coerceAtLeast(0)
            val pLeft = pathPoints[activePointIndex]
            val pRight = pathPoints.getOrNull(activePointIndex + 1)

            val markerY = if (pRight != null) {
                val ratio = (clampedDist - pLeft.distance) / (pRight.distance - pLeft.distance)
                val interAlt = pLeft.altitude + ratio * (pRight.altitude - pLeft.altitude)
                height - ((interAlt - cachedData.minAlt) / cachedData.altRange) * height
            } else {
                height - ((pLeft.altitude - cachedData.minAlt) / cachedData.altRange) * height
            }

            // Dashed Vertical Line
            drawLine(
                color = colorScheme.primary,
                start = Offset(markerX, 0f),
                end = Offset(markerX, height),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f))
            )

            // Progress Dot
            drawCircle(color = colorScheme.onSurface, radius = 6.dp.toPx(), center = Offset(markerX, markerY))
            drawCircle(color = colorScheme.primary, radius = 4.dp.toPx(), center = Offset(markerX, markerY))
        }
    }
}