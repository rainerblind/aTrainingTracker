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
                    val index = state.firstVisibleItemIndex
                    val offset = state.firstVisibleItemScrollOffset
                    val itemSize = firstVisibleItem.size
                    if (itemSize == 0) 0f
                    else {
                        val progress = (index.toFloat() + offset.toFloat() / itemSize) / totalItemsCount.toFloat()
                        progress.coerceIn(0f, 1f)
                    }
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
            .width(32.dp) // Wider touch target for better UX
            .alpha(alpha)
            .onGloballyPositioned { trackHeightPx = it.size.height }
            .pointerInput(state) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val totalItemsCount = state.layoutInfo.totalItemsCount
                        if (trackHeightPx > 0 && totalItemsCount > 0) {
                            val deltaProgress = dragAmount.y / trackHeightPx
                            val newProgress = (scrollProgress + deltaProgress).coerceIn(0f, 1f)
                            val targetIndex = (newProgress * totalItemsCount).toInt().coerceIn(0, totalItemsCount - 1)
                            coroutineScope.launch {
                                state.scrollToItem(targetIndex)
                            }
                        }
                    }
                )
            }
    ) {
        // Track Background
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .background(trackColor)
                .align(Alignment.Center)
        )

        // Draggable Thumb
        Box(
            modifier = Modifier
                .offset {
                    val maxOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
                    IntOffset(0, (scrollProgress * maxOffsetPx).roundToInt().coerceIn(0, maxOffsetPx.roundToInt()))
                }
                .padding(horizontal = 12.dp)
                .height(thumbHeightDp)
                .width(8.dp)
                .clip(CircleShape)
                .background(thumbColor)
                .align(Alignment.TopCenter)
        )
    }
}
