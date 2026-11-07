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

package com.atrainingtracker.trainingtracker.ui.aftermath

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.atrainingtracker.R

/**
 * State holder managing vertical drag-to-reorder gesture translation and real-time position swapping
 * (REQ-UI-456.2 / REQ-UI-456.3 / ATT-5761).
 */
@Stable
class ReorderDragState(
    val thresholdPx: Float,
    val minIndex: Int = 0,
    val maxIndex: Int = Int.MAX_VALUE,
    val onMoveItem: (fromIndex: Int, toIndex: Int) -> Unit
) {
    var draggingIndex by mutableStateOf<Int?>(null)
        private set

    var dragOffsetPx by mutableFloatStateOf(0f)
        private set

    fun onDragStart(index: Int) {
        draggingIndex = index
        dragOffsetPx = 0f
    }

    fun onVerticalDrag(deltaY: Float) {
        val currentIndex = draggingIndex ?: return
        dragOffsetPx += deltaY
        if (dragOffsetPx >= thresholdPx && currentIndex < maxIndex) {
            onMoveItem(currentIndex, currentIndex + 1)
            draggingIndex = currentIndex + 1
            dragOffsetPx -= thresholdPx
        } else if (dragOffsetPx <= -thresholdPx && currentIndex > minIndex) {
            onMoveItem(currentIndex, currentIndex - 1)
            draggingIndex = currentIndex - 1
            dragOffsetPx += thresholdPx
        } else if (currentIndex >= maxIndex && dragOffsetPx > thresholdPx) {
            dragOffsetPx = thresholdPx
        } else if (currentIndex <= minIndex && dragOffsetPx < -thresholdPx) {
            dragOffsetPx = -thresholdPx
        }
    }

    fun onDragEnd() {
        draggingIndex = null
        dragOffsetPx = 0f
    }
}

/**
 * Remembers a [ReorderDragState] instance configured for the active item count and boundary constraints.
 */
@Composable
fun rememberReorderDragState(
    itemCount: Int,
    rowHeight: Dp = 44.dp,
    minIndex: Int = 0,
    onMoveItem: (fromIndex: Int, toIndex: Int) -> Unit
): ReorderDragState {
    val density = LocalDensity.current
    val thresholdPx = remember(density, rowHeight) { with(density) { rowHeight.toPx() } }
    val currentOnMoveItem by rememberUpdatedState(onMoveItem)
    val maxIndex = (itemCount - 1).coerceAtLeast(0)

    return remember(thresholdPx, minIndex, maxIndex) {
        ReorderDragState(
            thresholdPx = thresholdPx,
            minIndex = minIndex,
            maxIndex = maxIndex,
            onMoveItem = { from, to -> currentOnMoveItem(from, to) }
        )
    }
}

/**
 * Reusable drag handle touch target icon for section reordering (REQ-UI-456.1 / REQ-UI-456.2).
 * Spans at least 44dp touch target width and full row height, isolating vertical drag detection
 * strictly to this area while allowing the rest of the row to scroll the parent container smoothly.
 */
@Composable
fun ReorderDragHandle(
    index: Int,
    dragState: ReorderDragState,
    modifier: Modifier = Modifier,
    isPinned: Boolean = false,
    pinnedContentDescription: String? = null,
    dragContentDescription: String? = null
) {
    val isDragging = dragState.draggingIndex == index
    val defaultDragDesc = stringResource(R.string.action_drag_to_reorder)
    val defaultPinnedDesc = stringResource(R.string.workout_detail_tabs_map_pinned_desc)

    val currentDragState by rememberUpdatedState(dragState)
    val currentIndex by rememberUpdatedState(index)

    Box(
        modifier = modifier
            .size(44.dp)
            .then(
                if (!isPinned) {
                    Modifier.pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                currentDragState.onDragStart(currentIndex)
                            },
                            onDragEnd = {
                                currentDragState.onDragEnd()
                            },
                            onDragCancel = {
                                currentDragState.onDragEnd()
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                currentDragState.onVerticalDrag(dragAmount)
                            }
                        )
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPinned) Icons.Default.PushPin else Icons.Default.DragHandle,
            contentDescription = if (isPinned) {
                pinnedContentDescription ?: defaultPinnedDesc
            } else {
                dragContentDescription ?: defaultDragDesc
            },
            tint = if (isPinned) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            } else if (isDragging) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            },
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Modifier extension applying visual elevation, z-index elevation, and translation offset
 * when a row is actively being dragged (REQ-UI-456.3).
 */
fun Modifier.reorderableRowElevation(
    index: Int,
    dragState: ReorderDragState
): Modifier {
    val isDragging = dragState.draggingIndex == index
    return if (isDragging) {
        this
            .zIndex(1f)
            .graphicsLayer {
                translationY = dragState.dragOffsetPx
            }
            .shadow(4.dp, shape = RoundedCornerShape(8.dp))
    } else {
        this.zIndex(0f)
    }
}
