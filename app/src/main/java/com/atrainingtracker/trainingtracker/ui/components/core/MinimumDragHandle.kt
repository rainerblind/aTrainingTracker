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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A unified, minimal drag handle for modal and scaffold bottom sheets (REQ-UI-148, REQ-UI-189, REQ-UI-196, ATT-939, ATT-1588, ATT-1644).
 *
 * Provides a standardized, subtle drag pill (32dp x 3dp) with 8dp top and 4dp bottom padding,
 * ensuring clear touch affordance without excessive vertical footprint or commanding visual weight.
 *
 * @param modifier Optional modifier applied to the outer container.
 */
@Composable
fun MinimumDragHandle(modifier: Modifier = Modifier) {
    MinimumDragHandle(
        modifier = modifier,
        width = BottomSheetDesign.DragHandleWidth,
        height = BottomSheetDesign.DragHandleHeight,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        topPadding = 8.dp,
        bottomPadding = 4.dp
    )
}

/**
 * Parameterized overload of [MinimumDragHandle] supporting custom dimensions, color, and padding.
 *
 * @param modifier Optional modifier applied to the outer container.
 * @param width Width of the drag handle pill. Defaults to [BottomSheetDesign.DragHandleWidth] (32dp).
 * @param height Height of the drag handle pill. Defaults to [BottomSheetDesign.DragHandleHeight] (3dp).
 * @param color Color of the drag handle pill. Defaults to `outlineVariant` at 60% opacity.
 * @param topPadding Top padding of the container. Defaults to 8dp.
 * @param bottomPadding Bottom padding of the container. Defaults to 4dp.
 */
@Composable
fun MinimumDragHandle(
    modifier: Modifier = Modifier,
    width: Dp = BottomSheetDesign.DragHandleWidth,
    height: Dp = BottomSheetDesign.DragHandleHeight,
    color: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    topPadding: Dp = 8.dp,
    bottomPadding: Dp = 4.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = bottomPadding),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(width = width, height = height),
            color = color,
            shape = CircleShape
        ) {
            Box(Modifier.matchParentSize())
        }
    }
}

