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

package com.atrainingtracker.trainingtracker.ui.climbs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.ui.components.core.EntityDetailSheetScaffold
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds

/**
 * Computes tight LatLngBounds enclosing the climb path, with zero-area expansion fallback.
 */
fun calculateClimbBounds(climb: Climb): LatLngBounds? {
    val points = if (climb.pathPoints.isNotEmpty()) {
        climb.pathPoints.map { it.latLng }
    } else {
        listOf(climb.startLatLng, climb.endLatLng)
    }
    if (points.isEmpty()) return null
    val builder = LatLngBounds.builder()
    points.forEach { builder.include(it) }
    val bounds = builder.build()
    return if (bounds.northeast.latitude == bounds.southwest.latitude &&
        bounds.northeast.longitude == bounds.southwest.longitude
    ) {
        LatLngBounds(
            LatLng(bounds.southwest.latitude - 0.001, bounds.southwest.longitude - 0.001),
            LatLng(bounds.northeast.latitude + 0.001, bounds.northeast.longitude + 0.001)
        )
    } else {
        bounds
    }
}

/**
 * Dedicated Climb Detail Bottom Sheet (REQ-UI-300 / REQ-UI-315 / REQ-UI-333 / ATT-3053).
 *
 * Directly hosts [ClimbOnMapScreen] inside [EntityDetailSheetScaffold] to ensure
 * 100% visual and functional identity with the map screen layout and [com.atrainingtracker.trainingtracker.ui.segments.SegmentDetailSheet]:
 * 1. Unified header featuring [ClimbHeader] and [ClimbDetails].
 * 2. Interactive [com.atrainingtracker.trainingtracker.ui.map.MapDetailLayout] with elevation profile scrubbing and collapsible map viewport.
 * 3. Shared [EntityDetailSheetScaffold] with floating overlay close button.
 */
@Composable
fun ClimbDetailSheet(
    climb: Climb,
    modifier: Modifier = Modifier,
    routeIndex: Int? = null,
    totalRouteClimbs: Int? = null,
    bSportType: BSportType = BSportType.BIKE,
    onDismiss: () -> Unit
) {
    EntityDetailSheetScaffold(
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        ClimbOnMapScreen(
            climb = climb,
            routeIndex = routeIndex,
            totalRouteClimbs = totalRouteClimbs,
            bSportType = bSportType,
            modifier = Modifier.fillMaxSize(),
            useStatusBarsPadding = false
        )
    }
}

/**
 * Isolated zoomed elevation profile canvas for a specific climb (retained for backward compatibility).
 */
@Composable
fun ClimbDetailElevationProfile(
    climb: Climb,
    modifier: Modifier = Modifier
) {
    val pathPoints = climb.pathPoints

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        if (pathPoints.size < 2) {
            val rampPath = Path().apply {
                moveTo(0f, height)
                lineTo(width, 0f)
                lineTo(width, height)
                close()
            }
            drawPath(
                path = rampPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFEF6C00).copy(alpha = 0.5f), Color.Transparent),
                    startY = 0f,
                    endY = height
                )
            )
            drawLine(
                color = Color(0xFFEF6C00),
                start = Offset(0f, height),
                end = Offset(width, 0f),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            return@Canvas
        }

        val startDist = pathPoints.first().distance
        val totalDist = (pathPoints.last().distance - startDist).coerceAtLeast(1.0)
        val minAlt = pathPoints.minOf { it.altitude }
        val maxAlt = pathPoints.maxOf { it.altitude }
        val altSpan = (maxAlt - minAlt).coerceAtLeast(10.0)

        val topPadding = 8f
        val bottomPadding = 4f
        val usableHeight = height - topPadding - bottomPadding

        var prevX = 0f
        var prevY = (height - bottomPadding - (((pathPoints[0].altitude - minAlt) / altSpan) * usableHeight)).toFloat()

        for (i in 1 until pathPoints.size) {
            val p = pathPoints[i]
            val x = (((p.distance - startDist) / totalDist) * width).toFloat().coerceIn(0f, width)
            val y = (height - bottomPadding - (((p.altitude - minAlt) / altSpan) * usableHeight)).toFloat().coerceIn(0f, height)

            val dDist = p.distance - pathPoints[i - 1].distance
            val dAlt = p.altitude - pathPoints[i - 1].altitude
            val grade = if (dDist > 0) (dAlt / dDist) * 100.0 else 0.0

            val segmentColor = when {
                grade < 3.0 -> Color(0xFF4CAF50)
                grade < 6.0 -> Color(0xFF8BC34A)
                grade < 9.0 -> Color(0xFFFFC107)
                grade < 12.0 -> Color(0xFFFF9800)
                grade < 20.0 -> Color(0xFFE53935)
                else -> Color(0xFF212121)
            }

            val fillPath = Path().apply {
                moveTo(prevX, height)
                lineTo(prevX, prevY)
                lineTo(x, y)
                lineTo(x, height)
                close()
            }
            drawPath(
                path = fillPath,
                color = segmentColor.copy(alpha = 0.35f)
            )

            drawLine(
                color = segmentColor,
                start = Offset(prevX, prevY),
                end = Offset(x, y),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            prevX = x
            prevY = y
        }
    }
}
