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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking


import android.content.res.Configuration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme


@Composable
fun RemoteDevices(
    devices: List<RemoteDeviceUIData>,
    onDeviceClick: (RemoteDeviceUIData) -> Unit
) {
    // If no devices, don't show the row at all
    if (devices.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            // Center items when there are few, scroll when there are many
            horizontalArrangement = Arrangement.Center,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
        ) {
            items(devices) { device ->
                RemoteDeviceItem(device = device, onClick = { onDeviceClick(device) })
            }
        }
    }
}

@Composable
private fun RemoteDeviceItem(
    device: RemoteDeviceUIData,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Icon(
            painter = painterResource(id = device.iconRes),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp)),
            tint = Color.Unspecified
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = device.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        // TODO: Add Battery State :)
    }
}

// --- Previews ---


@Preview(showBackground = true, name = "Light Mode")
@Composable
fun PreviewRemoteDeviceRow() {
    val mockDevices = getMockDevices()
    // Replace ATrainingTrackerTheme with your actual project theme name
    // Usually located in ui.theme package
    ATrainingTrackerTheme(darkTheme = false) {
        Surface {
            RemoteDevices(devices = mockDevices, onDeviceClick = {})
        }
    }
}

@Preview(
    showBackground = true,
    name = "Dark Mode",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewRemoteDeviceRowDark() {
    val mockDevices = getMockDevices()
    // Explicitly set darkTheme = true
    ATrainingTrackerTheme(darkTheme = true) {
        Surface {
            RemoteDevices(devices = mockDevices, onDeviceClick = {})
        }
    }
}

private fun getMockDevices() = listOf(
    RemoteDeviceUIData(1, deviceType = DeviceType.HRM, name = "HRM-123", R.drawable.hr),
    RemoteDeviceUIData(2, deviceType = DeviceType.BIKE_SPEED, "Speed-X", R.drawable.bt_bike_spd),
    RemoteDeviceUIData(3, deviceType = DeviceType.BIKE_POWER, "Cadence", R.drawable.bt_bike_pwr)
)