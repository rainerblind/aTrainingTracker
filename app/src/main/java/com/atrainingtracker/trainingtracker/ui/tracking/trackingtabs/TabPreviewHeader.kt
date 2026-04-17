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

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewInfo

@Composable
fun TabPreviewHeader(
    viewInfo: TrackingViewInfo?,
    screenMode: ScreenMode,
    onToggleMode: () -> Unit
) {
    // We use a Surface to give it a distinct elevation/background from the pager
    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (screenMode == ScreenMode.CONFIGURATION) {
                // CONFIGURATION MODE UI
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.configure),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    // If viewInfo is null (Control Tab), we might just show a static label
                    if (viewInfo != null) {
                        Text(
                            text = viewInfo.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "Control Tracking", // stringResource(R.string.control),  // Should never be shown.
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                // DONE BUTTON
                IconButton(
                    onClick = onToggleMode,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.Done),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            } else {
                // VIEW MODE UI
                Text(
                    text = viewInfo?.name ?: stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // EDIT BUTTON
                IconButton(onClick = onToggleMode) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.Edit),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}