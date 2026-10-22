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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.settings.tuning.SubordinateTuningToggleItem
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningToggleItem
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * Physiology & Training Models accordion category in Advanced Settings (REQ-UI-403 / REQ-UI-424).
 *
 * Provides progressive disclosure toggles for 4 training science models:
 * 1. Coggan Power Metrics (NP, IF, TSS, VI) — Default: ON
 *    - Show in Workout Summary subordinate toggle — Default: ON (Rule 59)
 * 2. Performance Management (PMC: ATL, CTL, TSB, ACWR, Ramp Rate) — Default: OFF
 * 3. Aerobic Efficiency & Decoupling (EF, Pw:Hr %, Pa:Hr %) — Default: OFF
 *    - Show in Workout Summary subordinate toggle — Default: ON (Rule 59)
 * 4. Critical Power & Anaerobic Battery (CP, W', W'bal) — Default: OFF
 *    - Show in Workout Summary subordinate toggle — Default: ON (Rule 59)
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
    onShowCriticalPowerInWorkoutSummaryChange: (Boolean) -> Unit = {}
) {
    val cogganCallback = remember(onEnableCogganPowerMetricsChange) { onEnableCogganPowerMetricsChange }
    val showCogganPowerCallback = remember(onShowCogganPowerInWorkoutSummaryChange) { onShowCogganPowerInWorkoutSummaryChange }
    val pmcCallback = remember(onEnablePmcModelChange) { onEnablePmcModelChange }
    val efCallback = remember(onEnableAerobicEfficiencyModelChange) { onEnableAerobicEfficiencyModelChange }
    val showEfCallback = remember(onShowAerobicEfficiencyInWorkoutSummaryChange) { onShowAerobicEfficiencyInWorkoutSummaryChange }
    val cpCallback = remember(onEnableCriticalPowerModelChange) { onEnableCriticalPowerModelChange }
    val showCpCallback = remember(onShowCriticalPowerInWorkoutSummaryChange) { onShowCriticalPowerInWorkoutSummaryChange }

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
    }
}

@Preview(name = "Default (Coggan Only) - Light", showBackground = true)
@Preview(name = "Default (Coggan Only) - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PhysiologyModelsSectionDefaultPreview() {
    ATrainingTrackerTheme {
        Surface {
            PhysiologyModelsSection(
                enableCogganPowerMetrics = true,
                onEnableCogganPowerMetricsChange = {},
                showCogganPowerInWorkoutSummary = true,
                onShowCogganPowerInWorkoutSummaryChange = {},
                enablePmcModel = false,
                onEnablePmcModelChange = {},
                enableAerobicEfficiencyModel = false,
                onEnableAerobicEfficiencyModelChange = {},
                showAerobicEfficiencyInWorkoutSummary = true,
                onShowAerobicEfficiencyInWorkoutSummaryChange = {},
                enableCriticalPowerModel = false,
                onEnableCriticalPowerModelChange = {},
                showCriticalPowerInWorkoutSummary = true,
                onShowCriticalPowerInWorkoutSummaryChange = {}
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
                enableCogganPowerMetrics = true,
                onEnableCogganPowerMetricsChange = {},
                showCogganPowerInWorkoutSummary = true,
                onShowCogganPowerInWorkoutSummaryChange = {},
                enablePmcModel = true,
                onEnablePmcModelChange = {},
                enableAerobicEfficiencyModel = true,
                onEnableAerobicEfficiencyModelChange = {},
                showAerobicEfficiencyInWorkoutSummary = true,
                onShowAerobicEfficiencyInWorkoutSummaryChange = {},
                enableCriticalPowerModel = true,
                onEnableCriticalPowerModelChange = {},
                showCriticalPowerInWorkoutSummary = true,
                onShowCriticalPowerInWorkoutSummaryChange = {}
            )
        }
    }
}
