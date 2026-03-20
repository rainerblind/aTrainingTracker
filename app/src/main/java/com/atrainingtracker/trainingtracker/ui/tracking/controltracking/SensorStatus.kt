package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme


@Composable
fun SensorStatus(
    activeSensors: Set<SensorType>,
    modifier: Modifier = Modifier
) {
    // Fixed order definition
    val sensorDefinitions = remember {
        listOf(
            SensorType.TIME_ACTIVE to R.drawable.ic_time_active,
            SensorType.LONGITUDE to R.drawable.ic_location,
            SensorType.ALTITUDE to R.drawable.ic_altitude,
            SensorType.DISTANCE_m to R.drawable.ic_distance,
            SensorType.SPEED_mps to R.drawable.ic_speed,
            SensorType.CADENCE to R.drawable.ic_cadence,
            SensorType.HR to R.drawable.ic_heart_rate,
            SensorType.POWER to R.drawable.ic_power
        )
    }
    val context = LocalContext.current // Get the context within the loop or at the top of the composable

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {

        sensorDefinitions.forEach { (type, iconRes) ->
            val isAvailable = activeSensors.contains(type)

            Column(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .alpha(if (isAvailable) 1f else 0.15f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp), // Slightly smaller to accommodate text
                    tint = if (isAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                )

                Text(
                    text = type.getShortName(context),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 1.dp),
                    maxLines = 1,
                    softWrap = false,
                    color = if (isAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
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