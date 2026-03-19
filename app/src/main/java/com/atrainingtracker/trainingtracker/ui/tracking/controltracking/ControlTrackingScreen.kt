package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrackingMode

@Composable
fun ControlTrackingScreen(viewModel: TrackingViewModel) {
    val trackingMode = TrackingMode.TRACKING  // TODO: get from viewModel
    val searchingFor = "Some Device"  // TODO: get from viewModel
    val devices = listOf(
        RemoteDevice("1", "HRM-123", R.drawable.hr)) // TODO: get real data from the view model
    val activeSensors  = emptySet<SensorType>() // TODO: get from viewModel

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Searching State Area
        SearchArea(
            searchingFor = searchingFor,
            onSearch = { viewModel.onSearchClicked() }
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Available Remote Devices
        RemoteDevicesRow(
            devices = devices,
            onDeviceClick = { device -> viewModel.onDeviceClicked(device) }
        )

        //Icons for the available sensors.
        SensorStatusRow(activeSensors = activeSensors)

        Spacer(modifier = Modifier.height(24.dp))

        // Dynamic Control Buttons (Start/Pause/Stop)
        ControlTrackingButton(
            modifier = Modifier.fillMaxWidth(),
            mode = trackingMode,
            onStart = { viewModel.onStartTracking() },
            onPause = { viewModel.onPauseTracking() },
            onResume = { viewModel.onResumeTracking() },
            onStop = { viewModel.onStopTracking() }
        )


        Spacer(modifier = Modifier.height(16.dp))
        SportTypeSelector(
            currentSport = BSportType.BIKE, // TODO: get from view model / live data
            onSportSelected = { viewModel.setSport(it) }
        )

        Spacer(modifier = Modifier.weight(1f))


        Spacer(modifier = Modifier.height(24.dp))

        // Pairing Buttons
        PairingButtons(
            isAntSupported = viewModel.isAntProperlyInstalled(),
            isBluetoothSupported = viewModel.isBluetoothSupported(),
            onPairingClicked = { protocol -> viewModel.onPairingClicked(protocol) }
        )
    }
}



