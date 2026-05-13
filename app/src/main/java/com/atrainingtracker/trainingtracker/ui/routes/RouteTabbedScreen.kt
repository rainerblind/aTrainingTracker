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

package com.atrainingtracker.trainingtracker.ui.routes

import androidx.activity.result.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isEmpty
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

@Composable
fun RouteTabbedScreen(viewModel: RoutesViewModel) {
    val routes by viewModel.routes.collectAsState()
    val pagerState = rememberPagerState(pageCount = { viewModel.sports.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = Color(0xFF228B22) // ForestGreen
                )
            }
        ) {
            viewModel.sports.forEachIndexed { index, sport ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(sport.name) },
                    selectedContentColor = Color(0xFF228B22),
                    unselectedContentColor = Color.Gray
                )
            }
        }

        HorizontalPager(state = pagerState) { pageIndex ->
            val currentSport = viewModel.sports[pageIndex]
            val filteredRoutes = routes.filter { it.summary.bSportType == currentSport }

            if (filteredRoutes.isEmpty()) {
                EmptyRoutesPlaceholder(currentSport)
            } else {
                RouteList(
                    routes = filteredRoutes,
                    onToggle = viewModel::toggleRouteSelection,
                    onDelete = viewModel::deleteRoute
                )
            }
        }
    }
}