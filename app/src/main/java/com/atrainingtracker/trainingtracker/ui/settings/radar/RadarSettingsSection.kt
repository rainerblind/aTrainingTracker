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

package com.atrainingtracker.trainingtracker.ui.settings.radar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.radar.RadarScenario
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository
import com.atrainingtracker.banalservice.devices.radar.RadarTrafficSimulator
import com.atrainingtracker.trainingtracker.settings.RadarAudioAlertMode
import com.atrainingtracker.trainingtracker.settings.RadarAudioRoute
import com.atrainingtracker.trainingtracker.settings.RadarClearMode
import com.atrainingtracker.trainingtracker.settings.RadarEdgeSide
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.ui.settings.tuning.SubordinateTuningToggleItem
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Settings and preview composable for cycling radar edge overlay and traffic simulator (REQ-TRK-062.5, REQ-CON-029.4).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadarSettingsSection(
    modifier: Modifier = Modifier,
    tuningDataStore: TuningPreferencesDataStore? = null,
    onSimulationToggled: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val actualDataStore = remember(tuningDataStore, context) {
        tuningDataStore ?: TuningPreferencesDataStore.getInstance(context)
    }
    val tuningConfig by actualDataStore.tuningConfigFlow.collectAsState(initial = actualDataStore.getTuningConfig())
    val coroutineScope = rememberCoroutineScope()

    var selectedScenario by remember { mutableStateOf(RadarScenario.SINGLE_CAR) }
    val radarState by RadarStateRepository.radarState.collectAsState()
    val isRunning = RadarTrafficSimulator.isSimulating

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Overlay Customization Card (REQ-TRK-062.5)
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.radar_overlay_settings_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Edge Side Selection
                Text(
                    text = stringResource(R.string.radar_overlay_edge_side),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = tuningConfig.radarEdgeSide == RadarEdgeSide.LEFT,
                        onClick = {
                            coroutineScope.launch {
                                actualDataStore.updateRadarEdgeSide(RadarEdgeSide.LEFT)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(text = stringResource(R.string.radar_overlay_edge_left))
                    }

                    SegmentedButton(
                        selected = tuningConfig.radarEdgeSide == RadarEdgeSide.RIGHT,
                        onClick = {
                            coroutineScope.launch {
                                actualDataStore.updateRadarEdgeSide(RadarEdgeSide.RIGHT)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(text = stringResource(R.string.radar_overlay_edge_right))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Clear State Mode Selection
                Text(
                    text = stringResource(R.string.radar_overlay_clear_mode),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = tuningConfig.radarClearMode == RadarClearMode.SLIM_RAIL,
                        onClick = {
                            coroutineScope.launch {
                                actualDataStore.updateRadarClearMode(RadarClearMode.SLIM_RAIL)
                            }
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.radar_overlay_clear_mode_slim),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )

                    FilterChip(
                        selected = tuningConfig.radarClearMode == RadarClearMode.BADGE_ONLY,
                        onClick = {
                            coroutineScope.launch {
                                actualDataStore.updateRadarClearMode(RadarClearMode.BADGE_ONLY)
                            }
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.radar_overlay_clear_mode_badge),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )

                    FilterChip(
                        selected = tuningConfig.radarClearMode == RadarClearMode.AUTO_HIDE_3S,
                        onClick = {
                            coroutineScope.launch {
                                actualDataStore.updateRadarClearMode(RadarClearMode.AUTO_HIDE_3S)
                            }
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.radar_overlay_clear_mode_autohide),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }
        }

        // Audio & Haptic Alerts Card (REQ-TRK-063.6)
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.radar_alerts_settings_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Alert Mode Selection
                Text(
                    text = stringResource(R.string.radar_alert_mode_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = tuningConfig.radarAudioAlertMode == RadarAudioAlertMode.ALL_ALERTS,
                        onClick = {
                            coroutineScope.launch {
                                actualDataStore.updateRadarAudioAlertMode(RadarAudioAlertMode.ALL_ALERTS)
                            }
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.radar_alert_mode_all),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )

                    FilterChip(
                        selected = tuningConfig.radarAudioAlertMode == RadarAudioAlertMode.APPROACH_AND_FAST,
                        onClick = {
                            coroutineScope.launch {
                                actualDataStore.updateRadarAudioAlertMode(RadarAudioAlertMode.APPROACH_AND_FAST)
                            }
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.radar_alert_mode_approach_fast),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )

                    FilterChip(
                        selected = tuningConfig.radarAudioAlertMode == RadarAudioAlertMode.FAST_ONLY,
                        onClick = {
                            coroutineScope.launch {
                                actualDataStore.updateRadarAudioAlertMode(RadarAudioAlertMode.FAST_ONLY)
                            }
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.radar_alert_mode_fast_only),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )

                    FilterChip(
                        selected = tuningConfig.radarAudioAlertMode == RadarAudioAlertMode.MUTED,
                        onClick = {
                            coroutineScope.launch {
                                actualDataStore.updateRadarAudioAlertMode(RadarAudioAlertMode.MUTED)
                            }
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.radar_alert_mode_muted),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Alert Volume Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.radar_alert_volume_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${tuningConfig.radarAlertVolume}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = tuningConfig.radarAlertVolume.toFloat(),
                    onValueChange = { newValue ->
                        coroutineScope.launch {
                            actualDataStore.updateRadarAlertVolume(newValue.roundToInt())
                        }
                    },
                    valueRange = 10f..100f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Audio Output Route Selection
                Text(
                    text = stringResource(R.string.radar_alert_route_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = tuningConfig.radarAudioRoute == RadarAudioRoute.SYSTEM_DEFAULT,
                        onClick = {
                            coroutineScope.launch {
                                actualDataStore.updateRadarAudioRoute(RadarAudioRoute.SYSTEM_DEFAULT)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(text = stringResource(R.string.radar_alert_route_default))
                    }

                    SegmentedButton(
                        selected = tuningConfig.radarAudioRoute == RadarAudioRoute.FORCE_SPEAKER,
                        onClick = {
                            coroutineScope.launch {
                                actualDataStore.updateRadarAudioRoute(RadarAudioRoute.FORCE_SPEAKER)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(text = stringResource(R.string.radar_alert_route_speaker))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Speaker Volume Boost Subordinate Toggle (design_guidelines.md §3.3)
                SubordinateTuningToggleItem(
                    title = stringResource(R.string.radar_alert_speaker_boost),
                    isChecked = tuningConfig.radarSpeakerBoost,
                    onCheckedChange = { checked ->
                        coroutineScope.launch {
                            actualDataStore.updateRadarSpeakerBoost(checked)
                        }
                    },
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Haptic Vibration Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = stringResource(R.string.radar_alert_haptic_title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.radar_alert_haptic_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = tuningConfig.radarHapticAlert,
                        onCheckedChange = { checked ->
                            coroutineScope.launch {
                                actualDataStore.updateRadarHapticAlert(checked)
                            }
                        }
                    )
                }
            }
        }

        // Simulator Card (REQ-CON-029.4)
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.radar_simulator_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.radar_simulator_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Scenario selection chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RadarScenario.values().forEach { scenario ->
                    FilterChip(
                        selected = selectedScenario == scenario,
                        onClick = {
                            selectedScenario = scenario
                            if (isRunning) {
                                RadarTrafficSimulator.start(scenario)
                            }
                        },
                        label = {
                            Text(
                                text = getScenarioTitle(scenario),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Active scenario description
            Text(
                text = getScenarioDescription(selectedScenario),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Controls and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isRunning) {
                            stringResource(R.string.radar_simulator_running)
                        } else {
                            stringResource(R.string.radar_simulator_idle)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isRunning) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    if (isRunning) {
                        Text(
                            text = "${radarState.connectionState} • ${radarState.targets.size} targets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isRunning) {
                    OutlinedButton(
                        onClick = {
                            RadarTrafficSimulator.stop()
                            onSimulationToggled?.invoke(false)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(text = stringResource(R.string.radar_simulator_stop))
                    }
                } else {
                    Button(
                        onClick = {
                            RadarTrafficSimulator.start(selectedScenario)
                            onSimulationToggled?.invoke(true)
                        }
                    ) {
                        Text(text = stringResource(R.string.radar_simulator_start))
                    }
                }
            }
        }
    }
}
}

@Composable
private fun getScenarioTitle(scenario: RadarScenario): String {
    return when (scenario) {
        RadarScenario.SINGLE_CAR -> stringResource(R.string.radar_scenario_single_car)
        RadarScenario.CONVOY -> stringResource(R.string.radar_scenario_convoy)
        RadarScenario.HIGH_SPEED -> stringResource(R.string.radar_scenario_high_speed)
        RadarScenario.DISCONNECT_RECONNECT -> stringResource(R.string.radar_scenario_disconnect)
        RadarScenario.COUNTRY_ROAD_LOOP -> stringResource(R.string.radar_scenario_country_road)
    }
}

@Composable
private fun getScenarioDescription(scenario: RadarScenario): String {
    return when (scenario) {
        RadarScenario.SINGLE_CAR -> stringResource(R.string.radar_scenario_single_car_desc)
        RadarScenario.CONVOY -> stringResource(R.string.radar_scenario_convoy_desc)
        RadarScenario.HIGH_SPEED -> stringResource(R.string.radar_scenario_high_speed_desc)
        RadarScenario.DISCONNECT_RECONNECT -> stringResource(R.string.radar_scenario_disconnect_desc)
        RadarScenario.COUNTRY_ROAD_LOOP -> stringResource(R.string.radar_scenario_country_road_desc)
    }
}
