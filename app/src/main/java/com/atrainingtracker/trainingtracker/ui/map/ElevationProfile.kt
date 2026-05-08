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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import com.atrainingtracker.trainingtracker.ui.theme.Zone1
import com.atrainingtracker.trainingtracker.ui.theme.Zone2
import com.atrainingtracker.trainingtracker.ui.theme.Zone3
import com.atrainingtracker.trainingtracker.ui.theme.Zone4
import com.atrainingtracker.trainingtracker.ui.theme.Zone5
import com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils
import com.google.android.gms.maps.model.LatLng

// Data class to cache the pre-calculated geometry and metadata
private data class CachedProfileData(
    val segments: List<ElevationSegment>,
    val minAlt: Double,
    val maxAlt: Double,
    val totalDist: Double,
    val altRange: Double,
    val kmStep: Float,
    val altStep: Float
)

private data class ElevationSegment(
    val p1: Offset, // Normalized 0..1
    val p2: Offset, // Normalized 0..1
    val color: Color
)

/**
 * Optimized ElevationProfile that accepts encoded strings.
 * Use this in lists for maximum performance.
 */
@Composable
fun ElevationProfile(
    encodedAltitudes: String,
    encodedDistances: String,
    currentDistance: Double? = null,
    onDistanceSelected: (Double?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 1. Decode strings into simple Double lists
    // This happens only if the strings change
    val decodedData = remember(encodedAltitudes, encodedDistances) {
        val alts = NumericalEncodingUtils.decodeDoubles(encodedAltitudes)
        val dists = NumericalEncodingUtils.decodeDoubles(encodedDistances)

        // Map to PathPoint objects for compatibility with the existing rendering logic
        dists.zip(alts) { dist, alt ->
            PathPoint(distance = dist,
                altitude = alt,
                latLng = LatLng(0.0, 0.0) // LatLng not needed for profile
            )
        }
    }

    // 2. Call the existing rendering logic
    ElevationProfile(
        pathPoints = decodedData,
        currentDistance = currentDistance,
        onDistanceSelected = onDistanceSelected,
        modifier = modifier
    )
}

@Composable
fun ElevationProfile(
    pathPoints: List<PathPoint>,
    currentDistance: Double?,
    onDistanceSelected: (Double?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (pathPoints.isEmpty()) return

    val colorScheme = MaterialTheme.colorScheme

    // --- 1. Cache Static Geometry & Adaptive Labels ---
    val cachedData = remember(pathPoints) {
        val totalDist = pathPoints.last().distance
        val pointCount = pathPoints.size

        // --- Adaptive Smoothing Logic ---
        // Calculate average distance between points (e.g., 5m or 20m)
        val avgPointSpacing = totalDist / pointCount

        // We want a smoothing window of ~80 meters.
        // WindowSize = 80m / spacing. We ensure it's at least 3 and always odd.
        val targetWindowMeters = 80f
        val calculatedWindow = (targetWindowMeters / avgPointSpacing).toInt()
            .coerceIn(3, 51) // Don't go below 3 or above 51 to keep performance high

        val windowSize = if (calculatedWindow % 2 == 0) calculatedWindow + 1 else calculatedWindow
        val halfWindow = windowSize / 2

        val smoothedAltitudes = pathPoints.indices.map { i ->
            val start = (i - halfWindow).coerceAtLeast(0)
            val end = (i + halfWindow).coerceAtMost(pathPoints.size - 1)
            var sum = 0.0
            var count = 0
            for (j in start..end) {
                sum += pathPoints[j].altitude
                count++
            }
            sum / count
        }

        // Use smoothed altitudes for Min/Max to avoid "spikes" affecting the scale
        val min = smoothedAltitudes.minOrNull() ?: 0.0
        val max = smoothedAltitudes.maxOrNull() ?: 1.0
        val range = (max - min).coerceAtLeast(1.0)

        // Adaptive Distance Ticks
        val kmStep = when {
            totalDist > 100_000 -> 20_000f
            totalDist > 50_000 -> 10_000f
            totalDist > 20_000 -> 5_000f
            totalDist > 5_000 -> 1_000f
            totalDist > 1_500 -> 500f
            else -> 200f              // For segments < 1.5km, use 200m steps
        }

        // Adaptive Altitude Ticks
        val altStep = when {
            range > 2000 -> 1000f
            range > 1000 -> 500f
            range > 500 -> 200f
            range > 100 -> 100f
            else -> 50f
        }

        val segments = mutableListOf<ElevationSegment>()
        for (i in 0 until pathPoints.size - 1) {
            val p1 = pathPoints[i]
            val p2 = pathPoints[i + 1]

            // Use Smoothed Altitudes for geometry
            val sAlt1 = smoothedAltitudes[i]
            val sAlt2 = smoothedAltitudes[i + 1]

            val d1: Double = p1.distance / totalDist
            val a1: Double = (sAlt1 - min) / range
            val d2 = p2.distance / totalDist
            val a2 = (sAlt2 - min) / range

            val distDiff = p2.distance - p1.distance

            // Use Smoothed Altitudes for Grade calculation (much more stable colors!)
            val grade = if (distDiff > 1.0) { // Small threshold to avoid division issues
                ((sAlt2 - sAlt1) / distDiff) * 100
            } else 0.0

            val color = when {
                grade < 2.0 -> Zone1
                grade < 5.0 -> Zone2
                grade < 10.0 -> Zone3
                grade < 15.0 -> Zone4
                grade < 20.0 -> Zone5
                else -> Color.Black
            }

            segments.add(ElevationSegment(Offset(d1.toFloat(), a1.toFloat()), Offset(d2.toFloat(), a2.toFloat()), color))
        }
        CachedProfileData(segments, min, max, totalDist, range, kmStep, altStep)
    }

    val textPaint = remember(colorScheme) {
        Paint().apply {
            color = colorScheme.onSurfaceVariant.toArgb()
            textSize = 32f
            isAntiAlias = true
        }
    }

    Canvas(
        modifier = modifier
            .pointerInput(pathPoints) {
                // Convert DP padding to PX
                val startPaddingPx = 50.dp.toPx()
                val endPaddingPx = 25.dp.toPx()

                detectDragGestures(
                    onDragStart = { offset ->
                        // Calculate width of the actual chart area
                        val chartWidthPx = size.width - startPaddingPx - endPaddingPx
                        // Adjust touch X: subtract start padding and clamp to chart bounds
                        val adjustedX = (offset.x - startPaddingPx).coerceIn(0f, chartWidthPx)

                        val dist = (adjustedX / chartWidthPx) * cachedData.totalDist
                        onDistanceSelected(dist.toDouble())
                    },
                    onDrag = { change, _ ->
                        val chartWidthPx = size.width - startPaddingPx - endPaddingPx
                        val adjustedX = (change.position.x - startPaddingPx).coerceIn(0f, chartWidthPx)

                        val dist = (adjustedX / chartWidthPx) * cachedData.totalDist
                        onDistanceSelected(dist.toDouble())
                    },
                    onDragEnd = { onDistanceSelected(null) },
                    onDragCancel = { onDistanceSelected(null) }
                )
            }
            .fillMaxWidth()
            .padding(bottom = 24.dp, start = 50.dp, end = 25.dp, top = 24.dp)
    ) {
        val width = size.width
        val height = size.height

        // --- 2. Static Background: Axes, Ticks, and Grid ---
        drawIntoCanvas { canvas ->
            val textHeightBuffer = textPaint.textSize
            val textWidthBuffer = 90f

            // 2a. Altitude labels
            canvas.nativeCanvas.drawText("${cachedData.minAlt.toInt()} m", -110f, height, textPaint)
            canvas.nativeCanvas.drawText("${cachedData.maxAlt.toInt()} m", -110f, 10f, textPaint)

            // 2b. Distance label (End point) - Handles meters vs km
            val endLabel = if (cachedData.totalDist < 1000) {
                "${cachedData.totalDist.toInt()} m"
            } else {
                "${String.format("%.1f", cachedData.totalDist / 1000f)} km"
            }
            canvas.nativeCanvas.drawText(endLabel, width - 40f, height + 45f, textPaint)

            // 2c. Adaptive Distance Ticks
            var currentKm = cachedData.kmStep
            while (currentKm < cachedData.totalDist) {
                val x = (currentKm / cachedData.totalDist) * width

                val isTooCloseToStart = x < 60f
                val isTooCloseToEnd = (width - x) < textWidthBuffer

                if (!isTooCloseToStart && !isTooCloseToEnd) {
                    canvas.nativeCanvas.drawLine(x.toFloat(), height, x.toFloat(), height - 10f, textPaint)

                    // IMPROVED LABEL LOGIC:
                    val label = when {
                        // Short segments (< 1.5km): "200m", "400m", etc.
                        cachedData.totalDist < 1500 -> "${currentKm.toInt()}m"

                        // Mid-length segments with sub-km steps (like your 4.4km / 500m case):
                        // If currentKm is not a multiple of 1000, show "1.5"
                        currentKm % 1000f != 0f -> String.format("%.1f", currentKm / 1000f)

                        // Whole kilometer steps: "1", "2", "3"
                        else -> "${(currentKm / 1000).toInt()}"
                    }

                    canvas.nativeCanvas.drawText(label, x.toFloat() - 15f, height + 45f, textPaint)
                }
                currentKm += cachedData.kmStep
            }

            // 2d. Adaptive Altitude Ticks...
            var currentAlt = (ceil(cachedData.minAlt / cachedData.altStep) * cachedData.altStep).toFloat()
            while (currentAlt < cachedData.maxAlt) {
                val y = height - ((currentAlt - cachedData.minAlt) / cachedData.altRange) * height

                val isTooCloseToBottom = (height - y) < textHeightBuffer
                val isTooCloseToTop = y < textHeightBuffer

                if (!isTooCloseToBottom && !isTooCloseToTop) {
                    canvas.nativeCanvas.drawLine(-10f, y.toFloat(), 0f, y.toFloat(), textPaint)
                    drawLine(
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                        start = Offset(0f, y.toFloat()),
                        end = Offset(width, y.toFloat()),
                        strokeWidth = 1.dp.toPx()
                    )
                    canvas.nativeCanvas.drawText("${currentAlt.toInt()}", -110f, y.toFloat() + 10f, textPaint)
                }
                currentAlt += cachedData.altStep
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
                color = seg.color.copy(alpha = 0.3f)
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

            val activeIndex = pathPoints.indexOfLast { it.distance <= clampedDist }.coerceAtLeast(0)
            val pLeft = pathPoints[activeIndex]
            val pRight = pathPoints.getOrNull(activeIndex + 1)

            val interAlt = if (pRight != null) {
                val ratio = (clampedDist - pLeft.distance) / (pRight.distance - pLeft.distance)
                pLeft.altitude + ratio * (pRight.altitude - pLeft.altitude)
            } else {
                pLeft.altitude
            }

            val markerY = height - ((interAlt - cachedData.minAlt) / cachedData.altRange) * height

            // Draw Vertical Dashed Line
            drawLine(
                color = colorScheme.primary,
                start = Offset(markerX.toFloat(), 0f),
                end = Offset(markerX.toFloat(), height),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
            )

            // Draw Labels at the top of the line
            drawIntoCanvas { canvas ->
                val distLabel = if (clampedDist < 1000) {
                    "${clampedDist.toInt()} m"
                } else {
                    "${String.format("%.2f", clampedDist / 1000f)} km"
                }
                val altLabel = "${interAlt.toInt()} m"
                val combinedLabel = "$distLabel | $altLabel"

                // Calculate text width to center it or keep it on screen
                val labelWidth = textPaint.measureText(combinedLabel)
                var labelX = markerX.toFloat() - (labelWidth / 2)

                // Keep label within chart bounds
                labelX = labelX.coerceIn(0f, width - labelWidth)

                canvas.nativeCanvas.drawText(
                    combinedLabel,
                    labelX,
                    -15f, // Draw slightly above the top of the chart
                    textPaint.apply {
                        // Optional: Make the active label bold or a different color
                        isFakeBoldText = true
                    }
                )
            }

            // Draw Intersection Points (Circles)
            drawCircle(color = colorScheme.onSurface, radius = 5.dp.toPx(), center = Offset(markerX.toFloat(), markerY.toFloat()))
            drawCircle(color = colorScheme.primary, radius = 3.dp.toPx(), center = Offset(markerX.toFloat(), markerY.toFloat()))
        }
    }
}