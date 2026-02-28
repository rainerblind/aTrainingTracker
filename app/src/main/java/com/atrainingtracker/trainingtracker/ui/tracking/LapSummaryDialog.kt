package com.atrainingtracker.trainingtracker.ui.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import kotlinx.coroutines.delay

private const val SHOW_LAP_SUMMARY_TIME_MS = 3000L

/**
 * A Compose-based dialog that shows a summary of the lap data and disappears after a few seconds.
 *
 * @param lapNr The number of the lap.
 * @param lapTime The formatted time for the lap.
 * @param lapDistance The formatted distance for the lap.
 * @param lapSpeed The formatted speed for the lap.
 * @param onDismissRequest Callback invoked when the dialog is dismissed (either automatically or by the user).
 */
@Composable
fun LapSummaryDialog(
    lapNr: Int,
    lapTime: String?,
    lapDistance: String?,
    lapSpeed: String?,
    onDismissRequest: () -> Unit
) {
    // This effect starts a timer when the dialog enters the composition.
    // If the dialog is dismissed early, the coroutine is cancelled automatically.
    LaunchedEffect(Unit) {
        delay(SHOW_LAP_SUMMARY_TIME_MS)
        onDismissRequest()
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        // The title of the dialog.
        title = {
            Text(
                text = stringResource(R.string.Lap_NR, lapNr),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        // The main content of the dialog.
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Lap Time
                Text(
                    text = stringResource(
                        R.string.value_unit_string_string,
                        lapTime ?: "--",
                        stringResource(MyHelper.getUnitsId(SensorType.TIME_LAP))
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
                // Lap Distance
                Text(
                    text = stringResource(
                        R.string.value_unit_string_string,
                        lapDistance ?: "--",
                        stringResource(MyHelper.getUnitsId(SensorType.DISTANCE_m_LAP))
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
                // Lap Speed
                Text(
                    text = stringResource(
                        R.string.value_unit_string_string,
                        lapSpeed ?: "--",
                        stringResource(MyHelper.getUnitsId(SensorType.SPEED_mps))
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        },
        // We don't need buttons for this dialog.
        confirmButton = {},
        dismissButton = {}
    )
}

@Preview(showBackground = true)
@Composable
fun LapSummaryDialogPreview() {
    ATrainingTrackerTheme {
        LapSummaryDialog(
            lapNr = 3,
            lapTime = "5:12",
            lapDistance = "1001",
            lapSpeed = "3.21",
            onDismissRequest = {}
        )
    }
}