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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.settings.tuning.SubordinateTuningToggleItem
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningToggleItem
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import java.util.Locale
import kotlin.math.round

/**
 * Morning Checks & Wellness accordion category in Advanced Settings (REQ-SET-113.2, REQ-UI-525.1, REQ-HRV-038, ATT-8117).
 *
 * Bundles the 7 morning readiness check settings:
 * 1. Morning Sleep & Wellness tracking toggle + 4 subordinate dimension toggles
 *    (sleep duration & quality, morning energy, muscle soreness, context tags)
 * 2. Morning HRV Check shortcut toggle on the Start Tracking screen + cutoff hour slider (06:00..23:00)
 */
@Composable
fun MorningCheckSection(
    enableMorningSleepAndWellness: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_MORNING_SLEEP_AND_WELLNESS,
    onEnableMorningSleepAndWellnessChange: (Boolean) -> Unit = {},
    enableSleepDurationAndQuality: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_SLEEP_DURATION_AND_QUALITY,
    onEnableSleepDurationAndQualityChange: (Boolean) -> Unit = {},
    enableMorningEnergy: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_MORNING_ENERGY,
    onEnableMorningEnergyChange: (Boolean) -> Unit = {},
    enableMuscleSoreness: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_MUSCLE_SORENESS,
    onEnableMuscleSorenessChange: (Boolean) -> Unit = {},
    enableContextTags: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_CONTEXT_TAGS,
    onEnableContextTagsChange: (Boolean) -> Unit = {},
    showMorningCheckOnStartScreen: Boolean = TuningPreferencesDefaults.DEFAULT_SHOW_MORNING_CHECK_ON_START_SCREEN,
    onShowMorningCheckOnStartScreenChange: (Boolean) -> Unit = {},
    morningCheckCutoffHour: Int = TuningPreferencesDefaults.DEFAULT_MORNING_CHECK_CUTOFF_HOUR,
    onMorningCheckCutoffHourChange: (Int) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Morning Sleep & Wellness Tracking (REQ-HRV-038)
        TuningToggleItem(
            title = stringResource(R.string.tuning_morning_sleep_wellness_title),
            isChecked = enableMorningSleepAndWellness,
            onCheckedChange = onEnableMorningSleepAndWellnessChange,
            summary = stringResource(R.string.tuning_morning_sleep_wellness_desc)
        )
        if (enableMorningSleepAndWellness) {
            SubordinateTuningToggleItem(
                title = stringResource(R.string.tuning_morning_sleep_duration_quality_title),
                isChecked = enableSleepDurationAndQuality,
                onCheckedChange = onEnableSleepDurationAndQualityChange
            )
            SubordinateTuningToggleItem(
                title = stringResource(R.string.tuning_morning_energy_title),
                isChecked = enableMorningEnergy,
                onCheckedChange = onEnableMorningEnergyChange
            )
            SubordinateTuningToggleItem(
                title = stringResource(R.string.tuning_morning_muscle_soreness_title),
                isChecked = enableMuscleSoreness,
                onCheckedChange = onEnableMuscleSorenessChange
            )
            SubordinateTuningToggleItem(
                title = stringResource(R.string.tuning_morning_context_tags_title),
                isChecked = enableContextTags,
                onCheckedChange = onEnableContextTagsChange
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Morning HRV Check on Start Screen (REQ-UI-525.1)
        TuningToggleItem(
            title = stringResource(R.string.tuning_show_morning_check_on_start_screen_title),
            isChecked = showMorningCheckOnStartScreen,
            onCheckedChange = onShowMorningCheckOnStartScreenChange,
            summary = stringResource(R.string.tuning_show_morning_check_on_start_screen_desc)
        )
        if (showMorningCheckOnStartScreen) {
            TuningSliderItem(
                title = stringResource(R.string.tuning_morning_check_cutoff_hour_title),
                valueText = String.format(Locale.US, "%02d:00", morningCheckCutoffHour),
                helperText = stringResource(R.string.tuning_morning_check_cutoff_hour_desc, morningCheckCutoffHour),
                defaultText = String.format(Locale.US, "%02d:00", TuningPreferencesDefaults.DEFAULT_MORNING_CHECK_CUTOFF_HOUR),
                value = morningCheckCutoffHour.toFloat(),
                onValueChange = { hourFloat ->
                    onMorningCheckCutoffHourChange(
                        round(hourFloat).toInt().coerceIn(
                            TuningPreferencesDefaults.MIN_MORNING_CHECK_CUTOFF_HOUR,
                            TuningPreferencesDefaults.MAX_MORNING_CHECK_CUTOFF_HOUR
                        )
                    )
                },
                valueRange = TuningPreferencesDefaults.MIN_MORNING_CHECK_CUTOFF_HOUR.toFloat()..TuningPreferencesDefaults.MAX_MORNING_CHECK_CUTOFF_HOUR.toFloat(),
                steps = TuningPreferencesDefaults.MAX_MORNING_CHECK_CUTOFF_HOUR - TuningPreferencesDefaults.MIN_MORNING_CHECK_CUTOFF_HOUR - 1
            )
        }
    }
}

@Preview(name = "Default - Light", showBackground = true)
@Preview(name = "Expanded - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun MorningCheckSectionPreview() {
    ATrainingTrackerTheme {
        Surface {
            MorningCheckSection(
                enableMorningSleepAndWellness = true,
                showMorningCheckOnStartScreen = true,
                morningCheckCutoffHour = 10
            )
        }
    }
}
