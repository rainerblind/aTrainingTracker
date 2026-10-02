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

package com.atrainingtracker.trainingtracker.ui.components.workoutlaps

/**
 * Configurable display modes for the workout lap overview section in detailed workout cards (REQ-UI-229 / ATT-1870).
 */
enum class LapDisplayMode {
    /** Only classic numeric table rows and header are displayed. */
    TABLE_ONLY,

    /** Only the high-aesthetic LapSplitVisualizer with proportional pace bars is displayed. */
    VISUALIZER_ONLY,

    /** Both the LapSplitVisualizer and classic table rows are displayed stacked. */
    BOTH
}
