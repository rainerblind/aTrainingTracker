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
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.repositories.DeviceTelemetry
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue


@Composable
fun SensorStatus(
    activeSensors: Set<SensorType>,
    sourceMapping: Map<SensorType, Long> = emptyMap(),
    allTelemetry: List<DeviceTelemetry> = emptyList(),
    allDevices: List<DeviceUiData> = emptyList(),
    modifier: Modifier = Modifier
) {
    var selectedSensor by remember { mutableStateOf<SensorType?>(null) }

    // Fixed order definition
    val sensorDefinitions = remember {
        listOf(
            SensorType.TIME_ACTIVE,
            SensorType.LONGITUDE,
            SensorType.ALTITUDE,
            SensorType.DISTANCE_m,
            SensorType.SPEED_mps,
            SensorType.CADENCE,
            SensorType.HR,
            SensorType.POWER
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {

        sensorDefinitions.forEach { type ->
            val isAvailable = activeSensors.contains(type)

            Icon(
                painter = painterResource(id = type.iconResId),
                contentDescription = type.name,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(24.dp)
                    .alpha(if (isAvailable) 1f else 0.15f)
                    .clickable {
                        selectedSensor = type
                    },
                tint = if (isAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
            )
        }
    }

    // Source Dialog
    selectedSensor?.let { sensor ->
        val sourceId = sourceMapping[sensor] ?: -1
        val sourceDevice = allDevices.find { it.id == sourceId }

        SensorSourceDialog(
            sensorType = sensor,
            sourceDevice = sourceDevice,
            allTelemetry = allTelemetry,
            allDevices = allDevices,
            onDismiss = { selectedSensor = null }
        )
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