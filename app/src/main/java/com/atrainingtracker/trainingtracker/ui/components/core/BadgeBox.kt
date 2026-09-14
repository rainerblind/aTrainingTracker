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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard badge and indicator column width (26dp) used across tabular rows, extrema summaries,
 * and data cards (REQ-UI-148, ATT-939).
 */
val BADGE_BOX_DEFAULT_WIDTH: Dp = 26.dp

/**
 * Standard container for tabular badges, icons, and indicators ensuring consistent
 * width (26dp) and horizontal alignment between headers and data rows.
 *
 * @param modifier Optional modifier applied to the box.
 * @param width Width of the badge column (defaults to 26dp).
 * @param contentAlignment Alignment of the inner icon/badge (e.g. [Alignment.Center] or [Alignment.BottomStart]).
 * @param content Composable slot for the badge or indicator.
 */
@Composable
fun BadgeBox(
    modifier: Modifier = Modifier,
    width: Dp = BADGE_BOX_DEFAULT_WIDTH,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.width(width),
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * Standard spacer matching the width of [BadgeBox] for header rows where no badge is displayed.
 *
 * @param modifier Optional modifier applied to the spacer.
 * @param width Width of the spacer matching the badge column (defaults to 26dp).
 */
@Composable
fun BadgeSpacer(
    modifier: Modifier = Modifier,
    width: Dp = BADGE_BOX_DEFAULT_WIDTH
) {
    Spacer(modifier = modifier.width(width))
}
