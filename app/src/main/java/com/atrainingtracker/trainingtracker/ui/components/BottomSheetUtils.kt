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

package com.atrainingtracker.trainingtracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.atrainingtracker.trainingtracker.ui.components.core.MinimumDragHandle as CoreMinimumDragHandle

/**
 * Backward-compatible forwarder for [MinimumDragHandle].
 * The authoritative implementation is housed in [com.atrainingtracker.trainingtracker.ui.components.core.MinimumDragHandle] (REQ-UI-148, ATT-939).
 */
@Deprecated(
    message = "Moved to com.atrainingtracker.trainingtracker.ui.components.core.MinimumDragHandle",
    replaceWith = ReplaceWith("MinimumDragHandle(modifier)", "com.atrainingtracker.trainingtracker.ui.components.core.MinimumDragHandle")
)
@Composable
fun MinimumDragHandle(modifier: Modifier = Modifier) {
    CoreMinimumDragHandle(modifier = modifier)
}
