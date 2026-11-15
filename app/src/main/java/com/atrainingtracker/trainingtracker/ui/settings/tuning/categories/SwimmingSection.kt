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

@file:Suppress("MatchingDeclarationName")

package com.atrainingtracker.trainingtracker.ui.settings.tuning.categories

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.settings.PoolLengthUnit
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSegmentedButtonRow
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningToggleItem
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pool length preset options presented in Swimming & Pool Expert Settings (REQ-SET-107.3).
 */
enum class PoolLengthPreset {
    M_25,
    M_50,
    YD_25,
    CUSTOM
}

/**
 * Maps a persisted pool length and unit to the matching UI preset.
 */
fun resolvePoolLengthPreset(lengthMeters: Float, unit: PoolLengthUnit): PoolLengthPreset {
    return when {
        unit == PoolLengthUnit.YARDS && abs(lengthMeters - TuningPreferencesDefaults.POOL_LENGTH_25YD_METERS) < 0.05f ->
            PoolLengthPreset.YD_25
        unit == PoolLengthUnit.METERS && abs(lengthMeters - TuningPreferencesDefaults.POOL_LENGTH_25M) < 0.05f ->
            PoolLengthPreset.M_25
        unit == PoolLengthUnit.METERS && abs(lengthMeters - TuningPreferencesDefaults.POOL_LENGTH_50M) < 0.05f ->
            PoolLengthPreset.M_50
        else -> PoolLengthPreset.CUSTOM
    }
}

/**
 * Swimming & Pool Expert Settings category composable (REQ-SET-107.3).
 * Allows pre-activating the Swimming sport type for locker/pool sessions and selecting default pool length.
 */
@Composable
fun SwimmingSection(
    isSwimActivated: Boolean,
    onSwimActivatedChange: (Boolean) -> Unit,
    selectedPreset: PoolLengthPreset,
    onPresetSelected: (PoolLengthPreset) -> Unit,
    defaultPoolLengthMeters: Float,
    onCustomPoolLengthMetersChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isActivationToggleEnabled: Boolean = true
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TuningToggleItem(
            title = stringResource(R.string.tuning_swim_activate_title),
            summary = if (isSwimActivated) {
                stringResource(R.string.tuning_swim_activate_active_summary)
            } else {
                stringResource(R.string.tuning_swim_activate_summary)
            },
            isChecked = isSwimActivated,
            onCheckedChange = onSwimActivatedChange,
            enabled = isActivationToggleEnabled
        )

        HorizontalDivider()

        TuningSegmentedButtonRow(
            title = stringResource(R.string.tuning_swim_pool_length_title),
            options = PoolLengthPreset.entries,
            selectedOption = selectedPreset,
            onOptionSelected = onPresetSelected,
            labelProvider = { preset ->
                when (preset) {
                    PoolLengthPreset.M_25 -> stringResource(R.string.tuning_swim_pool_preset_25m)
                    PoolLengthPreset.M_50 -> stringResource(R.string.tuning_swim_pool_preset_50m)
                    PoolLengthPreset.YD_25 -> stringResource(R.string.tuning_swim_pool_preset_25yd)
                    PoolLengthPreset.CUSTOM -> stringResource(R.string.tuning_swim_pool_preset_custom)
                }
            }
        )

        if (selectedPreset == PoolLengthPreset.CUSTOM) {
            val roundedMeters = defaultPoolLengthMeters
                .roundToInt()
                .coerceIn(
                    TuningPreferencesDefaults.MIN_POOL_LENGTH_METERS.roundToInt(),
                    TuningPreferencesDefaults.MAX_POOL_LENGTH_METERS.roundToInt()
                )
            TuningSliderItem(
                title = stringResource(R.string.tuning_swim_pool_preset_custom),
                valueText = "$roundedMeters m",
                helperText = stringResource(R.string.tuning_swim_pool_custom_meters_label, roundedMeters),
                defaultText = "",
                value = roundedMeters.toFloat(),
                onValueChange = { onCustomPoolLengthMetersChange(it.roundToInt().toFloat()) },
                valueRange = TuningPreferencesDefaults.MIN_POOL_LENGTH_METERS..TuningPreferencesDefaults.MAX_POOL_LENGTH_METERS,
                steps = (TuningPreferencesDefaults.MAX_POOL_LENGTH_METERS - TuningPreferencesDefaults.MIN_POOL_LENGTH_METERS).toInt() - 1
            )
        }
    }
}

@Preview(name = "SwimmingSection - Light", showBackground = true)
@Preview(name = "SwimmingSection - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun SwimmingSectionPreview() {
    ATrainingTrackerTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                SwimmingSection(
                    isSwimActivated = true,
                    onSwimActivatedChange = {},
                    selectedPreset = PoolLengthPreset.CUSTOM,
                    onPresetSelected = {},
                    defaultPoolLengthMeters = 33.0f,
                    onCustomPoolLengthMetersChange = {}
                )
            }
        }
    }
}
