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
 * Sensors, GPS and filtering tuning category composable (REQ-UI-262).
 * Encapsulates GPS accuracy, altitude filter window, and minimum slope speed thresholds.
 */
@Composable
fun SensorsGpsFilterSection(
    gpsAccuracy: Float,
    onGpsAccuracyChange: (Float) -> Unit,
    altitudeWindowSec: Int,
    onAltitudeWindowChange: (Int) -> Unit,
    slopeMinSpeed: Float,
    onSlopeMinSpeedChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TuningSliderItem(
            title = stringResource(R.string.tuning_gps_accuracy_title),
            valueText = "${gpsAccuracy.roundToInt()} m",
            helperText = stringResource(R.string.tuning_gps_accuracy_desc),
            defaultText = stringResource(R.string.tuning_default_format, "200 m"),
            value = gpsAccuracy,
            onValueChange = onGpsAccuracyChange,
            valueRange = TuningPreferencesDefaults.MIN_GPS_ACCURACY_M..TuningPreferencesDefaults.MAX_GPS_ACCURACY_M,
            steps = 49
        )

        TuningSliderItem(
            title = stringResource(R.string.tuning_altitude_window_title),
            valueText = "$altitudeWindowSec s",
            helperText = stringResource(R.string.tuning_altitude_window_desc),
            defaultText = stringResource(R.string.tuning_default_format, "21 s"),
            value = altitudeWindowSec.toFloat(),
            onValueChange = { onAltitudeWindowChange(it.roundToInt()) },
            valueRange = TuningPreferencesDefaults.MIN_ALTITUDE_WINDOW_SEC.toFloat()..TuningPreferencesDefaults.MAX_ALTITUDE_WINDOW_SEC.toFloat(),
            steps = 55
        )

        TuningSliderItem(
            title = stringResource(R.string.tuning_slope_min_speed_title),
            valueText = String.format(Locale.getDefault(), "%.1f m/s (%.1f km/h)", slopeMinSpeed, slopeMinSpeed * 3.6f),
            helperText = stringResource(R.string.tuning_slope_min_speed_desc),
            defaultText = stringResource(R.string.tuning_default_format, "0.5 m/s"),
            value = slopeMinSpeed,
            onValueChange = onSlopeMinSpeedChange,
            valueRange = TuningPreferencesDefaults.MIN_SLOPE_SPEED_MPS..TuningPreferencesDefaults.MAX_SLOPE_SPEED_MPS,
            steps = 18
        )
    }
}
