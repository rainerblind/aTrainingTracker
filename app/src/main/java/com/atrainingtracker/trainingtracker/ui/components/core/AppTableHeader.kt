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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign

/**
 * Standardized container row for tabular column headers.
 * Guarantees [Alignment.Bottom] vertical alignment across all header cells.
 *
 * @param modifier Optional modifier applied to the header row.
 * @param content Composable row scope content.
 */
@Composable
fun AppTableHeader(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        content()
    }
}

/**
 * Standardized cell text for tabular column headers.
 * Applies [appTableHeaderStyle] with [onSurfaceVariant] color and single-line constraints.
 *
 * @param text The header label string.
 * @param modifier Modifier applied to this cell (e.g. [RowScope.weight]).
 * @param color Text color, defaulting to [MaterialTheme.colorScheme.onSurfaceVariant].
 * @param textAlign Optional text alignment (e.g. [TextAlign.End] for numeric columns).
 * @param maxLines Maximum lines allowed (defaults to 1).
 */
@Composable
fun RowScope.AppTableHeaderCell(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign: TextAlign? = null,
    maxLines: Int = 1
) {
    Text(
        text = text,
        style = appTableHeaderStyle(),
        color = color,
        textAlign = textAlign,
        modifier = modifier,
        maxLines = maxLines
    )
}
