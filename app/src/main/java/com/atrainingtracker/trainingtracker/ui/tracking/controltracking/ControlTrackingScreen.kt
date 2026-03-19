package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import android.content.res.Configuration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.Protocol
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
        // 1. Searching Area
        SearchArea(
            searchingFor = searchingFor,
            bSportType = currentSport,
            onSearch = onSearch
        )

        Spacer(modifier = Modifier.height(2.dp))

        // 2. Available Remote Devices Row
        RemoteDevicesRow(
            devices = devices,
            onDeviceClick = onDeviceClick
        )

        // 3. Sensor Status Row (Fixed order, dims if inactive)
        SensorStatusRow(activeSensors = activeSensors)


        // Pushes the main control buttons to the bottom
        Spacer(modifier = Modifier.weight(1f))

        // 5. Large Control Buttons (Start/Pause/Stop)
        ControlTrackingButton(
            modifier = Modifier.fillMaxWidth(),
            mode = trackingMode,
            onStart = onStart,
            onPause = onPause,
            onResume = onResume,
            onStop = onStop
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Sport Selection
        SportTypeSelector(
            currentSport = currentSport,
            onSportSelected = onSportSelected
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 6. Hardware Pairing Buttons
        PairingButtons(
            isAntSupported = isAntSupported,
            isBluetoothSupported = isBluetoothSupported,
            onPairingClicked = onPairingClicked
        )
    }
}

// --- Previews ---

@Preview(showBackground = true, name = "Light Mode - IDLE")
@Preview(
    showBackground = true,
    name = "Dark Mode - IDLE",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewControlTrackingScreen() {
    ATrainingTrackerTheme {
        Surface {
            ControlTrackingScreen(
                trackingMode = TrackingMode.READY,
                searchingFor = null,
                devices = listOf(RemoteDeviceUIData("1", "Polar H10", R.drawable.hr)),
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



