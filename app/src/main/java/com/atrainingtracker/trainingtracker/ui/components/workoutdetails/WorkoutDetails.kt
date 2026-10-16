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

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.AltitudeFormatter
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.components.MetricLayout


@Composable
fun WorkoutDetails(
    data: WorkoutDetailsData,
    modifier: Modifier = Modifier
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
            data.ascentMeters,
            data.descentMeters,
            modifier = Modifier
        )

        // --- Section 4: Anaerobic Battery (W'bal) Minimum & Empty-Tank Badge (REQ-UI-399.3) ---
        if (data.minWPrimeBalKj != null && data.minWPrimeBalPct != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.workout_w_prime_bal_min_format, data.minWPrimeBalKj, data.minWPrimeBalPct),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (data.isEmptyTankWarning) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = stringResource(R.string.workout_w_prime_empty_tank),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // --- Section 5: Efficiency Factor (EF) & Aerobic Decoupling (Pw:Hr / Pa:Hr) (REQ-UI-400.3) ---
        if (data.efficiencyFactor != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val summaryText = if (data.aerobicDecouplingPct != null) {
                    if (data.isPowerBasedEfficiency) {
                        stringResource(R.string.workout_ef_pwhr_format, data.efficiencyFactor, data.aerobicDecouplingPct)
                    } else {
                        stringResource(R.string.workout_ef_pahr_format, data.efficiencyFactor, data.aerobicDecouplingPct)
                    }
                } else {
                    if (data.isPowerBasedEfficiency) {
                        stringResource(R.string.workout_ef_only_pw_format, data.efficiencyFactor)
                    } else {
                        stringResource(R.string.workout_ef_only_pa_format, data.efficiencyFactor)
                    }
                }
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (data.aerobicDecouplingPct != null) {
                    val badgeBg = if (data.isAerobicDecouplingHighDrift) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                    val badgeFg = if (data.isAerobicDecouplingHighDrift) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                    val badgeText = if (data.isAerobicDecouplingHighDrift) {
                        stringResource(R.string.workout_decoupling_drift)
                    } else {
                        stringResource(R.string.workout_decoupling_coupled)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = badgeBg,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeFg,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // --- Section 6: Power Training Metrics (NP, IF, TSS, Work kJ, W/kg, VI) (REQ-UI-402.2) ---
        val hasPowerTrainingMetrics = data.normalizedPowerWatts != null ||
            data.intensityFactor != null ||
            data.tss != null ||
            data.workKj != null ||
            data.avgPowerToWeight != null
        if (hasPowerTrainingMetrics) {
            PowerTrainingMetricsSection(data = data)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PowerTrainingMetricsSection(
    data: WorkoutDetailsData,
    modifier: Modifier = Modifier
) {
    val badges = buildList {
        data.normalizedPowerWatts?.let { add(stringResource(R.string.workout_power_metrics_np_format, it)) }
        data.intensityFactor?.let { add(stringResource(R.string.workout_power_metrics_if_format, it)) }
        data.tss?.let { add(stringResource(R.string.workout_power_metrics_tss_format, it)) }
        data.workKj?.let { add(stringResource(R.string.workout_power_metrics_work_format, it)) }
        data.avgPowerToWeight?.let { add(stringResource(R.string.workout_power_metrics_wkg_format, it)) }
        data.variabilityIndex?.let { add(stringResource(R.string.workout_power_metrics_vi_format, it)) }
    }
    if (badges.isEmpty()) return

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        badges.forEach { badgeLabel ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = badgeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AltitudeRow(
    ascentMeters: Long,
    descentMeters: Long,
    modifier: Modifier = Modifier,
){
    val formatters = com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter.current

    if (ascentMeters > 0 || descentMeters > 0) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.elevation),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Icon(
                    painter = painterResource(id = R.drawable.ic_altitude),
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp), // Slightly smaller than Time/Distance (28dp)
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column {
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
}



@Preview
@Composable
fun PreviewDetailItem() {
    MaterialTheme {
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
    MaterialTheme {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Active Time
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
    MaterialTheme {
        WorkoutDetails(
            data = WorkoutDetailsData(
                totalDistance = 12500.0,
                maxDisplacement = 4500.0,
                activeTimeSec = 3600,
                totalTimeSec = 4200,
                avgSpeedMps = 3.47,
                bSportType = BSportType.BIKE,
                ascentMeters = 250,
                descentMeters = 240
            )
        )
    }
}