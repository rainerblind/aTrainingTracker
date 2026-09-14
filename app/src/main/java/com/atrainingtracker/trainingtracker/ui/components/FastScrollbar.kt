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

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.ui.components.core.FastScrollableBox as CoreFastScrollableBox
import com.atrainingtracker.trainingtracker.ui.components.core.FastScrollbar as CoreFastScrollbar
import com.atrainingtracker.trainingtracker.ui.components.core.calculateAccumulatedProgress as coreCalculateAccumulatedProgress
import com.atrainingtracker.trainingtracker.ui.components.core.calculateScrollProgress as coreCalculateScrollProgress
import com.atrainingtracker.trainingtracker.ui.components.core.calculateTargetIndex as coreCalculateTargetIndex
import com.atrainingtracker.trainingtracker.ui.components.core.calculateTargetIndexFromProgress as coreCalculateTargetIndexFromProgress

/**
 * Backward-compatible forwarder for [FastScrollableBox].
 * The authoritative implementation is housed in [com.atrainingtracker.trainingtracker.ui.components.core.FastScrollableBox] (REQ-UI-148, ATT-939).
 */
@Composable
fun FastScrollableBox(
    state: LazyListState,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
    content: @Composable BoxScope.() -> Unit
) {
    CoreFastScrollableBox(
        state = state,
        modifier = modifier,
        topPadding = topPadding,
        bottomPadding = bottomPadding,
        thumbColor = thumbColor,
        trackColor = trackColor,
        content = content
    )
}

/**
 * Backward-compatible forwarder for [FastScrollbar].
 * The authoritative implementation is housed in [com.atrainingtracker.trainingtracker.ui.components.core.FastScrollbar] (REQ-UI-148, ATT-939).
 */
@Composable
fun FastScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
) {
    CoreFastScrollbar(
        state = state,
        modifier = modifier,
        thumbColor = thumbColor,
        trackColor = trackColor
    )
}

fun calculateScrollProgress(
    firstVisibleIndex: Int,
    firstVisibleOffset: Int,
    itemSize: Int,
    totalItems: Int
): Float = coreCalculateScrollProgress(firstVisibleIndex, firstVisibleOffset, itemSize, totalItems)

fun calculateAccumulatedProgress(
    currentProgress: Float,
    dragDeltaY: Float,
    trackLengthPx: Float
): Float = coreCalculateAccumulatedProgress(currentProgress, dragDeltaY, trackLengthPx)

fun calculateTargetIndexFromProgress(
    progress: Float,
    totalItems: Int
): Int = coreCalculateTargetIndexFromProgress(progress, totalItems)

fun calculateTargetIndex(
    currentProgress: Float,
    deltaProgress: Float,
    totalItems: Int
): Int = coreCalculateTargetIndex(currentProgress, deltaProgress, totalItems)
