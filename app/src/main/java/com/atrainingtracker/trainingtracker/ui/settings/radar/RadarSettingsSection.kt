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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.atrainingtracker.banalservice.devices.radar.RadarLightController
import com.atrainingtracker.banalservice.devices.radar.RadarScenario
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository
import com.atrainingtracker.banalservice.devices.radar.RadarTrafficSimulator
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.audio.radar.RadarAlertManager
import com.atrainingtracker.trainingtracker.audio.radar.RadarAudioPreviewType
import com.atrainingtracker.trainingtracker.settings.RadarAudioAlertMode
import com.atrainingtracker.trainingtracker.settings.RadarAudioRoute
import com.atrainingtracker.trainingtracker.settings.RadarClearMode
import com.atrainingtracker.trainingtracker.settings.RadarEdgeSide
import com.atrainingtracker.trainingtracker.settings.RadarLightControlMode
import com.atrainingtracker.trainingtracker.settings.RadarTrafficOrientation
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.ui.settings.tuning.SubordinateTuningToggleItem
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningDropdownRow
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSegmentedButtonRow
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningToggleItem
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Settings and preview composable for cycling radar edge overlay and traffic simulator (REQ-TRK-062.5, REQ-CON-029.4).
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongMethod")
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
    val isRunning by RadarTrafficSimulator.isSimulatingFlow.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            RadarAlertManager.releasePreviewSynthesizer()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Overlay Customization (REQ-TRK-062.5)
        Text(
            text = stringResource(R.string.radar_overlay_settings_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Edge Side Selection
        TuningSegmentedButtonRow(
            title = stringResource(R.string.radar_overlay_edge_side),
            options = listOf(RadarEdgeSide.LEFT, RadarEdgeSide.RIGHT),
            selectedOption = tuningConfig.radarEdgeSide,
            onOptionSelected = { side ->
                coroutineScope.launch {
                    actualDataStore.updateRadarEdgeSide(side)
                }
            },
            labelProvider = { side ->
                when (side) {
                    RadarEdgeSide.LEFT -> stringResource(R.string.radar_overlay_edge_left)
                    RadarEdgeSide.RIGHT -> stringResource(R.string.radar_overlay_edge_right)
                }
            }
        )

        // Clear State Mode Selection
        TuningSegmentedButtonRow(
            title = stringResource(R.string.radar_overlay_clear_mode),
            options = listOf(RadarClearMode.SLIM_RAIL, RadarClearMode.BADGE_ONLY, RadarClearMode.AUTO_HIDE_3S),
            selectedOption = tuningConfig.radarClearMode,
            onOptionSelected = { mode ->
                coroutineScope.launch {
                    actualDataStore.updateRadarClearMode(mode)
                }
            },
            labelProvider = { mode ->
                when (mode) {
                    RadarClearMode.SLIM_RAIL -> stringResource(R.string.radar_overlay_clear_mode_slim)
                    RadarClearMode.BADGE_ONLY -> stringResource(R.string.radar_overlay_clear_mode_badge)
                    RadarClearMode.AUTO_HIDE_3S -> stringResource(R.string.radar_overlay_clear_mode_autohide)
                }
            }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // 2. Audio & Haptic Alerts (REQ-TRK-063.6)
        Text(
            text = stringResource(R.string.radar_alerts_settings_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Alert Mode Selection
        TuningSegmentedButtonRow(
            title = stringResource(R.string.radar_alert_mode_title),
            options = listOf(
                RadarAudioAlertMode.ALL_ALERTS,
                RadarAudioAlertMode.APPROACH_AND_FAST,
                RadarAudioAlertMode.FAST_ONLY,
                RadarAudioAlertMode.MUTED
            ),
            selectedOption = tuningConfig.radarAudioAlertMode,
            onOptionSelected = { mode ->
                coroutineScope.launch {
                    actualDataStore.updateRadarAudioAlertMode(mode)
                }
            },
            labelProvider = { mode ->
                when (mode) {
                    RadarAudioAlertMode.ALL_ALERTS -> stringResource(R.string.radar_alert_mode_all)
                    RadarAudioAlertMode.APPROACH_AND_FAST -> stringResource(R.string.radar_alert_mode_approach_fast)
                    RadarAudioAlertMode.FAST_ONLY -> stringResource(R.string.radar_alert_mode_fast_only)
                    RadarAudioAlertMode.MUTED -> stringResource(R.string.radar_alert_mode_muted)
                }
            }
        )

        // Alert Volume Slider
        TuningSliderItem(
            title = stringResource(R.string.radar_alert_volume_title),
            valueText = "${tuningConfig.radarAlertVolume}%",
            helperText = "",
            defaultText = "",
            value = tuningConfig.radarAlertVolume.toFloat(),
            onValueChange = { newValue ->
                coroutineScope.launch {
                    actualDataStore.updateRadarAlertVolume(newValue.roundToInt())
                }
            },
            valueRange = 10f..100f,
            steps = 8
        )

        // Audio Output Route Selection
        TuningSegmentedButtonRow(
            title = stringResource(R.string.radar_alert_route_title),
            options = listOf(RadarAudioRoute.SYSTEM_DEFAULT, RadarAudioRoute.FORCE_SPEAKER),
            selectedOption = tuningConfig.radarAudioRoute,
            onOptionSelected = { route ->
                coroutineScope.launch {
                    actualDataStore.updateRadarAudioRoute(route)
                }
            },
            labelProvider = { route ->
                when (route) {
                    RadarAudioRoute.SYSTEM_DEFAULT -> stringResource(R.string.radar_alert_route_default)
                    RadarAudioRoute.FORCE_SPEAKER -> stringResource(R.string.radar_alert_route_speaker)
                }
            }
        )

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

        // Haptic Vibration Toggle
        TuningToggleItem(
            title = stringResource(R.string.radar_alert_haptic_title),
            isChecked = tuningConfig.radarHapticAlert,
            onCheckedChange = { checked ->
                coroutineScope.launch {
                    actualDataStore.updateRadarHapticAlert(checked)
                }
            },
            summary = stringResource(R.string.radar_alert_haptic_desc)
        )

        // Peloton Audio Sync Toggle (REQ-TRK-064.2)
        TuningToggleItem(
            title = stringResource(R.string.radar_peloton_audio_sync_title),
            isChecked = tuningConfig.radarPelotonAudioSync,
            onCheckedChange = { checked ->
                coroutineScope.launch {
                    actualDataStore.updateRadarPelotonAudioSync(checked)
                }
            },
            summary = stringResource(R.string.radar_peloton_audio_sync_desc)
        )

        // Smart Urban Congestion Filter Toggle (REQ-TRK-064.3)
        TuningToggleItem(
            title = stringResource(R.string.radar_smart_urban_mute_title),
            isChecked = tuningConfig.radarSmartUrbanMute,
            onCheckedChange = { checked ->
                coroutineScope.launch {
                    actualDataStore.updateRadarSmartUrbanMute(checked)
                }
            },
            summary = stringResource(R.string.radar_smart_urban_mute_desc)
        )

        // 3D Stereo Audio Toggle (REQ-TRK-065.1)
        TuningToggleItem(
            title = stringResource(R.string.radar_spatial_audio_title),
            isChecked = tuningConfig.radarSpatialAudioEnabled,
            onCheckedChange = { checked ->
                coroutineScope.launch {
                    actualDataStore.updateRadarSpatialAudioEnabled(checked)
                }
            },
            summary = stringResource(R.string.radar_spatial_audio_desc)
        )

        if (tuningConfig.radarSpatialAudioEnabled) {
            // Traffic Orientation Selection (Rule 1.1 SingleChoiceSegmentedButtonRow)
            TuningSegmentedButtonRow(
                title = stringResource(R.string.radar_traffic_orientation_title),
                options = listOf(RadarTrafficOrientation.RIGHT_HAND, RadarTrafficOrientation.LEFT_HAND),
                selectedOption = tuningConfig.radarTrafficOrientation,
                onOptionSelected = { orientation ->
                    coroutineScope.launch {
                        actualDataStore.updateRadarTrafficOrientation(orientation)
                    }
                },
                labelProvider = { orientation ->
                    when (orientation) {
                        RadarTrafficOrientation.RIGHT_HAND -> stringResource(R.string.radar_traffic_orientation_rht)
                        RadarTrafficOrientation.LEFT_HAND -> stringResource(R.string.radar_traffic_orientation_lht)
                    }
                }
            )

            // Lane-Change Spatial Tick Subordinate Toggle (REQ-TRK-065.3)
            SubordinateTuningToggleItem(
                title = stringResource(R.string.radar_lane_change_tick_title),
                isChecked = tuningConfig.radarLaneChangeTickEnabled,
                onCheckedChange = { checked ->
                    coroutineScope.launch {
                        actualDataStore.updateRadarLaneChangeTickEnabled(checked)
                    }
                },
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )
        }

        // Approach-Speed Pitch Modulation Subordinate Toggle (REQ-TRK-065.2)
        SubordinateTuningToggleItem(
            title = stringResource(R.string.radar_speed_pitch_title),
            isChecked = tuningConfig.radarSpeedPitchModulation,
            onCheckedChange = { checked ->
                coroutineScope.launch {
                    actualDataStore.updateRadarSpeedPitchModulation(checked)
                }
            },
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
        )

        // Interactive Audio Preview Section (REQ-TRK-065.5)
        Text(
            text = stringResource(R.string.radar_preview_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AssistChip(
                onClick = {
                    RadarAlertManager.playAudioPreview(context, RadarAudioPreviewType.CENTERED, tuningConfig)
                },
                label = {
                    Text(
                        text = stringResource(R.string.radar_preview_centered),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
            AssistChip(
                onClick = {
                    RadarAlertManager.playAudioPreview(context, RadarAudioPreviewType.PASSING, tuningConfig)
                },
                label = {
                    Text(
                        text = stringResource(R.string.radar_preview_passing),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
            AssistChip(
                onClick = {
                    RadarAlertManager.playAudioPreview(context, RadarAudioPreviewType.SLOW, tuningConfig)
                },
                label = {
                    Text(
                        text = stringResource(R.string.radar_preview_slow),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
            AssistChip(
                onClick = {
                    RadarAlertManager.playAudioPreview(context, RadarAudioPreviewType.FAST, tuningConfig)
                },
                label = {
                    Text(
                        text = stringResource(R.string.radar_preview_fast),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
            AssistChip(
                onClick = {
                    RadarAlertManager.playAudioPreview(context, RadarAudioPreviewType.LANE_CHANGE, tuningConfig)
                },
                label = {
                    Text(
                        text = stringResource(R.string.radar_preview_lane_change),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // 3. Tail Light Control (REQ-CON-031.5)
        Text(
            text = stringResource(R.string.radar_light_settings_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        // 3-Way Tail Light Control Mode Selector (REQ-CON-031.5c / ATT-7538)
        val controlModeHelperText = when (tuningConfig.radarLightControlMode) {
            RadarLightControlMode.MANUAL -> stringResource(R.string.radar_light_control_mode_manual_desc)
            RadarLightControlMode.FIXED_WORKOUT_MODE -> stringResource(R.string.radar_light_control_mode_fixed_desc)
            RadarLightControlMode.SMART_AUTOMATION -> stringResource(R.string.radar_light_control_mode_smart_desc)
        }
        TuningSegmentedButtonRow(
            title = stringResource(R.string.radar_light_control_mode_title),
            options = listOf(
                RadarLightControlMode.MANUAL,
                RadarLightControlMode.FIXED_WORKOUT_MODE,
                RadarLightControlMode.SMART_AUTOMATION
            ),
            selectedOption = tuningConfig.radarLightControlMode,
            onOptionSelected = { mode ->
                coroutineScope.launch {
                    actualDataStore.updateRadarLightControlMode(mode)
                }
            },
            labelProvider = { mode ->
                when (mode) {
                    RadarLightControlMode.MANUAL -> stringResource(R.string.radar_light_control_mode_manual)
                    RadarLightControlMode.FIXED_WORKOUT_MODE -> stringResource(R.string.radar_light_control_mode_fixed)
                    RadarLightControlMode.SMART_AUTOMATION -> stringResource(R.string.radar_light_control_mode_smart)
                }
            },
            helperText = controlModeHelperText
        )

        // Default Light Mode Selection: Conditionally visible ONLY in FIXED_WORKOUT_MODE (REQ-CON-031.5d)
        if (tuningConfig.radarLightControlMode == RadarLightControlMode.FIXED_WORKOUT_MODE) {
            val availableModes = RadarLightController.getAvailableModes()
            TuningDropdownRow(
                title = stringResource(R.string.radar_light_mode_title),
                options = availableModes,
                selectedOption = tuningConfig.radarLightDefaultMode,
                onOptionSelected = { mode ->
                    coroutineScope.launch {
                        actualDataStore.updateRadarLightDefaultMode(mode)
                    }
                },
                labelProvider = { stringResource(it.titleResId) },
                testTag = "RadarLightDefaultModeDropdown",
                itemTestTagPrefix = "RadarLightModeItem"
            )
        }

        // Software Deceleration Brake-Light Trigger (REQ-CON-035.6 / ATT-5121)
        TuningToggleItem(
            title = stringResource(R.string.radar_brake_light_title),
            isChecked = tuningConfig.radarBrakeLightEnabled,
            onCheckedChange = { checked ->
                coroutineScope.launch {
                    actualDataStore.updateRadarBrakeLightEnabled(checked)
                }
            },
            summary = stringResource(R.string.radar_brake_light_desc)
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // 4. Close-Pass Hazard Threshold (REQ-TRK-073.4 / ATT-5096)
        Text(
            text = stringResource(R.string.radar_close_pass_threshold_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        val thresholdPresets = listOf(1.0f, 1.5f, 2.0f)
        val currentThreshold = thresholdPresets.firstOrNull {
            kotlin.math.abs(tuningConfig.radarClosePassThresholdM - it) < 0.01f
        } ?: 1.5f

        TuningSegmentedButtonRow(
            title = stringResource(R.string.radar_close_pass_threshold_summary),
            options = thresholdPresets,
            selectedOption = currentThreshold,
            onOptionSelected = { threshold ->
                coroutineScope.launch {
                    actualDataStore.updateRadarClosePassThreshold(threshold)
                }
            },
            labelProvider = { threshold ->
                when (threshold) {
                    1.0f -> stringResource(R.string.radar_close_pass_threshold_1_0m)
                    2.0f -> stringResource(R.string.radar_close_pass_threshold_2_0m)
                    else -> stringResource(R.string.radar_close_pass_threshold_1_5m)
                }
            }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // 5. Post-Workout Traffic & Strava Export Summary (REQ-RAD-007 / ATT-5122)
        var stravaSummaryEnabled by remember {
            mutableStateOf(TrainingApplication.uploadStravaRadarSummary())
        }

        TuningToggleItem(
            title = stringResource(R.string.radar_strava_summary_title),
            isChecked = stravaSummaryEnabled,
            onCheckedChange = { checked ->
                stravaSummaryEnabled = checked
                TrainingApplication.setUploadStravaRadarSummary(checked)
            },
            summary = stringResource(R.string.radar_strava_summary_desc)
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // 6. Traffic Simulator (REQ-CON-029.4)
        Text(
            text = stringResource(R.string.radar_simulator_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = stringResource(R.string.radar_simulator_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Scenario selection dropdown (Design Guidelines §1.3: >4 options -> TuningDropdownRow)
        TuningDropdownRow(
            title = stringResource(R.string.radar_simulator_scenario_label),
            options = RadarScenario.values().toList(),
            selectedOption = selectedScenario,
            onOptionSelected = { scenario ->
                selectedScenario = scenario
                if (isRunning) {
                    RadarTrafficSimulator.start(scenario)
                }
            },
            labelProvider = { scenario -> getScenarioTitle(scenario) },
            testTag = "RadarSimulatorScenarioDropdown",
            itemTestTagPrefix = "RadarScenarioItem"
        )

        // Active scenario description
        Text(
            text = getScenarioDescription(selectedScenario),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
                        text = stringResource(
                            R.string.radar_simulator_status,
                            radarState.connectionState.name,
                            radarState.targets.size
                        ),
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
