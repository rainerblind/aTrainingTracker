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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R

/**
 * Pure mathematical calculations and boundary constants for split pane viewports (REQ-UI-223 / ATT-1890).
 */
object SplitPaneMath {
    const val DEFAULT_SPLIT_FRACTION: Float = 0.50f
    val MIN_MAP_HEIGHT: Dp = 120.dp
    val MIN_LOWER_HEIGHT: Dp = 160.dp
    val DIVIDER_TOUCH_HEIGHT: Dp = 24.dp
    val GRIP_WIDTH: Dp = 32.dp
    val GRIP_HEIGHT: Dp = 4.dp

    fun calculateAvailableHeight(totalHeightPx: Float, dividerHeightPx: Float): Float =
        (totalHeightPx - dividerHeightPx).coerceAtLeast(1f)

    fun calculateMinFraction(minTopHeightPx: Float, availableHeightPx: Float): Float =
        (minTopHeightPx / availableHeightPx).coerceIn(0.05f, 0.95f)

    fun calculateMaxFraction(minBottomHeightPx: Float, availableHeightPx: Float, minFraction: Float): Float =
        (1f - (minBottomHeightPx / availableHeightPx)).coerceIn(minFraction, 0.95f)

    fun updateFraction(
        currentFraction: Float,
        deltaPx: Float,
        availableHeightPx: Float,
        minFraction: Float,
        maxFraction: Float
    ): Float {
        val deltaFraction = deltaPx / availableHeightPx
        return (currentFraction + deltaFraction).coerceIn(minFraction, maxFraction)
    }
}

/**
 * A tactile, draggable horizontal divider composable for adjusting viewports in split layouts (REQ-UI-223 / ATT-1890).
 * Supports vertical dragging to adjust screen real estate and double-tap to reset to the balanced default (50/50 split).
 */
@Composable
fun SplitPaneDivider(
    onDelta: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val description = stringResource(R.string.map_splitter_content_description)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SplitPaneMath.DIVIDER_TOUCH_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onReset() }
                )
            }
            .draggable(
                state = rememberDraggableState { delta -> onDelta(delta) },
                orientation = Orientation.Vertical
            ),
        contentAlignment = Alignment.Center
    ) {
        // Visual pill grip affordance (32.dp x 4.dp)
        Box(
            modifier = Modifier
                .width(SplitPaneMath.GRIP_WIDTH)
                .height(SplitPaneMath.GRIP_HEIGHT)
                .background(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}
