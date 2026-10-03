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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atrainingtracker.trainingtracker.ui.map.toMapSegment
import com.atrainingtracker.trainingtracker.ui.segments.SegmentOnMapScreen

/**
 * Top-level Composable for Starred Segments tabbed list and map view (REQ-UI-159).
 */
@Composable
fun StarredSegmentsScreen(
    viewModel: SegmentListViewModel,
    onConnectToStrava: () -> Unit,
    modifier: Modifier = Modifier
) {
    val segments by viewModel.segmentsWithPath.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val refreshingSports by viewModel.refreshingSports.collectAsStateWithLifecycle()
    val isLocationAvailable by viewModel.isLocationAvailable.collectAsStateWithLifecycle()
    val filterCriteria by viewModel.filterCriteria.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(pageCount = { 2 })
    val bikeListState = rememberLazyListState()
    val runListState = rememberLazyListState()

    // 1. Manage local navigation state
    var selectedSegmentId by rememberSaveable { mutableStateOf<Long?>(null) }

    // 2. Logic to switch between List and Detail
    Box(modifier = modifier) {
        if (selectedSegmentId == null) {
            // SHOW LIST
            SegmentsTabsScreen(
                segmentsWithPath = segments,
                pagerState = pagerState,
                bikeListState = bikeListState,
                runListState = runListState,
                isStravaConnected = viewModel.connectedToStrava,
                onConnectToStrava = onConnectToStrava,
                isRefreshing = { sport -> refreshingSports.contains(sport) },
                onRefresh = { sport -> viewModel.onRefresh(sport) },
                onSegmentClick = { id ->
                    selectedSegmentId = id
                },
                sortOrder = sortOrder,
                scrollToTop = viewModel.shouldScrollToTop(sortOrder),
                onSortOrderChange = { viewModel.setSortOrder(it) },
                isLocationAvailable = isLocationAvailable,
                filterCriteria = filterCriteria,
                onFilterApply = { viewModel.setFilterCriteria(it) },
                onFilterClear = { viewModel.clearFilterCriteria() },
                onFilterUpdate = { transform -> viewModel.updateFilterCriteria(transform) }
            )
        } else {
            // SHOW DETAIL
            val selectedSegment = segments.find { it.summary.stravaId == selectedSegmentId }

            if (selectedSegment != null) {
                val backgroundPaths = remember(selectedSegment, segments, viewModel.routes) {
                    val otherSegments = segments
                        .filter { it.summary.stravaId != selectedSegment.summary.stravaId }
                        .map { it.toMapSegment(showStartAndFinishText = false) }

                    val routes = viewModel.routes.value

                    otherSegments + routes
                }

                SegmentOnMapScreen(
                    segmentSummary = selectedSegment.summary,
                    segment = selectedSegment.toMapSegment(showStartAndFinishText = false),
                    backgroundPaths = backgroundPaths
                )

                // Handle Back Press to return to list
                BackHandler {
                    selectedSegmentId = null
                }
            }
        }
    }
}
