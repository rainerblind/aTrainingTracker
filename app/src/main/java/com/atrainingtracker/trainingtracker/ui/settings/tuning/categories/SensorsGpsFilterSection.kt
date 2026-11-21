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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.trainingtracker.settings.RawSensorRetentionPolicy
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSegmentedButtonRow
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningToggleItem
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Sensors, GPS and filtering tuning category composable (REQ-UI-262, REQ-TRK-018, REQ-SET-080, REQ-SET-113.1).
 * Encapsulates GPS accuracy, altitude filter window, minimum slope speed thresholds,
 * post-workout ERTS smoothing, and HRV RR-interval artifact gate filtering.
 * Auto-pause configuration is located in its own dedicated settings section (ATT-4836).
 */
@Composable
fun SensorsGpsFilterSection(
    gpsAccuracy: Float,
    onGpsAccuracyChange: (Float) -> Unit,
    altitudeWindowSec: Int,
    onAltitudeWindowChange: (Int) -> Unit,
    slopeMinSpeed: Float,
    onSlopeMinSpeedChange: (Float) -> Unit,
    postWorkoutErtsSmoothingEnabled: Boolean = false,
    onPostWorkoutErtsSmoothingChange: (Boolean) -> Unit = {},
    hrvArtifactGateThresholdPercent: Float = TuningPreferencesDefaults.DEFAULT_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT,
    onHrvArtifactGateThresholdPercentChange: (Float) -> Unit = {},
    rawSensorRetentionPolicy: RawSensorRetentionPolicy = TuningPreferencesDefaults.RAW_SENSOR_RETENTION_POLICY,
    onRawSensorRetentionPolicyChange: (RawSensorRetentionPolicy) -> Unit = {},
    recordSmartphoneRawTelemetryEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_RECORD_SMARTPHONE_RAW_TELEMETRY_ENABLED,
    onRecordSmartphoneRawTelemetryChange: (Boolean) -> Unit = {}
) {
    val currentLocale = LocalConfiguration.current.locales[0]
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
                currentLocale,
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

        TuningToggleItem(
            title = stringResource(R.string.tuning_post_workout_erts_smoothing_title),
            isChecked = postWorkoutErtsSmoothingEnabled,
            onCheckedChange = onPostWorkoutErtsSmoothingChange,
            summary = stringResource(R.string.tuning_post_workout_erts_smoothing_desc)
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // HRV RR-interval signal quality / artifact gate threshold (REQ-TRK-061.2, REQ-SET-113.1, ATT-8117)
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
                onHrvArtifactGateThresholdPercentChange(clamped)
            },
            valueRange = TuningPreferencesDefaults.MIN_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT..TuningPreferencesDefaults.MAX_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT,
            steps = 3
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Opt-in smartphone raw sensor sidecar recording (REQ-EXP-045.5, ATT-9348)
        TuningToggleItem(
            title = stringResource(R.string.tuning_record_smartphone_raw_title),
            isChecked = recordSmartphoneRawTelemetryEnabled,
            onCheckedChange = onRecordSmartphoneRawTelemetryChange,
            summary = stringResource(R.string.tuning_record_smartphone_raw_desc)
        )

        // Raw Polar & smartphone IMU / telemetry retention (REQ-SET-117.4, REQ-EXP-045.4, ATT-8019, ATT-9348)
        RawTelemetryRetentionControl(
            policy = rawSensorRetentionPolicy,
            onPolicyChange = onRawSensorRetentionPolicyChange
        )
    }
}

@Composable
private fun RawTelemetryRetentionControl(
    policy: RawSensorRetentionPolicy,
    onPolicyChange: (RawSensorRetentionPolicy) -> Unit
) {
    TuningSegmentedButtonRow(
        title = stringResource(R.string.raw_telemetry_retention_title),
        options = RawSensorRetentionPolicy.entries,
        selectedOption = policy,
        onOptionSelected = onPolicyChange,
        labelProvider = { opt ->
            when (opt) {
                RawSensorRetentionPolicy.DAYS_30 -> stringResource(R.string.raw_telemetry_retention_30_days)
                RawSensorRetentionPolicy.KEEP_ALL -> stringResource(R.string.raw_telemetry_retention_keep_all)
                RawSensorRetentionPolicy.DAYS_7 -> stringResource(R.string.raw_telemetry_retention_7_days)
                RawSensorRetentionPolicy.OFF -> stringResource(R.string.raw_telemetry_retention_off)
            }
        }
    )
}
