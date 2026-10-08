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

package com.atrainingtracker.banalservice.ui.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet

import androidx.compose.ui.platform.LocalContext
import com.atrainingtracker.banalservice.BANALService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingProtocolBottomSheet(
    onProtocolSelected: (Protocol) -> Unit,
    onDismiss: () -> Unit,
    onAntServicesMissing: (() -> Unit)? = null
) {
    val context = LocalContext.current

    AppModalBottomSheet(
        title = stringResource(R.string.devices_pair_protocol_title),
        onDismissRequest = onDismiss
    ) {
        // Bluetooth LE
        ListItem(
            headlineContent = {
                Text(
                    text = stringResource(R.string.devices_pair_protocol_ble),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            leadingContent = {
                Icon(
                    painter = painterResource(id = Protocol.BLUETOOTH_LE.iconId),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    tint = Color.Unspecified
                )
            },
            modifier = Modifier.clickable { onProtocolSelected(Protocol.BLUETOOTH_LE) }
        )

        // ANT+
        ListItem(
            headlineContent = {
                Text(
                    text = stringResource(R.string.devices_pair_protocol_ant),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            leadingContent = {
                Icon(
                    painter = painterResource(id = Protocol.ANT_PLUS.iconId),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    tint = Color.Unspecified
                )
            },
            modifier = Modifier.clickable {
                if (!BANALService.areAllANTServicesInstalled(context) && onAntServicesMissing != null) {
                    onAntServicesMissing()
                } else {
                    onProtocolSelected(Protocol.ANT_PLUS)
                }
            }
        )
    }
}
