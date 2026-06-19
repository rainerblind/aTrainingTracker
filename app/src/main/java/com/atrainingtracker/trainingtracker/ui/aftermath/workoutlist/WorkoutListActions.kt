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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.atrainingtracker.R

/**
 * Reusable actions for workout lists: Toggling between compact/detailed view and sorting.
 */
@Composable
fun WorkoutListActions(
    isCompactView: Boolean,
    onToggleCompactView: () -> Unit,
    sortOrder: WorkoutSortOrder,
    onSortOrderChange: (WorkoutSortOrder) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Toggle View Mode Button
        IconButton(onClick = onToggleCompactView) {
            Icon(
                imageVector = if (isCompactView) Icons.Default.ViewStream else Icons.Default.ViewHeadline,
                contentDescription = "Switch View Mode",
                tint = tint
            )
        }

        // Sort Dropdown
        var showSortMenu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { showSortMenu = true }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = stringResource(R.string.sort),
                    tint = tint
                )
            }
            DropdownMenu(
                containerColor = MaterialTheme.colorScheme.surface,
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false }
            ) {
                WorkoutSortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(order.labelResId))
                        },
                        onClick = {
                            onSortOrderChange(order)
                            showSortMenu = false
                        },
                        leadingIcon = {
                            if (sortOrder == order) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
