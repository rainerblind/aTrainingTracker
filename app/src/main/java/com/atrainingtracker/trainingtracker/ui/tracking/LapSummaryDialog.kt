package com.atrainingtracker.trainingtracker.ui.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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

    // We use a general Dialog composable for complete customisation
    Dialog(onDismissRequest = onDismissRequest) {
        // Surface provides the dialog's background, shape, and shadow
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Custom Title with Background Color
                Text(
                    text = stringResource(R.string.Lap_NR, lapNr),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(16.dp)
                )

                // Container for the data rows
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LapDataRow(
                        hint = stringResource(R.string.time_lap),
                        value = lapTime ?: "--",
                        unit = stringResource(MyHelper.getUnitsId(SensorType.TIME_LAP))
                    )
                    LapDataRow(
                        hint = stringResource(R.string.distance),
                        value = lapDistance ?: "--",
                        unit = stringResource(MyHelper.getUnitsId(SensorType.DISTANCE_m_LAP))
                    )
                    LapDataRow(
                        hint = stringResource(R.string.speed_short),
                        value = lapSpeed ?: "--",
                        unit = stringResource(MyHelper.getUnitsId(SensorType.SPEED_mps))
                    )
                }
            }
        }
    }
}

/**
 * A helper composable to display a single row of lap data with a hint, value, and unit.
 */
@Composable
private fun LapDataRow(hint: String, value: String, unit: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = "$hint:",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.value_unit_string_string, value, unit),
            style = MaterialTheme.typography.headlineLarge
        )
    }
}


// We cannot call the main composable directly because of the resource dependencies.
// Instead, we build a similar-looking composable for the preview.
@Preview(showBackground = true)
@Composable
fun LapSummaryDialogPreview() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Lap 3", // Hardcoded string for preview
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(16.dp)
                )
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Use hardcoded strings for hints and units
                    LapDataRow(hint = "Lap Time", value = "5:12", unit = "/km")
                    LapDataRow(hint = "Distance", value = "1001", unit = "m")
                    LapDataRow(hint = "Speed", value = "3.21", unit = "m/s")
                }
            }
        }
    }
}

// The dark preview will also work now with these changes.
@Preview(showBackground = true)
@Composable
fun LapSummaryDialogDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Lap 3",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(16.dp)
                )
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LapDataRow(hint = "Lap Time", value = "5:12", unit = "/km")
                    LapDataRow(hint = "Distance", value = "1001", unit = "m")
                    LapDataRow(hint = "Speed", value = "3.21", unit = "m/s")
                }
            }
        }
    }
}