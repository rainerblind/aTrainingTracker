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

package com.atrainingtracker.trainingtracker.ui.components.core

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standardized Material 3 bottom sheet design tokens and contour styling (REQ-UI-189, ATT-1588).
 *
 * Provides a unified visual grammar for modal bottom sheets and persistent scaffold sheets
 * through physical form, top boundary contouring, and elevation drop shadows rather than
 * arbitrary background color shifts or multi-color status bars.
 */
object BottomSheetDesign {
    /** Standardized top corner curvature radius (20dp). */
    val SheetCornerRadius: Dp = 20.dp

    /** Standardized top curvature shape with 20dp top corners and 0dp bottom corners. */
    val SheetShape: Shape = RoundedCornerShape(topStart = SheetCornerRadius, topEnd = SheetCornerRadius)

    /** Standardized drop shadow elevation for persistent scaffold sheets (8dp). */
    val SheetShadowElevation: Dp = 8.dp

    /** Standardized zero tonal elevation for bottom sheets to eliminate grey tinting (0dp, REQ-UI-218, ATT-1817). */
    val SheetTonalElevation: Dp = 0.dp

    /** Standardized border outline stroke width (1dp). */
    val BorderWidth: Dp = 1.dp

    /** Standardized drag handle pill width (32dp). */
    val DragHandleWidth: Dp = 32.dp

    /** Standardized drag handle pill height (3dp). */
    val DragHandleHeight: Dp = 3.dp

    // --- Standardized Peek Height Baselines (REQ-UI-221, ATT-1645) ---

    /** Calibrated baseline for single workout detail peeks in analytical map screens (Heatmap & Period Map) cleanly framing WorkoutHeader. */
    val PeekHeightWorkout: Dp = 140.dp

    /** Calibrated baseline for route detail peeks framing RouteSummaryHeader and visibility switch. */
    val PeekHeightRoute: Dp = 112.dp

    /** Calibrated baseline for segment detail peeks framing SegmentHeader and SegmentDetails without empty container gaps. */
    val PeekHeightSegment: Dp = 156.dp

    /** Calibrated baseline for favorite location (Lieblingsort) peeks framing KnownLocationOnMapSheet. */
    val PeekHeightKnownLocation: Dp = 108.dp

    /** Calibrated baseline for active live segment tracking peek framing live delta and target metrics. */
    val PeekHeightLiveSegment: Dp = 140.dp
}

/**
 * Applies top contour clipping and a subtle boundary border stroke to bottom sheets.
 *
 * Ensures sheet content adheres to the rounded top corners and provides a crisp, visible
 * boundary against underlying map canvases and dark mode surfaces.
 *
 * @param borderColor Boundary outline stroke color. Defaults to `outlineVariant` at 60% opacity.
 * @param shape Shape to clip and outline. Defaults to [BottomSheetDesign.SheetShape].
 * @param borderWidth Thickness of the outline border. Defaults to [BottomSheetDesign.BorderWidth].
 */
fun Modifier.sheetContour(
    borderColor: Color? = null,
    shape: Shape = BottomSheetDesign.SheetShape,
    borderWidth: Dp = BottomSheetDesign.BorderWidth
): Modifier = composed {
    val strokeColor = borderColor ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    this
        .clip(shape)
        .border(
            width = borderWidth,
            color = strokeColor,
            shape = shape
        )
}
