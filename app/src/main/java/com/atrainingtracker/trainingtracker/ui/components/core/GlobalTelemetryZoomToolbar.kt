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

package com.atrainingtracker.trainingtracker.ui.components.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.map.ElevationProfileZoomMath
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import java.util.Locale

/**
 * Standard dimension constants and design tokens for [GlobalTelemetryZoomToolbar] (REQ-UI-225 / ATT-1876).
 */
object GlobalTelemetryZoomToolbarDefaults {
    /**
     * Standard vertical height allocated to the sticky global zoom toolbar.
     */
    val TOOLBAR_HEIGHT: Dp = 36.dp
}

/**
 * Persistent sticky global zoom toolbar positioned directly between the Map viewport and
 * the scrollable telemetry graph container in [com.atrainingtracker.trainingtracker.ui.map.MapDetailLayout]
 * (REQ-UI-225 / ATT-1876).
 *
 * Decouples horizontal zoom controls from child chart components and ensures zoom, pan, and reset
 * controls remain stationary and accessible at all times regardless of scroll depth.
 *
 * @param zoomScale Current horizontal magnification factor.
 * @param startDist Start distance/time offset of the visible horizontal window.
 * @param totalSpan Total distance or duration span of the inspected workout track.
 * @param onZoomChanged Callback invoked when zoom scale or start distance is adjusted.
 * @param modifier Optional modifier applied to the toolbar surface.
 * @param isPanMode Whether horizontal touch drag pans the visible window (`true`) or scrubs cursor (`false`).
 * @param onPanModeToggle Optional callback invoked to toggle between pan mode and scrub mode.
 */
@Composable
fun GlobalTelemetryZoomToolbar(
    zoomScale: Float,
    startDist: Double,
    totalSpan: Double,
    onZoomChanged: (zoomScale: Float, startDist: Double) -> Unit,
    modifier: Modifier = Modifier,
    isPanMode: Boolean = false,
    onPanModeToggle: (() -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val canZoomIn = zoomScale < ElevationProfileZoomMath.MAX_ZOOM - 0.01f
    val canZoomOut = zoomScale > ElevationProfileZoomMath.MIN_ZOOM + 0.01f
    val isZoomed = zoomScale > 1.01f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(GlobalTelemetryZoomToolbarDefaults.TOOLBAR_HEIGHT),
        color = colorScheme.surfaceVariant.copy(alpha = 0.25f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Zoom Out (-)
            IconButton(
                onClick = {
                    val (newZoom, newStart) = ElevationProfileZoomMath.applyZoomAtCentroid(
                        totalDist = totalSpan,
                        currentZoom = zoomScale,
                        targetZoom = zoomScale / 1.5f,
                        centroidX = 0.5f,
                        canvasWidth = 1.0f,
                        currentStartDist = startDist
                    )
                    onZoomChanged(newZoom, newStart)
                },
                enabled = canZoomOut,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = stringResource(R.string.zoom_out),
                    modifier = Modifier.size(18.dp),
                    tint = if (canZoomOut) colorScheme.onSurfaceVariant else colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Disabled)
                )
            }

            // Zoom In (+)
            IconButton(
                onClick = {
                    val (newZoom, newStart) = ElevationProfileZoomMath.applyZoomAtCentroid(
                        totalDist = totalSpan,
                        currentZoom = zoomScale,
                        targetZoom = zoomScale * 1.5f,
                        centroidX = 0.5f,
                        canvasWidth = 1.0f,
                        currentStartDist = startDist
                    )
                    onZoomChanged(newZoom, newStart)
                },
                enabled = canZoomIn,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.zoom_in),
                    modifier = Modifier.size(18.dp),
                    tint = if (canZoomIn) colorScheme.onSurfaceVariant else colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Disabled)
                )
            }

            // Pan / Scrub Mode Toggle
            if (onPanModeToggle != null) {
                IconButton(
                    onClick = onPanModeToggle,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isPanMode) Icons.Default.PanTool else Icons.Default.TouchApp,
                        contentDescription = stringResource(if (isPanMode) R.string.zoom_pan_mode else R.string.zoom_scrub_mode),
                        modifier = Modifier.size(18.dp),
                        tint = if (isPanMode) colorScheme.primary else colorScheme.onSurfaceVariant
                    )
                }
            }

            // Current Zoom Indicator / Reset Pill
            if (isZoomed) {
                Surface(
                    onClick = {
                        onZoomChanged(1.0f, 0.0)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.primaryContainer,
                    modifier = Modifier.height(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1fx", zoomScale),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onPrimaryContainer
                        )
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = stringResource(R.string.zoom_reset),
                            modifier = Modifier.size(14.dp),
                            tint = colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}
