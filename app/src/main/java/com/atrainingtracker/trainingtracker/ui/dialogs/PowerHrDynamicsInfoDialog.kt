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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet

/**
 * Educational bottom sheet explaining 1st-Order Dynamical Power-to-Heart-Rate identification,
 * parameter physiology, and graph interpretation (REQ-REV-009 / ATT-3904).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerHrDynamicsInfoDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppModalBottomSheet(
        title = stringResource(R.string.cardio_power_info_title),
        icon = Icons.Default.MonitorHeart,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        actions = {
            Button(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.close))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section 1: Why standard ratios fail on hills
            InfoSectionCard(
                icon = Icons.Default.Info,
                title = stringResource(R.string.cardio_power_info_lag_title),
                body = stringResource(R.string.cardio_power_info_lag_desc)
            )

            // Section 2: Key Parameter Explanations
            InfoMetricCard(
                icon = Icons.Default.Speed,
                title = stringResource(R.string.cardio_power_info_hr200_title),
                body = stringResource(R.string.cardio_power_info_hr200_desc)
            )

            InfoMetricCard(
                icon = Icons.Default.TrendingUp,
                title = stringResource(R.string.cardio_power_info_drift_title),
                body = stringResource(R.string.cardio_power_info_drift_desc)
            )

            InfoMetricCard(
                icon = Icons.Default.Timeline,
                title = stringResource(R.string.cardio_power_info_tau_title),
                body = stringResource(R.string.cardio_power_info_tau_desc)
            )

            InfoMetricCard(
                icon = Icons.Default.TrendingUp,
                title = stringResource(R.string.cardio_power_info_slope_title),
                body = stringResource(R.string.cardio_power_info_slope_desc)
            )

            // Section 3: Graph Overlay Interpretation
            InfoSectionCard(
                icon = Icons.Default.Timeline,
                title = stringResource(R.string.cardio_power_info_overlay_title),
                body = stringResource(R.string.cardio_power_info_overlay_desc)
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun InfoSectionCard(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoMetricCard(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(18.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
