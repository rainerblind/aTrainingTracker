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

package com.atrainingtracker.trainingtracker.ui.components.workoutdetails

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.components.MetricLayout
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * Shared workout telemetry summary details rendered inside `WorkoutSummary` cards
 * and `TrackOnMapScreen` / `MapDetailLayout` (`REQ-UI-399`, `REQ-UI-400`, `REQ-UI-402`,
 * `REQ-UI-403`, `REQ-UI-404`, `docs/design_guidelines.md` Section 5.10).
 */
@Composable
fun WorkoutDetails(
    data: WorkoutDetailsData,
    modifier: Modifier = Modifier,
    enableCogganPowerMetrics: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_COGGAN_POWER_METRICS,
    enableAerobicEfficiencyModel: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_AEROBIC_EFFICIENCY_MODEL,
    enableCriticalPowerModel: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_CRITICAL_POWER_MODEL
) {
    val formatters = com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Metrics Row: Time and Distance ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Active Time
            MetricItem(
                iconRes = R.drawable.ic_time_active,
                label = stringResource(R.string.time_active),
                value = formatters.time.format(data.activeTimeSec),
                secondaryValue = stringResource(R.string.total_time_format, formatters.time.format(data.totalTimeSec)),
                layout = MetricLayout.VERTICAL,
                isPrimary = true,
                iconSize = 28.dp,
                modifier = Modifier.weight(1f)
            )

            // 2. Distance
            val maxDispString = if (data.maxDisplacement != null) {
                val maxDispFormatted = formatters.distance.format_with_units(data.maxDisplacement)
                stringResource(R.string.format_max_displacement, maxDispFormatted)
            } else null

            MetricItem(
                iconRes = R.drawable.ic_distance,
                label = stringResource(R.string.distance),
                value = formatters.distance.format_with_units(data.totalDistance),
                secondaryValue = maxDispString,
                layout = MetricLayout.VERTICAL,
                isPrimary = true,
                iconSize = 28.dp,
                modifier = Modifier.weight(1f)
            )
        }

        // --- Section 3: Altitude (Ascent and Descent) ---
        AltitudeRow(
            ascentMeters = data.ascentMeters,
            descentMeters = data.descentMeters,
            modifier = Modifier
        )

        // --- Section 4: Power Training Metrics (NP, IF, TSS, Work kJ, W/kg, VI) (REQ-UI-402, REQ-UI-403, REQ-UI-404) ---
        val hasCogganMetrics = enableCogganPowerMetrics && (
            data.normalizedPowerWatts != null ||
                data.intensityFactor != null ||
                data.tss != null ||
                data.variabilityIndex != null
        )
        val hasBasicPowerMetrics = data.workKj != null || data.avgPowerToWeight != null
        if (hasCogganMetrics || hasBasicPowerMetrics) {
            PowerTrainingMetricsSection(
                data = data,
                enableCogganPowerMetrics = enableCogganPowerMetrics
            )
        }

        // --- Section 5: Efficiency Factor (EF) & Aerobic Decoupling (Pw:Hr / Pa:Hr) (REQ-UI-400, REQ-UI-403, REQ-UI-404) ---
        if (enableAerobicEfficiencyModel && data.efficiencyFactor != null) {
            AerobicEfficiencyRow(data = data)
        }

        // --- Section 6: Anaerobic Battery (W'bal) Minimum & Empty-Tank Status (REQ-UI-399, REQ-UI-403, REQ-UI-404) ---
        if (enableCriticalPowerModel && data.minWPrimeBalKj != null && data.minWPrimeBalPct != null) {
            AnaerobicBatteryRow(data = data)
        }
    }
}

/**
 * Canonical domain-grouped row layout following `docs/design_guidelines.md` Section 5.10 (`AltitudeRow` pattern):
 * Left column with `labelMedium` domain label + `24.dp` domain icon, `20.dp` horizontal spacer,
 * and right column of equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` rows.
 */
@Composable
private fun WorkoutDomainRow(
    domainLabel: String,
    @DrawableRes domainIconRes: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = domainLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Icon(
                painter = painterResource(id = domainIconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(20.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun AltitudeRow(
    ascentMeters: Long,
    descentMeters: Long,
    modifier: Modifier = Modifier
) {
    val formatters = com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter.current

    if (ascentMeters > 0 || descentMeters > 0) {
        WorkoutDomainRow(
            domainLabel = stringResource(R.string.elevation),
            domainIconRes = R.drawable.ic_altitude,
            modifier = modifier
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem(
                    iconRes = R.drawable.ic_ascent,
                    label = stringResource(R.string.ascent_short),
                    value = formatters.altitude.format_with_units(ascentMeters),
                    layout = MetricLayout.VERTICAL,
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    iconRes = R.drawable.ic_descent,
                    label = stringResource(R.string.descent_short),
                    value = formatters.altitude.format_with_units(descentMeters),
                    layout = MetricLayout.VERTICAL,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Grouped Power & Coggan Load row (`REQ-UI-404.2`, `docs/design_guidelines.md` Section 5.10).
 * Displays Coggan metrics (`NP` with `VI` secondary subtitle, `IF`, `TSS`) when `enableCogganPowerMetrics` is enabled,
 * and basic physics (`Work kJ`, `W/kg`) in balanced equal-weight `MetricItem` columns.
 */
@Composable
private fun PowerTrainingMetricsSection(
    data: WorkoutDetailsData,
    enableCogganPowerMetrics: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_COGGAN_POWER_METRICS,
    modifier: Modifier = Modifier
) {
    val showCogganRow = enableCogganPowerMetrics && (
        data.normalizedPowerWatts != null ||
            data.intensityFactor != null ||
            data.tss != null
    )
    val showBasicPhysicsRow = data.workKj != null || data.avgPowerToWeight != null

    if (!showCogganRow && !showBasicPhysicsRow) return

    WorkoutDomainRow(
        domainLabel = stringResource(R.string.power),
        domainIconRes = R.drawable.ic_power,
        modifier = modifier
    ) {
        if (showCogganRow) {
            Row(modifier = Modifier.fillMaxWidth()) {
                data.normalizedPowerWatts?.let { np ->
                    MetricItem(
                        iconRes = null,
                        label = stringResource(R.string.normalized_power_short),
                        value = stringResource(R.string.workout_power_value_watts_format, np),
                        secondaryValue = data.variabilityIndex?.let { vi ->
                            stringResource(R.string.workout_power_metrics_vi_format, vi)
                        },
                        layout = MetricLayout.VERTICAL,
                        modifier = Modifier.weight(1f)
                    )
                }
                data.intensityFactor?.let { ifVal ->
                    MetricItem(
                        iconRes = null,
                        label = stringResource(R.string.intensity_factor_short),
                        value = stringResource(R.string.workout_power_value_if_format, ifVal),
                        layout = MetricLayout.VERTICAL,
                        modifier = Modifier.weight(1f)
                    )
                }
                data.tss?.let { tssVal ->
                    MetricItem(
                        iconRes = null,
                        label = stringResource(R.string.tss_short),
                        value = stringResource(R.string.workout_power_value_tss_format, tssVal),
                        layout = MetricLayout.VERTICAL,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        if (showBasicPhysicsRow) {
            Row(modifier = Modifier.fillMaxWidth()) {
                data.workKj?.let { work ->
                    MetricItem(
                        iconRes = null,
                        label = stringResource(R.string.workout_power_label_work),
                        value = stringResource(R.string.workout_power_value_work_format, work),
                        layout = MetricLayout.VERTICAL,
                        modifier = Modifier.weight(1f)
                    )
                }
                data.avgPowerToWeight?.let { wkg ->
                    MetricItem(
                        iconRes = null,
                        label = stringResource(R.string.power_to_weight_short),
                        value = stringResource(R.string.workout_power_metrics_wkg_format, wkg),
                        layout = MetricLayout.VERTICAL,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Grouped Aerobic Efficiency & Decoupling (`EF`, `Pw:Hr` / `Pa:Hr`) row (`REQ-UI-404.3`, Section 5.10).
 */
@Composable
private fun AerobicEfficiencyRow(
    data: WorkoutDetailsData,
    modifier: Modifier = Modifier
) {
    val ef = data.efficiencyFactor ?: return

    WorkoutDomainRow(
        domainLabel = stringResource(R.string.workout_domain_efficiency),
        domainIconRes = R.drawable.ic_cardio_load,
        modifier = modifier
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            val efValue = if (data.isPowerBasedEfficiency) {
                stringResource(R.string.workout_ef_value_pw_format, ef)
            } else {
                stringResource(R.string.workout_ef_value_pa_format, ef)
            }
            MetricItem(
                iconRes = null,
                label = stringResource(R.string.workout_ef_short),
                value = efValue,
                layout = MetricLayout.VERTICAL,
                modifier = Modifier.weight(1f)
            )

            data.aerobicDecouplingPct?.let { decouplingPct ->
                val decouplingLabel = if (data.isPowerBasedEfficiency) {
                    stringResource(R.string.workout_pwhr_short)
                } else {
                    stringResource(R.string.workout_pahr_short)
                }
                val statusSubtitle = if (data.isAerobicDecouplingHighDrift) {
                    stringResource(R.string.workout_decoupling_drift)
                } else {
                    stringResource(R.string.workout_decoupling_coupled)
                }
                MetricItem(
                    iconRes = null,
                    label = decouplingLabel,
                    value = stringResource(R.string.workout_decoupling_pct_format, decouplingPct),
                    secondaryValue = statusSubtitle,
                    valueColor = if (data.isAerobicDecouplingHighDrift) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    layout = MetricLayout.VERTICAL,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Grouped Critical Power / Anaerobic Battery (`Min W'bal`, `Min %`) row (`REQ-UI-404.4`, Section 5.10).
 */
@Composable
private fun AnaerobicBatteryRow(
    data: WorkoutDetailsData,
    modifier: Modifier = Modifier
) {
    val minKj = data.minWPrimeBalKj ?: return
    val minPct = data.minWPrimeBalPct ?: return

    WorkoutDomainRow(
        domainLabel = stringResource(R.string.w_prime_bal_short),
        domainIconRes = R.drawable.ic_battery_full,
        modifier = modifier
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            MetricItem(
                iconRes = null,
                label = stringResource(R.string.workout_w_prime_min_kj_label),
                value = stringResource(R.string.workout_w_prime_min_kj_value_format, minKj),
                layout = MetricLayout.VERTICAL,
                modifier = Modifier.weight(1f)
            )
            MetricItem(
                iconRes = null,
                label = stringResource(R.string.workout_w_prime_min_pct_label),
                value = stringResource(R.string.workout_w_prime_min_pct_value_format, minPct),
                secondaryValue = if (data.isEmptyTankWarning) {
                    stringResource(R.string.workout_w_prime_empty_tank)
                } else {
                    null
                },
                valueColor = if (data.isEmptyTankWarning) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                layout = MetricLayout.VERTICAL,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Preview
@Composable
fun PreviewDetailItem() {
    ATrainingTrackerTheme {
        MetricItem(
            iconRes = R.drawable.ic_distance,
            label = "Distance",
            modifier = Modifier.fillMaxWidth(),
            value = "10,00 km",
            secondaryValue = "(Max. Luftlinie: 5,00 km)",
            layout = MetricLayout.VERTICAL
        )
    }
}

@Preview
@Composable
fun PreviewDistanceAndTime() {
    ATrainingTrackerTheme {
        Row(modifier = Modifier.fillMaxWidth()) {
            MetricItem(
                iconRes = R.drawable.ic_time_active,
                label = "Active Time",
                value = "0:30:00",
                secondaryValue = "(Total: 0:45:00)",
                modifier = Modifier.weight(1f),
                layout = MetricLayout.VERTICAL,
                iconSize = 28.dp
            )

            MetricItem(
                iconRes = R.drawable.ic_distance,
                label = "Distance",
                value = "10,00 km",
                secondaryValue = "(Max. Luftlinie: 5,00 km)",
                modifier = Modifier.weight(1f),
                layout = MetricLayout.VERTICAL,
                iconSize = 28.dp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewWorkoutDetails() {
    ATrainingTrackerTheme {
        WorkoutDetails(
            data = WorkoutDetailsData(
                totalDistance = 42500.0,
                maxDisplacement = 18500.0,
                activeTimeSec = 5400,
                totalTimeSec = 5700,
                avgSpeedMps = 7.87,
                bSportType = BSportType.BIKE,
                ascentMeters = 620,
                descentMeters = 615,
                normalizedPowerWatts = 215.0,
                intensityFactor = 0.78,
                tss = 91.0,
                workKj = 1080.0,
                avgPowerToWeight = 2.87,
                variabilityIndex = 1.06,
                efficiencyFactor = 1.48,
                aerobicDecouplingPct = 3.2,
                isPowerBasedEfficiency = true,
                isAerobicDecouplingHighDrift = false,
                minWPrimeBalKj = 4.2,
                minWPrimeBalPct = 21.0,
                isEmptyTankWarning = false
            ),
            enableCogganPowerMetrics = true,
            enableAerobicEfficiencyModel = true,
            enableCriticalPowerModel = true
        )
    }
}