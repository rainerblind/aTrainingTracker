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

package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs

import androidx.activity.compose.BackHandler
import androidx.activity.result.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingScreen
import kotlinx.coroutines.launch

@Composable
fun TrackingTabsScreen(
    viewModel: TrackingTabsViewModel,
    isExplicitMode: Boolean
) {
    val trackingViews by viewModel.trackingViews.collectAsState(initial = emptyList())
    val trackingMode by viewModel.trackingMode.observeAsState(TrackingMode.READY)
    val screenMode by viewModel.screenMode.collectAsState()

    // Page count: Control Tab + Sensor Tabs
    val pageCount = if (isExplicitMode) trackingViews.size else trackingViews.size + 1
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()

    // BACK NAVIGATION HANDLER: when in CONFIGURATION mode, exit to TRACKING mode
    BackHandler(enabled = screenMode == ScreenMode.CONFIGURATION) {
        viewModel.toggleScreenMode() // Exit config mode on back press
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. DYNAMIC HEADER (Config Mode or Tab Title)
        val currentViewInfo = if (isExplicitMode) {
            trackingViews.getOrNull(pagerState.currentPage)
        } else if (pagerState.currentPage > 0) {
            trackingViews.getOrNull(pagerState.currentPage - 1)
        } else null

        Column(modifier = Modifier.fillMaxSize()) {
            // TabConfigHeader includes the Toggle Button for Edit/View
            TabConfigHeader(
                viewInfo = currentViewInfo,
                screenMode = screenMode,
                onToggleMode = { viewModel.toggleScreenMode() }
            )

            // 2. TAB ROW (Only show in VIEW mode)
            if (screenMode == ScreenMode.TRACKING) {
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 16.dp,
                    divider = {}
                ) {
                    if (!isExplicitMode) {
                        Tab(
                            selected = pagerState.currentPage == 0,
                            onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                            text = {
                                // Dynamic Title for Control Tab (Tracking/Paused/Start)
                                Text(getControlTabTitle(trackingMode))
                            }
                        )
                    }
                    trackingViews.forEachIndexed { index, view ->
                        val targetPage = if (isExplicitMode) index else index + 1
                        Tab(
                            selected = pagerState.currentPage == targetPage,
                            onClick = { scope.launch { pagerState.animateScrollToPage(targetPage) } },
                            text = { Text(view.name) }
                        )
                    }
                }
            }

            // 3. THE HORIZONTAL PAGER (Optimized for speed)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = screenMode == ScreenMode.TRACKING,
                beyondViewportPageCount = 1 // KEEP MAPS WARM
            ) { page ->
                if (!isExplicitMode && page == 0) {
                    ControlTrackingScreen() // Dashboard
                } else {
                    val viewIndex = if (isExplicitMode) page else page - 1
                    val viewInfo = trackingViews[viewIndex]
                    // The Grid Content (including the Elevation Profile logic)
                    TrackingTabGridContent(viewInfo.tabViewId, screenMode)
                }
            }
        }

        // 4. THE CONDITIONAL LAP BUTTON
        // Requirement: check currentViewInfo.showLapButton
        val shouldShowLapButton = currentViewInfo?.showLapButton == true
                && screenMode == ScreenMode.TRACKING

        if (shouldShowLapButton) {
            LapButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                trackingMode = trackingMode,
                onClick = { viewModel.onLapButtonClick() }
            )
        }
    }
}

@Composable
fun getControlTabTitle(mode: TrackingMode): String {
    return when (mode) {
        TrackingMode.PAUSED -> stringResource(R.string.Paused)
        TrackingMode.TRACKING -> stringResource(R.string.Tracking)
        else -> stringResource(R.string.tab_start)
    }
}