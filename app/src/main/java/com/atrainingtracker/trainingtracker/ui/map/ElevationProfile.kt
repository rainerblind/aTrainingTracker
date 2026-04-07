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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.ui.theme.Zone1
import com.atrainingtracker.trainingtracker.ui.theme.Zone2
import com.atrainingtracker.trainingtracker.ui.theme.Zone3
import com.atrainingtracker.trainingtracker.ui.theme.Zone4
import com.atrainingtracker.trainingtracker.ui.theme.Zone5

@Composable
fun ElevationProfile(
    pathPoints: List<PathPoint>,
    modifier: Modifier = Modifier
) {
    if (pathPoints.isEmpty()) return

    val minAlt = pathPoints.minOf { it.altitude }
    val maxAlt = pathPoints.maxOf { it.altitude }
    val totalDist = pathPoints.last().distance
    val altRange = (maxAlt - minAlt).coerceAtLeast(1f)

    val colorScheme = MaterialTheme.colorScheme
    val textPaint = Paint().apply {
        color = colorScheme.onSurfaceVariant.toArgb()
        textSize = 36f
        isAntiAlias = true
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp) // Increased height for axes
            .background(colorScheme.surfaceVariant.copy(alpha = 0.1f))
            .padding(bottom = 24.dp, start = 40.dp, end = 16.dp, top = 8.dp)
    ) {
        val width = size.width
        val height = size.height

        // --- 1. Draw Axes Labels ---
        drawIntoCanvas { canvas ->
            val textHeightBuffer = textPaint.textSize // pixels to avoid overlap vertically
            val textWidthBuffer = 80f // pixels to avoid overlap horizontally

            // Altitude labels (Min/Max) - Fixed boundaries
            canvas.nativeCanvas.drawText("${minAlt.toInt()} m", -85f, height, textPaint)
            canvas.nativeCanvas.drawText("${maxAlt.toInt()} m", -85f, 10f, textPaint)

            // Distance label (End) - Fixed boundary
            canvas.nativeCanvas.drawText("${String.format("%.1f", totalDist / 1000f)} km", width - 60f, height + 40f, textPaint)

            // Distance labels (Ticks every 1km)
            val kmStep = 1000f
            var currentKm = 0f
            while (currentKm <= totalDist) {
                val x = (currentKm / totalDist) * width

                // Only draw if NOT too close to the 0 start or the calculated end label
                val isTooCloseToStart = x < 40f
                val isTooCloseToEnd = (width - x) < textWidthBuffer

                if (!isTooCloseToStart && !isTooCloseToEnd) {
                    canvas.nativeCanvas.drawLine(x, height, x, height + 10f, textPaint)
                    if (totalDist < 10000f || currentKm % 5000f == 0f) {
                        val label = "${(currentKm / 1000).toInt()} km"
                        canvas.nativeCanvas.drawText(label, x - 20f, height + 40f, textPaint)
                    }
                }
                currentKm += kmStep
            }

            // Altitude labels (Ticks every 100m)
            val altStep = 100f
            var currentAlt = (Math.ceil(minAlt.toDouble() / altStep) * altStep).toFloat()
            while (currentAlt <= maxAlt) {
                val y = height - ((currentAlt - minAlt) / altRange) * height

                // Only draw if NOT too close to the min altitude (bottom) or max altitude (top)
                val isTooCloseToBottom = (height - y) < textHeightBuffer
                val isTooCloseToTop = y < textHeightBuffer

                if (!isTooCloseToBottom && !isTooCloseToTop) {
                    // Draw a small tick line
                    canvas.nativeCanvas.drawLine(-10f, y, 0f, y, textPaint)

                    // Draw horizontal grid line
                    canvas.nativeCanvas.drawLine(0f, y, width, y, Paint().apply {
                        color = colorScheme.onSurfaceVariant.toArgb()
                        alpha = 30
                        strokeWidth = 1f
                    })

                    canvas.nativeCanvas.drawText("${currentAlt.toInt()} m", -85f, y + 10f, textPaint)
                }
                currentAlt += altStep
            }
        }

        // --- 2. Calculate Grade Segments & Draw Color-coded area ---
        // We iterate through points to create segmented paths or a gradient
        for (i in 0 until pathPoints.size - 1) {
            val p1 = pathPoints[i]
            val p2 = pathPoints[i + 1]

            val x1 = (p1.distance / totalDist) * width
            val y1 = height - ((p1.altitude - minAlt) / altRange) * height
            val x2 = (p2.distance / totalDist) * width
            val y2 = height - ((p2.altitude - minAlt) / altRange) * height

            // Calculate grade: (rise / run) * 100
            val distanceDiff = p2.distance - p1.distance
            val grade = if (distanceDiff > 0) ((p2.altitude - p1.altitude) / distanceDiff) * 100 else 0f

            // Map grade to Zone Colors
            val segmentColor = when {
                grade < 2f -> Zone1
                grade < 5f -> Zone2
                grade < 10f -> Zone3
                grade < 15f -> Zone4
                grade < 20f -> Zone5
                else -> Color.Black
            }

            // Draw the fill segment
            val segmentPath = Path().apply {
                moveTo(x1, y1)
                lineTo(x2, y2)
                lineTo(x2, height)
                lineTo(x1, height)
                close()
            }
            drawPath(path = segmentPath, color = segmentColor.copy(alpha = 0.4f))

            // Draw the line segment
            drawLine(
                color = segmentColor,
                start = androidx.compose.ui.geometry.Offset(x1, y1),
                end = androidx.compose.ui.geometry.Offset(x2, y2),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}