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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
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
                        uiState = uiState,
                        onSportSelected = { viewModel.setSport(it) },
                        onProtocolTypeSelected = { viewModel.setProtocolType(it) },
                        onStepDurationSelected = { viewModel.setStepDurationSec(it) },
                        onStartTest = { viewModel.startProtocol() }
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
    uiState: HrvRampTestUiState,
    onSportSelected: (HrvTestSport) -> Unit,
    onProtocolTypeSelected: (HrvTestProtocolType) -> Unit,
    onStepDurationSelected: (Int) -> Unit,
    onStartTest: () -> Unit
) {
    val config = uiState.config
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
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {}
                ) {
                    Text(
                        text = stringResource(R.string.hrv_test_sport_cycling),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SegmentedButton(
                    selected = config.sport == HrvTestSport.RUNNING,
                    onClick = { onSportSelected(HrvTestSport.RUNNING) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {}
                ) {
                    Text(
                        text = stringResource(R.string.hrv_test_sport_running),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {}
                ) {
                    Text(
                        text = stringResource(R.string.hrv_test_protocol_step),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SegmentedButton(
                    selected = config.protocolType == HrvTestProtocolType.GUIDED_RAMP,
                    onClick = { onProtocolTypeSelected(HrvTestProtocolType.GUIDED_RAMP) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {}
                ) {
                    Text(
                        text = stringResource(R.string.hrv_test_protocol_ramp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {}
                    ) {
                        Text(
                            text = stringResource(R.string.hrv_test_step_duration_4min),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    SegmentedButton(
                        selected = config.stepDurationSec == 180,
                        onClick = { onStepDurationSelected(180) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {}
                    ) {
                        Text(
                            text = stringResource(R.string.hrv_test_step_duration_3min),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Dynamic Protocol Info & Pros/Cons Card
            ProtocolInfoCard(config = config)

            // Inline Sensor & Signal Status Card (REQ-TRK-072.3)
            InlineSensorStatusCard(uiState = uiState)

            // Direct Start Button (Single-screen setup)
            Button(
                onClick = onStartTest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("hrv_test_start_button"),
                enabled = uiState.canStartTest,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.hrv_test_btn_start),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ProtocolInfoCard(config: HrvRampTestConfig) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hrv_test_protocol_info_card"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val titleText = if (config.protocolType == HrvTestProtocolType.GUIDED_STEP) {
                val durMin = config.stepDurationSec / 60
                stringResource(R.string.hrv_test_title_step_fmt, durMin)
            } else {
                stringResource(R.string.hrv_test_title_ramp)
            }

            val badgeText = if (config.protocolType == HrvTestProtocolType.GUIDED_STEP) {
                if (config.stepDurationSec == 240) {
                    stringResource(R.string.hrv_test_badge_step_4min)
                } else {
                    stringResource(R.string.hrv_test_badge_step_3min)
                }
            } else {
                stringResource(R.string.hrv_test_badge_ramp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = badgeText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Text(
                text = stringResource(R.string.hrv_test_info_procedure_label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Step 1: Warmup
            val step1Text = if (config.sport == HrvTestSport.CYCLING) {
                stringResource(R.string.hrv_test_step1_warmup_cycling)
            } else {
                stringResource(R.string.hrv_test_step1_warmup_running)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "1.",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = step1Text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Step 2: Progression
            val step2Text = if (config.protocolType == HrvTestProtocolType.GUIDED_STEP) {
                val durMin = config.stepDurationSec / 60
                if (config.sport == HrvTestSport.CYCLING) {
                    stringResource(R.string.hrv_test_step2_step_cycling_fmt, durMin)
                } else {
                    stringResource(R.string.hrv_test_step2_step_running_fmt, durMin)
                }
            } else {
                if (config.sport == HrvTestSport.CYCLING) {
                    stringResource(R.string.hrv_test_step2_ramp_cycling)
                } else {
                    stringResource(R.string.hrv_test_step2_ramp_running)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "2.",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = step2Text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Step 3: Submaximal End
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "3.",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(R.string.hrv_test_step3_completion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Pros and Cons
            val proText = if (config.protocolType == HrvTestProtocolType.GUIDED_STEP) {
                if (config.stepDurationSec == 240) {
                    stringResource(R.string.hrv_test_pro_step_4min)
                } else {
                    stringResource(R.string.hrv_test_pro_step_3min)
                }
            } else {
                stringResource(R.string.hrv_test_pro_ramp)
            }

            val conText = if (config.protocolType == HrvTestProtocolType.GUIDED_STEP) {
                if (config.stepDurationSec == 240) {
                    stringResource(R.string.hrv_test_con_step_4min)
                } else {
                    stringResource(R.string.hrv_test_con_step_3min)
                }
            } else {
                stringResource(R.string.hrv_test_con_ramp)
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = TTColor.HrvSuccessGreen.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "+",
                        fontWeight = FontWeight.ExtraBold,
                        color = TTColor.HrvSuccessGreen,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${stringResource(R.string.hrv_test_pro_label)} $proText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = TTColor.HrvQualityModerate.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "–",
                        fontWeight = FontWeight.ExtraBold,
                        color = TTColor.HrvQualityModerate,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${stringResource(R.string.hrv_test_con_label)} $conText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineSensorStatusCard(uiState: HrvRampTestUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hrv_test_inline_sensor_card"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.hrv_test_preflight_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val statusColor = when (uiState.sensorQualityTier) {
                SensorQualityTier.HIGH -> TTColor.HrvQualityHigh
                SensorQualityTier.MODERATE -> TTColor.HrvQualityModerate
                SensorQualityTier.UNSUITABLE -> MaterialTheme.colorScheme.error
            }

            val icon = when (uiState.sensorQualityTier) {
                SensorQualityTier.HIGH -> Icons.Default.Check
                SensorQualityTier.MODERATE, SensorQualityTier.UNSUITABLE -> Icons.Default.WarningAmber
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(statusColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    if (uiState.preFlightMessageResId != null) {
                        Text(
                            text = stringResource(uiState.preFlightMessageResId),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        uiState.currentHr?.let { hr ->
                            Text(
                                text = stringResource(R.string.hrv_test_live_hr_fmt, hr),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        uiState.preFlightArtifactRate?.let { art ->
                            Text(
                                text = stringResource(R.string.hrv_test_live_artifacts, art),
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor
                            )
                        }
                    }
                }
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
                SensorQualityTier.HIGH -> TTColor.HrvQualityHigh
                SensorQualityTier.MODERATE -> TTColor.HrvQualityModerate
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
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.hrv_test_btn_cancel),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onStartProtocol,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("hrv_test_start_button"),
                    enabled = canStart,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.hrv_test_btn_start),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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

            // Prominent Side-by-Side Ziel vs. Aktuell Comparison Block (REQ-TRK-072.4a)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hrv_test_pacing_comparison_card"),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Target (Ziel) Column
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.hrv_test_pacing_target_header),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val targetValueStr = if (uiState.config.sport == HrvTestSport.CYCLING) {
                            "${uiState.targetIntensity.toInt()} W"
                        } else {
                            String.format(Locale.US, "%.1f km/h", uiState.targetIntensity)
                        }
                        Text(
                            text = targetValueStr,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("hrv_test_target_intensity")
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(48.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    // Actual (Aktuell / Ist) Column
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.hrv_test_pacing_actual_header),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val actualValueStr = if (uiState.config.sport == HrvTestSport.CYCLING) {
                            uiState.currentPower?.let { "${it.toInt()} W" } ?: "--"
                        } else {
                            uiState.currentSpeedKmh?.let { String.format(Locale.US, "%.1f km/h", it) } ?: "--"
                        }
                        Text(
                            text = actualValueStr,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("hrv_test_actual_intensity")
                        )
                    }
                }
            }

            // Step Countdown Timer
            val timeStr = formatTimer(uiState.stepRemainingSec)
            Text(
                text = stringResource(R.string.hrv_test_time_remaining, timeStr),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("hrv_test_timer")
            )

            // Live Telemetry Row (REQ-TRK-072.4a)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Live Heart Rate
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = TTColor.HrvHeartRateRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = uiState.currentHr?.let { "$it bpm" } ?: "--",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("hrv_test_live_hr")
                    )
                }

                // Live Cycling Power
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.graph_heading_power),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = uiState.currentPower?.let { "${it.toInt()} W" } ?: "--",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("hrv_test_live_power")
                    )
                }

                // Live Speed (REQ-TRK-072.4a)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.graph_heading_speed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = uiState.currentSpeedKmh?.let { String.format(Locale.US, "%.1f km/h", it) } ?: "--",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("hrv_test_live_speed")
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
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("hrv_test_live_artifacts")
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
                    colors = CardDefaults.cardColors(containerColor = TTColor.HrvSuccessGreen.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.hrv_test_early_complete_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TTColor.HrvSuccessGreen
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
                                .height(44.dp)
                                .testTag("hrv_test_cooldown_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = TTColor.HrvSuccessGreen)
                        ) {
                            Text(
                                text = stringResource(R.string.hrv_test_btn_cooldown),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.hrv_test_btn_cooldown),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Button(
                    onClick = onFinishTest,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("hrv_test_finish_button")
                ) {
                    Text(
                        text = stringResource(R.string.hrv_test_btn_finish),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedButton(
                onClick = onCancelTest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(
                    text = stringResource(R.string.hrv_test_btn_cancel),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
                    .background(TTColor.HrvSuccessGreen.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = TTColor.HrvSuccessGreen,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.morning_hrv_btn_saved),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatTimer(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}
