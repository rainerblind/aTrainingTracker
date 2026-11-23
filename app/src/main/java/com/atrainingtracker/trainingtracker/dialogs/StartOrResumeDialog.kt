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

package com.atrainingtracker.trainingtracker.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.helpers.ProcessExitReasonHelper.KillDiagnosis
import com.atrainingtracker.trainingtracker.helpers.ProcessExitReasonHelper.KillReason
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * Material 3 Jetpack Compose presentation for workout recovery with contextual kill diagnosis
 * (REQ-UI-329, REQ-STB-012, REQ-STB-003, REQ-UI-2801.3 / ATT-2948, ATT-8129).
 */
@Composable
fun StartOrResumeDialogContent(
    diagnosis: KillDiagnosis,
    onStartNew: () -> Unit,
    onResume: () -> Unit,
    onDisableBatteryOptimization: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, iconTint, titleRes) = when (diagnosis.reason) {
        KillReason.BATTERY_KILL -> Triple(
            Icons.Default.BatteryAlert,
            MaterialTheme.colorScheme.error,
            R.string.unfinished_workout_title_battery
        )
        KillReason.LOW_MEMORY -> Triple(
            Icons.Default.Memory,
            MaterialTheme.colorScheme.primary,
            R.string.unfinished_workout_title_memory
        )
        KillReason.PERMISSION_REVOKED -> Triple(
            Icons.Default.Security,
            MaterialTheme.colorScheme.error,
            R.string.unfinished_workout_title_permission
        )
        KillReason.GENERIC_UNFINISHED -> Triple(
            Icons.Default.DirectionsRun,
            MaterialTheme.colorScheme.primary,
            R.string.unfinished_workout_title
        )
    }

    val explanation = buildString {
        when (diagnosis.reason) {
            KillReason.BATTERY_KILL -> {
                when (diagnosis.escalationLevel) {
                    1 -> {
                        append(stringResource(R.string.kill_reason_battery_stage1))
                        if (diagnosis.isStravaActive) {
                            append("\n\n")
                            append(stringResource(R.string.kill_reason_battery_stage1_strava))
                        }
                    }
                    2 -> append(stringResource(R.string.kill_reason_battery_stage2))
                    else -> append(stringResource(R.string.kill_reason_battery_stage3))
                }
            }
            KillReason.LOW_MEMORY -> append(stringResource(R.string.kill_reason_low_memory))
            KillReason.PERMISSION_REVOKED -> append(stringResource(R.string.kill_reason_permission_revoked))
            KillReason.GENERIC_UNFINISHED -> { /* No explanation box */ }
        }
    }

    Surface(
        shape = AlertDialogDefaults.shape,
        color = AlertDialogDefaults.containerColor,
        tonalElevation = AlertDialogDefaults.TonalElevation,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = iconTint
            )

            Text(
                text = stringResource(id = titleRes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            if (diagnosis.reason != KillReason.GENERIC_UNFINISHED && explanation.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.start_or_resume_dialog_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            if (diagnosis.shouldShowBatteryButton) {
                FilledTonalButton(
                    onClick = onDisableBatteryOptimization,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_disable_battery_optimization),
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.resume_workout),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onStartNew,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.start_new_workout),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Pure Compose modal alert dialog wrapper for [StartOrResumeDialogContent] (REQ-UI-2801.3).
 *
 * Uses `usePlatformDefaultWidth = false` paired with `widthIn(max = 420.dp)` in
 * [StartOrResumeDialogContent] to preserve full-width phone ergonomics (ATT-3165).
 */
@Composable
fun StartOrResumeAlertDialog(
    diagnosis: KillDiagnosis,
    onDismissRequest: () -> Unit,
    onStartNew: () -> Unit,
    onResume: () -> Unit,
    onDisableBatteryOptimization: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        StartOrResumeDialogContent(
            diagnosis = diagnosis,
            onStartNew = onStartNew,
            onResume = onResume,
            onDisableBatteryOptimization = onDisableBatteryOptimization,
            modifier = modifier
        )
    }
}

@Preview(name = "StartOrResumeDialog Battery Light", showBackground = true)
@Composable
private fun StartOrResumeDialogBatteryLightPreview() {
    ATrainingTrackerTheme(darkTheme = false) {
        StartOrResumeDialogContent(
            diagnosis = KillDiagnosis(
                reason = KillReason.BATTERY_KILL,
                escalationLevel = 1,
                isStravaActive = false,
                shouldShowBatteryButton = true
            ),
            onStartNew = {},
            onResume = {},
            onDisableBatteryOptimization = {}
        )
    }
}

@Preview(name = "StartOrResumeDialog Generic Dark", showBackground = true)
@Composable
private fun StartOrResumeDialogGenericDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true) {
        StartOrResumeDialogContent(
            diagnosis = KillDiagnosis(
                reason = KillReason.GENERIC_UNFINISHED,
                escalationLevel = 0,
                isStravaActive = false,
                shouldShowBatteryButton = false
            ),
            onStartNew = {},
            onResume = {},
            onDisableBatteryOptimization = {}
        )
    }
}
