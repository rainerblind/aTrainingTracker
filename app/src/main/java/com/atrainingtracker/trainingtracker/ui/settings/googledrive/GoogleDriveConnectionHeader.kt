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

package com.atrainingtracker.trainingtracker.ui.settings.googledrive

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.settings.cloud.CloudConnectionHeader

/**
 * Connection header composable for Google Drive cloud synchronization settings.
 *
 * Delegates to shared [CloudConnectionHeader] per Rule 36 and Rule 62.
 */
@Composable
fun GoogleDriveConnectionHeader(
    isConnected: Boolean,
    accountEmail: String?,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CloudConnectionHeader(
        isConnected = isConnected,
        accountEmail = accountEmail,
        statusConnectedText = stringResource(R.string.google_drive_connected_status),
        statusDisconnectedText = stringResource(R.string.google_drive_disconnected_status),
        connectButtonText = stringResource(R.string.google_drive_connect),
        disconnectButtonText = stringResource(R.string.google_drive_disconnect),
        iconPainter = painterResource(id = R.drawable.ic_google_drive),
        onConnectClick = onConnectClick,
        onDisconnectClick = onDisconnectClick,
        modifier = modifier,
        connectButtonContainerColor = MaterialTheme.colorScheme.primary,
        connectButtonContentColor = MaterialTheme.colorScheme.onPrimary
    )
}
