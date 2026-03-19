package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme


@Composable
fun SensorStatus(
    activeSensors: Set<SensorType>,
    modifier: Modifier = Modifier
) {
    // Fixed order definition
    val sensorDefinitions = listOf(
        SensorType.TIME_ACTIVE to R.drawable.ic_time_active,
        SensorType.LONGITUDE to R.drawable.ic_location,
        SensorType.ALTITUDE to R.drawable.ic_altitude,
        SensorType.DISTANCE_m to R.drawable.ic_distance,
        SensorType.SPEED_mps to R.drawable.ic_speed,
        SensorType.HR to R.drawable.ic_heart_rate,
        SensorType.CADENCE to R.drawable.ic_cadence,
        SensorType.POWER to R.drawable.ic_power
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        sensorDefinitions.forEach { (type, iconRes) ->
            val isAvailable = activeSensors.contains(type)

            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    // If not available, make it much less visible (0.15f) but keep the space
                    .alpha(if (isAvailable) 1f else 0.15f),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (isAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// --- Previews ---

@Preview(showBackground = true, name = "Light Mode")
@Composable
fun PreviewSensorStatusRow() {
    ATrainingTrackerTheme {
        Surface {
            SensorStatus(activeSensors = setOf(SensorType.TIME_ACTIVE, SensorType.HR))
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun PreviewSensorStatusRowDark() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface {
            SensorStatus(activeSensors = setOf(SensorType.TIME_ACTIVE, SensorType.DISTANCE_m))
        }
    }
}