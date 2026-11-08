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

package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewInfo

/**
 * Header for the Tab in Config Mode
 */
@Composable
fun TrackingTabConfigHeader(
    viewInfo: TrackingViewInfo,
    tabIndex: Int = 0,
    tabCount: Int = 1,
    sportName: String = "",
    // Callbacks to ViewModel
    onUpdateTabName: (Long, String) -> Unit,
    onAddTabRelative: (Long, Boolean) -> Unit,
    onDeleteTab: (Long) -> Unit,
    onCopyTab: (Long) -> Unit,
    onMoveTab: (Long, Boolean) -> Unit = { _, _ -> },
    onToggleMode: () -> Unit,
) {
    // Local state for the text field to ensure smooth typing
    var localName by remember(viewInfo.tabViewId) { mutableStateOf(viewInfo.name) }

    Surface(
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column {
            if (sportName.isNotEmpty()) {
                Text(
                    text = sportName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            // Row 1: Tab Name Input & Done Button
            Row(modifier = Modifier) {
                OutlinedTextField(
                    value = localName,
                    onValueChange = {
                        localName = it
                        onUpdateTabName(viewInfo.tabViewId, it)
                    },
                    label = { Text(stringResource(R.string.tab_name)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                // DONE BUTTON
                IconButton(
                    onClick = onToggleMode,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier
                        .padding(4.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.Done),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Row 2: Management Buttons (Move Left, Add Before, Copy Tab, Delete, Add After, Move Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Move Left
                IconButton(
                    onClick = { onMoveTab(viewInfo.tabViewId, true) },
                    enabled = tabIndex > 0
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_move_tab_left),
                        tint = if (tabIndex > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                // Add Before
                IconButton(onClick = { onAddTabRelative(viewInfo.tabViewId, false) }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.cd_add_before),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Copy Tab
                IconButton(onClick = { onCopyTab(viewInfo.tabViewId) }) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.cd_copy_tab),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Delete in the middle
                IconButton(onClick = { onDeleteTab(viewInfo.tabViewId) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cd_delete_tab)
                    )
                }

                // Add After
                IconButton(onClick = { onAddTabRelative(viewInfo.tabViewId, true) }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.cd_add_after),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Move Right
                IconButton(
                    onClick = { onMoveTab(viewInfo.tabViewId, false) },
                    enabled = tabIndex < tabCount - 1
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.cd_move_tab_right),
                        tint = if (tabIndex < tabCount - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        }
    }
}