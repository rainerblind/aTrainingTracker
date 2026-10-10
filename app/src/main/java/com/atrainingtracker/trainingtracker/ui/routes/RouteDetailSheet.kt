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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.ui.components.core.EntityDetailSheetScaffold
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
 * Dedicated Route Detail Bottom Sheet (REQ-UI-315 / REQ-UI-316 / REQ-UI-333 / ATT-2860 / ATT-3053).
 *
 * Directly hosts [RouteOnMapScreen] inside [EntityDetailSheetScaffold] to ensure
 * 100% visual and functional identity with the map screen layout:
 * 1. Unified header featuring [RouteSummaryHeader].
 * 2. Interactive [com.atrainingtracker.trainingtracker.ui.map.MapDetailLayout] with elevation profile scrubbing and collapsible map viewport.
 * 3. Shared [EntityDetailSheetScaffold] container with standardized floating close button.
 */
@Composable
fun RouteDetailSheet(
    routeWithPath: RouteWithPath,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    EntityDetailSheetScaffold(
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        RouteOnMapScreen(
            route = routeWithPath.toMapRoute(),
            routeSummary = routeWithPath.summary,
            onToggleSelection = {},
            modifier = Modifier.fillMaxSize(),
            useStatusBarsPadding = false
        )
    }
}
