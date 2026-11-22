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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.ui.climbs.getClimbCategoryColors
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import kotlin.math.abs

internal data class ElevationSegment(
    val dist1: Double,
    val timeSec1: Long,
    val altNorm1: Float,
    val dist2: Double,
    val timeSec2: Long,
    val altNorm2: Float,
    val color: Color
)

/**
 * Precomputed per-climb summit and span metadata cached via `remember(climbs)` so the
 * `ElevationProfile` Canvas draw pass avoids $O(n)$ summit scans per frame (`REQ-UI-2793`).
 */
internal data class CachedClimbDrawData(
    val category: ClimbCategory,
    val summitDistance: Double?,
    val summitAltitude: Double?,
    val startDistance: Double?,
    val endDistance: Double?,
    val bgColor: Color,
    val textColor: Color
)

internal class CachedClimbBadgePaints(
    val badgePaint: Paint,
    val bgPaint: Paint,
    val stemPaint: Paint
)

internal fun precomputeClimbDrawData(climbs: List<Climb>): List<CachedClimbDrawData> {
    if (climbs.isEmpty()) return emptyList()
    return climbs.map { climb ->
        val summitPt = climb.pathPoints.maxByOrNull { it.altitude ?: Double.NEGATIVE_INFINITY }
            ?: climb.pathPoints.lastOrNull()
        val (bgColor, textColor, _) = getClimbCategoryColors(climb.category)
        CachedClimbDrawData(
            category = climb.category,
            summitDistance = summitPt?.distance,
            summitAltitude = summitPt?.altitude,
            startDistance = climb.pathPoints.firstOrNull()?.distance,
            endDistance = climb.pathPoints.lastOrNull()?.distance,
            bgColor = bgColor,
            textColor = textColor
        )
    }
}

internal fun buildClimbBadgePaintCache(density: Density): Map<ClimbCategory, CachedClimbBadgePaints> {
    val badgeTextSizePx = with(density) { 10.sp.toPx() }
    val stemStrokePx = with(density) { 1.5.dp.toPx() }
    return ClimbCategory.entries.associateWith { category ->
        val (bgColor, textColor, _) = getClimbCategoryColors(category)
        CachedClimbBadgePaints(
            badgePaint = Paint().apply {
                color = textColor.toArgb()
                textSize = badgeTextSizePx
                isAntiAlias = true
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            },
            bgPaint = Paint().apply {
                color = bgColor.toArgb()
                isAntiAlias = true
                style = Paint.Style.FILL
            },
            stemPaint = Paint().apply {
                color = bgColor.toArgb()
                strokeWidth = stemStrokePx
                isAntiAlias = true
            }
        )
    }
}

/**
 * Draws visible elevation profile fill polygons coalesced per contiguous color run using a single
 * hoisted [reusableFillPath], followed by crisp top slope strokes (`REQ-UI-2793`).
 *
 * @return Number of coalesced fill draw calls emitted.
 */
internal fun DrawScope.drawElevationSegmentsCoalesced(
    segments: List<ElevationSegment>,
    isTimeDomain: Boolean,
    currentStartDist: Double,
    visibleSpan: Double,
    width: Float,
    height: Float,
    strokeWidthPx: Float,
    reusableFillPath: Path
): Int {
    val endDist = currentStartDist + visibleSpan
    var runColor: Color? = null
    var runLastX = 0f
    var runLastY = 0f
    var hasActiveRun = false
    var fillRunCount = 0

    fun flushRun() {
        val activeColor = runColor
        if (hasActiveRun && activeColor != null) {
            reusableFillPath.lineTo(runLastX, height)
            reusableFillPath.close()
            drawPath(reusableFillPath, activeColor.copy(alpha = TTAlpha.Disabled))
            fillRunCount++
            hasActiveRun = false
            runColor = null
        }
    }

    for (i in segments.indices) {
        val seg = segments[i]
        val v1 = if (isTimeDomain) seg.timeSec1.toDouble() else seg.dist1
        val v2 = if (isTimeDomain) seg.timeSec2.toDouble() else seg.dist2
        if (v2 < currentStartDist || v1 > endDist) continue

        val x1 = ElevationProfileZoomMath.distanceToCanvasX(v1, currentStartDist, visibleSpan, width)
        val y1 = height - (seg.altNorm1 * height)
        val x2 = ElevationProfileZoomMath.distanceToCanvasX(v2, currentStartDist, visibleSpan, width)
        val y2 = height - (seg.altNorm2 * height)

        val isContiguous = hasActiveRun &&
            runColor == seg.color &&
            abs(x1 - runLastX) <= 0.01f &&
            abs(y1 - runLastY) <= 0.01f

        if (!isContiguous) {
            flushRun()
            reusableFillPath.reset()
            reusableFillPath.moveTo(x1, height)
            reusableFillPath.lineTo(x1, y1)
            reusableFillPath.lineTo(x2, y2)
            runColor = seg.color
            runLastX = x2
            runLastY = y2
            hasActiveRun = true
        } else {
            reusableFillPath.lineTo(x2, y2)
            runLastX = x2
            runLastY = y2
        }
    }
    flushRun()

    for (i in segments.indices) {
        val seg = segments[i]
        val v1 = if (isTimeDomain) seg.timeSec1.toDouble() else seg.dist1
        val v2 = if (isTimeDomain) seg.timeSec2.toDouble() else seg.dist2
        if (v2 < currentStartDist || v1 > endDist) continue

        val x1 = ElevationProfileZoomMath.distanceToCanvasX(v1, currentStartDist, visibleSpan, width)
        val y1 = height - (seg.altNorm1 * height)
        val x2 = ElevationProfileZoomMath.distanceToCanvasX(v2, currentStartDist, visibleSpan, width)
        val y2 = height - (seg.altNorm2 * height)
        drawLine(seg.color, Offset(x1, y1), Offset(x2, y2), strokeWidthPx)
    }
    return fillRunCount
}
