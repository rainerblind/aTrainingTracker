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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.radar.RadarScenario
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository
import com.atrainingtracker.banalservice.devices.radar.RadarTrafficSimulator

/**
 * Settings and preview composable for the cycling radar traffic simulator (REQ-CON-029.4).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadarSettingsSection(
    modifier: Modifier = Modifier,
    onSimulationToggled: ((Boolean) -> Unit)? = null
) {
    var selectedScenario by remember { mutableStateOf(RadarScenario.SINGLE_CAR) }
    val radarState by RadarStateRepository.radarState.collectAsState()
    val isRunning = RadarTrafficSimulator.isSimulating

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
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
