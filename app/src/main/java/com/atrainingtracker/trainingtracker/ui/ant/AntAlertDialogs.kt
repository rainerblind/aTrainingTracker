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

package com.atrainingtracker.trainingtracker.ui.ant

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.safePainterResource

/**
 * Modern Material 3 alert dialog informing the athlete that native ANT+ hardware is not detected (REQ-UI-323).
 *
 * Presents actionable guidance linking directly to [com.atrainingtracker.banalservice.ui.devices.ant.AntServicesStatusSheet]
 * for USB-OTG and ANT+ USB stick setup instructions.
 *
 * @param onViewStatus Callback invoked when the athlete taps the primary action button to view ANT+ status.
 * @param onDismiss Callback invoked when the athlete dismisses the dialog.
 */
@Composable
fun AntMissingAdapterDialog(
    onViewStatus: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Image(
                painter = safePainterResource(id = R.drawable.ant_logo),
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = stringResource(id = R.string.ant_missing_adapter_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = stringResource(id = R.string.ant_missing_adapter_message),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onViewStatus) {
                Text(text = stringResource(id = R.string.ant_dialog_view_status))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.ant_dialog_dismiss))
            }
        },
        shape = AlertDialogDefaults.shape,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    )
}

/**
 * Modern Material 3 alert dialog informing the athlete that a required ANT+ system dependency is missing (REQ-UI-323).
 *
 * Provides a direct trigger to launch Google Play Store for the missing dependency package.
 *
 * @param dependencyName User-facing name of the missing package.
 * @param onGoToStore Callback invoked to open Google Play Store.
 * @param onDismiss Callback invoked when the athlete cancels or dismisses the dialog.
 */
@Composable
fun AntMissingDependencyDialog(
    dependencyName: String,
    onGoToStore: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Image(
                painter = safePainterResource(id = R.drawable.ant_logo),
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = stringResource(id = R.string.ant_missing_dependency_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = stringResource(id = R.string.ant_missing_dependency_message, dependencyName),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onGoToStore) {
                Text(text = stringResource(id = R.string.go_to_store))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        },
        shape = AlertDialogDefaults.shape,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    )
}
