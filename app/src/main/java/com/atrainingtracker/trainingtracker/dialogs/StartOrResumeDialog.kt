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

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.DialogFragment
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.helpers.ProcessExitReasonHelper
import com.atrainingtracker.trainingtracker.helpers.ProcessExitReasonHelper.KillDiagnosis
import com.atrainingtracker.trainingtracker.helpers.ProcessExitReasonHelper.KillReason
import com.atrainingtracker.trainingtracker.interfaces.StartOrResumeInterface
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * Modernized recovery dialog that presents forensic process termination diagnosis,
 * progressive escalation on repeat battery kills, and battery optimization settings shortcuts
 * when recovering unfinalized workouts using Material 3 Jetpack Compose (REQ-UI-329, REQ-STB-012, REQ-STB-003, ATT-2948).
 */
class StartOrResumeDialog : DialogFragment() {

    companion object {
        @JvmField
        val TAG: String = StartOrResumeDialog::class.java.name
    }

    internal var startOrResumeInterface: StartOrResumeInterface? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        attachInterface(context)
    }

    internal fun attachInterface(context: Context) {
        if (context is StartOrResumeInterface) {
            startOrResumeInterface = context
        } else {
            throw ClassCastException("$context must implement StartOrResumeInterface")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val context = requireContext()
        val diagnosis = ProcessExitReasonHelper.resolveKillReason(context)

        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ATrainingTrackerTheme {
                    StartOrResumeDialogContent(
                        diagnosis = diagnosis,
                        onStartNew = {
                            startOrResumeInterface?.chooseStart()
                            dismiss()
                        },
                        onResume = {
                            startOrResumeInterface?.chooseResume()
                            dismiss()
                        },
                        onDisableBatteryOptimization = {
                            ProcessExitReasonHelper.openBatteryOptimizationSettings(context)
                            dismiss()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Material 3 Jetpack Compose presentation for workout recovery with contextual kill diagnosis (REQ-UI-329).
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
            .padding(24.dp)
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.action_disable_battery_optimization))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onStartNew) {
                    Text(text = stringResource(R.string.start_new_workout))
                }
                Button(onClick = onResume) {
                    Text(text = stringResource(R.string.resume_workout))
                }
            }
        }
    }
}

/**
 * Composable modal alert dialog wrapper for [StartOrResumeDialogContent].
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
    Dialog(onDismissRequest = onDismissRequest) {
        StartOrResumeDialogContent(
            diagnosis = diagnosis,
            onStartNew = onStartNew,
            onResume = onResume,
            onDisableBatteryOptimization = onDisableBatteryOptimization,
            modifier = modifier
        )
    }
}
