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

package com.atrainingtracker.trainingtracker.ui.common.filters

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.atrainingtracker.R

/**
 * Standardized header action button for opening multi-dimensional filter sheets.
 *
 * Displays the Material funnel icon ([Icons.Default.FilterAlt]). When filter criteria are active
 * ([isFilterActive] is true and [activeFilterCount] > 0), renders a [BadgedBox] displaying the numeric
 * count of active filter dimensions.
 *
 * @param onClick Callback invoked when the button is clicked.
 * @param isFilterActive Whether at least one filter criterion is active.
 * @param activeFilterCount The number of active filter dimensions.
 * @param modifier Optional modifier applied to the [IconButton].
 * @param tint Icon tint color, defaulting to `MaterialTheme.colorScheme.onPrimaryContainer`.
 */
@Composable
fun FilterActionButton(
    onClick: () -> Unit,
    isFilterActive: Boolean,
    activeFilterCount: Int,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        if (isFilterActive && activeFilterCount > 0) {
            BadgedBox(
                badge = {
                    Badge {
                        Text(activeFilterCount.toString())
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.FilterAlt,
                    contentDescription = stringResource(R.string.filter_action),
                    tint = tint
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.FilterAlt,
                contentDescription = stringResource(R.string.filter_action),
                tint = tint
            )
        }
    }
}
