/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A draggable fast-scroll bar for LazyColumn (ATT-303).
 */
@Composable
fun FastScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
) {
    val coroutineScope = rememberCoroutineScope()
    var trackHeightPx by remember { mutableIntStateOf(0) }
    
    // Calculate progress (0.0 to 1.0)
    val totalItems = state.layoutInfo.totalItemsCount
    val visibleItems = state.layoutInfo.visibleItemsInfo.size
    
    // Hide if everything fits on screen or no items
    if (totalItems <= visibleItems || totalItems == 0) return

    val scrollProgress by remember(state) {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            if (totalItemsCount == 0) 0f
            else {
                val firstVisibleItem = layoutInfo.visibleItemsInfo.firstOrNull()
                if (firstVisibleItem == null) 0f
                else {
                    calculateScrollProgress(
                        firstVisibleIndex = state.firstVisibleItemIndex,
                        firstVisibleOffset = state.firstVisibleItemScrollOffset,
                        itemSize = firstVisibleItem.size,
                        totalItems = totalItemsCount
                    )
                }
            }
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (state.isScrollInProgress) 1f else 0.4f,
        animationSpec = tween(durationMillis = 500),
        label = "scrollbar_alpha"
    )

    val density = LocalDensity.current
    val thumbHeightDp = 48.dp
    val thumbHeightPx = with(density) { thumbHeightDp.toPx() }

    // Safety: Hide if track is smaller than thumb
    if (trackHeightPx > 0 && trackHeightPx < thumbHeightPx) return

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(16.dp)
            .alpha(alpha)
            .onGloballyPositioned { trackHeightPx = it.size.height }
    ) {
        // Track Background (Right-aligned, flush against viewport edge)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .background(trackColor)
                .align(Alignment.CenterEnd)
        )

        // Draggable Thumb (Right-aligned, touch target with scoped drag detection)
        Box(
            modifier = Modifier
                .offset {
                    val maxOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
                    IntOffset(0, (scrollProgress * maxOffsetPx).roundToInt().coerceIn(0, maxOffsetPx.roundToInt()))
                }
                .height(thumbHeightDp)
                .width(16.dp)
                .align(Alignment.TopEnd)
                .pointerInput(state) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val totalItemsCount = state.layoutInfo.totalItemsCount
                            if (trackHeightPx > 0 && totalItemsCount > 0) {
                                val deltaProgress = dragAmount.y / trackHeightPx
                                val targetIndex = calculateTargetIndex(scrollProgress, deltaProgress, totalItemsCount)
                                coroutineScope.launch {
                                    state.scrollToItem(targetIndex)
                                }
                            }
                        }
                    )
                }
        ) {
            // Visual Thumb Pill
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(8.dp)
                    .clip(CircleShape)
                    .background(thumbColor)
                    .align(Alignment.CenterEnd)
            )
        }
    }
}

/**
 * Calculates the current scroll progress (0.0 to 1.0) based on the first visible item and total item count.
 */
fun calculateScrollProgress(
    firstVisibleIndex: Int,
    firstVisibleOffset: Int,
    itemSize: Int,
    totalItems: Int
): Float {
    if (totalItems <= 0 || itemSize <= 0) return 0f
    val progress = (firstVisibleIndex.toFloat() + firstVisibleOffset.toFloat() / itemSize) / totalItems.toFloat()
    return progress.coerceIn(0f, 1f)
}

/**
 * Calculates the target item index when dragging the scrollbar by a given progress delta.
 */
fun calculateTargetIndex(
    currentProgress: Float,
    deltaProgress: Float,
    totalItems: Int
): Int {
    if (totalItems <= 0) return 0
    val newProgress = (currentProgress + deltaProgress).coerceIn(0f, 1f)
    return (newProgress * totalItems).toInt().coerceIn(0, totalItems - 1)
}
