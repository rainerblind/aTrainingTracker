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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

@Composable
fun ControlTrackingScreen(
    trackingMode: TrackingMode,
    searchingFor: String?,
    devices: List<RemoteDeviceUIData>,
    activeSensors: Set<SensorType>,
    currentSport: BSportType,
    isAntSupported: Boolean,
    isBluetoothSupported: Boolean,
    onSearch: () -> Unit,
    onDeviceClick: (RemoteDeviceUIData) -> Unit,
    onSportSelected: (BSportType) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onPairingClicked: (Protocol) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top part: Sensors
        SensorStatus(activeSensors = activeSensors)

        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
        ) {
            // The Information Area - Anchored to the MATHEMATICAL CENTER of the screen
            Column(
                modifier = Modifier.align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SearchArea(
                    searchingFor = searchingFor
                )

                RemoteDevices(
                    devices = devices,
                    onDeviceClick = onDeviceClick
                )
            }

            // Research Button - Anchored to the far left of the screen
            // note that this must be added at the end to get the clicking working...
            Box(modifier = Modifier.align(Alignment.TopStart)) {
                ResearchButton(
                    isEnabled = searchingFor == null,
                    onClick = onSearch
                )
            }
        }

        // Pushes the main control buttons to the center
        Spacer(modifier = Modifier.weight(1f))

        // Large Control Buttons (Start/Pause/Stop)
        ControlTrackingButton(
            modifier = Modifier.fillMaxWidth(),
            mode = trackingMode,
            onStart = onStart,
            onPause = onPause,
            onResume = onResume,
            onStop = onStop
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Sport Selection
        SportTypeSelector(
            currentSport = currentSport,
            onSportSelected = onSportSelected
        )

        // Pushes the main control buttons to the center
        Spacer(modifier = Modifier.weight(1f))

        // Pairing Buttons
        PairingButtons(
            isAntSupported = isAntSupported,
            isBluetoothSupported = isBluetoothSupported,
            onPairingClicked = onPairingClicked
        )
    }
}

// --- Previews ---

@Preview(showBackground = true, name = "Light Mode - Searching")
@Preview(
    showBackground = true,
    name = "Dark Mode - Searching",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewControlTrackingScreenSearching() {
    ATrainingTrackerTheme {
        Surface {
            ControlTrackingScreen(
                trackingMode = TrackingMode.READY,
                searchingFor = "my spd",
                devices = listOf(RemoteDeviceUIData(1, deviceType = DeviceType.HRM, name = "Polar H10", R.drawable.hr)),
                activeSensors = setOf(SensorType.TIME_ACTIVE, SensorType.HR),
                currentSport = BSportType.RUN,
                isAntSupported = true,
                isBluetoothSupported = true,
                onSearch = {}, onDeviceClick = {}, onSportSelected = {},
                onStart = {}, onPause = {}, onResume = {}, onStop = {}, onPairingClicked = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Light Mode - Not Searching")
@Preview(
    showBackground = true,
    name = "Dark Mode - Not Searching",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewControlTrackingScreen() {
    ATrainingTrackerTheme {
        Surface {
            ControlTrackingScreen(
                trackingMode = TrackingMode.READY,
                searchingFor = null,
                devices = listOf(RemoteDeviceUIData(1, deviceType = DeviceType.HRM, name = "Polar H10", R.drawable.hr)),
                activeSensors = setOf(SensorType.TIME_ACTIVE, SensorType.HR),
                currentSport = BSportType.RUN,
                isAntSupported = true,
                isBluetoothSupported = true,
                onSearch = {}, onDeviceClick = {}, onSportSelected = {},
                onStart = {}, onPause = {}, onResume = {}, onStop = {}, onPairingClicked = {}
            )
        }
    }
}





