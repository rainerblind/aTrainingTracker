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

package com.atrainingtracker.trainingtracker.ui.settings.dropbox

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.settings.cloud.CloudConnectionHeader
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.ui.theme.safePainterResource

/**
 * Connection header composable for Dropbox cloud synchronization settings.
 *
 * Delegates to shared [CloudConnectionHeader] per Rule 36 and Rule 62 while maintaining
 * backward-compatible signatures and [com.atrainingtracker.trainingtracker.ui.theme.ComposeSafePainterContractTest]
 * resource-safety invariants.
 */
@Composable
fun DropboxConnectionHeader(
    isConnected: Boolean,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    modifier: Modifier = Modifier,
    accountEmail: String? = null
) {
    CloudConnectionHeader(
        isConnected = isConnected,
        statusConnectedText = stringResource(R.string.dropbox_connected_status),
        statusDisconnectedText = stringResource(R.string.dropbox_disconnected_status),
        connectButtonText = stringResource(R.string.dropbox_connect),
        disconnectButtonText = stringResource(R.string.dropbox_disconnect),
        iconPainter = safePainterResource(id = R.drawable.dropbox_logo_blue, fallback = Icons.Default.Cloud),
        onConnectClick = onConnectClick,
        onDisconnectClick = onDisconnectClick,
        modifier = modifier,
        accountEmail = accountEmail,
        connectButtonContainerColor = TTColor.DropboxBlue,
        connectButtonContentColor = Color.White
    )
}
