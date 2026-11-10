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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.components.MetricLayout
import kotlin.math.roundToInt

/**
 * Grouped Cardio-Power Dynamics (HR@200W, \tau, 1/K) row (REQ-REV-010, Section 5.10).
 */
@Composable
internal fun CardioPowerDynamicsRow(
    data: WorkoutDetailsData,
    modifier: Modifier = Modifier
) {
    val record = data.cardioPowerDynamics ?: return

    WorkoutDomainRow(
        domainLabel = stringResource(R.string.cardio_power_domain_label),
        domainIconRes = R.drawable.ic_cardio_load,
        modifier = modifier
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            val hr200Value = if (record.hr200End != null) {
                stringResource(
                    R.string.cardio_power_hr200_range_format,
                    record.hr200Start.roundToInt(),
                    record.hr200End.roundToInt()
                )
            } else {
                stringResource(
                    R.string.cardio_power_hr200_single_format,
                    record.hr200Start.roundToInt()
                )
            }
            val driftSubtitle = record.deltaHr200?.let { delta ->
                val deltaInt = delta.roundToInt()
                val deltaStr = if (deltaInt >= 0) "+$deltaInt" else "$deltaInt"
                stringResource(R.string.cardio_power_drift_format, deltaStr)
            }
            MetricItem(
                iconRes = null,
                label = stringResource(R.string.cardio_power_hr200_label),
                value = hr200Value,
                secondaryValue = driftSubtitle,
                layout = MetricLayout.VERTICAL,
                modifier = Modifier.weight(1f)
            )

            val tauValue = if (record.tauEnd != null) {
                stringResource(
                    R.string.cardio_power_tau_range_format,
                    record.tauStart.roundToInt(),
                    record.tauEnd.roundToInt()
                )
            } else {
                stringResource(
                    R.string.cardio_power_tau_single_format,
                    record.tauStart.roundToInt()
                )
            }
            val deltaTauSubtitle = record.deltaTau?.let { dTau ->
                val dTauInt = dTau.roundToInt()
                val dTauStr = if (dTauInt >= 0) "+$dTauInt" else "$dTauInt"
                stringResource(R.string.cardio_power_tau_delta_format, dTauStr)
            }
            MetricItem(
                iconRes = null,
                label = stringResource(R.string.cardio_power_tau_label),
                value = tauValue,
                secondaryValue = deltaTauSubtitle,
                layout = MetricLayout.VERTICAL,
                modifier = Modifier.weight(1f)
            )

            val slope = record.aerobicSlopeStart
            if (slope != null) {
                MetricItem(
                    iconRes = null,
                    label = stringResource(R.string.cardio_power_slope_label),
                    value = stringResource(R.string.cardio_power_slope_format, slope),
                    secondaryValue = stringResource(R.string.cardio_power_r2_format, record.r2Start),
                    layout = MetricLayout.VERTICAL,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
