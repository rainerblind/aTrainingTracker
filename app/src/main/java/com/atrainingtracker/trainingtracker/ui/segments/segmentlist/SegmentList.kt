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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.atrainingtracker.trainingtracker.segments.SegmentSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentList(
    segments: List<SegmentSummary>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSegmentClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // PullToRefreshBox handles the loading spinner and gesture
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(
                items = segments,
                key = { it.stravaId } // Improves performance and scroll position handling
            ) { summary ->
                SegmentItem(
                    summary = summary,
                    // Note: You need the pathPoints for the map/elevation profile.
                    // Usually, these are stored within the summary or a wrapper.
                    pathPoints = emptyList(), // TODO:  Replace with actual points from data source
                    onSegmentClick = onSegmentClick
                )
            }
        }
    }
}