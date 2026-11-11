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

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.hrv.HrvSignalQualityStatus
import com.atrainingtracker.trainingtracker.database.hrv.HrvPosture
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessEntry
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessResult
import com.atrainingtracker.trainingtracker.database.hrv.ReadinessStatus
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.ui.tracking.HrvInfoBottomSheet
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Screen presenting guided morning resting HRV acquisition protocol (`REQ-PER-032`).
 *
 * Implements waking protocol guidance, sticky posture memory, live pacing countdown,
 * amber artifact warning for rate > 5.0%, and post-hoc SWC readiness evaluation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorningHrvCheckScreen(
    viewModel: MorningHrvCheckViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showInfoSheet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.morning_hrv_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.morning_hrv_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.hrv_info_title)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error banner if any
            if (uiState.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // 1. Checklist / Protocol Card
            WakingProtocolChecklistCard()

            // 2. Configuration Controls (Posture & Duration) - Enabled only in READY phase
            if (uiState.phase == MorningHrvPhase.READY) {
                PostureAndDurationCard(
                    selectedPosture = uiState.posture,
                    selectedDurationSec = uiState.durationSec,
                    onPostureSelected = { viewModel.setPosture(it) },
                    onDurationSelected = { viewModel.setDurationSec(it) }
                )
            }

            // 3. Live Pacing / Measurement Status Card
            MeasurementPacingCard(uiState = uiState)

            // 4. Artifact Warning (Amber Badge if > 5.0%)
            if (uiState.highArtifactWarning) {
                ArtifactWarningCard(artifactRatePct = uiState.artifactRatePct ?: 0.0)
            }

            // Smoothed / Quantized Signal Warning (REQ-TRK-068.3)
            if (uiState.phase == MorningHrvPhase.COMPLETED &&
                (uiState.signalQualityStatus == HrvSignalQualityStatus.SMOOTHED_STREAM ||
                 uiState.signalQualityStatus == HrvSignalQualityStatus.QUANTIZED_SYNTHETIC)
            ) {
                SmoothedSignalWarningCard()
            }

            // 5. Post-Hoc Measurement Results Card
            if (uiState.phase == MorningHrvPhase.COMPLETED && uiState.computedEntry != null) {
                ReadinessResultCard(
                    entry = uiState.computedEntry!!,
                    readinessResult = uiState.readinessResult
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 6. Action Buttons
            ActionButtonsSection(
                uiState = uiState,
                onStart = { viewModel.startMeasurement() },
                onCancel = { viewModel.cancelMeasurement() },
                onDiscard = { viewModel.resetMeasurement() },
                onSave = {
                    coroutineScope.launch {
                        val rowId = viewModel.saveReading()
                        if (rowId > 0) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.morning_hrv_saved_toast),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )
        }
    }

    if (showInfoSheet) {
        HrvInfoBottomSheet(
            onDismissRequest = { showInfoSheet = false },
            signalQualityStatus = uiState.signalQualityStatus
        )
    }
}

/**
 * Educational instructions card for standardized morning protocol (`REQ-PER-032.2`).
 */
@Composable
private fun WakingProtocolChecklistCard(modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.morning_hrv_instructions_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.morning_hrv_instructions_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Sticky posture and measurement duration selection chips (`REQ-PER-032.1`, `REQ-PER-032.2`).
 */
@Composable
private fun PostureAndDurationCard(
    selectedPosture: HrvPosture,
    selectedDurationSec: Int,
    onPostureSelected: (HrvPosture) -> Unit,
    onDurationSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.morning_hrv_posture_label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = selectedPosture == HrvPosture.SUPINE,
                    onClick = { onPostureSelected(HrvPosture.SUPINE) },
                    label = { Text(stringResource(R.string.morning_hrv_posture_supine)) }
                )
                FilterChip(
                    selected = selectedPosture == HrvPosture.SEATED,
                    onClick = { onPostureSelected(HrvPosture.SEATED) },
                    label = { Text(stringResource(R.string.morning_hrv_posture_seated)) }
                )
                FilterChip(
                    selected = selectedPosture == HrvPosture.STANDING,
                    onClick = { onPostureSelected(HrvPosture.STANDING) },
                    label = { Text(stringResource(R.string.morning_hrv_posture_standing)) }
                )
            }

            Text(
                text = stringResource(R.string.morning_hrv_duration_label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = selectedDurationSec == MorningHrvCheckViewModel.DURATION_STANDARD_SEC,
                    onClick = { onDurationSelected(MorningHrvCheckViewModel.DURATION_STANDARD_SEC) },
                    label = { Text(stringResource(R.string.morning_hrv_duration_60s)) }
                )
                FilterChip(
                    selected = selectedDurationSec == MorningHrvCheckViewModel.DURATION_EXTENDED_SEC,
                    onClick = { onDurationSelected(MorningHrvCheckViewModel.DURATION_EXTENDED_SEC) },
                    label = { Text(stringResource(R.string.morning_hrv_duration_120s)) }
                )
            }
        }
    }
}

/**
 * Pacing card displaying phase indicator, countdown timer, and live heart rate telemetry (`REQ-PER-032.1`, `REQ-PER-032.3`).
 */
@Composable
private fun MeasurementPacingCard(
    uiState: MorningHrvUiState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "HeartbeatTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Phase indicator badge
            val phaseLabel = when (uiState.phase) {
                MorningHrvPhase.READY -> stringResource(R.string.morning_hrv_state_ready)
                MorningHrvPhase.STABILIZING -> stringResource(R.string.morning_hrv_state_stabilizing, uiState.remainingSec)
                MorningHrvPhase.MEASURING -> stringResource(
                    R.string.morning_hrv_state_measuring,
                    uiState.durationSec - uiState.remainingSec,
                    uiState.durationSec
                )
                MorningHrvPhase.COMPLETED -> stringResource(R.string.morning_hrv_state_completed)
            }

            Surface(
                shape = CircleShape,
                color = when (uiState.phase) {
                    MorningHrvPhase.READY -> MaterialTheme.colorScheme.surfaceVariant
                    MorningHrvPhase.STABILIZING -> MaterialTheme.colorScheme.secondaryContainer
                    MorningHrvPhase.MEASURING -> MaterialTheme.colorScheme.primaryContainer
                    MorningHrvPhase.COMPLETED -> TTColor.ReadinessOptimal
                }
            ) {
                Text(
                    text = phaseLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (uiState.phase) {
                        MorningHrvPhase.READY -> MaterialTheme.colorScheme.onSurfaceVariant
                        MorningHrvPhase.STABILIZING -> MaterialTheme.colorScheme.onSecondaryContainer
                        MorningHrvPhase.MEASURING -> MaterialTheme.colorScheme.onPrimaryContainer
                        MorningHrvPhase.COMPLETED -> Color.White
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            // Big Countdown Timer
            if (uiState.phase == MorningHrvPhase.STABILIZING || uiState.phase == MorningHrvPhase.MEASURING) {
                Text(
                    text = "${uiState.remainingSec}s",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Live Heart Rate & Beat counter
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val iconScale = if (uiState.currentHeartRateBpm != null) pulseScale else 1.0f
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = TTColor.HeartRateRed,
                    modifier = Modifier
                        .size(28.dp)
                        .scale(iconScale)
                )

                Column {
                    Text(
                        text = if (uiState.currentHeartRateBpm != null) {
                            "${uiState.currentHeartRateBpm} bpm"
                        } else {
                            "-- bpm"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (uiState.phase == MorningHrvPhase.MEASURING) {
                        Text(
                            text = "${uiState.beatCount} beats",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Amber warning banner displayed when artifact rate exceeds 5.0% (`REQ-PER-032.5`).
 */
@Composable
private fun ArtifactWarningCard(
    artifactRatePct: Double,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = TTColor.ArtifactWarningContainer
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, TTColor.ArtifactWarningBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = TTColor.ArtifactWarningTitle,
                modifier = Modifier.size(24.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.morning_hrv_artifact_warning_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TTColor.ArtifactWarningTitle
                )
                Text(
                    text = stringResource(R.string.morning_hrv_artifact_warning_desc, artifactRatePct),
                    style = MaterialTheme.typography.bodySmall,
                    color = TTColor.ArtifactWarningDesc
                )
            }
        }
    }
}

/**
 * Amber warning banner displayed when completed measurement exhibits firmware smoothing or synthetic quantization (`REQ-TRK-068.3`).
 */
@Composable
private fun SmoothedSignalWarningCard(
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = TTColor.ArtifactWarningContainer
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, TTColor.ArtifactWarningBorder, RoundedCornerShape(12.dp))
            .testTag("morning_hrv_smoothed_warning_card")
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = TTColor.ArtifactWarningTitle,
                modifier = Modifier.size(24.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.morning_hrv_smoothed_warning_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TTColor.ArtifactWarningTitle
                )
                Text(
                    text = stringResource(R.string.morning_hrv_smoothed_warning_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = TTColor.ArtifactWarningDesc
                )
            }
        }
    }
}

/**
 * Result card displaying post-hoc computed resting HRV metrics and SWC readiness classification (`REQ-PER-032.5`).
 */
@Composable
private fun ReadinessResultCard(
    entry: HrvReadinessEntry,
    readinessResult: HrvReadinessResult?,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.morning_hrv_state_completed),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Autonomic Readiness Status Pill
            if (readinessResult != null) {
                val (statusBg, statusFg, statusText) = when (readinessResult.status) {
                    ReadinessStatus.OPTIMAL -> Triple(
                        TTColor.ReadinessOptimal,
                        Color.White,
                        "Optimal Autonomic Readiness"
                    )
                    ReadinessStatus.FATIGUED_LOW -> Triple(
                        TTColor.ReadinessFatigued,
                        Color.White,
                        "Low Readiness (Autonomic Fatigue)"
                    )
                    ReadinessStatus.ABOVE_NORMAL -> Triple(
                        TTColor.ReadinessElevated,
                        Color.White,
                        "Elevated Parasympathetic Activity"
                    )
                    ReadinessStatus.CALIBRATING -> Triple(
                        TTColor.ReadinessCalibrating,
                        Color.White,
                        readinessResult.calibrationProgressMessage
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = statusFg,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 16.dp)
                    )
                }
            }

            // Metrics Summary
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.morning_hrv_summary_rmssd, entry.rmssdMs),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.morning_hrv_summary_ln_rmssd, entry.lnRmssd),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.morning_hrv_summary_resting_hr, entry.restingHrBpm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.sdnnMs != null) {
                    Text(
                        text = String.format(Locale.US, "SDNN: %.1f ms", entry.sdnnMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (entry.artifactRatePct != null) {
                    Text(
                        text = String.format(Locale.US, "Artifact rate: %.1f%%", entry.artifactRatePct),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Action button bar managing lifecycle operations (`REQ-PER-032.1`, `REQ-PER-032.5`).
 */
@Composable
private fun ActionButtonsSection(
    uiState: MorningHrvUiState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        when (uiState.phase) {
            MorningHrvPhase.READY -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.morning_hrv_btn_start),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            MorningHrvPhase.STABILIZING, MorningHrvPhase.MEASURING -> {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.morning_hrv_btn_cancel),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
            MorningHrvPhase.COMPLETED -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDiscard,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.morning_hrv_btn_discard))
                    }

                    Button(
                        onClick = onSave,
                        enabled = !uiState.isSaved,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = if (uiState.isSaved) {
                            ButtonDefaults.buttonColors(
                                containerColor = TTColor.ReadinessOptimal
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        if (uiState.isSaved) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Saved")
                        } else {
                            Text(stringResource(R.string.morning_hrv_btn_save))
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Composable
private fun MorningHrvCheckScreenPreviewLight() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface {
            MeasurementPacingCard(
                uiState = MorningHrvUiState(
                    phase = MorningHrvPhase.READY,
                    currentHeartRateBpm = 54
                )
            )
        }
    }
}

@Preview(name = "Dark Mode", showBackground = true)
@Composable
private fun MorningHrvCheckScreenPreviewDark() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface {
            MeasurementPacingCard(
                uiState = MorningHrvUiState(
                    phase = MorningHrvPhase.MEASURING,
                    remainingSec = 45,
                    currentHeartRateBpm = 52,
                    beatCount = 18
                )
            )
        }
    }
}

@Preview(name = "AMOLED Dark Mode", showBackground = true)
@Composable
private fun MorningHrvCheckScreenPreviewAmoled() {
    ATrainingTrackerTheme(darkTheme = true, amoled = true) {
        Surface {
            ReadinessResultCard(
                entry = HrvReadinessEntry(
                    timestampMs = 1731222000000L,
                    dateIso = "2026-11-10",
                    timezoneId = "UTC",
                    restingHrBpm = 48,
                    rmssdMs = 62.5,
                    lnRmssd = 4.135,
                    hrvScore100 = 82.7,
                    sdnnMs = 74.0,
                    artifactRatePct = 1.2,
                    durationSec = 60,
                    posture = HrvPosture.SUPINE
                ),
                readinessResult = HrvReadinessResult(
                    targetDateIso = "2026-11-10",
                    posture = HrvPosture.SUPINE,
                    status = ReadinessStatus.OPTIMAL,
                    mu7d = 4.12,
                    n7d = 7,
                    mu60d = 4.08,
                    sigma60d = 0.15,
                    n60d = 42,
                    swcLower = 4.005,
                    swcUpper = 4.155,
                    calibrationDaysCount = 42,
                    calibrationProgressMessage = "Calibration complete",
                    todayEntry = null
                )
            )
        }
    }
}
