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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.ui.clusters

import androidx.compose.ui.graphics.Color
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.map.MappablePath
import com.atrainingtracker.trainingtracker.ui.map.MapVisualization
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PatternItem

/**
 * High-contrast color palette used to visually distinguish detected sub-corridors on preview maps (REQ-SET-096.3).
 */
val CLUSTER_VARIANT_PALETTE = TTColor.ClusterVariantPalette

fun getVariantColor(index: Int): Color = TTColor.ClusterVariantPalette[index % TTColor.ClusterVariantPalette.size]

/**
 * Lightweight [MappablePath] implementation used to render color-coded sub-corridor tracks (REQ-SET-096.3).
 */
data class ColoredPolylinePath(
    override val id: Long,
    override val bSportType: BSportType = BSportType.UNKNOWN,
    override val latLngs: List<LatLng>,
    override val color: Color,
    override val width: Float = MapVisualization.TRACK_WIDTH,
    override val zIndex: Float = MapVisualization.TRACK_BASE_Z_INDEX + 5f,
    override val overlayZIndex: Float? = null,
    override val pattern: List<PatternItem>? = null,
    override val onClick: ((Long) -> Unit)? = null,
    override val path: List<PathPoint> = emptyList()
) : MappablePath
