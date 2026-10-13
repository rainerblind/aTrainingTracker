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

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem
import java.util.Locale
import kotlin.math.roundToInt

/**
 * AMOLED battery saver & brightness tuning category composable (REQ-UI-262).
 * Encapsulates dimming factors, slope thresholds, wakeup duration, and downward delays.
 */
@Composable
fun AmoledBatterySaverSection(
    fullDimFactor: Float,
    onFullDimChange: (Float) -> Unit,
    mediumDimFactor: Float,
    onMediumDimChange: (Float) -> Unit,
    slopeFlat: Float,
    onSlopeFlatChange: (Float) -> Unit,
    slopeSteep: Float,
    onSlopeSteepChange: (Float) -> Unit,
    wakeupSec: Int,
    onWakeupSecChange: (Int) -> Unit,
    downwardDelaySec: Int,
    onDownwardDelayChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TuningSliderItem(
            title = stringResource(R.string.tuning_full_dim_factor_title),
            valueText = "${(fullDimFactor * 100).roundToInt()}%",
            helperText = stringResource(R.string.tuning_full_dim_factor_desc),
            defaultText = stringResource(R.string.tuning_default_format, "25%"),
            value = fullDimFactor,
            onValueChange = {
                onFullDimChange(it)
                if (it > mediumDimFactor) {
                    onMediumDimChange(it)
                }
            },
            valueRange = TuningPreferencesDefaults.MIN_FULL_DIM_FACTOR..TuningPreferencesDefaults.MAX_FULL_DIM_FACTOR,
            steps = 45
        )

        TuningSliderItem(
            title = stringResource(R.string.tuning_medium_dim_factor_title),
            valueText = "${(mediumDimFactor * 100).roundToInt()}%",
            helperText = stringResource(R.string.tuning_medium_dim_factor_desc),
            defaultText = stringResource(R.string.tuning_default_format, "50%"),
            value = mediumDimFactor,
            onValueChange = {
                onMediumDimChange(it)
                if (it < fullDimFactor) {
                    onFullDimChange(it)
                }
            },
            valueRange = TuningPreferencesDefaults.MIN_MEDIUM_DIM_FACTOR..TuningPreferencesDefaults.MAX_MEDIUM_DIM_FACTOR,
            steps = 70
        )

        TuningSliderItem(
            title = stringResource(R.string.tuning_slope_flat_title),
            valueText = String.format(Locale.getDefault(), "%.1f%%", slopeFlat),
            helperText = stringResource(R.string.tuning_slope_flat_desc),
            defaultText = stringResource(R.string.tuning_default_format, "2.0%"),
            value = slopeFlat,
            onValueChange = {
                onSlopeFlatChange(it)
                if (it > slopeSteep) {
                    onSlopeSteepChange(it)
                }
            },
            valueRange = TuningPreferencesDefaults.MIN_SLOPE_FLAT..TuningPreferencesDefaults.MAX_SLOPE_FLAT,
            steps = 10
        )

        TuningSliderItem(
            title = stringResource(R.string.tuning_slope_steep_title),
            valueText = String.format(Locale.getDefault(), "%.1f%%", slopeSteep),
            helperText = stringResource(R.string.tuning_slope_steep_desc),
            defaultText = stringResource(R.string.tuning_default_format, "5.0%"),
            value = slopeSteep,
            onValueChange = {
                onSlopeSteepChange(it)
                if (it < slopeFlat) {
                    onSlopeFlatChange(it)
                }
            },
            valueRange = TuningPreferencesDefaults.MIN_SLOPE_STEEP..TuningPreferencesDefaults.MAX_SLOPE_STEEP,
            steps = 26
        )

        TuningSliderItem(
            title = stringResource(R.string.tuning_wakeup_duration_title),
            valueText = "$wakeupSec s",
            helperText = stringResource(R.string.tuning_wakeup_duration_desc),
            defaultText = stringResource(R.string.tuning_default_format, "15 s"),
            value = wakeupSec.toFloat(),
            onValueChange = { onWakeupSecChange(it.roundToInt()) },
            valueRange = TuningPreferencesDefaults.MIN_WAKEUP_SEC.toFloat()..TuningPreferencesDefaults.MAX_WAKEUP_SEC.toFloat(),
            steps = 55
        )

        TuningSliderItem(
            title = stringResource(R.string.tuning_downward_delay_title),
            valueText = "$downwardDelaySec s",
            helperText = stringResource(R.string.tuning_downward_delay_desc),
            defaultText = stringResource(R.string.tuning_default_format, "3 s"),
            value = downwardDelaySec.toFloat(),
            onValueChange = { onDownwardDelayChange(it.roundToInt()) },
            valueRange = TuningPreferencesDefaults.MIN_DOWNWARD_DELAY_SEC.toFloat()..TuningPreferencesDefaults.MAX_DOWNWARD_DELAY_SEC.toFloat(),
            steps = 14
        )
    }
}
