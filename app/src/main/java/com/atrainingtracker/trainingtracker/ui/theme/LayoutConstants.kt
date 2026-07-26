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

package com.atrainingtracker.trainingtracker.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Standardized layout dimensions for consistent UI across the application.
 */
object LayoutConstants {
    /**
     * The standard height for the "Inner" part of collapsing headers (Title + Tabs).
     * Replaces previously hardcoded 135dp/130dp total heights.
     */
    val COMPACT_HEADER_CONTENT_HEIGHT = 80.dp

    /**
     * The fixed height for the title row within the header (ATT-351).
     * Strictly constrained to 32dp to fit within the 80dp total content height
     * (32dp Title + 48dp Tabs = 80dp).
     */
    val HEADER_TITLE_ROW_HEIGHT = 32.dp
}
