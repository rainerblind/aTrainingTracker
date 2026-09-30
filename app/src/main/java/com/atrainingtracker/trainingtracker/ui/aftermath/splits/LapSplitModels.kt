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

package com.atrainingtracker.trainingtracker.ui.aftermath.splits

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.atrainingtracker.banalservice.BSportType

/**
 * Immutable representation of a single lap or interval split item for compact chart visualization.
 * (REQ-UI-204 / ATT-1392)
 */
@Immutable
data class LapSplitItem(
    val lapNr: Long,
    val displayName: String,
    val durationSec: Int,
    val distanceMeters: Double,
    val speedMps: Double,
    val formattedPaceOrSpeed: String,
    val relativeRatio: Float,
    val isFastest: Boolean,
    val isSlowest: Boolean,
    val color: Color
)

/**
 * Immutable collection of lap splits and session metadata.
 * (REQ-UI-204 / ATT-1392)
 */
@Immutable
data class LapSplitChartData(
    val splits: List<LapSplitItem>,
    val bSportType: BSportType,
    val fastestLapNr: Long? = null,
    val slowestLapNr: Long? = null
)
