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

import androidx.compose.animation.core.copy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
 * Ported directly from TrackingTabsFragmentClassic.kt
 */
@Composable
fun TabConfigContent(
    viewInfo: TrackingViewInfo,
    // Callbacks to ViewModel
    onUpdateTabName: (Long, String) -> Unit,
    onAddTabRelative: (Long, Boolean) -> Unit,
    onDeleteTab: (Long) -> Unit,
    onUpdateShowMap: (Long, Boolean) -> Unit,
    onUpdateShowLiveSegments: (Long, Boolean) -> Unit,
    onUpdateShowLapButton: (Long, Boolean) -> Unit
) {
    // Local state for the text field to ensure smooth typing
    var localName by remember(viewInfo.tabViewId) { mutableStateOf(viewInfo.name) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(8.dp)
    ) {
        // Row 1: Tab Name Input
        OutlinedTextField(
            value = localName,
            onValueChange = {
                localName = it
                onUpdateTabName(viewInfo.tabViewId, it)
            },
            label = { Text(stringResource(R.string.tab_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(4.dp))

        // Row 2: Management Buttons (Add Before, Delete, Add After)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Add Before
            IconButton(onClick = { onAddTabRelative(viewInfo.tabViewId, false) }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Before",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Delete in the middle
            IconButton(onClick = { onDeleteTab(viewInfo.tabViewId) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Tab"
                )
            }

            // Add After
            IconButton(onClick = { onAddTabRelative(viewInfo.tabViewId, true) }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add After",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Row 3: Settings (The three Checkboxes)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConfigCheckbox(
                label = stringResource(R.string.showMap),
                checked = viewInfo.showMap,
                onCheckedChange = { onUpdateShowMap(viewInfo.tabViewId, it) }
            )
            ConfigCheckbox(
                label = stringResource(R.string.showLiveSegments),
                checked = viewInfo.showLiveSegments,
                onCheckedChange = { onUpdateShowLiveSegments(viewInfo.tabViewId, it) }
            )
            ConfigCheckbox(
                label = stringResource(R.string.showLapButton),
                checked = viewInfo.showLapButton,
                onCheckedChange = { onUpdateShowLapButton(viewInfo.tabViewId, it) }
            )
        }
    }
}

/**
 * Helper to keep the Checkbox logic clean (matching Classic implementation)
 */
@Composable
private fun ConfigCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}