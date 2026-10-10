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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.ui.components.core.BottomSheetDesign
import com.atrainingtracker.trainingtracker.ui.map.toMapRoute
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds

/**
 * Computes tight LatLngBounds enclosing the route path, with zero-area expansion fallback.
 */
fun calculateRouteBounds(route: RouteWithPath): LatLngBounds? {
    val points = route.path.map { it.latLng }
    if (points.isEmpty()) {
        val summary = route.summary
        if (summary.minLat != null && summary.maxLat != null && summary.minLng != null && summary.maxLng != null) {
            return LatLngBounds(
                LatLng(summary.minLat, summary.minLng),
                LatLng(summary.maxLat, summary.maxLng)
            )
        }
        return null
    }
    val builder = LatLngBounds.builder()
    points.forEach { builder.include(it) }
    val bounds = builder.build()
    return if (bounds.northeast.latitude == bounds.southwest.latitude &&
        bounds.northeast.longitude == bounds.southwest.longitude
    ) {
        LatLngBounds(
            LatLng(bounds.southwest.latitude - 0.001, bounds.southwest.longitude - 0.001),
            LatLng(bounds.northeast.latitude + 0.001, bounds.northeast.longitude + 0.001)
        )
    } else {
        bounds
    }
}

/**
 * Dedicated Route Detail Bottom Sheet (REQ-UI-315 / REQ-UI-316 / ATT-2860).
 *
 * Directly hosts [RouteOnMapScreen] inside a modal bottom sheet container to ensure
 * 100% visual and functional identity with the map screen layout:
 * 1. Unified header featuring [RouteSummaryHeader].
 * 2. Interactive [MapDetailLayout] with elevation profile scrubbing and collapsible map viewport.
 * 3. Climbs and segments breakdown sections with overlay dismiss button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailSheet(
    routeWithPath: RouteWithPath,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = BottomSheetDesign.SheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = BottomSheetDesign.SheetTonalElevation,
        dragHandle = null,
        modifier = modifier.fillMaxHeight()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            RouteOnMapScreen(
                route = routeWithPath.toMapRoute(),
                routeSummary = routeWithPath.summary,
                onToggleSelection = {},
                modifier = Modifier.fillMaxSize(),
                useStatusBarsPadding = false
            )

            // Top-right dismiss close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .zIndex(10f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shadowElevation = 2.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.Cancel),
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}
