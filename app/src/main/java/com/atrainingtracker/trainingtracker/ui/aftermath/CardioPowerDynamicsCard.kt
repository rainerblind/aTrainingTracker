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

package com.atrainingtracker.trainingtracker.ui.aftermath

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.CardioPowerDynamicsRecord
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.components.MetricLayout
import com.atrainingtracker.trainingtracker.ui.dialogs.PowerHrDynamicsInfoDialog
import kotlin.math.roundToInt

/**
 * Dedicated Aftermath summary card displaying 1st-order dynamical Power-to-Heart-Rate identification parameters,
 * fresh vs. fatigued window comparisons, cardiac drift metrics, and educational info action (REQ-REV-010 / ATT-3905).
 */
@Composable
fun CardioPowerDynamicsCard(
    record: CardioPowerDynamicsRecord?,
    modifier: Modifier = Modifier,
    onInfoClick: (() -> Unit)? = null
) {
    if (record == null) return

    var showInternalInfoDialog by remember { mutableStateOf(false) }

    if (showInternalInfoDialog) {
        PowerHrDynamicsInfoDialog(
            onDismissRequest = { showInternalInfoDialog = false }
        )
    }

    ElevatedCard(
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Icon + Title + Info Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_cardio_load),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.cardio_power_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (onInfoClick != null) {
                            onInfoClick()
                        } else {
                            showInternalInfoDialog = true
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.cardio_power_info_title),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Section A: Fresh Window vs. End-of-Workout Window Comparison
            Text(
                text = stringResource(R.string.cardio_power_fresh_window),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricItem(
                    iconRes = null,
                    label = stringResource(R.string.cardio_power_hr200_label),
                    value = stringResource(R.string.cardio_power_hr200_single_format, record.hr200Start.roundToInt()),
                    secondaryValue = stringResource(R.string.cardio_power_rmse_format, record.rmseStart),
                    layout = MetricLayout.VERTICAL,
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    iconRes = null,
                    label = stringResource(R.string.cardio_power_tau_label),
                    value = stringResource(R.string.cardio_power_tau_single_format, record.tauStart.roundToInt()),
                    secondaryValue = stringResource(R.string.cardio_power_r2_format, record.r2Start),
                    layout = MetricLayout.VERTICAL,
                    modifier = Modifier.weight(1f)
                )
                record.aerobicSlopeStart?.let { slope ->
                    MetricItem(
                        iconRes = null,
                        label = stringResource(R.string.cardio_power_slope_label),
                        value = stringResource(R.string.cardio_power_slope_format, slope),
                        layout = MetricLayout.VERTICAL,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Section B: Fatigued (End) Window (if available)
            if (record.hr200End != null && record.tauEnd != null) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )

                Text(
                    text = stringResource(R.string.cardio_power_fatigued_window),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val driftStr = record.deltaHr200?.let {
                        val d = it.roundToInt()
                        if (d >= 0) "+$d" else "$d"
                    } ?: "0"
                    MetricItem(
                        iconRes = null,
                        label = stringResource(R.string.cardio_power_hr200_label),
                        value = stringResource(R.string.cardio_power_hr200_single_format, record.hr200End.roundToInt()),
                        secondaryValue = stringResource(R.string.cardio_power_drift_format, driftStr),
                        layout = MetricLayout.VERTICAL,
                        modifier = Modifier.weight(1f)
                    )
                    val deltaTauStr = record.deltaTau?.let {
                        val dt = it.roundToInt()
                        if (dt >= 0) "+$dt" else "$dt"
                    } ?: "0"
                    MetricItem(
                        iconRes = null,
                        label = stringResource(R.string.cardio_power_tau_label),
                        value = stringResource(R.string.cardio_power_tau_single_format, record.tauEnd.roundToInt()),
                        secondaryValue = stringResource(R.string.cardio_power_tau_delta_format, deltaTauStr),
                        layout = MetricLayout.VERTICAL,
                        modifier = Modifier.weight(1f)
                    )
                    record.aerobicSlopeEnd?.let { slope ->
                        MetricItem(
                            iconRes = null,
                            label = stringResource(R.string.cardio_power_slope_label),
                            value = stringResource(R.string.cardio_power_slope_format, slope),
                            layout = MetricLayout.VERTICAL,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
