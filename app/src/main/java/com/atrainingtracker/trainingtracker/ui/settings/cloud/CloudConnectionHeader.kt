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

package com.atrainingtracker.trainingtracker.ui.settings.cloud

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.trainingtracker.ui.theme.TTColor

/**
 * Shared connection header card for cloud storage providers (Dropbox, Google Drive).
 *
 * Displays connection state, optional account email, and responsive Connect / Disconnect actions.
 * Adheres to Rule 36 (DRY) and Rule 62 (Multi-Provider Cloud UI Symmetry).
 */
@Composable
fun CloudConnectionHeader(
    isConnected: Boolean,
    statusConnectedText: String,
    statusDisconnectedText: String,
    connectButtonText: String,
    disconnectButtonText: String,
    iconPainter: Painter,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    modifier: Modifier = Modifier,
    accountEmail: String? = null,
    connectButtonContainerColor: Color = MaterialTheme.colorScheme.primary,
    connectButtonContentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = TTAlpha.Subtle),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isConnected) statusConnectedText else statusDisconnectedText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isConnected) TTColor.ConnectionConnected else MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            if (isConnected && !accountEmail.isNullOrBlank()) {
                Text(
                    text = accountEmail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            if (!isConnected) {
                Button(
                    onClick = onConnectClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = connectButtonContainerColor,
                        contentColor = connectButtonContentColor
                    ),
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = iconPainter,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(text = connectButtonText)
                    }
                }
            } else {
                Button(
                    onClick = onDisconnectClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text(text = disconnectButtonText)
                }
            }
        }
    }
}
