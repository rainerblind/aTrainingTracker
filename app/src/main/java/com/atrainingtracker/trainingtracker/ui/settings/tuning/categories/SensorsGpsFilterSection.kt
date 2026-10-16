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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningToggleItem
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Sensors, GPS and filtering tuning category composable (REQ-UI-262, REQ-TRK-018).
 * Encapsulates GPS accuracy, altitude filter window, minimum slope speed thresholds,
 * and sport-specific auto-pause configuration.
 */
@Composable
fun SensorsGpsFilterSection(
    gpsAccuracy: Float,
    onGpsAccuracyChange: (Float) -> Unit,
    altitudeWindowSec: Int,
    onAltitudeWindowChange: (Int) -> Unit,
    slopeMinSpeed: Float,
    onSlopeMinSpeedChange: (Float) -> Unit,
    autoPauseBikeEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_ENABLED,
    onAutoPauseBikeEnabledChange: ((Boolean) -> Unit)? = null,
    autoPauseBikeSpeedThreshold: Int = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_SPEED_THRESHOLD_METRIC,
    onAutoPauseBikeSpeedThresholdChange: ((Int) -> Unit)? = null,
    autoPauseBikeDebounceSec: Int = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_DEBOUNCE_SEC,
    onAutoPauseBikeDebounceSecChange: ((Int) -> Unit)? = null,
    autoPauseRunEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_ENABLED,
    onAutoPauseRunEnabledChange: ((Boolean) -> Unit)? = null,
    autoPauseRunSpeedThreshold: Int = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_SPEED_THRESHOLD_METRIC,
    onAutoPauseRunSpeedThresholdChange: ((Int) -> Unit)? = null,
    autoPauseRunDebounceSec: Int = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_DEBOUNCE_SEC,
    onAutoPauseRunDebounceSecChange: ((Int) -> Unit)? = null,
    autoPauseOtherEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_ENABLED,
    onAutoPauseOtherEnabledChange: ((Boolean) -> Unit)? = null,
    autoPauseOtherSpeedThreshold: Int = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_SPEED_THRESHOLD_METRIC,
    onAutoPauseOtherSpeedThresholdChange: ((Int) -> Unit)? = null,
    autoPauseOtherDebounceSec: Int = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_DEBOUNCE_SEC,
    onAutoPauseOtherDebounceSecChange: ((Int) -> Unit)? = null,
    // Deprecated compatibility parameters
    autoPauseSpeedThresholdKmh: Float = autoPauseBikeSpeedThreshold.toFloat(),
    onAutoPauseSpeedThresholdKmhChange: ((Float) -> Unit)? = null,
    autoPauseDebounceSec: Int = autoPauseBikeDebounceSec,
    onAutoPauseDebounceSecChange: ((Int) -> Unit)? = null
) {
    val speedUnit = stringResource(MyHelper.getShortUnitsId(SensorType.SPEED_mps))
    val isMetric = (TrainingApplication.getUnit() == MyUnits.METRIC)
    val bikeDefThreshold = if (isMetric) TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_SPEED_THRESHOLD_METRIC else TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_SPEED_THRESHOLD_IMPERIAL
    val runDefThreshold = if (isMetric) TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_SPEED_THRESHOLD_METRIC else TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_SPEED_THRESHOLD_IMPERIAL
    val otherDefThreshold = if (isMetric) TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_SPEED_THRESHOLD_METRIC else TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_SPEED_THRESHOLD_IMPERIAL

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
            valueText = String.format(
                Locale.getDefault(),
                "%.1f m/s (%s)",
                slopeMinSpeed,
                SpeedFormatter().format_with_units(slopeMinSpeed.toDouble())
            ),
            helperText = stringResource(R.string.tuning_slope_min_speed_desc),
            defaultText = stringResource(R.string.tuning_default_format, "0.5 m/s"),
            value = slopeMinSpeed,
            onValueChange = onSlopeMinSpeedChange,
            valueRange = TuningPreferencesDefaults.MIN_SLOPE_SPEED_MPS..TuningPreferencesDefaults.MAX_SLOPE_SPEED_MPS,
            steps = 18
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.tuning_auto_pause_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.tuning_auto_pause_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // --- Bike Auto-Pause ---
        TuningToggleItem(
            title = stringResource(R.string.tuning_auto_pause_bike_title),
            isChecked = autoPauseBikeEnabled,
            onCheckedChange = { onAutoPauseBikeEnabledChange?.invoke(it) }
        )
        if (autoPauseBikeEnabled) {
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TuningSliderItem(
                    title = stringResource(R.string.tuning_auto_pause_speed_threshold_title),
                    valueText = stringResource(R.string.tuning_speed_value_format, autoPauseBikeSpeedThreshold, speedUnit),
                    helperText = stringResource(R.string.tuning_auto_pause_speed_threshold_desc),
                    defaultText = stringResource(
                        R.string.tuning_default_format,
                        stringResource(R.string.tuning_speed_value_format, bikeDefThreshold, speedUnit)
                    ),
                    value = autoPauseBikeSpeedThreshold.toFloat(),
                    onValueChange = { onAutoPauseBikeSpeedThresholdChange?.invoke(it.roundToInt()) },
                    valueRange = TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD.toFloat()..TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD.toFloat(),
                    steps = 3
                )

                TuningSliderItem(
                    title = stringResource(R.string.tuning_auto_pause_debounce_title),
                    valueText = stringResource(R.string.tuning_seconds_value_format, autoPauseBikeDebounceSec),
                    helperText = stringResource(R.string.tuning_auto_pause_debounce_desc),
                    defaultText = stringResource(
                        R.string.tuning_default_format,
                        stringResource(R.string.tuning_seconds_value_format, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_DEBOUNCE_SEC)
                    ),
                    value = autoPauseBikeDebounceSec.toFloat(),
                    onValueChange = { onAutoPauseBikeDebounceSecChange?.invoke(it.roundToInt()) },
                    valueRange = TuningPreferencesDefaults.MIN_AUTO_PAUSE_BIKE_DEBOUNCE_SEC.toFloat()..TuningPreferencesDefaults.MAX_AUTO_PAUSE_BIKE_DEBOUNCE_SEC.toFloat(),
                    steps = 8
                )
            }
        }

        // --- Run Auto-Pause ---
        TuningToggleItem(
            title = stringResource(R.string.tuning_auto_pause_run_title),
            isChecked = autoPauseRunEnabled,
            onCheckedChange = { onAutoPauseRunEnabledChange?.invoke(it) }
        )
        if (autoPauseRunEnabled) {
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TuningSliderItem(
                    title = stringResource(R.string.tuning_auto_pause_speed_threshold_title),
                    valueText = stringResource(R.string.tuning_speed_value_format, autoPauseRunSpeedThreshold, speedUnit),
                    helperText = stringResource(R.string.tuning_auto_pause_speed_threshold_desc),
                    defaultText = stringResource(
                        R.string.tuning_default_format,
                        stringResource(R.string.tuning_speed_value_format, runDefThreshold, speedUnit)
                    ),
                    value = autoPauseRunSpeedThreshold.toFloat(),
                    onValueChange = { onAutoPauseRunSpeedThresholdChange?.invoke(it.roundToInt()) },
                    valueRange = TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD.toFloat()..TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD.toFloat(),
                    steps = 3
                )

                TuningSliderItem(
                    title = stringResource(R.string.tuning_auto_pause_debounce_title),
                    valueText = stringResource(R.string.tuning_seconds_value_format, autoPauseRunDebounceSec),
                    helperText = stringResource(R.string.tuning_auto_pause_debounce_desc),
                    defaultText = stringResource(
                        R.string.tuning_default_format,
                        stringResource(R.string.tuning_seconds_value_format, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_DEBOUNCE_SEC)
                    ),
                    value = autoPauseRunDebounceSec.toFloat(),
                    onValueChange = { onAutoPauseRunDebounceSecChange?.invoke(it.roundToInt()) },
                    valueRange = TuningPreferencesDefaults.MIN_AUTO_PAUSE_RUN_DEBOUNCE_SEC.toFloat()..TuningPreferencesDefaults.MAX_AUTO_PAUSE_RUN_DEBOUNCE_SEC.toFloat(),
                    steps = 18
                )
            }
        }

        // --- Other Auto-Pause ---
        TuningToggleItem(
            title = stringResource(R.string.tuning_auto_pause_other_title),
            isChecked = autoPauseOtherEnabled,
            onCheckedChange = { onAutoPauseOtherEnabledChange?.invoke(it) }
        )
        if (autoPauseOtherEnabled) {
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TuningSliderItem(
                    title = stringResource(R.string.tuning_auto_pause_speed_threshold_title),
                    valueText = stringResource(R.string.tuning_speed_value_format, autoPauseOtherSpeedThreshold, speedUnit),
                    helperText = stringResource(R.string.tuning_auto_pause_speed_threshold_desc),
                    defaultText = stringResource(
                        R.string.tuning_default_format,
                        stringResource(R.string.tuning_speed_value_format, otherDefThreshold, speedUnit)
                    ),
                    value = autoPauseOtherSpeedThreshold.toFloat(),
                    onValueChange = { onAutoPauseOtherSpeedThresholdChange?.invoke(it.roundToInt()) },
                    valueRange = TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD.toFloat()..TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD.toFloat(),
                    steps = 3
                )

                TuningSliderItem(
                    title = stringResource(R.string.tuning_auto_pause_debounce_title),
                    valueText = stringResource(R.string.tuning_seconds_value_format, autoPauseOtherDebounceSec),
                    helperText = stringResource(R.string.tuning_auto_pause_debounce_desc),
                    defaultText = stringResource(
                        R.string.tuning_default_format,
                        stringResource(R.string.tuning_seconds_value_format, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_DEBOUNCE_SEC)
                    ),
                    value = autoPauseOtherDebounceSec.toFloat(),
                    onValueChange = { onAutoPauseOtherDebounceSecChange?.invoke(it.roundToInt()) },
                    valueRange = TuningPreferencesDefaults.MIN_AUTO_PAUSE_OTHER_DEBOUNCE_SEC.toFloat()..TuningPreferencesDefaults.MAX_AUTO_PAUSE_OTHER_DEBOUNCE_SEC.toFloat(),
                    steps = 58
                )
            }
        }
    }
}

