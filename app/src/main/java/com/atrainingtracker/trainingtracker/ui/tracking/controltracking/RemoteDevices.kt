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
import com.atrainingtracker.R
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
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
            .padding(8.dp)
    ) {
        Icon(
            painter = painterResource(id = device.iconRes),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
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
    RemoteDeviceUIData("1", "HRM-123", R.drawable.hr),
    RemoteDeviceUIData("2", "Speed-X", R.drawable.bt_bike_cad),
    RemoteDeviceUIData("3", "Cadence", R.drawable.bt_bike_pwr)
)