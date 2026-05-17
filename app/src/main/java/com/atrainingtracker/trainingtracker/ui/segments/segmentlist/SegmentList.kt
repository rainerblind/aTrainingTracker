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

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.segments.SegmentWithPath

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentList(
    segmentsWithPath: List<SegmentWithPath>,
    scrollState: LazyListState,
    isStravaConnected: Boolean,
    onConnectToStrava: () -> Unit,
    isRefreshing: Boolean,
    onSegmentClick: (Long) -> Unit,
    appBarOffsetPx: Int,
    headerHeightPx: Float
) {
    val density = LocalDensity.current
    val topPadding = with(density) { (headerHeightPx + appBarOffsetPx).toDp() }

    Box(modifier = Modifier.fillMaxSize()) {
        val bottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

        // Visual feedback: A thin progress bar just below the header when updating
        if (isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = topPadding),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )
        }

        if (!isStravaConnected && segmentsWithPath.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.starred_segments__no_strava_connection))
                    Button(onClick = {
                        Log.i("SegmentList", "onConnectToStrava clicked" )
                        onConnectToStrava() }) {
                        Text(stringResource(R.string.starred_segments__connect_to_strava))
                    }
                }
            }
        }
        else {
            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    // Calculation: The initial header height (px) + the current offset (px)
                    // convert the final result to Dp.
                    top = with(density) { (headerHeightPx + appBarOffsetPx).toDp() + 16.dp },
                    bottom = bottomPadding + 16.dp,
                    start = 4.dp,
                    end = 4.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = segmentsWithPath,
                    key = { it.summary.stravaId } // Improves performance and scroll position handling
                ) { segmentWithPath ->
                    SegmentItem(
                        summary = segmentWithPath.summary,
                        pathPoints = segmentWithPath.path,
                        onSegmentClick = onSegmentClick
                    )
                }
            }
        }
    }
}