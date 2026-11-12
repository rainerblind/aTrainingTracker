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

package com.atrainingtracker.trainingtracker.ui.settings.tuning.categories

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.dialogs.PowerHrDynamicsInfoDialog
import com.atrainingtracker.trainingtracker.ui.settings.tuning.SubordinateTuningToggleItem
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningToggleItem
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import java.util.Locale

/**
 * Physiology & Training Models accordion category in Advanced Settings (REQ-UI-403 / REQ-UI-424 / REQ-REV-009).
 *
 * Provides progressive disclosure toggles for 5 training science models:
 * 1. Coggan Power Metrics (NP, IF, TSS, VI) — Default: ON
 *    - Show in Workout Summary subordinate toggle — Default: ON (Rule 59)
 * 2. Performance Management (PMC: ATL, CTL, TSB, ACWR, Ramp Rate) — Default: OFF
 * 3. Aerobic Efficiency & Decoupling (EF, Pw:Hr %, Pa:Hr %) — Default: OFF
 *    - Show in Workout Summary subordinate toggle — Default: ON (Rule 59)
 * 4. Critical Power & Anaerobic Battery (CP, W', W'bal) — Default: OFF
 *    - Show in Workout Summary subordinate toggle — Default: ON (Rule 59)
 * 5. Cardio-Power Dynamics (HR@200W, \u03c4 & Model Drift) — Default: OFF (REQ-REV-009 / ATT-3904)
 *    - Show in Workout Summary subordinate toggle — Default: ON
 */
@Composable
fun PhysiologyModelsSection(
    enableCogganPowerMetrics: Boolean,
    onEnableCogganPowerMetricsChange: (Boolean) -> Unit,
    showCogganPowerInWorkoutSummary: Boolean = true,
    onShowCogganPowerInWorkoutSummaryChange: (Boolean) -> Unit = {},
    enablePmcModel: Boolean,
    onEnablePmcModelChange: (Boolean) -> Unit,
    enableAerobicEfficiencyModel: Boolean,
    onEnableAerobicEfficiencyModelChange: (Boolean) -> Unit,
    showAerobicEfficiencyInWorkoutSummary: Boolean = true,
    onShowAerobicEfficiencyInWorkoutSummaryChange: (Boolean) -> Unit = {},
    enableCriticalPowerModel: Boolean,
    onEnableCriticalPowerModelChange: (Boolean) -> Unit,
    showCriticalPowerInWorkoutSummary: Boolean = true,
    onShowCriticalPowerInWorkoutSummaryChange: (Boolean) -> Unit = {},
    enableCardioPowerDynamics: Boolean = false,
    onEnableCardioPowerDynamicsChange: (Boolean) -> Unit = {},
    showCardioPowerInWorkoutSummary: Boolean = true,
    onShowCardioPowerInWorkoutSummaryChange: (Boolean) -> Unit = {},
    onCardioPowerInfoClick: (() -> Unit)? = null,
    dfaA1ThresholdT1: Float = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T1,
    onDfaA1ThresholdT1Change: (Float) -> Unit = {},
    dfaA1ThresholdT2: Float = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2,
    onDfaA1ThresholdT2Change: (Float) -> Unit = {},
    hrvArtifactGateThresholdPercent: Float = TuningPreferencesDefaults.DEFAULT_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT,
    onHrvArtifactGateThresholdPercentChange: (Float) -> Unit = {},
    enableMorningSleepAndWellness: Boolean = false,
    onEnableMorningSleepAndWellnessChange: (Boolean) -> Unit = {},
    enableSleepDurationAndQuality: Boolean = true,
    onEnableSleepDurationAndQualityChange: (Boolean) -> Unit = {},
    enableMorningEnergy: Boolean = true,
    onEnableMorningEnergyChange: (Boolean) -> Unit = {},
    enableMuscleSoreness: Boolean = false,
    onEnableMuscleSorenessChange: (Boolean) -> Unit = {},
    enableContextTags: Boolean = false,
    onEnableContextTagsChange: (Boolean) -> Unit = {},
    showRpeAndFeel: Boolean = false,
    onShowRpeAndFeelChange: (Boolean) -> Unit = {}
) {
    var showInternalInfoDialog by remember { mutableStateOf(false) }

    val cogganCallback = remember(onEnableCogganPowerMetricsChange) { onEnableCogganPowerMetricsChange }
    val showCogganPowerCallback = remember(onShowCogganPowerInWorkoutSummaryChange) { onShowCogganPowerInWorkoutSummaryChange }
    val pmcCallback = remember(onEnablePmcModelChange) { onEnablePmcModelChange }
    val efCallback = remember(onEnableAerobicEfficiencyModelChange) { onEnableAerobicEfficiencyModelChange }
    val showEfCallback = remember(onShowAerobicEfficiencyInWorkoutSummaryChange) { onShowAerobicEfficiencyInWorkoutSummaryChange }
    val cpCallback = remember(onEnableCriticalPowerModelChange) { onEnableCriticalPowerModelChange }
    val showCpCallback = remember(onShowCriticalPowerInWorkoutSummaryChange) { onShowCriticalPowerInWorkoutSummaryChange }
    val cardioPowerCallback = remember(onEnableCardioPowerDynamicsChange) { onEnableCardioPowerDynamicsChange }
    val showCardioPowerCallback = remember(onShowCardioPowerInWorkoutSummaryChange) { onShowCardioPowerInWorkoutSummaryChange }
    val dfaT1Callback = remember(onDfaA1ThresholdT1Change) { onDfaA1ThresholdT1Change }
    val dfaT2Callback = remember(onDfaA1ThresholdT2Change) { onDfaA1ThresholdT2Change }
    val hrvArtifactGateCallback = remember(onHrvArtifactGateThresholdPercentChange) { onHrvArtifactGateThresholdPercentChange }
    val morningSleepAndWellnessCallback = remember(onEnableMorningSleepAndWellnessChange) { onEnableMorningSleepAndWellnessChange }
    val sleepDurationAndQualityCallback = remember(onEnableSleepDurationAndQualityChange) { onEnableSleepDurationAndQualityChange }
    val morningEnergyCallback = remember(onEnableMorningEnergyChange) { onEnableMorningEnergyChange }
    val muscleSorenessCallback = remember(onEnableMuscleSorenessChange) { onEnableMuscleSorenessChange }
    val contextTagsCallback = remember(onEnableContextTagsChange) { onEnableContextTagsChange }
    val rpeAndFeelCallback = remember(onShowRpeAndFeelChange) { onShowRpeAndFeelChange }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Model 1: Coggan Power Metrics
        TuningToggleItem(
            title = stringResource(R.string.tuning_physiology_coggan_title),
            isChecked = enableCogganPowerMetrics,
            onCheckedChange = cogganCallback,
            summary = stringResource(R.string.tuning_physiology_coggan_desc)
        )
        if (enableCogganPowerMetrics) {
            SubordinateTuningToggleItem(
                title = stringResource(R.string.tuning_physiology_show_in_workout_summary),
                isChecked = showCogganPowerInWorkoutSummary,
                onCheckedChange = showCogganPowerCallback
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Model 2: PMC
        TuningToggleItem(
            title = stringResource(R.string.tuning_physiology_pmc_title),
            isChecked = enablePmcModel,
            onCheckedChange = pmcCallback,
            summary = stringResource(R.string.tuning_physiology_pmc_desc)
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Model 3: Aerobic Efficiency & Decoupling
        TuningToggleItem(
            title = stringResource(R.string.tuning_physiology_ef_title),
            isChecked = enableAerobicEfficiencyModel,
            onCheckedChange = efCallback,
            summary = stringResource(R.string.tuning_physiology_ef_desc)
        )
        if (enableAerobicEfficiencyModel) {
            SubordinateTuningToggleItem(
                title = stringResource(R.string.tuning_physiology_show_in_workout_summary),
                isChecked = showAerobicEfficiencyInWorkoutSummary,
                onCheckedChange = showEfCallback
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Model 4: Critical Power & Anaerobic Battery
        TuningToggleItem(
            title = stringResource(R.string.tuning_physiology_cp_title),
            isChecked = enableCriticalPowerModel,
            onCheckedChange = cpCallback,
            summary = stringResource(R.string.tuning_physiology_cp_desc)
        )
        if (enableCriticalPowerModel) {
            SubordinateTuningToggleItem(
                title = stringResource(R.string.tuning_physiology_show_in_workout_summary),
                isChecked = showCriticalPowerInWorkoutSummary,
                onCheckedChange = showCpCallback
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Model 5: Cardio-Power Dynamics (HR@200W, \u03c4 & Model Drift)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                TuningToggleItem(
                    title = stringResource(R.string.tuning_physiology_cardio_power_title),
                    isChecked = enableCardioPowerDynamics,
                    onCheckedChange = cardioPowerCallback,
                    summary = stringResource(R.string.tuning_physiology_cardio_power_desc)
                )
            }
            IconButton(
                onClick = {
                    if (onCardioPowerInfoClick != null) {
                        onCardioPowerInfoClick()
                    } else {
                        showInternalInfoDialog = true
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = stringResource(R.string.cardio_power_info_title),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (enableCardioPowerDynamics) {
            SubordinateTuningToggleItem(
                title = stringResource(R.string.tuning_physiology_show_in_workout_summary),
                isChecked = showCardioPowerInWorkoutSummary,
                onCheckedChange = showCardioPowerCallback
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Model 6: HRV DFA a1 Aerobic & Anaerobic Thresholds (REQ-TRK-061.4)
        TuningSliderItem(
            title = stringResource(R.string.tuning_dfa_a1_t1_title),
            valueText = String.format(Locale.US, "%.2f", dfaA1ThresholdT1),
            helperText = stringResource(R.string.tuning_dfa_a1_t1_desc),
            defaultText = stringResource(R.string.tuning_dfa_a1_default, TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T1),
            value = dfaA1ThresholdT1,
            onValueChange = { newValue ->
                val rounded = Math.round(newValue * 100f) / 100f
                val clamped = rounded.coerceIn(
                    dfaA1ThresholdT2 + TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD_GAP,
                    TuningPreferencesDefaults.MAX_DFA_A1_THRESHOLD
                )
                dfaT1Callback(clamped)
            },
            valueRange = 0.60f..1.20f,
            steps = 11
        )

        TuningSliderItem(
            title = stringResource(R.string.tuning_dfa_a1_t2_title),
            valueText = String.format(Locale.US, "%.2f", dfaA1ThresholdT2),
            helperText = stringResource(R.string.tuning_dfa_a1_t2_desc),
            defaultText = stringResource(R.string.tuning_dfa_a1_default, TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2),
            value = dfaA1ThresholdT2,
            onValueChange = { newValue ->
                val rounded = Math.round(newValue * 100f) / 100f
                val clamped = rounded.coerceIn(
                    TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD,
                    dfaA1ThresholdT1 - TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD_GAP
                )
                dfaT2Callback(clamped)
            },
            valueRange = 0.30f..0.80f,
            steps = 9
        )

        TuningSliderItem(
            title = stringResource(R.string.tuning_hrv_artifact_gate_title),
            valueText = String.format(Locale.US, "%.1f%%", hrvArtifactGateThresholdPercent),
            helperText = stringResource(R.string.tuning_hrv_artifact_gate_desc),
            defaultText = stringResource(
                R.string.tuning_hrv_artifact_gate_default,
                TuningPreferencesDefaults.DEFAULT_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT
            ),
            value = hrvArtifactGateThresholdPercent,
            onValueChange = { newValue ->
                val rounded = Math.round(newValue * 10f) / 10f
                val clamped = rounded.coerceIn(
                    TuningPreferencesDefaults.MIN_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT,
                    TuningPreferencesDefaults.MAX_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT
                )
                hrvArtifactGateCallback(clamped)
            },
            valueRange = 3.0f..5.0f,
            steps = 3
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Model 7: Morning Sleep & Wellness Tracking (REQ-HRV-038)
        TuningToggleItem(
            title = stringResource(R.string.tuning_morning_sleep_wellness_title),
            isChecked = enableMorningSleepAndWellness,
            onCheckedChange = morningSleepAndWellnessCallback,
            summary = stringResource(R.string.tuning_morning_sleep_wellness_desc)
        )
        if (enableMorningSleepAndWellness) {
            SubordinateTuningToggleItem(
                title = stringResource(R.string.tuning_morning_sleep_duration_quality_title),
                isChecked = enableSleepDurationAndQuality,
                onCheckedChange = sleepDurationAndQualityCallback
            )
            SubordinateTuningToggleItem(
                title = stringResource(R.string.tuning_morning_energy_title),
                isChecked = enableMorningEnergy,
                onCheckedChange = morningEnergyCallback
            )
            SubordinateTuningToggleItem(
                title = stringResource(R.string.tuning_morning_muscle_soreness_title),
                isChecked = enableMuscleSoreness,
                onCheckedChange = muscleSorenessCallback
            )
            SubordinateTuningToggleItem(
                title = stringResource(R.string.tuning_morning_context_tags_title),
                isChecked = enableContextTags,
                onCheckedChange = contextTagsCallback
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Model 8: Perceived Exertion (RPE) & Feel (REQ-WKO-036)
        TuningToggleItem(
            title = stringResource(R.string.settings_workout_card_rpe_feel),
            isChecked = showRpeAndFeel,
            onCheckedChange = rpeAndFeelCallback,
            summary = stringResource(R.string.tuning_rpe_feel_desc)
        )
    }

    if (showInternalInfoDialog) {
        PowerHrDynamicsInfoDialog(
            onDismissRequest = { showInternalInfoDialog = false }
        )
    }
}

@Preview(name = "Default (Coggan Only) - Light", showBackground = true)
@Preview(name = "Default (Coggan Only) - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PhysiologyModelsSectionDefaultPreview() {
    ATrainingTrackerTheme {
        Surface {
            PhysiologyModelsSection(
                enableCogganPowerMetrics = true, onEnableCogganPowerMetricsChange = {},
                enablePmcModel = false, onEnablePmcModelChange = {},
                enableAerobicEfficiencyModel = false, onEnableAerobicEfficiencyModelChange = {},
                enableCriticalPowerModel = false, onEnableCriticalPowerModelChange = {}
            )
        }
    }
}

@Preview(name = "All Enabled - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PhysiologyModelsSectionAllEnabledPreview() {
    ATrainingTrackerTheme {
        Surface {
            PhysiologyModelsSection(
                enableCogganPowerMetrics = true, onEnableCogganPowerMetricsChange = {},
                enablePmcModel = true, onEnablePmcModelChange = {},
                enableAerobicEfficiencyModel = true, onEnableAerobicEfficiencyModelChange = {},
                enableCriticalPowerModel = true, onEnableCriticalPowerModelChange = {},
                enableCardioPowerDynamics = true, onEnableCardioPowerDynamicsChange = {},
                enableMorningSleepAndWellness = true, onEnableMorningSleepAndWellnessChange = {},
                showRpeAndFeel = true, onShowRpeAndFeelChange = {}
            )
        }
    }
}
