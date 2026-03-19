package com.atrainingtracker.trainingtracker.ui.tracking.controltracking


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

@Composable
fun PairingButtons(
    isAntSupported: Boolean,
    isBluetoothSupported: Boolean,
    onPairingClicked: (Protocol) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ANT+ Pairing Item
        PairingItem(
            iconRes = R.drawable.ant_logo,
            labelRes = R.string.protocol_ant_plus,
            isSupported = isAntSupported,
            onClick = { onPairingClicked(Protocol.ANT_PLUS) }
        )

        // Bluetooth Pairing Item
        PairingItem(
            iconRes = R.drawable.logo_protocol_bluetooth,
            labelRes = R.string.protocol_bluetooth,
            isSupported = isBluetoothSupported,
            onClick = { onPairingClicked(Protocol.BLUETOOTH_LE) }
        )
    }
}

@Composable
private fun PairingItem(
    iconRes: Int,
    labelRes: Int,
    isSupported: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            // If not supported, make it semi-transparent (less visible)
            .alpha(if (isSupported) 1f else 0.3f)
            // Only enable clicking if the protocol is supported
            .clickable(enabled = isSupported, onClick = onClick)
            .padding(12.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = Color.Unspecified
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(id = labelRes),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

// --- Previews ---

@Preview(showBackground = true, name = "Both Supported")
@Composable
fun PreviewPairingBoth() {
    ATrainingTrackerTheme {
        Surface {
            PairingButtons(
                isAntSupported = true,
                isBluetoothSupported = true,
                onPairingClicked = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Only Bluetooth")
@Composable
fun PreviewPairingBluetoothOnly() {
    ATrainingTrackerTheme {
        Surface {
            PairingButtons(
                isAntSupported = false,
                isBluetoothSupported = true,
                onPairingClicked = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "None Supported")
@Composable
fun PreviewPairingNone() {
    ATrainingTrackerTheme {
        Surface {
            PairingButtons(
                isAntSupported = false,
                isBluetoothSupported = false,
                onPairingClicked = {}
            )
        }
    }
}

// DARK MODE PREVIEWS
@Preview(
    showBackground = true,
    name = "Dark: Both Supported",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewPairingBothDark() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface {
            PairingButtons(isAntSupported = true, isBluetoothSupported = true, onPairingClicked = {})
        }
    }
}

@Preview(
    showBackground = true,
    name = "Dark: Only Bluetooth",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewPairingBluetoothOnlyDark() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface {
            PairingButtons(isAntSupported = false, isBluetoothSupported = true, onPairingClicked = {})
        }
    }
}

@Preview(
    showBackground = true,
    name = "Dark: None Supported",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewPairingNoneDark() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface {
            PairingButtons(isAntSupported = false, isBluetoothSupported = false, onPairingClicked = {})
        }
    }
}