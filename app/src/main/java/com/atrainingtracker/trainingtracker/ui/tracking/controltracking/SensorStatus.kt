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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.Sensors
import com.atrainingtracker.trainingtracker.ui.theme.safePainterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.repositories.DeviceTelemetry
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import androidx.compose.foundation.clickable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.atrainingtracker.trainingtracker.monetization.ProFeatureManager
import com.atrainingtracker.trainingtracker.monetization.SupporterTier
import com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.LocationCalibrationStatus


@Composable
fun SensorStatus(
    activeSensors: Set<SensorType>,
    sourceMapping: Map<SensorType, Long> = emptyMap(),
    allTelemetry: List<DeviceTelemetry> = emptyList(),
    allDevices: List<DeviceUiData> = emptyList(),
    currentAltitude: Double? = null,
    matchedLocation: LocationCalibrationStatus? = null,
    onDeviceClick: (Long) -> Unit = {},
    onCalibrateAltitude: ((referenceAltitudeMeters: Double, locationName: String?, saveAsKnownLocation: Boolean) -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    onSupporterClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isCompactLandscape: Boolean = false
) {
    var selectedSensor by remember { mutableStateOf<SensorType?>(null) }

    val context = LocalContext.current
    val proFeatureManager = remember(context) { ProFeatureManager.getInstance(context.applicationContext) }
    val effectiveTier by proFeatureManager.effectiveTier.collectAsState()

    val supporterEmblemRes = when (effectiveTier) {
        SupporterTier.PIONEER -> R.drawable.ic_supporter_emblem_pioneer
        SupporterTier.BRONZE -> R.drawable.ic_supporter_emblem_bronze
        SupporterTier.SILVER -> R.drawable.ic_supporter_emblem_silver
        SupporterTier.GOLD -> R.drawable.ic_supporter_emblem_gold
        SupporterTier.DIAMOND -> R.drawable.ic_supporter_emblem_diamond
        SupporterTier.FREE -> null
    }

    // Fixed order definition
    val sensorDefinitions = remember {
        listOf(
            SensorType.TIME_ACTIVE,
            SensorType.ACCURACY,  // i.e., position
            SensorType.ALTITUDE,
            SensorType.DISTANCE_m,
            SensorType.SPEED_mps,
            SensorType.CADENCE,
            SensorType.HR,
            SensorType.POWER,
            SensorType.RADAR_THREAT_COUNT
        )
    }

    if (isCompactLandscape) {
        // Landscape Mode: compact horizontal status bar row centering icons and menu button (REQ-UI-434.1 / ATT-4773)
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 28.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (onMenuClick != null) {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            sensorDefinitions.forEach { type ->
                val isAvailable = activeSensors.contains(type)
                val iconAlpha = if (isAvailable) 1f else INACTIVE_SENSOR_ALPHA

                Icon(
                    painter = safePainterResource(id = type.iconResId, fallback = Icons.Default.Sensors),
                    contentDescription = type.name,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(18.dp)
                        .alpha(iconAlpha)
                        .clickable {
                            selectedSensor = type
                        },
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            if (supporterEmblemRes != null) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = { onSupporterClick?.invoke() },
                    enabled = onSupporterClick != null,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painter = safePainterResource(id = supporterEmblemRes, fallback = Icons.Default.Sensors),
                        contentDescription = stringResource(R.string.supporter_status_emblem_cd),
                        tint = Color.Unspecified,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else if (onMenuClick != null) {
                Spacer(modifier = Modifier.width(30.dp))
            }
        }
    } else {
        // Portrait Mode: preserved multi-row / full-width row with left-aligned menu button and centered sensor icons
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SENSOR_STATUS_PORTRAIT_HORIZONTAL_PADDING,
                    vertical = SENSOR_STATUS_PORTRAIT_VERTICAL_PADDING
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onMenuClick != null) {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(SENSOR_STATUS_ACTION_BUTTON_SIZE)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(SENSOR_STATUS_ACTION_ICON_SIZE)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(SENSOR_STATUS_ACTION_BUTTON_SIZE))
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    sensorDefinitions.forEach { type ->
                        val isAvailable = activeSensors.contains(type)
                        val iconAlpha = if (isAvailable) 1f else INACTIVE_SENSOR_ALPHA

                        Icon(
                            painter = safePainterResource(id = type.iconResId, fallback = Icons.Default.Sensors),
                            contentDescription = type.name,
                            modifier = Modifier
                                .padding(horizontal = SENSOR_STATUS_ICON_HORIZONTAL_PADDING)
                                .size(SENSOR_STATUS_ICON_SIZE)
                                .alpha(iconAlpha)
                                .clickable {
                                    selectedSensor = type
                                },
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (supporterEmblemRes != null) {
                IconButton(
                    onClick = { onSupporterClick?.invoke() },
                    enabled = onSupporterClick != null,
                    modifier = Modifier.size(SENSOR_STATUS_ACTION_BUTTON_SIZE)
                ) {
                    Icon(
                        painter = safePainterResource(id = supporterEmblemRes, fallback = Icons.Default.Sensors),
                        contentDescription = stringResource(R.string.supporter_status_emblem_cd),
                        tint = Color.Unspecified,
                        modifier = Modifier.size(SENSOR_STATUS_ACTION_ICON_SIZE)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(SENSOR_STATUS_ACTION_BUTTON_SIZE))
            }
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
            currentAltitude = currentAltitude,
            matchedLocation = matchedLocation,
            onDeviceClick = onDeviceClick,
            onCalibrateAltitude = onCalibrateAltitude,
            onDismiss = { selectedSensor = null }
        )
    }
}

// Inactive sensor content alpha adhering to Material Design 3 disabled content standards (REQ-UI-173)
internal const val INACTIVE_SENSOR_ALPHA = 0.38f

// Option A: 16 dp total display offset for cockpit top action icons (REQ-UI-451 / ATT-5422).
// Outer Surface in TrackingTabsScreen provides 4.dp, outer Row provides 8.dp horizontal padding,
// and 32.dp IconButton provides 4.dp internal icon inset for the 24.dp icon (4 + 8 + 4 = 16.dp).
internal val SENSOR_STATUS_PORTRAIT_HORIZONTAL_PADDING = 8.dp
internal val SENSOR_STATUS_PORTRAIT_VERTICAL_PADDING = 4.dp
internal val SENSOR_STATUS_ACTION_BUTTON_SIZE = 32.dp
internal val SENSOR_STATUS_ACTION_ICON_SIZE = 24.dp
internal val SENSOR_STATUS_ICON_SIZE = 22.dp
internal val SENSOR_STATUS_ICON_HORIZONTAL_PADDING = 4.dp

// --- Previews ---

@Preview(showBackground = true, name = "Light Mode")
@Composable
private fun PreviewSensorStatusRow() {
    ATrainingTrackerTheme {
        Surface {
            SensorStatus(activeSensors = setOf(SensorType.TIME_ACTIVE, SensorType.HR))
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
private fun PreviewSensorStatusRowDark() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface {
            SensorStatus(activeSensors = setOf(SensorType.TIME_ACTIVE, SensorType.DISTANCE_m))
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "AMOLED Dark Mode")
@Composable
private fun PreviewSensorStatusRowAmoled() {
    ATrainingTrackerTheme(darkTheme = true, amoled = true) {
        Surface {
            SensorStatus(activeSensors = setOf(SensorType.TIME_ACTIVE, SensorType.ACCURACY, SensorType.ALTITUDE))
        }
    }
}