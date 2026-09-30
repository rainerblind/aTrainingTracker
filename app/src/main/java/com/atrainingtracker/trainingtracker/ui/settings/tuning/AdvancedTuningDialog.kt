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

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.components.core.AppBottomSheetContent
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AdvancedTuningDialog(
    onDismiss: () -> Unit,
    onSettingsChanged: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tuningDataStore = remember { TuningPreferencesDataStore(context) }
    val persistedConfig by tuningDataStore.tuningConfigFlow.collectAsState(initial = TuningConfig())

    var fullDimFactor by remember { mutableFloatStateOf(TuningPreferencesDefaults.FULL_DIM_FACTOR) }
    var mediumDimFactor by remember { mutableFloatStateOf(TuningPreferencesDefaults.MEDIUM_DIM_FACTOR) }
    var slopeFlat by remember { mutableFloatStateOf(TuningPreferencesDefaults.SLOPE_FLAT_THRESHOLD) }
    var slopeSteep by remember { mutableFloatStateOf(TuningPreferencesDefaults.SLOPE_STEEP_THRESHOLD) }
    var wakeupSec by remember { mutableIntStateOf(TuningPreferencesDefaults.WAKEUP_DURATION_SEC) }
    var downwardDelaySec by remember { mutableIntStateOf(TuningPreferencesDefaults.DOWNWARD_DELAY_SEC) }
    var gpsAccuracy by remember { mutableFloatStateOf(TuningPreferencesDefaults.GPS_ACCURACY_THRESHOLD_M) }
    var altitudeWindowSec by remember { mutableIntStateOf(TuningPreferencesDefaults.ALTITUDE_FILTER_WINDOW_SEC) }
    var slopeMinSpeed by remember { mutableFloatStateOf(TuningPreferencesDefaults.SLOPE_MIN_SPEED_MPS) }

    LaunchedEffect(persistedConfig) {
        fullDimFactor = persistedConfig.fullDimFactor
        mediumDimFactor = persistedConfig.mediumDimFactor
        slopeFlat = persistedConfig.slopeFlatThreshold
        slopeSteep = persistedConfig.slopeSteepThreshold
        wakeupSec = persistedConfig.wakeupDurationSec
        downwardDelaySec = persistedConfig.downwardDelaySec
        gpsAccuracy = persistedConfig.gpsAccuracyThresholdMeters
        altitudeWindowSec = persistedConfig.altitudeFilterWindowSec
        slopeMinSpeed = persistedConfig.slopeMinSpeedMps
    }

    AppBottomSheetContent(
        title = stringResource(R.string.advanced_tuning_title),
        icon = Icons.Default.Tune,
        onDismissRequest = onDismiss,
        actions = {
            AppDialogActions.SaveCancel(
                onSave = {
                    val newConfig = TuningConfig(
                        fullDimFactor = fullDimFactor,
                        mediumDimFactor = mediumDimFactor,
                        slopeFlatThreshold = slopeFlat,
                        slopeSteepThreshold = slopeSteep,
                        wakeupDurationSec = wakeupSec,
                        downwardDelaySec = downwardDelaySec,
                        gpsAccuracyThresholdMeters = gpsAccuracy,
                        altitudeFilterWindowSec = altitudeWindowSec,
                        slopeMinSpeedMps = slopeMinSpeed
                    )
                    scope.launch {
                        tuningDataStore.saveTuningConfig(newConfig)
                        onSettingsChanged?.invoke()
                        onDismiss()
                    }
                },
                onCancel = onDismiss,
                saveText = stringResource(R.string.save)
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Advisory Warning Notice
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.advanced_tuning_warning_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = stringResource(R.string.advanced_tuning_warning_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Category 1: AMOLED Battery Saver
            TuningCategoryHeader(stringResource(R.string.tuning_cat_battery_saver))

            TuningSliderItem(
                title = stringResource(R.string.tuning_full_dim_factor_title),
                valueText = "${(fullDimFactor * 100).roundToInt()}%",
                helperText = stringResource(R.string.tuning_full_dim_factor_desc),
                defaultText = stringResource(R.string.tuning_default_format, "25%"),
                value = fullDimFactor,
                onValueChange = {
                    fullDimFactor = it
                    if (it > mediumDimFactor) {
                        mediumDimFactor = it
                    }
                },
                valueRange = TuningPreferencesDefaults.MIN_FULL_DIM_FACTOR..TuningPreferencesDefaults.MAX_FULL_DIM_FACTOR,
                steps = 45 // 5% to 50%
            )

            TuningSliderItem(
                title = stringResource(R.string.tuning_medium_dim_factor_title),
                valueText = "${(mediumDimFactor * 100).roundToInt()}%",
                helperText = stringResource(R.string.tuning_medium_dim_factor_desc),
                defaultText = stringResource(R.string.tuning_default_format, "50%"),
                value = mediumDimFactor,
                onValueChange = {
                    mediumDimFactor = it
                    if (it < fullDimFactor) {
                        fullDimFactor = it
                    }
                },
                valueRange = TuningPreferencesDefaults.MIN_MEDIUM_DIM_FACTOR..TuningPreferencesDefaults.MAX_MEDIUM_DIM_FACTOR,
                steps = 70 // 20% to 90%
            )

            TuningSliderItem(
                title = stringResource(R.string.tuning_slope_flat_title),
                valueText = String.format(Locale.getDefault(), "%.1f%%", slopeFlat),
                helperText = stringResource(R.string.tuning_slope_flat_desc),
                defaultText = stringResource(R.string.tuning_default_format, "2.0%"),
                value = slopeFlat,
                onValueChange = {
                    slopeFlat = it
                    if (it > slopeSteep) {
                        slopeSteep = it
                    }
                },
                valueRange = TuningPreferencesDefaults.MIN_SLOPE_FLAT..TuningPreferencesDefaults.MAX_SLOPE_FLAT,
                steps = 10 // 0.0 to 5.0 in 0.5 steps
            )

            TuningSliderItem(
                title = stringResource(R.string.tuning_slope_steep_title),
                valueText = String.format(Locale.getDefault(), "%.1f%%", slopeSteep),
                helperText = stringResource(R.string.tuning_slope_steep_desc),
                defaultText = stringResource(R.string.tuning_default_format, "5.0%"),
                value = slopeSteep,
                onValueChange = {
                    slopeSteep = it
                    if (it < slopeFlat) {
                        slopeFlat = it
                    }
                },
                valueRange = TuningPreferencesDefaults.MIN_SLOPE_STEEP..TuningPreferencesDefaults.MAX_SLOPE_STEEP,
                steps = 26 // 2.0 to 15.0 in 0.5 steps
            )

            TuningSliderItem(
                title = stringResource(R.string.tuning_wakeup_duration_title),
                valueText = "$wakeupSec s",
                helperText = stringResource(R.string.tuning_wakeup_duration_desc),
                defaultText = stringResource(R.string.tuning_default_format, "15 s"),
                value = wakeupSec.toFloat(),
                onValueChange = { wakeupSec = it.roundToInt() },
                valueRange = TuningPreferencesDefaults.MIN_WAKEUP_SEC.toFloat()..TuningPreferencesDefaults.MAX_WAKEUP_SEC.toFloat(),
                steps = 55
            )

            TuningSliderItem(
                title = stringResource(R.string.tuning_downward_delay_title),
                valueText = "$downwardDelaySec s",
                helperText = stringResource(R.string.tuning_downward_delay_desc),
                defaultText = stringResource(R.string.tuning_default_format, "3 s"),
                value = downwardDelaySec.toFloat(),
                onValueChange = { downwardDelaySec = it.roundToInt() },
                valueRange = TuningPreferencesDefaults.MIN_DOWNWARD_DELAY_SEC.toFloat()..TuningPreferencesDefaults.MAX_DOWNWARD_DELAY_SEC.toFloat(),
                steps = 14
            )

            HorizontalDivider()

            // Category 2: GPS & Location Filtering
            TuningCategoryHeader(stringResource(R.string.tuning_cat_gps))

            TuningSliderItem(
                title = stringResource(R.string.tuning_gps_accuracy_title),
                valueText = "${gpsAccuracy.roundToInt()} m",
                helperText = stringResource(R.string.tuning_gps_accuracy_desc),
                defaultText = stringResource(R.string.tuning_default_format, "200 m"),
                value = gpsAccuracy,
                onValueChange = { gpsAccuracy = it },
                valueRange = TuningPreferencesDefaults.MIN_GPS_ACCURACY_M..TuningPreferencesDefaults.MAX_GPS_ACCURACY_M,
                steps = 49 // 10m to 500m in 10m steps
            )

            HorizontalDivider()

            // Category 3: Elevation & Gradient Dynamics
            TuningCategoryHeader(stringResource(R.string.tuning_cat_elevation))

            TuningSliderItem(
                title = stringResource(R.string.tuning_altitude_window_title),
                valueText = "$altitudeWindowSec s",
                helperText = stringResource(R.string.tuning_altitude_window_desc),
                defaultText = stringResource(R.string.tuning_default_format, "21 s"),
                value = altitudeWindowSec.toFloat(),
                onValueChange = { altitudeWindowSec = it.roundToInt() },
                valueRange = TuningPreferencesDefaults.MIN_ALTITUDE_WINDOW_SEC.toFloat()..TuningPreferencesDefaults.MAX_ALTITUDE_WINDOW_SEC.toFloat(),
                steps = 55
            )

            TuningSliderItem(
                title = stringResource(R.string.tuning_slope_min_speed_title),
                valueText = String.format(Locale.getDefault(), "%.1f m/s (%.1f km/h)", slopeMinSpeed, slopeMinSpeed * 3.6f),
                helperText = stringResource(R.string.tuning_slope_min_speed_desc),
                defaultText = stringResource(R.string.tuning_default_format, "0.5 m/s"),
                value = slopeMinSpeed,
                onValueChange = { slopeMinSpeed = it },
                valueRange = TuningPreferencesDefaults.MIN_SLOPE_SPEED_MPS..TuningPreferencesDefaults.MAX_SLOPE_SPEED_MPS,
                steps = 18 // 0.2 to 2.0 in 0.1 steps
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Prominent Reset to Factory Defaults Action Button
            OutlinedButton(
                onClick = {
                    scope.launch {
                        tuningDataStore.resetToDefaults()
                        fullDimFactor = TuningPreferencesDefaults.FULL_DIM_FACTOR
                        mediumDimFactor = TuningPreferencesDefaults.MEDIUM_DIM_FACTOR
                        slopeFlat = TuningPreferencesDefaults.SLOPE_FLAT_THRESHOLD
                        slopeSteep = TuningPreferencesDefaults.SLOPE_STEEP_THRESHOLD
                        wakeupSec = TuningPreferencesDefaults.WAKEUP_DURATION_SEC
                        downwardDelaySec = TuningPreferencesDefaults.DOWNWARD_DELAY_SEC
                        gpsAccuracy = TuningPreferencesDefaults.GPS_ACCURACY_THRESHOLD_M
                        altitudeWindowSec = TuningPreferencesDefaults.ALTITUDE_FILTER_WINDOW_SEC
                        slopeMinSpeed = TuningPreferencesDefaults.SLOPE_MIN_SPEED_MPS
                        onSettingsChanged?.invoke()
                        Toast.makeText(
                            context,
                            context.getString(R.string.reset_to_defaults_success),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.reset_to_defaults))
            }
        }
    }
}

@Composable
private fun TuningCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun TuningSliderItem(
    title: String,
    valueText: String,
    helperText: String,
    defaultText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = helperText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = defaultText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
        )
    }
}
