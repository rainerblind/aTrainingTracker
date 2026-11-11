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

package com.atrainingtracker.trainingtracker.ui.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.hrv.HrvSignalQualityStatus
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet

/**
 * Educational and medical guidance sheet for Heart Rate Variability (HRV) metrics and aerobic threshold pacing
 * conforming to REQ-TRK-061.5, Rule 71 (theme color centralization), and Rule 72 (zero nested verticalScroll).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HrvInfoBottomSheet(
    onDismissRequest: () -> Unit,
    signalQualityStatus: HrvSignalQualityStatus? = null,
    modifier: Modifier = Modifier
) {
    AppModalBottomSheet(
        title = stringResource(R.string.hrv_info_title),
        onDismissRequest = onDismissRequest,
        icon = Icons.Default.Favorite,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (signalQualityStatus == HrvSignalQualityStatus.SMOOTHED_STREAM ||
                signalQualityStatus == HrvSignalQualityStatus.QUANTIZED_SYNTHETIC
            ) {
                HrvInfoSectionCard(
                    icon = Icons.Default.WarningAmber,
                    title = stringResource(R.string.hrv_info_smoothed_warning_title),
                    description = stringResource(R.string.hrv_info_smoothed_warning_desc),
                    isWarning = true,
                    modifier = Modifier.testTag("hrv_info_smoothed_warning_card")
                )
            }

            HrvInfoSectionCard(
                icon = Icons.Default.Info,
                title = stringResource(R.string.hrv_info_trailing_lag_title),
                description = stringResource(R.string.hrv_info_trailing_lag_desc)
            )

            HrvInfoSectionCard(
                icon = Icons.Default.Favorite,
                title = stringResource(R.string.hrv_info_sensor_recommendation_title),
                description = stringResource(R.string.hrv_info_sensor_recommendation_desc)
            )

            HrvInfoSectionCard(
                icon = Icons.Default.Info,
                title = stringResource(R.string.hrv_info_zones_title),
                description = stringResource(R.string.hrv_info_zones_desc)
            )

            HrvInfoSectionCard(
                icon = Icons.Default.WarningAmber,
                title = stringResource(R.string.hrv_info_disclaimer_title),
                description = stringResource(R.string.hrv_info_disclaimer_desc),
                isWarning = true
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.close))
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HrvInfoSectionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isWarning: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cardColors = if (isWarning) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val iconTint = if (isWarning) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = cardColors
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isWarning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
