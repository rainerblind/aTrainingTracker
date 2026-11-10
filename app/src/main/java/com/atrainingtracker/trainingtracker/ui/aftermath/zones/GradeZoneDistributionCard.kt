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

package com.atrainingtracker.trainingtracker.ui.aftermath.zones

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.atrainingtracker.R

/**
 * Compact card displaying a 7-zone Slope / Grade distribution and time-in-zone breakdown,
 * with an interactive toggle to switch between Stacked Bar, Columns, and fine-grained 1% Grade Histogram (REQ-UI-453).
 *
 * Delegates to generic [ZoneDistributionCard].
 *
 * @param showModeSelector Whether to render the bottom 3-way [androidx.compose.material3.SingleChoiceSegmentedButtonRow] mode switcher.
 *   Defaults to `true` (Workout Details); set to `false` in Workout Summary cards to keep the summary compact.
 */
@Composable
fun GradeZoneDistributionCard(
    distribution: ZoneDistributionData,
    modifier: Modifier = Modifier,
    displayMode: ZoneCardDisplayMode? = null,
    onDisplayModeChange: ((ZoneCardDisplayMode) -> Unit)? = null,
    showModeSelector: Boolean = true
) {
    var internalDisplayMode by rememberSaveable { mutableStateOf(ZoneCardDisplayMode.COLUMNS) }
    ZoneDistributionCard(
        distribution = distribution,
        iconRes = R.drawable.ic_grade,
        title = stringResource(R.string.aftermath_grade_zones_title),
        unit = "%",
        modifier = modifier,
        displayMode = displayMode ?: internalDisplayMode,
        onDisplayModeChange = onDisplayModeChange ?: { internalDisplayMode = it },
        showModeSelector = showModeSelector,
        defaultDisplayMode = ZoneCardDisplayMode.COLUMNS
    )
}
