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
 */

package com.atrainingtracker.trainingtracker.ui.clusters

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.RouteCluster
import com.atrainingtracker.trainingtracker.ui.components.EmptyStatePlaceholder

/**
 * A reusable list of Route Clusters.
 */
@Composable
fun FrequentPathsList(
    clusters: List<RouteCluster>,
    viewModel: FrequentPathsViewModel,
    onClusterClick: (RouteCluster) -> Unit,
    onDeleteRequest: (RouteCluster) -> Unit,
    scrollState: LazyListState,
    appBarOffsetPx: Int,
    headerHeightDp: Dp,
    density: androidx.compose.ui.unit.Density,
    emptyMessage: String
) {
    val currentAppBarOffsetDp = with(density) { appBarOffsetPx.toDp() }

    if (clusters.isEmpty()) {
        EmptyStatePlaceholder(
            modifier = Modifier.padding(top = headerHeightDp + currentAppBarOffsetDp + 32.dp),
            icon = Icons.Default.LocationOn,
            message = emptyMessage
        )
    } else {
        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = headerHeightDp + currentAppBarOffsetDp + 8.dp,
                bottom = 80.dp, // Space for FAB
                start = 8.dp,
                end = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(clusters) { cluster ->
                ClusterItem(
                    cluster = cluster,
                    viewModel = viewModel,
                    onClick = { onClusterClick(cluster) },
                    onDeleteRequest = onDeleteRequest
                )
            }
        }
    }
}
