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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.database.RouteWithPath

@Composable
fun RouteList(
    routes: List<RouteWithPath>,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        items(routes, key = { it.summary.id }) { route ->
            RouteItem(
                summary = route.summary,
                pathPoints = route.path,
                onToggleSelection = { onToggle(route.summary.id, it) },
                // onDelete = { onDelete(route.summary.id) },
                onRouteClick = { },
                modifier = Modifier
            )
        }
    }
}