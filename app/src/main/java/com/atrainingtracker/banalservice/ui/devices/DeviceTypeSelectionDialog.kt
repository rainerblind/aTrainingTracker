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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.helpers.UIHelper
import com.atrainingtracker.banalservice.ui.devices.devicedata.getIconId

@Composable
fun DeviceTypeSelectionDialog(
    protocol: Protocol,
    onSelected: (DeviceType) -> Unit,
    onDismiss: () -> Unit
) {
    val deviceTypeList = remember(protocol) { DeviceType.getRemoteDeviceTypes(protocol).toList() }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_device_type)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding() // Ensure items don't hide under nav bar if the list is long
            ) {
                deviceTypeList.forEach { type ->
                    ListItem(
                        headlineContent = { 
                            Text(
                                text = stringResource(UIHelper.getNameId(type)),
                                style = MaterialTheme.typography.bodyLarge
                            ) 
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(id = getIconId(type, protocol)),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(if (protocol == Protocol.ANT_PLUS) 2.dp else 0.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                tint = if (protocol == Protocol.SMARTPHONE) 
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)
                                else 
                                    Color.Unspecified
                            )
                        },
                        modifier = Modifier.clickable { onSelected(type) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelected(DeviceType.ALL) }) {
                Text(stringResource(R.string.devices_all))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
