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

package com.atrainingtracker.trainingtracker.ui.tracking

import androidx.compose.ui.graphics.Color

/**
 * Immutable UI state model for the horizontal training zone gauge bar (REQ-UI-376, REQ-UI-390).
 *
 * @property segmentColors Ordered list of zone colors (5 for Heart Rate, 7 for Power).
 * @property needleFraction Normalized horizontal position [0.0..1.0] of the needle, or null if telemetry is stale/null.
 * @property activeZoneIndex 1-based index of the currently active zone (e.g. 1..5 for HR, 1..7 for Power), or null.
 * @property segmentWeights Normalized visual width weights for each zone segment, summing to 1.0f.
 */
data class ZoneGaugeState(
    val segmentColors: List<Color>,
    val needleFraction: Float?,
    val activeZoneIndex: Int? = null,
    val segmentWeights: List<Float> = if (segmentColors.isNotEmpty()) {
        List(segmentColors.size) { 1f / segmentColors.size }
    } else {
        emptyList()
    }
)
