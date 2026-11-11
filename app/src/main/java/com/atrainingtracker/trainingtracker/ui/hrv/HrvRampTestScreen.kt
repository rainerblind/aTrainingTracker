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

package com.atrainingtracker.trainingtracker.ui.hrv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atrainingtracker.R
import java.util.Locale

/**
 * Guided DFA a1 Progressive Step and Continuous Ramp Test Protocol Screen (`REQ-TRK-072`).
 *
 * Implements:
 * 1. Pre-flight sensor validation ensuring beat-to-beat RR streaming and artifact rate <= 3.0% (`REQ-TRK-072.3`).
 * 2. Real-time step target intensity and countdown timer (`REQ-TRK-072.1`, `REQ-TRK-072.4`).
 * 3. Live DFA a1, artifact rate %, and HR display (`REQ-TRK-072.4`).
 * 4. Sub-exhaustion early completion detection banner alerting when a1 < 0.45 (`REQ-TRK-072.5`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HrvRampTestScreen(
    viewModel: HrvRampTestViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.hrv_test_title),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("hrv_test_title")
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.hrv_test_btn_cancel)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle Card
            Text(
                text = stringResource(R.string.hrv_test_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (uiState.phase) {
                HrvTestPhase.CONFIG -> {
                    ProtocolConfigurationCard(
                        config = uiState.config,
                        onSportSelected = { viewModel.setSport(it) },
                        onProtocolTypeSelected = { viewModel.setProtocolType(it) },
                        onStepDurationSelected = { viewModel.setStepDurationSec(it) },
                        onStartPreFlight = { viewModel.startPreFlight() }
                    )
                }
                HrvTestPhase.PRE_FLIGHT -> {
                    PreFlightSensorCard(
                        tier = uiState.sensorQualityTier,
                        artifactRate = uiState.preFlightArtifactRate,
                        messageResId = uiState.preFlightMessageResId,
                        canStart = uiState.canStartTest,
                        onStartProtocol = { viewModel.startProtocol() },
                        onCancel = { viewModel.cancelTest() }
                    )
                }
                HrvTestPhase.WARMUP,
                HrvTestPhase.TESTING,
                HrvTestPhase.EARLY_COMPLETION,
                HrvTestPhase.COOLDOWN -> {
                    ActivePacingGuidanceCard(
                        uiState = uiState,
                        onTransitionCooldown = { viewModel.transitionToCooldown() },
                        onFinishTest = { viewModel.finishTest() },
                        onCancelTest = { viewModel.cancelTest() }
                    )
                }
                HrvTestPhase.FINISHED -> {
                    ProtocolFinishedCard(
                        onDone = onNavigateBack
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtocolConfigurationCard(
    config: HrvRampTestConfig,
    onSportSelected: (HrvTestSport) -> Unit,
    onProtocolTypeSelected: (HrvTestProtocolType) -> Unit,
    onStepDurationSelected: (Int) -> Unit,
    onStartPreFlight: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hrv_test_config_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Sport Selector
            Text(
                text = stringResource(R.string.hrv_test_sport_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hrv_test_sport_selector")
            ) {
                SegmentedButton(
                    selected = config.sport == HrvTestSport.CYCLING,
                    onClick = { onSportSelected(HrvTestSport.CYCLING) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(stringResource(R.string.hrv_test_sport_cycling))
                }
                SegmentedButton(
                    selected = config.sport == HrvTestSport.RUNNING,
                    onClick = { onSportSelected(HrvTestSport.RUNNING) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(stringResource(R.string.hrv_test_sport_running))
                }
            }

            // 2. Protocol Type Selector
            Text(
                text = stringResource(R.string.hrv_test_protocol_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hrv_test_protocol_selector")
            ) {
                SegmentedButton(
                    selected = config.protocolType == HrvTestProtocolType.GUIDED_STEP,
                    onClick = { onProtocolTypeSelected(HrvTestProtocolType.GUIDED_STEP) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(stringResource(R.string.hrv_test_protocol_step))
                }
                SegmentedButton(
                    selected = config.protocolType == HrvTestProtocolType.GUIDED_RAMP,
                    onClick = { onProtocolTypeSelected(HrvTestProtocolType.GUIDED_RAMP) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(stringResource(R.string.hrv_test_protocol_ramp))
                }
            }

            // 3. Step Duration (if Step Protocol)
            if (config.protocolType == HrvTestProtocolType.GUIDED_STEP) {
                Text(
                    text = stringResource(R.string.hrv_test_step_duration_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = config.stepDurationSec == 240,
                        onClick = { onStepDurationSelected(240) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(stringResource(R.string.hrv_test_step_duration_4min))
                    }
                    SegmentedButton(
                        selected = config.stepDurationSec == 180,
                        onClick = { onStepDurationSelected(180) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(stringResource(R.string.hrv_test_step_duration_3min))
                    }
                }
            }

            // Warm-up hint
            Text(
                text = stringResource(R.string.hrv_test_warmup_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Pre-flight action
            Button(
                onClick = onStartPreFlight,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hrv_test_preflight_button"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.hrv_test_preflight_title))
            }
        }
    }
}

@Composable
private fun PreFlightSensorCard(
    tier: SensorQualityTier,
    artifactRate: Double?,
    messageResId: Int?,
    canStart: Boolean,
    onStartProtocol: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hrv_test_preflight_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.hrv_test_preflight_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            val statusColor = when (tier) {
                SensorQualityTier.HIGH -> Color(0xFF2E7D32)
                SensorQualityTier.MODERATE -> Color(0xFFF57F17)
                SensorQualityTier.UNSUITABLE -> MaterialTheme.colorScheme.error
            }

            val icon = when (tier) {
                SensorQualityTier.HIGH -> Icons.Default.Check
                SensorQualityTier.MODERATE -> Icons.Default.WarningAmber
                SensorQualityTier.UNSUITABLE -> Icons.Default.WarningAmber
            }

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(statusColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(36.dp)
                )
            }

            if (messageResId != null) {
                Text(
                    text = stringResource(messageResId),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (artifactRate != null) {
                Text(
                    text = stringResource(R.string.hrv_test_live_artifacts, artifactRate),
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.hrv_test_btn_cancel))
                }

                Button(
                    onClick = onStartProtocol,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hrv_test_start_button"),
                    enabled = canStart,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.hrv_test_btn_start))
                }
            }
        }
    }
}

@Composable
private fun ActivePacingGuidanceCard(
    uiState: HrvRampTestUiState,
    onTransitionCooldown: () -> Unit,
    onFinishTest: () -> Unit,
    onCancelTest: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hrv_test_active_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Phase Badge
            val phaseText = when (uiState.phase) {
                HrvTestPhase.WARMUP -> stringResource(R.string.hrv_test_phase_warmup)
                HrvTestPhase.TESTING, HrvTestPhase.EARLY_COMPLETION -> {
                    if (uiState.config.protocolType == HrvTestProtocolType.GUIDED_STEP) {
                        stringResource(R.string.hrv_test_phase_step, uiState.currentStepNumber)
                    } else {
                        stringResource(R.string.hrv_test_phase_ramp)
                    }
                }
                HrvTestPhase.COOLDOWN -> stringResource(R.string.hrv_test_phase_cooldown)
                else -> ""
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = phaseText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Target Intensity Display
            val targetStr = if (uiState.config.sport == HrvTestSport.CYCLING) {
                stringResource(R.string.hrv_test_target_power, uiState.targetIntensity.toInt())
            } else {
                stringResource(R.string.hrv_test_target_pace, String.format(Locale.US, "%.1f km/h", uiState.targetIntensity))
            }

            Text(
                text = targetStr,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("hrv_test_target_intensity")
            )

            // Step Countdown Timer
            val timeStr = formatTimer(uiState.stepRemainingSec)
            Text(
                text = stringResource(R.string.hrv_test_time_remaining, timeStr),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("hrv_test_timer")
            )

            // Live Telemetry Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Live Heart Rate
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = uiState.currentHr?.let { "$it bpm" } ?: "--",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Live DFA a1
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.hrv_dfa_a1),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = uiState.currentDfaA1?.let { String.format(Locale.US, "%.2f", it) } ?: "--",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("hrv_test_live_a1")
                    )
                }

                // Live Artifacts
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.hrv_artifact_rate_short),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = uiState.currentArtifactRate?.let { String.format(Locale.US, "%.1f%%", it) } ?: "--",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Early Completion Banner (REQ-TRK-072.5)
            AnimatedVisibility(
                visible = uiState.phase == HrvTestPhase.EARLY_COMPLETION || uiState.earlyCompletionDetected
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hrv_test_early_complete_banner"),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32).copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.hrv_test_early_complete_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            text = stringResource(R.string.hrv_test_early_complete_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Button(
                            onClick = onTransitionCooldown,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("hrv_test_cooldown_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text(stringResource(R.string.hrv_test_btn_cooldown))
                        }
                    }
                }
            }

            // Pacing Action Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.phase != HrvTestPhase.COOLDOWN) {
                    OutlinedButton(
                        onClick = onTransitionCooldown,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.hrv_test_btn_cooldown))
                    }
                }
                Button(
                    onClick = onFinishTest,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hrv_test_finish_button")
                ) {
                    Text(stringResource(R.string.hrv_test_btn_finish))
                }
            }

            OutlinedButton(
                onClick = onCancelTest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.hrv_test_btn_cancel))
            }
        }
    }
}

@Composable
private fun ProtocolFinishedCard(
    onDone: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFF2E7D32).copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(36.dp)
                )
            }

            Text(
                text = stringResource(R.string.hrv_test_early_complete_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.morning_hrv_btn_saved))
            }
        }
    }
}

private fun formatTimer(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}
