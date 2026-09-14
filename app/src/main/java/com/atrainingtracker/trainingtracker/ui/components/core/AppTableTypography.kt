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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle

/**
 * Shared typography helpers and styles for tabular data, column headers, and metrics
 * (REQ-UI-148, ATT-939).
 */
object AppTableTypography {

    /**
     * Standardized typography style for tabular headers based on [MaterialTheme.typography.labelSmall]
     * with bottom baseline alignment and zero font padding.
     */
    @Composable
    fun headerStyle(): TextStyle = appTableHeaderStyle()
}

/**
 * Extension function modifying a [TextStyle] to remove platform font padding
 * and align line height to the bottom, allowing pixel-perfect bottom baseline
 * alignment across adjacent table cells, icons, and micro-units.
 */
fun TextStyle.withBottomBaselineAlignment(): TextStyle {
    return this.copy(
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Bottom,
            trim = LineHeightStyle.Trim.Both
        )
    )
}

/**
 * Returns the standardized typography style for table column headers.
 * Uses [MaterialTheme.typography.labelSmall] with bottom baseline alignment.
 */
@Composable
fun appTableHeaderStyle(): TextStyle {
    return MaterialTheme.typography.labelSmall.withBottomBaselineAlignment()
}
