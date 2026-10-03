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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewInfo
import com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorStatus

/**
 * Header displayed during tracking tab preview mode ([ScreenMode.PREVIEW]).
 * Shows the tab title and provides actions to enter edit mode or exit configuration.
 *
 * @param viewInfo Metadata of the active tracking view tab.
 * @param onToggleMode Callback invoked when the user taps the edit button to switch to [ScreenMode.CONFIGURATION].
 * @param onExitConfig Callback invoked when the user taps the checkmark/done button to exit configuration and return to [ScreenMode.TRACKING].
 */
@Composable
fun TrackingTabPreviewHeader(
    viewInfo: TrackingViewInfo,
    onToggleMode: () -> Unit,
    onExitConfig: () -> Unit = {},
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {

        // -- Simply the name of the current tab and an edit button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = viewInfo.name.ifEmpty { stringResource(R.string.app_name) },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )

            // EDIT BUTTON
            IconButton(onClick = onToggleMode) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.Edit),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // DONE / EXIT BUTTON
            IconButton(onClick = onExitConfig) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.Done),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
