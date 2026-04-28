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

package com.atrainingtracker.trainingtracker.ui.segments.segmentlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.segments.LiveSegment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentList(
    liveSegments: List<LiveSegment>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSegmentClick: (Long) -> Unit,
    appBarOffsetPx: Int,
    headerHeightPx: Float
) {
    // PullToRefreshBox handles the loading spinner and gesture
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh
    ) {
        val density = LocalDensity.current
        val bottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                // Calculation: The initial header height (px) + the current offset (px)
                // convert the final result to Dp.
                top = with(density) { (headerHeightPx + appBarOffsetPx).toDp() },
                bottom = bottomPadding + 16.dp,
                start = 8.dp,
                end = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                items = liveSegments,
                key = { it.summary.stravaId } // Improves performance and scroll position handling
            ) { liveSegment ->
                SegmentItem(
                    summary = liveSegment.summary,
                    pathPoints = liveSegment.path,
                    onSegmentClick = onSegmentClick
                )
            }
        }
    }
}