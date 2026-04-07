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

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun ElevationProfile(
    pathPoints: List<PathPoint>,
    modifier: Modifier = Modifier
) {
    if (pathPoints.isEmpty()) return

    // Calculate bounds for scaling
    val minAlt = pathPoints.minOf { it.altitude }
    val maxAlt = pathPoints.maxOf { it.altitude }
    val totalDist = pathPoints.last().distance
    val altRange = (maxAlt - minAlt).coerceAtLeast(1f) // Avoid division by zero
    Log.i("ElevationProfile", "minAlt=$minAlt, maxAlt=$maxAlt, totalDist=$totalDist, altRange=$altRange")

    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            // .height(200.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(vertical = 8.dp)
    ) {
        val width = size.width
        val height = size.height

        val path = Path()

        pathPoints.forEachIndexed { index, point ->
            // Scale X based on distance
            val x = (point.distance / totalDist) * width
            // Scale Y based on altitude (inverted for screen coordinates)
            val y = height - ((point.altitude - minAlt) / altRange) * height

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        // 1. Draw the filled area under the curve
        val fillPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            path = fillPath,
            color = primaryColor.copy(alpha = 0.2f)
        )

        // 2. Draw the actual elevation line
        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}